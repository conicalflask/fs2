package client.indexnode;

import java.util.ArrayList;
import java.util.HashSet;

import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;

import common.Util.FileSize;
import common.Util.NiceMagnitude;

/**
 * This class provides a table model that shows statistics for each connected indexnode.
 * It is guaranteed that this will use the same row indices as the IndexNodeCommunicator's table.
 * @author gp
 */
public class IndexNodeStatsTableModel implements TableModel, TableModelListener {

	IndexNodeCommunicator comm;
	
	public IndexNodeStatsTableModel(IndexNodeCommunicator comm) {
		this.comm = comm;
		this.comm.addTableModelListener(this);
	}
	
	HashSet<IndexNode> knownNodes = new HashSet<IndexNode>();
	
	/**
	 * We piggyback of the indexnode communicator tablemodel to get notified when events happen.
	 * 
	 * This method will always be called in the swing thread, by contract.
	 */
	@Override
	public void tableChanged(TableModelEvent ev) {
		if (ev.getType()==TableModelEvent.INSERT) { //adding a stats update listener only appropriate for the insert event
			final IndexNode n = comm.getNodeForRow(ev.getFirstRow());
			if (knownNodes.add(n)) {
				n.addStatsListener(new StatsListener() {
					/**
					 * When the indexnode has fresh stats, update that row of the table.
					 */
					@Override
					public void statsUpdated() {
						synchronized (listeners) {
							for (TableModelListener l : listeners) {
								int rowIdx = comm.getRowForNode(n); //have to look up the index every time... uh.
								l.tableChanged(new TableModelEvent(IndexNodeStatsTableModel.this, rowIdx, rowIdx, TableModelEvent.ALL_COLUMNS, TableModelEvent.UPDATE)); ///notify of changed row.
							}
						}
					}
				});
			}
		}
		//Pass on the event.
		synchronized (listeners) {
			for (TableModelListener l : listeners) {
				l.tableChanged(ev);
			}
		}
	}
	
	ArrayList<TableModelListener> listeners = new ArrayList<TableModelListener>();
	
	@Override
	public void addTableModelListener(TableModelListener l) {
		synchronized (listeners) {
			listeners.add(l);
		}
	}

	private Class<?>[] columnClasses = {String.class, FileSize.class, FileSize.class, NiceMagnitude.class, NiceMagnitude.class}; //Name, size, unique size, files, unique files
	private String[] columnNames = {"Indexnode", "Total Size", "Unique Size", "File count", "Unique files"};
	private static final int NAME_IDX=0;
	private static final int SIZE_IDX=1;
	private static final int UNIQUE_SIZE_IDX=2;
	private static final int COUNT_IDX=3;
	private static final int UNIQUE_COUNT_IDX=4;
	
	@Override
	public Class<?> getColumnClass(int colIdx) {
		return columnClasses[colIdx];
	}

	@Override
	public int getColumnCount() {
		return columnClasses.length;
	}

	@Override
	public String getColumnName(int col) {
		return columnNames[col];
	}

	@Override
	public int getRowCount() {
		return comm.getRowCount();
	}

	@Override
	public Object getValueAt(int row, int col) {
		IndexNode node = comm.getNodeForRow(row);
		switch (col) {
		case NAME_IDX:
			return node.getName();
		case SIZE_IDX:
			return new FileSize(node.getStats().getSize());
		case UNIQUE_SIZE_IDX:
			return new FileSize(node.getStats().getUniqueSize());
		case COUNT_IDX:
			return new NiceMagnitude((long)node.getStats().getIndexedFiles(),"");
		case UNIQUE_COUNT_IDX:
			return new NiceMagnitude((long)node.getStats().getUniqueFiles(),"");
		}
		return null;
	}

	@Override
	public boolean isCellEditable(int row, int col) {
		return false; //nothing user editable
	}

	@Override
	public void removeTableModelListener(TableModelListener l) {
		synchronized (listeners) {
			listeners.remove(l);
		}
	}

	@Override
	public void setValueAt(Object val, int row, int col) {
		//nothing to do, as it's a readonly table.
	}

	

}
