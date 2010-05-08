package client.gui.chat;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.LinkedList;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;

import client.gui.ChatTab;
import client.gui.JTextFieldLimit;
import client.gui.MainFrame;
import client.gui.Toaster;
import client.indexnode.ChatListener;
import client.indexnode.IndexNode;

import common.ChatMessage;

@SuppressWarnings("serial")
public class NodeChatTab extends JPanel implements MouseListener, ChatListener, ActionListener, KeyListener {
	IndexNode indexNode;
	MainFrame frame;
	boolean active;
	ChatTab owner;
	Toaster toaster;
	
	public NodeChatTab(MainFrame frame, IndexNode indexNode, ChatTab owner){
		this.frame = frame;
		this.indexNode = indexNode;
		this.owner = owner;
		
		setupTab();
		
		// Allow for notifications
		toaster = new Toaster(frame, this);
	}
	
	public ChatTab getOwner(){
		return owner;
	}
	
	JTextPane chatLog;
	JScrollPane chatLogScrollPane;
	JTextArea message;
	JScrollPane messageScrollPane;
	JButton sendMessage;
	JPanel chatMessagePanel;
	JSplitPane chatPane;
	JTable avatarTable;
	JScrollPane avatarScrollPane;
	
	private void setupTab(){
		
		// The chat area
		BorderLayout chatLayout = new BorderLayout();
		JPanel chatWrapper = new JPanel(chatLayout);
		
		chatLog = new JTextPane();
		chatLog.setEditable(false);
		chatLog.setDocument(new ChatDocument());
		chatLogScrollPane = new JScrollPane(chatLog);
		chatLogScrollPane.addMouseListener(this);
		
		chatWrapper.add(chatLogScrollPane, BorderLayout.CENTER);
		
		// The send message panel
		message = new JTextArea();
		message.addMouseListener(this);
		message.addKeyListener(this);
		messageScrollPane = new JScrollPane(message);
		message.setLineWrap(true);
		message.setWrapStyleWord(true);
		message.requestFocus();
		message.setDocument(new JTextFieldLimit(512));
		
		sendMessage = new JButton("Send", frame.getGui().getUtil().getImage("sendmessage"));
		sendMessage.addMouseListener(this);
		sendMessage.addActionListener(this);
		
		chatMessagePanel = new JPanel();
		chatMessagePanel.setLayout(new BoxLayout(chatMessagePanel, BoxLayout.X_AXIS));
		chatMessagePanel.add(messageScrollPane);
		chatMessagePanel.add(sendMessage);
		
		chatWrapper.add(chatMessagePanel, BorderLayout.PAGE_END); 
		
		// Setup the avatars
		avatarTable = new JTable(new AvatarTableModel(indexNode));
		avatarTable.addMouseListener(this);
		avatarTable.setTableHeader(null);
		avatarTable.setDefaultRenderer(Object.class, new AvatarRenderer(frame));
		avatarTable.setRowHeight(70);
		avatarScrollPane = new JScrollPane(avatarTable);
		avatarScrollPane.setMaximumSize(new Dimension(200,-1));
		avatarScrollPane.setPreferredSize(new Dimension(200,-1));
		
		// Add all to parent
		BorderLayout pageLayout = new BorderLayout();
		
		this.setLayout(pageLayout);
		this.add(avatarScrollPane, BorderLayout.LINE_END);
		this.add(chatWrapper, BorderLayout.CENTER);
		
		// Set the status
		active = true;
	}
	
	public boolean isActive(){
		return active;
	}
	
	public IndexNode getIndexNode() {
		return indexNode;
	}	

	@Override
	public void mouseClicked(MouseEvent e) {}

	@Override
	public void mouseEntered(MouseEvent e) {
		if (e.getSource()==message) {
			frame.setStatusHint("Enter your message here");
		} else if (e.getSource()==sendMessage) {
			frame.setStatusHint("Click to send your message");
		} else if (e.getSource()==avatarTable) {
			frame.setStatusHint("Lists everyone connected to the index node");
		}		
	}

	@Override
	public void mouseExited(MouseEvent e) {}

	@Override
	public void mousePressed(MouseEvent e) {}

	@Override
	public void mouseReleased(MouseEvent e) {}

	@Override
	public void messageReturn(ChatMessage result) {
		if (result.id==-1){
			ChatDocument doc = (ChatDocument)chatLog.getDocument();
			
			try {
				if (result.message.length() > 0){
					doc.insertCommandResponse(doc.getLength(), /*"You requested some information: \n" +*/ result.message + "\n");
					chatLogScrollPane.getVerticalScrollBar().setValue(chatLogScrollPane.getVerticalScrollBar().getMaximum());
				}
			} catch (BadLocationException e) {
				e.printStackTrace();
			}
		}
	}
	
	private int missedMessageCount = 0;
	
	@Override
	public void newMessages(LinkedList<ChatMessage> messages) {
		ChatDocument doc = (ChatDocument)chatLog.getDocument();
		
		for (ChatMessage m : messages){
			if (m.id!=-1){ // Only print non-commands
				try {
					doc.insertChatMessage(doc.getLength(), m.message +"\n", indexNode.getStats().getPeers().values());

					if (!owner.isActiveTab()){
						missedMessageCount++;
					} else{
						missedMessageCount = 0;
					}
					
					if (owner.displayChatNotifications()){
						// Only show messages if we're not focused on the tab

						// Currently pops up messages intended for the user only
						// If we don't trim the username out of the string then it will pop up every time the user sends a message	
//						if (m.message.replaceFirst(".*:", "").contains(frame.getGui().getShareServer().getAlias())){
							toaster.showToaster(frame.getGui().getUtil().getImageFullname("chat.png"), indexNode.getName(), m.message);//"There are "+missedMessageCount+" unread chat messages.");
//						}
						
						//toaster.showToaster(m.message);
					}
					
					chatLogScrollPane.getVerticalScrollBar().setValue(chatLogScrollPane.getVerticalScrollBar().getMaximum());
				} catch (BadLocationException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public void reactivate() {
		// Need to re-register incase the index node has been rebooted
		indexNode.registerChatListener(this);
		
		message.setEnabled(true);
		message.setText("");
		message.requestFocus();
		sendMessage.setEnabled(true);
		avatarTable.setEnabled(true);
		chatLog.setBackground(Color.white);
		
		// Set status
		active = true;
	}

	public void deactivate() {
		indexNode.deregisterChatListenter(this);
		
		message.setEnabled(false);
		message.setText("You are no longer connected to this index node.  No chats for you.");
		sendMessage.setEnabled(false);
		avatarTable.setEnabled(false);
		chatLog.setBackground(Color.lightGray);
		
		// Set status
		active = false;
	}
	
	/**
	 * Send the message to the index node
	 */
	@Override
	public void actionPerformed(ActionEvent e) {
		String messageToSend = message.getText();
		messageToSend = messageToSend.replace("\n", "");
		
		// send the message
		indexNode.sendMessage(messageToSend);	
		
		// Clear the message from the textbox
		message.setText("");
		message.requestFocus();
	}

	@Override
	public void keyPressed(KeyEvent e) { }

	@Override
	public void keyReleased(KeyEvent e) { }

	@Override
	public void keyTyped(KeyEvent e) {
		// Allow the user to send a message using the Enter key
		if (e.getKeyChar() == '\n'){
			actionPerformed(null);
		}
	}

	public int getUnreadMessageCount() {
		return missedMessageCount;
	}

	public void markMessagesRead() {
		missedMessageCount=0;
	}
}
