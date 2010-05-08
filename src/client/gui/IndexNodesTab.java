package client.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;

import common.FS2Constants;
import common.Logger;
import common.Util;

import client.gui.MainFrame.StatusHint;
import client.indexnode.IndexNode;
import client.indexnode.IndexNodeCommunicator;
import client.platform.ClientConfigDefaults.CK;

/**
 * A configuration panel for indexnodes and simple networking parameters.
 * 
 * @author gary
 */
@SuppressWarnings("serial")
public class IndexNodesTab extends TabItem implements MouseListener, KeyListener, ActionListener, ListSelectionListener {

	public IndexNodesTab(JTabbedPane pane, MainFrame frame) {
		super(pane, frame, "Indexnodes", FS2Tab.INDEXNODES, frame.gui.util.getImage("autodetect"));
		
		setLayout(new BorderLayout());
		add(createAliasPanel(), BorderLayout.NORTH);
		add(createIndexNodesPanel(), BorderLayout.CENTER);
	}
	
	JTable nodesTable;
	JCheckBox autodetect;
	IndexNodeCommunicator comm = frame.gui.ssvr.getIndexNodeCommunicator();
	JButton addIndexnode, removeIndexnode, setPassword;
	JPanel createIndexNodesPanel() {
		JPanel panel = new JPanel(new BorderLayout());
		
		JPanel autoPanel = new JPanel(new BorderLayout());
		panel.add(autoPanel, BorderLayout.NORTH);
		
		autodetect = new JCheckBox();
		autodetect.addActionListener(this);
		autodetect.setSelected(comm.isListeningForAdverts());
		autoPanel.add(autodetect,BorderLayout.CENTER);
		registerHint(autodetect, new StatusHint(frame.gui.util.getImage("tick"), "(saved on change) Check this box to enable autodetection of indexnodes"));
		
		JLabel autolabel = new JLabel("Autodetect indexnodes: ", frame.gui.util.getImage("autodetect"), JLabel.LEFT);
		autoPanel.add(autolabel, BorderLayout.WEST);
		registerHint(autolabel, new StatusHint(frame.gui.util.getImage("tick"), "(saved on change) Check this box to enable autodetection of indexnodes"));
		
		JPanel buttonsPanel = new JPanel(new FlowLayout());
		panel.add(buttonsPanel, BorderLayout.SOUTH);
		addIndexnode = new JButton("Add indexnode...", frame.gui.util.getImage("add"));
		addIndexnode.addActionListener(this);
		registerHint(addIndexnode, new StatusHint(null, "Click here to add a new indexnode manually"));
		removeIndexnode = new JButton("Remove selected indexnode", frame.gui.util.getImage("delete"));
		removeIndexnode.addActionListener(this);
		registerHint(removeIndexnode,new StatusHint(null, "Click here to de-register the selected indexnode"));
		removeIndexnode.setEnabled(false);
		
		setPassword = new JButton("Provide password...", frame.gui.util.getImage("unlock"));
		setPassword.addActionListener(this);
		registerHint(setPassword, new StatusHint(null, "Click here to provide a password for a secure indexnode..."));
		setPassword.setEnabled(false);
		
		buttonsPanel.add(addIndexnode);
		buttonsPanel.add(removeIndexnode);
		buttonsPanel.add(setPassword);
		
		nodesTable = new FancierTable(comm, frame.gui.conf, CK.INDEXNODE_TABLE_COLWIDTHS);
		panel.add(new JScrollPane(nodesTable), BorderLayout.CENTER);
		for (int i=0; i<comm.getColumnCount();i++) {
			TableColumn col = nodesTable.getColumn(comm.getColumnName(i));
			if (i==0) col.setCellRenderer(new IndexNodeNameRenderer());
			if (i==1) col.setCellRenderer(new IndexNodeStatusRenderer());
			if (i==2) col.setCellRenderer(new IndexNodeDateRenderer());
		}
		registerHint(nodesTable, new StatusHint(frame.gui.util.getImage("tick"), "(saved on change) Tick the permanant box to save an autodetect node for the future"));
		nodesTable.getSelectionModel().addListSelectionListener(this);
		
		return panel;
	}

	private void registerHint(JComponent comp, StatusHint hint) {
		comp.addMouseListener(this);
		hints.put(comp, hint);
	}
	
	private class IndexNodeStatusRenderer extends DefaultTableCellRenderer {
		@Override
		public Component getTableCellRendererComponent(JTable table,
				Object value, boolean isSelected, boolean hasFocus, int row,
				int column) {
			super.getTableCellRendererComponent(table, value, isSelected, hasFocus,
					row, column);
			
			IndexNode node = comm.getRegisteredIndexNodes().get(nodesTable.convertRowIndexToModel(row));
			if (node.wasAdvertised()) setIcon(frame.gui.util.getImage("autodetect")); else setIcon(null);
			
			return this;
		}
	}
	
	private class IndexNodeDateRenderer extends DefaultTableCellRenderer {
		@Override
		public Component getTableCellRendererComponent(JTable table,
				Object value, boolean isSelected, boolean hasFocus, int row,
				int column) {
			super.getTableCellRendererComponent(table, value, isSelected, hasFocus,
					row, column);
			
			if (((Date)value).getTime()==0) setText("never"); 

			return this;
		}
	}
	
	private class IndexNodeNameRenderer extends DefaultTableCellRenderer {
		
		@Override
		public Component getTableCellRendererComponent(JTable table,
				Object value, boolean isSelected, boolean hasFocus, int row,
				int column) {
			super.getTableCellRendererComponent(table, value, isSelected, hasFocus,
					row, column);
			
			IndexNode node = comm.getRegisteredIndexNodes().get(nodesTable.convertRowIndexToModel(row));
			
			switch (node.getNodeStatus()) {
			case ACTIVE:
				if (node.isSecure()) {
					setIcon(frame.gui.util.getImage("secure"));
				} else {
					setIcon(frame.gui.util.getImage("connect"));
				}
				break;
			case AUTHREQUIRED:
				setIcon(frame.gui.util.getImage("secure"));
				break;
			case UNCONTACTABLE:
				setIcon(frame.gui.util.getImage("disconnect"));
				break;
			case INCOMPATIBLE:
				setIcon(frame.gui.util.getImage("error"));
				break;
			case FIREWALLED:
				setIcon(frame.gui.util.getImage("failure"));
				break;
			default:
				setIcon(frame.gui.util.getImage("disconnect"));
				break;
			}
						
			return this;
		}
		
	}
	
	HashMap<JComponent, StatusHint> hints = new HashMap<JComponent, StatusHint>();
	
	JTextField aliasText;
	JPanel createAliasPanel() {
		JPanel panel = new JPanel(new BorderLayout());
		JPanel panel0 = new JPanel(new BorderLayout());
		panel.add(panel0, BorderLayout.NORTH);
		
		JLabel aliasLabel = new JLabel("Alias: ");
		panel0.add(aliasLabel, BorderLayout.WEST);
		
		aliasText = new JTextField();
		panel0.add(aliasText, BorderLayout.CENTER);
		aliasText.setDocument(new JTextFieldLimit(32));
		aliasText.setText(frame.gui.ssvr.getAlias());
		aliasText.addKeyListener(this);
		
		registerHint(aliasText, new StatusHint(frame.gui.util.getImage("tick"), "(saved on change) Set your alias on the FS2 network here."));
		registerHint(aliasLabel, new StatusHint(frame.gui.util.getImage("tick"), "(saved on change) Set your alias on the FS2 network here."));
		
		return panel;
	}
	
	@Override
	public void mouseClicked(MouseEvent e) {}
	@Override
	public void mouseEntered(MouseEvent e) {
		if (hints.containsKey(e.getSource())) {
			frame.setStatusHint(hints.get(e.getSource()));
		}
	}
	
	@Override
	public void mouseExited(MouseEvent e) {}
	@Override
	public void mousePressed(MouseEvent e) {}
	@Override
	public void mouseReleased(MouseEvent e) {}

	@Override
	public void keyPressed(KeyEvent e) {}

	@Override
	public void keyReleased(KeyEvent e) {
		if (e.getSource()==aliasText) frame.gui.ssvr.setAlias(aliasText.getText());
	}

	@Override
	public void keyTyped(KeyEvent e) {}

	@Override
	public void actionPerformed(ActionEvent e) {
		if (e.getSource()==autodetect) {
			if (autodetect.isSelected()) {
				comm.enableAdvertAcceptance();
			} else {
				comm.disableAdvertAcceptance();
			}
		} else if (e.getSource()==addIndexnode) {
			String result = (String) JOptionPane.showInputDialog(null, "Enter the URL of the new indexnode:", "New Indexnode", JOptionPane.QUESTION_MESSAGE, null, null, "");
			if (result==null) return;
			try {
				final URL resURL = new URL(result);
				Thread elsewhere = new Thread(new Runnable() {
					@Override
					public void run() {
						comm.registerNewIndexNode(resURL);
					}
				});
				elsewhere.setDaemon(true);
				elsewhere.setName("Add new indexnode thread");
				elsewhere.start();
				frame.setStatusHint("Added: "+result+"... It might take a few seconds to show up...");
			} catch (MalformedURLException e1) {
				frame.setStatusHint(new StatusHint(frame.gui.util.getImage("error"), "Invalid new indexnode URL! ("+e1.getMessage()+")"));
				Logger.log("Invalid new indexnode url: "+e1);
			}
		} else if (e.getSource()==removeIndexnode) {
			int[] togo = nodesTable.getSelectedRows();
			LinkedList<IndexNode> goodbye = new LinkedList<IndexNode>();
			for (int i : togo) {
				goodbye.add(comm.getRegisteredIndexNodes().get(nodesTable.convertRowIndexToModel(i)));
			}
			for (IndexNode n : goodbye) comm.deregisterIndexNode(n);
		} else if (e.getSource()==setPassword) {
			JPasswordField password = new JPasswordField();
			if (JOptionPane.showConfirmDialog(null, new Object[]{new JLabel("<html><b>Enter this indexnode's password carefully.</b><br>The indexnode may create you an account if you do not already have one.<html>"), password}, "Password:", JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION) {
				comm.getRegisteredIndexNodes().get(nodesTable.convertRowIndexToModel(nodesTable.getSelectedRow())).setPassword(Util.md5(FS2Constants.FS2_USER_PASSWORD_SALT+new String(password.getPassword())));
				for (int i=0; i<password.getPassword().length; i++) password.getPassword()[i]=0; //null out password from memory.
			}
		}
	}

	@Override
	public void valueChanged(ListSelectionEvent e) {
		if (nodesTable.getSelectedRows().length==0) {
			removeIndexnode.setEnabled(false);
			setPassword.setEnabled(false);
		} else {
			removeIndexnode.setEnabled(true);
			setPassword.setEnabled(comm.getRegisteredIndexNodes().get(nodesTable.convertRowIndexToModel(nodesTable.getSelectedRow())).isSecure());
		}
	}
}
