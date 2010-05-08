package client.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.ImageObserver;
import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;

import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.JSpinner.DefaultEditor;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableCellRenderer;

import client.gui.MainFrame.StatusHint;
import client.platform.Platform;
import client.platform.ClientConfigDefaults.CK;
import client.shareserver.Share;
import client.shareserver.Share.Status;

import common.FS2Constants;
import common.Logger;
import common.Util;


/**
 * The shares tab configures shared directories and lets the user change transfer speeds and slots.
 * @author gary
 *
 */
@SuppressWarnings("serial")
public class SharesTab extends TabItem implements MouseListener,
											      ChangeListener,
											      KeyListener,
											      ActionListener {

	HashMap<JComponent, StatusHint> hints = new HashMap<JComponent, StatusHint>();
	Color good = new Color(200, 255, 200);
	Color bad = new Color(255, 200, 100);
	ImageIcon tick;
	ImageIcon error;
	JButton downloadDirectory;
	
	public SharesTab(JTabbedPane pane, MainFrame frame) {
		super(pane, frame, "Shares", FS2Tab.SHARES, frame.gui.util.getImage("shares"));
		
		tick = frame.gui.util.getImage("tick");
		error = frame.gui.util.getImage("error");

		setLayout(new BorderLayout());
		
		downloadDirectory = new JButton("Default download directory: "+frame.gui.dc.getDefaultDownloadDirectory(), frame.gui.util.getImage("type-dir"));
		downloadDirectory.addActionListener(this);
		JPanel ddPanel = new JPanel();
		ddPanel.add(downloadDirectory);
		
		JPanel top = new JPanel();
		BoxLayout bl = new BoxLayout(top, BoxLayout.Y_AXIS);
		top.setLayout(bl);
		
		top.add(createUploadsPanel());
		top.add(createDownloadsPanel());
		top.add(ddPanel);
		
		this.add(top, BorderLayout.NORTH);
		
		add(createSharesPanel(), BorderLayout.CENTER);
	}

	JTextField upSpeed;
	JSpinner upMaxSlots;
	JSpinner upMaxSlotsPerClient;
	
	private JPanel createUploadsPanel() {
		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.X_AXIS));
		
		upSpeed = new JTextField(Util.niceSize(frame.gui.ssvr.getUploadSpeed()));
		upMaxSlots = new JSpinner(new SpinnerNumberModel(frame.gui.ssvr.getUploadSlots(), 1, Integer.MAX_VALUE, 1));
		upMaxSlotsPerClient = new JSpinner(new SpinnerNumberModel(frame.gui.ssvr.getUploadSlotsPerUser(), 1, Integer.MAX_VALUE, 1));
		
		upSpeed.addKeyListener(this);
		upMaxSlots.addChangeListener(this);
		upMaxSlotsPerClient.addChangeListener(this);
		upSpeed.addMouseListener(this);
		upMaxSlots.addMouseListener(this);
		upMaxSlotsPerClient.addMouseListener(this);
		
		hints.put(upSpeed, new StatusHint(tick, "(saved on change) The maximum upload amount per second, examples: 5.5mb, 10b, 999tib"));
		hints.put(upMaxSlots, new StatusHint(tick, "(saved on change) The number of upload slots, at least two is good."));
		hints.put(upMaxSlotsPerClient, new StatusHint(tick, "(saved on change) Another FS2 user can only use this many of your slots at once."));
		
		upSpeed.setBackground(good);
		((DefaultEditor) upMaxSlots.getEditor()).getTextField().setColumns(3);
		((DefaultEditor) upMaxSlotsPerClient.getEditor()).getTextField().setColumns(3);
		
		content.add(new JLabel("Maximum upload per second:"));
		content.add(upSpeed);
		content.add(new JLabel("  Upload slots:"));
		content.add(upMaxSlots);
		content.add(new JLabel("  Uploads per user:"));
		content.add(upMaxSlotsPerClient);
		
		return content;
	}
	
	JTextField downSpeed;
	JSpinner downMaxSlots;
	JSpinner downSlotsPerFile;
	private JPanel createDownloadsPanel() {
		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.X_AXIS));
		
		downSpeed = new JTextField(Util.niceSize(frame.gui.dc.getDownloadSpeed()));
		downMaxSlots = new JSpinner(new SpinnerNumberModel(frame.gui.dc.getDownloadSlots(), 1, Integer.MAX_VALUE, 1));
		downSlotsPerFile = new JSpinner(new SpinnerNumberModel(frame.gui.dc.getMaxSlotsPerFile(), 1, Integer.MAX_VALUE, 1));
		
		
		downSpeed.addKeyListener(this);
		downMaxSlots.addChangeListener(this);
		downSlotsPerFile.addChangeListener(this);
		
		downSpeed.addMouseListener(this);
		downMaxSlots.addMouseListener(this);
		downSlotsPerFile.addMouseListener(this);
		
		hints.put(downSpeed, new StatusHint(tick, "(saved on change) The maximum download amount per second, examples: 5.5mb, 10b, 999tib"));
		hints.put(downMaxSlots, new StatusHint(tick, "(saved on change) The maximum number of simultanous downloads"));
		hints.put(downSlotsPerFile, new StatusHint(tick, "(saved on change) The maximum number slots each file can use"));
		
		downSpeed.setBackground(good);
		((DefaultEditor) downMaxSlots.getEditor()).getTextField().setColumns(3);
		((DefaultEditor) downSlotsPerFile.getEditor()).getTextField().setColumns(3);
		
		content.add(new JLabel("Maximum download per second:"));
		content.add(downSpeed);
		content.add(new JLabel("  Download slots:"));
		content.add(downMaxSlots);
		content.add(new JLabel("  Slots per file:"));
		content.add(downSlotsPerFile);
		
		return content;
	}
	
	JTable shares;
	JButton addShare;
	JButton removeShares;
	JButton refreshShares;
	private JPanel createSharesPanel() {
		JPanel content = new JPanel(new BorderLayout());
		
		shares = new FancierTable(frame.gui.ssvr, frame.gui.conf, CK.SHARES_TABLE_COLWIDTHS);
		
		shares.getColumn(frame.gui.ssvr.getColumnName(0)).setCellRenderer(new ShareNameCellRenderer());
		
		content.add(new JLabel("Your shared folders: ", frame.gui.util.getImage("shares"), JLabel.LEFT), BorderLayout.NORTH);
		content.add(new JScrollPane(shares), BorderLayout.CENTER);
		
		JPanel bp = new JPanel(new FlowLayout());
		content.add(bp, BorderLayout.SOUTH);
		addShare = new JButton("Add share...", frame.gui.util.getImage("add"));
		addShare.addActionListener(this);
		bp.add(addShare);
		
		removeShares = new JButton("Remove selected shares", frame.gui.util.getImage("delete"));
		removeShares.addActionListener(this);
		bp.add(removeShares);
		
		refreshShares = new JButton("Refresh selected shares", frame.gui.util.getImage("refresh"));
		refreshShares.addActionListener(this);
		bp.add(refreshShares);
		
		return content;
	}
	
	private class SharesLoadingAnimationHelper implements ImageObserver, TableModelListener {

		ImageIcon spinner = new ImageIcon(frame.gui.util.getImageFullname("loading.gif").getImage()); //take a copy because we have to assign our own observer.
		int activeCount = 0;
		
		public SharesLoadingAnimationHelper() {
			spinner.setImageObserver(this);
		}
		
		@Override
		public boolean imageUpdate(Image img, int infoflags, int x, int y,
				int width, int height) {
			if (activeCount>0) {
				shares.imageUpdate(img, infoflags, x, y, width, height);
			}
			return true;
		}

		@Override
		public void tableChanged(TableModelEvent e) {
			//There's no way of knowing which ones became inactive, so traul the whole list of shares every update :'(
			int newCount = 0;
			for (Share s : frame.gui.ssvr.getShares()) {
				if (s.getStatus()==Status.BUILDING || s.getStatus()==Status.REFRESHING || s.getStatus()==Status.SAVING) {
					newCount++;
				}
			}
			activeCount=newCount;
		}
		
	}
	
	private class ShareNameCellRenderer extends DefaultTableCellRenderer {
		
		SharesLoadingAnimationHelper spinner = new SharesLoadingAnimationHelper();
		
		public ShareNameCellRenderer() {
			frame.gui.ssvr.addTableModelListener(spinner);
		}
		
		@Override
		public Component getTableCellRendererComponent(JTable table,
				Object value, boolean isSelected, boolean hasFocus, int row,
				int column) {
			super.getTableCellRendererComponent(table, value, isSelected, hasFocus,
					row, column);
			
			switch (frame.gui.ssvr.getShares().get(shares.convertRowIndexToModel(row)).getStatus()) {
			case ACTIVE:
				if (value.toString().equals(FS2Constants.CLIENT_DEFAULT_SHARE_NAME)) {
					setIcon(frame.gui.util.getImage("mydownloads"));
				} else {
					setIcon(frame.gui.util.getImage("shareactive"));
				}
				break;
			case SAVING:
			case BUILDING:
			case REFRESHING:
				setIcon(spinner.spinner);
				break;
			case ERROR:
				setIcon(error);
				break;
			default:
				setIcon(null);
				break;
			}
			
			return this;
		}
	}
	
	@Override
	public void mouseClicked(MouseEvent e) {}

	@Override
	public void mouseEntered(MouseEvent e) {
		if (hints.containsKey(e.getSource())) {
			frame.setStatusHint(hints.get(e.getSource()));
		}
	}

	@Override
	public void mouseExited(MouseEvent e) {}

	@Override
	public void mousePressed(MouseEvent e) {}

	@Override
	public void mouseReleased(MouseEvent e) {}

	@Override
	public void stateChanged(ChangeEvent e) {
		if (e.getSource()==upMaxSlots) {
			frame.gui.ssvr.setUploadSlots((Integer)upMaxSlots.getValue());
		} else if (e.getSource()==upMaxSlotsPerClient) {
			frame.gui.ssvr.setUploadSlotsPerUser((Integer)upMaxSlotsPerClient.getValue());
		} else if (e.getSource()==downMaxSlots) {
			frame.gui.dc.setDownloadSlots((Integer)downMaxSlots.getValue());
		} else if (e.getSource()==downSlotsPerFile) {
			frame.gui.dc.setMaxSlotsPerFile((Integer) downSlotsPerFile.getValue());
		}
	}

	@Override
	public void keyPressed(KeyEvent e) {}

	@Override
	public void keyReleased(KeyEvent e) {
		if (e.getSource()==upSpeed || e.getSource()==downSpeed) {
			setSpeed((JTextField) e.getSource());
		} 
	}
	
	void setSpeed(JTextField source) {
		long sp = Util.parseNiceSize(source.getText().trim());
		if (sp==-1) {
			source.setBackground(bad);
			frame.setStatusHint(new StatusHint(error,"Can't set the "+(source==upSpeed ? "upload" : "download")+" speed to '"+source.getText()+"'"));
		} else {
			source.setBackground(good);
			frame.setStatusHint(new StatusHint(tick,"Maximum "+(source==upSpeed ? "upload" : "download")+" speed set to: "+Util.niceSize(sp)+" per second."));
			if (source==upSpeed) {
				frame.gui.ssvr.setUploadSpeed(sp);
			} else {
				frame.gui.dc.setDownloadSpeed(sp);
			}
		}
	}

	@Override
	public void keyTyped(KeyEvent e) {}

	@Override
	public void actionPerformed(ActionEvent e) {
		if (e.getSource()==addShare) {
			JFileChooser fc = new JFileChooser();
			fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			int retVal = fc.showOpenDialog(null);
			if (retVal==JFileChooser.APPROVE_OPTION) {
				final File chosen = fc.getSelectedFile();
				String shareName;
				while(true) {
					shareName = (String) JOptionPane.showInputDialog(null, "What name should it be shared as?", "Share name...", JOptionPane.QUESTION_MESSAGE, null, null, chosen.getName());
					if (shareName==null) return;
					if (shareName.equals("") || frame.gui.ssvr.shareNameExists(shareName)) {
						JOptionPane.showMessageDialog(null, "That name is empty or already exists, try again.");
						continue;
					} else if (!Util.isValidFileName(Platform.getPlatformFile("filelists"+File.separator+shareName+".FileList"))) {
						JOptionPane.showMessageDialog(null, "That share name is invalid, try again. (share names must be valid filenames!)");
						continue;
					} else break;
				}
				
				final String chosenName = shareName;
				try {
					Thread inBackground = new Thread(new Runnable() {
						@Override
						public void run() {
							frame.gui.ssvr.addShare(chosenName, chosen);
						}
					});
					inBackground.setDaemon(true);
					inBackground.setName("GUI bg: adding share");
					inBackground.start();
					
					frame.setStatusHint(new StatusHint(tick, "Share added! (may take a little while to appear if the file list needs loading!)"));
				} catch (Exception ex) {
					Logger.log("Exception adding share: "+e);
					ex.printStackTrace();
					frame.setStatusHint(new StatusHint(error, "Share couldn't be added!"));
				}
			}	
		} else if (e.getSource()==removeShares) {
			int[] togo = shares.getSelectedRows();
			final LinkedList<Share> goodbye = new LinkedList<Share>();
			for (int i : togo) {
				goodbye.add(frame.gui.ssvr.getShares().get(shares.convertRowIndexToModel(i)));
			}
			Thread inBackground = new Thread(new Runnable() {
				@Override
				public void run() {
					for (Share s : goodbye) frame.gui.ssvr.removeShare(s);
				}
			});
			inBackground.setDaemon(true);
			inBackground.setName("GUI bg: removing shares");
			inBackground.start();
			
		} else if (e.getSource()==refreshShares) {
			int[] torefresh = shares.getSelectedRows();
			LinkedList<Share> ref = new LinkedList<Share>();
			for (int i : torefresh) {
				ref.add(frame.gui.ssvr.getShares().get(shares.convertRowIndexToModel(i)));
			}
			for (Share s : ref) s.refresh();
		} else if (e.getSource()==downloadDirectory) {
			JFileChooser fc = new JFileChooser(frame.gui.dc.getDefaultDownloadDirectory());
			fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			int retVal = fc.showOpenDialog(null);
			if (retVal==JFileChooser.APPROVE_OPTION) {
				frame.gui.dc.setDefaultDownloadDirectory(fc.getSelectedFile());
				frame.gui.ssvr.defaultDownloadDirectoryChanged(fc.getSelectedFile()); //change the "My Downloads" share if it still exists.
				downloadDirectory.setText("Default download directory: "+frame.gui.dc.getDefaultDownloadDirectory());
			}
		}
	}
}
