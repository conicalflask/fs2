package common;

import java.io.IOException;

import common.httpserver.HttpExchange;
import common.httpserver.HttpHandler;

public class SimpleHttpHandler implements HttpHandler {

	private int status;
	private String message;
	
	public SimpleHttpHandler(int inStatus, String msg) {
		status = inStatus;
		message = msg;
	}
	
	public void handle(HttpExchange exchange) throws IOException {
		HttpUtil.simpleResponse(exchange, message, status);
	}

}
