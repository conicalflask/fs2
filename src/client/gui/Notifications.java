package client.gui;

import java.awt.GraphicsEnvironment;
import java.awt.SplashScreen;
import java.awt.Toolkit;
import java.lang.reflect.Field;

import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.ProgressMonitor;

import common.Config;
import common.Logger;
import common.ProgressTracker;
import common.Util;
import common.HttpUtil.SimpleDownloadProgress;

import client.ClientExecutor;
import client.platform.ClientConfigDefaults.CK;
import client.platform.updatesources.CodeUpdate;

public class Notifications {

	/**
	 * Returns true if the user would like to autoupdate.
	 * Throws an exception if now gui dialog could be triggered.
	 * @return
	 */
	public boolean askAutoUpdate(Config conf, CodeUpdate update) {
		Object[] choices = {"Always update automatically", "Update now (and ask in future)", "Ask next time", "Never update automatically"};
		String s = (String)JOptionPane.showInputDialog(
		                    null,
		                    "There is a new version of FS2 available! ("+update.version+")\nWould you like to download and update to it?\n\nDescription:\n\""+update.description+"\"\n\nUpdate Source: "+update.locationDescription+"\n",
		                    "Download and install update?",
		                    JOptionPane.QUESTION_MESSAGE,
		                    null,
		                    choices,
		                    choices[1]);
	
		if (s==null) {
			return false;
		} else if (s.equals(choices[0])) {
			conf.putString(CK.UPDATE_POLICY, "auto");
			return true;
		} else if (s.equals(choices[1])) {
			conf.putString(CK.UPDATE_POLICY, "ask");
			return true;
		} else if (s.equals(choices[2])) {
			conf.putString(CK.UPDATE_POLICY, "ask");
			return false;
		} else if (s.equals(choices[3])) {
			conf.putString(CK.UPDATE_POLICY, "none");
			return false;
		}
		return false;
	}

	public SimpleDownloadProgress downloadStarted() {
		if (GraphicsEnvironment.isHeadless()) return null;
		updatePm = new ProgressMonitor(null, "Downloading FS2 update...", "nomming bytes...(opening connection to download server)", 0, 0);
		fixupPM(updatePm, true);
		return new SimpleDownloadProgress() {
			ProgressTracker tracker = new ProgressTracker();
			
			@Override
			public void totalSize(long totalSize) {
				updatePm.setMaximum((int) totalSize);
				tracker.setExpectedMaximum(totalSize);
			}
			
			@Override
			public void progress(long downloadedBytes) {
				if (updatePm.isCanceled()) throw new IllegalStateException("User cancelled the update download.");
				updatePm.setProgress((int) downloadedBytes);
				tracker.progressTo(downloadedBytes);
				updatePm.setNote(tracker.describe());
			}
		};
	}

	public static void notifyCriticalFailure(String message, String titleDescription) {
		try {
			JOptionPane.showMessageDialog(null, message, "FS2 is broken"+(titleDescription.equals("") ? "" : " - " + titleDescription), JOptionPane.ERROR_MESSAGE);
		} catch (Exception e) {}
	}
	
	ProgressMonitor updatePm;
	ProgressMonitor launchProgress;
	Config conf;
	int launchValue = 0;
	
	/**
	 * Creates (if needed) and maintains a launch progress dialog box.
	 * @param description
	 */
	public void incrementLaunchProgress(String description) {
		if (GraphicsEnvironment.isHeadless() || (System.getProperty("headless")!=null)) {
			closeSplash(); //close splash screen on gui'd systems where the user would prefer no gui.
			return;
		}

		if (launchProgress==null) {
			launchProgress = new ProgressMonitor(null, "Starting FS2...", description, 0, conf.getInt(CK.LAUNCH_ITEMS));
			fixupPM(launchProgress, false);
		} else {
			closeSplash();
			launchValue++;
			if (launchValue==launchProgress.getMaximum()) launchProgress.setMaximum(launchValue+1); //Don't let this auto close until actually closed.
			launchProgress.setProgress(launchValue);
			launchProgress.setNote(description);
		}
	}

	public static void closeSplash() {
		if (!GraphicsEnvironment.isHeadless() && SplashScreen.getSplashScreen()!=null) SplashScreen.getSplashScreen().close();
	}
	
	ProgressMonitor shutdownPm;
	public void shutdownProgress(String description) {
		if (GraphicsEnvironment.isHeadless() || (System.getProperty("headless")!=null)) return;
		if (ClientExecutor.endGame) return; //don't trigger in a shutdown hook
		
		if (shutdownPm==null) {
			shutdownPm = new ProgressMonitor(null, "Shutting down... (might take a while)", description, 0, 100000);
			fixupPM(shutdownPm, false);
		} else {
			shutdownPm.setProgress(99999);
			shutdownPm.setNote(description);
		}
	}
	
	public void shutdownComplete() {
		if (shutdownPm!=null) {
			shutdownPm.close();
			shutdownPm = null;
		}
	}
	
	/**
	 * Fixes a progress monitor to be wide, and pops it up now.
	 * This take 100ms to execute!
	 * @param pm
	 */
	public static void fixupPM(ProgressMonitor pm, boolean cancellable) {
		try {
			pm.setMillisToDecideToPopup(1);
			pm.setMillisToPopup(1);
			Thread.sleep(100);
			pm.setProgress(0);
			
			Field diafield = pm.getClass().getDeclaredField("dialog");
			diafield.setAccessible(true);
			JDialog d = (JDialog) diafield.get(pm);
			
			if (!cancellable) 
			{
				((JOptionPane)d.getContentPane().getComponent(0)).setOptions(new Object[]{});
			}
			
			int nx, ny, w, h;
			w = 640;
			h = d.getSize().height;
			nx = (Toolkit.getDefaultToolkit().getScreenSize().width-w)/2;
			ny = (Toolkit.getDefaultToolkit().getScreenSize().height-h)/2;
			d.setBounds(nx, ny, w, h);
		} catch (Exception e) {
			Logger.warn("Unable to runtime-bork ProgressMonitor.class: "+e);
			Logger.log("This is not a problem!");
			Logger.log(e);
		}
	}

	/**
	 * Closes the launch progress dialog.
	 */
	public void launchComplete() {
		if (launchProgress!=null) {
			conf.putInt(CK.LAUNCH_ITEMS, launchProgress.getMaximum()); //record for next time how many items there were.
			launchProgress.close();
		}
	}
	
	public Notifications(Config conf) {
		this.conf = conf;
		//Initialise the LaF now, as it might be needed for a progress dialog.
		Gui.setStartupLaF(conf);
	}
	
	public void trayIconReminder() {
		oneTimeReminder(CK.TRAY_ICON_KNOWLEDGE, "FS2 is minimized to the system tray when you close the main window!\nClick the tray icon to restore FS2.");
	}
	
	public void heapSizeReminder() {
		if (System.getProperty("increasedheap")!=null) oneTimeReminder(CK.AUTO_HEAP_KNOWLEDGE, "FS2 has automatically increased its maximum java heap size to "+Util.niceSize(Runtime.getRuntime().maxMemory())+".\nYou can change FS2's maximum heap size in the advanced settings.");
	}
	
	public void oneTimeReminder(String configurationKey, String message) {
		if (!Boolean.parseBoolean(conf.getString(configurationKey))) {
			Logger.warn(message);
			conf.putString(configurationKey, Boolean.TRUE.toString());
			if (!GraphicsEnvironment.isHeadless()) JOptionPane.showMessageDialog(null, message, "One time reminder...",JOptionPane.INFORMATION_MESSAGE);
		}
	}
	
	public void shutdown(boolean endGame) {
		if (endGame) return; //Don't bother to free resources when JVM quitting as this will deadlock with AWT trying to do the same.
		if (updatePm!=null) {
			updatePm.close();
		}
		if (launchProgress!=null) {
			launchProgress.close();
		}
	}

}
