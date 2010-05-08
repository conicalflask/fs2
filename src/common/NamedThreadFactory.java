package common;

import java.util.concurrent.ThreadFactory;

/**
 * A thread factory that allows naming and daemonisation of the threads it creates.
 * @author gary
 */
public class NamedThreadFactory implements ThreadFactory {
	
	boolean daemon;
	String name;
	
	public NamedThreadFactory(boolean daemon, String name) {
		this.daemon = daemon;
		this.name = name;
	}
	
	@Override
	public Thread newThread(Runnable r) {
		Thread nt = new Thread(r, name);
		nt.setDaemon(daemon);
		return nt;
	}
}
