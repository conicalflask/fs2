package client.gui.chat;

import javax.swing.table.AbstractTableModel;

import client.indexnode.IndexNode;
import client.indexnode.StatsListener;
import client.indexnode.IndexNodeStats.IndexNodeClient;


@SuppressWarnings("serial")
public class AvatarTableModel extends AbstractTableModel implements StatsListener{

	private Object[] peers;
	
	IndexNode indexNode;
	
	public AvatarTableModel(IndexNode indexNode){
		this.indexNode = indexNode;
		
		peers = indexNode.getStats().getPeers().values().toArray();
		
		indexNode.addStatsListener(this);
	}
	
	@Override
	public int getColumnCount() {
		return 1;
	}

	@Override
	public int getRowCount() {
		return peers.length;
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		return (IndexNodeClient)peers[rowIndex];
	}
	
	@Override
	public boolean isCellEditable(int row, int col){
		return false;
	}

	@Override
	public void statsUpdated() {
		peers = indexNode.getStats().getPeers().values().toArray();
		fireTableDataChanged();
	}
}