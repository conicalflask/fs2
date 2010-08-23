package client.indexnode;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import common.Util.Deferrable;
import common.Util.ImageResizeType;
import common.httpserver.Filter;
import common.httpserver.HttpContext;

import common.Base64Coder;
import common.Config;
import common.FS2Constants;
import common.Logger;
import common.NamedThreadFactory;
import common.Sxml;
import common.Util;
import client.gui.Utilities;
import client.indexnode.downloadcontroller.DownloadSource;
import client.indexnode.internal.InternalIndexnodeManager;
import client.platform.ClientConfigDefaults.CK;
import client.shareserver.Share;
import client.shareserver.ShareServer;
import client.shareserver.Share.Status;

/**
 * Communicates with the indexnode(s).
 * This hides the transport level details of the indexnode protocol from other parts of FS2.
 * 
 * Requires a running shareserver to start!
 * 
 * This also provides access to a {@link FileSystem} object that allows highly abstracted access
 * to the connected indexnodes.
 * 
 * @author gary
 */
public class IndexNodeCommunicator implements TableModel {

	ArrayList<IndexNode> nodes = new ArrayList<IndexNode>();
	AdvertListener listener = null;
	boolean autodetectIndexnodes = false;
	ShareServer ssvr = null;
	String shareListXML = "";
	IndexNodeOnlyFilter inof = new IndexNodeOnlyFilter(this);
	Config conf;
	FileSystem fs = new FileSystem(this);
	ExecutorService chatRequestPool = Executors.newFixedThreadPool(FS2Constants.CLIENT_MAX_CONCURRENT_CHAT_REQUESTS, new NamedThreadFactory(true, "Chat request"));
	ArrayList<TableModelListener> modelListeners = new ArrayList<TableModelListener>();
	IndexNodeStatsTableModel statsTable = new IndexNodeStatsTableModel(this);
	ArrayList<NewPeerListener> newPeerListeners = new ArrayList<NewPeerListener>();
	
	//Our own personal indexnode:
	InternalIndexnodeManager iim;
	
	//Avatar stuff:
	String encodedAvatar;		//base64 encoded cache of the current avatar
	String encodedAvatarMD5;    //md5 of the cached avatar
	
	/**
	 * Returns the image file that will be used as this client's avatar on the indexnodes.
	 * @return
	 */
	public File getAvatarFile() {
		return new File(conf.getString(CK.AVATAR_PATH));
	}

	public void setAvatarFile(File newAvatar) {
		conf.putString(CK.AVATAR_PATH, newAvatar.getPath());
		setupAvatar();
	}
	
	void setupAvatar() {
		File avatarFile = getAvatarFile();
		if (avatarFile.isFile() && avatarFile.canRead()) {
			try {
				InputStream is = new BufferedInputStream(new FileInputStream(avatarFile));
				try {
					encodedAvatar = new String(Base64Coder.encode(Util.processImage(is, "png", FS2Constants.FS2_AVATAR_ICON_SIZE, FS2Constants.FS2_AVATAR_ICON_SIZE, ImageResizeType.NORATIO)));
					encodedAvatarMD5 = Util.md5(encodedAvatar);
					synchronized (nodes) {
						for (IndexNode node : nodes) node.notifyIndexNode();
					}
				} finally {
					is.close();
				}
			} catch (IOException e ) {
				Logger.warn("Avatar "+avatarFile.getPath()+" couldn't be loaded: "+e);
			}
		} else {
			encodedAvatar = null;
			encodedAvatarMD5 = null;
		}
	}
	
	public ShareServer getShareServer() {
		return ssvr;
	}
	
	public boolean isListeningForAdverts() {
		return listener!=null;
	}
	
	public IndexNodeCommunicator(ShareServer ssvr, Config conf) {
		this.ssvr = ssvr;
		this.conf = conf;
		HttpContext ping = ssvr.getHttpServer().createContext("/ping", new PingHandler(this));
		ping.getFilters().add(ssvr.getFS2Filter());
		ping.getFilters().add(inof);
		buildShareListXML();
		setupAvatar();
		iim = new InternalIndexnodeManager(this);
	}
	
	public Config getConf() {
		return conf;
	}

	public InternalIndexnodeManager getInternalIndexNode() {
		return iim;
	}
	
	public TableModel getStatsTableModel() {
		return statsTable;
	}
	
	/**
	 * Loads configured indexnodes, and sets up autoregistration (if needed)
	 * This must be done after starting the shareserver!
	 */
	public void loadConfiguredNodes() {
		for (String key : conf.getChildKeys(CK.INDEX_NODES)) {
			try {
				String url = conf.getString(key+"/path");
				if (url.equals("")) {
					Logger.warn("Ignoring empty indexnode URL in config.");
					continue;
				}
				registerNewIndexNode(new URL(url), 0, key);
			} catch (MalformedURLException e) {
				Logger.warn("IndexNode URL: '"+conf.getString(key+"/path")+"' specified in the configuration file is invalid.");
				e.printStackTrace();
			}
		}
		setupAdvertListeningIfNeeded();
	}

	void setupAdvertListeningIfNeeded() {
		autodetectIndexnodes = Boolean.parseBoolean(conf.getString(CK.AUTO_INDEX_NODE));
		if ((listener=AdvertListener.getAdvertListener(this))==null) {
			Logger.log("Indexnode advert listener couldn't be created. Autodetection and autohosting will be unavailable.\nIt's likely the port ("+FS2Constants.ADVERTISMENT_DATAGRAM_PORT+") is already in use,\nor you do not have permission to recieve UDP broadcasts.");
		}
	}
	
	/**
	 * Returns a filter that ensures only IndexNodes can access an HttpContext.
	 * @return
	 */
	public Filter getIndexNodeOnlyFilter() {
		return inof;
	}
	
	
	/**
	 * @return the indexnodes that we are communicating with presently.
	 */
	public ArrayList<IndexNode> getRegisteredIndexNodes() {
		return nodes;
	}
	
	/**
	 * Stop communicating with this indexnode and remove it from our configuration files.
	 * @param node Specifies an indexnode to stop communicating with.
	 */
	public void deregisterIndexNode(IndexNode node) {
		conf.deleteKey(node.confKey);
		int idx=-1;
		synchronized(nodes) {
			idx = nodes.indexOf(node);
			nodes.remove(node);
		}
		notifyIndexNodeRemoved(idx);
		node.shutdown();
	}
	
	/**
	 * Registers a new indexnode, as specified by it's URL
	 * Also places it into the configuration file.
	 * @param nodeURL the location of the new node.
	 */
	public void registerNewIndexNode(URL nodeURL) {
		registerNewIndexNode(nodeURL, 0, CK.INDEX_NODES+"/i"+nodeURL.hashCode());
	}

	/**
	 * Registers an indexnode.
	 * @param nodeURL
	 * @param advertuid the advertisment id of this indexnode, zero if it was manually/config added.
	 */
	private void registerNewIndexNode(URL nodeURL, long advertuid) {
		registerNewIndexNode(nodeURL, advertuid, CK.INDEX_NODES+"/i"+nodeURL.hashCode());
	}
	
	private void registerNewIndexNode(URL nodeURL, long advertuid, String configurationKey) {
		if (advertuid==0) conf.putString(configurationKey+"/path", nodeURL.toString()); //if it wasn't advertised.
		//due to awkwardness of the fs2 registration protocol the indexnode will add itself to the list of indexnodes.
		//(this is because the constructor registers with the server, which will call back, the indexnode server handler will reject unknown indexnodes however)
		new IndexNode(nodeURL, advertuid, ssvr, configurationKey);
		fs.notifyNewIndexnode();
	}
	
	/**
	 * Notifies this communicator that there's an active indexnode available.
	 * @param nodeURL
	 * @param advertuid
	 */
	void advertRecieved(URL nodeURL, long advertuid) {
		if (!autodetectIndexnodes) return; //ignore the advert if we're not configured to autodetect.
		synchronized (nodes) {
			for (IndexNode node : nodes) {
				if (node.advertuid == advertuid) return; //do nothing, we already have this node.
			}
		}
		//if we haven't returned, then it must be a new node:
		registerNewIndexNode(nodeURL, advertuid);
	}
	
	/**
	 * Determines if this client will automatically register with new advertising indexnodes.
	 * @return Returns true if adverts will now be listened to. Returns false if advert reception is unavailable.
	 */
	public void enableAdvertAcceptance() {
		conf.putString(CK.AUTO_INDEX_NODE, Boolean.TRUE.toString());
		autodetectIndexnodes = true;
	}
	
	public void disableAdvertAcceptance() {
		conf.putString(CK.AUTO_INDEX_NODE, Boolean.FALSE.toString());
		autodetectIndexnodes = false;
		synchronized (nodes) {
			for (IndexNode node : nodes.toArray(new IndexNode[]{})) { //copy the array to prevent modification exceptions.
				if (node.wasAdvertised()) deregisterIndexNode(node);
			}
		}
	}
	
	/**
	 * Shuts this indexnode communicator down gracefully.
	 */
	public void shutdown() {
		synchronized(nodes) {
			for (IndexNode sd : nodes) {
				sd.shutdown();
			}
			nodes.clear();
		}
		
		if (listener!=null) {
			listener.shutdown();
			listener=null;
		}
		
		chatRequestPool.shutdownNow();
		
		fs.shutdown();
	}
	
	/**
	 * Returns true if we are actively sharing onto any insecure indexnodes.
	 * The share server uses this to decide if it must reject insecure requests.
	 */
	public boolean peersNeedInsecure() {
		synchronized (nodes) {
			for (IndexNode node : nodes) if (node.isWritable() && !node.isSecure()) return true;
		}
		return false;
	}
	
	/**
	 * Returns true if we are connected to no secured indexnodes for reading.
	 * This is used by the download controller to decide if it's acceptable to use insecure requests.
	 * @return
	 */
	public boolean noPeersAreSecure() {
		synchronized (nodes) {
			for (IndexNode node : nodes) if (node.isReadable() && node.isSecure()) return false;
		}
		return true;
	}
	
	//-----------------------------------------------
	//Filesystem related tasks: (these are spread over each indexnode)
	// the result of browsing the root of a filesystem or searching will be the merged results from each indexnode.
	// Expansion of real directories will obviously be unique to a single indexnode.
	
	/**
	 * Returns a sorted (by filename) list of filesystem entries that are the children of the given parent.
	 * 
	 * The events for the new objects must obviously be generated by the caller. This function is just a scraper.
	 * 
	 * The utility in it being public is that it can be used as a lightweight way to query an indexnode synchronously and without caching.
	 * (ie, for queueing thousands of nested downloads without using the non-blocking FileSystem infrastructure)
	 */
	public LinkedList<FileSystemEntry> lookupChildren(FileSystemEntry parent) {
		LinkedList<FileSystemEntry> ret = new LinkedList<FileSystemEntry>();
		//If the indexnode is null then the result is the merge of all indexnodes we're registered with.
		if (parent.getIndexNode()==null) {
			LinkedList<IndexNode> cachedNodeList = new LinkedList<IndexNode>();
			synchronized (nodes) { //Don't hold the lock when doing such a long winded operation, but take a copy of the list with a lock:
				cachedNodeList.addAll(nodes);
			}
			if (parent.isSearch()) {
				for (IndexNode n : cachedNodeList) {
					if (n.isReadable()) {
						ret.addAll(n.lookupChildren(parent));
					}
				}	
			} else {
				HashMap<String, FileSystemEntry> deduped = new HashMap<String, FileSystemEntry>(); //remove duplicate aliases if this is a browse query with no indexnode (ie: root of all indexnodes)
				//the assumption is that aliases are globally unique... weak (read: impossible and certainly false)  but very convinient!
				for (IndexNode n : cachedNodeList) {
					if (n.isReadable()) {
						for (FileSystemEntry e : n.lookupChildren(parent)) {
							deduped.put(e.getName(), e);
						}
					}
				}
				ret.addAll(deduped.values());
			}
		} else {
			if (parent.getIndexNode().isReadable()) ret.addAll(parent.getIndexNode().lookupChildren(parent));
		}
		Collections.sort(ret);
		return ret;
	}
	
	/**
	 * Used by individual indexnode instances to indicate that they have a new peer available.
	 */
	void notifyNewPeersPresent() {
		synchronized (newPeerListeners) {
			for (NewPeerListener npl : newPeerListeners) {
				npl.newPeersPresent();
			}
		}
	}
	
	
	/**
	 * Registers the specified listener to be invoked whenever new peers are available on any connected indexnode.
	 */
	public void registerNewPeerListener(NewPeerListener npl) {
		synchronized (newPeerListeners) {
			newPeerListeners.add(npl);
		}
	}
	
	/**
	 * Generates a list of download sources for a given file. These sources can then be used to download files from peers.
	 * This is only intended for use by the download controller.
	 * @param hash the hash of the file to find sources for.
	 * @return a set of distinct download sources, indexed by their peer aliases.
	 */
	public Map<String, DownloadSource>getSourcesForFile(String hash) {
		HashMap<String, DownloadSource> sources = new HashMap<String, DownloadSource>();
		LinkedList<IndexNode> cachedNodeList = new LinkedList<IndexNode>();
		synchronized (nodes) { //Don't hold the lock when doing such a long winded operation, but take a copy of the list with a lock:
			cachedNodeList.addAll(nodes);
		}
		for (IndexNode n : cachedNodeList) {
			if (n.isReadable()) sources.putAll(n.getSources(hash));
		}
		return sources;
	}
	
	/**
	 * Returns the high-level filesystem expressed by these indexnodes.
	 * @return
	 */
	public FileSystem getFileSystem() {
		return fs;
	}
	
	//-----------------------------------------------
	//Used by the share server:
	
	/**
	 * Notify the indexnodes that the shares on this client have been updated somehow.
	 */
	public void sharesChanged() {
		//1) build a new filelist.xml
		buildShareListXML();
		//2) notify each indexnode.
		synchronized (nodes) {
			for (IndexNode node : nodes) {
				node.notifyIndexNode();
			}
		}
	}
	
	/**
	 * When called it will rate-limitedly and asynchronously destroy saved passwords and renegotiate with secured indexnodes.
	 */
	public void aliasChanged() {
		Util.scheduleExecuteNeverFasterThan(1000, aliasChangeHandler);
		iim.clientAliasChanged();
	}
	
	AliasChangeHandler aliasChangeHandler = new AliasChangeHandler();
	private class AliasChangeHandler implements Deferrable {
		@Override
		public void run() {
			Thread worker = new Thread(new Runnable() {
				@Override
				public void run() {
					synchronized (nodes) {
						for (IndexNode node : nodes) {
							node.setPassword("");
						}
					}
				}
			}, "alias change handler worker");
			worker.start();
		}
	}
	
	private void buildShareListXML() {
		try {
			Sxml result = new Sxml();
			Document doc = result.getDocument();
			Element root = doc.createElement("shares");
			doc.appendChild(root);
			synchronized (ssvr.getShares()) {
				for (Share thisShare : ssvr.getShares()) {
					if (thisShare.getStatus()==Status.BUILDING || thisShare.getStatus()==Status.ERROR) continue; //the file-list is valid in all other cases.
					Element next = doc.createElement("share");
					next.setAttribute("name", thisShare.getName());
					next.setAttribute("revision", Integer.toString(thisShare.getRevision()));
					next.setAttribute("type", "FileList");
					root.appendChild(next);
				}
			}
			synchronized (shareListXML) {
				shareListXML = result.toString();
			}
		}
		catch (Exception e) {
			Logger.severe("Error generating share list: "+e.toString());
			e.printStackTrace();
		}
	}

	private Class<?>[] columnClasses = {String.class, String.class, Date.class}; //Name, status, last-seen
	private String[] columnNames = {"Name", "Status", "Last-seen"};
	private static final int NAME_IDX=0;
	private static final int STATUS_IDX=1;
	private static final int LASTSEEN_IDX=2;
	
	
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
		return nodes.size();
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		IndexNode node;
		synchronized (nodes) {
			node = nodes.get(rowIndex);
		}
		if (columnIndex==NAME_IDX) {
			return node.getName();
		} else if (columnIndex==STATUS_IDX) {
			return node.getStatusDescription();
		} else if (columnIndex==LASTSEEN_IDX) {
			return node.getLastSeen();
		} else return "Column index invalid";
	}

	/**
	 * Gets the indexnode that is at rowIdx in the tablemodel.
	 * @param rowIdx
	 * @return
	 */
	public IndexNode getNodeForRow(int rowIdx) {
		synchronized (nodes) {
			return nodes.get(rowIdx);
		}
	}
	
	/**
	 * Gets the row index of the node in the table model.
	 * @param node
	 * @return
	 */
	public int getRowForNode(IndexNode node) {
		synchronized(nodes) {
			return nodes.indexOf(node);
		}
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
		//nothing can be changed.
	}
	
	void notifyIndexNodeInserted(final int idx) {
		if (idx<0) return;
		try {
			Utilities.dispatch(new Runnable() {
				@Override
				public void run() {
					synchronized (modelListeners) {
						for (TableModelListener l : modelListeners) {
							l.tableChanged(new TableModelEvent(IndexNodeCommunicator.this, idx, idx, TableModelEvent.ALL_COLUMNS, TableModelEvent.INSERT));
						}
					}
				}
			});
		} catch (Exception e) {
			Logger.warn("Exception dispatching notifyIndexNodeInserted(): "+e);
			e.printStackTrace();
		}
 	}
	
	void notifyIndexNodeRemoved(final int oldIndex) {
		if (oldIndex<0) return;
		try {
			Utilities.dispatch(new Runnable() {
				@Override
				public void run() {
					synchronized (modelListeners) {
						for (TableModelListener l : modelListeners) {
							l.tableChanged(new TableModelEvent(IndexNodeCommunicator.this, oldIndex, oldIndex, TableModelEvent.ALL_COLUMNS, TableModelEvent.DELETE));
						}
					}
				}
			});
		} catch (Exception e) {
			Logger.warn("Exception dispatching notifyIndexNodeRemoved(): "+e);
			e.printStackTrace();
		}
	}
	
	void notifyIndexNodeChanged(final int idx) {
		if (idx<0) return;
		try {
			Utilities.dispatch(new Runnable() {
				@Override
				public void run() {
					synchronized (modelListeners) {
						for (TableModelListener l : modelListeners) {
							l.tableChanged(new TableModelEvent(IndexNodeCommunicator.this, idx));
						}
					}
				}
			}, false);
		} catch (Exception e) {
			Logger.warn("Exception dispatching notifyIndexNodeChanged(): "+e);
			e.printStackTrace();
		}
	}
}
