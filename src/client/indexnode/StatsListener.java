package client.indexnode;

/**
 * Classes that wish to recieve notifications of updated statistics from an indexnode must implement this interface.
 * 
 * This is primarily intended for use by the indexnodestatstablemodel class.
 * 
 * @author gp
 *
 */
public interface StatsListener {
	/**
	 * Called when the stats for the indexnode registered with this listener have changed.
	 * 
	 * These events will happen in an arbitrary thread!
	 */
	public void statsUpdated();
}
