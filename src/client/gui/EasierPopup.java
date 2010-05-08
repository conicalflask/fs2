package client.gui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import client.gui.MainFrame.StatusHint;

/**
 * The anscestor of all popup menus in FS2,
 * 
 * This provides convinience methods for maintaining(modifying) popup menu items, popping up in a cross-laf way
 * 
 */
@SuppressWarnings("serial")
public abstract class EasierPopup extends JPopupMenu implements MouseListener, ActionListener {

	MainFrame frame;

	/**
	 * The interface to support actions on selecting menu items.
	 * @author gary
	 */
	public interface PopupAction {
		public void doAction(ActionEvent e);
	}
	
	public EasierPopup(MainFrame frame) {
		this.frame = frame;
	}
	
	protected class EPopup extends JMenuItem {
		PopupAction action;
		String description;
		
		public EPopup(String caption, PopupAction action, ImageIcon icon, String description) {
			setText(caption);
			this.action = action;
			setIcon(icon);
			addMouseListener(EasierPopup.this);
			addActionListener(EasierPopup.this);
			this.description = description;
			EasierPopup.this.add(this);
		}
	}
	
	@Override
	public void mouseClicked(MouseEvent e) {}
	
	@Override
	public void mouseEntered(MouseEvent e) {
		if (e.getSource() instanceof EPopup) {
			EPopup i = (EPopup) e.getSource();
			frame.setStatusHint(new StatusHint((ImageIcon)i.getIcon(), i.description));
		}
	}
	
	@Override
	public void mouseExited(MouseEvent e) {}
	
	@Override
	public void mousePressed(MouseEvent e) {
		considerPopup(e);
	}
	
	@Override
	public void mouseReleased(MouseEvent e) {
		considerPopup(e);
	}
	
	protected void considerPopup(MouseEvent e) {
		if (e.isPopupTrigger()) {
			if (popupDecision(e)) {
				this.show(e.getSource() instanceof JComponent ? (JComponent)e.getSource() : null, e.getX(), e.getY());
			}
		}
	}
	
	/**
	 * This may be overridden by subclasses to decide if a popup is appropriate, and optionally modify the items in the popup menu.
	 * @return
	 */
	protected boolean popupDecision(MouseEvent e) {
		return true;
	}
	
	@Override
	public void actionPerformed(ActionEvent e) {
		if (e.getSource() instanceof EPopup) {
			if (((EPopup)e.getSource()).action!=null) ((EPopup)e.getSource()).action.doAction(e);
		}
	}
	
}
