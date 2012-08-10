package indexnode;

import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.Random;

import common.httpserver.HttpExchange;
import common.httpserver.HttpHandler;
import common.HttpUtil;
import common.Logger;

/**
 * Allows downloading of a file from fs2.
 * 
 * The file to be downloaded is specified by a hash, and a candidate for this file
 * is selected at random from all files in the filesystem that match this file.
 * 
 * @author gary
 *
 */
public class IndexDownloader implements HttpHandler {
	private Filesystem fs;
	private Random gen = new Random();
	
	public IndexDownloader(Filesystem fs) {
		this.fs = fs;
	}
	
	public void handle(HttpExchange exchange) throws IOException {
		try {
			Collection<? extends FilesystemEntry> hashMatches = null;
			
			String path = HttpUtil.getPathAfterContext(exchange);
			hashMatches = fs.searchForHash(path);
			if (hashMatches.size() == 0) {
				//Then they supplied a hash that matches no files:
				HttpUtil.simple404(exchange);
				return;
			}
			
			//Collections do not allow random access, so linearly march through the matches until a random
			// position, then redirect the client to the location of this file.
			int pickedIndex = gen.nextInt(hashMatches.size());
			for (FilesystemEntry pickedEntry : hashMatches) {
				if (pickedIndex-- == 0) {
					Logger.log(exchange.getRequestHeaders().getFirst("fs2-alias")+" requested: "+pickedEntry.getName());
					HttpUtil.redirectToURL(exchange, new URL(pickedEntry.getURL()));
					fs.incrementSent(pickedEntry.getSize());
					break;
				}
			}
			
		} catch (Exception e) {
			Logger.severe("Exception handling a download request: " +e.toString());
			Logger.log(e);
			//If we're here then things have gone so wrong we shouldn't bother with nice output.
			HttpUtil.simpleResponse(exchange, "Your request couldn't be handled due to an internal exception.", 500);
		}
	}

}
