package client.gui;

import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.TrayIcon.MessageType;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import client.platform.Platform;

import common.Logger;

public class TrayIconManager implements MouseListener, ActionListener {

	boolean enabled = false;
	TrayIcon icon;
	MainFrame frame;
	MenuItem stats;
	private MenuItem exiter;
	
	public TrayIconManager(MainFrame frame) {
		this.frame = frame;
		if (SystemTray.isSupported()) {
			try {
				icon = new TrayIcon(frame.gui.util.getImage("trayicon").getImage(),frame.getTitle());
				icon.setImageAutoSize(true);
				icon.addMouseListener(this);
				
				PopupMenu popup = new PopupMenu();
				
				popup.add(stats = new MenuItem(""));
				stats.addActionListener(this);
				popup.addSeparator();
				
				exiter = new MenuItem("Exit");
				exiter.addActionListener(this);
				
				popup.add(exiter);
				
				icon.setPopupMenu(popup);
				icon.addActionListener(this);
				
				SystemTray.getSystemTray().add(icon);
				
				enabled = true;
			} catch (Exception e) {
				Logger.warn("Unable to enable the FS2 tray icon: "+e);
			}
		}
	}
	
	/**
	 * Determines if FS2's tray icon is functional.
	 * @return
	 */
	public boolean isEnabled() {
		return enabled || Platform.OS_X_DOCK_ACTIVE;
	}
	
	/**
	 * Call this to destroy the tray icon.
	 */
	public void shutdown() {
		if (enabled) {
			SystemTray.getSystemTray().remove(icon);
			enabled = false;
		}
	}
	
	public void setPopupInfoString(String info) {
		if (stats!=null) stats.setLabel(info);
	}
	
	
	private Object messageListenerMutex = new Object();
	private ActionListener messageListener;
	/**
	 * Pops up a message from the tray icon. If the OS supports it the clickedAction will be triggered if the message is clicked.
	 * @param caption
	 * @param message
	 * @param type
	 * @param clickedAction
	 */
	public void popupMessage(String caption, String message, MessageType type, ActionListener clickedAction) {
		synchronized (messageListenerMutex) {
			messageListener = clickedAction;
			icon.displayMessage(caption, message, type);
		}
	}
	
	@Override
	public void mouseClicked(MouseEvent e) {}

	@Override
	public void mouseEntered(MouseEvent e) {}

	@Override
	public void mouseExited(MouseEvent e) {}

	@Override
	public void mousePressed(MouseEvent e) {}

	@Override
	public void mouseReleased(MouseEvent e) {
		if (!e.isPopupTrigger()) frame.gui.showFS2();
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		if (e.getSource()==exiter) {
			frame.gui.triggerShutdown();
		} else if (e.getSource()==icon) {
			synchronized (messageListenerMutex) {
				if (messageListener!=null) messageListener.actionPerformed(e);
			}
		} else if (e.getSource()==stats) {
			frame.gui.showFS2();
		}
	}

}
