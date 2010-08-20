package client.gui;

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import javax.swing.JTextField;

import common.Util;

/**
 * A text box that accepts values written in bytes.
 * 
 * If the contents fail validation then the box is painted red and the value becomes some negative number.
 * 
 * @author gp
 */
@SuppressWarnings("serial")
public class JBytesBox extends JTextField implements KeyListener {

	protected long currentValue;
	public static Color good = new Color(200, 255, 200);
	public static Color bad = new Color(255, 200, 100);
	
	/**
	 * Returns the value represented by this box in bytes. Will be negative if the value in the box is not valid.
	 */
	public long getValue() {
		return currentValue;
	}
	
	/**
	 * @param initialValue
	 * @param frame Used to set status hints on errors
	 */
	public JBytesBox(long initialValue) {
		currentValue = initialValue;
		setText(Util.niceSize(initialValue));
		addKeyListener(this);
		setBackground(good);
	}

	@Override
	public void keyPressed(KeyEvent e) {}

	@Override
	public void keyReleased(KeyEvent e) {
		long oldValue = currentValue;
		currentValue = Util.parseNiceSize(getText().trim());
		if (currentValue==-1) {
			setBackground(bad);
		} else {
			setBackground(good);
		}
		if (oldValue == -1) currentValue = -2; //ensure invalid changes always issue an event.
		firePropertyChange("value", oldValue, currentValue); //
	}

	@Override
	public void keyTyped(KeyEvent e) {}
	
}
