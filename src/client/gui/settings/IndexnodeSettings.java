package client.gui.settings;

import client.gui.MainFrame;

@SuppressWarnings("serial")
public class IndexnodeSettings extends SettingsPanel {

	public IndexnodeSettings(MainFrame frame) {
		super(frame, "Indexnodes", frame.getGui().getUtil().getImage("autodetect"));
	}

}
