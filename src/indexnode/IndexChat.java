package indexnode;

import indexnode.IndexNode.Client;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;

import common.httpserver.HttpExchange;
import common.httpserver.HttpHandler;

import common.ChatMessage;
import common.HttpUtil;
import common.Logger;

public class IndexChat implements HttpHandler {
	ChatDatabase data;
	IndexNode node;
	
	public IndexChat(ChatDatabase chat, IndexNode node) {
		this.data = chat;
		this.node = node;
	}
	
	@Override
	public void handle(HttpExchange exchange) throws IOException {
		try {
			IndexTemplate template = new IndexTemplate(exchange);
			template.setTitle("FS2 chat");
			
			HashMap<String, String> args = HttpUtil.getArguments(exchange);
			if (args.containsKey("say")) {
				Client cl = node.getClientFromExchange(exchange);
				if (cl==null) {
					//Only registered clients may chat.
					HttpUtil.simple403(exchange);
					return;
				}
				//this is a message/command request
				ChatMessage res = data.processMessage(cl, args.get("say"));
				if (res!=null) template.addMessageResult(res);
			} 
			
			//always view the chatlog:
			int fromId = -1;
			if (args.containsKey("lastmessage")) fromId = Integer.parseInt(args.get("lastmessage"));
			LinkedList<ChatMessage> items = data.getMessages(fromId);
			template.addChatItems(items);
			template.setChatRefresh((items.isEmpty() ? fromId : items.getLast().id));
			
			template.sendToClient(exchange);
			
		} catch (Exception e) {
			Logger.warn("Exception handling chat request:"+e);
			e.printStackTrace();
			HttpUtil.simpleResponse(exchange, "Your request couldn't be handled due to an internal exception.", 500);
		}
		
	}
}
