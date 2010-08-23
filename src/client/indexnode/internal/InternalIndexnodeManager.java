package client.indexnode.internal;

import common.Config;
import common.Logger;

import client.indexnode.IndexNodeCommunicator;
import client.indexnode.internal.InternalIndexnodeConfigDefaults.IIK;
import client.platform.ClientConfigDefaults.CK;
import indexnode.AdvertDataSource;
import indexnode.IndexAdvertismentManager;
import indexnode.IndexNode;
import indexnode.IndexConfigDefaults.IK;

/**
 * Manages a client-internal indexnode. This involves tasks such as advertising auto-indexnode ability if enabled, and starting an indexnode when needed.
 * 
 * @author gp
 */
public class InternalIndexnodeManager {
	
	private class AdsImpl implements AdvertDataSource {

		long advertuid;
		
		public AdsImpl() {
			advertuid = nodeConfig.getLong(IIK.ADVERTUID);
		}
		
		@Override
		public long getAdvertUID() {
			return advertuid;
		}

		/**
		 * We calculate the potential worth of this indexnode based upon the maximum heap size of the current JVM.
		 */
		@Override
		public long getIndexValue() {
			return Runtime.getRuntime().maxMemory();
		}

		@Override
		public int getPort() {
			return InternalIndexnodeManager.this.getPort();
		}

		@Override
		public boolean isActive() {
			return InternalIndexnodeManager.this.isCurrentlyActive();
		}

		@Override
		public boolean isProspectiveIndexnode() {
			return isAutoIndexnodeEnabled();
		}
	}
	
	
	
	private IndexNode executingNode;
	private IndexNodeCommunicator comm;
	private Config nodeConfig;
	private IndexAdvertismentManager advertManager;
	private CapabilityRecorder cr;
	
	public InternalIndexnodeManager(IndexNodeCommunicator comm) {
		this.comm = comm;
		this.nodeConfig = comm.getConf().deriveConfig(new InternalIndexnodeConfigDefaults(), CK.INTERNAL_INDEXNODE_ROOTKEY);
		
		//1) build the advertisers
		setupAdvertisers();
		
		//2) listen for capability adverts from other autoindexnodes:
		cr = new CapabilityRecorder();
		
		//3) start an indexnode if configured to always run one
		if (isAlwaysOn()) ensureIndexnode();
		
		//4) determine if an indexnode should be started.
		considerNecessity();
	}
	
	/**
	 * Notifies the internal indexnode that a new capability advert has arrived.
	 * @param advertuid
	 * @param capability
	 */
	public void receivedCapabilityAdvert(long advertuid, long capability) {
		cr.recordCapability(capability, advertuid);
	}
	
	/**
	 * Determines if this client should run an internal indexnode, and possibly starts/stops one.
	 * 
	 * This will start an indexnode if:
	 * 1) we aren't connected to any active indexnodes.
	 * 2) we havent heard a capability advert from a more/equally capable client recently.
	 * 
	 * This will stop an indexnode if:
	 * 1) we are connected to an active indexnode that is not us.
	 * 2) we have heard a capability advert that exceeds our own capability.
	 * 
	 * So, this will do nothing if:
	 * 1) we are only connected to ourselves.
	 * 
	 */
	private void considerNecessity() {
		
	}
	
	/**
	 * Ensures an indexnode is running, or starts one.
	 */
	private void ensureIndexnode() {
		if (executingNode!=null) return;
		try {
			executingNode = new IndexNode(nodeConfig, true);
		} catch (Exception e) {
			Logger.severe("Internal indexnode couldn't be started: "+e);
			e.printStackTrace();
		}
	}
	
	public boolean isAlwaysOn() {
		return comm.getConf().getBoolean(CK.INTERNAL_INDEXNODE_ALWAYS_ON);
	}
	
	/**
	 * Returns true if the internal indexnode is currently active (ie: other peers could connect to it)
	 * @return
	 */
	public boolean isCurrentlyActive() {
		return executingNode!=null;
	}
	
	/**
	 * Returns the port number that this indexnode is configured to use.
	 * @return
	 */
	public int getPort() {
		return nodeConfig.getInt(IK.PORT);
	}
	
	/**
	 * Returns true if this internal indexnode is automatically active.
	 * @return
	 */
	public boolean isAutoIndexnodeEnabled() {
		return comm.getConf().getBoolean(CK.AUTOMATIC_INDEXNODE);
	}
	
	public void setAutoIndexnode(boolean enabled) {
		comm.getConf().putBoolean(CK.AUTOMATIC_INDEXNODE, enabled);
		setupAdvertisers();
	}
	
	private void setupAdvertisers() {
		if (isAutoIndexnodeEnabled()) {
			try {
				advertManager = new IndexAdvertismentManager(nodeConfig, new AdsImpl());
			} catch (Exception e) {
				Logger.warn("Unable to build advertisment framework: "+e);
				e.printStackTrace();
			}
		} else {
			advertManager.shutdown();
			advertManager = null;
		}
	}
	
	
	/**
	 * Updates the alias of the internal indexnode, immediately if it is currently active.
	 * @param newAlias
	 */
	public void setAlias(String newAlias) {
		nodeConfig.putString(IK.ALIAS, newAlias);
		if (executingNode!=null) {
			executingNode.setAlias(newAlias);
		}
	}

	/**
	 * Notifies the indexnode that the client has changed their alias.
	 * TODO: enable an override.
	 */
	public void clientAliasChanged() {
		setAlias(comm.getShareServer().getAlias()+"'s Indexnode");
	}

	
	public void shutdown() {
		if (advertManager!=null) advertManager.shutdown();
		if (executingNode!=null) executingNode.shutdown();
	}
	
}
