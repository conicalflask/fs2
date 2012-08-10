package common;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import common.httpserver.Headers;
import common.httpserver.HttpExchange;
import common.HttpFileHandler.HttpFileHandlerEvents;

public class HttpUtil {
	
	/**
	 * returns the value of the first cookie in the request with the specified name.
	 * Will return "" if the cookie doesn't exist
	 */
	public static String getCookie(HttpExchange exchange, String cookieName) {
		String retval = "";
		if (exchange.getRequestHeaders().get("Cookie") == null) return retval;
		for (String cookieString : exchange.getRequestHeaders().get("Cookie")) {
			String[] cookieBits = cookieString.split("=");
			if (cookieBits.length != 2) continue;
			if (cookieBits[0].equals(cookieName)) return cookieBits[1];
		}
		return retval;
	}
	
	/**
	 * Adds a header to the request that specifies that the content range should be from startPosition to the end.
	 * @param conn
	 * @param startPosision
	 * @param endPosition The index of the last byte index to recieve. if zero, then all remaining bytes will be requested.
	 */
	public static void setRequestRange(HttpURLConnection conn, long startPosition, long endPosition) {
		conn.addRequestProperty("Range", "bytes="+Long.toString(startPosition)+"-"+(endPosition == 0 ? "" : endPosition));
	}
	
	/**
	 * @param inPath Any normal looking path (ie the path after a host/port in a url)
	 * @return the string of the filename only in the path. (just the last component of the path)
	 */
	public static String pathBasename(String inPath) {
		return (new File(inPath)).getName();
	}
	
	/**
	 * Returns the parameters passed to the request on the URL in the form: ?arg1=val1&arg2=val2 etc.
	 * 
	 * This url decodes each argument, so ensure that arguments are urlencoded in the first instance if they contain clashing chars.
	 * 
	 * @param exchange
	 * @return
	 */
	public static HashMap<String, String> getArguments(HttpExchange exchange) {
		HashMap<String, String> retval = new HashMap<String, String>();
		
		if (exchange.getRequestURI().getQuery() != null) {
			String query = exchange.getRequestURI().getRawQuery();
			String[] argvalPairs = query.split("&");
			for (int i=0;i<argvalPairs.length;i++) {
				String[] argVal = argvalPairs[i].split("=");
				if (argVal.length == 2) {	//Only do it for correct pairs...
					retval.put(argVal[0], HttpUtil.urlDecode(argVal[1]));
				}
			}
		}
		
		return retval;
	}
	
	/**
	 * Send a very simple one string response to the client with the status code specified.
	 * Closes the connection and exchange too.
	 * @param exchange
	 * @param msg
	 * @param statusCode
	 * @throws IOException
	 */
	public static void simpleResponse(HttpExchange exchange, String msg, int statusCode) throws IOException {
		simpleBinaryResponse(exchange, msg.getBytes("UTF-8"), statusCode);
	}
	
	/**
	 * Like simpleResponse but sends a byte buffer verbatim.
	 * The byte buffer is unmodified by this.
	 * @param exchange
	 * @param msg
	 * @param statusCode
	 * @throws IOException
	 */
	public static void simpleBinaryResponse(HttpExchange exchange, byte[] msg, int statusCode) throws IOException {
		try {
			exchange.getResponseHeaders().add("Content-Type", "text/html");
			exchange.sendResponseHeaders(statusCode,msg.length);
			OutputStream response = exchange.getResponseBody();
			response.write(msg);
			response.flush();
			response.close();
		} finally {
			exchange.close();
		}
	}
	
	public static void simple404(HttpExchange exchange) throws IOException {
		simpleResponse(exchange, "File not found.", 404);
	}
	
	public static void simple403(HttpExchange exchange) throws IOException {
		simpleResponse(exchange, "Forbidden.", 403);
	}
	
	public static void simple400(HttpExchange exchange) throws IOException {
		simpleResponse(exchange, "Bad request.", 400);
	}
	
	/**
	 * Sets appropriate headers and sends the file. 
	 * Do security and validation before calling this.
	 * This is a one-shot service. Only this file is provided on the output, then the exchange is closed.
	 */
	public static void sendFileOnly(HttpTransferInfo info) throws FileNotFoundException, IOException {
		HttpExchange exchange = info.getExchange();
		int responseCode = 200;
		File fileToSend = info.getFile();
		long responseLength = fileToSend.length();
		info.setPosition(0);
		info.setRemaining(responseLength);
		Headers h = exchange.getResponseHeaders();
	
		try {
			if (exchange.getRequestHeaders().containsKey("Range")) {
				String requestRange = exchange.getRequestHeaders().getFirst("Range");
				//Logger.log("range: "+requestRange);
				Pattern pattern = Pattern.compile("bytes=([0-9]*)-([0-9]*)");
			    Matcher matcher = pattern.matcher(requestRange);
			    if (matcher.matches()) {
			    	try {
			    		long rangeStart = -1;
			    		if (!matcher.group(1).equals("")) rangeStart = Long.parseLong(matcher.group(1));
			    		long rangeEnd = -1;
			    		if (!matcher.group(2).equals("")) rangeEnd = Long.parseLong(matcher.group(2));
			    		
			    		long newPosition = 0;
			    		long newRemaining = 0;
			    		long length = fileToSend.length();
			    		
			    		//There are three different valid simple ways the user can request ranges.
			    		//(We do NOT support multiple range requests.)
			    		
			    		//1) Just a start point: (this is the number of bytes to skip at the start of the file)
			    		if (rangeStart > -1 && rangeEnd == -1) {
			    			newPosition = rangeStart;
			    			newRemaining = length-rangeStart;
			    		}
			    		//2) Just an end point: (this is how many bytes to return from the end of the file)
			    		else if (rangeStart == -1 && rangeEnd > -1) {
			    			newPosition = length-rangeEnd;
			    			newRemaining = rangeEnd+1;
			    		}
			    		//3) Both are specified: (this is a proper range!)
			    		else if (rangeStart > -1 && rangeEnd > -1) {
			    			newPosition = rangeStart;
			    			newRemaining = (rangeEnd+1)-rangeStart;
			    		}
			    		
			    		//Now sanity check, if it is ok, then setup the partial transfer.
			    		if (newPosition > length ||
			    			newPosition + newRemaining > length) {
			    			Logger.log("Too much data was requested. Sending a normal (not partial) response.");
			    		} else {
			    			info.setPosition(newPosition);
			    			info.setRemaining(newRemaining);
			    			responseLength = newRemaining;
			    			String crh = "bytes "+newPosition+"-"+((newRemaining+newPosition)-1)+"/"+newRemaining;
			    			h.add("Content-Range", crh);
			    			//Logger.log("content-range: "+crh);
			    			responseCode = 206;
			    		}
			    		
			    	} catch (Exception e) {
			    		Logger.log("Couldn't service range request: "+requestRange+" - "+e.toString());
			    	}
			    }
			}
			info.setupTracker();
			h.add("Accept-Ranges", "bytes");
			h.add("Content-Disposition", "attachment; filename=\""+fileToSend.getName()+"\";");
			h.add("Content-Transfer-Encoding","binary");
			h.add("Content-Length", Long.toString(responseLength));
			h.add("Content-Type", "application/octet-stream");
			exchange.sendResponseHeaders(responseCode, responseLength);
			OutputStream response = exchange.getResponseBody();
			writeFileToStream(fileToSend, response, info);
			response.flush();
			response.close();
		} finally {
			exchange.close();
		}
	}
	
	
	public static interface TransferInfoPosition {
		public long getPosition();
		public void setPosition(long position);
		public long getRemaining();
		public void setRemaining(long remaining);
	}

	/**
	 * Used to encapsulate an HttpFile upload.
	 * @author gary
	 */
	public static class HttpTransferInfo implements TransferInfoPosition {
		private final HttpExchange exchange;
		private long position = 0;
		private final File file;
		private final int uid;
		private long remaining = 0;
		private final HttpFileHandlerEvents events;
		private boolean shouldStop = false;
		private long lastCheckedTime;
		private final ProgressTracker tracker;
		
		HttpTransferInfo(HttpExchange inExchange, File inFile, HttpFileHandlerEvents inEvents, int uid, List<HttpTransferInfo> infos, ProgressTracker tracker) {
			exchange = inExchange;
			file = inFile;
			events = inEvents;
			this.uid = uid;
			infos.add(this);
			lastCheckedTime = System.currentTimeMillis();
			this.tracker = tracker;
			events.transferStarted(this); //this obviously has to come last.
		}

		public void setupTracker() {
			tracker.setExpectedMaximum(remaining);
		}

		public synchronized ProgressTracker getTracker() {
			return tracker;
		}
		
		public void stopTransfer() {
			shouldStop = true;
		}
		
		public boolean shouldStop() {
			return this.shouldStop;
		}
		
		public synchronized long getPosition() {
			return position;
		}

		public synchronized void setPosition(long position) {
			this.position = position;
		}

		public HttpExchange getExchange() {
			return exchange;
		}

		public File getFile() {
			return file;
		}

		public int getUid() {
			return uid;
		}

		public synchronized long getRemaining() {
			return remaining;
		}

		public synchronized void setRemaining(long remaining) {
			this.remaining = remaining;
		}
		
		void transferComplete() {
			events.transferEnded(this);
		}

		public synchronized void sentBytes(int allocation) {
			this.remaining-=allocation;
			events.bytesTransferred(this, allocation);
			tracker.progress(allocation);
		}
		
		public String getAlias() {
			String a = exchange.getRequestHeaders().getFirst("fs2-alias");
			if (a==null || a.equals("")) {
				return "unknown@"+exchange.getRemoteAddress().getAddress().getHostAddress();
			} else {
				return a;
			}
		}
		
		/**
		 * Returns the number of milliseconds since this method was last called,
		 * or this object was created, whichever is less.
		 * 
		 * This can be used to find the time a transfer took, incrementally.
		 * 
		 * @return
		 */
		public long getInterval() {
			long now = System.currentTimeMillis();
			long ret = now-lastCheckedTime;
			lastCheckedTime = now;
			return ret;
		}
	}
	
	/*
	 * Writes the specified file out of the outputstream. Not really HTTP only, maybe this deserves it's own place in the world...
	 * 
	 * If the position argument is supplied, an object implementing TransferInfoPosition
	 *  will be updated with the position of the streaming.
	 */
	
	public static void writeFileToStream(File inFile, OutputStream stream, HttpTransferInfo info) throws 
										FileNotFoundException, IOException {
		byte[] buffer = new byte[FS2Constants.ARBITRARY_BUFFER_SIZE];
		int allocation;
		InputStream filestream=null;
		try {
			filestream = new BufferedInputStream(new FileInputStream(inFile));
		
			//Skip part of the file if the position isn't 0
			if (info.getPosition() > 0) {
				long toSkip = info.getPosition();
				while (toSkip > 0) {
					toSkip -= filestream.skip(toSkip);
				}
			}
			
			while (info.getRemaining() > 0 && !info.shouldStop()) {
				int nextReadRequest = (int)(info.getRemaining() < buffer.length ? info.getRemaining() : buffer.length);
				allocation = filestream.read(buffer,0,nextReadRequest);
				info.setPosition(allocation+info.getPosition());
				stream.write(buffer,0,allocation);
				info.sentBytes(allocation);
			}
		} finally {
			if (filestream != null) filestream.close();
		}
	}
	
	/**
	 * Get the path to the file/resource requested after the httpcontext part of the URL has been stripped away.
	 * @param exchange
	 * @return
	 */
	public static String getPathAfterContext(HttpExchange exchange) {
		URI requestURI = exchange.getRequestURI();
		String pathToHandler = exchange.getHttpContext().getPath()+"/";
		try {
			return HttpUtil.urlDecode(requestURI.getRawPath().substring(pathToHandler.length()));
		} catch (IndexOutOfBoundsException e) {
			return "";
		}
	}
	
	/**
	 * Generate the nicest possible url to the root path on this server from the client's point of view.
	 * @param exchange
	 * @return
	 */
	public static String getClientURLToServerRoot(HttpExchange exchange) {
		Headers rHeaders = exchange.getRequestHeaders();
		String potentialAddr = "";
		if (rHeaders.containsKey("Host")) {
			potentialAddr = "/"+rHeaders.getFirst("Host");
		} else {
			potentialAddr = exchange.getLocalAddress().toString();
		}
		return "http:/"+potentialAddr+"/";
	}
	
	/**
	 * Redirects an http client to a new URL
	 * The URL should be validly URLencoded so the client may understand it.
	 */
	public static void redirectToURL(HttpExchange exchange, URL newURL) throws IOException {
		exchange.getResponseHeaders().add("Location", newURL.toString());
		//Send the temporary redirect response and also send a message to old or stubborn clients.
		simpleResponse(exchange, "Your browser must support HTTP/1.1 redirects to continue.\n<br/>Redirect to: "+newURL.toString(), 307);
	}
	
	/**
	 * Does what it says: A connection is openened to the url and the entire response is placed into a string and returned.
	 * @param url The location of the resource.
	 * @return The string contained at the resource.
	 * @throws IOException 
	 */
	public static String simpleDownloadToString(URL url) throws IOException {
		StringBuilder sb = new StringBuilder();
		InputStream is = url.openStream();
		byte[] buf = new byte[1024];
		int amount;
		while ((amount=is.read(buf))>0) {
			sb.append(new String(buf,0,amount));
		}
		is.close();
		return sb.toString();
	}
	
	public interface SimpleDownloadProgress {
		public void totalSize(long totalSize);
		public void progress(long downloadedBytes);
	}
	
	/**
	 * A very basic URL downloader. It will overwrite the file saveAs if it already exists.
	 * This will either succeed or throw an exception. There is no provision for retries, partial downloads etc.
	 * @param saveAs
	 * @param progress You can use this interface to track the progress of this simple download. This is called every time a read() completes on the underlying stream so this had better be fast!
	 * @throws IOException 
	 */
	public static void simpleDownloadToFile(URL url, File saveAs, SimpleDownloadProgress progress) throws IOException {
		HttpURLConnection uc = (HttpURLConnection) url.openConnection();
		if (progress!=null) {
			String clength = uc.getHeaderField("content-length");
			if (clength!=null) {
				try {
					progress.totalSize(Long.parseLong(clength));
				} catch (Exception e) {
					Logger.warn("Couldn't convert the content-length header to a Long: "+e);
				}
			}
		}
		InputStream is = null;
		try {
			is = uc.getInputStream();
			//BandwidthSharer bs = new BandwidthSharer();
			//bs.setBytesPerSecond(5000);
			//InputStream is = new ThrottledInputStream(uc.getInputStream(), bs);
			Util.writeStreamToFile(is, saveAs, progress);
		} finally {
			try {
				if (is!=null) is.close();
			} finally {
				InputStream es = uc.getErrorStream();
				if (es!=null) es.close();
			}
		}
	}
	
	/**
	 * Safely gets an inputstream for an Http
	 * @param inUrl
	 * @return
	 * @throws IOException
	 */
	public static InputStream simpleGetHttpInputStream(URL inUrl) throws IOException {
		return null;
	}

	/**
	 * Returns a not-modified message to the client with no body.
	 * @param exchange
	 * @throws IOException 
	 */
	public static void simple304(HttpExchange exchange) throws IOException {
		try {
			exchange.sendResponseHeaders(304,0);
			OutputStream response = exchange.getResponseBody();
			response.flush();
			response.close();
		} finally {
			exchange.close();
		}
	}
	
	/**
	 * Simply url-encodes using UTF-8.
	 * This is useful to avoid having "utf-8" littered everywhere, and the inevitable catches.
	 * @param in
	 * @return
	 */
	public static String urlEncode(String in) {
		try {
			return URLEncoder.encode(in, "utf-8");
		} catch (UnsupportedEncodingException e) {
			Logger.severe("UTF-8 isn't supported on this system! "+e);
			//e.printStackTrace();
			return null;
		}
	}
	
	/**
	 * The dual of urlEncode, but decodes into a raw string.
	 * @param in
	 * @return
	 */
	public static String urlDecode(String in) {
		try {
			return URLDecoder.decode(in, "utf-8");
		} catch (UnsupportedEncodingException e) {
			Logger.severe("UTF-8 isn't supported on this system! "+e);
			//e.printStackTrace();
			return null;
		}
	}
	
}
