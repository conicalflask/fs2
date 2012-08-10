package indexnode;

import indexnode.IndexNode.Client;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import common.Base64Coder;
import common.FS2Constants;
import common.HttpFileHandler;
import common.HttpUtil;
import common.Logger;
import common.ProgressTracker;
import common.Util;
import common.Util.ImageResizeType;
import common.httpserver.HttpExchange;
import common.httpserver.HttpHandler;

/**
 * An http handler that recieves new avatars from a client.
 * @author gp
 *
 */
public class IndexAvatar implements HttpHandler {

	final File avatarDir;
	final IndexNode onNode;
	final HttpHandler avatarHandler;
	
	public IndexAvatar(File saveInto, IndexNode node) throws IOException {
		this.avatarDir = saveInto;
		this.onNode = node;
		
		if (!avatarDir.isDirectory() && !avatarDir.mkdirs()) {
			throw new IOException("Couldn't create avatar cache directory");
		}
		avatarHandler = new HttpFileHandler(saveInto, new HttpFileHandler.NullHttpFileHandlerEvents(), new ProgressTracker());
		
	}
	
	public HttpHandler getAvatarHandler() {
		return avatarHandler;
	}
	
	@Override
	public void handle(HttpExchange exchange) throws IOException {
		try {
			HashMap<String, String> args = HttpUtil.getArguments(exchange);
			
			Client cl = onNode.getClientFromExchange(exchange);
			if (cl==null) {
				//Only registered clients may submit avatars.
				Logger.warn("Unregistered user (from: "+exchange.getRemoteAddress()+") attempted to submit an avatar.");
				HttpUtil.simple403(exchange);
				return;
			}
			
			if (!args.containsKey("avatar")) {
				Logger.warn("User (from: "+exchange.getRemoteAddress()+") attempted to submit an avatar without an 'avatar' parameter.");
				HttpUtil.simple400(exchange);
				return;
			} 
			
			try {
				String inAv = args.get("avatar");
				String avHash = Util.md5(inAv);
				
				if (!cl.getAvatarHash().equals(avHash)) {
					
					File avFile = new File(avatarDir.getPath()+File.separator+avHash+".png");
					
					if (!avFile.isFile()) {
						byte[] sanitised = Util.processImage(new ByteArrayInputStream(Base64Coder.decode(inAv)), "png", FS2Constants.FS2_AVATAR_ICON_SIZE, FS2Constants.FS2_AVATAR_ICON_SIZE, ImageResizeType.NORATIO);
						Util.writeStreamToFile(new ByteArrayInputStream(sanitised), avFile);
					}
					
					cl.setAvatarHash(avHash);
				}
				
			} catch (Exception e) {
				HttpUtil.simple400(exchange);
			}
			
			HttpUtil.simpleResponse(exchange, "Success!", 200);
			
		} catch (Exception e) {
			Logger.warn("Exception handling chat request:"+e);
			Logger.log(e);
			HttpUtil.simpleResponse(exchange, "Your request couldn't be handled due to an internal exception.", 500);
		}
	}
}
