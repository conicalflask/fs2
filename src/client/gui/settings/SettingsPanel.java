package client.gui.settings;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import client.gui.MainFrame;

/**
 * Settings panels on the settings tab should extend this class.
 * @author gp
 *
 */
@SuppressWarnings("serial")
public abstract class SettingsPanel extends JPanel {

	protected String settingName;
	protected ImageIcon icon;
	protected MainFrame frame;
	
	public SettingsPanel(MainFrame frame, String name, ImageIcon icon) {
		this.frame = frame;
		this.settingName = name;
		this.icon = icon;
		
		add(new JLabel(name,icon,SwingConstants.LEFT));
	}
	
	/**
	 * This is the name that will be displayed in the settings categories box on the left of the settings tab
	 * @return
	 */
	public String getSettingName() {
		return settingName;
	}
	
	/**
	 * Supplies an icon to represent this settings category.
	 * @return
	 */
	public ImageIcon getIcon() {
		return icon;
	}
	
}
