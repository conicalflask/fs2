package client.gui;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedList;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.filechooser.FileNameExtensionFilter;

import common.FS2Constants;
import common.Logger;
import common.Util;

import client.gui.chat.NodeChatTab;
import client.indexnode.IndexNode;
import client.indexnode.IndexNodeCommunicator;
import client.platform.Platform;
import client.platform.ClientConfigDefaults.CK;

/**
 * A simple chat feature.  
 * @author jimmy
 *
 */
@SuppressWarnings("serial")
public class ChatTab extends TabItem implements TableModelListener {

	private String noobTabName = "Not Connected";
	
	public ChatTab(JTabbedPane pane, MainFrame frame) {
		super(pane, frame, "Chat", FS2Tab.CHAT, frame.gui.util.getImage("chat"));
		
		comm.addTableModelListener(this);

		setLayout(new BorderLayout());
		
		add(createPreferencesPane(), BorderLayout.PAGE_START);
		add(createChatPane());
	}
	JButton avatarButton;
	JCheckBox displayNotificationsCheck;
	private Boolean displayNotifications = false;
	public Boolean displayChatNotifications(){
		return displayNotifications;
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
								frame.gui.ssvr.getIndexNodeCommunicator().setAvatarFile(avatarCache);
							
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
	
	private JPanel createPreferencesPane() {
		JPanel preferencesPane = new JPanel(new BorderLayout());
		
		ImageIcon ico = null;
		File avatarFile = frame.gui.ssvr.getIndexNodeCommunicator().getAvatarFile();
		if (avatarFile.isFile()) {
			try {
				ico = new ImageIcon(ImageIO.read(avatarFile));
			} catch (IOException e) {
				Logger.warn("Avatar "+avatarFile.getPath()+" couldn't be loaded from disk: "+ e);
				ico = frame.gui.util.getImage("defaultavatar");
			}
		} else {
			ico = frame.gui.util.getImage("defaultavatar");
		}
		
		avatarButton = new JButton("Select avatar", ico); //TODO: get current avatar from the indexnode communicator.
		avatarButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				setIcon();
			}
		});
		
		displayNotificationsCheck = new JCheckBox("Display chat notifications");
		displayNotificationsCheck.addActionListener(new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent e) {
				if (displayNotificationsCheck.isSelected()) {
					displayNotifications = true;
				} else{
					displayNotifications = false;
				}
				frame.getGui().getConf().putBoolean(CK.DISPLAY_CHAT_NOTIFICATIONS, displayNotifications);
			}
		});
		
		displayNotifications = frame.getGui().getConf().getBoolean(CK.DISPLAY_CHAT_NOTIFICATIONS);
		displayNotificationsCheck.setSelected(displayNotifications);
		
		preferencesPane.add(avatarButton, BorderLayout.LINE_START);  //FIXME: this is fugly!
		preferencesPane.add(displayNotificationsCheck, BorderLayout.LINE_END);
		
		
		return preferencesPane;
	}
	
	JTabbedPane chatTabs;
	IndexNodeCommunicator comm = frame.gui.ssvr.getIndexNodeCommunicator();
	LinkedList<NodeChatTab> nodeTabs = new LinkedList<NodeChatTab>();
	
	private JTabbedPane createChatPane(){
		chatTabs = new JTabbedPane();;
		
		ArrayList<IndexNode> connectedIndexNodes = comm.getRegisteredIndexNodes(); 
		
		if (connectedIndexNodes.isEmpty()){
			addNotConnectedTab();
			return chatTabs;
		}
		
		for (IndexNode i : connectedIndexNodes){
			NodeChatTab n = new NodeChatTab(frame, i, this);
			nodeTabs.add(n); // Keep a record of all open tabs for reference when updating
			i.registerChatListener(n);
			chatTabs.addTab(i.getName(), n);
		}
		
		return chatTabs;
	}

	/**
	 * Catch any index nodes shutting down or turning on and create/enable/disable chat tabs accordingly.
	 */
	@Override
	public void tableChanged(TableModelEvent e) {
		int row;
		ArrayList<IndexNode> indexNodes = comm.getRegisteredIndexNodes();
		NodeChatTab tab;
		IndexNode node;
		
		switch (e.getType()){
			case TableModelEvent.INSERT:
				row = e.getFirstRow();
				
				node = indexNodes.get(row);
				
				// Check this hasn't already been found and the user had previously deleted the node
				tab = getTabByIndexNode(node);
				
				if (tab==null){
					tab = createNewChatTab(node);
				}else{
					tab.reactivate();
				}
				
				// Get rid of the noob tab
				int noobTabIndex = chatTabs.indexOfTab(noobTabName);
				if (noobTabIndex != -1) chatTabs.removeTabAt(noobTabIndex);
				
				break;
			case TableModelEvent.UPDATE:
				row = e.getFirstRow();
				
				if (row > -1){
					node = indexNodes.get(row);
					
					tab = getTabByIndexNode(node);
					if (row < chatTabs.getTabCount() && row < nodeTabs.size()){
						// Rename
						String oldTabName = chatTabs.getTitleAt(row); // Dangerous - assumes that the indices match (they should though)
						chatTabs.setTitleAt(chatTabs.indexOfTab(oldTabName), node.getName());
						
						tab = nodeTabs.get(row);
						if (node.isWritable()) {
							// Only reactivate if we need to, otherwise we keep wiping what the user is typing
							if (!tab.isActive()) tab.reactivate();
						} else if (!node.isReadable()){
							tab.deactivate();
						}
					}else{
						// Create a new chat tab
						createNewChatTab(node);
					}
				}
				break;
			case TableModelEvent.DELETE: 
				// Delete the chat tab
				row = e.getFirstRow();
				if (row > -1){ 
					nodeTabs.remove(row);
					chatTabs.remove(row);
					addNotConnectedTab();
				}
				break;
			default:
				// Do nothing
				break;
		}
	}
	
	private NodeChatTab createNewChatTab(IndexNode node){
		NodeChatTab tab = new NodeChatTab(frame, node, this);
		
		nodeTabs.add(tab);
		node.registerChatListener(tab);
		chatTabs.addTab(node.getName(),tab);
		
		return tab;
	}
	
	/**
	 * Searches the list of know index nodes for the specified node and returns the related tab object if it exists, otherwise
	 * returns null
	 * @param node
	 * @return
	 */
	private NodeChatTab getTabByIndexNode(IndexNode node){
		for (NodeChatTab n : nodeTabs){
			// As the names differ, and the objects will be different, test the URI path
			if (n.getIndexNode().getLocation().getPath().equals(node.getLocation().getPath())) 
				return n;
		}
		return null;
	}
	
	public void activateChatTab(NodeChatTab toActivate) {
		int idx = 0;
		for (NodeChatTab tab : nodeTabs) {
			if (tab!=toActivate) {
				idx++;
			} else {
				break;
			}
		}
		chatTabs.setSelectedIndex(idx);
	}
	
	public int getUnreadMessageCount() {
		if (isActiveTab()) {
			markAllMessagesRead();
			return 0;
		} else {
			int acc = 0;
			for (NodeChatTab nt : nodeTabs) {
				acc+=nt.getUnreadMessageCount();
			}
			return acc;
		}
	}
	
	private void markAllMessagesRead() {
		for (NodeChatTab nt : nodeTabs) {
			nt.markMessagesRead();
		}
	}
	
	private void addNotConnectedTab(){
		chatTabs.addTab(noobTabName, new JLabel("<HTML><p>You're not connected to any index nodes. Once you connect to an index node, a chat panel will appear here. You have one chat panel per index node.</p></HTML>"));
	}
}
