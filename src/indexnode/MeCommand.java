package indexnode;

import indexnode.IndexNode.Client;

public class MeCommand implements ChatCommandable {

	@Override
	public String doCommand(ChatDatabase chat, Client cl, String command, String args) {
		if (args!=null && !args.equals("")) chat.putMessage(cl.getAlias()+" "+args);
		return null;
	}

}
