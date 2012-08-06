package common.httpserver;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;

import javax.net.ssl.SSLContext;

import common.httpserver.impl.ServerImpl;

/**
 * An description/shell implementation of an embedded HTTP server in the style of the com.sun.net.httpserver.HttpServer class.
 * 
 * Rationale:
 * 1) com.sun.net.httpserver.HttpServer appears to have a resource leak on every platform, ports/anon_inodes/afd get lost.
 * 2) com.sun.net.httpserver.HttpServer is too complex, simplicity is a virtue?
 * 3) com.sun.net.httpserver.HttpServer is not part of the java specification, so some Java runtime libraries may not have it. (this is the reason this is a complete respecification, rather than just a provider implementation)
 * 4) Having my own implementation will expand my mind in another direction.
 * 5) It'll make debugging easier to have less opaque code.
 * 6) I can slot SSL into it more transparently than HttpsServer would have allowed.
 * 
 * Drawbacks:
 * 1) More code to maintain.
 * 2) Not all of com.sun.net.httpserver.HttpServer functionality will be supplied, but enough that it can be a drop-in replacement in FS2.
 * 
 * @author gp
 *
 */
public abstract class HttpServer {

	protected boolean useKeepAlives = true;
	protected int soTimeout = 0; 
	
	/**
	 * Return a new instance of an HttpServer, bound to the address provided
	 * @param addr The address to bind to.
	 * @param backlog The maximum number of queued connections waiting on the socket to be allowed.
	 * @return
	 * @throws IOException
	 */
	public static HttpServer create(InetSocketAddress addr, int backlog) throws IOException {
		return create(addr, null, null, null, backlog);
	}
	
	/**
	 * Returns a new instance of an HttpServer.
	 * 
	 * This allows secure HTTP servers (https) to be created that also use the same configuration but with a TLS/SSL socket underneith.
	 * 
	 * @param insecure The inetaddresss to bind a normal, plaintext HTTP server to. Use null for no HTTP server.
	 * @param secure The inetaddress to bind a secured, HTTPS server to. Use null fro no HTTPS server.
	 * @param context The SSLContext to run the secured server, null if no HTTPS server is used.
	 * @param cipherSuites An array of acceptable cipher suites to use for the HTTPS server. If null then the default suites are used.
	 * @param backlog The backlog of requests allowed. This is seriously limited on Windows platforms.
	 * @return the http server created.
	 */
	public static HttpServer create(InetSocketAddress insecure, InetSocketAddress secure, SSLContext context, String[] cipherSuites, int backlog) throws IOException {
		return new ServerImpl(insecure, secure, context, cipherSuites, backlog);
	}
	
	/**
	 * Set the executor that this server instance will use to dispatch requests.
	 * @param exec
	 */
	public abstract void setExecutor(ExecutorService exec);
	
	/**
	 * Creates a new context on this server, handled by the handler provided.
	 * 
	 * The semantics differ slightly from the com.sun.net documentation for HttpServer:
	 * ) Here, the matching context is the one that represents the longest prefix of the request path. (that is, the most specific matching context)
	 * )... How efficient this match is, is implementation defined.
	 * 
	 * @param path Subtree of paths to handle requests for.
	 * @param handler The handler to satisfy the requests.
	 * @return The context created.
	 */
	public abstract HttpContext createContext(String path, HttpHandler handler);

	/**
	 * Returns the currently installed executor for this server.
	 * @return
	 */
	public abstract ExecutorService getExecutor();

	/**
	 * Stops the http server immediately.
	 * No further connections are allowed after this method call.
	 */
	public abstract void stop();

	/**
	 * Starts the server. This entails starting a server thread. This call does not block.
	 */
	public abstract void start();
	
	/**
	 * Removes the specified context from the server. 
	 * @param context
	 */
	public abstract void removeContext(HttpContext context);

	/**
	 * Returns true if this server will use keepalives on its newly created sockets
	 * @return
	 */
	public boolean isUsingKeepAlives() {
		return useKeepAlives;
	}

	/**
	 * Sets keep alives on the sockets accepted by this server.
	 * @param useKeepAlives
	 */
	public void setUseKeepAlives(boolean useKeepAlives) {
		this.useKeepAlives = useKeepAlives;
	}

	/**
	 * Returns the socket timeout on sockets accepted by this server.
	 * @return
	 */
	public int getSoTimeout() {
		return soTimeout;
	}

	/**
	 * Sets the read() timeout on sockets accepted by this server.
	 * @param soTimeout the socket timeout in milliseconds. Zero for no timeout.
	 */
	public void setSoTimeout(int soTimeout) {
		this.soTimeout = soTimeout;
	}

}
