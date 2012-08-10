package client.platform;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;

import common.FS2Constants;
import common.Logger;
import common.Util;

import client.ClientExecutor;
import client.platform.updatesources.CacheSource;
import client.platform.updatesources.CodeUpdate;

/**
 * Contains methods used to relaunch FS2, either to updated, cached code or to increase the java heapsize.
 * 
 * FOR UPDATES:
 * Determines if there is a newer version of FS2 in the code updates storage, and if there is, that version is executed.
 * It's quite important that this code is correct as the oldest version installed on a system is the one that is used:
 * It is never 'overwritten' by updates.
 * 
 * FOR JVM HEAP:
 * Attemps to relaunch the jvm with more heap, this code is updated by the autoupdater as it is invoked later in the startup process.
 * 
 * @author gary
 */
public class Relauncher {

	/**
	 * Relaunches newer code if it exists.
	 * Returns true if a new instance was launched.
	 * 
	 * This can safely be called at anytime. (It will invoke ClientExecutor's shutdown)
	 * 
	 * @return True if an updated version was launched.
	 */
	public static boolean go() {
		CodeUpdate update = (new CacheSource()).getLatestUpdate();
		if (update==null) {
			Logger.log("There are no newer versions of FS2 in the update cache.");
		} else {
			return relaunch(update.location);
		}
		return false;
	}
	
	@SuppressWarnings({ "unchecked" })
	private static boolean relaunch(URL newJar) {
		Logger.log("Attempting to start a newer version of FS2... ("+newJar+")");
		URLClassLoader loader = new URLClassLoader(new URL[] { newJar }, null);
		try {
			//                    __/-This is used so that automatic refactoring might work in future if the entry point is changed.
			@SuppressWarnings("rawtypes")
			Class ce = loader.loadClass(ClientExecutor.class.getName());
			Method init = ce.getDeclaredMethod("main", String[].class);
			//Shutdown the current instance:
			ClientExecutor.shutdown();
			//Now launch the new instance:
			init.invoke(null, (Object)ClientExecutor.args);
			
			return true;
			
		} catch (Exception e) {
			Logger.severe("The update is corrupt or incompatible with this version of FS2.");
			Logger.log("This version of FS2 will continue to execute.");
			Logger.log(e);
			return false;
		}
	}
	
	/**
	 * Attempts to relaunch the JVM and FS2 with the new heap-size in bytes specified.
	 * 
	 * the returns of this method are subtle:
	 * 
	 * false for outright instant failure: such as trying to relaunch not from a jar, or if the JVM is missing.
	 * true if the execution started and ended without exception.
	 * 
	 * @param pipeThrough if true then this process will wait in a minimal state passing through stderr and stdout. Otherwise these get disgarded. On os x you'll get a load of dock icons for the semi-active processes if piping is enabled.
	 * 
	 * @return 
	 */
	public static boolean increaseHeap(long newHeapSize, boolean pipeThrough) {
		try {
			String jvmPath = Platform.getCurrentPlatformOS().getJVMExecutablePath(); 
			
			//determine which jar we are executing within:
			String thisJarPath = new File(Relauncher.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath()).getAbsolutePath();
			
			//ensure running in a jar and not a debug environment.
			if (!thisJarPath.toLowerCase().endsWith(".jar")) {
				return false;
			}
			
			Logger.log("Attempting to relaunch JVM with more heap... "+Util.niceSize(newHeapSize));
			ClientExecutor.shutdown();
			
			ArrayList<String> args = new ArrayList<String>();
			args.add(jvmPath);
			args.add("-Xmx"+newHeapSize);
			
			//copy java properties to new jvm:
			for (String property : FS2Constants.CLIENT_IMPORTANT_SYSTEM_PROPERTIES) {
				if (System.getProperty(property)!=null) args.add("-D"+property+"="+System.getProperty(property));
			}
			
			if (pipeThrough) args.add("-Dincreasedheap"); //when piping through add a new system property to prevent looping restarts.
			
			args.add("-jar");
			args.add(thisJarPath);
			
			Process newJVM = Runtime.getRuntime().exec(args.toArray(new String[] {}));
			
			if (pipeThrough) {
				pipeOutput(newJVM);
				newJVM.waitFor();
			}
			
			return true; //at this point the subprocess has ended. Hopefully well...
		} catch (Exception e) {
			Logger.severe("Couldn't relaunch the JVM to increase the heapsize.");
			Logger.log("This version of FS2 will continue to execute.");
			Logger.log(e);
			return false;
		}
	}
	
	//From: http://stackoverflow.com/questions/60302/starting-a-process-with-inherited-stdin-stdout-stderr-in-java-6
	private static void pipeOutput(Process process) {
	    pipe(process.getErrorStream(), System.err);
	    pipe(process.getInputStream(), System.out);
	}
	private static void pipe(final InputStream src, final PrintStream dest) {
	    new Thread(new Runnable() {
	        public void run() {
	            try {
	                byte[] buffer = new byte[1024];
	                for (int n = 0; n != -1; n = src.read(buffer)) {
	                    dest.write(buffer, 0, n);
	                }
	            } catch (IOException e) { // just exit
	            }
	        }
	    }).start();
	}

}
