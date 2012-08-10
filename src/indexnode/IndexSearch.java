package indexnode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

import common.httpserver.HttpExchange;
import common.httpserver.HttpHandler;

import common.FS2Constants;
import common.HttpUtil;
import common.Logger;

/**
 * The search interface for the fs2 indexnode.
 * @author gary
 *
 */
public class IndexSearch implements HttpHandler {
	Filesystem fs;
	
	public IndexSearch(Filesystem fs) {
		this.fs = fs;
	}
	
	public void handle(HttpExchange exchange) throws IOException {
		try {
			IndexTemplate template = new IndexTemplate(exchange);
			template.setTitle("Search the filesystem...");
			
			HashMap<String, String> args = HttpUtil.getArguments(exchange);
			if (args.containsKey("q")) {
				String query = args.get("q");
				Logger.log(exchange.getRequestHeaders().getFirst("fs2-alias")+" searched for: "+query);
				
				Collection<? extends FilesystemEntry> res = fs.searchForName(query); //do the search
				
				//now we skim off INDEXNODE_SEARCH_MAX_RESULTS results, but only keeping items if they are directories or non-identical files.
				ArrayList<FilesystemEntry> results = new ArrayList<FilesystemEntry>(FS2Constants.INDEXNODE_SEARCH_MAX_RESULTS);
				
				HashSet<String> alreadyHave = new HashSet<String>(FS2Constants.INDEXNODE_SEARCH_MAX_RESULTS*2);
				
				int itemsCopied = 0;
				for (FilesystemEntry item : res) {
					if (item.isDirectory() || alreadyHave.add(item.getHash())) {  //sexy :)
						results.add(item);
						itemsCopied++;
					} else continue;
					
					if (itemsCopied>=FS2Constants.INDEXNODE_SEARCH_MAX_RESULTS) break;
				}
				
				template.setSearchHeader(query, results.size());
				template.generateFilelist(results, false, true);
			} else {
				template.setSearchHeader("", 0);
				template.generateFilelist(new LinkedList<FilesystemEntry>(), false, true);
			}
			
			template.sendToClient(exchange);
			
		} catch (Exception e) {
			Logger.severe("Exception handling a search request: " +e.toString());
			Logger.log(e);
			//If we're here then things have gone so wrong we shouldn't bother with nice output.
			HttpUtil.simpleResponse(exchange, "Your request couldn't be handled due to an internal exception.", 500);
		}

	}

}
