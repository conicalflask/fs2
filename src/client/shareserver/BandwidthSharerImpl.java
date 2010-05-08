package client.shareserver;

/**
 * The BandwidthSharer provides a simple API to enable a fixed throughput (bandwidth) of bytes
 * to be fairly shared between multiple throughput consumers.
 * 
 * Aside from configuration methods, the principle method is getBytes.
 * Calls to getBytes are blocked until bytes can be allocated such that if the caller uses them
 * the bitrate will not exceede the rate this object is configured for. 
 * 
 * This version is threadless! :o (it only computes in caller's threads!)
 * 
 * getBytes is threadsafe, this class is now quite elegant.
 * 
 * @author gary
 */
public class BandwidthSharerImpl implements BandwidthSharer{

	int allocationInterval = 100; //The number of milliseconds between each bandwidth allocation.
	long maxAllocation = 1024L; //The number of bytes that may be allocated at most, per allocation.
	
	//Represents the number of future allocations already consumed.
	Integer futuresConsumed = 0;
	//the most recent time a future allocatoin became a real allocation:
	long mostRecentConsumedDecrement = System.currentTimeMillis();
	
	//the amount of remaining bytes for the next future
	long remainingBytes = maxAllocation;
	
	@Override
	public int getAllocationInterval() {
		return this.allocationInterval;
	}

	@Override
	public long getBytes(long request) throws InterruptedException {
		
		boolean iConsumedAFuture = false;
		long allocation;
		long waitUntil;
		
		synchronized (futuresConsumed) {
			
			if (remainingBytes==0) {
				remainingBytes = maxAllocation;
				iConsumedAFuture = true;
				futuresConsumed++;
			}
			
			allocation = (request>remainingBytes ? remainingBytes : request);
			remainingBytes -= allocation;
			
			waitUntil = mostRecentConsumedDecrement+(futuresConsumed*allocationInterval);
		}

		long now;
		while (waitUntil>(now=System.currentTimeMillis())) {
			Thread.sleep(waitUntil-now);
		}
		
		if (iConsumedAFuture) {
			synchronized (futuresConsumed) {
				futuresConsumed--;
				mostRecentConsumedDecrement = System.currentTimeMillis();
			}
		}
		
		return allocation;
	}

	@Override
	public long getMaxAllocation() {
		return maxAllocation;
	}

	@Override
	public void setAllocationInterval(int allocationPeriod) {
		this.allocationInterval = allocationPeriod;
	}

	/* (non-Javadoc)
	 * @see client.shareserver.BandwidthSharerI#getBytesPerSecond()
	 */
	public long getBytesPerSecond() {
		return new Double(maxAllocation / ((new Double(allocationInterval))/1000d) ).longValue();
	}
	
	/* (non-Javadoc)
	 * @see client.shareserver.BandwidthSharerI#setBytesPerSecond(long)
	 */
	public void setBytesPerSecond(long bytes) {
		maxAllocation = new Double(bytes * ((new Double(allocationInterval))/1000d) ).longValue();
	}

	@Override
	public void setMaxAllocation(long maxAllocation) {
		this.maxAllocation = maxAllocation;
	}
	
}
