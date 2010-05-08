package client.shareserver;

import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * Implements a queue where each client in the queue must continuously re-request.
 * If they have not re-requested after a set interval then they are removed from the queue.
 * 
 * The general case is the client asks: can I have resource?
 *	the answer is true if there are resource and the client is within the top (resources unused) items of the queue.
 * (so a client can jump items in the queue iff there are enough free slots for both them and all the clients ahead of them)
 * 
 * Once a client has reached the top of the queue, they may have resources if they are free.
 * They are NOT removed from the queue though, and so may claim resources repeatedly at the head of the queue.
 * They will lose their prime position after their token expires. When this happens they must wait in queue again.
 * 
 * Each token may only take a certain proportion of the resources at most (50% default).
 * This is to stop one user choking the server. 
 * 
 * @author gary
 *
 */
public class TimedQueue<E> {
	private HashMap<E, Date> tokenExpiries = new HashMap<E, Date>();
	private LinkedList<E> queue = new LinkedList<E>();
	private HashMap<E, Integer> resourceUsage = new HashMap<E, Integer>();
	/** the number of allowed resources*/
	private int resourceCount = 2;
	
	/** the number of resources per client */
	private int clientLimit = 1;
	
	/** the number of milliseconds before a client's token is removed*/
	private Long queueTimeoutMS = 10*1000L;
	
	/**
	 * Attempts to take a resource.
	 * 
	 * @param token The token to represent this client.
	 * @return True if a resource was taken for this client. False if they were queued, or already in the queue and not at the top.
	 */
	public synchronized boolean takeResource(E token) {
		if (queue.contains(token)) {
			return canQueuedHaveResource(token);
		} else {
			queue.addLast(token);
			return canQueuedHaveResource(token);
		}
	}
	
	/**
	 * Returns true if a resource can be allocated to a client that refuses to queue.
	 * They must still free the resource when done.
	 * 
	 * A client is allowed to skip the queue iff there are more free slots than other clients queueing.
	 * 
	 * @param token
	 * @return true if the client is given a resource. False otherwise.
	 */
	public synchronized boolean takeResourceWithoutQueueing(E token) {
		removeExpiredTokensAheadOf(token);
		if (getFreeResourceCount()-queue.size() > 0 && getUsedCount(token)<clientLimit) {
			incrementUsed(token);
			return true;
		} else {
			return false;
		}
	}
	
	/**
	 * Indicates this token is no longer using one of the resources.
	 * They may still take another resource now if they are still at the head of the queue.
	 * @param token
	 * @return true if this had any effect.
	 */
	public synchronized void freeResource(E token) {
		decrementUsed(token);
	}
	
	public synchronized int positionInQueue(E token) {
		return queue.indexOf(token);
	}
	
	public synchronized int queueSize() {
		return queue.size();
	}
	
	/**
	 * allocates a resource to this token if it can. This token must be on the queue.
	 * @param token
	 * @return
	 */
	private boolean canQueuedHaveResource(E token) {
		//We've just seen this client, so update their expiry.
		tokenExpiries.put(token, new Date());
		removeExpiredTokensAheadOf(token);
		//If there are more free resources than us and all the clients ahead of us, then we can go!
		if (getFreeResourceCount() > queue.indexOf(token) && getUsedCount(token)<clientLimit) {
			incrementUsed(token);
			return true;
		} else {
			return false;
		}
	}
	
	private void removeExpiredTokensAheadOf(E token) {
		Iterator<E> queueIterator = queue.iterator();
		while (queueIterator.hasNext()) {
			E thisToken = queueIterator.next();
			if (token == thisToken) return;
			if (hasExpired(thisToken)) { 
				queueIterator.remove();
				tokenExpiries.remove(thisToken);
			}
		}
	}
	
	//The number of resources used 
	private int getUsedCount(E token) {
		Integer count = resourceUsage.get(token);
		if (count==null) {
			resourceUsage.put(token, 0);
		}
		return resourceUsage.get(token);
	}
	
	//increment the number of resources used by this token:
	private void incrementUsed(E token) {
		Integer count = resourceUsage.get(token);
		if (count==null) {
			count=0;
		}
		resourceUsage.put(token, count+1);
	}
	
	//decrement the number of resources used by this token:
	private void decrementUsed(E token) {
		//Will deliberately NullPointerException if called on a token that is not present.
		Integer count = resourceUsage.get(token);
		if (count-1 == 0) {
			resourceUsage.remove(token);
		} else {
			resourceUsage.put(token, count-1);
		}
	}
	
	private boolean hasExpired(E token) {
		return ((new Date()).getTime()-tokenExpiries.get(token).getTime()) > queueTimeoutMS;
	}
	
	public synchronized int getFreeResourceCount() {
		return resourceCount-getUsedResourceCount();
	}
	
	public synchronized int getUsedResourceCount() {
		int out=0;
		for (Integer i : resourceUsage.values()) {
			out+=i;
		}
		return out;
	}
	
	public synchronized int getResourceCount() {
		return resourceCount;
	}
	public synchronized void setResourceCount(int resourceCount) {
		this.resourceCount = resourceCount;
	}
	public synchronized Long getQueueTimeoutMS() {
		return queueTimeoutMS;
	}
	public synchronized void setQueueTimeoutMS(Long queueTimeoutMS) {
		this.queueTimeoutMS = queueTimeoutMS;
	}
	
	public synchronized int getClientLimit() {
		return clientLimit;
	}

	public synchronized void setClientLimit(int clientLimit) {
		this.clientLimit = clientLimit;
	}
	
	
}
