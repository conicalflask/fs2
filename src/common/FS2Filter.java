package common;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Random;

import common.httpserver.Filter;
import common.httpserver.Headers;
import common.httpserver.HttpExchange;

/**
 * Used for HttpContexts to add FS2-specific HTTP response headers.
 * Also used for URLConnections to add appropriate FS2-request headers.
 *  (also sets urlconnection timeout)
 */
public class FS2Filter extends Filter {

	/**
	 * 
	 * @param conn The connection to add fs2 headers to.
	 * @param cltoken only needed chen communicating with the indexnode
	 */
	public void fs2FixupURLConnectionForClient(HttpURLConnection conn, Long cltoken) {
		fs2FixupCommon(conn);
		conn.setReadTimeout(FS2Constants.CLIENT_URL_CONNECTION_TIMEOUT_MS);
		conn.setConnectTimeout(FS2Constants.CLIENT_URL_CONNECTION_TIMEOUT_MS);
		conn.addRequestProperty("fs2-port", Integer.toString(port));
		conn.addRequestProperty("Cookie", "fs2-token="+Long.toString(queuetoken));
		conn.addRequestProperty("fs2-cltoken", cltoken.toString());
	}
	
	/**
	 * To be used when the indexnode retreives fileslists/pings the client.
	 * This allows an extra token to be sent back to the client to provide assurance that
	 * this indexnode is the one that the client registered with this session.
	 * 
	 * The token is supplied by the client, enabling them to know that it originated from them.
	 * 
	 * This does not provide secure identity checking for _which_ indexnode this is though.
	 * (ssl/some kind of keypair system would do for this)
	 * 
	 * @param conn
	 * @param cltoken
	 */
	public void fs2FixupURLConnectionForIndexNode(HttpURLConnection conn, Long cltoken) {
		fs2FixupCommon(conn);
		conn.setReadTimeout(FS2Constants.SERVER_URL_CONNECTION_TIMEOUT_MS);
		conn.setConnectTimeout(FS2Constants.SERVER_URL_CONNECTION_TIMEOUT_MS);
		conn.addRequestProperty("fs2-verify", cltoken.toString());
	}
	
	private void fs2FixupCommon(HttpURLConnection conn) {
		conn.addRequestProperty("fs2-version", FS2Constants.FS2_PROTOCOL_VERSION);
		conn.addRequestProperty("fs2-alias", alias);
		conn.addRequestProperty("User-Agent", FS2Constants.FS2_PROTOCOL_VERSION);
	}
	
	public String description() {
		return "FS2Filter makes responses from this HTTPServer look like they're from an FS2 server/client";
	}
	
	private String alias = "Unnamed";
	private int port = 0;
	private long queuetoken = 0;
	private boolean automaticIndexnode;
	
	public FS2Filter() {
		Random gen = new Random();
		queuetoken = gen.nextLong();
	}
	
	public void setAlias(String newAlias) {
		alias = newAlias;
	}
	
	public String getAlias() {
		return alias;
	}
	
	public void setPort(int inPort) {
		port = inPort;
	}
	
	public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
		Headers h = exchange.getResponseHeaders();
		h.add("fs2-version", FS2Constants.FS2_PROTOCOL_VERSION);
		h.add("fs2-port", Integer.toString(port));
		h.add("fs2-alias", alias);
		if (automaticIndexnode) h.add("fs2-automatic", "true");
		chain.doFilter(exchange);
	}

	/**
	 * Transforms an URL into 'fs2 secure' form, where http->https and portnumber->{portnumber+1}
	 * This does not support all URL forms! Just Fs2 http urls.
	 * 
	 * @param insecureURL
	 * @return new URL or null if impossible.
	 * @throws MalformedURLException 
	 */
	public static URL getFS2SecureURL(URL insecureURL) {
		try {
			return new URL("https://"+insecureURL.getHost()+":"+(insecureURL.getPort()+1)+insecureURL.getFile());
		} catch (MalformedURLException e) {
			Logger.log(e);
			Logger.severe("Can't securify URL: "+insecureURL.toString()+", "+e);
			return null;
		}
	}

	public void setAutomatic(boolean b) {
		automaticIndexnode = b;
	}
	
}
