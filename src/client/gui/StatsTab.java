package client.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;

import client.indexnode.IndexNode;
import client.indexnode.IndexNodeCommunicator;
import client.indexnode.PeerStatsCollector;
import client.indexnode.IndexNodeStats.IndexNodeClient;
import client.platform.ClientConfigDefaults.CK;

import common.FS2Constants;
import common.Util;
import common.Util.NiceMagnitude;

@SuppressWarnings("serial")
public class StatsTab extends TabItem implements PropertyChangeListener, TableModelListener, ComponentListener, ListSelectionListener, ActionListener {
	
	public StatsTab(JTabbedPane pane, MainFrame frame) {
		super(pane, frame, "Stats", FS2Tab.STATS, frame.gui.util.getImage("stats"));
		
		this.setLayout(new BorderLayout());
		add(getSplitSection(), BorderLayout.CENTER);
	}
	
	JSplitPane splitPane;
	private JSplitPane getSplitSection() {
		splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
		
		splitPane.setTopComponent(getTopPanel());
		splitPane.setContinuousLayout(true);
		splitPane.setResizeWeight(0.5);
		
		splitPane.setBottomComponent(getTransferGraphPanel());
		
		splitPane.setDividerLocation(frame.gui.conf.getInt(CK.STATS_DIVIDER_LOCATION));
		splitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, this);
		
		return splitPane;
	}
	
	private JTable indexnodes;
	private TableModel statsTableModel;
	private PieChart pie;
	private JPanel tableAndInfo;
	private JPanel topPanel;
	private JLabel downInfo;
	private JLabel upInfo;
	private JLabel ratioInfo;
	public JPanel getTopPanel() {
		topPanel = new JPanel();
		topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.LINE_AXIS));

		statsTableModel = frame.gui.ssvr.getIndexNodeCommunicator().getStatsTableModel();
		statsTableModel.addTableModelListener(this);
		indexnodes = new FancierTable(statsTableModel, frame.gui.conf, CK.STATS_INDEXNODES_COLWIDTHS);
		indexnodes.getSelectionModel().addListSelectionListener(this);
		JScrollPane tablepane = new JScrollPane(indexnodes);
		
		tableAndInfo = new JPanel(new BorderLayout());
		topPanel.add(tableAndInfo);
		topPanel.addComponentListener(this);
		
		tableAndInfo.add(tablepane, BorderLayout.CENTER);
				
		JPanel info = new JPanel();
		info.setLayout(new BoxLayout(info, BoxLayout.PAGE_AXIS));
		info.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
		tableAndInfo.add(info, BorderLayout.SOUTH);
		
		downInfo = new JLabel(frame.gui.util.getImage("download"), SwingConstants.LEFT);
		upInfo = new JLabel(frame.gui.util.getImage("upload"), SwingConstants.LEFT);
		ratioInfo = new JLabel(frame.gui.util.getImage("stats"), SwingConstants.LEFT);
		
		info.add(upInfo);
		info.add(downInfo);
		info.add(ratioInfo);
		
		updateLabels();
		
		pie = new PieChart();
		topPanel.add(pie);
		updatePie();
		
		return topPanel;
	}
	
	private void updateLabels() {
		PeerStatsCollector ps = frame.gui.ssvr.getPeerstats();
		upInfo.setText("<html><b>upload...</b> All time: "+Util.niceSize(ps.getTotalUpBytes())+" (files: "+new NiceMagnitude(ps.getTotalUpFiles(),"")+")"+
					                    ", Session: "+Util.niceSize(ps.getUpBytesForSession())+
					                    ", All time avg. speed: "+Util.niceSize((long)ps.getAllTimeUpSpeed())+"/s"+
					   "</html>");
		downInfo.setText("<html><b>download...</b> All time: "+Util.niceSize(ps.getTotalDownBytes())+" (files: "+new NiceMagnitude(ps.getTotalDownFiles(),"")+")"+
                							  ", Session: "+Util.niceSize(ps.getDownBytesForSession())+
                                              ", All time avg. speed: "+Util.niceSize((long)ps.getAllTimeDownSpeed())+"/s"+
					     "</html>");
		float atr = ps.getAllTimeRatio();
		ratioInfo.setText("<html><b>ratio...</b> All time: "+String.format("%.2f", atr)+
											  ", Session: "+String.format("%.2f", ps.getSessionRatio())+
					        "</html>");
		if (atr>=2.0) {
			ratioInfo.setIcon(frame.gui.util.getImage("veryhappy"));
		} else if (atr>=1.0) {
			ratioInfo.setIcon(frame.gui.util.getImage("happy"));
		} else {
			ratioInfo.setIcon(frame.gui.util.getImage("unhappy"));
		}
	}
	
	void updatePie() {
		LinkedList<IndexNodeClient> clients = new LinkedList<IndexNodeClient>();
		IndexNodeCommunicator comm = frame.gui.ssvr.getIndexNodeCommunicator();
		if (indexnodes.getSelectedRowCount()>0) {
			for (int i : indexnodes.getSelectedRows()) {
				clients.addAll(comm.getNodeForRow(indexnodes.convertRowIndexToModel(i)).getStats().getPeers().values()); //woof!
			}
		} else {
			synchronized (comm.getRegisteredIndexNodes()) {
				for (IndexNode node : comm.getRegisteredIndexNodes()) {
					clients.addAll(node.getStats().getPeers().values());
				}
			}
		}
		Collections.sort(clients);
		Map<String, Double> newPieSlices = new LinkedHashMap<String, Double>();
		for (IndexNodeClient c : clients)  {
			newPieSlices.put(c.getAlias()+"("+Util.niceSize(c.getTotalShareSize(), true)+")", (double)c.getTotalShareSize());
		}
		pie.setWedges(newPieSlices);
	}
	
	Timer updateTimer;
	TimeGraph speedsGraph;
	public JPanel getTransferGraphPanel() {
		JPanel ret = new JPanel(new BorderLayout());
		
		updateTimer = new Timer(FS2Constants.CLIENT_STATUS_BAR_UPDATE_INTERVAL, this);
		speedsGraph = new TimeGraph(2);		
		ret.add(speedsGraph, BorderLayout.CENTER);
		
		updateTimer.start();
		
		return ret;
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if (evt.getSource()==splitPane) {
			frame.gui.conf.putInt(CK.STATS_DIVIDER_LOCATION, splitPane.getDividerLocation());
		}
	}

	@Override
	public void tableChanged(TableModelEvent e) {
		if (e.getType()==TableModelEvent.DELETE) {
			indexnodes.clearSelection();
		}
		if (indexnodes.getSelectedRowCount()==0) {
			updatePie();
			return;
		}
		for (int i : indexnodes.getSelectedRows()) {
			if (indexnodes.convertRowIndexToModel(i)==e.getFirstRow()) { //assuming only single-row changes are ever issued.
				updatePie();
			}
		}
	}

	@Override
	public void componentHidden(ComponentEvent e) {}

	@Override
	public void componentMoved(ComponentEvent e) {}

	@Override
	public void componentResized(ComponentEvent e) {
		if (e.getSource()==topPanel) {
			Dimension pSize = topPanel.getSize();
			tableAndInfo.setPreferredSize(new Dimension(pSize.width-pSize.height, pSize.height));
			pie.setPreferredSize(new Dimension(pSize.height, pSize.height)); //pie is in a square box ideally.
		}
		
	}

	@Override
	public void componentShown(ComponentEvent e) {}

	@Override
	public void valueChanged(ListSelectionEvent e) {
		updatePie();
	}

	public void shutdown() {
		updateTimer.stop();
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		if (e.getSource()==updateTimer) {
			speedsGraph.addPoint(new double[] {frame.gui.dc.getDownloadTracker().getSpeed(), frame.gui.ssvr.getUploadTracker().getSpeed()});
			updateLabels();
		}
	}
	
}
