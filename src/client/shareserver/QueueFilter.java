package client.shareserver;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Date;
import java.util.Random;

import org.w3c.dom.Document;
import org.w3c.dom.Element;


import common.httpserver.Filter;
import common.httpserver.HttpExchange;

import common.FS2Constants;
import common.HttpUtil;
import common.Sxml;

/**
 * Facilitates queueing of uploads in a way that both browsers and FS2 clients will understand.
 * 
 * It acheives queuing by allocating a token to a client that can be used to reserve a slot.
 * If a client does not supply a token-cookie it will be allocated one, This will only be used however if they re-request
 * using this token. This is so that non-cookie clients do not fill the queue with useless tokens.
 * Non-cookie clients may only
 * download from us if there is enough free resources to jump the queue without disadvantaging other clients.
 * @author gary
 *
 */
public class QueueFilter extends Filter {
	private Random gen = new Random();
	private TimedQueue<Long> tq;
	
	/** We use Long objects as tokens */
	public QueueFilter(TimedQueue<Long> tq) {
		this.tq = tq;
	}
	
	@Override
	public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
		try {
			String clientToken = HttpUtil.getCookie(exchange, "fs2-token");
			if (clientToken == "") {
				long newToken = gen.nextLong();
				//Establish the new cookie (containing the token) on the client:
				exchange.getResponseHeaders().add("Set-Cookie", "fs2-token="+Long.toString(newToken)+"; path=/");
				if (tq.takeResourceWithoutQueueing(newToken)) {
					allowClientToContinue(exchange, chain, newToken);
				} else {
					queueClient(exchange, newToken);
				}
			} else {
				Long token = 0L;
				try {
					token = Long.parseLong(clientToken);
				} catch (NumberFormatException e) {
					HttpUtil.simpleResponse(exchange, "Bad token specification.", 400);
					return;
				}
				if (tq.takeResource(token)) {
					allowClientToContinue(exchange, chain, token);
				} else {
					queueClient(exchange, token);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private void allowClientToContinue(HttpExchange exchange, Chain chain, Long token) throws IOException {
		try {
			chain.doFilter(exchange);
		} finally {
			tq.freeResource(token);
			exchange.close();
		}
	}

	private void queueClient(HttpExchange exchange, Long token) {
		try {
			Sxml xml = new Sxml();
			xml.setStandalone(true);
			xml.setDoctypeSystem("http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd");
			xml.setDoctypePublic("-//W3C/DTD XHTML 1.0 Strict//EN");
			Document doc = xml.getDocument();
			Element html = doc.createElement("html");
			doc.appendChild(html);
			html.setAttribute("xmlns", "http://www.w3.org/1999/xhtml");
			html.setAttribute("xml:lang", "en");
			html.setAttribute("lang", "en");
			Element head = doc.createElement("head");
			html.appendChild(head);
			Element meta = doc.createElement("meta");
			head.appendChild(meta);
			meta.setAttribute("http-equiv", "refresh");
			int refreshInterval = FS2Constants.BROWSER_META_REFRESH_INTERVAL;
			meta.setAttribute("content", Integer.toString(refreshInterval));
			Element title = doc.createElement("title");
			head.appendChild(title);
			title.setTextContent("You are in a queue to download "+HttpUtil.pathBasename(exchange.getRequestURI().getPath()));
			Element htmlbody = doc.createElement("body");
			html.appendChild(htmlbody);
			Element header = doc.createElement("div");
			html.appendChild(header);
			header.setAttribute("id", "fs2-header");
			Element body = doc.createElement("div");
			body.setAttribute("id", "fs2-queued");
			html.appendChild(body);
			int positionInQueue = tq.positionInQueue(token)+1;
			body.setAttribute("fs2-queueindex", Integer.toString(positionInQueue));
			body.setAttribute("fs2-queuesize", Integer.toString(tq.queueSize()));
			if (positionInQueue == -1) {
				body.setTextContent("(This page refreshes every "+refreshInterval+" seconds) There are no free slots to download. You are not in the queue yet. Try enabling cookies if you repeatedly see this message.");
			} else {
				body.setTextContent("(This page refreshes every "+refreshInterval+" seconds) You're in the queue at position "+Integer.toString(positionInQueue)+" of "+Integer.toString(tq.queueSize()));
			}
			Element footer = doc.createElement("div");
			html.appendChild(footer);
			footer.setAttribute("id", "fs2-footer");
			footer.appendChild(doc.createElement("hr"));
			footer.appendChild(doc.createTextNode(FS2Constants.FS2_PROTOCOL_VERSION+" client at "+InetAddress.getLocalHost()+" on "+new Date()));
			
			HttpUtil.simpleResponse(exchange, xml.toString(), 503);
			
		} catch (Exception e) {
			e.printStackTrace();
			try {
				HttpUtil.simpleResponse(exchange, "Internal exception generating a pretty \"you're queued\" page.", 500);
			} catch (Exception ohno) {
				ohno.printStackTrace();
			}
		}
	}
	
	
}
