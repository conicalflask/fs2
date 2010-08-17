package client.gui.settings;

import client.gui.MainFrame;

@SuppressWarnings("serial")
public class ShareSettings extends SettingsPanel {

	public ShareSettings(MainFrame frame) {
		super(frame, "Shares", frame.getGui().getUtil().getImage("shares"));
	}

}
