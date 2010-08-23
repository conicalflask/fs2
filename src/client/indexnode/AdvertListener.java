package client.indexnode;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet6Address;
import java.net.SocketException;
import java.net.URL;

import common.FS2Constants;
import common.Logger;

/**
 * Listens out for adverts sent by indexnodes.
 * 
 * There are two kinds of adverts:
 * 1) from active indexnodes specifying they are ready for connection
 * 2) from potential automatic indexnodes specifying their readiness to become an indexnode if there's no better candidate available.
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
			e.printStackTrace();
			return null;
		}
	}
	
	@Override
	public void run() {
		byte[] buf = new byte[50];
		DatagramPacket packet = new DatagramPacket(buf, buf.length);
		while (!mustShutdown) {
			try {
				try {
					socket.receive(packet);
					String[] splitAdvert = (new String(packet.getData(),0,packet.getLength(), "utf-8")).split(":");
					if (splitAdvert.length < 2) {
						continue;
					}
					int indexNodePort = 0;
					try {
						indexNodePort = Integer.parseInt(splitAdvert[1]);
					} catch (NumberFormatException e) {
						continue;
					}
					if (!splitAdvert[0].equals(FS2Constants.FS2_PROTOCOL_VERSION)) {
						Logger.warn("An incompatible indexnode is advertising on this network. It uses protocol version: "+splitAdvert[0]+" This client requires protocol version: "+FS2Constants.FS2_PROTOCOL_VERSION);
						continue;
					}
					long advertuid = 0;
					try {
						advertuid = Long.parseLong(splitAdvert[2]);
					} catch (Exception e) {
						Logger.warn("A supposedly compatible indexnode is providing no advert ID, ignoring.");
						Logger.log("Advert: "+new String(packet.getData(),0,packet.getLength(), "utf-8"));
						continue;
					}
					if (advertuid==0) {
						Logger.warn("Indexnode is advertising advertuid of zero, ignoring.");
						continue;
					}
					
					URL url;
					if (packet.getAddress() instanceof Inet6Address) {
						url = new URL("http://["+packet.getAddress().getHostAddress()+"]:"+indexNodePort);
					} else {
						url = new URL("http://"+packet.getAddress().getHostAddress()+":"+indexNodePort);
					}
					
					if (mustShutdown) return;
					
					indexcomms.advertRecieved(url, advertuid);
					
				} finally {
					if (mustShutdown) return;
				}
			} catch (Exception e){
				try { Thread.sleep(FS2Constants.INDEXNODE_ADVERTISE_INTERVAL_MS); } catch (InterruptedException dontcare) {}
				Logger.warn("Advertisment reception failed: "+e.toString());
				e.printStackTrace();
			}
		}
	}
	
	public void shutdown() {
		mustShutdown = true;
		socket.close();
		this.interrupt();
	}
}

