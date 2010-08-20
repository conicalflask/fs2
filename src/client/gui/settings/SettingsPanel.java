package client.gui.settings;

import java.awt.BorderLayout;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.HashMap;

import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JPanel;

import client.gui.MainFrame;
import client.gui.MainFrame.StatusHint;

/**
 * Settings panels on the settings tab should extend this class.
 * 
 * Subclasses should use the 'inner' field to place components.
 * @author gp
 */
@SuppressWarnings("serial")
public abstract class SettingsPanel extends JPanel implements MouseListener {

	protected String settingName;
	protected ImageIcon icon;
	protected MainFrame frame;
	private HashMap<JComponent, StatusHint> hints = new HashMap<JComponent, StatusHint>();
	
	public SettingsPanel(MainFrame frame, String name, ImageIcon icon) {
		this.frame = frame;
		this.settingName = name;
		this.icon = icon;
		
		setLayout(new BorderLayout());
	}
	
	/**
	 * Registers a status bar hint for a component.
	 * @param comp
	 * @param hint
	 */
	protected void registerHint(JComponent comp, StatusHint hint) {
		comp.addMouseListener(this);
		hints.put(comp, hint);
	}
	
	@Override
	public void mouseEntered(MouseEvent e) {
		if (hints.containsKey(e.getSource())) {
			frame.setStatusHint(hints.get(e.getSource()));
		}
	}
	
	@Override
	public void mouseClicked(MouseEvent e) {}
	@Override
	public void mouseExited(MouseEvent e) {}
	@Override
	public void mousePressed(MouseEvent e) {}
	@Override
	public void mouseReleased(MouseEvent e) {}
	
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
