package client.gui.chat;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JTextArea;
import javax.swing.JTextPane;

import client.indexnode.IndexNode;

public class SendMessageListener implements ActionListener {
//	final static String newLine = "\n";
//	final static String prompt = ">";
//	final static String[] bannedWords = {"fuck","shit","cunt","piss","cock","wanker"};
//	
//	IndexNode indexNode;
//	JTextPane chatBox;
//	JTextArea messageBox;
//	StyledDocument doc;


	// Highlighters
//	BannedWordsHighlightPainter bannedWordsPainter = new BannedWordsHighlightPainter(
//			Color.red);

	public SendMessageListener(IndexNode indexNode, JTextPane chatBox, JTextArea messageBox) {
//		this.indexNode = indexNode;
//		this.chatBox = chatBox;
//		this.messageBox = messageBox;
//		this.doc = (StyledDocument)chatBox.getDocument();
	}

	@Override
	public void actionPerformed(ActionEvent e) {
//		String sender = "jimmy";
//		String m = messageBox.getText() + newLine;
//		try {
//			doc.insertString(doc.getLength(), sender+prompt, ChatDocument.chatName);
//			doc.insertString(doc.getLength(), m, null);
//		} catch (BadLocationException e1) {
//			// TODO Auto-generated catch block
//			e1.printStackTrace();
//		}
//		messageBox.setText("");
////		highlightChat();
	}

//	private void highlightChat() {
//		Highlighter high = chatBox.getHighlighter();
//		Document doc = chatBox.getDocument();
//		String text;
//		try {
//			text = doc.getText(0, doc.getLength());
//			
//			// 1. Highlight any swear words
//			int pos = 0;
//
//			for (String swear : bannedWords){
//				while ((pos = text.indexOf(swear, pos)) >= 0) {
//					// Create highlighter using private painter and apply around pattern
//					high.addHighlight(pos, pos + swear.length(), bannedWordsPainter);
//	
//					pos += swear.length();
//				}
//			}
//			
//			// 2. Convert any tokens
//			while ((pos = text.indexOf("/", pos)) >= 0){
//				int nextPos = text.indexOf("/", pos);
//				if (nextPos < 0)
//					break;
//				
//				pos += nextPos;//FIXME
//			}
//			
//		} catch (BadLocationException e1) {
//			// TODO Auto-generated catch block
//			e1.printStackTrace();
//		}
//
//	}
//
//	class BannedWordsHighlightPainter extends DefaultHighlightPainter {
//
//		public BannedWordsHighlightPainter(Color color) {
//			super(color);
//		}
//
//	}

}
