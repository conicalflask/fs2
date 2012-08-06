package client.gui.chat;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.border.Border;
import javax.swing.table.TableCellRenderer;

import common.Logger;
import common.Util;

import client.gui.MainFrame;
import client.indexnode.IndexNodeStats.IndexNodeClient;
import client.platform.Platform;

@SuppressWarnings("serial")
public class AvatarRenderer extends JPanel implements TableCellRenderer {

	Border unselectedBorder = null;
	Border selectedBorder = null;
	boolean isBordered = true;
	JPanel right;
	JLabel icon;
	JLabel name;
	JLabel status;
	MainFrame frame;
	
	public AvatarRenderer(MainFrame frame){
		this.frame = frame;
		setLayout(new BoxLayout(this, BoxLayout.X_AXIS));

		// Setup the layout
		right = new JPanel();
		right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
		right.setBackground(null);
		
		// Avatar
		icon = new JLabel(" ");
		add(icon);
		icon.setBorder(BorderFactory.createEmptyBorder(0,0,0,4));

		// Right
		name = new JLabel();
		name.setFont(new Font(Font.DIALOG, Font.BOLD, 12));
		status = new JLabel();
		status.setFont(new Font(Font.DIALOG, Font.PLAIN, 10));
		
		right.add(name);
		right.add(status);
		
		add(right);
		
		
		Platform.getPlatformFile("avatars").mkdirs();
	}
	
	@Override
	public Component getTableCellRendererComponent(JTable table, Object value,
			boolean isSelected, boolean hasFocus, int row, int column) {

		setOpaque(true);
		
		IndexNodeClient avatar = (IndexNodeClient)value;

		name.setText(avatar.getAlias());
		status.setText(Util.niceSize(avatar.getTotalShareSize()) + " shared");
		
		icon.setIcon(getAvatarForClient(avatar));
		icon.setPreferredSize(new Dimension(68,68));
		icon.setMaximumSize(new Dimension(68,68));
		
		if (isSelected) {
            if (selectedBorder == null) {
                selectedBorder = BorderFactory.createLineBorder(new Color(212,212,212));
            }
            setBorder(selectedBorder);
            setBackground(new Color(235,235,235));
        } else {
            if (unselectedBorder == null) {
                unselectedBorder = BorderFactory.createLineBorder(new Color(222,222,222));
            }
            setBorder(unselectedBorder);
            setBackground(Color.white);
        }
		
		setToolTipText(avatar.getAlias()+"... lol");
		
		return this;
	}
	
	/**
	 * Fetches the icon for this client if it can.
	 * 1) Attempt to load it from the memory cache.
	 * 2) Attempt to load it from the disk cache.
	 * 3) Attempt to get it from the indexnode and cache it.
	 * 4) if all that failed then return the default avatar.
	 * @param cg
	 * @return
	 */
	private Icon getAvatarForClient(IndexNodeClient c) {
		
		if (c.getCachedAvatar()!=null || loadFromDisk(c) || loadFromIndexnode(c) || loadDefault(c)) {
			return new ImageIcon(c.getCachedAvatar());
		}
		
		return null; //this shouldn't ever happen as load default should always work.
	}

	/**
	 * Attempts to load in a cached icon from disk.
	 */
	private boolean loadFromDisk(IndexNodeClient client) {
		try {
			File onDiskIcon = getIconOnDisk(client.getAvatarhash());
			if (!onDiskIcon.isFile()) return false; //if it's not on disk no point trying to load it.
			BufferedImage im = ImageIO.read(onDiskIcon);
			if (im==null) return false;
			client.setCachedAvatar(im);
			return true;
		} catch (IOException e) {
			Logger.warn("Couldn't load cached image icon from disk: "+e);
			e.printStackTrace();
			return false;
		}
	}

	private File getIconOnDisk(String avatarhash) {
		File onDiskIcon = Platform.getPlatformFile("avatars"+File.separator+avatarhash+".png");
		return onDiskIcon;
	}
	
	private boolean loadFromIndexnode(IndexNodeClient client) {
		try {
			if (client.getAvatarhash().length()!=32) return false; //don't try to get from the indexnode unless it looks plausible.
			Util.writeStreamToFile(client.getIconStreamFromIndexNode(), getIconOnDisk(client.getAvatarhash()));
			//so, we've put it into the filesystem now try to load it:
			return loadFromDisk(client);
		} catch (IOException e) {
			Logger.warn("Unable to save an avatar from the indexnode to disk: "+e);
			//e.printStackTrace();
		}
		
		return false;
	}
	
	boolean loadDefault(IndexNodeClient client) {
		client.setCachedAvatar(frame.getGui().getUtil().getBufferedImageFullname("defaultavatar.png"));
		return true;
	}
}
