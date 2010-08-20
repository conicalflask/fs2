package client.indexnode.internal;

/**
 * Manages a client-internal indexnode.
 * 
 * TODO: implement this.
 * 
 * @author gp
 */
public class InternalIndexnodeManager {
	
	/**
	 * Returns true if the internal indexnode is currently active (ie: other peers could connect to it)
	 * @return
	 */
	public boolean isCurrentlyActive() {
		return false;
	}
	
	/**
	 * Returns the port number that this indexnode is active on.
	 * @return
	 */
	public int getPort() {
		return 0;
	}
	
}
