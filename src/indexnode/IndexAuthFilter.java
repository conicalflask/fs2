package indexnode;

import java.io.IOException;

import common.HttpUtil;
import common.httpserver.Filter;
import common.httpserver.HttpExchange;

/**
 * This filter will drop clients that do not supply valid credentials.
 * 
 * It uses an extremely simple (read: insecure) scheme at the moment, this should be improved in future versions.
 * It's essentially a much simplified version of HTTP basic and as such should _never_ be used over a plain-text channel.
 * 
 * Scheme:
 * 1) Clients must supply an fs2-alias header, if they dont then they're dropped.
 * 2) Clients must also specify an fs2-auth header containing the MD5 of their password.
 * 3) The connection is terminated with a 403 unless their user exists, is authorised, and the MD5 supplied matches.
 * 
 * @author gary
 */
public class IndexAuthFilter extends Filter {	
	
	UserDatabase db;
	IndexNode node;

	public IndexAuthFilter(UserDatabase db, IndexNode node) {
		this.db = db;
		this.node = node;
	}
	
	@Override
	public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
		String suppliedAlias = exchange.getRequestHeaders().getFirst("fs2-alias");
		if (db.isAdmin(suppliedAlias)) exchange.getResponseHeaders().add("fs2-indexnodeadmin", "true"); //let the client know that they're a superuser on this indexnode, most facilities should ignore their admin-ness unless securemode is enabled though.

		//this filter always accepts if the indexnode is not in secure mode:
		if (!node.isSecure()) {
			chain.doFilter(exchange);
			return;
		} else {
			if (exchange.isSecure()) { //attempt authentication if they're securely connected
				String suppliedPassword = exchange.getRequestHeaders().getFirst("fs2-auth");
				
				//Sanity check:
				if (suppliedAlias == null ||
					suppliedPassword==null ||
					suppliedAlias.equals("") ||
					suppliedPassword.length()!=32) {
					HttpUtil.simple403(exchange);
					return;
				}
				if (!db.isValidAuthRequest(suppliedAlias, suppliedPassword)) {
					HttpUtil.simple403(exchange);
					return;
				}
				chain.doFilter(exchange); //they're secure and provided enough evidence of their identity.
			} else { //otherwise drop as TLS is essential for authentication to succeede.
				HttpUtil.simple403(exchange);
				return;
			}
		}
	}

}
