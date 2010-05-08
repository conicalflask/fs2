package client.gui;

import java.awt.GraphicsEnvironment;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import common.Config;
import common.Logger;

import client.ClientExecutor;
import client.CriticalFailureHandler;
import client.gui.SingleInstanceDetector.InstanceNotifiable;
import client.indexnode.downloadcontroller.DownloadController;
import client.platform.Platform;
import client.platform.ClientConfigDefaults.CK;
import client.shareserver.ShareServer;

/**
 * The root of the gui, provides access to gui elements (such as dialogs for errors and autoupdate)
 * Non-main elements of the gui can be triggered even if the main element is disabled or broken.
 * (non-main elements are in the Notifications class)
 * 
 * @author gary
 */
public class Gui {

	private class NewInstanceDetector implements InstanceNotifiable {
		@Override
		public void newInstanceStarted() {
			Logger.log("A new instance of FS2 attempted to start, unhiding the main window.");
			mainFrame.setVisible(true);
		}
	}
	
	SingleInstanceDetector sid = new SingleInstanceDetector();
	MainFrame mainFrame;
	Config conf;
	Notifications notify;
	ShareServer ssvr;
	Utilities util = new Utilities();
	DownloadController dc;
	
	public Utilities getUtil() {
		return util;
	}
	
	public Config getConf() {
		return conf;
	}
	
	/**
	 * Returns the shareserver this gui is attached to.
	 */
	public ShareServer getShareServer(){
		return ssvr;
	}
	
	//Hide the constructor.
	private Gui(Config conf, Notifications notify, ShareServer ssvr, DownloadController dc) {
		this.conf = conf;
		this.notify = notify;
		this.ssvr = ssvr;
		
		this.dc = dc;
		
		sid.addNotifiable(new NewInstanceDetector());
		mainFrame = new MainFrame(this);

		Platform.performGuiVisibleOSHacks(this);
		
		notify.launchComplete();
	}
	
	/**
	 * Starts the Gui if it can, and returns the new gui object.
	 */
	public static void startGUI(final Config conf, final Notifications notify, final ShareServer ssvr) {
		//Must start GIU in AWT thread!
		if (System.getProperty("headless")==null) {
			notify.incrementLaunchProgress("Loading download queue...");
			final DownloadController dc = new DownloadController(ssvr);
			notify.incrementLaunchProgress("Starting user interface...");
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					try {
						ClientExecutor.setGui(new Gui(conf, notify, ssvr, dc));
					} catch (Exception e) {
						CriticalFailureHandler.criticalException(e);
					}
				}
			});
		}
	}
	
	/**
	 * Triggers a shutdown of the FS2 application.
	 * 
	 * Returns false, for compatibility with OS X 'Quit' menu hook.
	 * 
	 * @return
	 */
	public boolean triggerShutdown() {
		Thread notInSwing = new Thread(new Runnable() {
			@Override
			public void run() {
				ClientExecutor.shutdown();
			}
		}, "Shutdowner");
		notInSwing.setDaemon(false);
		notInSwing.start();
		return false;
	}
	
	/**
	 * Terminates the gui gracefully...
	 * @param endGame specifies if the JVM is fully going down.
	 * This is so that we can avoid killing swing-bits during a real shutdown as AWT is busy doing that already. (and will cause a deadlock if we both do)
	 */
	public void shutdown(boolean endGame) {
		sid.shutdown();
		
		mainFrame.setVisible(false);
		//very important!:
		
		notify.shutdownProgress("Saving the download queue..."); 
		dc.shutdown();
		
		notify.shutdownProgress("Saving GUI state..."); 
		mainFrame.shutdown(endGame);
	}
	
	public static void setStartupLaF(Config conf) {
		if (GraphicsEnvironment.isHeadless()) return;
		if (!conf.getString(CK.LOOK_AND_FEEL).equals("")) {
			try {
				UIManager.setLookAndFeel(conf.getString(CK.LOOK_AND_FEEL));
			} catch (Exception e) {
				Logger.warn("Your configuration file contains an invalid look and feel specification: "+e);
			}
		}
	}
	
	void setLaF(String newLaF) {
		try {
			UIManager.setLookAndFeel(newLaF);
			SwingUtilities.updateComponentTreeUI(mainFrame);
			conf.putString(CK.LOOK_AND_FEEL, newLaF);
			mainFrame.setStatusHint("Look and Feel applied!");
			mainFrame.pack();
			mainFrame.setConfiguredBounds();
		} catch (Exception e) {
			mainFrame.setStatusHint("Can't set that Look and Feel!");
			Logger.warn("Couldn't set system look and feel.");
			e.printStackTrace();
		}
	}

	/**
	 * Displays the main window, even if hidden.
	 */
	public void showFS2() {
		mainFrame.setVisible(true);
	}

	public void disableTrayIcon() {
		mainFrame.trayicon.shutdown();
	}
}
