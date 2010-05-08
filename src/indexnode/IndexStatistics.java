/**
 * 
 */
package indexnode;

import indexnode.IndexNode.Client;
import indexnode.IndexNode.Share;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import common.FS2Constants;
import common.HttpUtil;
import common.Util;
import common.httpserver.HttpExchange;
import common.httpserver.HttpHandler;

/**
 * A fugly hacked together statistics page for the indexnode.
 * It's extremely expensive to generate so should not be generated often!
 * @author gp
 */
public class IndexStatistics implements HttpHandler {
	private final IndexNode onNode;
	private volatile long lastGenerated = 0;
	private String cachedStatsPage = "";
	private Document doc;
	private Element body;
	private IndexTemplate template;
	private volatile boolean generating = false;
	private Object genMutex = new Object();
	
	public IndexStatistics(IndexNode indexNode) {
		onNode = indexNode;
		generating = true;
		generateStatistics();
	}
	
	public void handle(HttpExchange exchange) throws IOException {
		synchronized (genMutex) {
			if ((System.currentTimeMillis()-lastGenerated > FS2Constants.INDEXNODE_CACHE_STATISTICS_DURATION) && !generating) {
				generating = true;
				generateStatistics();
			}
		}
		synchronized (cachedStatsPage) {
			HttpUtil.simpleResponse(exchange, cachedStatsPage, 200);
		}
	}
	
	public void generateStatistics() {
		Thread worker = new Thread(new Runnable() {
			
			@Override
			public void run() {
				try {
					template = new IndexTemplate();
					template.setTitle("FS2 statistics");
					doc = template.doc;
					body = template.body;
					Element heading = doc.createElement("h3");
					heading.setTextContent("Statistics");
					template.getHeaderElement().appendChild(heading);
					Element general = addSection("General", "general");
					Element memory = addSection("Memory Usage", "memory");
					//Uptime
					addStatistic("Indexnode started",IndexStatistics.this.onNode.startedDate.toString(),Long.toString(IndexStatistics.this.onNode.startedDate.getTime()), "indexnode-started", general);
					//Memory usage:
					addStatistic("Maximum heap",Util.niceSize(Runtime.getRuntime().maxMemory()), Long.toString(Runtime.getRuntime().maxMemory()) , "indexnode-maxheap", memory);
					addStatistic("Used heap",Util.niceSize(Runtime.getRuntime().totalMemory()), Long.toString(Runtime.getRuntime().totalMemory()) , "indexnode-usedheap", memory);
					addStatistic("Available heap",Util.niceSize(Runtime.getRuntime().freeMemory()), Long.toString(Runtime.getRuntime().freeMemory()) , "indexnode-freeheap", memory);
					
					
					//number of files
					int fileCount = IndexStatistics.this.onNode.fs.countFiles();
					addStatistic("Indexed files", Long.toString(fileCount),Long.toString(fileCount), "file-count", general);
					//number of unique files
					int uniqueCount = IndexStatistics.this.onNode.fs.countUniqueFiles();
					addStatistic("Unique files", Long.toString(uniqueCount),Long.toString(uniqueCount), "unique-file-count", general);
					//total size
					long totalSize = IndexStatistics.this.onNode.fs.totalSize();
					addStatistic("Total size", Util.niceSize(totalSize), Long.toString(totalSize), "total-size", general);
					//Total unique size
					long uniqueSize = IndexStatistics.this.onNode.fs.uniqueSize();
					addStatistic("Size of unique files", Util.niceSize(uniqueSize), Long.toString(uniqueSize), "total-unique-size", general);
					//Total estimated transfer
					long totalTransfer = IndexStatistics.this.onNode.fs.getEstimatedTransfer();
					addStatistic("Total bytes for all requested files", Util.niceSize(totalTransfer), Long.toString(totalTransfer), "total-transfer", general);
					
					
					//number of clients
					addStatistic("Connected clients", Integer.toString(IndexStatistics.this.onNode.clients.size()), Integer.toString(IndexStatistics.this.onNode.clients.size()),"client-count", general);
					//clients by sharesize,
					clientSizes(addSection("Clients", "clients"));
					//top ten popular files
					template.generateFilelist(IndexStatistics.this.onNode.fs.getPopularFiles(100), false, true, addSection("Most popular files", "popular-files"));
					synchronized (cachedStatsPage) {
						cachedStatsPage = template.toString();
					}
					lastGenerated = System.currentTimeMillis();
					
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					synchronized (genMutex) {
						generating = false;
					}
				}
			}
		});
		worker.setName("Statistics generation");
		worker.start();
	}
	
	public long getTotalClientSize(Client c) {
		long ret = 0;
		for (Share share : c.shares.values()) {
			ret+=share.getSize();
		}
		return ret;
	}
	
	//A comparator for descending sort of client's total share size.
	class ClientComparator implements Comparator<Client> {
		public int compare(Client o2, Client o1) {
			Long o1s = getTotalClientSize(o1);
			Long o2s = getTotalClientSize(o2);
			if (o1s == o2s) return 0;
			else if (o1s > o2s) return 1;
			else return -1;
		}
	}
	
	private LinkedList<Client> sortedClientsBySize() {
		LinkedList<Client> ret = new LinkedList<Client>(onNode.clients.values());
		Collections.sort(ret, new ClientComparator());
		return ret;
	}
	
	private void clientSizes(Element section) {
		int rank = 1;
		for (Client client : sortedClientsBySize()) {
			addClientStatistic(Integer.toString(rank++)+") "+client.getAlias(), Util.niceSize(getTotalClientSize(client)),Long.toString(getTotalClientSize(client)),"client-"+client.getAlias()+"-size", section, client.getAlias(), client.getAvatarHash());
		}
	}
	
	private void addClientStatistic(String name, String content, String machineReadableValue, String id, Element section, String alias, String avatarhash) {
		if (avatarhash!=null && !avatarhash.equals("")) {
			Element img = doc.createElement("img");
			img.setAttribute("src", "/avatars/"+avatarhash+".png");
			section.appendChild(img);
		}
		Element b = doc.createElement("b");
		b.setTextContent(name+": ");
		section.appendChild(b);
		Element c = doc.createElement("span");
		c.setTextContent(content);
		c.setAttribute("id", id);
		c.setAttribute("value", machineReadableValue);
		if (avatarhash!=null) c.setAttribute("fs2-avatarhash", avatarhash);
		if (alias!=null) c.setAttribute("fs2-clientalias", alias);
		section.appendChild(c);
		section.appendChild(doc.createElement("br"));
	}
	
	private void addStatistic(String name, String content, String machineReadableValue, String id, Element section) {
		addClientStatistic(name, content, machineReadableValue, id, section, null, null);
	}
	
	private Element addSection(String name, String id) {
		body.appendChild(doc.createElement("hr"));
		Element heading = doc.createElement("h4");
		heading.setTextContent(name);
		body.appendChild(heading);
		Element newSection = doc.createElement("div");
		body.appendChild(newSection);
		newSection.setAttribute("id", id);
		return newSection;
	}
}