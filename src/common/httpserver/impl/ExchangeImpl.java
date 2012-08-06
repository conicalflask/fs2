package common.httpserver.impl;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLSocket;

import common.HttpUtil;
import common.Logger;
import common.Util;
import common.httpserver.Headers;
import common.httpserver.HttpContext;
import common.httpserver.HttpExchange;
import common.httpserver.Filter.Chain;

/**
 * An implementation of the HttpExchange class.
 * @author gp
 *
 */
public class ExchangeImpl extends HttpExchange{

	public final static Pattern REQUEST_LINE_PATTERN = Pattern.compile("(.*?) (.*) HTTP/1\\.(\\d)");
	
	public final static HashMap<Integer, String> statuses;
	
	static {
	  statuses = new HashMap<Integer, String>();
	  statuses.put(100,"Continue");
	  statuses.put(101,"Switching Protocols");
	  statuses.put(200,"OK");
	  statuses.put(201,"Created");
	  statuses.put(202,"Accepted");
	  statuses.put(203,"Non-Authoritative Information");
	  statuses.put(204,"No Content");
	  statuses.put(205,"Reset Content");
	  statuses.put(206,"Partial Content");
	  statuses.put(300,"Multiple Choices");
	  statuses.put(301,"Moved Permanently");
	  statuses.put(302,"Found");
	  statuses.put(303,"See Other");
	  statuses.put(304,"Not Modified");
	  statuses.put(305,"Use Proxy");
	  statuses.put(307,"Temporary Redirect");
	  statuses.put(400,"Bad Request");
	  statuses.put(401,"Unauthorized");
	  statuses.put(402,"Payment Required");
	  statuses.put(403,"Forbidden");
	  statuses.put(404,"Not Found");
	  statuses.put(405,"Method Not Allowed");
	  statuses.put(406,"Not Acceptable");
	  statuses.put(407,"Proxy Authentication Required");
	  statuses.put(408,"Request Time-out");
	  statuses.put(409,"Conflict");
	  statuses.put(410,"Gone");
	  statuses.put(411,"Length Required");
	  statuses.put(412,"Precondition Failed");
	  statuses.put(413,"Request Entity Too Large");
	  statuses.put(414,"Request-URI Too Large");
	  statuses.put(415,"Unsupported Media Type");
	  statuses.put(416,"Requested range not satisfiable");
	  statuses.put(417,"Expectation Failed");
	  statuses.put(500,"Internal Server Error");
	  statuses.put(501,"Not Implemented");
	  statuses.put(502,"Bad Gateway");
	  statuses.put(503,"Service Unavailable");
	  statuses.put(504,"Gateway Time-out");
	  statuses.put(505,"HTTP Version not supported");
	}
	
	final Socket rsock;
	InputStream in;
	OutputStream out;
	ContextImpl context;
	Headers requestHeaders;
	final Headers responseHeaders = new Headers();
	URI requestURI;
	String requestMethod;
	
	/**
	 * Package-private constructor to build this exchange from only a socket with the client and the server it was created from.
	 * @throws IOException 
	 */
	ExchangeImpl(Socket request, ServerImpl srv) throws IOException {
		rsock = request;
		try {
			in = new BufferedInputStream(rsock.getInputStream());
			out = new BufferedOutputStream(rsock.getOutputStream());
			
			//Now process the request:
			//1) get and process request line
			if (!parseRequestLine()) return;
			//2) build headers
			try {
				requestHeaders = new Headers(in); 
			} catch (Exception e) {
				Logger.warn("Client sent invalid HTTP request headers: "+e);
				HttpUtil.simple400(this);
				return;
			}
			
			//now determine the context
			context = srv.determineContext(requestURI);
			
			if (context==null) {
				HttpUtil.simple404(this); //send them home, they've requested something we don't have.
				return;
			}
			
			//build the chain
			Chain ch = new Chain(context);
			//start the chain (this will eventually trigger all needed filters, and call the handler)
			ch.doFilter(this);
			
		} finally {
			close(); //ensure that the socket is always freed even if the HttpHandler forgets.
		}
	}
	
	/**
	 * Processes the Http-request line (see rfc) and returns true if it checks out.
	 * @return
	 * @throws IOException
	 */
	private boolean parseRequestLine() throws IOException {
		String reqLine = new String(Util.readLine(in)).trim();
		
		Matcher rlm = REQUEST_LINE_PATTERN.matcher(reqLine);
		if (!rlm.matches()) {
			Logger.warn("Client sent invalid HTTP request-line: "+reqLine);
			HttpUtil.simple400(this); //Send them home...
			return false;
		}
		requestMethod = rlm.group(1);
		try {
			//Logger.log("Requested URI: "+rlm.group(2));
			requestURI = new URI(rlm.group(2));
		} catch (URISyntaxException e) {
			Logger.warn("Client requested invalid URI from HttpServer: "+reqLine);
			HttpUtil.simple400(this); //Send them home...
			return false;
		}
		//TBH, we don't really care too much about the minor http version, but why not sanity check anyway?
		int httpMinor = Integer.parseInt(rlm.group(3));
		if (httpMinor != 1 && httpMinor != 0) {
			Logger.warn("Client requested to use an unsupported HTTP version: "+reqLine);
			HttpUtil.simple400(this); //Send them home...
			return false;
		}
		return true;
	}
	
	@Override
	public void close() {
		try {
			try {
				out.flush();
			} finally {
				rsock.close();
			}
		} catch (IOException e) {
			Logger.warn("While closing HTTP server socket: "+e);
			//e.printStackTrace();
		}
	}
	
	@Override
	public HttpContext getHttpContext() {
		return context;
	}

	@Override
	public InetSocketAddress getLocalAddress() {
		return (InetSocketAddress) rsock.getLocalSocketAddress();
	}

	@Override
	public InetSocketAddress getRemoteAddress() {
		return (InetSocketAddress) rsock.getRemoteSocketAddress();
	}

	@Override
	public InputStream getRequestBody() {
		return in;
	}

	@Override
	public Headers getRequestHeaders() {
		return requestHeaders;
	}

	@Override
	public URI getRequestURI() {
		return requestURI;
	}

	@Override
	public OutputStream getResponseBody() {
		return out;
	}

	@Override
	public Headers getResponseHeaders() {
		return responseHeaders;
	}

	@Override
	public void sendResponseHeaders(int rCode, long responseLength) throws IOException {
		String headerLine = "HTTP/1.1 "+rCode+" "+statuses.get(rCode)+"\r\n";
		out.write(headerLine.getBytes("ASCII"));
		responseHeaders.add("Content-Length", Long.toString(responseLength));
		out.write(responseHeaders.toString().getBytes("ASCII"));
		out.write("\r\n".getBytes("ASCII"));
	}

	@Override
	public void setStreams(InputStream requestBody, OutputStream responseBody) {
		this.in = requestBody;
		this.out = responseBody;
	}

	@Override
	public boolean isSecure() {
		return (rsock instanceof SSLSocket);
	}
}
