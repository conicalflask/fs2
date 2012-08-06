package indexnode;

import java.io.IOException;
import java.net.URL;

import common.httpserver.HttpExchange;
import common.httpserver.HttpHandler;
import common.HttpUtil;

public class IndexRootHandler implements HttpHandler {

	public void handle(HttpExchange exchange) throws IOException {
		HttpUtil.redirectToURL(exchange, new URL(HttpUtil.getClientURLToServerRoot(exchange)+"browse/"));
	}

}
