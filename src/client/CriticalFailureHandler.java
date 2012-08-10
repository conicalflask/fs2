package client;

import java.awt.HeadlessException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.SocketException;

import client.gui.Notifications;
import client.gui.SingleInstanceDetector;

import common.Logger;
import common.Util;

/**
 * Catches all kinds of 
 * @author gary
 *
 */
public class CriticalFailureHandler implements UncaughtExceptionHandler {
	@Override
	public void uncaughtException(Thread t, Throwable e) {
		if (e instanceof Error) {
			criticalException(e);
		} else {
			Logger.severe("Thread '"+t.getName()+"' didn't catch "+e);
			Logger.log(e);
		}
	}
	
	/**
	 * If there's a critical problem with FS2 that requires a complete, messy shutdown, then it should call this method with the throwable.
	 * @param t
	 */
	public static void criticalException(Throwable t) {
		String failureMessage = "";
		boolean printStackTrace=true;
		if (t instanceof SocketException) {
			printStackTrace=false;
			Logger.warn("Attempting to notify another instance of FS2...");
			if (SingleInstanceDetector.notifyOtherInstance()) {
				Logger.log("Success, closing this instance.");
				Runtime.getRuntime().halt(0);
			} else {
				failureMessage = "FS2 can't start because another program us using the network ports it needs.\nClose the other program (which might be another instance of FS2) and try again.";
			}
		}
		if (t instanceof OutOfMemoryError) {
			
			printStackTrace = false;
			long maxMemory = Runtime.getRuntime().maxMemory();
			if (maxMemory<250*1024*1024) {
				failureMessage = "FS2 has run out of memory! Your java was allowed "+Util.niceSize(maxMemory)+" but it is\nrecomended to have at least 256MiB if you're sharing lots or queueing lots of downloads.\n\nYou can allocate more memory to Java in the advanced settings.";
			} else {
				//If here then they already have a pretty good heap size, so maybe a memory leak or really large shares?
				failureMessage = "FS2 has run out of memory! You have a good sized maximum heap already ("+Util.niceSize(maxMemory)+") so...\n1) You're sharing too many files, so increase the memory limit some more.\n2) Your download queue is too big, increase your heap and try again. \n3) There's a memory leak I don't know about.";
			}
		}
		if (t instanceof HeadlessException) {
			printStackTrace=false;
			failureMessage = "FS2 can't start its GUI as your system is headless. If you actually want to run headless, use the '-Dheadless' command line switch.";
		}
		if (failureMessage.equals("")) failureMessage = "FS2 cannot start/continue due to a critical fault: "+t;
		Logger.severe(failureMessage);
		Notifications.notifyCriticalFailure(failureMessage+"\n\nThe FS2 Working Group (mostly conicalflask) would probably like to hear about this!\nIf this was unexpected please consider enabling logging to disk, and raise an issue at https://github.com/conicalflask/fs2/issues with the log file.\n\nCaused at: "+(t.getStackTrace().length > 0 ? t.getStackTrace()[0] : "unknown"), t.getClass().getSimpleName());
		if (Logger.getLogFile()!=null) Logger.log("A copy of much of this log is stored in: "+Logger.getLogFile().getAbsolutePath());
		if (printStackTrace) Logger.log(t);
		System.err.println("\nExiting disgracefully... Please raise an issue at https://github.com/conicalflask/fs2/issues with the stack trace if the reason is not obvious.");
		Runtime.getRuntime().halt(1);
	}
}
