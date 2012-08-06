package client.platform;

import java.awt.HeadlessException;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedList;

import javax.swing.JOptionPane;

import common.Config;
import common.HttpUtil;
import common.Logger;

import client.gui.Notifications;
import client.platform.ClientConfigDefaults.CK;
import client.platform.updatesources.CodeUpdate;
import client.platform.updatesources.FS2Source;
import client.platform.updatesources.HttpSource;
import client.platform.updatesources.UpdateSource;
import client.platform.updatesources.UpdateSource.CodeUpdateComparator;

/**
 * Downloads new updates and stores them in the code updates directory.
 * @author gary
 */
public class Acquire {
	
	Config conf;
	Notifications notify;
	
	public Acquire(Config conf, Notifications notify) {
		this.conf = conf;
		this.notify = notify;
	}
	
	/**
	 * Performs the check to see if the user has demanded that an update be performed if one exists.
	 * @return true iff a new version was downloaded and started.
	 */
	public boolean startupForceCheck() {
		String updateURL = System.getProperty("update");
		if (updateURL!=null) {
			try {
				URL forced = null;
				if (!updateURL.equals("")) forced = new URL(System.getProperty("update"));
				if (checkAndDownloadUpdate(forced)) {
					return Relauncher.go(); 
				} else {
					Logger.warn("The update URL provided did not contain a new version of FS2");
				}
			} catch (MalformedURLException e) {
				Logger.severe("The update URL provided is invalid!");
			}
		}
		return false;
	}
	
	/**
	 * Uses the update policy from the configuration file to autoupdate if necessary.
	 * @return true iff a new version of FS2 was started.
	 */
	public boolean autoCheckForUpdates() {
		if (conf.getString(CK.UPDATE_POLICY).equals("auto")) {
			if (checkAndDownloadUpdate(null)) {
				return Relauncher.go();
			}
		} else if (conf.getString(CK.UPDATE_POLICY).equals("ask")) {
			CodeUpdate update = getLatestUpdate(null);
			if (update!=null) {
				try {
					if (notify.askAutoUpdate(conf, update)) {
						if (downloadUpdate(update)) {
							return Relauncher.go();
						}
					}
				} catch (HeadlessException e) {
					Logger.warn("There is a new version of FS2 available! ("+update.name+")\nRelaunch with -Dupdate to update!");
				}
			}
		}
		return false;
	}
	
	/**
	 * Checks for updates immediately, and prompts regardless of conf settings.
	 * Only works with a GUI.
	 */
	public void checkForUpdatesNowAndAsk() {
		CodeUpdate update = getLatestUpdate(null);
		if (update!=null) {
			if (notify.askAutoUpdate(conf, update)) {
				if (downloadUpdate(update)) {
					if (!Relauncher.go()) {
						JOptionPane.showMessageDialog(null, "The new version of FS2 failed to start. Try closing FS2 completely and restarting it.", "Update failed", JOptionPane.ERROR_MESSAGE); //They're pretty screwed if this is happening.
					}
				}
			}
		} else {
			JOptionPane.showMessageDialog(null, "You're already running the latest version of FS2.", "No updates available", JOptionPane.INFORMATION_MESSAGE);
		}
	}
	
	/**
	 * Returns information about a new update if there is one available.
	 * @return The code update information object if there is an update available.
	 */
	public CodeUpdate getLatestUpdate(URL additional) {
		Logger.log("Checking for updates...");
		LinkedList<UpdateSource> sources = new LinkedList<UpdateSource>();
		
		//Build a list of update sources in order:
		sources.add(new FS2Source());
		if (additional!=null) sources.add(new HttpSource(additional));
		
		//Add the sources from the configuration:
		for (String configKey : conf.getChildKeys(CK.HTTP_UPDATE_SOURCES)) {
			try {
				String url = conf.getString(configKey);
				sources.add(new HttpSource(new URL(url)));
			} catch (MalformedURLException e) {
				Logger.warn("Configured update URL: "+conf.getString(configKey)+" is invalid.");
			}
		}
		
		//Now get a list of the updates they have:
		LinkedList<CodeUpdate> updates = new LinkedList<CodeUpdate>();
		for (UpdateSource s : sources) {
			updates.add(s.getLatestUpdate());
		}
		
		Collections.sort(updates, new CodeUpdateComparator());
		
		return updates.getLast();
	}
	
	/**
	 * Attemps to download a newer version of FS2 from all the known sources.
	 * @param additional An HTTP URL to locate updates in, in addition to built-in sources.
	 * @return true iff a newer version was sucesfully downloaded.
	 */
	public boolean checkAndDownloadUpdate(URL additional) {
		CodeUpdate update = getLatestUpdate(additional);
		return downloadUpdate(update);
	}

	/**
	 * Downloads a specified code update into the cache.
	 * @param update
	 * @return true iff a new update was succesfully downloaded into the cache.
	 */
	public boolean downloadUpdate(CodeUpdate update) {
		if (update==null) return false;
		try {
			Logger.log("Starting to download update  ("+update.name+") from: "+update.location);
			HttpUtil.simpleDownloadToFile(update.location, new File(Platform.getUpdatesDirectory()+File.separator+update.name), notify.downloadStarted());
			Logger.log("A new version ("+update.name+") of FS2 was downloaded.");
			return true;
		} catch (Exception e) {
			Logger.warn("The code update couldn't be downloaded: "+e);
			return false;
		}
	}
	
}
