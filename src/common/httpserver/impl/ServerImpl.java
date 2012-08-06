package common.httpserver.impl;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLServerSocket;

import common.Logger;
import common.httpserver.HttpContext;
import common.httpserver.HttpHandler;
import common.httpserver.HttpServer;

/**
 * An implementation of an HttpServer meeting a subset of the com.sun.net.httpserver.HttpServer specification.
 * 
 * This will not efficiently handle many contexts, lookup time per request is O(n) where n=number of contexts.
 * This is likely to be sufficient until there are more than thousands of contexts due to likely network/handling overheads.
 * 
 * @author gp
 */
public class ServerImpl extends HttpServer {

	ServerSocket insecureSocket = null;
	SSLServerSocket secureSocket = null;
	LinkedList<ContextImpl> contexts = new LinkedList<ContextImpl>();
	ExecutorService executor = Executors.newSingleThreadExecutor(); //setup a default executor.
	Thread insecureListener;
	Thread secureListener;
	
	public ServerImpl(InetSocketAddress insecure, InetSocketAddress secure, SSLContext context, String[] cipherSuites, int backlog) throws IOException {
		if (insecure!=null) {
			insecureSocket = new ServerSocket(insecure.getPort(), backlog, insecure.getAddress());
			insecureSocket.setPerformancePreferences(2, 1, 3); //probably has no effect on the TCP connections we're using.
		}
		if (secure!=null) {
			secureSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket(secure.getPort(), backlog, secure.getAddress());
			if (cipherSuites!=null) secureSocket.setEnabledCipherSuites(cipherSuites);
			secureSocket.setPerformancePreferences(2, 1, 3); //again, probably no effect.
		}
	}
	
	@Override
	public HttpContext createContext(String path, HttpHandler handler) {
		ContextImpl nc = new ContextImpl(path, handler);
		synchronized (contexts) {
			contexts.add(nc);
		}
		return nc;
	}

	@Override
	public ExecutorService getExecutor() {
		return executor;
	}

	@Override
	public void removeContext(HttpContext context) {
		synchronized (contexts) {
			contexts.remove(context);
		}
	}

	@Override
	public void setExecutor(ExecutorService exec) {
		this.executor = exec;
	}
	
	/**
	 * Returns the matching context for the URI specified.
	 * @param requestUri
	 * @return The context that will service this request, or null if none match.
	 */
	ContextImpl determineContext(URI requestUri) {
		String path = requestUri.toString();
		int mLength = 0;
		ContextImpl ret = null;
		synchronized (contexts) {
			for (ContextImpl c : contexts) {
				if (path.startsWith(c.path) && c.path.length() > mLength) {
					mLength = c.path.length();
					ret = c;
				}
			}
		}
		return ret;
	}
	
	@Override
	public void start() {
		if (insecureSocket!=null) insecureListener = listenOn(insecureSocket);
		if (secureSocket  !=null) secureListener   = listenOn(secureSocket);
	}

	private Thread listenOn(final ServerSocket socket) {
		Thread listener = new Thread(new Runnable() {
			@Override
			public void run() {
				while (true) {
					try {
						final Socket req = socket.accept();
						//set socket options:
						req.setKeepAlive(useKeepAlives);
						req.setSoTimeout(soTimeout);
						
						//Simulate a slow connection:
//						try {
//							Thread.sleep(1000);
//						} catch (InterruptedException e1) {
//							// TODO Auto-generated catch block
//							e1.printStackTrace();
//						}
						
						executor.submit(new Runnable() {
							@Override
							public void run() {
								try {
									try {
										new ExchangeImpl(req, ServerImpl.this);}
									finally {
										if (!req.isClosed()) req.close();
									}
								} catch (SSLException e) {
									Logger.warn(req.getRemoteSocketAddress()+" can't handshake with us: "+e);
								} catch (SocketException e) { 
									Logger.warn("General socket bork while handling HTTP(s) from '"+req.getRemoteSocketAddress()+"': "+e);
								} catch (SocketTimeoutException e) { 
									Logger.warn("Socket timeout while handling HTTP(s) from '"+req.getRemoteSocketAddress()+"': "+e);
								} catch (IOException e) {
									Logger.warn("Can't handle HTTP"+(socket instanceof SSLServerSocket ? "S" : "")+" request from "+req.getRemoteSocketAddress()+": "+e);
									e.printStackTrace();
								} catch (Throwable t) {
									Logger.severe("Unexpected throwable by http exchange: "+t);
									t.printStackTrace();
								}
							}
						});
					} catch (IOException e) {
						if (Thread.currentThread().isInterrupted()) return; //shutdown listener
						Logger.warn("Can't accept an HTTP"+(socket instanceof SSLServerSocket ? "S" : "")+" request: "+e);
						e.printStackTrace();
					}
				}
			}
		},this.getClass().getCanonicalName()+(socket instanceof SSLServerSocket ? " secure" : " insecure")+" listener");
		listener.setDaemon(false);
		listener.start();
		return listener;
	}

	@Override
	public void stop() {
		try {
			executor.shutdownNow();
			if (insecureListener!=null) {
				insecureListener.interrupt();
				insecureSocket.close();
			}
			if (secureListener!=null) {
				secureListener.interrupt();
				secureSocket.close();
			}
		} catch (IOException e) {
			Logger.warn("While closing http server socket: "+e);
			e.printStackTrace();
		}
	}

}
