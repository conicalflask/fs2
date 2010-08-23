package client.gui.settings;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.JSpinner.DefaultEditor;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import common.FS2Constants;
import common.Logger;
import common.Util;

import client.ClientExecutor;
import client.gui.JBytesBox;
import client.gui.MainFrame;
import client.gui.MainFrame.StatusHint;
import client.indexnode.internal.InternalIndexnodeManager;
import client.platform.Relauncher;
import client.platform.ClientConfigDefaults.CK;

@SuppressWarnings("serial")
public class AdvancedSettings extends SettingsPanel {

	InternalIndexnodeManager iim = frame.getGui().getShareServer().getIndexNodeCommunicator().getInternalIndexNode();
	
	public AdvancedSettings(MainFrame frame) {
		super(frame, "Advanced", frame.getGui().getUtil().getImage("advanced"));
				
		JPanel boxes = createScrollableBoxlayout();
		
		//###### actual items go here:
		JLabel warning = new JLabel("<html>\"<b>You're probably going to break anything you change here</b>\"<br><i>--Captain Obvious</i></html>", frame.getGui().getUtil().getImage("failure"), SwingConstants.LEFT);
		warning.setAlignmentX(CENTER_ALIGNMENT);
		boxes.add(warning);
		boxes.add(createSlotsPanel());
		boxes.add(autoupdatePanel());
		boxes.add(heapSizePanel());
		boxes.add(autoindexnodePanel());
		boxes.add(portPanel());
		boxes.add(resetToDefaultsPanel());
		//###### no more items.
		
	}

	JLabel autoindexInfo;
	
	private JPanel autoindexnodePanel() {
		JPanel ret = new JPanel(new BorderLayout());
		ret.setBorder(getTitledBoldBorder("Internal indexnode"));
		
		autoindexInfo = new JLabel();
		updateAutoIndexnodeInfo();
		
		ret.add(autoindexInfo, BorderLayout.NORTH);
		
		final JCheckBox autoindex = new JCheckBox("become an indexnode if needed", iim.isAutoIndexnodeEnabled());
		autoindex.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				iim.setAutoIndexnode(autoindex.isSelected());
				updateAutoIndexnodeInfo();
			}
		});
		
		final JCheckBox always = new JCheckBox("always run an indexnode", iim.isAlwaysOn());
		always.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				iim.setAlwaysOn(always.isSelected());
				updateAutoIndexnodeInfo();
			}
		});
		
		ret.add(autoindex, BorderLayout.WEST);
		ret.add(always, BorderLayout.EAST);
		
		return ret;
	}
	
	private void updateAutoIndexnodeInfo() {
		String status;
		
		if (iim.isCurrentlyActive()) {
			status = "active";
		} else if (iim.isAutoIndexNodeInhibited()) {
			status = "inhibited";
		} else if (iim.isAutoIndexnodeEnabled()) {
			status = "inactive";
		} else {
			status = "disabled";
		}
		
		String positionInfo = (iim.getRank()!=0 && (status.equals("inactive") || status.equals("active")) ? "<br>Our automatic indexnode rank is <b>"+iim.getRank()+"</b> out of <b>"+iim.getAlternativeNodes()+"</b>." : "");
		
		autoindexInfo.setText("<html>The internal indexnode is: <b>"+status+"</b>" + positionInfo +
				              "</html>");
	}
	
	private JPanel createSlotsPanel() {
		JPanel ret = new JPanel(new BorderLayout());
		ret.setBorder(getTitledBoldBorder("Transfer slots"));
		
		JPanel inner = new JPanel();
		ret.add(inner, BorderLayout.NORTH);
		
		inner.setLayout(new BoxLayout(inner, BoxLayout.PAGE_AXIS));
		
		inner.add(doUploadSlotsSection());
		inner.add(doDownloadSlotsSection());
		
		return ret;
	}

	private JPanel doUploadSlotsSection() {
		JPanel upload = new JPanel();
		upload.setLayout(new BoxLayout(upload, BoxLayout.X_AXIS));
		upload.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
		
		final JSpinner upMaxSlots = new JSpinner(new SpinnerNumberModel(frame.getGui().getShareServer().getUploadSlots(), 1, Integer.MAX_VALUE, 1));
		final JSpinner upMaxSlotsPerClient = new JSpinner(new SpinnerNumberModel(frame.getGui().getShareServer().getUploadSlotsPerUser(), 1, Integer.MAX_VALUE, 1));
		
		upMaxSlots.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				frame.getGui().getShareServer().setUploadSlots((Integer)upMaxSlots.getValue());
			}
		});
		upMaxSlotsPerClient.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				frame.getGui().getShareServer().setUploadSlotsPerUser((Integer)upMaxSlotsPerClient.getValue());
			}
		});
		
		registerHint(upMaxSlots, new StatusHint(frame.getGui().getUtil().getImage("upload"), "The maximum number of concurrent uploads"));
		registerHint(upMaxSlotsPerClient, new StatusHint(frame.getGui().getUtil().getImage("upload"), "The number of slots a single peer can use at once"));
		
		((DefaultEditor) upMaxSlots.getEditor()).getTextField().setColumns(3);
		((DefaultEditor) upMaxSlotsPerClient.getEditor()).getTextField().setColumns(3);
		
		JLabel uploadl = new JLabel("<html><b>Upload:</b></html>");
		uploadl.setBorder(BorderFactory.createEmptyBorder(0,0,0,5));
		upload.add(uploadl);
		upload.add(upMaxSlots);
		upload.add(new JLabel("   per user:"));
		upload.add(upMaxSlotsPerClient);
		
		JPanel lefter = new JPanel(new BorderLayout());
		lefter.add(upload, BorderLayout.WEST);
		return lefter;
	}
	
	private JPanel doDownloadSlotsSection() {
		JPanel dl = new JPanel();
		dl.setLayout(new BoxLayout(dl, BoxLayout.X_AXIS));
		dl.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
		
		final JSpinner downMaxSlots = new JSpinner(new SpinnerNumberModel(frame.getGui().getDc().getDownloadSlots(), 1, Integer.MAX_VALUE, 1));
		final JSpinner downMaxParts = new JSpinner(new SpinnerNumberModel(frame.getGui().getDc().getMaxSlotsPerFile(), 1, Integer.MAX_VALUE, 1));
		
		downMaxSlots.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				frame.getGui().getDc().setDownloadSlots((Integer)downMaxSlots.getValue());
			}
		});
		downMaxParts.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				frame.getGui().getDc().setMaxSlotsPerFile((Integer)downMaxParts.getValue());
			}
		});
		
		registerHint(downMaxSlots, new StatusHint(frame.getGui().getUtil().getImage("download"), "The maximum number of concurrent downloads"));
		registerHint(downMaxParts, new StatusHint(frame.getGui().getUtil().getImage("download"), "The maximum number of chunks a single file can be split into for downloading."));
		
		((DefaultEditor) downMaxSlots.getEditor()).getTextField().setColumns(3);
		((DefaultEditor) downMaxParts.getEditor()).getTextField().setColumns(3);
		
		JLabel dll = new JLabel("<html><b>Download:</b></html>");
		dll.setBorder(BorderFactory.createEmptyBorder(0,0,0,5));
		dl.add(dll);
		dl.add(downMaxSlots);
		dl.add(new JLabel("   per file:"));
		dl.add(downMaxParts);
		
		JPanel lefter = new JPanel(new BorderLayout());
		lefter.add(dl, BorderLayout.WEST);
		return lefter;
	}
	
	JLabel heapInfo = new JLabel();
	Timer infoTimer;
	private JPanel heapSizePanel() {
		JPanel content = new JPanel();
		content.setLayout(new BorderLayout());
		content.setBorder(getTitledBoldBorder("Maximum heap size"));
		
		//Runtime.getRuntime().maxMemory()<(conf.getLong(CK.HEAPSIZE)*0.9
		final JBytesBox heapsize = new JBytesBox(frame.getGui().getConf().getLong(CK.HEAPSIZE));
		
		heapsize.addPropertyChangeListener("value", new PropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				long nv = (Long) evt.getNewValue();
				if (nv<0) {
					frame.setStatusHint(new StatusHint(SettingsTab.ERROR, "FS2's heap size can't be set to '"+heapsize.getText()+"'."));
				} else {
					if (nv > 32*1024*1024) {
						frame.getGui().getConf().putLong(CK.HEAPSIZE, nv);
						frame.setStatusHint(new StatusHint(SettingsTab.TICK, "FS2's heap set to "+Util.niceSize(nv)+", click 'Restart FS2' to apply changes"));
						frame.getGui().getConf().putBoolean(CK.AUTO_HEAP_KNOWLEDGE, true); //suppress the "i've changed your heap for you" message.
						restartNeeded();
					} else {
						frame.setStatusHint(new StatusHint(SettingsTab.ERROR, "The heap must be at least 32MiB."));
					}
				}
			}
		});
		
		
		content.add(heapInfo, BorderLayout.WEST);
		setHeapInfo();
		heapInfo.setBorder(BorderFactory.createEmptyBorder(0,0,0,5));
		content.add(heapsize, BorderLayout.CENTER);
		
		infoTimer = new Timer(5000, new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				setHeapInfo();
				setPortNumberInfo();
				updateAutoIndexnodeInfo();
			}
		});
		infoTimer.start();
		
		JButton gc = new JButton(frame.getGui().getUtil().getImage("gc"));
		gc.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				System.gc();
				setHeapInfo();
			}
		});
		content.add(gc, BorderLayout.EAST);
		
		registerHint(heapsize, new StatusHint(SettingsTab.TICK, "Set this to several GiB to host a large indexnode."));
		registerHint(gc, new StatusHint(frame.getGui().getUtil().getImage("gc"), "Triggers a garbage collection now."));
		
		return content;
	}
	
	private void setHeapInfo() {
		heapInfo.setText("<html>Active JVM maximum heap size: <b>"+Util.niceSize(Runtime.getRuntime().maxMemory())+"</b><br>" +
		         "Current heap usage: <b>"+Util.niceSize(Runtime.getRuntime().maxMemory()-Runtime.getRuntime().freeMemory())+"</b><br>" +
		         "Configured maximum heap size:");
	}
	
	JLabel portNumberInfo = new JLabel();
	private JPanel portPanel() {
		JPanel ppanel = new JPanel(new BorderLayout());
		ppanel.setBorder(getTitledBoldBorder("Client port"));
		
		final JSpinner port = new JSpinner(new SpinnerNumberModel(frame.getGui().getConf().getInt(CK.PORT), 1, Integer.MAX_VALUE, 1));
		
		port.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				frame.getGui().getConf().putInt(CK.PORT, ((Integer)port.getValue()));
				setPortNumberInfo();
				restartNeeded();
			}
		});
		
		registerHint(port, new StatusHint(null, "The port that FS2 listens on. (port+1 is also used!)"));
		
		((DefaultEditor) port.getEditor()).getTextField().setColumns(7);
		JPanel portShrinker = new JPanel(new BorderLayout());
		portShrinker.add(port, BorderLayout.WEST);
		
		ppanel.add(portNumberInfo, BorderLayout.NORTH);
		ppanel.add(portShrinker, BorderLayout.CENTER);
		setPortNumberInfo();
		
		return ppanel;
	}
	
	private void setPortNumberInfo() {
		ArrayList<String> ports = new ArrayList<String>();
		ports.add(frame.getGui().getConf().getString(CK.PORT));
		ports.add(Integer.toString(frame.getGui().getConf().getInt(CK.PORT)+1));
		ports.add(Integer.toString(FS2Constants.ADVERTISMENT_DATAGRAM_PORT));
		ports.add(Integer.toString(FS2Constants.ADVERTISMENT_DATAGRAM_PORT+1));
		if (frame.getGui().getShareServer().getIndexNodeCommunicator().getInternalIndexNode().isCurrentlyActive()) {
			ports.add(Integer.toString(frame.getGui().getShareServer().getIndexNodeCommunicator().getInternalIndexNode().getPort()));
			ports.add(Integer.toString(frame.getGui().getShareServer().getIndexNodeCommunicator().getInternalIndexNode().getPort()+1));
		}
		
		portNumberInfo.setText("<html>FS2 is currently using ports: <b>"+Util.join(ports.toArray(), ", ")+"</b><br>Open these ports on your firewall to use FS2</html>");
	}
	
	/**
	 * configures autoupdate settings, triggers a check for updates right now.
	 * @return
	 */
	private JPanel autoupdatePanel() {
		JPanel ret = new JPanel(new BorderLayout());
		ret.setBorder(getTitledBoldBorder("Autoupdate"));
		
		String[] options = {"Automatically update (Recommended)", "Ask when updates are available", "Never update"};
		
		final JComboBox choice = new JComboBox(options);
		
		if (frame.getGui().getConf().getString(CK.UPDATE_POLICY).equals("none")) {
			choice.setSelectedIndex(2);
		} else if (frame.getGui().getConf().getString(CK.UPDATE_POLICY).equals("ask")) {
			choice.setSelectedIndex(1);
		} else if (frame.getGui().getConf().getString(CK.UPDATE_POLICY).equals("auto")) { //pointless test for completeness.
			choice.setSelectedIndex(0);
		}
		
		choice.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				switch (choice.getSelectedIndex()) {
				case 0:
					frame.getGui().getConf().putString(CK.UPDATE_POLICY, "auto");
					break;
				case 1:
					frame.getGui().getConf().putString(CK.UPDATE_POLICY, "ask");
					break;
				default:
					frame.getGui().getConf().putString(CK.UPDATE_POLICY, "none");
					break;
				}
			}
		});
		
		JLabel auinfo = new JLabel("<html>Select <i>'"+options[2]+"'</i> to prevent FS2 from even checking for updates.</html>");
		ret.add(auinfo, BorderLayout.NORTH);
		ret.add(choice, BorderLayout.WEST);
		
		JButton aunow = new JButton("Check for updates now", frame.getGui().getUtil().getImage("checkupdates"));
		ret.add(aunow, BorderLayout.EAST);
		aunow.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				ClientExecutor.getAcquire().checkForUpdatesNowAndAsk();
			}
		});
		
		return ret;
	}
	
	
	
	private void restartNeeded() {
		restartFS2.setText(restartFS2.getText().toUpperCase());
		restartFS2.setBackground(JBytesBox.bad);
		buttonsPanel.setBackground(JBytesBox.bad);
		restartFS2.setFont(restartFS2.getFont().deriveFont(Font.BOLD|Font.ITALIC));
		((TitledBorder)buttonsPanel.getBorder()).setTitle("You need to restart FS2 to apply the changes!");
	}
	
	private JButton resetFS2;
	private JButton restartFS2; //clicking this restarts the FS2 client.
	private JPanel buttonsPanel;
	/**
	 * A single button that nukes FS2's configuration, and a button to relaunch FS2.
	 * @return
	 */
	private JPanel resetToDefaultsPanel() {
		buttonsPanel = new JPanel(new FlowLayout());
		buttonsPanel.setBorder(getTitledBoldBorder("Reset controls"));
		((FlowLayout)buttonsPanel.getLayout()).setAlignment(FlowLayout.LEFT);
		
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
