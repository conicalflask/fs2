package client.gui.settings;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;

import common.FS2Constants;
import common.Logger;
import common.Util;

import client.gui.JBytesBox;
import client.gui.JTextFieldLimit;
import client.gui.MainFrame;
import client.gui.Utilities;
import client.gui.MainFrame.StatusHint;
import client.platform.Platform;

@SuppressWarnings("serial")
public class BasicSettings extends SettingsPanel implements KeyListener {

	public BasicSettings(MainFrame frame) {
		super(frame, "Basic", frame.getGui().getUtil().getImage("basic"));
		
		//Construct a basic settings page including: alias, avatar, etc.
		JPanel boxes = createScrollableBoxlayout();
		boxes.add(createAliasPanel());
		boxes.add(createDDPanel());
		boxes.add(createSpeedsPanel());
		
	}

	private JPanel createSpeedsPanel() {
		JPanel panel0 = new JPanel();
		panel0.setLayout(new BoxLayout(panel0, BoxLayout.PAGE_AXIS));
		
		panel0.setBorder(getTitledBoldBorder("Maximum transfer speeds"));
		
		panel0.add(createUploadSpeedPanel());
		panel0.add(createDownloadSpeedPanel());
		
		return panel0;
	}
	
	private JPanel createUploadSpeedPanel() {
		JPanel content = new JPanel();
		content.setBorder(BorderFactory.createEmptyBorder(0,0,5,0));
		
		content.setLayout(new BoxLayout(content, BoxLayout.X_AXIS));
		
		final JBytesBox speed = new JBytesBox(frame.getGui().getShareServer().getUploadSpeed());
		
		speed.addPropertyChangeListener("value", new PropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				long nv = (Long) evt.getNewValue();
				if (nv<=0) {
					frame.setStatusHint(new StatusHint(SettingsTab.TICK, "The upload speed can't be set to '"+speed.getText()+"'."));
				} else {
					frame.getGui().getShareServer().setUploadSpeed(nv);
					frame.setStatusHint(new StatusHint(SettingsTab.TICK, "The upload speed has been set to "+Util.niceSize(nv)));
				}
			}
		});
		
		content.add(new JLabel("Upload:     "));
		content.add(speed);
		
		registerHint(speed, new StatusHint(SettingsTab.TICK, "(saved on change) The maximum upload amount per second, examples: 5.5mb, 10b, 999tib"));
		
		return content;
	}
	
	private JPanel createDownloadSpeedPanel() {
		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.X_AXIS));
		
		final JBytesBox speed = new JBytesBox(frame.getGui().getDc().getDownloadSpeed());
		
		speed.addPropertyChangeListener("value", new PropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				long nv = (Long) evt.getNewValue();
				if (nv<0) {
					frame.setStatusHint(new StatusHint(SettingsTab.ERROR, "The download speed can't be set to '"+speed.getText()+"'."));
				} else {
					frame.getGui().getDc().setDownloadSpeed(nv);
					frame.setStatusHint(new StatusHint(SettingsTab.ERROR, "The download speed has been set to "+Util.niceSize(nv)));
				}
			}
		});
		
		content.add(new JLabel("Download: "));
		content.add(speed);
		
		registerHint(speed, new StatusHint(SettingsTab.TICK, "(saved on change) The maximum download amount per second, examples: 5.5mb, 10b, 999tib"));
		
		return content;
	}
	
	private JPanel createDDPanel() {
		final JButton downloadDirectory;
		final JLabel currentLocation = new JLabel(frame.getGui().getDc().getDefaultDownloadDirectory().getPath());
		downloadDirectory = new JButton("<html><b>Browse</b></html>", frame.getGui().getUtil().getImage("type-dir"));
		downloadDirectory.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				JFileChooser fc = new JFileChooser(frame.getGui().getDc().getDefaultDownloadDirectory());
				fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
				int retVal = fc.showOpenDialog(null);
				if (retVal==JFileChooser.APPROVE_OPTION) {
					frame.getGui().getDc().setDefaultDownloadDirectory(fc.getSelectedFile());
					frame.getGui().getShareServer().defaultDownloadDirectoryChanged(fc.getSelectedFile()); //change the "My Downloads" share if it still exists.
					currentLocation.setText(frame.getGui().getDc().getDefaultDownloadDirectory().getPath());
				}
			}
		});
		
		JPanel ddPanel = new JPanel(new BorderLayout());
		ddPanel.setBorder(getTitledBoldBorder("Default download directory"));
		JPanel panel0 = new JPanel();
		ddPanel.add(panel0, BorderLayout.WEST);
		ddPanel.add(currentLocation, BorderLayout.NORTH);
		currentLocation.setAlignmentX(LEFT_ALIGNMENT);
		panel0.add(downloadDirectory);
		registerHint(downloadDirectory, new StatusHint(frame.getGui().getUtil().getImage("type-dir"), "This is where your downloads will go to by default"));
		
		return ddPanel;
	}
	
	JButton avatarButton;
	private JTextField aliasText;
	private JPanel createAliasPanel() {
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBorder(getTitledBoldBorder("Alias and Avatar"));
		
		JPanel panel0 = new JPanel(new BorderLayout());
		panel.add(panel0, BorderLayout.NORTH);
		
		aliasText = new JTextField();
		JPanel panel1 = new JPanel();
		panel1.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		
		panel1.setLayout(new BoxLayout(panel1, BoxLayout.PAGE_AXIS));
		panel1.add(new Box.Filler(new Dimension(2, 2), new Dimension(2, 2), new Dimension(2, 200)));	//enable the slack space to be taken in.s
		panel1.add(aliasText);
		aliasText.setMaximumSize(new Dimension(aliasText.getMaximumSize().width, aliasText.getMinimumSize().height)); //fix to be the correct height.
		panel1.add(new Box.Filler(new Dimension(2, 2), new Dimension(2, 2), new Dimension(2, 200)));
		panel0.add(panel1, BorderLayout.CENTER);
		aliasText.setDocument(new JTextFieldLimit(32));
		aliasText.setText(frame.getGui().getShareServer().getAlias());
		aliasText.addKeyListener(this);
		
		ImageIcon ico = null;
		File avatarFile = frame.getGui().getShareServer().getIndexNodeCommunicator().getAvatarFile();
		if (avatarFile.isFile()) {
			try {
				ico = new ImageIcon(ImageIO.read(avatarFile));
			} catch (IOException e) {
				Logger.warn("Avatar "+avatarFile.getPath()+" couldn't be loaded from disk: "+ e);
				ico = frame.getGui().getUtil().getImage("defaultavatar");
			}
		} else {
			ico = frame.getGui().getUtil().getImage("defaultavatar");
		}
		
		avatarButton = new JButton("", ico);
		avatarButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				setIcon();
			}
		});
		
		panel0.add(avatarButton, BorderLayout.WEST);
		
		registerHint(aliasText, new StatusHint(frame.getGui().getUtil().getImage("tick"), "(saved on change) Set your alias on the FS2 network here."));
		registerHint(avatarButton, new StatusHint(frame.getGui().getUtil().getImage("type-image"), "Click this button to set your avatar"));
		
		return panel;
	}
	
	private File lastUsedIconPath;
	private void setIcon() {
		JFileChooser iconPicker = new JFileChooser(lastUsedIconPath);
		FileNameExtensionFilter filter = new FileNameExtensionFilter("Images", "jpg", "gif", "jpeg", "png", "tiff", "bmp");
		iconPicker.setFileFilter(filter);
		int result = iconPicker.showOpenDialog(this);
		if(result == JFileChooser.APPROVE_OPTION) {
			try {
				lastUsedIconPath = iconPicker.getCurrentDirectory();
				InputStream fis = null;
				try {
					fis = new BufferedInputStream(new FileInputStream(iconPicker.getSelectedFile()));
		    		final BufferedImage chosen = Util.processImageInternal(fis, FS2Constants.FS2_AVATAR_ICON_SIZE, FS2Constants.FS2_AVATAR_ICON_SIZE, Util.ImageResizeType.OUTER); //resize to appropriate dimensions.
		    		avatarButton.setText("Sending...");
		    		avatarButton.setEnabled(false);
		    		avatarButton.setIcon(new ImageIcon(chosen));
		    		
		    		Thread worker = new Thread(new Runnable() {
						@Override
						public void run() {
							boolean success;
							IOException ex = null;
							try {
								//1) save the resized image to a cache file:
								File avatarCache = Platform.getPlatformFile("avatar.png");
								ImageIO.write(chosen, "png", avatarCache);
								
								//2) set the indexnode comm to use this file:
								frame.getGui().getShareServer().getIndexNodeCommunicator().setAvatarFile(avatarCache);
							
								success = true;
							} catch (IOException e) {
								ex = e;
								success = false;
								Logger.warn("Couldn't send avatar to indexnode: "+e);
							}
							
						    final boolean esuccess = success;
						    final IOException eex = ex;
							
				    		Utilities.edispatch(new Runnable() {
								@Override
								public void run() {
									if (esuccess) {
										avatarButton.setText("");
									} else {
										avatarButton.setText("failure: "+eex);
									}
						    		avatarButton.setEnabled(true);
								}
							});
						}
					});
		    		worker.setName("avatar change submitter");
		    		worker.start();
				} finally {
					if (fis!=null) fis.close();
				}
			} catch (Exception ex) {
				Logger.warn("Couldn't load a selected avatar: "+ex);
				avatarButton.setText(iconPicker.getSelectedFile().getName()+" can't be loaded.");
			}
		}
	}
	
	@Override
	public void keyReleased(KeyEvent e) {
		if (e.getSource()==aliasText) frame.getGui().getShareServer().setAlias(aliasText.getText());
	}

	@Override
	public void keyPressed(KeyEvent e) {}

	@Override
	public void keyTyped(KeyEvent e) {}

}
