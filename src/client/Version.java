package client;

import java.util.HashMap;
import java.util.Map;

import common.Util;

/**
 * A simple executable class to print the reference client version.
 * 
 * TODO for the next big version of FS2:
 * Make the logger intercept all console stuff for logging to disk.
 * Make the settings tab useful and pull all config items into it
 * 		Heap
 * 		Ports
 * 		All current settings
 * Add an indexnode config facility in the gui
 * Add an indexnode host facility into the gui. (with some auto indexnode hosting too)
 * Change autodetection to non-permenant only, and advert-duration based. Make advert listener more spammy and move the intelligence into the indexnode communicator class.
 * Add an 'up' button in the file browser.
 * Add more things on the download screen
 * Add an access/security log (apache style probably.)
 * Make logging to disk default.
 * 
 * @author gary
 *
 */
public class Version {
	
	public static final Map<String, String> FS2_VERSION_DESCRIPTIONS;
	
	/**
	 * Each version of the fs2 client must be described!
	 * Don't delete old ones when adding new ones.
	 */
	static {
		FS2_VERSION_DESCRIPTIONS = new HashMap<String, String>();
		FS2_VERSION_DESCRIPTIONS.put("0.8.24", "Settings tab on the way. This is a work in progress. Completion will move to 0.9");
		FS2_VERSION_DESCRIPTIONS.put("0.8.23", "Many bug fixes: launching, tab closing, search removing, average speed calculation, etc.");
		FS2_VERSION_DESCRIPTIONS.put("0.8.22", "Now with extra chat avatars...");
		FS2_VERSION_DESCRIPTIONS.put("0.8.21", "Chat notification in status bar, status bar when no tray icon, fixed popup tray behaviour.");
		FS2_VERSION_DESCRIPTIONS.put("0.8.20", "Serious improvements to reliability and games feature removed. Avatars in chat coming.");
		FS2_VERSION_DESCRIPTIONS.put("0.8.19", "Fixed games tab breaking and chat symbols... added some chat highlights too.");
		FS2_VERSION_DESCRIPTIONS.put("0.8.18", "Adds secure mode, games tab, better chat and vast reliability improvements."); //fixed autoupdate bug with fully headless clients.
		FS2_VERSION_DESCRIPTIONS.put("0.8.17", "Adds secure mode, games tab, better chat and vast reliability improvements."); //fixed games tab null-pointer if the tab isn't opened when there are zero profiles.
		FS2_VERSION_DESCRIPTIONS.put("0.8.16", "Adds secure mode, games tab, better chat and vast reliability improvements.");
		FS2_VERSION_DESCRIPTIONS.put("0.8.15", "Games tab now with more polish.");
		FS2_VERSION_DESCRIPTIONS.put("0.8.14", "Games tab appears to work now!");
		FS2_VERSION_DESCRIPTIONS.put("0.8.13", "Progress towards a working games tab including pretty table cells.");
		FS2_VERSION_DESCRIPTIONS.put("0.8.12", "Development version towards getting a game viewer working.");
		
	}
	
	/** The version identifier for the client.
	 * This is used by the client to identify new versions 
	 * DONT FORGET TO DESCRIBE THIS VERSION! */
	public static final Integer[] FS2_CLIENT_VERSION_BITS = {0,8,24};
	
	//Into a string:
	public static final String FS2_CLIENT_VERSION() { return Util.join(FS2_CLIENT_VERSION_BITS,"."); }
	
	/**The name of the fs2client. This is important to the filenaming convention. See UpdateSource.java*/
	public static final String FS2_CLIENT_NAME = "fs2client";
	
	/** The fancy release name for this minor version of FS2 */
	public static final String FS2_CLIENT_RELEASE = "Antiquated Machinery";
	
	public static void main(String[] args) {
		if (args.length==0) {
			System.out.print(FS2_CLIENT_VERSION());
		} else if (args[0].equals("--describe")) {
			System.out.print(FS2_VERSION_DESCRIPTIONS.get(FS2_CLIENT_VERSION()));
		}
	}

}
