package client.indexnode;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import client.gui.Utilities;
import client.indexnode.IndexNodeStats.IndexNodeClient;
import client.indexnode.downloadcontroller.DownloadSource;
import client.shareserver.ShareServer;

import common.Sxml.SXMLException;
import common.httpserver.HttpExchange;

import common.ChatMessage;
import common.FS2Constants;
import common.FS2Filter;
import common.HttpUtil;
import common.Logger;
import common.Sxml;
/**
 * Abstracts communication with a single indexnode.
 * @author gp
 */
public class IndexNode {
	
	public enum Status {ACTIVE, STARTING, SHUTDOWN, UNCONTACTABLE, FIREWALLED, INCOMPATIBLE, AUTHREQUIRED};
	private Status status = Status.STARTING;
	private URL location;
	
	/**
	 * Secure location is where the indexnode may be found over a TLS socket. by convention this is just the location with port++
	 */
	private URL secureLocation;
	
	/**
	 * When secure is true this indexnode will never use insecure sockets to contact the indexnode.
	 */
	private volatile boolean secure = false;
	
	/**
	 * secureTested indicates if the indexnode has undergone testing this session to see if it is in secureMode.
	 * When this is true no more secure testing will happen this session.
	 * 
	 * when this is false no outbound requests nor file list serving is permitted.
	 */
	private volatile boolean secureTested = false;
	/**
	 * Related to security, amAdmin is true if we are an admin on this indexnode.
	 */
	private boolean amAdmin = false;
	
	/**
	 * The MD5 of our password for this indexnode. Required to attempt registration with secure nodes.
	 */
	private String passwordMD5 = "";
	
	private String alias = "unknown";
	private Timer utilityTimer = new Timer("Indexnode utility timer (ping & chat & stats)", true);
	private PingTimerTask ping;
	private ShareServer ssvr;
	private IndexNodeStats stats = new IndexNodeStats();
	private StatsUpdateTask statsUpdater = new StatsUpdateTask();
	private Date lastSeen = new Date(0);
	/** cltoken is used to ensure that only indexnodes we have registered with may access our file list */
	private final Long cltoken = (new Random()).nextLong();
	String confKey = "";
	int lastId = -1;
	long advertuid;
	
	private class PingTimerTask extends TimerTask {
		@Override
		public void run() {
			Logger.fine("Indexnode: "+alias+" hasn't pinged for a while... saying 'hello' again...");
			resetPingTimer();
			contactIndexNode();
		}
	}
	
	public ShareServer getShareServer() {
		return ssvr;
	}
	
	IndexNode(URL nodeURL, long advertuid, ShareServer ssvr, String confKey) {
		this.advertuid = advertuid;
		this.ssvr = ssvr;
		this.confKey = confKey;
		this.passwordMD5 = ssvr.getConf().getString(confKey+"/password");
		
		String su = nodeURL.toString();
		if (su.endsWith("/")) {
			Logger.warn("Indexnode URLs shouldn't end with a slash!");
			try {
				nodeURL = new URL(su.substring(0,su.length()-1));
			} catch (MalformedURLException e) {
				Logger.warn("Fixed indexnode url is invalid!? "+e);
				e.printStackTrace();
			}
		}
		
		this.location = nodeURL;
		this.secureLocation = FS2Filter.getFS2SecureURL(location);
		int idx=-1;
		synchronized (ssvr.getIndexNodeCommunicator().getRegisteredIndexNodes()) {
			ssvr.getIndexNodeCommunicator().getRegisteredIndexNodes().add(this);
			idx=ssvr.getIndexNodeCommunicator().getRegisteredIndexNodes().size()-1;
		}
		ssvr.getIndexNodeCommunicator().notifyIndexNodeInserted(idx);
		
		if (wasAdvertised()) {
			Logger.log("An indexnode at '"+getLocation()+"' was just autodetected...");
		}
		
		// (even if the indexnode is uncontactable we still want to poll it periodically)
		resetPingTimer();
		//setup a recurring task to check for new messages: (and check now)
		utilityTimer.schedule(chatPoll, 0, FS2Constants.CLIENT_CHAT_POLL_INTERVAL);
		//Setup statistics polling: (and also check now)
		utilityTimer.schedule(statsUpdater, 0, FS2Constants.INDEXNODE_CACHE_STATISTICS_DURATION); 
		
		contactIndexNode();
	}
	
	/**
	 * Checks for new messages on the indexnode.
	 * @author gary
	 */
	ChatPoll chatPoll = new ChatPoll();
	class ChatPoll extends TimerTask {
		@Override
		public void run() {
			synchronized (chatListeners) {
				if (chatListeners.size()==0) {
					return;
				}
			}
			//If we're here then start a new chat poll task.
			scheduleMessageAction(null);
		}
	}
	
	/**
	 * Places a task onto the chat request pool to send a message or command or to recieve them.
	 * @param message
	 */
	private void scheduleMessageAction(String message) {
		ssvr.getIndexNodeCommunicator().chatRequestPool.submit(new ChatAction(message));
	}
	
	Object chatActionMutex = new Object();
	
	/**
	 * Retrieves messages from the indexnode and sends one if needed.
	 * @author gary
	 *
	 */
	class ChatAction implements Runnable {
		
		final String outMessage;
		
		public ChatAction(String message) {
			this.outMessage = message;
		}
		
		@Override
		public void run() {
			synchronized (chatActionMutex) {
				try {
					if (!isWritable()) return; //dont bother even trying unless the indexnode is chattable
					Sxml xml = getXmlFromIndexnode(getChatURL(lastId, outMessage));
					
					int newLastId = dispatchNewMessagesFromXml(xml);
					lastId = (newLastId==-1 ? lastId : newLastId);
					
					if (outMessage!=null && !outMessage.equals("")) {
						dispatchMessageReturn(xml);
					}
					
				} catch (IOException e) {
					if (outMessage==null) {
						Logger.warn("Unable to check for new chat messages: "+e);
					} else {
						Logger.warn("Unable to send a new chat message: "+e);
					}
					contactIndexNode(); //if there's IO trouble, we want to drop the indexnode ASAP.
				} catch (Exception e) {
					Logger.warn("Malformed or broken chat messages from indexnode '"+getName()+"': "+e);
					e.printStackTrace();
				}
			}
		}

		private void dispatchMessageReturn(Sxml xml) throws InterruptedException, InvocationTargetException {
			//dispatch message return if a message was sent:
			Element cr = xml.getElementById("fs2-chatresult");
			int msgid = Integer.parseInt(cr.getAttribute("fs2-chatindex"));
			String msg = (msgid==-1 ? msg=cr.getTextContent() : outMessage); //decide if it's a command or a message return
			final ChatMessage ret = new ChatMessage(msg, msgid);
			Utilities.dispatch(new Runnable() {
				@Override
				public void run() {
					synchronized (chatListeners) {
						for (ChatListener l : chatListeners) {
							l.messageReturn(ret);
						}
					}
				}
			});
		}
		
		private LinkedList<ChatMessage> getMessagesFromXml(Sxml xml) {
			LinkedList<ChatMessage> outmsgs = new LinkedList<ChatMessage>();
			//3) process into ChatMessages
			Element messages = xml.getElementById("fs2-chatmessages");
			Node onNode = messages.getFirstChild();
			while (onNode!=null) {
				try {
					if (onNode.getNodeType()!=Element.ELEMENT_NODE) continue;
					try {
						//Behold the bracketmaster:
						outmsgs.add(new ChatMessage(onNode.getTextContent(), Integer.parseInt(((Element)onNode).getAttribute("fs2-chatindex"))));
					} catch (Exception e) {
						Logger.warn("Broken chat entry from indexnode '"+getName()+"': "+e);
					}
				} finally {
					onNode=onNode.getNextSibling();
				}
			}
			return outmsgs;
		}

		/**
		 * returns the new lastId
		 * @param xml
		 * @return
		 * @throws InterruptedException
		 * @throws InvocationTargetException
		 */
		private int dispatchNewMessagesFromXml(Sxml xml) throws InterruptedException, InvocationTargetException {
			final LinkedList<ChatMessage> outmsgs = getMessagesFromXml(xml);
			
			if (outmsgs.isEmpty()) {
				return -1;
			} else {
				Utilities.dispatch(new Runnable() {
					@Override
					public void run() {
						synchronized (chatListeners) {
							for (ChatListener l : chatListeners) {
								l.newMessages(outmsgs);
							}
						}
					}
				});
				return outmsgs.getLast().id;
			}
		}
	}
	
	private HashSet<ChatListener> chatListeners = new HashSet<ChatListener>();
	private boolean isAutomaticNode; //true iff this node was automatically started.
	
	public void registerChatListener(ChatListener l) {
		synchronized (chatListeners) {
			chatListeners.add(l);
		}
		//Attempt to retrieve messages right now, for the benifit of the new listener. (this is pretty pointles but helps to make the gui appear more responsive on startup)
		scheduleMessageAction(null);
	}
	
	public void deregisterChatListenter(ChatListener l) {
		synchronized (chatListeners) {
			chatListeners.remove(l);
		}
	}
	
	public void sendMessage(String message) {
		scheduleMessageAction(message);
	}
	
	/**
	 * Returns true if this is an automatically registered node; it is not permanent if this is true.
	 * @return
	 */
	public boolean wasAdvertised() {
		return advertuid!=0;
	}
	
	public long getAdvertuid() {
		return advertuid;
	}
	
	/**
	 * Provided with a new MD5'd password string this will save it to the config file and reconsider being secure with the indexnode.
	 * You should rate-limit calls to this method as it will necessarily contact the indexnode several times. (only if we're already in secure mode though)
	 * @param passwordMD5
	 */
	public synchronized void setPassword(String passwordMD5) {
		ssvr.getIndexNodeCommunicator().conf.putString(confKey+"/password", passwordMD5);
		this.passwordMD5 = passwordMD5;
		
		//force renegotiation with the indexnode:
		secure = false;
		secureTested = false;
		amAdmin = false;
		//restart
		setStatus(Status.STARTING);
		contactIndexNode();
	}
	
	/**
	 * Returns true if we're communicating with this indexnode in securemode.
	 * @return
	 */
	public boolean isSecure() {
		return secure;
	}
	
	/**
	 * Returns true if we're an admin of this indexnode. Being an admin allows managing users amongst other goodies.
	 * @return
	 */
	public boolean amAdmin() {
		return amAdmin;
	}
	
	public Status getNodeStatus() {
		return status;
	}
	
	/**
	 * Returns true iff the indexnode is available to serve requests for files and stats
	 * @return
	 */
	public boolean isReadable() {
		return status==Status.ACTIVE || status==Status.FIREWALLED;
	}
	
	/**
	 * Returns true iff we are able to share files and chat on this indexnode
	 * @return
	 */
	public boolean isWritable() {
		return status==Status.ACTIVE;
	}
	
	public String getStatusDescription() {
		String ret;
		
		switch (status) {
		case UNCONTACTABLE:
			ret = "attempting to connect";
			break;
		case AUTHREQUIRED:
			ret = (passwordMD5.equals("") ? "password required" : "awaiting authorisation or password incorrect");
			break;
		case FIREWALLED:
			ret = "indexnode can't contact us (check ports/firewall)";
			break;
		default:
			ret = status.toString().toLowerCase();
		}
		
		if (isSecure()) ret+=" (secure)";
		if (amAdmin()) ret+=" (admin)";
		if (isAutomatic()) ret+=" (automatic)";
		
		return ret;
	}
	
	/**
	 * returns true if this indexnode was automatically started inside another client
	 * @return
	 */
	public boolean isAutomatic() {
		return isAutomaticNode;
	}
	
	/**
	 * Checks to see if this httpExchange was from this indexnode, returns true if it was.
	 * As this indicates the indexnode is still alive, and 'pinging' us, this also resets the ping-timer.
	 * 
	 * This is only moderately secure. It checks the indexnode supplies us with our session-unique token.
	 * It does not prevent man-in-the-middle nor replaced indexnode attacks.
	 * This should be Good Enough(tm) until SSL is implemented.
	 */
	boolean fromThisIndexnode(HttpExchange ex) {
		if (!secureTested) {
			Logger.warn("Rejecting request to have filelists while secureTested is false.");
			return false; //can't have indexnodes if not secure tested.
		}
		if (status==Status.AUTHREQUIRED) return false; //indexnodes that don't trust us are untrusted. Prevents hanging onto indexnodes if we no longer know our password.
		
		if (!ex.getRequestHeaders().containsKey("fs2-verify")) {
			Logger.warn("Indexnode didn't supply any verification token.");
			return false;
		}
		if (ex.getRequestHeaders().getFirst("fs2-verify").equals(cltoken.toString())) {
			
			//we've established it's the correct indexnode, but is it secure?
			if (secure && !ex.isSecure()) {
				Logger.warn("Secure indexnode '"+getName()+"' rejected for attempting plaintext filelist retreival.");
				return false; //can't have filelists over non-ssl if the server is in SSL mode.
			}
			
			resetPingTimer();
			lastSeen = new Date();
			notifyGui();
			if (status==Status.FIREWALLED) setStatus(Status.ACTIVE); //promote to active if they can now contact us.
			return true;
		}
		//Logger.warn("Rejecting indexnode for invalid clToken.");
		return false;
	}
	
	public Date getLastSeen() {
		return lastSeen;
	}
	
	void resetPingTimer() {
		if (ping!=null) ping.cancel();
		utilityTimer.schedule((ping= new PingTimerTask()), FS2Constants.CLIENT_PING_WAIT_TIMEOUT_MS, FS2Constants.CLIENT_PING_WAIT_TIMEOUT_MS);
	}
	
	/**
	 * Notifies the indexnode if anything has changed on this client.
	 */
	void notifyIndexNode() {
		if (status==Status.ACTIVE) contactIndexNode(); //only bother notifying active indexnodes.
	}
	
	/**
	 * If applicable this method will determine if the indexnode is in secure mode.
	 * 
	 * An indexnode is in securemode if it can be contacted surely and NOT insecurely.
	 * Otherwise it is running insecurely.
	 * @return true iff this test was a success and securemode is now known, false if the indexnode was uncontactable.
	 */
	boolean testForSecure() {
		if (!secureTested) {
			boolean canContactInsecure = false;
			boolean canContactSecure = false;
			byte[] buf = new byte[1024];
			
			try {
				//1) do secure test (this should be fast as even insecure indexnodes still listen on secure sockets)
				try {
					//method: get / on the indexnode without sending a password. This is always allowed: / (indexroot handler) never requires a password.
					InputStream is = secureLocation.openStream();
					is.read(buf);
					is.close();
					canContactSecure = true;
				} catch (Exception e) {
					canContactSecure = false;
				}
				
				//2) If the secure test was a success (and it should be for all compatible, running, contactable indexnodes)
				//   then test insecure.
				if (canContactSecure) {
					try {
						InputStream is = location.openStream();
						is.read(buf);
						is.close();
						canContactInsecure = true;
					} catch (Exception e) {
						canContactInsecure = false;
					}
				}
			} finally {
				secure = canContactSecure && !canContactInsecure; //test success and no insecure access.
				if (secure) Logger.log("Indexnode at "+getActiveLocation()+" is now SECURE, all communication with it now goes over TLS sockets.");
				secureTested = canContactSecure; //if secure contact failed then this whole test failed.
			}
		}
		return secureTested;
	}
	
	/**
	 * This registers with (or renotifies) the indexnode.
	 * 
	 * During registration the indexnode will attempt to download our sharelist, but unless we
	 * know their alias, we will forbid it, breaking registration. So, we determine the indexnode's alias once first,
	 * then subsequently registration should work.
	 * 
	 * This doesn't use the getXmlFromIndexnode helper as it needs to inspect the connection properties more closely.
	 * 
	 */
	private synchronized void contactIndexNode() {
		try {
			if (status==Status.SHUTDOWN || status==Status.INCOMPATIBLE) return;
			
			//testing for secure mode must be nearly the first item as it changes the url that the indexnode is contacted via.
			if (!testForSecure()) {
				throw new IOException("Failed to test for secure mode.");
			}
			
			if (secure && passwordMD5.equals("") && status!=Status.AUTHREQUIRED) {
				Logger.warn("Indexnode '"+getName()+"' requires a password to connect in secure mode.");
				setStatus(Status.AUTHREQUIRED);
				return;
			}
			
			URL helloURL = new URL(getActiveLocation().toString()+"/hello");
			HttpURLConnection conn = (HttpURLConnection)helloURL.openConnection();
			
			ssvr.getFS2Filter().fs2FixupURLConnectionForClient(conn, cltoken);
			if (secure) conn.setRequestProperty("fs2-auth", passwordMD5);
			conn.connect();
			int status = conn.getResponseCode();
			//Only accept a successful message from the search node.
			if (status != 200) {
				if (status==412) {
					if (getNodeStatus()!=Status.FIREWALLED) {
						Logger.warn("Indexnode cannot connect to us, ensure ports are mapped and firewalls are open on the correct ports");
						setStatus(Status.FIREWALLED);
					}
					conn.getErrorStream().close();
					return;
				} if (status==403) {
					if (getNodeStatus()!=Status.AUTHREQUIRED) {
						Logger.warn("Indexnode '"+getName()+"' rejects our credentials.");
						setStatus(Status.AUTHREQUIRED);
					}
					conn.getErrorStream().close();
					return;
				} else {
					Logger.warn("Failure status from indexnode: "+Integer.toString(status));
					setStatus(Status.INCOMPATIBLE);
					conn.getErrorStream().close();
					return;
				}
			}
			try {
				if (!conn.getHeaderField("fs2-version").equals(FS2Constants.FS2_PROTOCOL_VERSION)) { //TODO: change this to an acceptable range in the future.
					Logger.severe("Indexnode "+alias+" uses an incompatible protocol version: "+conn.getHeaderField("fs2-version")+", we need: "+ FS2Constants.FS2_PROTOCOL_VERSION+"\n Consider updating fs2!");
					setStatus(Status.INCOMPATIBLE);
					return;
				}
				amAdmin = Boolean.parseBoolean(conn.getHeaderField("fs2-indexnodeadmin"));
				isAutomaticNode = Boolean.parseBoolean(conn.getHeaderField("fs2-automatic"));
				
				String indexNodeAvatar = conn.getHeaderField("fs2-avatarhash");
				String nAvatarHash = ssvr.getIndexNodeCommunicator().encodedAvatarMD5;
				if (indexNodeAvatar == null || !indexNodeAvatar.equals(nAvatarHash)) {
					if (nAvatarHash!=null && nAvatarHash.length()==32) sendAvatar();
				}
				
				setStatus(Status.ACTIVE);
			} finally {
				try {
					conn.getInputStream().close();
				} catch (IOException e) {
					Logger.warn("Exception closing hello input stream: "+e);
					e.printStackTrace();
				}
			}
		} catch (IOException e){
			Logger.fine("Indexnode '"+alias+"' can't be contacted ("+e.getMessage()+").");
			setStatus(Status.UNCONTACTABLE);
		} catch (Exception e) {
			Logger.warn("Contacting indexnode '"+alias+"' failed: \n"+e.toString());
			setStatus(Status.UNCONTACTABLE);
			e.printStackTrace();
		}
	}
	
	/**
	 * Sends our avatar to the indexnode:
	 */
	private void sendAvatar() {
		URL asu = getAvatarSubmissionURL();
		try {
			getInputStreamFromIndexnode(asu).close();
		} catch (IOException e) {
			Logger.warn("Couldn't submit avatar to indexnode: "+e);
		}
	}
	
	private void setStatus(Status status) {
		if (this.status!=status) {
			this.status = status;
			
			if (status == Status.UNCONTACTABLE && wasAdvertised()) { //remove the indexnode from the list altogether.
				ssvr.getIndexNodeCommunicator().deregisterIndexNode(this);
			}
			
			if (status==Status.INCOMPATIBLE && ping!=null) ping.cancel(); //dont retry continuously for an incompatible indexnode.
			Logger.log("Indexnode '"+getName()+"' just became "+status);
			if (status!=Status.SHUTDOWN) {
				notifyGui();
			}
		}
	}

	/**
	 * Allows the notification of gui elements that something non-structural has changed. (ie a status or alias has changed)
	 */
	private void notifyGui() {
		ssvr.getIndexNodeCommunicator().notifyIndexNodeChanged(ssvr.getIndexNodeCommunicator().getRegisteredIndexNodes().indexOf(this));
	}

	/**
	 * Prevents this indexnode client from chatting with its indexnode anymore.
	 */
	void shutdown() {
		if (ping!=null) ping.cancel();
		if (utilityTimer!=null) utilityTimer.cancel();
		setStatus(Status.SHUTDOWN);
	}
	
	@Override
	public String toString() {
		return alias+" ("+location.getHost()+")";
	}
	
	public String getName() {
		return (alias.equals("unknown") ? location.toString() : alias);
	}
	
	/**
	 * Returns this indexnode's insecure URL.
	 * @return
	 */
	public URL getLocation() {
		return location;
	}
	
	/**
	 * Gets the URL currently used to contact the indexnode.
	 * @return
	 */
	URL getActiveLocation() {
		return (secure ? secureLocation : location);
	}

	/**
	 * Get the statistics and list of connected peers from this indexnode.
	 */
	public IndexNodeStats getStats() {
		return stats;
	}
	
	HashSet<StatsListener> statsListeners = new HashSet<StatsListener>();
	
	/**
	 * Register a new stats listener with this indexnode.
	 * @param l
	 */
	public void addStatsListener(StatsListener l) {
		synchronized (statsListeners) {
			statsListeners.add(l);
		}
	}
	
	public void removeStatsListener(StatsListener l) {
		synchronized (statsListeners) {
			statsListeners.remove(l);
		}
	}
	
	/**
	 * Used to indicate that a resource has not changed so there is no useful data to use.
	 * @author gary
	 */
	public class NotChangedException extends IOException {
		private static final long serialVersionUID = 7831580251332621088L;
	}
	
	/**
	 * Safely gets an input stream from the indexnode.
	 * 
	 * It is important to remember to close the stream that is retreived!
	 * 
	 * @param path
	 * @return
	 * @throws IOException
	 * @throws NotChangedException 
	 */
	private synchronized InputStream getInputStreamFromIndexnode(URL path) throws IOException, NotChangedException {
		if (!secureTested) {
			throw new IllegalStateException("Indexnode: '"+getName()+"' was used before secure testing!");
		}
		
		HttpURLConnection conn = (HttpURLConnection) path.openConnection();

		try {
			ssvr.getFS2Filter().fs2FixupURLConnectionForClient(conn, cltoken);
			if (secure) conn.setRequestProperty("fs2-auth", passwordMD5);
			
			String newAlias = conn.getHeaderField("fs2-alias");
			if (newAlias!=null && !newAlias.equals(alias)) {
				Logger.log("The indexnode at "+getActiveLocation().toString()+" is now known as '"+newAlias+"'");
				alias = newAlias;
				notifyGui();
			}
			
			int status = conn.getResponseCode();
			if (status==304) {
				conn.getInputStream().close();
				throw new NotChangedException();
			}
			
			return conn.getInputStream();
		} finally {
			InputStream es = conn.getErrorStream();
			if (es!=null) es.close();
		}
	}
	
	/**
	 * A helper to acomplish the common task of getting some XML from the indexnode
	 * @param path
	 * @return
	 * @throws IOException 
	 * @throws SXMLException 
	 * @throws NotChangedException 
	 */
	private synchronized Sxml getXmlFromIndexnode(URL path) throws IOException, SXMLException, NotChangedException {
		InputStream is = null;
		try {
			is = getInputStreamFromIndexnode(path);
			return new Sxml(is);
		} finally {
			if (is!=null) is.close();
		}
	}
	
	/**
	 * Used to update stats
	 * @author gary
	 *
	 */
	private class StatsUpdateTask extends TimerTask {
		@Override
		public void run() {
			try {
				if (!isReadable()) { //only bother polling if the indexnode is healthy.
					if (stats.peers.size()!=0) {
						//indexnode has things, so clear these out:
						stats = new IndexNodeStats();
						updateStatsListeners();
					}
					return; 
				}
				Sxml xml = getXmlFromIndexnode(getStatsURL());
				
				//Now process the XML:
				synchronized (stats) {
					
					//1) get normal stats items:
					stats.started = new Date(getStatisticValue(xml, "indexnode-started"));
					stats.indexedFiles = (int) getStatisticValue(xml, "file-count");
					stats.uniqueFiles = (int) getStatisticValue(xml, "unique-file-count");
					stats.totalRequestedBytes = getStatisticValue(xml, "total-transfer");
					stats.size = getStatisticValue(xml, "total-size");
					stats.uniqueSize = getStatisticValue(xml, "total-unique-size");
					
					Element clients = xml.getElementById("clients");
					HashSet<String> knownAliases = new HashSet<String>(stats.peers.keySet());
					
					boolean newPeers = false;
					
					for (Node onNode=clients.getFirstChild(); onNode!=null; onNode=onNode.getNextSibling()) {
						if (onNode.getNodeType()!=Element.ELEMENT_NODE) continue;
						Element elem = (Element)onNode;
						if (!elem.getTagName().equals("span")) continue;
						String alias = elem.getAttribute("fs2-clientalias");
						long shareSize = Long.parseLong(elem.getAttribute("value"));
						String avatarHash = elem.getAttribute("fs2-avatarhash");
						if (knownAliases.remove(alias)) { //IndexNodeClients are hashed by their aliases.
							IndexNodeClient c = stats.peers.get(alias);
							c.setAvatarhash(avatarHash);
							c.setTotalShareSize(shareSize);
						} else {
							stats.peers.put(alias,new IndexNodeClient(elem.getAttribute("fs2-clientalias"), Long.parseLong(elem.getAttribute("value")), elem.getAttribute("avatarhash"), IndexNode.this));
							newPeers = true;
						}
					}

					if (newPeers) ssvr.getIndexNodeCommunicator().notifyNewPeersPresent();
					
					//now remove peers that were not in the indexnode's list:
					for (String deadPeer : knownAliases) stats.peers.remove(deadPeer);
					
				}
				
				updateStatsListeners();
				
			} catch (IOException e) {
				Logger.warn("Unable to get stats from indexnode '"+getName()+"': "+e);
				contactIndexNode();
			} catch (Exception e) {
				Logger.warn("Malformed or broken stats from indexnode '"+getName()+"': "+e);
				e.printStackTrace();
			}
		}
	}
	
	private void updateStatsListeners() {
		synchronized (statsListeners) {
			for (StatsListener l : statsListeners) {
				l.statsUpdated();
			}
		}
	}
	
	private long getStatisticValue(Sxml xml, String key) {
		return Long.parseLong(xml.getElementById(key).getAttribute("value"));
	}
	
	/**
	 * Returns a list of uninitialised FileSystemEntry objects that represent the current children of the given parent on this indexnode.
	 * @param parent
	 * @return The UNSORTED list of children for the given parent.
	 */
	Collection<FileSystemEntry> lookupChildren(FileSystemEntry parent) {
		LinkedList<FileSystemEntry> ret = new LinkedList<FileSystemEntry>();
		try {
			//1) Generate a string to represent the query URL for the indexnode
			URL query;
			if (parent.isSearch()) {
				query = getSearchURL(parent.getSearchTerms());
			} else {
				query = getBrowseURL(parent.getIndexNodePath());
			}
			Sxml xml = getXmlFromIndexnode(query);
			
			//3) build and add fse objects
			addFileListItemsToList(xml, ret, parent);
			
		} catch (FileNotFoundException e) {
			//This is normal when we attempt to get children for a node that no longer exists.
			//The empty list is returned and this is correct.
		} catch (IOException e) {
			Logger.warn("Couldn't get updated filelists from indexnode '"+getName()+"': "+e);
			contactIndexNode();
		} catch (Exception e) {
			Logger.warn("Couldn't get updated filelists from indexnode '"+getName()+"': "+e);
			e.printStackTrace();
		}
		return ret;
	}
	
	private void addFileListItemsToList(Sxml xml, LinkedList<FileSystemEntry> ret, FileSystemEntry parent) {
		Element filelist = xml.getElementById("fs2-filelist");
		
		Node onNode = filelist.getFirstChild();
		while (onNode!=null) {
			try {
				if (onNode.getNodeType()!=Element.ELEMENT_NODE) continue;
				Element cFile = (Element)onNode;
				
				boolean isDirectory;
				long size;
				String name;
				String hash = "";
				int linkCount = 0;
				String clientAlias = "";
				String path = "";
				int alternativesCount = 1;
				
				try {
					if (!cFile.hasAttribute("fs2-type")) continue;
					isDirectory = cFile.getAttribute("fs2-type").equalsIgnoreCase("directory");
					if (!cFile.hasAttribute("fs2-size")) continue;
					size = Long.parseLong(cFile.getAttribute("fs2-size")); //even directories have a size!
					if (!cFile.hasAttribute("fs2-name")) continue;
					name = cFile.getAttribute("fs2-name");
					if (!isDirectory) {
						//Hash is essential for files.
						if (!cFile.hasAttribute("fs2-hash")) continue;
						hash = cFile.getAttribute("fs2-hash");
						if (cFile.hasAttribute("fs2-clientalias")) clientAlias = cFile.getAttribute("fs2-clientalias");
						if (cFile.hasAttribute("fs2-alternativescount")) alternativesCount = Integer.parseInt(cFile.getAttribute("fs2-alternativescount"));
					} else {
						//path is essential for directories:
						if (!cFile.hasAttribute("fs2-path")) continue;
						path = cFile.getAttribute("fs2-path");
						if (cFile.hasAttribute("fs2-linkcount")) linkCount = Integer.parseInt(cFile.getAttribute("fs2-linkcount"));
					}
				} catch (Exception e) {
					Logger.warn("Indexnode supplied an invalid filesystem entry to us: "+e);
					continue;
				}
				
				if (isDirectory) {
					ret.add(parent.generateUninitialisedChildDirectory(name, this, linkCount, path, size));
				} else {
					ret.add(parent.generateChildFile(name, this, size, hash, clientAlias, alternativesCount));
				}
				
			} finally {
				onNode = onNode.getNextSibling();
			}
		}
	}
	
	/**
	 * Get sources for this file from this indexnode
	 * @param hash the hash of the file to get sources for.
	 * @return a map of peer aliases-> download source objects.
	 */
	public Map<String, DownloadSource> getSources(String hash) {
		HashMap<String, DownloadSource> sources = new HashMap<String, DownloadSource>();
		
		try {
			Sxml xml = getXmlFromIndexnode(getAlternativesURL(hash));
			
			//3) parse the result into the sources set:
			Element altslist = xml.getElementById("fs2-filelist");
			
			Node onNode = altslist.getFirstChild();
			while (onNode!=null) {
				try {
					if (onNode.getNodeType()!=Element.ELEMENT_NODE) continue;
					Element cAlt = (Element)onNode;
					if (!cAlt.hasAttribute("fs2-type")) continue; //ignore formatting elements
					if (!cAlt.hasAttribute("href")) throw new IllegalArgumentException("No href in filelist item.");
					if (!cAlt.hasAttribute("fs2-clientalias")) throw new IllegalArgumentException("No client alias in filelist item.");
					
					String cAlias = cAlt.getAttribute("fs2-clientalias");
					sources.put(cAlias, new DownloadSource(cAlias, new URL(cAlt.getAttribute("href"))));
					
				} finally {
					onNode = onNode.getNextSibling();
				}
			}
			
		} catch (IOException e) {
			Logger.warn("Couldn't get alternative download sources from indexnode '"+getName()+"': "+e);
		} catch (Exception e) {
			Logger.warn("Couldn't get alternative download sources from indexnode '"+getName()+"': "+e);
			e.printStackTrace();
		}
		
		return sources;
	}
	
	/**
	 * Generates a search URL. The search terms are specified in a raw string.
	 * @param query
	 * @return
	 */
	URL getSearchURL(String query) {
		try {
			return new URL(getActiveLocation().toString()+"/search/?q="+HttpUtil.urlEncode(query));
		} catch (Exception e) {
			Logger.severe("Cannot produce a search URL: "+e);
			e.printStackTrace();
			return null;
		}
	}
	
	/**
	 * Generate a browse url, the browse path is assumed to have had its elements URLencoded already!
	 * @param path
	 * @return
	 */
	URL getBrowseURL(String path) {
		try {
			return new URL(getActiveLocation().toString()+"/browse/"+path);
		} catch (Exception e) {
			Logger.severe("Cannot produce a browse URL: "+e);
			e.printStackTrace();
			return null;
		}
	}
	
	URL getAlternativesURL(String hash) {
		try {
			return new URL(getActiveLocation().toString()+"/alternatives/"+hash);
		} catch (Exception e) {
			Logger.severe("Cannot produce an alterntatives URL: "+e);
			e.printStackTrace();
			return null;
		}
	}
	
	/**
	 * Generates a url to the indexnode's stats page
	 */
	URL getStatsURL() {
		try {
			return new URL(getActiveLocation().toString()+"/stats");
		} catch (Exception e) {
			Logger.severe("Cannot produce a stats URL: "+e);
			e.printStackTrace();
			return null;
		}
	}
	
	/**
	 * Generate a chat URL for retreiving messages or saying a message.
	 * @param lastId the last ID received by this client.
	 * @param message the message to send. Use null if no message should be sent.
	 * @return the url
	 */
	URL getChatURL(int lastId, String message) {
		try {
			return new URL(getActiveLocation().toString()+"/chat/?lastmessage="+lastId+(message==null ? "" : "&say="+HttpUtil.urlEncode(message)));
		} catch (Exception e) {
			Logger.severe("Cannot produce a chat URL: "+e);
			e.printStackTrace();
			return null;
		}
	}

	URL getAvatarIconURL(String iconhash) {
		try {
			return new URL(getActiveLocation().toString()+"/avatars/"+iconhash+".png");
		} catch (MalformedURLException e) {
			Logger.severe("Couldn't construct an avatar icon fetch url: "+e);
			e.printStackTrace();
			return null;
		}
	}
	

	/**
	 * Get the url that submits our avatar to the indexnode.
	 * @return
	 */
	URL getAvatarSubmissionURL() {
		try {
			return new URL(getActiveLocation().toString()+"/submitavatar/?avatar="+HttpUtil.urlEncode(ssvr.getIndexNodeCommunicator().encodedAvatar));
		} catch (MalformedURLException e) {
			Logger.severe("Couldn't construct an avatar icon fetch url: "+e);
			e.printStackTrace();
			return null;
		}
	}

	
	public InputStream getClientAvatarStream(String iconhash) throws IOException {
		return getInputStreamFromIndexnode(getAvatarIconURL(iconhash));
	}

	public boolean isActive() {
		return status==Status.ACTIVE;
	}

	
}
