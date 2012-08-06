package common;

public class FS2Constants {
	/**Timeout to wait for a connection on a client (10s)*/
	public static final int CLIENT_URL_CONNECTION_TIMEOUT_MS = 10*1000;
	
	/**Timeout to wait for a slow client (5s)*/
	public static final int SERVER_URL_CONNECTION_TIMEOUT_MS = 5*1000;
	
	/**The version identifier for the method clients and indexnodes communicate: This must never include a ':' symbol!*/
	public static final String FS2_PROTOCOL_VERSION = "fs2protocol-0.13"; 

	/** Arbitrarily sized buffers are 64KiB at the moment:*/
	public static final int ARBITRARY_BUFFER_SIZE = 1024*64;
	
	/**
	 * How often will the filesystem consider refreshing its cache?
	 */
	public static final int CLIENT_REFRESH_FILESYSTEM_CACHE_INTERVAL = 1000*10;
	
	/** the minimum time between fast unimportant events triggering events in millis
	 * A primary example is a download causing "i've changed" events to the gui.
	 * Default 4 per second.
	 *  */
	public static final int CLIENT_EVENT_MIN_INTERVAL = 500;
	
	/**
	 * How many concurrent filesystem requests (to indexnodes) can the client make simultaniously.
	 */
	public static final int CLIENT_MAX_CONCURRENT_FILESYSTEM_REQUESTS = 3;
	
	/**
	 * How many concurrent chat requests (to indexnodes) can the client make simultaniously.
	 */
	public static final int CLIENT_MAX_CONCURRENT_CHAT_REQUESTS = 3;
	
	/**
	 * The interval between checking indexnodes for chat messages: (in milliseconds)
	 */
	public static final int CLIENT_CHAT_POLL_INTERVAL = 5*1000;
	
	/**How long do tokens last in the upload queue before they expire?*/
	public static final long CLIENT_TIMEDQUEUE_TOKEN_EXPIRY_INTERVAL = 20*1000L;
	
	/**How many seconds does the client's browser wait to refresh if in a queue or watching chat?*/
	public static final int BROWSER_META_REFRESH_INTERVAL = 5;
	
	/** Deterines how long the client will wait for a shutdown of all its elements. After this delay the VM will be terminated.
	 * 10 seconds...
	 * */
	public static final int CLIENT_SHUTDOWN_MAX_MILLIS = 10000;
	
	/**What algorithm should be used for file hashes?*/
	public static final String FILE_DIGEST_ALGORITHM = "MD5";
	//Md5 currently as it's implementation is polished on desktop platforms and we don't need cryptographically strong hashes.
	
	/** The hash version we are using for fs2, so we know to not
	 *  accept incompatible hashes in the indexnode and clients know to
	 *  recreate hashes if they are obsolete. */
	public static final String FILE_DIGEST_VERSION_XML = "MD5-v4";
	public static final int FILE_DIGEST_VERSION_INT = 4;
	
	/**How much of the start and end of files should be digested? */
	public static final long FILE_DIGEST_HEAD_FOOT_LENGTH = 64*1024; //64KiB
	
	/**How often the indexnode pings its clients (20s)*/
	public static final int INDEXNODE_PING_CLIENT_INTERVAL_MS = 20*1000;
	
	/**How many times is the client allowed to fail a ping before they are delisted?*/
	public static final int INDEXNODE_CLIENT_MAX_FAILED_LIVENESSES = 2; //delist on third<<<

	/**The maximum number of search results to return to a client*/
	public static final int INDEXNODE_SEARCH_MAX_RESULTS = 200;
	
	/**How often should the indexnode advertise itself? (a second)*/
	public static final int INDEXNODE_ADVERTISE_INTERVAL_MS = 1*1000;
	
	/** how often should an internal indexnode consider if it should run? */
	public static final int INTERNAL_INDEXNODE_RECONSIDER_INTERVAL_MS = 4000;
	
	/** how long should the capability table last for before entries are dropped?*/
	public static final int INDEXNODE_CAPABILITY_TABLE_DECAY = 20*1000;
	
	/**How often should the indexnode cache statistics for? (10 seconds)*/
	public static final int INDEXNODE_CACHE_STATISTICS_DURATION = 1000*10;
	
	/** the maximum number of chat log entries the indexnode will keep */
	public static final int INDEXNODE_CHAT_LOG_LENGTH = 100;
	
	/**The port that FS2 uses for advertisments*/
	public static final int ADVERTISMENT_DATAGRAM_PORT = 42444;
	
	/**Name of the file used for configuration on the CLIENT*/
	public static final String CONFIGURATION_FILE_NAME = "config.xml";
	
	/** The minimum interval between configuration saves */
	public static final long CONFIG_SAVE_INTERVAL = 5*1000;
	
	/** The name of the directory (within the platform root) that code updates are kept*/
	public static String CLIENT_UPDATES_SUBDIRECTORY = "updates";
	
	/**
	 * This defines the minimum interval between stats saving to disk. 1m currently.
	 */
	public static final long CLIENT_PEERSTATS_SAVE_MIN_INTERVAL = 60*1000;
	
	/**
	 * This defines the minimum interval between saving download queues to disk. 1m currently.
	 */
	public static final long CLIENT_DOWNLOADQUEUE_SAVE_MIN_INTERVAL = 60*1000;
	
	/** The minimum number of milliseconds between each iteration of the download dispatcher*/
	public static final int CLIENT_DOWNLOAD_DISPATCH_ITERATION_THROTTLE = 1000;
	
	public static final String CLIENT_LOGS_SUBDIRECTORY = "logs";
	
	/**How long the client waits for the indexnode to ping before re-announcing itself (twice indexnode ping interval)*/
	public static final int CLIENT_PING_WAIT_TIMEOUT_MS = 2*INDEXNODE_PING_CLIENT_INTERVAL_MS;
	
	/** How long does a download have to be active before being split? in milliseconds*/
	public static final long CLIENT_DOWNLOAD_MIN_SPLIT_INTERVAL = 10*1000;
	
	/** How what is the maximum amount (in percent as a float) of the chunk that may have completed if it is to be split?*/
	public static final float CLIENT_DOWNLOAD_CHUNK_SPLIT_PERCENT = 50f;

	/**The number of milliseconds between updating the status bar in the client gui.*/
	public static final int CLIENT_STATUS_BAR_UPDATE_INTERVAL = 1000;

	/** The name of the default download directory share*/
	public static final String CLIENT_DEFAULT_SHARE_NAME = "Default download directory";

	/** The client autorefreshes shares that have new items downloaded into them, but not more often than this:*/
	public static final long CLIENT_DOWNLOADED_TO_SHARE_REFRESH_MIN_INTERVAL = 60*1000;

	/**The number of milliseconds between considering if shares need refreshing: 10s*/
	public static final long CLIENT_SHARE_REFRESH_POLL_INTERVAL = 10*1000;
	
	/**the number of milliseconds between considering if browseTree nodes should be collapsed to save load on the indexnode*/
	public static final int CLIENT_BROWSETREE_COLLAPSE_POLL_INTERVAL = 5*1000;
	
	/**The number of milliseconds an idle leaf in the browse tree should remain open for. This is also the number of seconds an idle search will remain open for*/
	public static final int CLIENT_BROWSETREE_COLLAPSE_INTERVAL = 60*1000;

	/**The anonymous diffie-hellman cipher suite used for encrypting communication between clients and clients-indexnodes.*/
	public static final String DH_ANON_CIPHER_SUITE_USED = "TLS_DH_anon_WITH_AES_128_CBC_SHA";
	
	/** This salt is used on fs2 passwords to prevent malicious indexnode admins from using common md5-reversers to find a n00b's passwords.*/
	public static final String FS2_USER_PASSWORD_SALT = "fs2user:";
	
	/** The width and height of a peer avatar in FS2*/
	public static final int FS2_AVATAR_ICON_SIZE = 64;

	public static final String[] CLIENT_IMPORTANT_SYSTEM_PROPERTIES = {"headless", "java.awt.headless", "platform", "update"};

	
}
