package client.gui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;

import javax.swing.JPanel;

import client.indexnode.downloadcontroller.DownloadChunk;
import client.indexnode.downloadcontroller.DownloadInfo;

/**
 * Draws a progress bar with multiple internal sections, intended to render a multipart download's progress
 * @author gary
 */
@SuppressWarnings("serial")
public class DownloadInfoProgressRenderer extends JPanel {

	DownloadInfo dlinfo;
	DownloadChunk activeChunk;
	
	public void setDownloadInfo(DownloadInfo dlinfo) {
		this.dlinfo = dlinfo;
	}
	
	/**
	 * Set the download chunk that will be highlighted in this progress bar.
	 * 
	 * This also sets the download info of this progress bar to match the owner of the chunk specified.
	 * @param activeChunk
	 */
	public void setActiveChunk(DownloadChunk activeChunk) {
		this.activeChunk = activeChunk;
		this.setDownloadInfo(activeChunk.getOwner());
	}
	
	static Color PROGRESS_BACKGROUND = Color.LIGHT_GRAY;
	static Color PROGRESS_BORDER 	 = Color.DARK_GRAY;
	static Color PROGRESS_RXD		 = Color.BLUE.darker();
	static Color PROGRESS_ACTIVE	 = Color.RED;
	
	@Override
	public void paint(Graphics g) {
		Rectangle clip = g.getClipBounds();
		//1) draw bg:
		g.setColor(PROGRESS_BACKGROUND);
		g.fillRect(clip.x, clip.y, clip.width, clip.height);
		
		//now draw chunks......
		float fsize = dlinfo.getFile().getSize();
		for (DownloadChunk c : dlinfo.getChunks()) {
			int sX = Math.round((c.getStartByte()/fsize)*clip.width);
			int eX = Math.round((c.getDownloadedBytes()/fsize)*clip.width);
			if (c==activeChunk) {
				g.setColor(PROGRESS_ACTIVE);
			} else {
				g.setColor(PROGRESS_RXD);
			}
			g.fillRect(sX, clip.y, eX+1, clip.height);
		}
		
		//finally) draw border:
		g.setColor(PROGRESS_BORDER);
		g.drawRect(clip.x, clip.y, clip.width-1, clip.height-1);
	}
	
}
