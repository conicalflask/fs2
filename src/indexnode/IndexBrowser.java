package indexnode;

import java.io.IOException;
import java.net.URL;
import java.util.Map;

import common.httpserver.HttpExchange;
import common.httpserver.HttpHandler;
import common.HttpUtil;
import common.Logger;

public class IndexBrowser implements HttpHandler {
	private Filesystem fs;
	
	public IndexBrowser(Filesystem fs) {
		this.fs = fs;
	}
	
	public void handle(HttpExchange exchange) throws IOException {
		try {
			//Thread.sleep(1000);//simulate internet latency
			
			FilesystemEntry requestedEntry = null;
			//A message to give to the user if they do something stupid or to tell them the status
			IndexTemplate template = new IndexTemplate(exchange);
			template.setTitle("Browse the filesystem...");
			
			String path = HttpUtil.getPathAfterContext(exchange);
			requestedEntry = fs.lookupFromPath(path);
			if (path.length() > 0 && (requestedEntry == null)) {
				//Then they supplied an invalid path:
				HttpUtil.simple404(exchange);
				return;
			}
			
			//Logger.log("Serving browse request: "+path);
			
			if (requestedEntry.isDirectory()) {
				template.setFileHeader(requestedEntry);
				Map<String, ? extends FilesystemEntry> children = requestedEntry.getChildren();
				synchronized (children) {
					template.generateFilelist(children.values(), false, false);
				}
			} else {
				//This interface to the indexnode is really for browsing structure. (directories)
				//However, it will also understand paths to files
				// in the case of a file path, redirect to /download/{hash}
				
				HttpUtil.redirectToURL(exchange, new URL(HttpUtil.getClientURLToServerRoot(exchange)+"download/"+requestedEntry.getHash()));
				return;
			}
			
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
