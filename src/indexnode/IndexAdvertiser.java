package indexnode;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;

import common.FS2Constants;
import common.Logger;

/**
 * Advertises this indexnode via UDP broadcast.
 * 
 * Current advert format:
 * {protocol-version-identifier-string}:{insecurePort}:{advertUID}
 * 
 * @author gary
 */
public class IndexAdvertiser extends Thread {
	
	private DatagramSocket socket;
	private InetSocketAddress sock;
	private long advertuid;
	
	public IndexAdvertiser(InetSocketAddress sock, long advertuid) throws SocketException {
		super();
		Logger.log("Advertising starts on: "+sock.getAddress().getHostAddress());
		this.sock = sock;
		setDaemon(true);
		socket = new DatagramSocket(sock);
		this.advertuid = advertuid;
		start();
	}
	
	public void run() {
		boolean warned = false;
		try {
			String advertString = FS2Constants.FS2_PROTOCOL_VERSION+":"+Integer.toString(sock.getPort())+":"+Long.toString(advertuid);
			//Logger.log(advertString);
			byte[] advert = (advertString).getBytes("utf-8");
			DatagramPacket packet = new DatagramPacket(advert, advert.length, (socket.getInetAddress() instanceof Inet6Address ? InetAddress.getByName("ff02::1") : InetAddress.getByName("255.255.255.255")), FS2Constants.ADVERTISMENT_DATAGRAM_PORT);
			while (true) {
				try {
					socket.send(packet);
				} catch (IOException e) {
					if (!warned) {
						Logger.warn("Failed to send an advertisment on: "+sock.toString()+", Retrying silently now. Why? "+e.toString());
						warned = true;
					}
				}
				try {
					sleep(FS2Constants.INDEXNODE_ADVERTISE_INTERVAL_MS);
				} catch (Exception dontCare) {};
			}
		} catch (Exception e) {
			Logger.warn("IndexAdvertiser: "+e.toString()+ ", No longer advertising on: "+sock.getAddress().getHostAddress());
			//e.printStackTrace();
		}
	}
	
}
