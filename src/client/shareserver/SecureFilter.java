package client.shareserver;

import java.io.IOException;

import client.indexnode.IndexNodeCommunicator;

import common.HttpUtil;
import common.Logger;
import common.httpserver.Filter;
import common.httpserver.HttpExchange;

/**
 * Used by the shareserver to enforce secure connections when all of our peers are secured, or (TODO) when the user has requested that only secure connections are allowed.
 * 
 * In the future this will drop connections that do not supply a valid 'fs2-auth' token. TODO
 * 
 * @author gp
 */
public class SecureFilter extends Filter {

	IndexNodeCommunicator comm;
	
	public SecureFilter(IndexNodeCommunicator comm) {
		this.comm = comm;
	}
	
	@Override
	public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
		if (comm.peersNeedInsecure() || exchange.isSecure()) {
			chain.doFilter(exchange);
		} else {
			Logger.log("Rejected file transfer to insecure peer ("+exchange.getRemoteAddress()+") when security is needed.");
			HttpUtil.simple403(exchange); //reject as they weren't secure when they should have been.
		}
	}

}
