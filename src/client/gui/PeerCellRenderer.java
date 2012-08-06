package client.gui;

import java.awt.Component;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * Renders a peer alias in a table cell, but with a little heart icon if they are a favourite.
 * @author gary
 *
 */
@SuppressWarnings("serial")
public class PeerCellRenderer extends DefaultTableCellRenderer {
	MainFrame frame;
	public PeerCellRenderer(MainFrame frame) {
		this.frame = frame;
	}
	
	@Override
	public Component getTableCellRendererComponent(JTable table, Object value,
			boolean isSelected, boolean hasFocus, int row, int column) {
		super.getTableCellRendererComponent(table, value, isSelected, hasFocus,
				row, column);
		
		if (frame.gui.ssvr.getPeerstats().isFavourite((String) value)) {
			setIcon(frame.gui.util.getImage("favourite"));
		} else {
			setIcon(null);
		}
			
		return this;
	}
	
}
