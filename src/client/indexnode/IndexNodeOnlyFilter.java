package client.indexnode;

import java.io.IOException;
import java.util.LinkedList;

import common.httpserver.Filter;
import common.httpserver.HttpExchange;
import common.HttpUtil;
import common.Logger;

/**
 * Restricts an HttpContext so that only an IndexNode may access the content.
 * For non-indexnode accesses a 404 is presented.
 * 
 * Deciding if an access is from an indexnode is decided by the {@link IndexNode} class.
 * 
 * It always permits ping access if the connection was from a local loopback address
 * 
 * @author gary
 */
public class IndexNodeOnlyFilter extends Filter {

	private IndexNodeCommunicator comm;
	
	public IndexNodeOnlyFilter(IndexNodeCommunicator comm) {
		this.comm = comm;
	}
	
	@Override
	public void doFilter(HttpExchange ex, Chain chain) throws IOException {
		boolean canContinue = false;
		if (!ex.getRemoteAddress().getAddress().isLoopbackAddress()) {
			//We have to copy the nodes structure (without a lock!)
			//and hope this works because there is a very good chance that
			//whatever caused the indexnode to be poking us has a lock on the node list already
			//(and taking it here just causes deadlock). grumbles.
			LinkedList<IndexNode> nodes = new LinkedList<IndexNode>(comm.nodes);
			for (IndexNode node : nodes) {
				if (node.fromThisIndexnode(ex)) { //If an indexnode client accepts this indexnode then the request may be served.
					canContinue = true;
					break; //Do not continue now, as this would hold the lock for too long.
				}
			}
		} else canContinue=true;
		if (canContinue) {
			chain.doFilter(ex);
		} else {
			HttpUtil.simple404(ex);
			Logger.warn("Access to restricted resource attempted by: "+ex.getRemoteAddress());
		}
	}

}
