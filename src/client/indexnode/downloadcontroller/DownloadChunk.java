package client.indexnode.downloadcontroller;

import java.io.Serializable;

import common.ProgressTracker;

public class DownloadChunk implements Serializable {
    private static final long serialVersionUID = 6410087525405124042L;

	public enum Status {CONNECTING, QUEUED, DOWNLOADING, ALLOCATING};
    
    public DownloadChunk(DownloadInfo owner) {
		this.owner = owner;
	}
    
	final DownloadInfo owner;
	
	//record information about this chunk's place in the owner file: (needed to resume the file download later)
	long startByte;  			//inclusive (0 is first byte's index)
	volatile long endByte;    	//inclusive (file.size()-1 is the endByte for a whole file)
	volatile long position;   	//the index of the next byte to be recieved, is endByte+1 after the chunk has completed. This is relative to the WHOLE FILE not just this chunk!
	
	//where this chunk is currently coming from:
	transient DownloadSource source;
	
	transient long lastCheckedTime;  // the worker must set this when it sets 'started'
	transient long lastSplit; //when this chunk began to download (the time the chunkStartedEvent was issued)
	transient ProgressTracker chunkTracker;   //it is the worker's responsibility to maintain this field!
	transient Status status; //obviously, worker controlled.
	transient boolean secure; //true when the download is in progress and secure.
	
	/**
	 * Returns the number of milliseconds since this method was last called,
	 * or this object was created, whichever is less.
	 * 
	 * This can be used to find the time a transfer took, incrementally.
	 * 
	 * @return
	 */
	public long getInterval() {
		long now = System.currentTimeMillis();
		long ret = now-lastCheckedTime;
		lastCheckedTime = now;
		return ret;
	}
	
	public boolean isComplete() {
		return position>endByte;
	}
	
	public long getTimeSinceLastSplit() {
		return System.currentTimeMillis()-lastSplit;
	}
	
	public float getCompletePercent() {
		return chunkTracker.percentComplete();
	}

	public boolean isWholeFile() {
		return (startByte==0 && endByte==owner.file.size-1);
	}
	
	void setTrackerExpectedMaximum() {
		chunkTracker.setExpectedMaximum((endByte-startByte)+1);
		chunkTracker.setPosition(getDownloadedBytes());
	}
	
	public long getDownloadedBytes() {
		return position-startByte;
	}

	public DownloadInfo getOwner() {
		return owner;
	}
	
	public long getStartByte() {
		return startByte;
	}
	
	public long getPosition() {
		return position;
	}
	
	public long getEndByte() {
		return endByte;
	}
	
	public boolean isSecure() {
		return this.status==Status.DOWNLOADING&&secure;
	}
	
}
