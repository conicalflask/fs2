package client.indexnode.downloadcontroller;


import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.ExecutorService;

/**
 * The superclass of all download workers and a factory for generating them.
 * 
 * It uses a chunk-splitting approach to multipart downloads,
 * where a download in progress may be split into two downloads at any point but never merged back together.
 * 
 * Supporting multipart downloads is optional.
 * A worker signals that it does not support multipart downloads by returning null from splitChunk()
 * 
 * @author gary
 */
public abstract class DownloadWorker {

	DownloadDispatcher dispatch;
	DownloadInfo info;
	
	DownloadWorker(DownloadDispatcher dispatch, DownloadInfo info) {
		this.dispatch = dispatch;
		this.info = info;
	}
	
	/**
	 * Returns an implementation of a download worker, the worker will have as many chunks as the info specifies.
	 * 
	 * The returned worker will be idle.
	 * 
	 * @param dispatch
	 * @param info the information about this download. It may already contain chunks are in any state of completion.
	 * @return
	 * @throws IOException 
	 */
	public static DownloadWorker getDownloadWorker(DownloadDispatcher dispatch, DownloadInfo info) throws IOException {
		return new URLConnWorker(dispatch, info);
	}
	
	/**
	 * Returns the number of live chunkworkers associated with this download worker.
	 * 
	 * @return
	 */
	public abstract int getActiveChunkCount();
	
	/**
	 * Kills a live chunk on this download worker.
	 */
	public abstract void killAChunk();
	
	/**
	 * Instructs this download worker to split the download chunk into two equal parts, the new chunk is returned.
	 * 
	 * this will also insert the new chink into the info object.
	 * 
	 * @param chunk
	 * @param newSource
	 * @return the new chunk, or null if this worker does not support multipart downloads.
	 */
	public abstract DownloadChunk splitChunk(DownloadChunk chunk);
	
	/**
	 * Instructs this worker to begin downloading the chunk specified.
	 * The chunk may chose to use the pool provided to schedule execution.
	 */
	public abstract void downloadChunk(DownloadChunk chunk, DownloadSource source, ExecutorService pool);
	
	/**
	 * Returns a collection of chunks in this download that are not complete but also are not being 'worked' on.
	 * 
	 * Presumably this will be used by a dispatcher to issue an inactive chunk to download when a chunk causes "download finished" and was complete.
	 * 
	 * Will also presumably return an empty collection if this worker is shutdown.
	 * @return
	 */
	public abstract Collection<DownloadChunk> getIncompleteInactiveChunks();
	
	/**
	 * Returns the chunks that are currently active, this only includes actively downloading chunks, not remotely queued chunks.
	 * @return
	 */
	public abstract Collection<DownloadChunk> getDownloadingChunks();
	
	/**
	 * Kills all live chunkworkers
	 */
	public abstract void shutdown();
	
	/**
	 * Shuts down this download and deletes any temporary files if needed.
	 */
	public abstract void cancel();

	/**
	 * returns true if this worker is shutdown
	 * @return
	 */
	public abstract boolean isShutdown();
	
	/**
	 * Returns true if this worker has active workers and all of them are secure.
	 * @return
	 */
	public abstract boolean isSecure();
}
