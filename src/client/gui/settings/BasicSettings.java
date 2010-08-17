package client.gui.settings;

import client.gui.MainFrame;

@SuppressWarnings("serial")
public class BasicSettings extends SettingsPanel {

	public BasicSettings(MainFrame frame) {
		super(frame, "Basic", frame.getGui().getUtil().getImage("basic"));
		
		
		//Construct a basic settings page including: alias, avatar, etc.
	}


}
