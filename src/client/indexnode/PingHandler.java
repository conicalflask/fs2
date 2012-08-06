package client.indexnode;

import java.io.IOException;

import common.httpserver.HttpExchange;
import common.httpserver.HttpHandler;
import common.HttpUtil;

/**
 * This HttpHandler simply spits out our sharelist.
 * The {@link IndexNodeOnlyFilter} will prevent accesses from non-indexnodes and register the 'ping'
 * @author gary
 *
 */
public class PingHandler implements HttpHandler {

	private IndexNodeCommunicator comm;
	
	public PingHandler(IndexNodeCommunicator comm) {
		this.comm = comm;
	}
	
	@Override
	public void handle(HttpExchange ex) throws IOException {
		synchronized (comm.shareListXML) {
			HttpUtil.simpleResponse(ex, comm.shareListXML, 200);
		}
	}

}
