package client.gui;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.plaf.basic.BasicButtonUI;

import client.platform.ClientConfigDefaults.CK;

/**
 * A helper used in FS2 to create tabs with close buttons and changeable labels.
 * 
 * The constructor for this class MUST be called!
 * 
 * @author gary
 */
@SuppressWarnings("serial")
public abstract class TabItem extends JPanel {
	
	public enum FS2Tab {FILES, INDEXNODES, CHAT, SHARES, PEERS, DOWNLOADS, STATS, UPLOADS, SETTINGS}
	
	static Icon closeInactive;
	static Icon closeActive;
	
	final JTabbedPane pane;
	private TabLabelComponent label;
	final protected MainFrame frame;
	final protected FS2Tab type;
	
	public int getIndex() {
		return pane.indexOfTabComponent(label);
	}
	
	public void activateTab() {
		pane.setSelectedIndex(getIndex());
	}
	
	public boolean isActiveTab() {
		return pane.getSelectedIndex()==getIndex();
	}
	
	public FS2Tab getType() {
		return type;
	}
	
	public TabItem(JTabbedPane pane, MainFrame frame, String initialCaption, FS2Tab type, ImageIcon icon) {
		this.pane = pane;
		this.frame = frame;
		this.type = type;
		
		frame.instantiatedTabs.put(type, this);
		
		//Initialises static icons if they havent been already:
		if (closeInactive==null) {
			ImageIcon cd = frame.gui.util.getImage("close-disable");
			ImageIcon ca = frame.gui.util.getImage("close");
			if (cd!=null) {
				closeInactive = cd;
			}
			if (cd!=null) {
				closeActive = ca;
			}
		}
		
		label = new TabLabelComponent(initialCaption, icon);
		
		addToTabs();
	}

	public void addToTabs() {
		frame.openTabs.put(type, this);
		pane.addTab(null, this);
		pane.setTabComponentAt(pane.getTabCount()-1, label);
		activateTab();
	}
	
	private class TabLabelComponent extends JPanel implements ActionListener, MouseListener{
		
		JLabel caption;
		JButton closer;
		
		public TabLabelComponent(String initialCaption, ImageIcon icon) {
			setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
			setOpaque(false);
			
			closer = new JButton(closeInactive);
			caption = new JLabel(initialCaption, icon, JLabel.LEFT);
			caption.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 6));
			
			closer.setUI(new BasicButtonUI());
			closer.setPreferredSize(new Dimension(closeInactive.getIconWidth()+2, closeInactive.getIconHeight()+2));
			closer.setBorder(BorderFactory.createEmptyBorder());
			
			add(caption);
			add(closer);
			
			closer.addActionListener(this);
			closer.addMouseListener(this);
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			if (e.getSource()==closer) {
				int tabIndex = pane.indexOfTabComponent(this);
				if (tabIndex!=-1) {
					pane.removeTabAt(tabIndex);
					frame.gui.conf.deleteKey(CK.OPEN_TABS+"/"+type.toString());
					frame.openTabs.remove(type);
				}
			}
		}

		@Override
		public void mouseClicked(MouseEvent e) {}

		@Override
		public void mouseEntered(MouseEvent e) {
			closer.setIcon(closeActive);
			frame.setStatusHint("Click here to close the '"+caption.getText()+"' tab");
		}

		@Override
		public void mouseExited(MouseEvent e) {
			closer.setIcon(closeInactive);
		}

		@Override
		public void mousePressed(MouseEvent e) {}

		@Override
		public void mouseReleased(MouseEvent e) {}
	}
	
	/**
	 * Set the label for this tab.
	 * @param title
	 */
	public void setLabel(String title) {
		this.label.caption.setText(title);
	}
	
	/**
	 * When this is set to true this tab is closable (with a little 'x') otherwise it is unclosable.
	 * @param closable
	 */
	public void setClosable(boolean closable) {
		label.closer.setVisible(closable);
		if (closable) {
			label.caption.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 6));
		} else {
			label.caption.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
		}
	}
	
	void shutdown() {
		//A subclass should override this if they need to stop a timer, close a socket etc.
		//This is closed at application shutdown/update NOT when the tab is closed.
	}
}
