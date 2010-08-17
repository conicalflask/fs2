package client.gui.settings;

import client.gui.MainFrame;

@SuppressWarnings("serial")
public class AdvancedSettings extends SettingsPanel {

	public AdvancedSettings(MainFrame frame) {
		super(frame, "Advanced", frame.getGui().getUtil().getImage("advanced"));
	}

}
