package client.indexnode;

import java.util.LinkedList;

import common.ChatMessage;

/**
 * The interface that UI elements that can recieve chat messages must implement.
 * @author gary
 *
 */
public interface ChatListener {
	/**
	 * This will be called when there are new messages from the indexnode.
	 * @param messages
	 */
	void newMessages(LinkedList<ChatMessage> messages);
	
	/**
	 * This will be called when a message has been sent to the indexnode.
	 * @param result A special chat message:
	 * if the id of the chat message is -1 then the result.message is the return value of a command.
	 * otherwise the result.message will be the message that was sent (verbatim, same string object)
	 *   and the id field represents the message id of the newly submitted message.
	 */
	void messageReturn(ChatMessage result);
}
