package common;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.SocketException;
import java.nio.channels.ClosedByInterruptException;
import java.util.LinkedList;

import common.httpserver.HttpExchange;
import common.httpserver.HttpHandler;
import common.HttpUtil.HttpTransferInfo;


/*
 * Serves a File/Directory onto a context.
 */
public class HttpFileHandler implements HttpHandler {

	public static class NullHttpFileHandlerEvents extends HttpFileHandlerEvents {}
	
	public abstract static class HttpFileHandlerEvents {
		public void transferStarted(HttpTransferInfo info) {}
		public void transferEnded(HttpTransferInfo info) {}
		public void bytesTransferred(HttpTransferInfo info, long byteCount) {}
	}
	
	private File servePath;
	private LinkedList<HttpTransferInfo> transfers = new LinkedList<HttpTransferInfo>();
	private int currentUID = 0;		//synchronized by transfers... (the index for any given transfer)
	private HttpFileHandlerEvents events;
	private ProgressTracker parentTracker;
	public LinkedList<HttpTransferInfo> getTransferInfos() {
		return transfers;
	}
	
	public int getTransferCount() {
		return transfers.size();
	}
	
	/**
	 * Constructs a new HTTP file handler context.
	 * @param path The path of the directory or file to be exported.
	 * @param inEvents The events that will be used when interesting occurences happen.
	 * @param parentTracker ProgressTrackers will be used for all file transfers, this will be their parent
	 */
	public HttpFileHandler(File path, HttpFileHandlerEvents inEvents, ProgressTracker parentTracker) {
		servePath = path;
		events = inEvents;
		this.parentTracker = parentTracker;
	}
	
	public void handle(HttpExchange exchange) throws IOException, FileNotFoundException {
		HttpTransferInfo info = null;
		try {
			if (!servePath.exists()) {
				HttpUtil.simple404(exchange);
				throw new FileNotFoundException(servePath.getAbsolutePath()+ " doesn't exist");
			}
			if (servePath.isFile()) {
				synchronized (transfers) {
					info = new HttpTransferInfo(exchange, servePath, events, currentUID++, transfers, parentTracker.getNewChild());
				}
				HttpUtil.sendFileOnly(info);;
			} else if (servePath.isDirectory()) {
				//1) Determine what file the user requested:
				File requestedFile = new File(servePath.getAbsolutePath()+File.separator+HttpUtil.getPathAfterContext(exchange)).getCanonicalFile();
				//2) Check it exists, is not a directory,
				//     and is actually within the servePath.
				if (requestedFile.exists() && !requestedFile.isDirectory()
										   && Util.isWithin(requestedFile,servePath)) {
					synchronized (transfers) {
						info = new HttpTransferInfo(exchange, requestedFile, events, currentUID++, transfers, parentTracker.getNewChild());
					}
					HttpUtil.sendFileOnly(info);
				} else {
					HttpUtil.simple404(exchange);
				}
			} else {
				//Not directory or file:
				HttpUtil.simple404(exchange);
				
			}
		} catch (ClosedByInterruptException e) {
			Logger.warn("Terminated transfer of '"+info.getFile().getName()+"' due to interrupt.");
		} catch (SocketException e) {
			Logger.warn("Socket failed, probably remote or local disconnect: "+e);
		} catch (IOException e) {
			//Broken pipes are benign, this is just when the client disconnects ahead of schedule.
			//Just like "connection reset by peer" stuff. I don't know what the correct thing to do is.
			if (!e.getMessage().equals("Broken pipe") && !e.getMessage().equals("Connection reset by peer")) {
				Logger.warn("Probably benign: sending http file: "+e.toString());
			}
		} catch (Exception e) {
			 Logger.warn("HttpFileHandler: "+e.toString());
			 Logger.log(e);
		} finally {
			try {
				exchange.close();
			} catch (Exception e) {
				Logger.log(e);
			}
			synchronized (transfers) {
				if (transfers.contains(info)) {
					transfers.remove(info);
					info.transferComplete();
				}
			}
		}
	}

}
