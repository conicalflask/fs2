package client.indexnode.internal;

/**
 * Given adverts from other autoindexnode capable clients this class will store them to allow the current best autoindexnode to be determined.
 * 
 * @author gp
 *
 */
public class CapabilityRecorder {

	/**
	 * Records a capability claim from another client.
	 * @param capability
	 * @param advertuid
	 */
	public void recordCapability(long capability, long advertuid) {
		
	}
	
	/**
	 * Returns the advertuid belonging to the client that has provided the greatest capability claim.
	 * @return
	 */
	public long getGreatestRecentCapability() {
		return 0l;
	}
}
