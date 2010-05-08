package client.gui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.HashMap;

import javax.swing.ImageIcon;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.UIManager;

import client.gui.MainFrame.StatusHint;
import client.gui.TabItem.FS2Tab;

@SuppressWarnings("serial")
public class MenuBar extends JMenuBar implements ActionListener, MouseListener {
	
	private class MenuItem {
		Runnable action;
		String description;
		ImageIcon icon;
		
		public MenuItem(Runnable action, String description, ImageIcon icon) {
			this.action = action;
			this.description = description;
			this.icon = icon;
		}
	}
	
	MainFrame frame;
	
	public MenuBar(MainFrame m) {
		this.frame = m;
		
		addFileMenu();
		addViewMenu();
		addConfigureMenu();
	}
	
	private void addConfigureMenu() {
		JMenu menu = new JMenu("Configure");
		this.add(menu);
		
		JMenu laf = new JMenu("Look and Feel");
		menu.add(laf);
		
		registerNewMenuItem(laf, "System default", null, "Sets FS2 to look how your system would prefer", new Runnable() {
			@Override
			public void run() {
				frame.gui.setLaF(UIManager.getSystemLookAndFeelClassName());
			}
		});
		
		registerNewMenuItem(laf, "Pretty cross platform", null, "Sets java to use a modern, not too shabby look and feel", new Runnable() {
			@Override
			public void run() {
				frame.gui.setLaF("com.sun.java.swing.plaf.nimbus.NimbusLookAndFeel");
			}
		});
		
		registerNewMenuItem(laf, "Cross platform", null, "Sets java to use it's delicious cross-platform look and feel", new Runnable() {
			@Override
			public void run() {
				frame.gui.setLaF(UIManager.getCrossPlatformLookAndFeelClassName());
			}
		});
		
		registerNewMenuItem(laf, "Motif?", null, "Sets the super secret look and feel...", new Runnable() {
			@Override
			public void run() {
				frame.gui.setLaF("com.sun.java.swing.plaf.motif.MotifLookAndFeel");
			}
		});
		
		menu.addSeparator();
		
		registerNewMenuItem(menu, "Shares", frame.gui.util.getImage("shares"), "See and change what you share", new Runnable() {
			@Override
			public void run() {
				frame.openTab(FS2Tab.SHARES);
			}
		});
		
		registerNewMenuItem(menu, "Indexnodes", frame.gui.util.getImage("autodetect"), "Change the indexnodes that FS2 tries to connect with", new Runnable() {
			@Override
			public void run() {
				frame.openTab(FS2Tab.INDEXNODES);;
			}
		});
	}
	
	private void addViewMenu() {
		JMenu menu = new JMenu("View");
		this.add(menu);
		
		registerNewMenuItem(menu, "Files", frame.gui.util.getImage("files"), "Lets you browse and search the FS2 network" , new Runnable() {
			@Override
			public void run() {
				frame.openTab(FS2Tab.FILES);
			}
		});
		
		menu.addSeparator();
		
		registerNewMenuItem(menu, "Downloads", frame.gui.util.getImage("download"), "Shows your queued and active downloads as a big tree", new Runnable() {
			@Override
			public void run() {
				frame.openTab(FS2Tab.DOWNLOADS);
			}
		});
		
		registerNewMenuItem(menu, "Uploads", frame.gui.util.getImage("upload"), "See active uploads", new Runnable() {
			@Override
			public void run() {
				frame.openTab(FS2Tab.UPLOADS);
			}
		});
		
		menu.addSeparator();
		
		registerNewMenuItem(menu, "Peers", frame.gui.util.getImage("peers"), "Shows peers you've exchanged data with in the past", new Runnable() {
			@Override
			public void run() {
				frame.openTab(FS2Tab.PEERS);
			}
		});
		
		registerNewMenuItem(menu, "Statistics", frame.gui.util.getImage("stats"), "Shows statistics about you and the indexnodes", new Runnable() {
			@Override
			public void run() {
				frame.openTab(FS2Tab.STATS);
			}
		});
		
		menu.addSeparator();
		
		registerNewMenuItem(menu, "Chat", frame.gui.util.getImage("chat"), "Opens the chat room", new Runnable() {
			@Override
			public void run() {
				frame.openTab(FS2Tab.CHAT);
			}
		});
	}

	private void addFileMenu() {
		JMenu menu = new JMenu("File");
		this.add(menu);
		
		registerNewMenuItem(menu, "Exit", frame.gui.util.getImage("quit"), "Exits FS2 immediately", new Runnable() {
			@Override
			public void run() {
				frame.gui.triggerShutdown();
			}
		});
	}

	HashMap<String, MenuItem> menuInfo = new HashMap<String, MenuItem>();
	void registerNewMenuItem(JMenu onMenu, String name, ImageIcon icon, String description, Runnable action) {
		JMenuItem item = new JMenuItem(name, icon);
		onMenu.add(item);
		item.addActionListener(this);
		item.addMouseListener(this);
		menuInfo.put(name, new MenuItem(action, description, icon));
	}
	
	@Override
	public void actionPerformed(ActionEvent e) {
		menuInfo.get(e.getActionCommand()).action.run();
	}

	@Override
	public void mouseClicked(MouseEvent e) {}

	@Override
	public void mouseEntered(MouseEvent e) {
		MenuItem item = menuInfo.get(((JMenuItem)e.getSource()).getText());
		frame.setStatusHint(new StatusHint(item.icon, item.description));
	}

	@Override
	public void mouseExited(MouseEvent e) {}

	@Override
	public void mousePressed(MouseEvent e) {}

	@Override
	public void mouseReleased(MouseEvent e) {}
}
