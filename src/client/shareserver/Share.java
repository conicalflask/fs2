package client.shareserver;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;

import common.httpserver.HttpContext;

import client.platform.Platform;

import common.FS2Constants;
import common.FileList;
import common.HttpFileHandler;
import common.HttpUtil;
import common.Logger;
import common.ProgressTracker;
import common.Util;
import common.FileList.Item;
import common.Util.Deferrable;
import common.Util.NiceMagnitude;

public class Share {
	
	private class Refresher implements Runnable {
		
		volatile boolean shouldStop = false;
		ProgressTracker tracker = new ProgressTracker();
		long changed = 0;
		long buildSizeSoFar = 0;
		FileCounter fileCounter = null;
		
		public void shutdown() {
			shouldStop = true;
			if (fileCounter != null) {
				fileCounter.shutdown();
			}
		}
		
		@Override
		public void run() {
			try {
				tracker.setExpectedMaximum(list.root.fileCount);
				if (list.root.fileCount == 0l) {
					// We don't have a clue, so set off a counter worker to find out
					fileCounter = new FileCounter(tracker);
					(new Thread(fileCounter)).start();
				}
				refreshActive = true;
				if (!location.exists()) {
					setStatus(Status.ERROR);
					cause = ErrorCause.NOTFOUND;
					Logger.warn("Share "+getName()+" ("+location+") doesn't exist on disk!");
					return;
				}
				//Always start on a canonical file so that symlink detection works.
				refreshDirectory(canonicalLocation, list.root);
				if (shouldStop) return;
				
				if (changed>0) list.revision++;
				Logger.log(changed>0 ? "Share '"+getName()+"' is now at revision "+list.revision : "Share '"+getName()+"' is unchanged at revision " + list.revision);
				refreshComplete();
				
				
			} catch (Exception e) {
				Logger.severe("Exception during share refresh: "+e);
				Logger.log(e);
				causeOtherDescription = e.toString();
				setStatus(Status.ERROR);
				cause = ErrorCause.OTHER;
			} finally {
				//heh:
				activeRefresh = null;
				refreshActive = false;
			}
		}
		
		void refreshDirectory(File directory, Item directoryItem) {
			HashSet<String> existing = new HashSet<String>(directoryItem.children.keySet());
			
			File[] dirChildren = directory.listFiles();
			if (dirChildren!=null) {
				for (final File f : dirChildren) {
					//Here is the 'main' loop, items place here will happen before each file is considered.
					if (shouldStop) return;
					Util.executeNeverFasterThan(FS2Constants.CLIENT_EVENT_MIN_INTERVAL, notifyShareServer);
					
					if (f.getPath().endsWith(".incomplete")) continue; //don't share incomplete files as they can hash collide! (this effectively pollutes FS2 networks of large files :S)
					if (f.isDirectory() && isSymlink(f)) continue; //forbid linked directories to avoid infinite loops.
					
					try {
						if (f.isFile() && !Util.isWithin(f, canonicalLocation)) {
							Logger.warn("Ignoring '"+f.getAbsolutePath()+"' while building share '"+getName()+"' as it is a link to outside of the share root.");
							continue; //ignore symlinks to outside of the share as these cannot be downloaded.
						}
					} catch (IOException e) {
						Logger.warn("Unable to check for canonical containment while building filelist: "+e);
						Logger.log(e);
						continue;
					} 
					
					if (existing.remove(f.getName())) {
						//We already have this item so update it:
						Item exists = directoryItem.children.get(f.getName());
						long csize = exists.size;
						long ccount = exists.fileCount;
						
						updateItem(f, exists);
						
						directoryItem.size-=csize;
						directoryItem.size+=exists.size;
						directoryItem.fileCount-=ccount;
						directoryItem.fileCount+=exists.fileCount;
					} else {
						changed++;
						//brand new file or directory.
						Item newitem = new Item();
						newitem.name = f.getName();
						directoryItem.children.put(newitem.name, newitem);
						if (!updateItem(f, newitem)) {
							directoryItem.children.remove(newitem.name);
							changed-=2; //Item couldn't be updated (probably no permission) so this change didn't count.
							            //Nor did the change incurred by the rehash that failed.
						} else {
							directoryItem.size+=newitem.size;
							directoryItem.fileCount+=newitem.fileCount;
							if (!newitem.isDirectory()) buildSizeSoFar+=newitem.size;
						}
					}
					if (f.isFile()) {
						tracker.progress(1); //one more item done.
					}
				}
			}
			
			//Remove files/directories from the list that are still in the 'existing' set,
			//as they are clearly not in the filesystem.
			for (String fn : existing) {
				changed++; //This must be a change if there are items to remove.
				directoryItem.size-=directoryItem.children.get(fn).size;
				directoryItem.fileCount-=directoryItem.children.get(fn).fileCount; //this .fileCount should always be one for files.
				directoryItem.children.remove(fn);
			}
		}
		
		boolean updateItem(final File f, Item i) {
			if (f.isDirectory()) {
				if (i.children==null) i.children = new HashMap<String, Item>();
				refreshDirectory(f, i);
				return true;
			} else {
				i.fileCount=1;
				boolean hash = false;
				if (i.size!=f.length()) {
					hash = true;
					i.size=f.length();
				}
				if (i.lastModified!=f.lastModified()) {
					hash = true;
					i.lastModified = f.lastModified();
				}
				if (i.hashVersion!=FS2Constants.FILE_DIGEST_VERSION_INT) {
					hash = true;
					i.hashVersion = FS2Constants.FILE_DIGEST_VERSION_INT;
				}
				if (hash || i.hash.equals("")) {
					changed++;
					return calculateHash(f, i);
				}
				return true;
			}
		}
		
		boolean calculateHash(File f, Item i) {
			try {
				i.hash = ThrottledFileDigester.fs2DigestFile(f, null);
				return true;
			} catch (Exception e) {
				Logger.warn("Failed to generate hash for "+f.getName()+", "+e.toString());
				return false;
			}
		}
		
		private boolean isSymlink(File file) {
			try {
				return !file.getAbsolutePath().equals(file.getCanonicalPath());
			} catch (IOException e) {
				Logger.severe("Symlink detection failed!:"+e);
				Logger.log(e);
				return false;
			}
		}
	
		/***
		 * A file counter which just recurses directories in order to find out how many
		 * files are within it.
		 * @author r4abigman
		 *
		 */
		private class FileCounter implements Runnable {

			volatile boolean         shouldStop = false;
			private  int             fileCount  = 0;
			private  ProgressTracker tracker;
			
			public FileCounter(ProgressTracker tracker) {
				this.tracker = tracker;
			}
			
			public void shutdown() {
				shouldStop = true;
			}
			
			@Override
			public void run() {
				try {
					//Always start on a canonical file so that symlink detection works.
					countDirectory(canonicalLocation);
					if (shouldStop) return;
				} catch (Exception e) {
					Logger.severe("Exception during file count: "+e);
					Logger.log(e);
					// As something went wrong, just set the max expected to zero
					tracker.setExpectedMaximum(0);
				}
			}
			
			void countDirectory(File directory) {
				File[] dirChildren = directory.listFiles();
				if (dirChildren!=null) {
					for (final File f : dirChildren) {
						// Here is the 'main' loop, items place here will happen before each file is considered.
						if (shouldStop) return;
						Util.executeNeverFasterThan(FS2Constants.CLIENT_EVENT_MIN_INTERVAL, notifyShareServer);
						
						if (f.getPath().endsWith(".incomplete")) continue; // we don't share incomplete files
						if (f.isDirectory() && isSymlink(f)) continue; // forbid linked directories to avoid infinite loops.
						
						try {
							if (f.isFile() && !Util.isWithin(f, canonicalLocation)) {
								continue; // ignore symlinks to outside of the share as these cannot be downloaded.
							}
						} catch (IOException e) {
							Logger.warn("Unable to check for canonical containment while building filelist count: "+e);
							Logger.log(e);
							continue;
						}
						
						if (f.isDirectory()) {
							countDirectory(f);
						} else if (f.isFile()) {
							fileCount++;
						}
					}
					// Update the expected maximum of the tracker
					tracker.setExpectedMaximum(fileCount);
				}
			}
			
		}
	}
	
	public enum Status {ACTIVE, REFRESHING, BUILDING, ERROR, SHUTDOWN, SAVING};
	public enum ErrorCause {NOTFOUND, UNSAVEABLE, OTHER};
	Status status = Status.BUILDING;
	ErrorCause cause = ErrorCause.OTHER;
	String causeOtherDescription;
	private File location;
	File canonicalLocation;
	FileList list; //The structure that holds the list of files.
	File listFile;
	ShareServer ssvr;
	HttpContext context;
	
	volatile Refresher activeRefresh;
	volatile boolean refreshActive = false;
	
	/**
	 * Returns the size of this share.
	 * @return
	 */
	public long getSize() {
		if (status==Status.BUILDING || status==Status.REFRESHING) {
			try {
				return Math.max(activeRefresh.buildSizeSoFar, list.root.size);
			} catch (NullPointerException n) {
				return list.root.size;
			}
		} else {
			return list.root.size;
		}
	}
	
	/**
	 * Returns the number of files in this share
	 * @return
	 */
	public long getFileCount() {
		if (status==Status.BUILDING || status==Status.REFRESHING) {
			try {
				return Math.max(activeRefresh.tracker.getPosition(), list.root.fileCount);
			} catch (NullPointerException n) {
				return list.root.fileCount;
			}
		} else {
			return list.root.fileCount;
		}
	}
	
	public String describeStatus() {
		switch (status) {
		case BUILDING:
			try {
				ProgressTracker tr = activeRefresh.tracker;
				String msg = "building";
				if (!refreshActive) {
					msg += " (queued)";
				} else {
					msg += " at " + new NiceMagnitude((long)activeRefresh.tracker.getSpeed(),"") + " files/s";
					if (tr.getMaximum() > tr.getPosition()) {
						// Maximum expected is actually set, meaning we must have scanned for file counts
						msg += ", ETR: " + tr.describeTimeRemaining();
					}
				}
				return msg;
			} catch (NullPointerException n) {};
		case REFRESHING:
			try {
				ProgressTracker tr = activeRefresh.tracker;
				return tr.percentCompleteString()+" refreshed "+(refreshActive ? "at "+new NiceMagnitude((long)tr.getSpeed(),"")+" files/s, ETR: "+tr.describeTimeRemaining() : "(queued)");
			} catch (NullPointerException n) {};
			case ERROR:
			return describeError();
		case ACTIVE:
			return "Active";
		case SAVING:
			return "Saving";
		default:
			return status.toString().toLowerCase();
		}
	}
	
	public String describeError() {
		if (cause==ErrorCause.NOTFOUND) {
			return "error: not found on disk";
		} else if (cause == ErrorCause.UNSAVEABLE) {
			return "error: file list unsaveable: "+causeOtherDescription;
		} else return "error: "+causeOtherDescription;
	}
	
	public Share(ShareServer ssvr, String name, File location) throws IOException {
		this.location = location;
		this.canonicalLocation = location.getCanonicalFile();
		this.ssvr = ssvr;
		
		listFile = Platform.getPlatformFile("filelists"+File.separator+name+".FileList");
		
		if (listFile.exists()) {
			try {
				InputStream is = new BufferedInputStream(new FileInputStream(listFile));
				list = FileList.reconstruct(is);
				is.close();
				if (list==null) newFileList(name);
				
				//The user might (for some reason) have changed the case of the share name, so update the filelist now:
				if (!list.getName().equals(name)) {
					Logger.log("Share name doesn't match FileList's internal name... updating.");
					list.root.name = name;
					saveList();
				}
				
			} catch (FileNotFoundException what) {
				Logger.severe("File that exists couldn't be found!?: "+what);
				Logger.log(what);
			}
		} else {
			newFileList(name);
		}
		
		context = ssvr.getHttpServer().createContext("/shares/"+HttpUtil.urlEncode(name),
				new HttpFileHandler(location, ssvr.getHttpEventsHandler(), ssvr.getUploadTracker()));
		context.getFilters().add(ssvr.getSecureFilter());
		context.getFilters().add(ssvr.getFS2Filter());
		context.getFilters().add(ssvr.getQueueFilter());
	    context.getFilters().add(ssvr.getThrottleFilter());
		
		//If we just created a new filelist then it must be built for the first time, else refreshed.
		if (list.revision==0) {
			Logger.log("Share '"+name+"' is being built for the first time.");
			scheduleRefresh(true);
		} else {
			setStatus(Status.ACTIVE);
		}
	}
	
	private void newFileList(String name) {
		//Create a new list from scratch.
		list = FileList.newFileList(name);
	}
	
	/**
	 * Schedules this share to be refreshed.
	 */
	public void refresh() {
		scheduleRefresh(false);
	}
	
	/**
	 * Schedule this share to be refreshed when there is space in the refresh pool.
	 * @param firstRefresh specify true iff this is the initial refresh.
	 */
	private synchronized void scheduleRefresh(boolean firstRefresh) {
		synchronized (status) { if (status==Status.SHUTDOWN) return; } //can't refresh a shutdown share.
		if (activeRefresh==null) {			 						   //Only do something if there is no active/scheduled refresher already.
			if (!firstRefresh) setStatus(Status.REFRESHING);
			activeRefresh = new Refresher();
			ssvr.getShareRefreshPool().execute(activeRefresh);
		}
	}
	
	private void refreshComplete() {
		
		list.setRefreshedNow();
		if (saveList()) {
			setStatus(Status.ACTIVE);	
		}

		ssvr.getIndexNodeCommunicator().sharesChanged(); //does not return immediately.
	}
	
	public synchronized void shutdown() {
		if (activeRefresh!=null) activeRefresh.shutdown();
		ssvr.getHttpServer().removeContext(context);
		setStatus(Status.SHUTDOWN);
	}
	
	private boolean saveList() {
		try {
			setStatus(Status.SAVING);
			File partial = new File(listFile.getAbsoluteFile()+".working");
			
			if (partial.exists()) partial.delete();
			
			FileOutputStream fos = new FileOutputStream(partial);
			list.deconstruct(fos);
			fos.close();
			
			if (listFile.exists()) listFile.delete();
			
			if (!partial.renameTo(listFile)) {
				throw new IllegalStateException("Couldn't rename the working filelist for share '"+getName()+"'");
			}
			return true;
		} catch (Exception e) {
			Logger.severe("Share filelist couldn't be saved: "+e.toString());
			Logger.log(e);
			causeOtherDescription = e.toString();
			cause = ErrorCause.UNSAVEABLE;
			setStatus(Status.ERROR);
			return false;
		}
	}
	
	public String getName() {
		return list.getName();
	}
	
	public int getRevision() {
		return list.revision;
	}
	
	private void setStatus(Status newStatus) {
		synchronized (status) {
			if (status!=newStatus) {
				status = newStatus;
				Logger.log("Share '"+getName()+"' became "+getStatus());
				if (status!=Status.SHUTDOWN) ssvr.notifyShareChanged(this);
			}
		}
	}
	
	private NotifyShareServer notifyShareServer = new NotifyShareServer();
	private class NotifyShareServer implements Deferrable {
		@Override
		public void run() {
			ssvr.notifyShareChanged(Share.this);
		}
	}
	
	public Status getStatus() {
		return status;
	}

	public void setPath(File path) throws IOException {
		this.location = path;
		this.canonicalLocation = location.getCanonicalFile();
		this.refresh();
	
	}
	
	/**
	 * Returns the timestamp of when this share was last successfully refreshed.
	 * @return
	 */
	public long getLastRefreshed() {
		return list.getLastRefreshed();
	}

	/**
	 * Returns the path that this shares.
	 * @return
	 */
	public File getPath() {
		return location;
	}
	
}
