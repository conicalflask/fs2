package client.shareserver;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import javax.net.ssl.SSLContext;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;

import sun.misc.Signal;
import sun.misc.SignalHandler;

import client.gui.Notifications;
import client.gui.Utilities;
import client.indexnode.IndexNodeCommunicator;
import client.indexnode.PeerStatsCollector;
import client.platform.Platform;
import client.platform.ClientConfigDefaults.CK;
import client.shareserver.Share.Status;

import common.httpserver.HttpContext;
import common.httpserver.HttpServer;

import common.Config;
import common.FS2Constants;
import common.FS2Filter;
import common.HttpFileHandler;
import common.Logger;
import common.NamedThreadFactory;
import common.ProgressTracker;
import common.SimpleHttpHandler;
import common.Util;
import common.HttpFileHandler.HttpFileHandlerEvents;
import common.HttpUtil.HttpTransferInfo;
import common.Util.Deferrable;
import common.Util.FileSize;
import common.Util.NiceMagnitude;

/**
 * This is the second major implementation of the ShareServer subsystem.
 * 
 * The shareserver is responsible for:
 * 1) managing shares (filelist creation, hashing, etc)
 * 2) exporting shares to other FS2 clients. (A webserver)
 * 
 * This is the top-level class for the shareserver.
 * 
 * A table model is also implemented so this can drive a swing table with ease.
 * 
 * @author gary
 */
public class ShareServer implements TableModel {

	private class RefreshSignalHandler implements SignalHandler {
		@Override
		public void handle(Signal sig) {
			Logger.log("Caught signal '"+sig.getName()+"', refreshing shares...");
			refreshAllShares();
		}
	}
	
	private static final int FILENAME_IDX=0;
	public static final int SECURE_IDX=1;
	public static final int PEER_IDX=2;
	public static final int PROGRESS_IDX=3;
	private static final int SPEED_IDX=4;
	private static final int ETR_IDX=5;
	private class UploadsTableModel implements TableModel {

		ArrayList<HttpTransferInfo> currentTransfers = new ArrayList<HttpTransferInfo>();
		ArrayList<TableModelListener> listeners = new ArrayList<TableModelListener>();
		
		private Class<?>[] columnClasses = {String.class, Boolean.class, String.class, Float.class, FileSize.class, String.class}; //fileName, peer alias, progress,  speed, etr
		private String[] columnNames = {"Filename", "Secure?", "Peer", "Progress", "Speed", "Time remaining"};
		
		
		@Override
		public void addTableModelListener(TableModelListener l) {
			synchronized (listeners) {
				listeners.add(l);
			}
		}

		@Override
		public Class<?> getColumnClass(int columnIndex) {
			return columnClasses[columnIndex];
		}

		@Override
		public int getColumnCount() {
			return columnClasses.length;
		}

		@Override
		public String getColumnName(int columnIndex) {
			return columnNames[columnIndex];
		}

		@Override
		public int getRowCount() {
			return currentTransfers.size();
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			HttpTransferInfo info = currentTransfers.get(rowIndex);
			switch (columnIndex) {
			case FILENAME_IDX:
				return info.getFile().getName();
			case SECURE_IDX:
				return info.getExchange().isSecure();
			case PEER_IDX:
				return info.getAlias();
			case PROGRESS_IDX:
				return info.getTracker().percentComplete();
			case SPEED_IDX:
				return new FileSize((long) info.getTracker().getSpeed(), true);
			case ETR_IDX:
				return info.getTracker().describeTimeRemaining();
			}
			return null;
		}

		@Override
		public boolean isCellEditable(int rowIndex, int columnIndex) {
			return false; //read only table
		}

		@Override
		public void removeTableModelListener(TableModelListener l) {
			synchronized (listeners) {
				listeners.remove(l);
			}
		}

		@Override
		public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
			//nothing is editable
		}
		
		void newTransfer(HttpTransferInfo info) {
			synchronized (currentTransfers) {
				currentTransfers.add(info);
				try {
					Utilities.dispatch(new Runnable() {
						@Override
						public void run() {
							int idx = currentTransfers.size()-1; //this must be the index of the new transfer.
							synchronized (listeners) {
								for (TableModelListener l : listeners) {
									l.tableChanged(new TableModelEvent(UploadsTableModel.this, idx, idx, TableModelEvent.ALL_COLUMNS, TableModelEvent.INSERT));
								}
							}
						}
					});
				} catch (Exception e) {
					Logger.warn("Couldn't insert into uploads table: "+e);
					e.printStackTrace();
				}
			}
		}
		
		void transferEnded(HttpTransferInfo info) {
			synchronized (currentTransfers) {
				final int oldidx = currentTransfers.indexOf(info);
				currentTransfers.remove(oldidx);
				if (Thread.currentThread().isInterrupted()) {
					try {
						Thread.sleep(1); //try to sleep (which will fail due to interruption), and will clear the interrupt flag so then we may dispatch safely.
					} catch (InterruptedException cleared) {}
				}
				try {
					Utilities.dispatch(new Runnable() {
						@Override
						public void run() {
							synchronized (listeners) {
								for (TableModelListener l : listeners) {
									l.tableChanged(new TableModelEvent(UploadsTableModel.this, oldidx, oldidx, TableModelEvent.ALL_COLUMNS, TableModelEvent.DELETE));
								}
							}
						}
					});
				} catch (Exception e) {
					Logger.warn("Couldn't remove from uploads table: "+e);
					e.printStackTrace();
				}
			}
		}
		
		void transferChanged(HttpTransferInfo info) {
			tableUpdater.updateInfo(info);
			Util.scheduleExecuteNeverFasterThan(FS2Constants.CLIENT_EVENT_MIN_INTERVAL, tableUpdater);
		}
		TableUpdater tableUpdater = new TableUpdater();
		private class TableUpdater implements Deferrable {
			HashSet<HttpTransferInfo> infosToUpdate = new HashSet<HttpTransferInfo>();
			
			public void updateInfo(HttpTransferInfo info) {
				synchronized (infosToUpdate) {
					infosToUpdate.add(info);
				}
			}
			
			@Override
			public void run() {
				final HashSet<HttpTransferInfo> copy;
				synchronized (infosToUpdate) {
					copy = new HashSet<HttpTransferInfo>(infosToUpdate);
					infosToUpdate.clear();
				}
				synchronized (currentTransfers) {
					try {
						Utilities.dispatch(new Runnable() {
							@Override
							public void run() {
								for (int i=0; i<currentTransfers.size(); i++) {
									if (copy.contains(currentTransfers.get(i))) {
										synchronized (listeners) {
											for (TableModelListener l : listeners) {
												l.tableChanged(new TableModelEvent(UploadsTableModel.this, i,i,TableModelEvent.ALL_COLUMNS, TableModelEvent.UPDATE));
											}
										}
									}
								}
							}
						});
					} catch (Exception e) {
						Logger.warn("Couldn't update uploads table: "+e);
						e.printStackTrace();
					}
				}
			}
		}
	}
	
	/**
	 * Handles events from the file serving contexts.
	 * 1) notifies peer stats collector
	 * 2) updates current uploads table
	 * 
	 * @author gary
	 */
	private class HttpEventsImpl extends HttpFileHandlerEvents {
		@Override
		public void transferStarted(HttpTransferInfo info) {
			peerstats.sendingFileStarted(info.getAlias());
			uploadsModel.newTransfer(info);
		}
		
		@Override
		public void bytesTransferred(HttpTransferInfo info, long byteCount) {
			peerstats.sentBytes(info.getAlias(), byteCount, info.getInterval());
			uploadsModel.transferChanged(info);
		}
		
		@Override
		public void transferEnded(HttpTransferInfo info) {
			peerstats.sentFile(info.getAlias());
			uploadsModel.transferEnded(info);
		}
	}
	
	private TimedQueue<Long> tq = new TimedQueue<Long>();
	private BandwidthSharer httpThrottle = new BandwidthSharerImpl();
	
	private FS2Filter fs2Filter = new FS2Filter();
	private QueueFilter queueFilter = new QueueFilter(tq);
	private HttpThrottleOutputFilter throttleFilter = new HttpThrottleOutputFilter(httpThrottle);
	
	private HttpServer http;
	private IndexNodeCommunicator communicator;
	private List<Share> shares = Collections.synchronizedList(new ArrayList<Share>());
	private Timer shareRefreshTimer;
	private Config conf;
	private ThreadPoolExecutor shareRefreshPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(1, new NamedThreadFactory(true, "Share refresh thread."));
	private HttpEventsImpl httpEvents = new HttpEventsImpl();
	private PeerStatsCollector peerstats;
	private Notifications notify;
	private ProgressTracker uploadTracker = new ProgressTracker();
	private UploadsTableModel uploadsModel = new UploadsTableModel();
	private SSLContext context; //used for serving TLS.
	private SecureFilter secureFilter; //Enforces secure connections when it's not possible to have found us via insecure means.
	
	public TableModel getUploadsModel() {
		return uploadsModel;
	}
	
	public ShareServer(Config conf, PeerStatsCollector peerstats, Notifications notify) throws IOException, NoSuchAlgorithmException, KeyManagementException {
		this.conf = conf;
		this.peerstats = peerstats;
		this.notify = notify;
		
		int onPort = conf.getInt(CK.PORT);
		//Check for valid port:
		if ((onPort < 1) || (onPort > 65535)) {
			throw new IllegalArgumentException("Bad port number");
		}
		
		//Prepare the filelist directory if needed:
		File filelistDir = Platform.getPlatformFile("filelists");
		if (!filelistDir.isDirectory()) {
			filelistDir.mkdir();
		}
		
		fs2Filter.setPort(onPort);
		fs2Filter.setAlias(conf.getString(CK.ALIAS));
		
		tq.setQueueTimeoutMS(FS2Constants.CLIENT_TIMEDQUEUE_TOKEN_EXPIRY_INTERVAL);
		
		context = SSLContext.getInstance("TLS");
		context.init(null, null, null); //no key manager needed, no trust manager needed, default secure random generator is fine.
		
		//Setup and bind the http server: 
		//  it's bound both on secure and insecure sockets. The secureFilter will reject requests on the insecure socket when the requests are not needed.
		http = HttpServer.create(new InetSocketAddress(onPort), new InetSocketAddress(onPort+1), context, new String[] {FS2Constants.DH_ANON_CIPHER_SUITE_USED}, 0);
		//Enable a multithreaded executor:
		http.setExecutor(Executors.newCachedThreadPool(new NamedThreadFactory(true, "HTTP serving thread")));
		
		http.setSoTimeout(FS2Constants.SERVER_URL_CONNECTION_TIMEOUT_MS);
		http.setUseKeepAlives(false); //no idle connections are useful to us.
		
		//Instantiating the communicator will add a new context to our http server.
		communicator = new IndexNodeCommunicator(this, conf);
		
		//Can generate the secureFilter now with the communicator:
		secureFilter = new SecureFilter(communicator);
		//Add a default context:
		http.createContext("/",new SimpleHttpHandler(404,"File not found.")).getFilters().add(secureFilter);
		
		
		//Share the filelists:
		HttpContext flc = http.createContext("/filelists", new HttpFileHandler(filelistDir, httpEvents, uploadTracker));
		//Make the filelists talk correctly.
		flc.getFilters().add(fs2Filter);
		flc.getFilters().add(communicator.getIndexNodeOnlyFilter()); //only indexnode and localhost may access them.

		
		//And we're go!
		http.start();
		
		//setup abuse limits:
		setUploadSpeedFromConf();
		setSlotsFromConf();
		
		//Now add shares specified in the config:
		restartConfigShares();
		
		//Start the timer that will periodically check to see if a share needs refreshing:
		shareRefreshTimer = new Timer("Share refresh timer", false);
		shareRefreshTimer.schedule(new ConsiderRefreshingSharesTask(), 0, FS2Constants.CLIENT_SHARE_REFRESH_POLL_INTERVAL);
		
		//Register with configured indexnodes:
		notify.incrementLaunchProgress("Registering with configured indexnodes...");
		communicator.loadConfiguredNodes();
		
		try {
			Signal.handle(new Signal("USR1"), new RefreshSignalHandler());
		} catch (IllegalArgumentException e) {
			Logger.warn("Couldn't attach a signal handler for share refreshes.");
		}
		
		Logger.log("Now exporting shares on port "+onPort);
	}
	
	private class ConsiderRefreshingSharesTask extends TimerTask {

		@Override
		public void run() {
			ArrayList<Share> scopy;
			synchronized (shares) {
				scopy = new ArrayList<Share>(shares);
			}
			for (Share s : scopy) {
				if (isShareOverdueForRefresh(s)) {
					s.refresh();
				}
				notifyShareChanged(s);
			}
		}
		
	}
	
	public Config getConf() {
		return conf;
	}
	
	/**
	 * Returns the filter used to add headers to HTTP server responses and client requests:
	 * @return
	 */
	public FS2Filter getFS2Filter() {
		return fs2Filter;
	}
	
	/**
	 * Gets the parent tracker of all the uploads.
	 * @return
	 */
	public ProgressTracker getUploadTracker() {
		return uploadTracker;
	}
	
	public QueueFilter getQueueFilter() {
		return queueFilter;
	}
	
	public PeerStatsCollector getPeerstats() {
		return peerstats;
	}
	
	public HttpThrottleOutputFilter getThrottleFilter() {
		return throttleFilter;
	}
	
	public HttpServer getHttpServer() {
		return http;
	}
	
	public HttpFileHandlerEvents getHttpEventsHandler() {
		return httpEvents;
	}
	
	public IndexNodeCommunicator getIndexNodeCommunicator() {
		return communicator;
	}
	
	public long getUploadSpeed() {
		return conf.getLong(CK.UPLOAD_BYTES_PER_SEC);
	}
	
	private void setUploadSpeedFromConf() {
		httpThrottle.setBytesPerSecond(conf.getLong(CK.UPLOAD_BYTES_PER_SEC));
	}
	
	public void setUploadSpeed(long bytesPerSecond) {
		conf.putLong(CK.UPLOAD_BYTES_PER_SEC, bytesPerSecond);
		httpThrottle.setBytesPerSecond(bytesPerSecond);
	}
	
	public int getUploadSlots() {
		return conf.getInt(CK.ACTIVE_UPLOADS);
	}
	
	public int getUploadSlotsPerUser() {
		return conf.getInt(CK.ACTIVE_UPLOADS_PER_USER);
	}
	
	private void setSlotsFromConf() {
		tq.setClientLimit(conf.getInt(CK.ACTIVE_UPLOADS_PER_USER));
		tq.setResourceCount(conf.getInt(CK.ACTIVE_UPLOADS));
		
	}
	
	public void setUploadSlots(int slots) {
		conf.putInt(CK.ACTIVE_UPLOADS, slots);
		tq.setResourceCount(slots);
	}
	
	public void setUploadSlotsPerUser(int slotsps) {
		conf.putInt(CK.ACTIVE_UPLOADS_PER_USER, slotsps);
		tq.setClientLimit(slotsps);
	}
	
	public void shutdown() {
		
		//shutdown the refresh timer:
		shareRefreshTimer.cancel();
		
		//) shutdown each share as they might be refreshing:
		synchronized (shares) {
			for (Share s : shares) {
				s.shutdown();
			}
		}
		//) shutdown the communicator.
		communicator.shutdown();
		
		http.stop();
	}
	
	public void setAlias(String alias) {
		conf.putString(CK.ALIAS, alias);
		fs2Filter.setAlias(alias);
		communicator.aliasChanged();
	}
	
	public String getAlias() {
		return fs2Filter.getAlias();
	}
	
	int cachedAutoRefreshInterval = 0;
	/**
	 * Returns the number of SECONDS between autorefreshes of shares.
	 * @return
	 */
	public int getAutoRefreshInterval() {
		if (cachedAutoRefreshInterval>0) return cachedAutoRefreshInterval;
		return cachedAutoRefreshInterval=conf.getInt(CK.SHARE_AUTOREFRESH_INTERVAL);
	}
	
	/**
	 * Sets the time between refreshes in seconds.
	 * @param seconds
	 */
	public void setAutoRefreshInterval(int seconds) {
		conf.putInt(CK.SHARE_AUTOREFRESH_INTERVAL, seconds);
		cachedAutoRefreshInterval = seconds;
	}
	
	public SecureFilter getSecureFilter() {
		return secureFilter;
	}
	
	//==========================
	// Share management methods:
	
	/**
	 * Returns a map of share names onto share objects.
	 * This is is the actual data structure used by the shareserver, so don't mess it up.
	 * This returns a syncrhonized list, but make sure to synchronize again if iterating.
	 */
	public List<Share> getShares() {
		return shares;
	}
	
	/**
	 * Permenantly exports the given directory as the name given.
	 * @param name
	 * @param location
	 */
	public void addShare(String name, File location) {
		if (name.equals("")) throw new IllegalArgumentException("Invalid name.");
		if (shareNameExists(name)) throw new IllegalArgumentException("Share name already exists!");
		conf.putString(CK.SHARES+"/s"+name.hashCode()+"/name", name);
		conf.putString(CK.SHARES+"/s"+name.hashCode()+"/path", location.getPath());
		try {
			listShare(name, location);
		} catch (IOException e) {
			Logger.severe("Couldn't add new share: "+e);
			e.printStackTrace();
		}
		communicator.sharesChanged();
		Logger.log("Share '"+name+"' sucessfully added.");
	}
	
	public boolean shareNameExists(String name) {
		synchronized (shares) {
			for (Share s : shares) {
				if (s.getName().equals(name)) return true;
			}
		}
		return false;
	}
	
	/**
	 * Called on startup to load shares from config file:
	 * @throws IOException 
	 */
	private void restartConfigShares() throws IOException {
		for (String shareKey : conf.getChildKeys(CK.SHARES)) {
			String shareName = conf.getString(shareKey+"/name");
			File sharePath = new File(conf.getString(shareKey+"/path"));
			notify.incrementLaunchProgress("Loading share '"+shareName+"'....");
			if (!shareKey.equals(CK.SHARES+"/s"+shareName.hashCode())) {
				Logger.log("Making config key canonical for share: "+shareName);
				//stored in the config with a non-canonical key, so erase it:
				conf.deleteKey(shareKey);
				//and re-add it canonically
				addShare(shareName, sharePath);
			} else {
				listShare(shareName, sharePath);
			}
		}
		communicator.sharesChanged();
	}
	
	/**
	 * Creates and exports a share object. but does not create a record in the configuration file, nor notify the indexnodes.
	 * @param name
	 * @param location
	 * @throws IOException 
	 */
	private void listShare(String name, File location) throws IOException {
		Share s = new Share(this, name, location);
		int idx;
		synchronized (shares) {
			shares.add(s);
			idx = shares.indexOf(s);
		}
		notifySharedAdded(idx);
	}
	
	/**
	 * Removes the share with the given name.
	 * @param name
	 */
	public void removeShare(Share share) {
		int idx;
		share.shutdown();
		synchronized (shares) {
			if ((idx=shares.indexOf(share))==-1) {
				Logger.warn("Attempt to remove unknown share '"+share.getName()+"'");
				return;
			}
			shares.remove(idx);
		}
		notifyShareRemoved(idx);
		conf.deleteKey(CK.SHARES+"/s"+share.getName().hashCode());
		communicator.sharesChanged();
	}
	
	ThreadPoolExecutor getShareRefreshPool() {
		return shareRefreshPool;
	}
	
	private void refreshAllShares() {
		synchronized (shares) {
			for (Share s : shares) {
				s.refresh();
			}
		}
	}
	
	public void defaultDownloadDirectoryChanged(File newDefaultDir) {
		synchronized (shares) {
			for (Share s : shares) {
				if (s.getName().equals(FS2Constants.CLIENT_DEFAULT_SHARE_NAME)) {
					conf.putString(CK.SHARES+"/s"+FS2Constants.CLIENT_DEFAULT_SHARE_NAME.hashCode()+"/path", newDefaultDir.getPath());
					try {
						s.setPath(newDefaultDir);
					} catch (IOException e) {
						Logger.severe("Couldn't set the path for the default download dir: "+e);
					}
				}
			}
		}
	}
	
	ArrayList<TableModelListener> modelListeners = new ArrayList<TableModelListener>();
	private Class<?>[] columnClasses = {String.class, String.class, FileSize.class, FileSize.class, String.class, String.class}; //Name, status , size, time to next refresh
	private String[] columnNames = {"Name", "Status", "Size", "Files", "Next refresh", "Path"};
	private static final int NAME_IDX=0;
	private static final int STATUS_IDX=1;
	private static final int SIZE_IDX=2;
	private static final int FILE_IDX=3;
	private static final int NEXT_REFRESH_IDX=4;
	private static final int PATH_IDX=5;

	@Override
	public void addTableModelListener(TableModelListener l) {
		synchronized (modelListeners) {
			modelListeners.add(l);
		}
	}

	@Override
	public Class<?> getColumnClass(int columnIndex) {
		return columnClasses[columnIndex];
	}

	@Override
	public int getColumnCount() {
		return columnClasses.length;
	}

	@Override
	public String getColumnName(int columnIndex) {
		return columnNames[columnIndex];
	}

	@Override
	public int getRowCount() {
		return shares.size();
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		Share share = null;
		share = shares.get(rowIndex);
		switch (columnIndex) {
		case NAME_IDX:
			return share.getName();
		case STATUS_IDX:
			return share.describeStatus();
		case SIZE_IDX:
			return new FileSize(share.getSize());
		case FILE_IDX:
			return new NiceMagnitude(share.getFileCount(), " files");
		case NEXT_REFRESH_IDX:
			long ttnr = getTimeToNextRefreshShare(share);
			return (ttnr<=0 ? "pending" : Util.describeInterval(ttnr));
		case PATH_IDX:
			return share.getPath().getAbsolutePath();
		}
		return null;
	}

	/**
	 * Determines the seconds until the share specified should be refreshed.
	 * 
	 * If the refresh is overdue this still returns zero.
	 * 
	 * @param share
	 * @return A non-negative integer representing the time that should elapse between now and the next refresh of the share specified.
	 */
	private long getTimeToNextRefreshShare(Share share) {
		return Math.max(0l,(((long)getAutoRefreshInterval()*1000l+share.getLastRefreshed())-System.currentTimeMillis())/1000);
	}

	@Override
	public boolean isCellEditable(int rowIndex, int columnIndex) {
		return false;
	}

	@Override
	public void removeTableModelListener(TableModelListener l) {
		synchronized (modelListeners) {
			modelListeners.remove(l);
		}
	}

	@Override
	public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
		//Cells are not editable
	}
	
	void notifyShareChanged(Share share) {
		int rowidx = -1;
		rowidx = shares.indexOf(share);
		if (rowidx==-1) {
			//this is possible as shares can change status before they are in the list.
			return;
		}
		fireTableChanged(new TableModelEvent(this, rowidx));
	}
	
	void notifySharedAdded(int rowidx) {
		fireTableChanged(new TableModelEvent(this, rowidx, rowidx, TableModelEvent.ALL_COLUMNS, TableModelEvent.INSERT));
	}
	
	void notifyShareRemoved(int rowidx) {
		fireTableChanged(new TableModelEvent(this, rowidx, rowidx, TableModelEvent.ALL_COLUMNS, TableModelEvent.DELETE));
	}
	
	void fireTableChanged(final TableModelEvent e) {
		try {
			Utilities.dispatch(new Runnable() {
				@Override
				public void run() {
					synchronized (modelListeners) {
						for (TableModelListener l : modelListeners) {
							l.tableChanged(e);
						}
					}
				}
			});
		} catch (Exception e1) {
			Logger.warn("Couldn't notify Shares table listeners: "+e1);
			e1.printStackTrace();
		}
	}

	private boolean isShareOverdueForRefresh(Share s) {
		return getTimeToNextRefreshShare(s)<=0 && s.getStatus()!=Status.REFRESHING && s.getStatus()!=Status.BUILDING && s.getStatus()!=Status.SAVING;
	}

}
