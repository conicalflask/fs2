package client.gui.chat;

import java.awt.Color;
import java.util.Collection;

import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;

import client.indexnode.IndexNodeStats.IndexNodeClient;

@SuppressWarnings("serial")
public class ChatDocument extends DefaultStyledDocument {

	private DefaultStyledDocument doc;	

	static MutableAttributeSet chatMessage;
	static MutableAttributeSet commandMessage;
	static MutableAttributeSet aliasHighlight;

	public ChatDocument(){
		doc = this;
		putProperty(DefaultEditorKit.EndOfLineStringProperty, "\n");

		commandMessage = new SimpleAttributeSet();
		chatMessage = new SimpleAttributeSet();
		aliasHighlight = new SimpleAttributeSet();
		StyleConstants.setBackground(commandMessage, new Color(205, 255, 205));
		StyleConstants.setFontSize(commandMessage, 12);
		StyleConstants.setFontFamily(commandMessage, "monospaced");
		
		StyleConstants.setFontFamily(chatMessage, "sansserif");
		StyleConstants.setFontSize(chatMessage, 12);
		
		StyleConstants.setFontFamily(aliasHighlight, "sansserif");
		StyleConstants.setFontSize(aliasHighlight, 12);
		StyleConstants.setBold(aliasHighlight, true);
	}
	
	/**
	 * Inserts a chat onto the panel, and highlights the alias bold.
	 */
	public void insertChatMessage(int offset, String str, Collection<IndexNodeClient> aliases) throws BadLocationException{
		super.insertString(offset, str, chatMessage);
		
		for (IndexNodeClient a : aliases){
			String alias = a.getAlias() + ":";
			if (str.indexOf(alias) > -1){
				doc.setCharacterAttributes(offset, alias.length(), aliasHighlight, false);
				
				// Update the offset and string
				offset += alias.length();
				str.replaceFirst(alias, "");
				break;
			}
		}
		
		doc.setCharacterAttributes(offset, str.length(), chatMessage, false);
	}
	
	public void insertCommandResponse(int offset, String str) throws BadLocationException{
		super.insertString(offset, str, commandMessage);
		
		doc.setCharacterAttributes(offset, str.length(), commandMessage, false);
	}
}
