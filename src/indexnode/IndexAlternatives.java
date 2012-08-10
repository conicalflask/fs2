package indexnode;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;

import common.httpserver.HttpExchange;
import common.httpserver.HttpHandler;
import common.HttpUtil;
import common.Logger;
import common.Util;

public class IndexAlternatives implements HttpHandler {

	Filesystem fs;
	public IndexAlternatives(Filesystem fs) {
		this.fs = fs;
	}
	
	/**
	 * Enables clients to find all the alternative sources for a particular file on the network.
	 * It will only return one entry per peer, so these are real alternatives, not just alternative copies of the same file on one client
	 * 
	 * This not intended to be humanly browsable so will look poor.
	 */
	@Override
	public void handle(HttpExchange exchange) throws IOException {
		try {
			IndexTemplate template = new IndexTemplate(exchange);
			template.setTitle("Alternative sources...");
			
			//Get a copy of the list of all files with the same hash:
			LinkedList<FilesystemEntry> results = new LinkedList<FilesystemEntry>(fs.searchForHash(HttpUtil.getPathAfterContext(exchange)));
			
			//Keep track of the aliases that we've allowed into the result list...
			final HashSet<String> seenAliases = new HashSet<String>();
			
			//Filter the list... neat eh?
			Util.filterList(results, new Util.Filter<FilesystemEntry>() {
				@Override
				public boolean accept(FilesystemEntry item) {
					return seenAliases.add(item.getOwnerAlias());
				}
			});
			
			if (!results.isEmpty()) {
				Logger.log(exchange.getRequestHeaders().getFirst("fs2-alias")+" requested alts: "+results.getFirst().getName());
				fs.incrementSent(results.getFirst().getSize());
			}
			
			template.generateFilelist(results, true, false);
			
			template.sendToClient(exchange);
		} catch (IOException e) {
			if (e.getMessage().equals("Broken pipe")) Logger.warn("Ungrateful bastard client ('"+exchange.getRequestHeaders().getFirst("fs2-alias")+"') broke the pipe.");
		} catch (Exception e) {
			Logger.severe("Exception handling a browse request: " +e.toString());
			Logger.log(e);
			//If we're here then things have gone so wrong we shouldn't bother with nice output.
			HttpUtil.simpleResponse(exchange, "Your request couldn't be handled due to an internal exception.", 500);
		}
	}

}
