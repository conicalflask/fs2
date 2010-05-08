package common;

import common.Util.Deferrable;

/**
 * Saves an object not more often than specified, safely, and on shutdown.
 * 
 * @author gary
 */
public class SafeSaver implements Deferrable {

	/**
	 * Marks an object as capable of saving periodically.
	 * @author gary
	 */
	public interface Savable {
		/**
		 * Saves this object in the object specific way.
		 * 
		 * This method should be synchronized!
		 */
		public void doSave();
	}
	
	private Savable save;
	private long interval;
	
	/**
	 * Constructs a new SafeSaver, with the savable supplied and the minimum interval between saves in milliseconds.
	 * @param save
	 * @param minSaveInterval
	 */
	public SafeSaver(Savable save, long minSaveInterval) {
		this.save = save;
		this.interval = minSaveInterval;
	}
	
	/**
	 * Requests that the saveable is saved now. If this is called too often some calls will be ignored.
	 */
	public void requestSave() {
		Util.executeNeverFasterThan(interval, this);
	}
	
	/**
	 * Called by executeNeverFasterThan.
	 * This must not be called by other classes.
	 */
	@Override
	public synchronized void run() {
		save.doSave();
	}
	
	/**
	 * Saves the saveable and blocks until the save is fully complete.
	 * It is still executed in another (non-daemon) thread.
	 */
	public void saveShutdown() {
		run();
	}
}
