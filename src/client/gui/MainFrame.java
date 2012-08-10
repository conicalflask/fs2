package client.gui;

import java.awt.BorderLayout;
import java.awt.Rectangle;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.HashMap;
import java.util.TreeMap;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import common.Logger;

import client.Version;
import client.gui.TabItem.FS2Tab;
import client.gui.settings.SettingsPanel;
import client.gui.settings.SettingsTab;
import client.platform.ClientConfigDefaults.CK;

/**
 * The main window in FS2.
 * @author gary
 */
@SuppressWarnings("serial")
public class MainFrame extends JFrame implements ComponentListener, WindowListener {

	@Override
	public void componentHidden(ComponentEvent e) {}

	@Override
	public void componentMoved(ComponentEvent e) {
		gui.conf.putInt(CK.MAIN_WINDOW_LEFT,getX());
		gui.conf.putInt(CK.MAIN_WINDOW_TOP,getY());
		gui.conf.putInt(CK.MAIN_WINDOW_WIDTH,getWidth());
		gui.conf.putInt(CK.MAIN_WINDOW_HEIGHT,getHeight());
	}

	@Override
	public void componentResized(ComponentEvent e) {}

	@Override
	public void componentShown(ComponentEvent e) {}

	@Override
	public void windowActivated(WindowEvent e) {
	}

	@Override
	public void windowClosed(WindowEvent e) {}

	@Override
	public void windowClosing(WindowEvent e) {
		if (trayicon.isEnabled()) {
			gui.notify.trayIconReminder();
			setVisible(false);
		} else {
			gui.triggerShutdown();
		}
	}

	@Override
	public void windowDeactivated(WindowEvent e) {
	}

	@Override
	public void windowDeiconified(WindowEvent e) {}

	@Override
	public void windowIconified(WindowEvent e) {
		if (trayicon.isEnabled()) {
			gui.notify.trayIconReminder();
			setVisible(false);
		}
	}

	@Override
	public void windowOpened(WindowEvent e) {}
	
	final Gui gui;
	TrayIconManager trayicon;
	public TrayIconManager getTrayIcon() {
		return trayicon;
	}

	StatusBar status;
	JPanel content;
	JTabbedPane tabs;
	HashMap<FS2Tab, TabItem> openTabs = new HashMap<FS2Tab, TabItem>();
	HashMap<FS2Tab, TabItem> instantiatedTabs = new HashMap<FS2Tab, TabItem>();
	
	public HashMap<FS2Tab, TabItem> getInstantiatedTabs() {
		return instantiatedTabs;
	}
	
	public Gui getGui() {
		return gui;
	}
	
	public void openTab(FS2Tab tab) {
		if (openTabs.containsKey(tab)) {
			openTabs.get(tab).activateTab();
		} else if (instantiatedTabs.containsKey(tab)) {
			instantiatedTabs.get(tab).addToTabs();
		} else {
			switch (tab) {
			case FILES:
				new FilesTab(tabs, this);
				break;
			case CHAT:
				new ChatTab(tabs, this);
				break;
			case PEERS:
				new PeersTab(tabs, this);
				break;
			case DOWNLOADS:
				new DownloadsTab(tabs, this);
				break;
			case STATS:
				new StatsTab(tabs, this);
				break;
			case UPLOADS:
				new UploadsTab(tabs, this);
				break;
			case SETTINGS:
				new SettingsTab(tabs, this);
				break;
			default:
				break;
			}
		}
	}
	
	public MainFrame(Gui gui) {
		this.gui = gui;
		setTitle(Version.FS2_CLIENT_RELEASE+" (FS2: "+Version.FS2_CLIENT_VERSION()+")");
		setIconImage(gui.util.getImage("trayicon").getImage());
		setConfiguredBounds();
		addComponentListener(this); //Save the dimensions on resize;
		addWindowListener(this);
		
		setJMenuBar(new MenuBar(this));
		trayicon = new TrayIconManager(this);
		
		//Now deal with the content of the panel:
		content = new JPanel(new BorderLayout());
		setContentPane(content);
		status = new StatusBar(this);
		content.add(status, BorderLayout.SOUTH);
		tabs = new DnDTabbedPane();
		content.add(tabs,BorderLayout.CENTER);
		
		loadSavedTabs();
		
		//ensure the filestab and downloads tab are open correctly:
		if (!instantiatedTabs.containsKey(FS2Tab.DOWNLOADS)) openTab(FS2Tab.DOWNLOADS);
		if (!instantiatedTabs.containsKey(FS2Tab.FILES)) openTab(FS2Tab.FILES);
		

		//activate the correct tab:
		FS2Tab t;
		try {
			t = FS2Tab.valueOf(gui.conf.getString(CK.ACTIVE_TAB));
		} catch (IllegalArgumentException e) {
			Logger.warn(gui.conf.getString(CK.ACTIVE_TAB)+" isn't a valid tab.");
			t = FS2Tab.FILES;
		}
		openTab(t);
		
		setVisible(true);
	}
	
	void setConfiguredBounds() {
		setBounds(new Rectangle(gui.conf.getInt(CK.MAIN_WINDOW_LEFT),gui.conf.getInt(CK.MAIN_WINDOW_TOP),gui.conf.getInt(CK.MAIN_WINDOW_WIDTH),gui.conf.getInt(CK.MAIN_WINDOW_HEIGHT)));
	}
	
	/**
	 * Shuts down the gui. Only shuts swingy bits down if this is NOT a full jvm shutdown. (to avoid deadlock)
	 * @param endGame
	 */
	public void shutdown(boolean endGame) {
		if (!endGame) trayicon.shutdown();
		
		saveTabs();
		
		status.shutdown();
		
		//do this last:
		if (!endGame) {
			dispose();
		}
	}
	
	void loadSavedTabs() {
		TreeMap<Integer, FS2Tab> tabs = new TreeMap<Integer, FS2Tab>();
		try {
			//Build a map of tabs sorted by their indices:
			for (String key : gui.conf.getChildKeys(CK.OPEN_TABS)) {
				String[] splitKey = key.split("/");
				try {
					tabs.put(gui.conf.getInt(key), FS2Tab.valueOf(splitKey[splitKey.length-1]));
				} catch (IllegalArgumentException e) {
					Logger.warn(splitKey[splitKey.length-1]+" isn't a valid tab, ignoring.");
				}
			}
			//Now open them in order:
			for (FS2Tab t : tabs.values()) {
				openTab(t);
			}
			
		} catch (Exception e) {
			Logger.warn("Couldn't load tabs from configuration file: "+e);
			Logger.log(e);
		}
	}
	
	void saveTabs() {
		gui.conf.deleteKey(CK.OPEN_TABS);
		for (TabItem t : openTabs.values()) {
			gui.conf.putInt(CK.OPEN_TABS+"/"+t.getType().toString(), t.getIndex());
		}
		TabItem tab = ((TabItem)tabs.getSelectedComponent());
		if (tab!=null) gui.conf.putString(CK.ACTIVE_TAB, tab.getType().toString());
		
		//shutdown the tabs:
		for (FS2Tab t : FS2Tab.values()) {
			if (instantiatedTabs.containsKey(t)) instantiatedTabs.get(t).shutdown();
		}
	}
	
	@Override
	public void setVisible(boolean b) {
		super.setVisible(b);
		if (isVisible()) {
			setExtendedState((~JFrame.ICONIFIED)&getExtendedState()); //un-minimize :)
			//trick window managers into putting the unhidden fs2 in front.
			setAlwaysOnTop(true);
			setAlwaysOnTop(false);
		}
	}
	
	/**
	 * Sets a hint into the status bar for when a mouse-enter occurs.
	 * @param hint
	 */
	public void setStatusHint(String hint) {
		status.setInfoText(hint);
	}
	
	public static class StatusHint {
		ImageIcon icon;
		String text;
		
		public StatusHint(ImageIcon icon, String text) {
			this.icon = icon;
			this.text = text;
		}
	}
	
	/**
	 * with an icon too!
	 */
	public void setStatusHint(StatusHint hint) {
		status.setInfoTextWithIcon(hint.text, hint.icon);
	}

	/**
	 * Opens the settings tab to the page specified by the class supplied. ie: ShareSettings.class for the shares page.
	 * @param settingClass the class of the panel to show.
	 */
	public void openSettingsPage(Class<? extends SettingsPanel> settingClass) {
		openTab(FS2Tab.SETTINGS);
		((SettingsTab)instantiatedTabs.get(FS2Tab.SETTINGS)).showSetting(settingClass);
	}
}
