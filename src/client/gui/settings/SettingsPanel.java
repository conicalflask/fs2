package client.gui.settings;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.HashMap;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;

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
		setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
	}
	

	/**
	 * Returns a panel that will autoscroll but otherwise behave like a box-layouted panel. (it is added to the settingspanel already)
	 * @return
	 */
	protected JPanel createScrollableBoxlayout() {
		//setup a scrollable multipanel area:
		JPanel inner = new JPanel(new BorderLayout());
		JPanel boxes = new JPanel();
		boxes.setLayout(new BoxLayout(boxes, BoxLayout.PAGE_AXIS));
		inner.add(boxes, BorderLayout.NORTH);
		JScrollPane sp = new JScrollPane(inner);
		sp.setAutoscrolls(true);
		sp.setBorder(BorderFactory.createEmptyBorder());
		add(sp);
		return boxes;
	}
	
	/**
	 * Gets a 'control group' looking border for a jpanel.
	 * @param text
	 * @return
	 */
	protected Border getTitledBoldBorder(String text) {
		return BorderFactory.createTitledBorder(null, text, TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, getFont().deriveFont(Font.BOLD));
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
