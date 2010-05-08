package client.indexnode.downloadcontroller;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import client.indexnode.downloadcontroller.DownloadQueue.DownloadFile;
import client.indexnode.downloadcontroller.DownloadQueue.DownloadItem;
import client.shareserver.ThrottledFileDigester;

import common.FS2Constants;
import common.Logger;
import common.NamedThreadFactory;
import common.Util;

/**
 * Provided with a worker factory and a queue this class manages active workers,
 * and intelligently submits queued downloads to start.
 * 
 * This is quite complex, as it has to manage contention for remote slots, and local slots.
 * 
 * @author gary
 */
public class DownloadDispatcher {
	
	/**
	 * Handle events caused by download workers, pass most onto the controller but also do more stuff on chunk completion.
	 * @author gary
	 *
	 */
	class DispatchEvents implements DownloadEvents {

		@Override
		public void chunkEnded(DownloadChunk chunk) throws InterruptedException {
			if (chunk.isComplete()) {
				//look for other chunks in this download that are idle and dispatch them with this chunk's source:
				dispatchAnIncompleteInactiveChunk(chunk.owner.worker, chunk.source);
			}
			
			removeIdleWorker(chunk.owner.worker);
			
			controller.events.chunkEnded(chunk);
		}

		@Override
		public void chunkStarted(DownloadChunk chunk) throws InterruptedException {
			controller.events.chunkStarted(chunk);
		}

		@Override
		public void chunkTransfer(DownloadChunk chunk, long byteCount) {
			controller.events.chunkTransfer(chunk, byteCount);
		}
		
		@Override
		public void chunkQueued(DownloadChunk chunk) {
			controller.events.chunkQueued(chunk);
		}
		
		@Override
		public void chunkUnqueued(DownloadChunk chunk) {
			controller.events.chunkUnqueued(chunk);
		}
		
	}
	
	int maxActiveSlots = 0;
	int maxSlotsPerFile = 0;
	Thread dispatcher;
	volatile boolean mustShutdown = false;
	DownloadController controller;
	DispatchEvents events = new DispatchEvents();
	private ExecutorService downloadThreadPool = Executors.newCachedThreadPool(new NamedThreadFactory(true, "download"));
	
	HashSet<DownloadWorker> workers = new HashSet<DownloadWorker>();
	
	/**
	 * Tests to see if the specified worker has no active chunks and cleans it up if it is idle.
	 * 
	 * If the download is also complete, then it is removed from the queue.
	 * 
	 * Has no effect on an active worker.
	 * @param w
	 */
	private synchronized void removeIdleWorker(DownloadWorker w) {
		if (w==null) return; //the worker has already been cleared up.
		if (w.getActiveChunkCount()==0) {
			w.shutdown();
			workers.remove(w);
			w.info.worker = null;
			//if any chunks are still incomplete then leave the file in the queue.
			if (w.info.isComplete()) {
				//if we're here then all were complete:
				w.info.file.downloadComplete();
				//on a completion notify the dispatch loop that it's ok to dispatch now:
				this.notify();
			}
		}
	}
	
	/**
	 * Ensures slot limitations are not being violated.
	 */
	private synchronized void clipDownloadCount() {
		//1) enforce all active slots limit:
		while (getActiveSlotCount()>maxActiveSlots) {
			for (DownloadWorker w : workers) { //use the iterator to get an arbitrary worker
				w.killAChunk();
				if (!(getActiveSlotCount()>maxActiveSlots)) break;
			}
		}
		//2) enforce the active slots per file limit: It is not necessary to clear up idle workers at this stage as maxSlotsPerFile must be at least one, so removal of a chunkworker means there must have been at least two to begin with.
		for (DownloadWorker w : workers) {
			while (w.getActiveChunkCount()>maxSlotsPerFile) {
				w.killAChunk();
			}
		}
	}
	
	/**
	 * The number of active chunk downloads right now
	 * @return
	 */
	private synchronized int getActiveSlotCount() {
		int ret=0;
		for (DownloadWorker w : workers) {
			ret+=w.getActiveChunkCount();
		}
		return ret;
	}
	
	/**
	 * Checks to see if a chunk is suitable to split.
	 * for this to be true:
	 * 1) The chunk must have been active for longer than CLIENT_DOWNLOAD_MIN_SPLIT_INTERVAL.
	 * 2) The chunk's complete proportion must be less than CLIENT_DOWNLOAD_CHUNK_SPLIT_PROPORTION
	 * 
	 * @return
	 */
	private boolean chunkEligableForSplitting(DownloadChunk c) {
		return (c.getTimeSinceLastSplit()>FS2Constants.CLIENT_DOWNLOAD_MIN_SPLIT_INTERVAL &&
				c.getCompletePercent()<FS2Constants.CLIENT_DOWNLOAD_CHUNK_SPLIT_PERCENT);
	}
	
	/**
	 * Finds active chunks that could be efficiently split into two peices, and then does that if individual file slots permit.
	 * 
	 * This will only dispatch one chunk per call.
	 */
	private synchronized void chunkWorkers() {
		for (DownloadWorker w : workers) {
			if (w.isShutdown()) continue;
			
			 //0) check this worker is eligable for dispatch of another chunk:
			if (!(w.getActiveChunkCount()<maxSlotsPerFile) || w.info.file.hasNoSources()) continue; //no point trying if there are no free slots for this file or there are no sources.
			
			//1) dispatch inactive incomplete chunks:
			if (dispatchAnIncompleteInactiveChunk(w, null)) return; //return, only one per call.
			
			//2) split existing active chunks:
			for (DownloadChunk c : w.getDownloadingChunks()) {
				if (chunkEligableForSplitting(c)) {
					//It's appropriate to split this chunk so:
					//1) get the sources for this download:
					Map<String, DownloadSource> sources = controller.ssvr.getIndexNodeCommunicator().getSourcesForFile(c.owner.file.hash);
					if (sources.isEmpty()) {
						c.owner.file.notifyNoSources();
					} else {
						//2) split the download:
						DownloadChunk nextChunk = w.splitChunk(c);
						if (nextChunk!=null) {
							//woo, worker supports chunks! now start the chunk on the best available peer:
							w.downloadChunk(nextChunk, controller.peerstats.getBestSource(sources), downloadThreadPool);
							return; //only one per call.
						}
					}
				}
			}
		}
	}
	
	/**
	 * Does what it says.
	 * @param w The worker that might have a chunk to dispatch.
	 * @param source An optional source to use if specified. If null a new source will be located if possible.
	 * @return true iff an inactive chunk was restarted.
	 */
	private synchronized boolean dispatchAnIncompleteInactiveChunk(DownloadWorker w, DownloadSource source) {
		for (DownloadChunk c : w.getIncompleteInactiveChunks()) {
			if (source==null) {
				Map<String, DownloadSource> sources = controller.ssvr.getIndexNodeCommunicator().getSourcesForFile(c.owner.file.hash);
				if (sources.isEmpty()) {
					c.owner.file.notifyNoSources();
					return false; //do nothing if there are no sources available.
				}
				source = controller.peerstats.getBestSource(sources);
			}
			w.downloadChunk(c, source, downloadThreadPool);
			return true;
		}
		return false;
	}
	
	/**
	 * Finds a file that is not being serviced by a download worker and starts to download it.
	 * 
	 * @return true iff there was a file in the queue to consider, false otherwise.
	 * 
	 */
	private synchronized boolean dispatchFile() {
		//1) establish the active directory:
		DownloadFile nF = controller.q.getInactiveDownloadFile();
		if (nF!=null) {
			//Logger.log("attempting to dispatch: "+nF);
			//start it downloading...
			//1) find a source for the download:
			Map<String, DownloadSource> sources = controller.ssvr.getIndexNodeCommunicator().getSourcesForFile(nF.hash);
			if (sources.isEmpty()) {
				nF.notifyNoSources();
			} else {
				startFileDownloading(nF, controller.peerstats.getBestSource(sources));
			}
			return true;
		} else {
			//Logger.log("Nothing to dispatch.");
			return false;
		}
	}
	
	/**
	 * Checks if the downloadFile specified has been completed already.
	 * This returns true iff:
	 * ) file exists
	 * ) is expected size
	 * ) hashes to the same fs2 hash.
	 * @param f
	 * @return
	 */
	private boolean downloadFileCompleteOnDisk(DownloadFile f) {
		try {
			File onDisk = f.getFile();
			if (!onDisk.isFile()) return false;
			if (onDisk.length()!=f.size) return false;
			if (!ThrottledFileDigester.fs2DigestFile(onDisk, null).equals(f.hash)) return false;
		} catch (Exception e) {
			Logger.warn("Couldn't test for file completion on disk: "+e);
			e.printStackTrace();
			return false;
		}
		
		return true; //it's already complete!
	}
	
	/**
	 * Given a file and a source this will start a worker-less file downloading.
	 * @param nF
	 * @param bestSource
	 */
	private void startFileDownloading(DownloadFile nF, DownloadSource bestSource) {
		//first ensure that the file is not already complete on disk:
		if (downloadFileCompleteOnDisk(nF)) {
			Logger.warn("Complete download: "+nF+" was in the queue. Issueing completion.");
			nF.downloadComplete();//issue completion
			wereCompleteItemsInQueue = true;
			return;
		}
		
		//Create a new info container for the file (if it has never been started before, or if for some reason there are no chunks in it (corrupt for some reason? broken code in the past probably))
		if (nF.active==null) {
			if (nF.getSize()==0) {
				//zero byte files do not need downloading. (and indeed they will fail to dispatch correctly as they will never have any incomplete chunks)
				try {
					nF.getFile().createNewFile();
					nF.downloadComplete();
					wereCompleteItemsInQueue = true;
					return;
				} catch (IOException e) {
					Logger.severe("Empty file: "+nF.getFile().getPath()+" couldn't be created on disk: "+e);
				}
			}
			nF.active = new DownloadInfo(nF);
		} else {
			if (nF.active.isComplete()) {
				Logger.warn("Complete download: "+nF+" was in the queue. Issueing completion.");
				nF.downloadComplete();
				wereCompleteItemsInQueue = true;
				return;
			} else if (nF.active.chunks.size()==0){
				Logger.warn("Active download '"+nF+"' in queue with no chunks. Restarting download from the beginning.");
				nF.active = new DownloadInfo(nF);
			}
		}
		
		try {
			//Create the new worker:
			DownloadWorker w = DownloadWorker.getDownloadWorker(this, nF.active);
			nF.active.worker = w;
			workers.add(w);
			
			
			//Start one of the download chunks present in the info:
			for (DownloadChunk c : w.getIncompleteInactiveChunks()) {
				w.downloadChunk(c, bestSource, downloadThreadPool);
				break;
			}
		} catch (IOException e) {
			Logger.severe("Unable to dispatch a download: "+e);
			e.printStackTrace();
		}
	}
	
	/**
	 * complete items then this allows the dispatcher to skip the throttle for this iteration.
	 */
	private boolean wereCompleteItemsInQueue = false; 
	private void dispatchLoop() {
		while (true) {
			long lastIteration = System.currentTimeMillis();
			
			try {
				//Logger.log("dispatch: "+lastIteration);
				
				if (mustShutdown) return;
				//1) kill downloads over the limits
				clipDownloadCount();
				
				if (mustShutdown) return;
				
				int canQueue = maxActiveSlots-getActiveSlotCount();
				 //only consider dispatching another if there are free slots:
				while (canQueue>0) {
					//2) dispatch another file: (this means we prefer to give slots to new files rather than split existing chunks)
					if (!dispatchFile()) break; //stop iterating if the queue is empty.
					
					if (mustShutdown) return;
					canQueue--;
				}

				if (mustShutdown) return;
				
				if (canQueue>0) { //if there are potentially still slots:
					//3) find chunks ripe for splitting and split and dispatch them:
					chunkWorkers();
				}
				
				if (mustShutdown) return;
				
			} catch (Exception e) {
				Logger.warn("Dispatch iteration failed: "+e);
				e.printStackTrace();
			}

			//Throttle this loop so that it does not spin exceedinly fast if there is nothing to do.
			long now = System.currentTimeMillis();
			long remaining = (lastIteration+FS2Constants.CLIENT_DOWNLOAD_DISPATCH_ITERATION_THROTTLE)-now;
			try {
				if (remaining>0 && !wereCompleteItemsInQueue) {
					synchronized (this) {
						this.wait(remaining);
					}
				}
				wereCompleteItemsInQueue = false;
			} catch (InterruptedException e) {
				if (mustShutdown) return;
			}
		}
	}
	
	public DownloadDispatcher(DownloadController controller) {
		this.controller = controller;
		
		controller.ssvr.getIndexNodeCommunicator().registerNewPeerListener(controller.q); //ensure the queue knows when new peers have arrived.
		
		dispatcher = new Thread(new Runnable() {
			@Override
			public void run() {
				dispatchLoop();
			}
		}, "download dispatcher");
		dispatcher.setDaemon(true);
		dispatcher.start();
		
	}
	
	public int getMaxActiveSlots() {
		return maxActiveSlots;
	}
	
	public void setMaxActiveSlots(int maxActiveSlots) {
		this.maxActiveSlots = maxActiveSlots;
	}
	
	public int getMaxSlotsPerFile() {
		return maxSlotsPerFile;
	}
	
	public void setMaxSlotsPerFile(int maxSlotsPerFile) {
		this.maxSlotsPerFile = maxSlotsPerFile;
	}
	
	public synchronized void shutdown() {
		mustShutdown = true;
		dispatcher.interrupt();
		for (DownloadWorker w : workers) w.shutdown();
		downloadThreadPool.shutdown();
	}

	/**
	 * Called by the queue to notify us that some chunk of the download queue has been erased
	 * so this should un-dispatch those items contained within it.
	 */
	synchronized void queueItemCancelled(DownloadItem cancelled) {
		for (DownloadWorker w : workers) {
			try {
				if (Util.isWithin(w.info.file.getFile(), cancelled.getFile())) {
					w.cancel();
				}
			} catch (IOException e) {
				Logger.severe("Couldn't determine file locations: "+e);
				e.printStackTrace();
			}
		}
	}

	/**
	 * Stops all current downloads so that new items can be dispatched. This does not discard any data.
	 */
	public synchronized void requeue() {
		for (DownloadWorker w : workers) w.shutdown();
	}
	
}
