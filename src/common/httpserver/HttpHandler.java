package common.httpserver;

import java.io.IOException;

/**
 * An interface to represent classes that can satisfy http requests.
 * @author gp
 *
 */
public interface HttpHandler {

	/**
	 * Handles the request detailed by the HttpExchange object provided.
	 * @param exchange The exchange representing the client's request.
	 * @throws IOException
	 */
	public void handle(HttpExchange exchange) throws IOException;
	
}
