package client.shareserver;

public interface BandwidthSharer {

	/**
	 * Returns the number of bytes allocated to the caller in this allocation interval.
	 * @param request the number of bytes the caller requires.
	 * @return The allocation of bytes for this channel. This is never greater than request or the maximum allocation per interval.
	 */
	public abstract long getBytes(long request) throws InterruptedException;

	public abstract int getAllocationInterval();

	public abstract void setAllocationInterval(int allocationInterval);

	/**
	 * Returns the maximum number of bytes that can ever be made per allocation.
	 * @return
	 */
	public abstract long getMaxAllocation();

	public abstract void setMaxAllocation(long maxAllocation);

	public abstract long getBytesPerSecond();

	/**
	 * Sets the bytes per second allowed by this sharer by calculating the maxAllocation per interval.
	 */
	public abstract void setBytesPerSecond(long bytes);

}