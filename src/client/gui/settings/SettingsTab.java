package client.gui.settings;

import javax.swing.JTabbedPane;

import client.gui.MainFrame;
import client.gui.TabItem;

@SuppressWarnings("serial")
public class SettingsTab extends TabItem {

	public SettingsTab(JTabbedPane pane, MainFrame frame) {
		super(pane, frame, "Settings", FS2Tab.SETTINGS, frame.getGui().getUtil().getImage("settings"));
		// TODO Auto-generated constructor stub
	}

}
