package indexnode;

import indexnode.IndexNode.Client;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import common.ChatMessage;
import common.FS2Constants;

/**
 * Provides a very simple database for chat.
 * It enables many httpcontexts to share a single chat repository and not care about synchronisation.
 * 
 * It also enables other indexnode components to register command handlers so that they may respond to special command messages of the form:
 * /{command} {args}
 * 
 * @author gary
 *
 */
public class ChatDatabase {
	
	static final Pattern commandRecogniser = Pattern.compile("^/(.+?)(\\s+(.*))?$");
	
	//Master chat log (sent to everybody):
	HashMap<Integer, String> log = new HashMap<Integer, String>();
	private int oldestId=0;
	private int nextId=0;
	
	public ChatDatabase() {
		registerCommandable("me", new MeCommand());
	}
	
	HashMap<String, ChatCommandable> commandHandlers = new HashMap<String, ChatCommandable>();
	
	synchronized void registerCommandable(String command, ChatCommandable handler) {
		commandHandlers.put(command, handler);
	}
	
	synchronized void deregisterCommandable(String command) {
		commandHandlers.remove(command);
	}
	
	/**
	 * Processes a new message from the client, processing the command if it contained one.
	 * @param cl The client this message originated from.
	 * @param message The message/command the client has sent.
	 * @return A ChatMessage object. If the id is -1 then this was a command and the message is the result of the command.
	 * Otherwise the message can be ignored, the id is the index of the message inserted into the log.
	 */
	public synchronized ChatMessage processMessage(Client cl, String message) {
		Matcher m = commandRecogniser.matcher(message);
		if (m.matches()) {
			String command = m.group(1);
			String args = m.group(3);
			//this message was a command!
			if (commandHandlers.containsKey(command)) {
				return new ChatMessage(commandHandlers.get(command).doCommand(this, cl, command, args),-1);
			} else {
				return new ChatMessage("Unknown command '"+command+"'",-1);
			}
		} else {
			//just a normal message, so queue it:
			putMessage(cl.getAlias()+": "+message);
			pruneMaster();
			return new ChatMessage("", nextId-1);
		}
	}
	
	/**
	 * put a plain message into the log.
	 * @param message
	 */
	synchronized void putMessage(String message) {
		log.put(nextId++, message);
		pruneMaster();
	}
	
	/**
	 * gets all the messages in the log from {fromId}+1 until the most recent message.
	 * @param fromId The id of the last message recieved by the caller.
	 * @return A list of strings, in order from oldest to most recent. They are indexed {fromId}+1 to the most recent id.
	 */
	synchronized LinkedList<ChatMessage> getMessages(int fromId) {
		LinkedList<ChatMessage> ret = new LinkedList<ChatMessage>();
		
		if (fromId>nextId) fromId = 0;
		if (fromId<oldestId) fromId = oldestId-1;
		
		
		for (int i=fromId+1; i<nextId; i++) {
			ret.add(new ChatMessage(log.get(i),i));
		}
		
		return ret;
	}
	
	/**
	 * Culls messages from the log if there are too many
	 */
	synchronized void pruneMaster() {
		while (log.size()>FS2Constants.INDEXNODE_CHAT_LOG_LENGTH) {
			log.remove(oldestId++);
		}
	}
	
}
