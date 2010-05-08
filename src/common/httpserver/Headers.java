package common.httpserver;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import common.Util;

/**
 * Represents HTTP headers.
 * 
 * toString provides a serialisation of these headers in the form you would expect to see during an HTTP communication.
 * 
 * @author gp
 */
@SuppressWarnings("serial")
public class Headers extends HashMap<String,LinkedList<String>> {
	
	//========== Helpers specified by com.sun.net.httpserver.Headers
	/**
	 * Returns the first header that matches the key specified.
	 * @param key 
	 * @return
	 */
	public String getFirst(String key) {
		LinkedList<String> potentials = this.get(key);
		if (potentials==null) {
			return null;
		}else {
			return this.get(key).getFirst();
		}
	}
	
	/**
	 * Add the specified header, even if the key already exists.
	 * @param key
	 * @param value
	 */
	public void add(String key, String value) {
		LinkedList<String> l = this.get(key);
		if (l==null) {
			l = new LinkedList<String>();
			l.add(value);
			this.put(key, l);
		} else {
			l.add(value);
		}
	}
	
	/**
	 * Sets the specified header to exactly this value, clearing all other headers with this key.
	 * @param key
	 * @param value
	 */
	public void set(String key, String value) {
		LinkedList<String> l = new LinkedList<String>();
		l.add(value);
		this.put(key, l);
	}
	
	/**
	 * An HTTP formatted serialisation of these headers
	 */
	@Override
	public String toString() {
		StringBuilder out = new StringBuilder();
		
		for (Entry<String, LinkedList<String>> header : this.entrySet()) {
			for (String value : header.getValue()) {
				out.append(header.getKey());
				out.append(": ");
				out.append(value);
				out.append("\r\n");
			}
		}
		
		return out.toString();
	}
	
	/**
	 * Create a new empty collection of headers
	 */
	public Headers() {}
	
	public class InvalidHttpHeaderFormat extends Exception {
		public InvalidHttpHeaderFormat(String description) {
			super(description);
		}
	}

	public final static Pattern HEADER_PATTERN = Pattern.compile("(.*):( (.*))?");
	
	/**
	 * Builds a new collection of headers from an HTTP formatted stream.
	 * @throws IOException 
	 */
	public Headers(InputStream in) throws InvalidHttpHeaderFormat, IOException {
		String lastKey = null;
		while (true) {
			byte[] read = Util.readLine(in);
			String rStr = new String(read).trim();
			if (rStr.equals("")) return; //end of headers.
			if (read[0] == ' ' || read[1] == '\t') {
				if (lastKey == null) throw new InvalidHttpHeaderFormat("An LWS was specified without a preceding header.");
				
				//append the LWS to the last header value created with this key.
				String lastValue = this.get(lastKey).removeLast();
				lastValue += rStr;
				this.get(lastKey).addLast(lastValue);
			} else {
				//attempt to match the header (which may be incomplete) with the regex:
				Matcher headm = HEADER_PATTERN.matcher(rStr);
				if (!headm.matches()) throw new InvalidHttpHeaderFormat("Header line does not conform to header-line format: "+rStr);
				this.add(lastKey = headm.group(1), headm.group(3));
			}
		}
	}
	
	
}
