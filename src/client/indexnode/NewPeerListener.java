package client.indexnode;

/**
 * Indicates a class understands how to respond to new peers being available on the FS2 network.
 * @author gary
 *
 */
public interface NewPeerListener {
	/**
	 * Called to indicate that new peers are present on the FS2 network.
	 * This might indicate that there are more sources for files available.
	 */
	public void newPeersPresent();
}
