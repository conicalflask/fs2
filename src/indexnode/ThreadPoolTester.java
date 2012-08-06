package indexnode;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A proof of concept for threadpools.
 * @author Gary Plumbridge
 */
public class ThreadPoolTester {

	static class stringPrinterThread implements Runnable {
		private String printString;
		
		public stringPrinterThread(String inString) {
			printString = inString;
		}
		
		public void run() {
			System.out.println(printString+" starting+++++++++");
			try {
				Thread.sleep(2000);
			} catch (InterruptedException unused) {}
			System.out.println(printString+" ending===========");
		}
	}
	
	public static void main(String[] args) {
		ExecutorService exec = Executors.newFixedThreadPool(2);
		//ExecutorService exec = Executors.newCachedThreadPool();
		//ExecutorService exec = Executors.newSingleThreadExecutor();
		
		exec.submit(new stringPrinterThread("Red"));
		exec.submit(new stringPrinterThread("Green"));
		exec.submit(new stringPrinterThread("Blue"));
		exec.submit(new stringPrinterThread("Cyan"));
		exec.submit(new stringPrinterThread("Magenta"));
		exec.submit(new stringPrinterThread("Yellow"));
		System.out.println("Tasks submitted.");
	}

}
