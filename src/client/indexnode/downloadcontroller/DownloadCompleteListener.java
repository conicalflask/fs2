package client.indexnode.downloadcontroller;

import client.indexnode.downloadcontroller.DownloadQueue.DownloadFile;

/**
 * Enables client classes to request notification from the download queue when a download has finished successfully.
 * 
 * @author gary
 *
 */
public interface DownloadCompleteListener {
	
	/**
	 * This event is triggered when a file has sucessfully finished downloading.
	 * @param file
	 */
	public void downloadComplete(DownloadFile file);
	
}
