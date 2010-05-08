package client.indexnode.downloadcontroller;

public interface DownloadEvents {
	
	/**
	 * A chunk from a remote peer has started to download
	 * @param chunk
	 * @throws InterruptedException 
	 */
	public void chunkStarted(DownloadChunk chunk) throws InterruptedException;

	/**
	 * Notification that some bytes have been downloaded from a peer
	 * @param chunk
	 * @param byteCount the number of bytes transferred
	 */
	public void chunkTransfer(DownloadChunk chunk, long byteCount);
	
	/**
	 * A chunk has stopped downloading. There is no differentiation between ending and completing successfully.
	 * @param chunk
	 * @throws InterruptedException 
	 */
	public void chunkEnded(DownloadChunk chunk) throws InterruptedException;
	
	/**
	 * Notfies us that a chunk is waiting remotely
	 * @param chunk
	 */
	public void chunkQueued(DownloadChunk chunk);
	
	/**
	 * Notfies us that a chunk has finished waiting. (either has become active or terminated...)
	 * @param chunk
	 */
	public void chunkUnqueued(DownloadChunk chunk);
	
}
