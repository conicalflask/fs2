package client.indexnode.internal;

import java.io.File;
import java.util.Timer;
import java.util.TimerTask;

import common.Config;
import common.FS2Constants;
import common.Logger;

import client.indexnode.IndexNodeCommunicator;
import client.indexnode.internal.InternalIndexnodeConfigDefaults.IIK;
import client.platform.Platform;
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
			advertuid = InternalIndexnodeManager.this.getAdvertUID();
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
			return InternalIndexnodeManager.this.getCapability();
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
			return (isAutoIndexnodeEnabled() && !isAutoIndexNodeInhibited()) || isCurrentlyActive();
		}
	}
	
	private IndexNode executingNode;
	private final IndexNodeCommunicator comm;
	private final Config nodeConfig;
	private IndexAdvertismentManager advertManager;
	private final CapabilityRecorder cr;
	private final String indexnodeFilesPath;
	private Timer considerationTimer;
	
	public InternalIndexnodeManager(IndexNodeCommunicator comm) {
		this.comm = comm;
		this.nodeConfig = comm.getConf().deriveConfig(new InternalIndexnodeConfigDefaults(comm.getConf()), CK.INTERNAL_INDEXNODE_ROOTKEY);
		
		indexnodeFilesPath = Platform.getPlatformFile("internalindexnode")+"/";
		(new File(indexnodeFilesPath)).mkdirs();
		
		//1) build the advertisers
		setupAdvertisers();
		
		//2) listen for capability adverts from other autoindexnodes:
		capability = IndexNode.generateCapabilityValue();
		
		cr = new CapabilityRecorder(getAdvertUID());
		
		//3) start an indexnode if configured to always run one
		if (isAlwaysOn()) ensureIndexnode();
		
		//4) determine if an indexnode should be started.
		considerationTimer = new Timer("Internal Indexnode timer", true);
		considerationTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				considerNecessity();
			}
		}, FS2Constants.INTERNAL_INDEXNODE_RECONSIDER_INTERVAL_MS /* wait initially to prevent premature indexnode starting.*/, FS2Constants.INTERNAL_INDEXNODE_RECONSIDER_INTERVAL_MS);
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
	 * 1) we aren't connected to any active indexnodes. AND
	 * 2) we havent heard a capability advert from a more/equally capable client recently.
	 * 2...) that is to say: we're the most capable indexnode around.
	 * 
	 * This will stop an indexnode if:
	 * 1) we are connected to an active indexnode that is not us. AND
	 * 2) we have heard a capability advert that exceeds our own capability.
	 * 
	 * OR stop if we're no longer in automode.
	 * 
	 * So, this will do nothing if:
	 * 1) we are only connected to ourselves.
	 * 2) we are connected to another node but believe we are still the best to run.
	 * 
	 */
	private void considerNecessity() {
		if (isAlwaysOn()) return; //Nothing need be done if always on.
		//1) Think about starting an indexnode:
		// --must meet three critera:
			//a) Automatic indexnode enabled. AND
			//b) not inhibited by a statically configured indexnode AND
			//c) must be the most capable applicant in the area. OR we're not connected to the most capable, but are configured to do so.
		// whoa!
		if (isAutoIndexnodeEnabled() && !isAutoIndexNodeInhibited() && (cr.amIMostCapable(getCapability()) || (!comm.isConnectedToARemoteAutodetectedIndexnode() && comm.isListeningForAdverts()))) {
			ensureIndexnode();
			executingNode.setAutomatic(true);
		}
		
		
		//2) Think about stopping our indexnode:
		// --must meet criteria:
			//a) Not running an auto indexnode
			//b) OR inhibited now (by a statically configured, connected indexnode)
			//c) OR (connected to another non-static indexnode AND we're not the top of the league table)
		if (!isAutoIndexnodeEnabled() || isAutoIndexNodeInhibited() || (comm.isConnectedToARemoteAutodetectedIndexnode() && !cr.amIMostCapable(getCapability()))){
			stopIndexnode();
		}
	}
	
	private final long capability;
	public long getCapability() {
		return capability;
	}
	
	/**
	 * Returns true if this internal indexnode wont run because the client is already connected to a non-detected indexnode.
	 * 
	 * <br>This is to prevent the autoindexnode from advertising it will run automatically when actually it never will.
	 * @return
	 */
	public boolean isAutoIndexNodeInhibited() {
		return comm.isAStaticIndexnodeActive();
	}
	
	/**
	 * Ensures an indexnode is running, or starts one.
	 */
	private void ensureIndexnode() {
		if (executingNode!=null) return;
		try {
			Logger.warn("Internal indexnode started!");
			executingNode = new IndexNode(nodeConfig, true, indexnodeFilesPath);
		} catch (Exception e) {
			Logger.severe("Internal indexnode couldn't be started: "+e);
			e.printStackTrace();
		}
	}
	
	/**
	 * Stops a running indexnode if needed.
	 */
	private void stopIndexnode() {
		if (executingNode!=null) {
			executingNode.shutdown();
			executingNode=null;
			comm.notifyInternalIndexnodeShutdown();
			Logger.warn("Internal indexnode stopped.");
		}
	}
	
	public boolean isAlwaysOn() {
		return comm.getConf().getBoolean(CK.INTERNAL_INDEXNODE_ALWAYS_ON);
	}
	
	public void setAlwaysOn(boolean enabled) {
		comm.getConf().putBoolean(CK.INTERNAL_INDEXNODE_ALWAYS_ON, enabled);
		if (enabled) {
			ensureIndexnode();
			executingNode.setAutomatic(false);
		} else {
			stopIndexnode();
		}
		
		setupAdvertisers();
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
	 * Returns the advertuid of this internal indexnodes
	 * @return
	 */
	public long getAdvertUID() {
		return nodeConfig.getLong(IIK.ADVERTUID);
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
		if (isAutoIndexnodeEnabled() || isAlwaysOn()) {
			try {
				if (advertManager==null) advertManager = new IndexAdvertismentManager(nodeConfig, new AdsImpl());
			} catch (Exception e) {
				Logger.warn("Unable to build advertisment framework: "+e);
				e.printStackTrace();
			}
		} else {
			if (advertManager!=null) {
				advertManager.shutdown();
				advertManager = null;
			}
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
	 */
	public void clientAliasChanged() {
		setAlias(comm.getShareServer().getAlias());
	}

	
	public void shutdown() {
		considerationTimer.cancel();
		if (advertManager!=null) advertManager.shutdown();
		stopIndexnode();
	}

	/**
	 * Returns our rank in all possible automatic indexnodes or 0 if we have no rank.
	 * @return
	 */
	public int getRank() {
		return cr.getRank();
	}

	/**
	 * Returns the number of automatic indexnodes detectable by this client.
	 * @return
	 */
	public int getAlternativeNodes() {
		return cr.getRecordCount();
	}
	
}
