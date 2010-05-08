package common.httpserver;

import java.io.IOException;
import java.util.LinkedList;

import common.httpserver.impl.ContextImpl;

/**
 * Enables an http request to be modified, or otherwise acted upon before it is handled.
 * 
 * This specification of filter specifically does not require a desciption, as I believe it to be unnecessary and messy.
 * 
 * @author gp
 *
 */
public abstract class Filter {

	/**
	 * The chain of filters in the current HttpExchange
	 * @author gp
	 */
	public static class Chain {
		
		LinkedList<Filter> filters;
		ContextImpl context;
		
		public Chain(ContextImpl context) {
			filters = new LinkedList<Filter>(context.getFilters());
			this.context = context;
		}
		
		public void doFilter(HttpExchange exchange) throws IOException {
			if (filters.isEmpty()) {
				context.getHandler().handle(exchange);
			} else {
				filters.removeFirst().doFilter(exchange, this);
			}
		}

	}

	public abstract void doFilter(HttpExchange exchange, Filter.Chain chain) throws IOException;
	
}
