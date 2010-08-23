package indexnode;

import indexnode.IndexConfigDefaults.IK;
import indexnode.IndexNode.Client.RegistrationException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLContext;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import common.httpserver.HttpContext;
import common.httpserver.HttpExchange;
import common.httpserver.HttpHandler;
import common.httpserver.HttpServer;

import common.Config;
import common.FS2Constants;
import common.FS2Filter;
import common.FileList;
import common.HttpUtil;
import common.Logger;
import common.NamedThreadFactory;
import common.SimpleHttpHandler;
import common.Sxml;
import common.Util;
import common.Sxml.SXMLException;

/**
 * An implementation of an IndexNode for FS2.
 * @author Gary Plumbridge
 */
public class IndexNode {
	
	/** This is how clients register with us.
	 * A client informs us of their existence and to signal that new filelists might be available
	 * by requesting the /hello address.
	 * 
	 * 'secure' clients will have been dropped before this handler unless their credentials were accepted.
	 * 
	 */
	private class HelloHandler implements HttpHandler {
		public void handle(HttpExchange exchange) throws IOException {
			
			//Simple test to catch out web browsers:
			if (!exchange.getRequestHeaders().containsKey("fs2-version")) {
				Logger.log(exchange.getRemoteAddress().getAddress().getHostAddress()+" likely web-browser registration attempt.");
				HttpUtil.simpleResponse(exchange, "Invalid registration", 400);
				return;
			}

			//Get a client info, and test for sanity:
			SimpleClientInfo clInfo = getSimpleClientInfo(exchange);
			if (clInfo==null || clInfo.clientAddressID==null || clInfo.port<1 || clInfo.port>65535) {
				Logger.log(exchange.getRemoteAddress().getAddress().getHostAddress()+" failed to supply the correct headers.");
				HttpUtil.simpleResponse(exchange, "Invalid registration", 400);
				return;
			}
			
			Client cl;
			//looking good, check if they are already registered:
			synchronized (clients) {
				cl = clients.get(clInfo.clientAddressID);
				if (cl!=null) {
					cl.clientSaidHello(clInfo);
					helloSuccess(exchange, cl);
					return;
				}
			}
			
			try {
				cl = new Client(clInfo);
				helloSuccess(exchange, cl);
				return;
			} catch (RegistrationException e) {
				Logger.warn("Client '"+clInfo.clientAddressID+"' failed registration: "+e);
				HttpUtil.simpleResponse(exchange, "Uncontactable client", 412);
			}
		}

		private void helloSuccess(HttpExchange exchange, Client cl) {
			try {
				exchange.getResponseHeaders().add("fs2-avatarhash", cl.getAvatarHash()); //let the client know what we think their avatar is.
				HttpUtil.simpleResponse(exchange, "", 200);
			} catch (IOException e) {
				Logger.log("While sending 'welcome' to the ungrateful bastard: "+e);
			}
		}
	}
	
	/**
	 * Represents basic information about a client, non authoritive!
	 * It's just intended to hold scraped headers. These values MUST not be trusted.
	 * 
	 * This object should be used to obtain a client object which may then be used authoritavely.
	 * 
	 * @author gary
	 */
	public final class SimpleClientInfo {
		final InetSocketAddress clientAddressID;
		final boolean local;
		final int port;
		final long cltoken;
		final String alias;
		final boolean secure;
		public SimpleClientInfo(InetSocketAddress cai, boolean local, int port, long cltoken, String alias, boolean secure) {
			this.port = port;
			this.local = local;
			this.clientAddressID = cai;
			this.cltoken = cltoken;
			this.alias = alias;
			this.secure = secure;
		}
	}
	
	/**
	 * Returns the string that identifies the remote client to us uniquely.
	 * This can be used as a key to lookup the client from the Clients map.
	 * 
	 * Note that this will return null if the client does not specify the appropriate headers or they do not parse correctly.
	 * It is safe to reject these clients.
	 * (therefore this should never be used to reject web-browser based clients from browsing/searching)
	 * 
	 * @param exchange
	 * @param localClient
	 * @return The ID string, or null if there is not enough information from the client to construct one.
	 */
	public InetSocketAddress getRemoteAddressIDStr(HttpExchange exchange) {
		return getSimpleClientInfo(exchange).clientAddressID;
	}
	
	/**
	 * Gets simple info about the client (must be a registerable client, not a browser)
	 * Returns null if an internal exception was caught.
	 * @param exchange
	 * @return The SimpleClientInfo, this will not have been checked for sanity!
	 */
	protected SimpleClientInfo getSimpleClientInfo(HttpExchange exchange) {
		try {
			boolean localClient = false;
			int onPort = -1;
			long cltoken = 0;
			String alias = "";
			InetAddress clientAddr = exchange.getRemoteAddress().getAddress();
			if (clientAddr.isLoopbackAddress()) {
				//The loopback address is not world accessable, so try to find out how others can contact us.
				//This strongly depends on working wins/dns!
				clientAddr = InetAddress.getLocalHost();
				localClient = true;
			}
			InetSocketAddress clientAddress = null;
			try {
				cltoken = Long.parseLong(exchange.getRequestHeaders().getFirst("fs2-cltoken")); //this token is required in order them to be registered!
				onPort = Integer.parseInt(exchange.getRequestHeaders().getFirst("fs2-port"));
				//Aliases are optional: (although an authenticator may decide to enforce them, that is not the task here)
				if (exchange.getRequestHeaders().containsKey("fs2-alias")) {
					alias = exchange.getRequestHeaders().getFirst("fs2-alias");
				}
				clientAddress = new InetSocketAddress(clientAddr, onPort);
			} catch (Exception e) {
				//onPort and clientAddress are already initialised to error values.
			}
			return new SimpleClientInfo(clientAddress, localClient, onPort, cltoken, alias, exchange.isSecure());
		} catch (UnknownHostException e) {
			//unknown host exception on localhost? we're in trouble!
			Logger.severe("This system seems insane: "+e);
			e.printStackTrace();
			return null;
		}
	}
	
	/**
	 * Gets a client object from a request to one of our httpservers, useful for chat and registration.
	 * @param exchange The http request of the exchange.
	 * @return the client object that belongs to the request made.
	 */
	public Client getClientFromExchange(HttpExchange exchange) {
		synchronized (clients) {
			//Logger.log(getRemoteAddressIDStr(exchange));
			return clients.get(getRemoteAddressIDStr(exchange));
		}
	}
	
	/**
	 * Represents connected clients.
	 * @author gary
	 */
	public class Client {
		private InetSocketAddress address;
		//all of this client's shares:
		HashMap<String,Share> shares = new HashMap<String, Share>();
		private int failedLiveness = 0;
		private URL pingURL;
		private String alias = "";
		private AliveTimerTask alivenessTask = new AliveTimerTask();
		private FilesystemEntry filesystemRoot; //Our root in the filesystem.
		private boolean local;
		private long cltoken;
		private boolean secure;
		private String avatarhash = "";
		
		public boolean isLocal() {
			return local;
		}
		
		public String getAvatarHash() {
			return avatarhash;
		}

		public boolean isSecure() {
			return secure;
		}
		
		public long getCltoken() {
			return cltoken;
		}
		
		@SuppressWarnings("serial")
		public class RegistrationException extends Exception {
			public RegistrationException(String message) {
				super(message);
			}
		};
		
		public FilesystemEntry getFilesystemRoot() {
			return filesystemRoot;
		}

		private void setAlias(String potential) {
			String newAlias = potential;
			if (potential.equals("")) newAlias = "Unnamed@"+address.getAddress().getHostAddress()+":"+address.getPort();
			newAlias = newAlias.replace('/', ' ');
			//Determine if this alias is already in use:
			synchronized (clientAliases) {
				//case 1: The new alias is the same as our old. Do nothing.
				if (newAlias.equals(alias)) return;
				
				boolean resetAlias = false;
				//case 2: The new alias conflicts with somebody that is not us, so rename to a new alias.
				if (clientAliases.contains(newAlias)) resetAlias = true;
				
				//case 3: The user has requested a reserved alias, and is not allowed it:
				if (users.isLocalonly(newAlias) && !local) resetAlias = true;
				
				if (resetAlias) newAlias += "@"+address.getAddress().getHostAddress()+":"+address.getPort();
				
				//case 4: The new alias does not conflict, so rename us:
				clientAliases.remove(alias);
				clientAliases.add(newAlias);
				alias = newAlias;
				//rename our database entry if we have one
				if (filesystemRoot != null) filesystemRoot.rename(newAlias);
			}
		}
		
		private boolean aliveQuerySubmitted = false;
		private class AliveTimerTask extends TimerTask {
			public void run() {
				synchronized (Client.this) {
					if (aliveQuerySubmitted) {
						Logger.warn("Client '"+alias+"' is responding slower than our ping interval, they might be silently dropping our packets... <hint value=\"firewall\"/> ;)");
						return; //only allow one onto the queue (this might overlap if there is a firewall on the client that is preventing connection refused messages)
					}
					aliveQuerySubmitted = true;
				}
				clientPingPool.submit(new Runnable() {
					@Override
					public void run() {
						checkLiveness();
						synchronized (Client.this) {
							aliveQuerySubmitted = false; //Allow another potential ping task.
						}
					}
				});
			}
		}
		
		public void clientSaidHello(SimpleClientInfo info) {
			//Logger.log("Hello received from: "+alias);
			//We've seen from them again, so reset the failed liveness.
			failedLiveness = 0;
			//update clToken, it might have changed:
			cltoken = info.cltoken;
			secure = info.secure;
			//tell the existing object to update.
			updateFromClient();
		}
		
		/**
		 * Creates a new Client and adds it to the refresh list and the list of all clients:
		 */
		Client(SimpleClientInfo info) throws RegistrationException {
			address = info.clientAddressID;
			this.local = info.local;
			setAlias(info.alias);
			this.cltoken = info.cltoken;
			this.secure = info.secure;
			
			if (local) Logger.warn("Connection from loopback addresses may not work unless valid DNS/WINS servers are available!");
			
			try {
				pingURL = new URL("http://"+getURLAddress()+"/ping");
			} catch (MalformedURLException e) {
				Logger.severe("Failed to create ping URL: "+e.toString());
			}
			
			//Check that the client given is valid:
			if (!isAlive()) {
				//To prevent the current alias from being "reserved" when this client drops out:
				synchronized (clientAliases) {
					clientAliases.remove(alias);
				}
				throw new RegistrationException("The client specified is uncontactable.");
			}
			
			synchronized (clients) {
				//Got this far, so register this client with the world.
				filesystemRoot = fs.registerClient(this);
				clients.put(address, this);
			}
			
			//Add this freshly created client to the refresh timer:
			clientLivenessTimer.schedule(alivenessTask, FS2Constants.INDEXNODE_PING_CLIENT_INTERVAL_MS, FS2Constants.INDEXNODE_PING_CLIENT_INTERVAL_MS);
			
			Logger.log(alias+" ("+address+") has registed successfully.");
			//trigger an update of the client's shares:
			updateFromClient();
		}
		
		/**
		 * This is triggered when a client asks for us to consider their sharelist.
		 * Requests a list of shares and updated shares from the client:
		 * 
		 * This should complete pretty quickly as the client is waiting for the response.
		 * (The reference client waits 5 seconds for a response)
		 * 
		 */
		private synchronized void updateFromClient() {
			//Logger.log("Updating "+alias+"'s share lists...");
			try {
				//Get the sharelist.
				InputStream pingXml = getClientPing();
				Sxml clientShares;
				try {
					clientShares = new Sxml(pingXml);
				} finally {
					pingXml.close();
				}
				
				//1) build a new list of all our shares
				HashMap<String, Share> sharesToKill = new HashMap<String, Share>();
				synchronized (shares) {
					sharesToKill.putAll(shares);
				
					//2) for each share the client reported, delete that item from the list and update the share.
					Node root = clientShares.getDocument().getElementsByTagName("shares").item(0);
					if (root != null) {
						Node onNode = root.getFirstChild();
						while (onNode != null) {
							if (onNode.getNodeType() == Element.ELEMENT_NODE) {
								Element thisElem = (Element)onNode;
								String thisShareName = thisElem.getAttribute("name");
								int thisShareRevision = Integer.parseInt(thisElem.getAttribute("revision"));
								ShareType type = ShareType.XML;
								if (thisElem.getAttribute("type").equalsIgnoreCase("filelist")) type = ShareType.FILELIST;
								//a) it still exists, so no need to kill this share
								sharesToKill.remove(thisShareName);
								//b) Did it exist already? If it did: update it(if needed), otherwise create it.
								Share thisShare = shares.get(thisShareName);
								if (thisShare != null) {
									refreshShare(thisShare, thisShareRevision);
								} else {
									addShare(thisShareName, thisShareRevision, type);
								}
							} //If
							onNode = onNode.getNextSibling();
						} //While
					}
					//3) for each item remaining on our list, delist that share.
					for (Share shareToDie : sharesToKill.values()) {
						removeShare(shareToDie);
					}
					
				} //END synchronize on shares
			} catch (Exception e) {
				Logger.warn("Exception updating "+alias+"'s sharelist: "+e.toString());
			}
		}
		
		/**
		 * Checks if a client still exists:
		 * (will ignore failures for a set number of times, then will delist this client)
		 */
		public void checkLiveness() {
			//If success then all is good, reset bad counter.
			if (isAlive()) {
				failedLiveness = 0;
			} else {
				Logger.log(alias+" missed a ping! ("+Integer.toString(failedLiveness+1)+"/"+Integer.toString(FS2Constants.INDEXNODE_CLIENT_MAX_FAILED_LIVENESSES+1)+")");
				failedLiveness++;
				if (failedLiveness > FS2Constants.INDEXNODE_CLIENT_MAX_FAILED_LIVENESSES) {
					destroyClient();
				}
			}
		}
		
		/**
		 * Checks to see if this client responds correctly to one 'ping'
		 */
		private boolean isAlive() {
			try {
				getClientPing().close();
				return true;
			} catch (IOException e) {
				//e.printStackTrace();
				return false;
			}
		}
//		
//		/**
//		 * Returns true if this client is still actively registered with the indexnode.
//		 * @return
//		 */
//		public boolean stillAlive() {
//			return failedLiveness <= FS2Constants.INDEXNODE_CLIENT_MAX_FAILED_LIVENESSES;
//		}
		
		/**
		 * Establish a connection to the client's ping URL and return the inputstream.
		 * Close the stream when done.
		 */
		private InputStream getClientPing() throws IOException {
			URL toUse = pingURL;
			if (isSecure()) toUse = FS2Filter.getFS2SecureURL(pingURL);
			HttpURLConnection connection = (HttpURLConnection) toUse.openConnection();
			fs2Filter.fs2FixupURLConnectionForIndexNode(connection, cltoken);
			try {
				//Update from the client's alias on each ping:
				String newAlias = connection.getHeaderField("fs2-alias");
				
				InputStream inS = connection.getInputStream();
				if (newAlias != null) {
					setAlias(newAlias);
				}

				return inS;
			} finally {
				InputStream es = connection.getErrorStream();
				if (es!=null) es.close();
			}
		}
		
		/**
		 * Removes this client from the list of clients and the refreshQueue.
		 * -also delists every share for this client.
		 */
		private void destroyClient() {
			alivenessTask.cancel();
			synchronized (clients) {
				clients.remove(address);
			}
			synchronized (shares) {
				//We must create a copy of the list of shares as it'll be modified each time we remove a share.
				LinkedList<Share> sharesCopy = new LinkedList<Share>();
				sharesCopy.addAll(shares.values());
				for (Share share : sharesCopy) {
					removeShare(share);
				}
			}
			synchronized(clientAliases) {
				clientAliases.remove(alias);
			}
			fs.deregisterClient(filesystemRoot);
			Logger.log("Client "+alias+" has been disconnected.");
		}
		
		/**
		 * Makes this share exist if it doesn't already:
		 * Idempotent!
		 */
		private void addShare(String name, int startRevision, ShareType type) {
			synchronized (shares) {
				if (!shares.containsKey(name)) {
					Share newShare = new Share(name,this,type);
					shares.put(name, newShare);
					synchronized (allShares) {
						allShares.put(newShare.shareUID, newShare);
					}
					refreshShare(newShare, startRevision);
				}
			}
		}
		
		/**
		 * Removes and delists a share if it exists.
		 */
		private void removeShare(Share share) {
			synchronized (shares) {
				if (shares.containsValue(share)) {
					shares.remove(share.getName());
					share.delistShare();
					synchronized (allShares) {
						allShares.remove(share.shareUID);
					}
				}
			}
		}
		
		/**
		 * Put the share specified on the queue to be refreshed:
		 * (must exist, be a newer revision)
		 */
		private void refreshShare(Share thisShare, int newRevision) {
			if (thisShare != null) {
				if (newRevision > thisShare.getRevision()) {
					//Set the revision to the new one even though we haven't actually refreshed yet.
					//This is to ensure we don't keep filling the queue with many refreshes for the same revision.
					//This isn't perfect, (as if a refresh fails then we still think we're up to date) but it's a tradeoff.
					thisShare.setRevision(newRevision);
					refreshSharesPool.submit(thisShare);
				}
			}
		}
		
		/**
		 * Gets the address and port of this client in a form that can be placed into an URL:
		 * @return
		 */
		public String getURLAddress() {
			if (address.getAddress() instanceof Inet6Address) {
				return "["+address.getAddress().getHostAddress()+"]:"+address.getPort();
			} else {
				return address.getAddress().getHostAddress()+":"+address.getPort();
			}
		}

		public String getAlias() {
			return alias;
		}

		public void setAvatarHash(String avHash) {
			this.avatarhash = avHash;
		}
	}
	
	public enum ShareType {XML, FILELIST};
	
	/**
	 * The class to represent a single share from a client.
	 * It's run from a threadpool to refresh it.
	 */
	public class Share implements Runnable {
		private ShareType type;
		private String name;
		private Client owner;
		private int revision = 0;
		private int shareUID; //In the database, filesystem objects with this ID belong to this share.
		public int getShareUID() {
			return shareUID;
		}
		private boolean listed = false;

		//Once delisted this share is defunct and may not be refreshed.
		private boolean delisted = false;
		
		Share(String inname, Client inowner, ShareType type) {
			name = inname;
			owner = inowner;
			this.type = type;
			synchronized (nextShareUID) {
				shareUID = nextShareUID;
				nextShareUID +=1;
			}
			Logger.log("Share "+name+" on "+owner.alias+" has been created and now must be refreshed...");
		}
		
		public String getName() {
			return name;
		}
		
		public Client getOwner() {
			return owner;
		}
		
		/*
		 * Executes refreshShare. This is to allow a share to be added to a threadpool for execution.
		 */
		public void run() {
			try {
				refreshShare();
			} catch (Throwable e) { //all possible, including vm errors
				Logger.severe(e.toString()+" caught but not handleable in the share-refresher thread.");
				e.printStackTrace();
			}
		}
		
		/**
		 * Does the hard work of refreshing this share:
		 * 1) Gets the new file list from the client
		 * 2) Removes this share's entries from the database
		 * 3) Adds this share's files to the database
		 */
		private synchronized void refreshShare() {
			//Logger.log("Refreshing share "+name+" on "+owner.getAlias());
			
			//Only continue if not delisted.
			if (delisted) return;
			/*
			 * 1) Get/parse the fileList XML from the client.
			 * 2) Set our revision to that of this filelist
			 * 3) Remove all existing entries for this share.
			 *  ) Find the toplevel directory in the fs for this share
			 * 4) Add (recursively) to the filesystem, keeping a total of this sharesize.
			 * 5) done!
			 */
			try {
				if (type==ShareType.XML) {
					importXML();
				} else if (type==ShareType.FILELIST) {
					importFileList();
				}
				listed = true;
			} catch (FileNotFoundException e) { 
				Logger.warn("Filelist not found: "+e);
				this.revision = errorRevision; //rollback revision so we can retry again later.
			} catch (IOException e) {
				Logger.warn("IOException refreshing share: "+name+"... "+e.toString());
				this.revision = errorRevision; //rollback as it was probably just a network problem.
				e.printStackTrace();
			} catch (Exception e) {
				Logger.warn("Exception refreshing share "+name+"... "+e.toString());
				e.printStackTrace(); //do not bother to rollback. Something more serious is wrong so retrying will just damage our QOS.
			}
			Logger.log("Refresh complete (share "+name+" on "+owner.getAlias()+")");
		}
		
		private void importFileList() throws IOException {
			URL filelistURL = new URL("http://"+owner.getURLAddress()+"/filelists/"+HttpUtil.urlEncode(name)+".FileList");
			if (owner.isSecure()) filelistURL = FS2Filter.getFS2SecureURL(filelistURL);
			
			HttpURLConnection conn = (HttpURLConnection) filelistURL.openConnection();
			InputStream is = null;
			try {
				fs2Filter.fs2FixupURLConnectionForIndexNode(conn, owner.getCltoken());
				is = new BufferedInputStream(conn.getInputStream());
				FileList list = FileList.reconstruct(is);
				if (list==null) throw new IllegalArgumentException("A FileList object couldn't be reconstructed.");
				if (listed) fs.delistShare(this);
				fs.importShare(list.root, this);
			} finally {
				try {
					if (is!=null) is.close();
				} finally {
					InputStream es = conn.getErrorStream();
					if (es!=null) es.close();
				}
			}
		}

		private void importXML() throws SXMLException,
				IOException {
			URL filelistURL = new URL("http://"+owner.getURLAddress()+"/filelists/"+HttpUtil.urlEncode(name)+".xml");
			if (owner.isSecure())filelistURL = FS2Filter.getFS2SecureURL(filelistURL);
			
			HttpURLConnection conn = (HttpURLConnection) filelistURL.openConnection();
			InputStream is = null;
			fs2Filter.fs2FixupURLConnectionForIndexNode(conn, owner.getCltoken());
			try {
				is = conn.getInputStream();
				Sxml flXML = null;
				flXML = new Sxml(is);
				Element flElement = (Element)flXML.getDocument().getElementsByTagName("filelist").item(0);
				revision = Integer.parseInt(flElement.getAttribute("revision"));
				//Mmmm it's easy now.
				if (listed) fs.delistShare(this);
				fs.importShare(flElement, this);
			} finally {
				try {
					if (is!=null) is.close();
				} finally {
					InputStream es = conn.getErrorStream();
					if (es!=null) es.close();
				}
			}
		}
		
		//Removes all trace of this share from the database
		public synchronized void delistShare() {
			delisted = true;
			try {
				if (listed) fs.delistShare(this);
			} catch (Exception e) {
				Logger.warn("Exception delisting share: "+e.toString());
				e.printStackTrace();
			}
			Logger.log("Share "+name+" on "+owner.getAlias()+" has been delisted.");
		}

		public int getRevision() {
			return revision;
		}

		int errorRevision; //if share refreshing fails we'll revert to this saved revision.
		public void setRevision(int revision) {
			errorRevision = this.revision;
			this.revision = revision;
		}

		public long getSize() {
			FilesystemEntry me = owner.getFilesystemRoot().getNamedChild(name);
			if (me != null)	{
				return me.getSize();
			} else {
				return 0;
			}
		}
	}
	
	
	//All the clients we currently know about: address->clientObject
	HashMap<InetSocketAddress, Client> clients = new HashMap<InetSocketAddress, Client>();
	//Keep a list of all current aliases to avoid conflicts:
	private HashSet<String> clientAliases = new HashSet<String>();
	
	//This timer is used to check known clients on an interval to see if they are still alive.
	private Timer clientLivenessTimer = new Timer(true);
	
	/**
	 * Allows looking up of share objects from the database which only uses integers to identify them.
	 */
	private HashMap<Integer, Share> allShares = new HashMap<Integer, Share>();
	
	/**
	 * The pool of threads for refreshing shares.
	 * It's a fixed thread pool to limit the number of threads thrashing the filesystem with massive updates at once.
	 * When the threads are all used up a queue is maintained to wait for free threads.
	 */
	private ExecutorService refreshSharesPool;
	private ExecutorService clientPingPool = Executors.newCachedThreadPool(new NamedThreadFactory(true, "Client ping"));
	private ExecutorService httpServicePool = Executors.newCachedThreadPool(new NamedThreadFactory(true, "http service"));
	
	private Integer nextShareUID = 1;
	private FS2Filter fs2Filter;
	private IndexAuthFilter authFilter = null;
	Date startedDate = new Date();
	Filesystem fs;
	private int onPort;
	/** when true this indexnode is in secure mode*/
	private boolean useSecure;
	private boolean dhanonUsed;
	private UserDatabase users;
	private SSLContext sslcontext;
	private HelloHandler hh;
	private IndexBrowser ib;
	private IndexRootHandler irh;
	private IndexSearch is;
	private IndexStatistics stats;
	private IndexDownloader downloader;
	private IndexAlternatives alts;
	private IndexAvatar avatar;
	private ChatDatabase chat = new ChatDatabase();
	private IndexChat ic;
	private Config conf;
	private IndexAdvertismentManager advertManager;
	
	//used for shutdown:
	private ArrayList<HttpServer> httpServers = new ArrayList<HttpServer>();
	
	public Config getConf() {
		return conf;
	}
	
	public ChatDatabase getChat() {
		return chat;
	}
	
	public UserDatabase getUsers() {
		return users;
	}
	
	public boolean isSecure() {
		return useSecure;
	}
	
	/**
	 * Constucts an indexnode that is not internal
	 * @param conf
	 * @param internal
	 * @throws Exception
	 */
	public IndexNode(Config conf) throws Exception {
		this(conf, false, "");
	}
	
	/**
	 * Constructs a new IndexNode server. This constructor is for internal indexnodes.
	 * @param confFile The configuration file to use for this indexnode.
	 * @param internal true iff this is executing within a client;
	 * @param pathPrefix the directory into which indexnode things can be stored.
	 */
	public IndexNode(final Config conf, boolean internal, String pathPrefix) throws Exception {
		this.conf = conf;
		
		//Initialise filesystem:
		fs = new NativeFS();
		
		//Setup the the thread pool for refreshing client shares:
		refreshSharesPool = Executors.newFixedThreadPool(conf.getInt(IK.FILESYSTEM_UPDATE_POOLSIZE), new NamedThreadFactory(true, "Share refresh"));
		
		onPort = conf.getInt(IK.PORT);
		
		//Load users database:
		users = new UserDatabase(new File(pathPrefix+conf.getString(IK.USER_DATABSE)), this);
		
		//security things
		dhanonUsed = Boolean.parseBoolean(conf.getString(IK.DHANON_TLS));
		useSecure = Boolean.parseBoolean(conf.getString(IK.SECURE_MODE));
		if (dhanonUsed) Util.enableDHanonCipherSuite();
		sslcontext = SSLContext.getInstance("TLS");
		sslcontext.init(null, null, null);
		authFilter = new IndexAuthFilter(users, this); //the authorisation filter is always used regardless of security mode.
		if (useSecure) {
			Logger.log("Running in secure mode. Only connections via a TLS socket will be accepted.");
		} else {
			Logger.severe("Running in INSECURE mode. Plain-text sockets without authentication will be accepted!");
		}
		fs2Filter = new FS2Filter();
		
		fs2Filter.setAlias(conf.getString(IK.ALIAS));
		fs2Filter.setPort(onPort);
		
		hh = new HelloHandler();
		ib = new IndexBrowser(fs);
		irh = new IndexRootHandler();
		is = new IndexSearch(fs);
		stats = new IndexStatistics(this);
		downloader = new IndexDownloader(fs);
		ic = new IndexChat(chat, this);
		alts = new IndexAlternatives(fs);
		avatar = new IndexAvatar(new File(pathPrefix+conf.getString(IK.AVATAR_CACHE_PATH)), this);
		
		String bindTo = conf.getString(IK.BIND_INTERFACE);
		
		if (bindTo.equals("all")) {
			Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
			while (ifs.hasMoreElements()) {
				listenOnInterface(ifs.nextElement());
			}
		} else {
			if (bindTo.equals("")) {
				Logger.log("You must specify a bind-interface (or \"all\") in your configuation!\nExiting...");
				return;
			}
			listenOnInterface(NetworkInterface.getByName(bindTo));
		}
		
		//internal indexnodes do not advertise themselves.
		if (!internal && conf.getBoolean(IK.ADVERTISE)) {
			final long advertuid = conf.getLong(IK.ADVERTUID);
			final long capability = generateCapabilityValue();
			advertManager = new IndexAdvertismentManager(conf, new AdvertDataSource() {
				
				/**
				 * Real, standalone indexnodes are always prospective. (because they always run, and want to inhibit worse autoindexnodes)
				 */
				@Override
				public boolean isProspectiveIndexnode() {
					return true;
				}
				
				@Override
				public int getPort() {
					return onPort;
				}
				
				/**
				 * Return our capability.
				 */
				@Override
				public long getIndexValue() {
					return capability;
				}
				
				@Override
				public long getAdvertUID() {
					return advertuid;
				}
	
				@Override
				public boolean isActive() {
					return true; //a standalone indexnode can't be inactive.
				}
			});
		}
	}
	
	public static long generateCapabilityValue() {
		Random r = new Random();
		
		//round to the nearest 100k
		long c = Runtime.getRuntime().maxMemory()/100000; 
		c *= 100000;
		
		c += (Math.abs(r.nextLong())%100000); //add noise to the end of the number.
		
		return c;
	}
	
	public IndexAdvertismentManager getAdvertManager() {
		return advertManager;
	}

	/**
	 * Destroys an indexnode instance.
	 */
	public void shutdown() {
		if (advertManager!=null) advertManager.shutdown();
		
		for (HttpServer hs : httpServers) {
			hs.stop();
		}
		
		clientLivenessTimer.cancel();
	}
	
	private void listenOnInterface(NetworkInterface if0) throws IOException, SocketException {
		InetSocketAddress addr;
		Enumeration<InetAddress> addrs = if0.getInetAddresses();
		if (addrs.hasMoreElements()) {
			while (addrs.hasMoreElements()) {
				addr = new InetSocketAddress(addrs.nextElement(), onPort);
				bindToAddress(addr);
			}
		} else {
			Logger.warn("Not listening on "+if0.getDisplayName()+" it has no addresses.");
			return;
		}
	}

	/**
	 * Binds the indexnode to the addr supplied and advertises too.
	 * @param advertiseOn Set this to null or equal to addr to advertsise on addr's address.
	 * @param addr
	 * @throws IOException
	 * @throws SocketException
	 */
	private void bindToAddress(InetSocketAddress addr)
			throws IOException, SocketException {
		//Setup and bind the http server:
		HttpServer http = startHttpServer(addr);
		if (http==null) return;
		httpServers.add(http);
		
		//Enable a multithreaded executor:
		http.setExecutor(httpServicePool);
		//Setup the contexts:
		
		//1) The "i'm here" context to register clients and get the searchnode to check updated file lists:
		addContext(http, "/hello", hh);
		
		//2) The filesystem browser... Let's surf the filesystem!
		addContext(http, "/browse", ib);
		
		//2.1) this is also the root context:
		//      (not actually mapped here though, just redirected)
		HttpContext hc = http.createContext("/", irh);
		hc.getFilters().add(fs2Filter); //NOTE: this handler does not require a password, this is for secure access testing.
		
		//3) the search interface:
		addContext(http, "/search", is);
		
		addContext(http, "/stats", stats);
		
		addContext(http, "/download", downloader);
		
		addContext(http, "/chat", ic);
		
		addContext(http, "/alternatives", alts);
		
		addContext(http, "/submitavatar", avatar);
		
		addContext(http, "/avatars", avatar.getAvatarHandler()); //allow clients to download avatars from the cache.
		
		//Tell robots to GTFO:
		http.createContext("/robots.txt",new SimpleHttpHandler(200,"User-agent: *\nDisallow: /\n"));
		
		// Start the web server:
		http.start();
	}
	
	void addContext(HttpServer server, String path, HttpHandler handler) {
		HttpContext hc = server.createContext(path, handler);
		hc.getFilters().add(fs2Filter);
		hc.getFilters().add(authFilter);
	}

	private HttpServer startHttpServer(InetSocketAddress addr) {
		HttpServer http = null;
		try {
			http = HttpServer.create((useSecure ? null : addr), new InetSocketAddress(addr.getAddress(), onPort+1), sslcontext, (dhanonUsed ? new String[] {FS2Constants.DH_ANON_CIPHER_SUITE_USED} : null), 0);
			http.setSoTimeout(FS2Constants.SERVER_URL_CONNECTION_TIMEOUT_MS);
			http.setUseKeepAlives(false); //no part of fs2 uses persistent, idle connections.
			return http;
		} catch (IOException e) {
			Logger.warn("Can't bind to: "+addr+", because: "+e);
			//e.printStackTrace();
			return null;
		}
	}

	public void setAlias(String newAlias) {
		fs2Filter.setAlias(newAlias);
	}

	public void setAutomatic(boolean b) {
		fs2Filter.setAutomatic(b);
	}
	
}
