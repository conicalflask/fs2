package client.gui.settings;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.ImageObserver;
import java.io.File;
import java.util.LinkedList;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableCellRenderer;

import common.FS2Constants;
import common.Logger;
import common.Util;

import client.gui.FancierTable;
import client.gui.MainFrame;
import client.gui.MainFrame.StatusHint;
import client.platform.Platform;
import client.platform.ClientConfigDefaults.CK;
import client.shareserver.Share;
import client.shareserver.Share.Status;

@SuppressWarnings("serial")
public class ShareSettings extends SettingsPanel {
	
	private JTable shares;
	private JButton addShare;
	private JButton removeShares;
	private JButton refreshShares;
	
	private class SharesLoadingAnimationHelper implements ImageObserver, TableModelListener {

		ImageIcon spinner = new ImageIcon(frame.getGui().getUtil().getImageFullname("loading.gif").getImage()); //take a copy because we have to assign our own observer.
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
			for (Share s : frame.getGui().getShareServer().getShares()) {
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
			frame.getGui().getShareServer().addTableModelListener(spinner);
		}
		
		@Override
		public Component getTableCellRendererComponent(JTable table,
				Object value, boolean isSelected, boolean hasFocus, int row,
				int column) {
			super.getTableCellRendererComponent(table, value, isSelected, hasFocus,
					row, column);
			
			switch (frame.getGui().getShareServer().getShares().get(shares.convertRowIndexToModel(row)).getStatus()) {
			case ACTIVE:
				if (value.toString().equals(FS2Constants.CLIENT_DEFAULT_SHARE_NAME)) {
					setIcon(frame.getGui().getUtil().getImage("mydownloads"));
				} else {
					setIcon(frame.getGui().getUtil().getImage("shareactive"));
				}
				break;
			case SAVING:
			case BUILDING:
			case REFRESHING:
				setIcon(spinner.spinner);
				break;
			case ERROR:
				setIcon(SettingsTab.ERROR);
				break;
			default:
				setIcon(null);
				break;
			}
			
			return this;
		}
	}
	
	public ShareSettings(final MainFrame frame) {
		super(frame, "Shares", frame.getGui().getUtil().getImage("shares"));
		
		shares = new FancierTable(frame.getGui().getShareServer(), frame.getGui().getConf(), CK.SHARES_TABLE_COLWIDTHS);
		registerHint(shares, new StatusHint(frame.getGui().getUtil().getImage("shares"), "These directories are shared with other peers"));
		
		shares.getColumn(frame.getGui().getShareServer().getColumnName(0)).setCellRenderer(new ShareNameCellRenderer());
		
		add(new JLabel("Your shared folders: ", frame.getGui().getUtil().getImage("shares"), JLabel.LEFT), BorderLayout.NORTH);
		add(new JScrollPane(shares), BorderLayout.CENTER);
		
		JPanel bp = new JPanel(new FlowLayout());
		add(bp, BorderLayout.SOUTH);
		addShare = new JButton("Add share...", frame.getGui().getUtil().getImage("add"));
		registerHint(addShare, new StatusHint(frame.getGui().getUtil().getImage("add"), "Shares a new folder with FS2"));
		addShare.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				addShare(frame, e);	
			}
		});
		bp.add(addShare);
		
		removeShares = new JButton("Remove selected shares", frame.getGui().getUtil().getImage("delete"));
		registerHint(removeShares, new StatusHint(frame.getGui().getUtil().getImage("delete"), "Stops sharing the selected folders"));
		removeShares.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				removeShares(frame);
			}
		});
		bp.add(removeShares);
		
		refreshShares = new JButton("Refresh selected shares", frame.getGui().getUtil().getImage("refresh"));
		registerHint(refreshShares, new StatusHint(frame.getGui().getUtil().getImage("refresh"), "Makes changes in the selected folders available to other peers immediately"));
		refreshShares.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				refreshShares(frame);
			}
		});
		bp.add(refreshShares);
		
	}

	private void addShare(final MainFrame frame, ActionEvent e) {
		JFileChooser fc = new JFileChooser();
		fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		int retVal = fc.showOpenDialog(null);
		if (retVal==JFileChooser.APPROVE_OPTION) {
			final File chosen = fc.getSelectedFile();
			String shareName;
			while(true) {
				shareName = (String) JOptionPane.showInputDialog(null, "What name should it be shared as?", "Share name...", JOptionPane.QUESTION_MESSAGE, null, null, chosen.getName());
				if (shareName==null) return;
				if (shareName.equals("") || frame.getGui().getShareServer().shareNameExists(shareName)) {
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
						frame.getGui().getShareServer().addShare(chosenName, chosen);
					}
				});
				inBackground.setDaemon(true);
				inBackground.setName("GUI bg: adding share");
				inBackground.start();
				
				frame.setStatusHint(new StatusHint(SettingsTab.TICK, "Share added! (may take a little while to appear if the file list needs loading!)"));
			} catch (Exception ex) {
				Logger.log("Exception adding share: "+e);
				ex.printStackTrace();
				frame.setStatusHint(new StatusHint(SettingsTab.ERROR, "Share couldn't be added!"));
			}
		}
	}

	private void removeShares(final MainFrame frame) {
		int[] togo = shares.getSelectedRows();
		final LinkedList<Share> goodbye = new LinkedList<Share>();
		for (int i : togo) {
			goodbye.add(frame.getGui().getShareServer().getShares().get(shares.convertRowIndexToModel(i)));
		}
		Thread inBackground = new Thread(new Runnable() {
			@Override
			public void run() {
				for (Share s : goodbye) frame.getGui().getShareServer().removeShare(s);
			}
		});
		inBackground.setDaemon(true);
		inBackground.setName("GUI bg: removing shares");
		inBackground.start();
	}

	private void refreshShares(final MainFrame frame) {
		int[] torefresh = shares.getSelectedRows();
		LinkedList<Share> ref = new LinkedList<Share>();
		for (int i : torefresh) {
			ref.add(frame.getGui().getShareServer().getShares().get(shares.convertRowIndexToModel(i)));
		}
		for (Share s : ref) s.refresh();
	}
	
	
	

}
