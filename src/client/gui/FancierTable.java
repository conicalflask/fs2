package client.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JTable;
import javax.swing.JViewport;
import javax.swing.UIManager;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;

import common.Config;
import common.Logger;

/**
 * Makes JTables look nicer, and auto saves and loads column widths.
 * 
 * for alternating row colours, many thanks to:
 * http://elliotth.blogspot.com/2004/09/alternating-row-colors-in-jtable.html
 * Also, enhanced using their later ETable, although without some silly extra stuff like lines :D
 * @author gary
 *
 */
@SuppressWarnings("serial")
public class FancierTable extends JTable implements PropertyChangeListener {

	Config conf;
	String colKey;
	
	/**
	 * Constructs a new fancier table.
	 * @param tm
	 * @param conf The configuration object to load and save column widths to.
	 * @param colKey
	 */
	public FancierTable(TableModel tm, Config conf, String colKey) {
		super(tm);
		this.conf = conf;
		this.colKey = colKey;
		
		setAutoCreateRowSorter(true);
		setShowGrid(false);
		
		for (int i=0; i<tm.getColumnCount();i++) {
			TableColumn col = getColumn(tm.getColumnName(i));
			int preferredWidth = conf.getInt(colKey+i);
			if (preferredWidth>0) col.setPreferredWidth(preferredWidth);
			getColumn(tm.getColumnName(i)).addPropertyChangeListener(this);
		}
	}
	
	protected void paintEmptyRows(Graphics g) {
        final int rowCount = getRowCount();
        final Rectangle clip = g.getClipBounds();
        final int height = clip.y + clip.height;
        if (rowCount * rowHeight < height) {
            for (int i = rowCount; i <= height/rowHeight; ++i) {
                g.setColor(colorForRow(i));
                g.fillRect(clip.x, i * rowHeight, clip.width, rowHeight);
            }
        }
    }
	
	
	@Override
	public void paint(Graphics g) {
		try {
			super.paint(g);
		} catch (IndexOutOfBoundsException e) {
			Logger.log("Repainting fancier table: "+e);
		}
		paintEmptyRows(g);

	}
	
	private static final Color ALT_ROW_COLOUR = new Color(0xecf3ff);
	
    protected Color colorForRow(int row) {
        return (row % 2 == 1) ? ALT_ROW_COLOUR : getBackground();
    }
	
    /**
     * Changes the behavior of a table in a JScrollPane to be more like
     * the behavior of JList, which expands to fill the available space.
     * JTable normally restricts its size to just what's needed by its
     * model.
     */
    @Override
    public boolean getScrollableTracksViewportHeight() {
        if (getParent() instanceof JViewport) {
            JViewport parent = (JViewport) getParent();
            return (parent.getHeight() > getPreferredSize().height);
        }
        return false;
    }
    
    public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
        Component c = super.prepareRenderer(renderer, row, column);
        if (isCellSelected(row, column) == false) {
            c.setBackground(colorForRow(row));
            c.setForeground(UIManager.getColor("Table.foreground"));
        } else {
            c.setBackground(UIManager.getColor("Table.selectionBackground"));
            c.setForeground(UIManager.getColor("Table.selectionForeground"));
        }
        return c;
    }

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if (evt.getSource() instanceof TableColumn) {
			TableColumn col = (TableColumn) evt.getSource();
			conf.putInt(colKey+col.getModelIndex(),col.getPreferredWidth());
		}
	}
}
