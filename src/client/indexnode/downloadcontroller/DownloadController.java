package client.indexnode.downloadcontroller;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashSet;

import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;

import common.FS2Constants;
import common.Logger;
import common.ProgressTracker;
import common.Util;
import common.Util.Deferrable;
import common.Util.FileSize;

import client.gui.Utilities;
import client.indexnode.PeerStatsCollector;
import client.indexnode.downloadcontroller.DownloadQueue.DownloadFile;
import client.platform.ClientConfigDefaults.CK;
import client.shareserver.BandwidthSharerImpl;
import client.shareserver.ShareServer;
import client.shareserver.BandwidthSharer;

/**
 * Coordinates downloads on the client, this involves management of a set of queues, download workers, and a download dispatcher.
 * This is mostly just glue and might not really need to exist.
 * 
 * This also implements a tablemodel to drive a table of current download chunks.
 * 
 * @author gary
 */
public class DownloadController implements TableModel {
	
	DownloadQueue q;
	ShareServer ssvr;
	PeerStatsCollector peerstats;
	ProgressTracker allDownload = new ProgressTracker(); //the root tracker of all downloads.
	BandwidthSharer throttle = new BandwidthSharerImpl();
	DownloadDispatcher dispatch;
	
	public ProgressTracker getDownloadTracker() {
		return allDownload;
	}
	
	public DownloadController(ShareServer ssvr) {
		this.ssvr = ssvr;
		this.peerstats = ssvr.getPeerstats();
		q = DownloadQueue.getDownloadQueue(ssvr.getIndexNodeCommunicator(), this);
		
		allDownload.setExpectedMaximum(q.calculateSize());
		
		throttle.setBytesPerSecond(ssvr.getConf().getLong(CK.DOWNLOAD_BYTES_PER_SEC));
		
		dispatch = new DownloadDispatcher(this);
		dispatch.setMaxActiveSlots(getDownloadSlots());
		dispatch.setMaxSlotsPerFile(getMaxSlotsPerFile());
	}
	
	/**
	 * returns the configured maximum download speed.
	 * @return
	 */
	public long getDownloadSpeed() {
		return ssvr.getConf().getLong(CK.DOWNLOAD_BYTES_PER_SEC);
	}
	
	public void setDownloadSpeed(long newBytesPerSecond) {
		throttle.setBytesPerSecond(newBytesPerSecond);
		ssvr.getConf().putLong(CK.DOWNLOAD_BYTES_PER_SEC, newBytesPerSecond);
	}
	
	public int getDownloadSlots() {
		return ssvr.getConf().getInt(CK.ACTIVE_DOWNLOADS);
	}
	
	public int getMaxSlotsPerFile() {
		return ssvr.getConf().getInt(CK.ACTIVE_DOWNLOADS_PER_FILE);
	}
	
	public void setDownloadSlots(int newSlots) {
		ssvr.getConf().putInt(CK.ACTIVE_DOWNLOADS, newSlots);
		dispatch.setMaxActiveSlots(newSlots);
	}
	
	public void setMaxSlotsPerFile(int newSlotspf) {
		ssvr.getConf().putInt(CK.ACTIVE_DOWNLOADS_PER_FILE, newSlotspf);
		dispatch.setMaxSlotsPerFile(newSlotspf);
	}
	

	public File getDefaultDownloadDirectory() {
		return new File(ssvr.getConf().getString(CK.DOWNLOAD_DIRECTORY));
	}
	
	public void setDefaultDownloadDirectory(File newDir) {
		ssvr.getConf().putString(CK.DOWNLOAD_DIRECTORY, newDir.getAbsolutePath());
	}
	
	public void shutdown() {
		dispatch.shutdown();
		q.shutdown();
	}
	
	/**
	 * Gets the download queue for this controller
	 * @return
	 */
	public DownloadQueue getQueue() {
		return q;
	}
	

	/**
	 * Notifies the peerstats collector of interesting events,
	 * maintains the chunk transfers table,
	 * and informs the queue tree when a file might need redrawing. (this is passed off to a periodic chunk updater)
	 */
	public class ControllerEvents implements DownloadEvents {

		@Override
		public void chunkEnded(DownloadChunk chunk) throws InterruptedException {
			peerstats.downloadComplete(chunk.source.peerAlias);
			chunkAddedRemoved(chunk, false);
		}

		@Override
		public void chunkStarted(DownloadChunk chunk) throws InterruptedException {
			peerstats.downloadStarted(chunk.source.peerAlias);
			chunkAddedRemoved(chunk, true);
		}

		@Override
		public void chunkTransfer(DownloadChunk chunk, long byteCount) {
			peerstats.receivedBytes(chunk.source.peerAlias, byteCount, chunk.getInterval());
			q.saver.requestSave(); //should periodically save where we are in big files....
			chunkChanged(chunk);
		}
		
		@Override
		public void chunkQueued(DownloadChunk chunk) {
			peerstats.peerQueuedUs(chunk.source.peerAlias);
			chunkChanged(chunk);
		}
		
		@Override
		public void chunkUnqueued(DownloadChunk chunk) {
			peerstats.peerUnqueuedUs(chunk.source.peerAlias);
			chunkChanged(chunk);
		}
	}
	
	
	//======================
	// table model stuff:
	//======================
	
	
	/**
	 * Asynchronously dispatch events to swing to update the chunk transfer table.
	 * @param ev
	 * @throws InterruptedException 
	 */
	private void chunkAddedRemoved(final DownloadChunk chunk, final boolean added) throws InterruptedException {
		chunk.owner.file.updateThis(); //update in the tree.
		
		try {
			Utilities.dispatch(new Runnable() {
				@Override
				public void run() {
					synchronized (tableChunks) {
						synchronized (listeners) {
							for (TableModelListener l : listeners) {
								int idx;
								if (added) {
									tableChunks.add(chunk);
									idx = tableChunks.size()-1;
								} else {
									idx = tableChunks.indexOf(chunk);
									tableChunks.remove(idx);
								}
								l.tableChanged(new TableModelEvent(DownloadController.this, idx, idx, TableModelEvent.ALL_COLUMNS, (added ? TableModelEvent.INSERT : TableModelEvent.DELETE)));
							}
						}
					}
				}
			});
		} catch (InterruptedException e) {
			throw e;
		} catch (InvocationTargetException e) {
			Logger.log(e);
		}
		
	}
	
	
	HashSet<DownloadChunk> chunksToUpdate = new HashSet<DownloadChunk>();
	/**
	 * Indicates that a chunk has changed and that the gui must be updated, but this will be done at some point in the future.
	 * All chunks changed between now and then will be updated at once.
	 * @param chunk
	 */
	void chunkChanged(DownloadChunk chunk) {
		synchronized (chunksToUpdate) {
			chunksToUpdate.add(chunk);
		}
		Util.scheduleExecuteNeverFasterThan(FS2Constants.CLIENT_EVENT_MIN_INTERVAL, chunkUpdater);
		chunk.owner.file.updateThis(); //notify the tree. (this is throttled elsewhere)
	}
	
	private ChunkUpdater chunkUpdater = new ChunkUpdater();
	private class ChunkUpdater implements Deferrable {
		@Override
		public void run() {
			Utilities.edispatch(new Runnable() { //run in swing thread, synchronously.
				@Override
				public void run() {
					synchronized (chunksToUpdate) {
						synchronized (tableChunks) {
							for (int i=0; i<tableChunks.size(); i++) {
								if (chunksToUpdate.contains(tableChunks.get(i))) {
									for (TableModelListener l : listeners) {
										l.tableChanged(new TableModelEvent(DownloadController.this, i));
									}
								}
							}
						}
						chunksToUpdate.clear();
					}
				}
			});
		}
	}
	
	DownloadEvents events = new ControllerEvents();
	ArrayList<TableModelListener> listeners = new ArrayList<TableModelListener>();
	ArrayList<DownloadChunk> tableChunks = new ArrayList<DownloadChunk>(); //the data to drive the table model.
	String[] columnNames = {"File name", "Peer", "Status", "Progress", "Speed", "ETR"};
	Class<?>[] columnClasses = {String.class, String.class, String.class, DownloadChunk.class, FileSize.class, String.class};
	public static final int FILENAME_IDX = 0;
	public static final int PEER_IDX = 1;
	public static final int STATUS_IDX = 2;
	public static final int PROGRESS_IDX = 3;
	public static final int SPEED_IDX = 4;
	public static final int ETR_IDX = 5;
	
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
		return columnNames.length;
	}

	@Override
	public String getColumnName(int columnIndex) {
		return columnNames[columnIndex];
	}

	@Override
	public int getRowCount() {
		return tableChunks.size();
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		DownloadChunk c = tableChunks.get(rowIndex);
		switch (columnIndex) {
		case FILENAME_IDX:
			return c.owner.file.getName();
		case PEER_IDX:
			return c.source.peerAlias;
		case STATUS_IDX:
			return (c.isSecure() ? "Secure " : "")+c.status.toString().toLowerCase();
		case PROGRESS_IDX:
			return c; //The table cell renderer should translate this into a progress bar or something nice.
		case SPEED_IDX:
			return new FileSize((long) c.chunkTracker.getSpeed(),true);
		case ETR_IDX:
			return c.chunkTracker.describeTimeRemaining();
		}
		return null;
	}
	
	public DownloadFile getFileForRow(int rowIdx) {
		return tableChunks.get(rowIdx).owner.file;
	}

	@Override
	public boolean isCellEditable(int rowIndex, int columnIndex) {
		return false; //nothing is user editable in this table.
	}

	@Override
	public void removeTableModelListener(TableModelListener l) {
		synchronized (listeners) {
			listeners.remove(l);
		}
	}

	@Override
	public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
		//Nothing to do, everything is read only.
	}

	/**
	 * Re-dispatches all downloads from the queue. Useful if priorities have changed.
	 */
	public void requeue() {
		dispatch.requeue();
	}

	public void recalculateRemaining() {
		allDownload.setPosition(0);
		allDownload.setExpectedMaximum(q.calculateSize());
	}
}
