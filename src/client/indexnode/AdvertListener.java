package client.indexnode;

import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet6Address;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URL;

import common.FS2Constants;
import common.Logger;

/**
 * Listens out for adverts sent by indexnodes.
 * 
 * There are two kinds of adverts:
 * 1) from active indexnodes specifying they are ready for connection (activity advert)
 * 2) from potential automatic indexnodes specifying their readiness to become an indexnode if there's no better candidate available.(capability advert)
 * 
 * @author gary
 */
public class AdvertListener extends Thread {
	private DatagramSocket socket;
	private boolean mustShutdown = false;
	private IndexNodeCommunicator indexcomms;
	
	private AdvertListener(IndexNodeCommunicator indexcomms) {
		super();
		this.setDaemon(true);
		this.setName("IndexNode advert listener");
		this.indexcomms = indexcomms;
	}
	
	/**
	 * Creates a new, living advert listener, or returns null.
	 */
	public static AdvertListener getAdvertListener(IndexNodeCommunicator indexcomms) {
		AdvertListener n = new AdvertListener(indexcomms);
		try {
			n.socket = new DatagramSocket(FS2Constants.ADVERTISMENT_DATAGRAM_PORT);
			n.start();
			Logger.log("Now listening for indexnode adverts");
			return n;
		} catch (SocketException e) {
			Logger.warn("Advertisment reception couldn't be enabled.");
			Logger.log(e);
			return null;
		}
	}
	
	@Override
	public void run() {
		byte[] buf = new byte[100];
		DatagramPacket packet = new DatagramPacket(buf, buf.length);
		while (!mustShutdown) {
			try {
				try {
					socket.receive(packet);
					
					if (mustShutdown) return;
					
					String[] splitAdvert = (new String(packet.getData(),0,packet.getLength(), "utf-8")).split(":");
					//Both types of advert have more than two fields:
					if (splitAdvert.length < 2) {
						continue;
					}
					
					//All adverts start with the FS2 protocol version identifier.
					if (!splitAdvert[0].equals(FS2Constants.FS2_PROTOCOL_VERSION)) {
						Logger.warn("An incompatible indexnode is advertising on this network. It uses protocol version: "+splitAdvert[0]+" This client requires protocol version: "+FS2Constants.FS2_PROTOCOL_VERSION);
						continue;
					}
					
					if (splitAdvert[1].equals("autoindexnode")) {
						//It's a capability advert
						attemptCapabilityRX(splitAdvert, packet);
					} else {
						//it's an activity advert
						attemptActivityRX(splitAdvert, packet);
					}
					
				} finally {
					if (mustShutdown) return;
				}
			} catch (Exception e){
				try { Thread.sleep(FS2Constants.INDEXNODE_ADVERTISE_INTERVAL_MS); } catch (InterruptedException dontcare) {}
				Logger.warn("Advertisment reception failed: "+e.toString());
				Logger.log(e);
			}
		}
	}
	
	/**
	 * attempts to recieve an activity advert.
	 * @param splitAdvert
	 * @param packet
	 * @throws UnsupportedEncodingException 
	 * @throws MalformedURLException 
	 */
	private void attemptActivityRX(String[] splitAdvert, DatagramPacket packet) throws UnsupportedEncodingException, MalformedURLException {
		int indexNodePort = 0;
		try {
			indexNodePort = Integer.parseInt(splitAdvert[1]);
		} catch (NumberFormatException e) {
			return;
		}
		if (!splitAdvert[0].equals(FS2Constants.FS2_PROTOCOL_VERSION)) {
			Logger.warn("An incompatible indexnode is advertising on this network. It uses protocol version: "+splitAdvert[0]+" This client requires protocol version: "+FS2Constants.FS2_PROTOCOL_VERSION);
			return;
		}
		long advertuid = 0;
		try {
			advertuid = Long.parseLong(splitAdvert[2]);
		} catch (Exception e) {
			Logger.warn("A supposedly compatible indexnode is providing no advert ID, ignoring.");
			Logger.log("Advert: "+new String(packet.getData(),0,packet.getLength(), "utf-8"));
			return;
		}
		if (advertuid==0) {
			Logger.warn("Indexnode is advertising advertuid of zero, ignoring.");
			return;
		}
		
		URL url;
		if (packet.getAddress() instanceof Inet6Address) {
			url = new URL("http://["+packet.getAddress().getHostAddress()+"]:"+indexNodePort);
		} else {
			url = new URL("http://"+packet.getAddress().getHostAddress()+":"+indexNodePort);
		}
		indexcomms.advertRecieved(url, advertuid);
	}

	/**
	 * Attempts to decode a received packet into a capability advert.
	 * @param splitAdvert
	 * @param packet
	 */
	private void attemptCapabilityRX(String[] splitAdvert, DatagramPacket packet) {
		long advertuid = Long.parseLong(splitAdvert[3]); //for some reason it's field three.
		long capability = Long.parseLong(splitAdvert[2]);
		indexcomms.getInternalIndexNode().receivedCapabilityAdvert(advertuid, capability);
		
	}

	public void shutdown() {
		mustShutdown = true;
		socket.close();
		this.interrupt();
	}
}

