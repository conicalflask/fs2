package indexnode;

import indexnode.IndexNode.Client;

/**
 * An object implementing this interface can register itself to recieve commands from chat users.
 * @author gary
 */
public interface ChatCommandable {
	/**
	 * Issued when a command registered for this object has arrived.
	 * @param chat the chat database that is issueing this command.
	 * @param cl The client that caused the command.
	 * @param command The command string
	 * @param args the arguments to the command
	 * @return A string to return only to the client that issued the command.
	 */
	String doCommand(ChatDatabase chat, Client cl, String command, String args);
}
