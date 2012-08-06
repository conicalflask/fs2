package client.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.LinkedList;

import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

import client.indexnode.PeerStatsCollector;
import client.platform.ClientConfigDefaults.CK;

@SuppressWarnings("serial")
public class PeersTab extends TabItem implements ActionListener {

	PeerStatsCollector ps;
	
	public PeersTab(JTabbedPane pane, MainFrame frame) {
		super(pane, frame, "Peers", FS2Tab.PEERS, frame.gui.util.getImage("peers"));
		ps = frame.gui.ssvr.getPeerstats();
		
		setLayout(new BorderLayout());
		add(createPeersTable(), BorderLayout.CENTER);
		add(getButtonsPanel(), BorderLayout.SOUTH);
	}
	
	JButton addPeer;
	JButton removePeers;
	JPanel getButtonsPanel() {
		JPanel ret = new JPanel(new FlowLayout());
		
		addPeer = new JButton("Add a peer...", frame.gui.util.getImage("favourite_add"));
		addPeer.addActionListener(this);
		ret.add(addPeer);
		removePeers = new JButton("Forget data for selected peers", frame.gui.util.getImage("delete"));
		removePeers.addActionListener(this);
		ret.add(removePeers);
		
		return ret;
	}
	
	JTable peers;
	JScrollPane createPeersTable() {
		peers = new FancierTable(ps, frame.gui.conf, CK.PEERS_TABLE_COLWIDTHS);
		peers.getColumn(ps.getColumnName(0)).setCellRenderer(new PeerNameRenderer());
		return new JScrollPane(peers);
	}
	
	private class PeerNameRenderer extends DefaultTableCellRenderer {
		@Override
		public Component getTableCellRendererComponent(JTable table,
				Object value, boolean isSelected, boolean hasFocus, int row,
				int column) {
			super.getTableCellRendererComponent(table, value, isSelected, hasFocus,
					row, column);
			if ((Boolean) ps.getValueAt(peers.convertRowIndexToModel(row), PeerStatsCollector.FAVOURITE_IDX)) {
				setIcon(frame.gui.util.getImage("favourite"));
			} else {
				setIcon(null);
			}
			return this;
		}
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		if (e.getSource()==addPeer) {
			String alias = (String) JOptionPane.showInputDialog(null, "What is their alias?", "New favourite peer alias...", JOptionPane.QUESTION_MESSAGE, null, null, "");
			if (alias!=null && !alias.equals("")) {
				ps.addPeer(alias);
			}
		} else if (e.getSource()==removePeers) {
			LinkedList<String> aliasesToGo = new LinkedList<String>();
			for (int i : peers.getSelectedRows()) {
				aliasesToGo.add((String) ps.getValueAt(peers.convertRowIndexToModel(i), PeerStatsCollector.ALIAS_IDX));
			}
			for (String alias : aliasesToGo) {
				ps.forgetPeer(alias);
			}
		}
	}

}
