package indexnode;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;

import javax.xml.transform.TransformerException;

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import common.httpserver.HttpExchange;

import common.ChatMessage;
import common.FS2Constants;
import common.HttpUtil;
import common.Sxml;
import common.Util;
import common.Sxml.SXMLException;

/**
 * An output template for the indexnode.
 * @author gary
 */
public class IndexTemplate {
	private Sxml xml;
	Document doc;
	private Element title;
	private Element header;
	Element body;
	private Element footer;
	private String linkBase;
	private Element generationTime;
	private Date startedGeneration = new Date();
	private SimpleDateFormat dateFormat = new SimpleDateFormat("s.SSS");
	
	public IndexTemplate(HttpExchange exchange) throws SXMLException {
		this();
		linkBase = HttpUtil.getClientURLToServerRoot(exchange);
		footer.appendChild(doc.createTextNode(FS2Constants.FS2_PROTOCOL_VERSION+" at "+linkBase+" on "+new Date()));
	}
	
	public IndexTemplate() throws SXMLException {
		//Setup the document that will be output:
		xml = new Sxml();
		xml.setStandalone(true);
		xml.setDoctypeSystem("http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd");
		xml.setDoctypePublic("-//W3C/DTD XHTML 1.0 Strict//EN");
		doc = xml.getDocument();
		Element html = doc.createElement("html");
		doc.appendChild(html);
		html.setAttribute("xmlns", "http://www.w3.org/1999/xhtml");
		html.setAttribute("xml:lang", "en");
		html.setAttribute("lang", "en");
		Element head = doc.createElement("head");
		html.appendChild(head);
		title = doc.createElement("title");
		head.appendChild(title);
		title.setTextContent("IndexNode Template Title");
		Element htmlbody = doc.createElement("body");
		html.appendChild(htmlbody);
		header = doc.createElement("div");
		htmlbody.appendChild(header);
		header.setAttribute("id", "fs2-header");
		body = doc.createElement("div");
		htmlbody.appendChild(body);
		footer = doc.createElement("div");
		htmlbody.appendChild(footer);
		footer.setAttribute("id", "fs2-footer");
		footer.appendChild(doc.createElement("hr"));
		//Find the link base:
		generationTime = doc.createElement("span");
		footer.appendChild(generationTime);
	}
	
	public void setSimpleFooter(String text) {
		footer.setTextContent(text);
	}
	
	public void setTitle(String newTitle) {
		title.setTextContent(newTitle);
	}
	
	public void setSimpleHeader(String text){
		header.setTextContent(text);
	}
	
	public Element getHeaderElement(){
		return header;
	}
	
	public void setFileHeader(FilesystemEntry entry) throws SQLException, UnsupportedEncodingException {
		String name = entry.getName();
		if (entry.isRoot()) name = "Root of fs2";
		Element h3 = doc.createElement("h3");
		h3.setTextContent("Browsing directory: "+name);

		header.appendChild(h3);
		if (!entry.isRoot()) {
			Element t = doc.createElement("span");
			t.setTextContent("Direct URL to this: ");
			header.appendChild(t);
			Element link = linkToEntry(entry, false);
			header.appendChild(link);
			link.setTextContent(link.getAttribute("href"));
			header.appendChild(doc.createElement("br"));
			Element parentLink = linkToEntry(entry.getParent(), false);
			parentLink.setTextContent("(up one level)");
			header.appendChild(parentLink);
		}
		header.appendChild(doc.createElement("hr"));
		addSearchToHeader("");
	}
	
	/**
	 * returns a blank 'a' tag to a node.
	 * @param nodeID the ID of the node to link to.
	 * @param directURLs generate links to client machines directly
	 * @return
	 * @throws UnsupportedEncodingException  
	 * @throws DOMException 
	 */
	public Element linkToEntry(FilesystemEntry entry, boolean directURLs) throws UnsupportedEncodingException {
		Element retElem = doc.createElement("a");
		if (entry.isDirectory()) {
			String entryPath = entry.getPath(true, true);
			retElem.setAttribute("href", linkBase+"browse/"+entryPath);
			retElem.setAttribute("fs2-path", entryPath);
		} else {
			if (directURLs) {
				retElem.setAttribute("href", entry.getURL());
			} else {
				retElem.setAttribute("href", linkBase+"download/"+entry.getHash());
			}
		}
		return retElem;
	}
	
	/**
	 * Setup the header of the output for search results/search.
	 * @param currentQuery if this is empty then a blank search will be created. Otherwise it'll be in the context of results.
	 * @param results The number of results this search returned.
	 */
	public void setSearchHeader(String currentQuery, int results) {
		Element h3 = doc.createElement("h3");
		header.appendChild(h3);
		if (currentQuery == "") {
			h3.setTextContent("Search fs2:");
		} else {
			h3.setTextContent(Integer.toString(results)+" result"+(results != 1 ? "s" : "")+" containing \""+currentQuery+"\"");
		}
		addSearchToHeader(currentQuery);
	}
	
	public void addSearchToHeader(String currentQuery) {
		Element form = doc.createElement("form");
		form.setAttribute("action", linkBase+"search/");
		form.setAttribute("method", "get");
		header.appendChild(form);
		form.appendChild(doc.createTextNode("New search: "));
		Element input = doc.createElement("input");
		form.appendChild(input);
		input.setAttribute("name", "q");
		input.setAttribute("value", currentQuery);
		input.setAttribute("type", "text");
		Element submit = doc.createElement("input");
		form.appendChild(submit);
		submit.setAttribute("type", "submit");
		submit.setAttribute("value", "Search");
		header.appendChild(doc.createElement("hr"));
	}
	
	/**
	 * Append a fileList to the body of the page:
	 */
	public void generateFilelist(Collection<? extends FilesystemEntry> collection, boolean directDownload, boolean parentLinkForEach) throws DOMException, UnsupportedEncodingException, SQLException {
		generateFilelist(collection, directDownload, parentLinkForEach, body);
	}
	

	/**
	 * Adds the result of a command to a chat return.
	 * @param result
	 */
	public void addMessageResult(ChatMessage result) {
		Element cr = doc.createElement("div");
		body.appendChild(cr);
		cr.setAttribute("id", "fs2-chatresult");
		cr.setAttribute("fs2-chatindex", Integer.toString(result.id));
		cr.setTextContent(result.message);
	}
	
	public void setChatRefresh(int newLastId) {
		Element meta = doc.createElement("meta");
		header.appendChild(meta);
		meta.setAttribute("http-equiv", "refresh");
		int refreshInterval = FS2Constants.BROWSER_META_REFRESH_INTERVAL;
		meta.setAttribute("content", Integer.toString(refreshInterval)+";?lastmessage="+newLastId);
	}
	
	public void addChatItems(LinkedList<ChatMessage> items) {
		Element cr = doc.createElement("div");
		body.appendChild(cr);
		cr.setAttribute("id", "fs2-chatmessages");
		for (ChatMessage s : items) {
			Element ci = doc.createElement("div");
			cr.appendChild(ci);
			ci.setAttribute("fs2-chatindex", Integer.toString(s.id));
			ci.setTextContent(s.message);
		}
	}
	
	/**
	 * Generates a filelist in the section of the page supplied.
	 * 
	 * @param collection The files in the list
	 * @param directDownload Direct links to clients if this is true
	 * @param parentLinkForEach Each item has a link to its parents if this is true.
	 * @param inSection the section to add the list to.
	 * @throws UnsupportedEncodingException 
	 */
	public void generateFilelist(Collection<? extends FilesystemEntry> collection, boolean directDownload, boolean parentLinkForEach, Element inSection) throws UnsupportedEncodingException  {
		Element fl = doc.createElement("div");
		inSection.appendChild(fl);
		fl.setAttribute("id", "fs2-filelist");
		if (collection.size() > 0) {
			for (FilesystemEntry file : collection) {
				Element ForD = doc.createElement("b");
				fl.appendChild(ForD);
				Element newLink = linkToEntry(file, directDownload);
				if (file.isDirectory()) {
					newLink.setAttribute("fs2-type", "directory");
					newLink.setAttribute("fs2-linkcount", Integer.toString(file.getLinkCount()));
					ForD.setTextContent(">>");
					fl.appendChild(newLink);
				} else {
					newLink.setAttribute("fs2-type", "file");
					newLink.setAttribute("fs2-hash", file.getHash()); //mandatory for files
					newLink.setAttribute("fs2-clientalias", file.getOwnerAlias());
					int altsCount = file.getAlternatives().size();
					newLink.setAttribute("fs2-alternativescount", Integer.toString(altsCount));
					ForD.setTextContent("("+altsCount+")");
					fl.appendChild(newLink);
				}
				newLink.setAttribute("fs2-size", Long.toString(file.getSize()));
				newLink.setAttribute("fs2-name", file.getName());
				newLink.setTextContent(file.getName());
				Element info = doc.createElement("span");
				info.setTextContent(describeEntry(file));
				fl.appendChild(info);
				if (parentLinkForEach) {
					Element pLink = linkToEntry(file.getParent(), directDownload);
					pLink.setTextContent("(parent)");
					fl.appendChild(pLink);
				}
				fl.appendChild(doc.createElement("br"));
			}
		} else {
			fl.setTextContent("There is nothing to list.");
		}
	}
	
	/**
	 * Send this template to the client with a success code and close the connections.
	 * @param exchange
	 * @throws IOException
	 * @throws TransformerException
	 */
	public void sendToClient(HttpExchange exchange) throws IOException {
		HttpUtil.simpleResponse(exchange, toString(), 200);
	}
	
	public String toString() {
		String generationTime = ", generation time: " + dateFormat.format(new Date((new Date()).getTime() - startedGeneration.getTime())) +"s";
		this.generationTime.setTextContent(generationTime);
		try {
			return xml.generateString(true);
		} catch (Exception e) {
			e.printStackTrace();
			return "";
		}
	}
	
	public String describeEntry(FilesystemEntry entry) {
			return "("+Util.niceSize(entry.getSize())+")";
	}
}
