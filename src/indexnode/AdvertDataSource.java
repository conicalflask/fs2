package indexnode;

/**
 * An advert manager uses this to find information that might come from either a client or an indexnode.
 * @author gp
 *
 */
public interface AdvertDataSource {
	/**
	 * The port the indexnode is running on.
	 * @return
	 */
	public int getPort();
	/**
	 * The advertuid of the indexnode.
	 * @return
	 */
	public long getAdvertUID();
	
	/**
	 * Is this indexnode running and clients can currently connect to it?
	 * @return
	 */
	public boolean isActive();
	
	/**
	 * Is this an internal indexnode that may become active at some point if needed?
	 * @return
	 */
	public boolean isProspectiveIndexnode();
	/**
	 * The 'worth' of potential indexnodes hosted on this client.
	 * @return
	 */
	public long getIndexValue();
	
	
}
