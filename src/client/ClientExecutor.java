package client;

import java.io.File;
import java.util.Date;

import client.gui.Gui;
import client.gui.Notifications;
import client.indexnode.PeerStatsCollector;
import client.platform.Acquire;
import client.platform.ClientConfigDefaults;
import client.platform.Platform;
import client.platform.Relauncher;
import client.platform.ClientConfigDefaults.CK;
import client.shareserver.ShareServer;

import common.Config;
import common.FS2Constants;
import common.Logger;
import common.Util;

/**
 * The class which provides the entry point for the client.  Sets up and executes
 * the client appropriately.
 * 
 * @author Gary Plumbridge
 */
public class ClientExecutor {

	//The instances of key classes that the FS2 client is composed of:
	private static Config conf;
	private static Acquire acquire;
	private static PeerStatsCollector peerstats;
	private static ShareServer ssvr;
	private static ShutdownHandler shutdowner;
	private static Gui gui;
	private static Notifications notify;
	
	public static String[] args;
	
	/**
	 * FS2 client normal entry point.
	 * There is another entry point in {@link Version}, but this is only used in the build process.
	 */
	public static void main(String[] args) {
		try {
			//Catch evils in all threads:
			Thread.setDefaultUncaughtExceptionHandler(new CriticalFailureHandler());
			
			Logger.setLoggingEnabled(true);
			
			ClientExecutor.args = args;
			
			//) Setup the CTRL-C handler:
			shutdowner = new ShutdownHandler();
			Runtime.getRuntime().addShutdownHook(shutdowner);
			
	    	Logger.log("Starting "+Version.FS2_CLIENT_NAME+" version " + Version.FS2_CLIENT_VERSION()+ " with "+Util.niceSize(Runtime.getRuntime().maxMemory())+" heap.");
			Logger.log("Using platform directory at: "+Platform.getPlatformRoot().getAbsolutePath());
			
			//Check to see if we need to hand over to a newer version of FS2:
			if (Relauncher.go()) return; //(return before starting this version if a new version was launched)
			
			//) load config:
			conf = new Config(new ClientConfigDefaults(), Platform.getPlatformFile(FS2Constants.CONFIGURATION_FILE_NAME)); //If this fails then we have to crash. (it already includes a best-effort fixer)
			new File(conf.getString(CK.DOWNLOAD_DIRECTORY)).mkdirs(); //intended primarily for first launch so that the default share works.
			
			
			// Now we have the config we can check to see if we have inadequate heap:
			if (Runtime.getRuntime().maxMemory()<(conf.getLong(CK.HEAPSIZE)*0.9) && System.getProperty("increasedheap")==null) {
				if (Relauncher.increaseHeap(conf.getLong(CK.HEAPSIZE), true)) {
					return; //hopefully the large-jvm subprocess was a success. no easy way to tell programmatically.
				} else {
					Logger.warn("Failed to relaunch JVM with a larger heap. This is likely caused if not running from a .jar file.");
				}
			}

			//##########################
			// Full environment setup, now can boot FS2.
			//##########################
			
			//Must happen before the first window appears!
			Platform.performStartupOSHacks();
			
			
			//Start notifications manager:
			notify = new Notifications(conf);
			//this will cause the first window:
			notify.incrementLaunchProgress("Starting...");
			//Notify the user if this is a relaunch due to heap, the first time.
			notify.heapSizeReminder();
			
			//) setup on-disk logging:
			setupLogging();
			
			//Enable DH anon cipher suite for subsequent HTTPS things:
			Util.enableDHanonCipherSuite();
			
			//) check for forced updates:
			notify.incrementLaunchProgress("Performing forced update check (if applicable)...");
			acquire = new Acquire(conf, notify);
			if (acquire.startupForceCheck()) return; //We've got a new version starting, so stop this version.
			
			//)
			notify.incrementLaunchProgress("Loading peer infomation...");
			peerstats = PeerStatsCollector.getPeerStatsCollector();
			
			//) Launch shareserver NG:
			notify.incrementLaunchProgress("Starting share server...");
			ssvr = new client.shareserver.ShareServer(conf, peerstats, notify);

			//) Launch GUI:
			Gui.startGUI(conf, notify, ssvr); //This will set the gui field when the gui is initialised.
			
			//6) check for updates
			if (acquire.autoCheckForUpdates()) return;
			
			
		} catch (Throwable t) {
			try {
				ssvr.shutdown();
			} catch (Throwable e) {
				// Dont care
			} finally {
				CriticalFailureHandler.criticalException(t);
			}
		}
	}
	
	public static Acquire getAcquire() {
		return acquire;
	}
	
	public static void setGui(Gui gui) {
		ClientExecutor.gui = gui;
	}
	
	private static void setupLogging() {
		if (Boolean.parseBoolean(conf.getString(CK.LOG_MESSAGES_TO_DISK))) {
			Logger.setLogFileName(Platform.getLogsDirectory().getAbsolutePath()+File.separator+(new Date()).toString().replace(':','-'));
			Logger.log("Log messages are now being saved to: "+Logger.getLogFile());
		}
	}

	private static class ShutdownHandler extends Thread {
		@Override
		public void run() {
			super.run();
			shutdowner=null;
			Logger.warn("Shutting down gracefully...");
			shutdown();
		}
	}

	public static boolean endGame;
	public static boolean shuttingDown;
	
	/**
	 * Closes down this instance of FS2.
	 * This is used primarily for when a new instance will be launched.
	 * (and to gracefully save stuff when closing the app)
	 */
	public static void shutdown() {
		shuttingDown = true;
		endGame = false; //This is true when this method was invoked by the shutdown hook.
		
		//Release our previous shutdown handler, so that it's not executed again (as this may have been invoked during an autoupdate rather than JVM shutdown)
		if (shutdowner!=null) {
			Runtime.getRuntime().removeShutdownHook(shutdowner);
			shutdowner=null;
		} else {
			//if we're here then this is running in a shutdown hook.
			endGame = true;
		}
		
		if (endGame) {
			Logger.severe("Shutting down due to extenal request (JVM shutdown, signal etc)\nIf shutdown takes longer than "+(FS2Constants.CLIENT_SHUTDOWN_MAX_MILLIS/1000)+" seconds the JVM will be terminated.");
			Thread timer = (new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						Thread.sleep(FS2Constants.CLIENT_SHUTDOWN_MAX_MILLIS);
					} catch (InterruptedException e) {
						Logger.log(e);
					}
					Logger.severe("Shutdown took too long, terminating FS2 v"+Version.FS2_CLIENT_VERSION()+"...");
					Runtime.getRuntime().halt(1);
				}
			}));
			timer.setDaemon(true);
			timer.setName("Shutdown timeout");
			timer.start();
		} else {
			Logger.severe("Shutting down due to internal request (Graphical menus, autoupdate, etc), There is no timeout for this operation!");
		}
		
		//notify may be null if we are relaunching to a new version of fs2 right away...
		if (notify!=null) notify.shutdownProgress("Shutting down the gui..."); 
		
		//) close the gui (contains the download controller)
		if (gui!=null) {
			gui.shutdown(endGame);
			gui=null;
		}
		
		if (notify!=null) notify.shutdownProgress("Shutting down shares..."); 
		
		//) Shutdown the shareserver
		if (ssvr!=null) {
			ssvr.shutdown();
			ssvr = null;
		}
		
		if (notify!=null) notify.shutdownProgress("Saving peers..."); 
		//) Save statistics
		if (peerstats!=null) {
			peerstats.shutdown();
			peerstats=null;
		}
		
		if (notify!=null) notify.shutdownProgress("Closing dialogs..."); 
		if (notify!=null) {
			notify.shutdown(endGame);
		}
		
		if (notify!=null) notify.shutdownProgress("Saving and cleaning the config..."); 
		//shutdown the configuration manager (must be the last meaningful item):
		if (conf!=null) {
			conf.shutdown();
			conf=null;
		}
		
		if (notify!=null) notify.shutdownProgress("Shutting down logging subsystem..."); 
		Logger.shutdown(); //It will be restarted by the next process with a new filename.
		
		if (notify!=null) notify.shutdownProgress("Finishing..."); 
		//) garbage collect. (This is a good time to spend time purging as the user is already aware that something is happening)
		if (!endGame) System.gc();

		if (notify!=null) notify.shutdownComplete();
		//It is important that the JVM not be stopped as there might be a newer version of FS2 using the same JVM.
		
		//A splash may still be open if we shutdown before any windows were opened by UI toolkits, and before notifications was started:
		Notifications.closeSplash();
		
		Logger.log("FS2 v"+Version.FS2_CLIENT_VERSION()+" finished.");
	}
	
}
