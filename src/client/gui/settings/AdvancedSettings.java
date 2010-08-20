package client.gui.settings;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;

import common.Logger;
import common.Util;

import client.gui.JBytesBox;
import client.gui.MainFrame;
import client.gui.MainFrame.StatusHint;
import client.platform.Relauncher;
import client.platform.ClientConfigDefaults.CK;

@SuppressWarnings("serial")
public class AdvancedSettings extends SettingsPanel {

	public AdvancedSettings(MainFrame frame) {
		super(frame, "Advanced", frame.getGui().getUtil().getImage("advanced"));
		
		JPanel inner = new JPanel(new BorderLayout());
		
		//Construct a basic settings page including: alias, avatar, etc.
		JPanel boxes = new JPanel();
		boxes.setLayout(new BoxLayout(boxes, BoxLayout.PAGE_AXIS));
		
		//###### actual items go here:
		JLabel warning = new JLabel("<html>\"<b>You're probably going to break anything you change here</b>\"<br><i>--Captain Obvious</i></html>", frame.getGui().getUtil().getImage("failure"), SwingConstants.LEFT);
		warning.setAlignmentX(CENTER_ALIGNMENT);
		boxes.add(warning);
		boxes.add(new JSeparator());
		boxes.add(heapSizePanel());
		boxes.add(new JSeparator());
		boxes.add(resetToDefaultsPanel());
		//###### no more items.
		
		inner.add(boxes, BorderLayout.NORTH);
		
		JScrollPane sp = new JScrollPane(inner);
		sp.setAutoscrolls(true);
		sp.setBorder(BorderFactory.createEmptyBorder());
		add(sp);
	}

	private JPanel heapSizePanel() {
		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.X_AXIS));
		
		//Runtime.getRuntime().maxMemory()<(conf.getLong(CK.HEAPSIZE)*0.9
		final JBytesBox heapsize = new JBytesBox(frame.getGui().getConf().getLong(CK.HEAPSIZE));
		
		heapsize.addPropertyChangeListener("value", new PropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				long nv = (Long) evt.getNewValue();
				if (nv==-1) {
					frame.setStatusHint(new StatusHint(SettingsTab.error, "FS2's heap size can't be set to '"+heapsize.getText()+"'."));
				} else {
					if (nv > 32*1024*1024) {
						frame.getGui().getConf().putLong(CK.HEAPSIZE, nv);
						frame.setStatusHint(new StatusHint(SettingsTab.tick, "FS2's heap set to "+Util.niceSize(nv)+", click 'Restart FS2' to apply changes"));
						frame.getGui().getConf().putBoolean(CK.AUTO_HEAP_KNOWLEDGE, true); //suppress the "i've changed your heap for you" message.
						restartNeeded();
					} else {
						frame.setStatusHint(new StatusHint(SettingsTab.error, "The heap must be at least 32MiB."));
					}
				}
			}
		});
		
		content.add(new JLabel("<html>Active JVM maximum heap size: <b>"+Util.niceSize(Runtime.getRuntime().maxMemory())+"</b><br>" +
							         "Current heap usage: <b>"+Util.niceSize(Runtime.getRuntime().totalMemory())+"</b><br>" +
							         "Configured maximum heap size:"));
		content.add(heapsize);
		
		registerHint(heapsize, new StatusHint(SettingsTab.tick, "Set this to several GiB to host a large indexnode."));
		
		return content;
	}
	
	private JPanel portPanel() {
		return null;
	}
	
	/**
	 * configures autoupdate settings, triggers a check for updates right now.
	 * @return
	 */
	private JPanel autoupdatePanel() {
		return null;
	}
	
	
	
	private void restartNeeded() {
		restartFS2.setText(restartFS2.getText().toUpperCase());
		restartFS2.setBackground(JBytesBox.bad);
	}
	
	JButton resetFS2;
	JButton restartFS2; //clicking this restarts the FS2 client.
	
	/**
	 * A single button that nukes FS2's configuration, and a button to relaunch FS2.
	 * @return
	 */
	private JPanel resetToDefaultsPanel() {
		JPanel buttonsPanel = new JPanel(new FlowLayout());
		
		restartFS2 = new JButton("Restart FS2", frame.getGui().getUtil().getImage("refresh"));
		restartFS2.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (!Relauncher.increaseHeap(frame.getGui().getConf().getLong(CK.HEAPSIZE), false)) { //this is done by restarting to a new heap of possibly the same size.
					JOptionPane.showMessageDialog(null, "The client couldn't be restarted. Restart is only supported from .jar files.", "Restart failure.", JOptionPane.ERROR_MESSAGE);

				}
			}
		});
		registerHint(restartFS2, new StatusHint(frame.getGui().getUtil().getImage("refresh"), "This restarts FS2. Use it to apply some settings or to fix weird behaviour."));
		
		resetFS2 = new JButton("Reset configuration to defaults", frame.getGui().getUtil().getImage("failure"));
		resetFS2.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (JOptionPane.showOptionDialog(frame, new JLabel("<html>If you continue FS2 will shutdown and erase its configuration.<br><b>You will have to manually restart FS2.</b></html>", frame.getGui().getUtil().getImage("failure"), SwingConstants.LEFT), "Really?", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, new String[] {"Erase configuration", "cancel"}, "Erase configuration") == JOptionPane.YES_OPTION) {
					Logger.severe("Erasing FS2's configuration...");
					frame.getGui().getConf().eraseOnShutdown();
					frame.getGui().triggerShutdown();
				}
			}
		});
		registerHint(resetFS2, new StatusHint(frame.getGui().getUtil().getImage("failure"), "This resets all changes to FS2's default configuration. USE WITH CARE."));
		
		buttonsPanel.add(restartFS2);
		buttonsPanel.add(resetFS2);
		
		return buttonsPanel;
	}
	
}
