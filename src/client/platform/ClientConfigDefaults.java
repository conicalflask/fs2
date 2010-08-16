package client.platform;

import java.util.HashMap;
import java.util.Map;

import javax.swing.UIManager;

import common.ConfigDefaults;
import common.FS2Constants;


/**
 * Provides the defaults for every configurable option key.
 * @author gary
 */
public class ClientConfigDefaults implements ConfigDefaults {
	
	/**
	 * Configurable option keys
	 * 
	 * Version numbers are used to enable changes to the defaults to override choices made by the user (in important cases)
	 * 
	 * @author gary
	 */
	public static class CK {
		public static final String HEAPSIZE  = "max_heap";
		public static final String PORT = "port_v2";
		public static final String ALIAS = "alias";
		public static final String DOWNLOAD_DIRECTORY = "download_dir";
		public static final String UPLOAD_BYTES_PER_SEC = "upload_bps";
		public static final String DOWNLOAD_BYTES_PER_SEC = "download_bps";
		public static final String ACTIVE_UPLOADS = "active_up";
		public static final String ACTIVE_UPLOADS_PER_USER = "active_up_per_user";
		public static final String ACTIVE_DOWNLOADS = "active_down";
		public static final String ACTIVE_DOWNLOADS_PER_FILE = "active_down_per_file";
		public static final String AUTO_INDEX_NODE = "auto_index";
		public static final String INDEX_NODES = "indexnodes";
		public static final String SHARES = "shares";
		public static final String DEFAULT_SHARE_NAME = "shares/default/name";
		public static final String DEFAULT_SHARE_PATH = "shares/default/path";
		public static final String SHARE_AUTOREFRESH_INTERVAL = "share_autorefresh_interval";
		public static final String UPDATE_POLICY = "update_policy_v2";
		public static final String HTTP_UPDATE_SOURCES = "http_update_sources";
		public static final String DEFAULT_HTTP_UPDATE1 = "http_update_sources/empty";
		public static final String LOG_MESSAGES_TO_DISK = "save_messages";
		public static final String AUTO_HEAP_KNOWLEDGE = "auto_heap_grow_acknowledged";
		public static final String DISPLAY_CHAT_NOTIFICATIONS = "display_notifications";
		public static final String AVATAR_PATH = "avatar_path";
		
		//gui bits
		public static final String MAIN_WINDOW_TOP = "gui/geometry/main_window_top";
		public static final String MAIN_WINDOW_LEFT = "gui/geometry/main_window_left";
		public static final String MAIN_WINDOW_WIDTH = "gui/geometry/main_window_width";
		public static final String MAIN_WINDOW_HEIGHT = "gui/geometry/main_window_height";
		public static final String LAUNCH_ITEMS = "gui/launch_items";
		public static final String TRAY_ICON_KNOWLEDGE = "gui/tray_icon_reminded";
		public static final String LOOK_AND_FEEL = "gui/look_and_feel";
		public static final String FILES_DIVIDER_LOCATION = "gui/files_divider_location";
		public static final String DOWNLOADS_DIVIDER_LOCATION = "gui/downloads_divider_location";
		public static final String DOWNLOADCHUNKS_TABLE_COLWITDHS = "gui/downloadchunks_table_columnwidths/col";
		public static final String FILES_TABLE_COLWIDTHS = "gui/files_table_columnwidths/col";
		public static final String INDEXNODE_TABLE_COLWIDTHS = "gui/indexnode_table_columnwidths/col";
		public static final String SHARES_TABLE_COLWIDTHS = "gui/shares_table_columnwidths/col";
		public static final String PEERS_TABLE_COLWIDTHS = "gui/peers_table_columnwidths/col";
		public static final String OPEN_TABS = "gui/open_tabs";
		public static final String ACTIVE_TAB = "gui/active_tab";
		public static final String LAST_CUSTOM_DOWNLOAD_DIRECTORY = "gui/lastcustomdownloaddir";
		public static final String STATS_DIVIDER_LOCATION = "gui/stats_divider_location";
		public static final String STATS_INDEXNODES_COLWIDTHS = "gui/stats_indexnodes_table_columnwidths/col";
		public static final String UPLOADS_COLWIDTHS = "gui/uploads_table_columnwidths/col";
	}
	
	protected HashMap<String, String> defaults = new HashMap<String, String>();
	//If a key is in this map then the value will be added to the default configuration file just
	//after that key.
	protected HashMap<String, String> comments = new HashMap<String, String>();
	
	public ClientConfigDefaults() {
		defaults.put(CK.HEAPSIZE, Integer.toString(256*1024*1024));
		comments.put(CK.HEAPSIZE, "defines how much Java heap (in bytes) fs2 should use at minimum. If FS2 detects that you have less than 90% of this value it will attempt to relaunch the JVM with more heap.");
		
		defaults.put(CK.PORT, "41234");
		comments.put(CK.PORT, "Port specifies which port FS2 wil serve files on. This port must be open (inbound) on your firewall/router.");
		
		defaults.put(CK.ALIAS, Platform.getHostnameSafely().equals("") ? "n00b " +Math.random() : Platform.getHostnameSafely());
		comments.put(CK.ALIAS, "Your alias enables other users to preferentially use you, and to loosely identify you.");
		
		defaults.put(CK.AVATAR_PATH, "none");
		comments.put(CK.AVATAR_PATH, "an image file used for our avatar. This can be any standard image format and size.");
		
		defaults.put(CK.DOWNLOAD_DIRECTORY, Platform.getDefaultDownloadsDirectory().getAbsolutePath());
		
		defaults.put(CK.UPLOAD_BYTES_PER_SEC, Long.toString(1024l*1024l*1024l*1024l));
		defaults.put(CK.DOWNLOAD_BYTES_PER_SEC, Long.toString(1024l*1024l*1024l*1024l));
		defaults.put(CK.ACTIVE_UPLOADS, "10");
		defaults.put(CK.ACTIVE_UPLOADS_PER_USER, "10");
		comments.put(CK.ACTIVE_UPLOADS_PER_USER, "indicates how many upload slots a single remote client can use on their own.");
		defaults.put(CK.ACTIVE_DOWNLOADS, "4");
		defaults.put(CK.ACTIVE_DOWNLOADS_PER_FILE, "1");
		comments.put(CK.ACTIVE_DOWNLOADS_PER_FILE, "indicates how many separate chunks a file may be downloaded as simultaniously.");
		
		defaults.put(CK.AUTO_INDEX_NODE,"true");
		comments.put(CK.AUTO_INDEX_NODE, "if auto_index is 'true' then this client will automatically register with autodetected indexnodes.");
		
		defaults.put(CK.INDEX_NODES, "");
		comments.put(CK.INDEX_NODES, "Children of the indexnodes key should contain two subchildren: 'path' is the insecure URL to the indexnode, 'password' is the plain MD5 of the password used for this indexnode. The password hash is never transmitted over clear channels.");
		
		defaults.put(CK.SHARES, "");
		comments.put(CK.SHARES, "Children of the shares key should contain two further children: <name> and <path>. These will be exported.");
		defaults.put(CK.DEFAULT_SHARE_NAME, FS2Constants.CLIENT_DEFAULT_SHARE_NAME);
		defaults.put(CK.DEFAULT_SHARE_PATH, Platform.getDefaultDownloadsDirectory().getAbsolutePath());
		defaults.put(CK.LAST_CUSTOM_DOWNLOAD_DIRECTORY, Platform.getDefaultDownloadsDirectory().getAbsolutePath());
		
		defaults.put(CK.SHARE_AUTOREFRESH_INTERVAL, "1800"); //30 minutes.
		comments.put(CK.SHARE_AUTOREFRESH_INTERVAL, CK.SHARE_AUTOREFRESH_INTERVAL+" is the number of seconds that a share will refresh itself automatically after.");
		
		defaults.put(CK.UPDATE_POLICY, "auto");
		comments.put(CK.UPDATE_POLICY, "update_policy controls the automatic update behaviour.\nSet to 'none' for no autoupdates, 'ask' to make the gui ask and the command line notify (but do nothing)\nor 'auto' to always download and hotpatch new versions");
		
		defaults.put(CK.HTTP_UPDATE_SOURCES, "");
		comments.put(CK.HTTP_UPDATE_SOURCES, "Children of http_update_sources must contain an HTTP URL to an FS2 client update repository.");
		
		defaults.put(CK.DEFAULT_HTTP_UPDATE1, "http://www.empty.org.uk/fs2-autoupdate/");
		
		defaults.put(CK.LOG_MESSAGES_TO_DISK, Boolean.FALSE.toString());
		comments.put(CK.LOG_MESSAGES_TO_DISK, "Setting "+CK.LOG_MESSAGES_TO_DISK+" to true will save all console output into a log file. You can use this to help the FS2 developers if FS2 breaks unexpectedly.");
		
		defaults.put(CK.MAIN_WINDOW_TOP, "100");
		defaults.put(CK.MAIN_WINDOW_LEFT, "100");
		defaults.put(CK.MAIN_WINDOW_WIDTH, "800");
		defaults.put(CK.MAIN_WINDOW_HEIGHT, "600");
		
		defaults.put(CK.LAUNCH_ITEMS, "1");
		defaults.put(CK.FILES_DIVIDER_LOCATION, "150");
		defaults.put(CK.DOWNLOADS_DIVIDER_LOCATION, "150");
		defaults.put(CK.STATS_DIVIDER_LOCATION, "150");
		
		defaults.put(CK.ACTIVE_TAB, "FILES");
		
		defaults.put(CK.OPEN_TABS+"/FILES", "0");
		defaults.put(CK.OPEN_TABS+"/DOWNLOADS", "1");
		defaults.put(CK.OPEN_TABS+"/UPLOADS", "2");
		defaults.put(CK.OPEN_TABS+"/SHARES", "3");
		defaults.put(CK.OPEN_TABS+"/INDEXNODES", "4");
		defaults.put(CK.OPEN_TABS+"/STATS", "5");
		defaults.put(CK.OPEN_TABS+"/CHAT", "6");
		
		defaults.put(CK.DISPLAY_CHAT_NOTIFICATIONS, Boolean.FALSE.toString());
		comments.put(CK.DISPLAY_CHAT_NOTIFICATIONS, "when true notifications will be displayed each time a chat message is received.");
		
		defaults.put(CK.LOOK_AND_FEEL, UIManager.getSystemLookAndFeelClassName());
	}

	@Override
	public Map<String, String> getComments() {
		return comments;
	}

	@Override
	public Map<String, String> getDefaults() {
		return defaults;
	}	
	
	@Override
	public String getRootElementName() {
		return "fs2client-configuration";
	}
}
