package client.gui;

import java.awt.Component;
import java.awt.Rectangle;

import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;

import client.indexnode.downloadcontroller.DownloadChunk;

/**
 * Implements a rubber-stampable table-cell rendering version of the DownloadInfoProgress
 * 
 * It assumes the value for the cell it is rendering is a DownloadChunk object.
 * 
 * @author gary
 *
 */
@SuppressWarnings("serial")
public class CellProgressBar extends DownloadInfoProgressRenderer implements TableCellRenderer {
	@Override
	public void revalidate() {
		//nop
	}
	
	@Override
	public void repaint() {
		//nop
	}
	
	@Override
	public void repaint(int x, int y, int width, int height) {
		//nop
	}
	
	@Override
	public void repaint(long tm) {
		//nop
	}
	
	@Override
	public void repaint(long tm, int x, int y, int width, int height) {
		//nop
	}
	
	@Override
	public void repaint(Rectangle r) {
		//nop
	}
	
	@Override
	public void invalidate() {
		//nop
	}
	
	@Override
	public void validate() {
		//nop
	}

	@Override
	public boolean isOpaque() {
		return true;
	}
	
	@Override
	public Component getTableCellRendererComponent(JTable table, Object value,
			boolean isSelected, boolean hasFocus, int row, int column) {
		this.setActiveChunk((DownloadChunk) value);
		return this;
	}
}
