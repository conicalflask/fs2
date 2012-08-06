package client.indexnode.downloadcontroller;

import java.io.Serializable;
import java.util.ArrayList;

import client.indexnode.downloadcontroller.DownloadQueue.DownloadFile;

import common.ProgressTracker;

public class DownloadInfo implements Serializable {
    private static final long serialVersionUID = 6780128212791095292L;
    
    /**
     * Create a new DownloadInfo with one chunk filling the whole file.
     * @param file
     */
    public DownloadInfo(DownloadFile file) {
		this.file = file;
		
		DownloadChunk c = new DownloadChunk(this);
		c.startByte = 0;
		c.position = 0;
		c.endByte = file.size-1;
		chunks.add(c);
	}
    
	//saved members
	final ArrayList<DownloadChunk> chunks = new ArrayList<DownloadChunk>();
	final DownloadFile file;
	
	//transients:
	transient DownloadWorker worker;  //the dispatcher modifies this field.
	transient ProgressTracker fileProgress; //it is the worker's responsibility to maintain this field.
	transient boolean error; //if true then this file failed recently.
	transient String errorDescription; //Used to describe an error if one exists for this file.
	
	/**
	 * returns true if all the data has finished for this chunk and there is no error still active.
	 * @return
	 */
	public synchronized boolean isComplete() {
		if (error) return false;
		for (DownloadChunk c : chunks) {
			if (!c.isComplete()) return false;
		}
		return true;
	}
	
	/**
	 * returns the number of bytes downloaded so far
	 */
	public synchronized long bytesDownloaded() {
		long ret=0;
		for (DownloadChunk c : chunks) {
			ret+=(c.getDownloadedBytes());
		}
		return ret;
	}
	
	/**
	 * Returns the number if bytes remaining to download for this file.
	 * @return
	 */
	public long bytesRemaining() {
		return file.size-bytesDownloaded();
	}
	
	public ArrayList<DownloadChunk> getChunks() {
		return chunks;
	}
	
	public DownloadFile getFile() {
		return file;
	}
}
