package client.shareserver;

import java.io.IOException;

import common.httpserver.Filter;
import common.httpserver.HttpExchange;

/**
 * A filter that can be applied to an httpcontext to throttle the output bitrate.
 */
public class HttpThrottleOutputFilter extends Filter {

	private BandwidthSharer group;
	
	public HttpThrottleOutputFilter(BandwidthSharer sharer) {
		group = sharer;
	}
	
	public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
		exchange.setStreams(exchange.getRequestBody(), new ThrottledOutputStream(exchange.getResponseBody(),group));
		//propagate:
		chain.doFilter(exchange);
	}

}
