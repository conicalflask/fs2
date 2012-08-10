package client.platform;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;

import client.Version;
import client.gui.Gui;
import client.gui.Utilities;

import common.FS2Constants;
import common.Logger;

/**
 * Provides high-level abstraction between different operating systems.
 *
 * @author gary
 */
public class Platform {

	static String productName = Version.FS2_CLIENT_NAME;
	
	//Is this OS X? OS X needs a lot of hacks to make it work acceptably.
    public static boolean OS_X = (System.getProperty("os.name").toLowerCase().startsWith("mac os x"));
    public static boolean WINDOWS = (System.getProperty("os.name").toLowerCase().startsWith("win"));
    public static boolean LINUX = (System.getProperty("os.name").toLowerCase().contains("linux"));
    
    public static boolean OS_X_DOCK_ACTIVE = false;
	
	public static void performStartupOSHacks() {
		if (OS_X) {
			System.setProperty("apple.laf.useScreenMenuBar","true");
			System.setProperty("com.apple.mrj.application.apple.menu.about.name","FS2");
			OSXAdapter.setDockIcon(new Utilities().getImage("trayicon").getImage());
			OSXAdapter.setAboutHandler(null, null); //disable the about menu item.
		}
		if (LINUX) {
			Logger.severe("When running FS2 on linux you should not launch with 'java -jar {jarname}'  but as 'java -cp {jarname} client.ClientExecutor'\nThis is because the splash-screen will break drag-and-drop support and cause X11 to lockup: http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6397447");
		}
	}
	
	public static void performGuiVisibleOSHacks(Gui gui) {
		if (OS_X) {
			try {
				OSXAdapter.setQuitHandler(gui, gui.getClass().getDeclaredMethod("triggerShutdown", new Class[] {}));
				OSXAdapter.setReOpenHandler(gui, gui.getClass().getDeclaredMethod("showFS2", new Class[] {}));
				gui.disableTrayIcon();
				OS_X_DOCK_ACTIVE = true;
			} catch (Exception e) {
				Logger.warn("Unable to apply OS X UI fixes to java...");
				Logger.log(e);
			}
		}
	}
	
	/**
	 * Returns the hostname or an empty string if the hostname can't be determined.
	 * @return
	 */
	public static String getHostnameSafely() {
		try {
			return InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			return "";
		}
	}
	
	/**
	 * Determines where configurations and executable libraries/code are stored.
	 * @return
	 */
	private static File platformRoot;
	public static File getPlatformRoot() {
		if (platformRoot != null) return platformRoot;
		String platformOverride = System.getProperty("platform");
		if (platformOverride!=null) {
			platformRoot = new File(platformOverride);
		} else {
			platformRoot = new File(System.getProperty("user.home")+File.separator+"."+productName);
		}
		return createWriteTest(platformRoot);
	}
	
	/**
	 * Gets the initial downloads directory. This might be different on each platform.
	 * Place it on the user's desktop by default... lol.
	 * @return
	 */
	public static File getDefaultDownloadsDirectory() {
		File ddd = new File(System.getProperty("user.home")+File.separator+"Desktop/fs2-downloads");
		//if (!ddd.exists()) ddd.mkdirs();
		return ddd;
	}
	
	/**
	 * Gets a File object for a name within the FS2 client platform directory.
	 * @param name
	 * @return
	 */
	public static File getPlatformFile(String name) {
		return new File(getPlatformRoot().getAbsolutePath()+File.separator+name);
	}
	
	static File updateDir;
	/**
	 * Locates the directory that will contain code updates to this product.
	 * @return
	 */
	public static File getUpdatesDirectory() {
		if (updateDir!=null) return updateDir;
		updateDir = new File(getPlatformRoot().toString()+File.separator+FS2Constants.CLIENT_UPDATES_SUBDIRECTORY);
		return createWriteTest(updateDir);
	}
	
	public static File getLogsDirectory() {
		File logs = new File(getPlatformRoot().toString()+File.separator+FS2Constants.CLIENT_LOGS_SUBDIRECTORY);
		return createWriteTest(logs);
	}
	
	private static File createWriteTest(File inFile) {
		if (!inFile.exists()) {
			if (!inFile.mkdirs()) {
				Logger.severe(inFile.toString()+" is not creatable! Create this directory and ensure it is writable.");
			}
		}
		if (!inFile.canWrite()) {
			Logger.severe(inFile.toString()+" is not writable! Fix this and restart "+productName+".");
		}
		
		return inFile;
	}
	
	private static PlatformOS pos = null;
	/**
	 * Returns an operating system abstraction object if one is possible for this operating system.
	 * @return
	 */
	public static PlatformOS getCurrentPlatformOS() {
		if (pos==null) {
			if (OS_X) {
				pos = new MacOSX();
			} else if (WINDOWS) {
				pos = new Windows();
			} else if (LINUX) {
				pos = new Linux();
			}
			return pos;
		} else return pos;
	}
}
