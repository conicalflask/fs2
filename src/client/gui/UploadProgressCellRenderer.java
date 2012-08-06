package client.gui;

import java.awt.Component;
import java.awt.Rectangle;

import javax.swing.JProgressBar;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;

/**
 * Efficiently renders a system default looking progress bar into a table cell.
 * This expects that the value for the cell is a float expressing the percent completion.
 * @author gary
 *
 */
@SuppressWarnings("serial")
public class UploadProgressCellRenderer extends JProgressBar implements
		TableCellRenderer {

	public UploadProgressCellRenderer() {
		setMaximum(10000);
	}
	
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
		setValue((int)(((Float)value)*100));
		return this;
	}

}
