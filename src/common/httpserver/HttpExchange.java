package common.httpserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;

/**
 * A class to represent HTTP requests from clients.
 * 
 * This includes access to streams and headers.
 * 
 * @author gp
 *
 */
public abstract class HttpExchange {

	/**
	 * Ensures that all resources used by this exchange are freed.
	 * This will close open streams, close the TCP connection and free any non-heap resources allocated by the server during the request.
	 */
	public abstract void close();

	/**
	 * Get the headers that we will send to the client.
	 * @return
	 */
	public abstract Headers getResponseHeaders();
	
	/**
	 * Get the headers sent to us by the client.
	 * @return
	 */
	public abstract Headers getRequestHeaders();

	/**
	 * Get the URI for this request. This the URI as specified by the client, not the URL to the file on the server.
	 * For example, for /cow/moo.wav this method would return just that, not http://cowsounds.org/cow/moo.wav
	 * 
	 * This will include any context prefix, as it is the uri as requested.
	 * 
	 * @return
	 */
	public abstract URI getRequestURI();

	/**
	 * Return the inputstream from the client.
	 * @return
	 */
	public abstract InputStream getRequestBody();
	
	/**
	 * Return the outputstream from us to the client.
	 * @return
	 */
	public abstract OutputStream getResponseBody();

	/**
	 * Sets the streams for this request, presumably to implement filtering of some kind, such as compression, encryption, rate-limiting, etc.
	 * @param requestBody
	 * @param responseBody
	 */
	public abstract void setStreams(InputStream requestBody, OutputStream responseBody);

	/**
	 * Get the socket address of the client.
	 * @return
	 */
	public abstract InetSocketAddress getRemoteAddress();

	/**
	 * Issues the standard HTTP response to the client. This includes a serialisation of the headers and the standard HTTP response line.
	 * @param rCode
	 * @param responseLength
	 * @throws IOException
	 */
	public abstract void sendResponseHeaders(int rCode, long responseLength) throws IOException;

	/**
	 * Returns the context that this request was handled by.
	 * @return
	 */
	public abstract HttpContext getHttpContext();

	/**
	 * Gets the local socket address that the client used to contact us on.
	 * @return
	 */
	public abstract InetSocketAddress getLocalAddress();
	
	/**
	 * Returns true if this exchange is taking place over a secure socket.
	 * The definition of secure depends on the cipher suites in use, Some may not guarantee many or any important properties.
	 * This method returning true does _not_ guarantee perfection :)
	 * 
	 * @return
	 */
	public abstract boolean isSecure();
}
