package indexnode;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;

import indexnode.IndexConfigDefaults.IK;
import common.Config;
import common.Logger;

/**
 * Manages advertisments on many interfaces and addresses.
 * 
 * Handles both active and prospective adverts.
 * 
 * @author gp
 *
 */
public class IndexAdvertismentManager {

	ArrayList<IndexAdvertiser> advertisers = new ArrayList<IndexAdvertiser>();
	AdvertDataSource ads;
	
	/**
	 * 
	 * @param conf The indexnode config with parameters useful for this manager.
	 * @throws UnknownHostException 
	 * @throws SocketException 
	 */
	public IndexAdvertismentManager(Config conf, AdvertDataSource ads) throws UnknownHostException, SocketException {
		this.ads = ads;
		
		//create an advertiser for each address on each interface:
		startAdvertisers(conf);
		
	}

	private void startAdvertisers(Config conf) throws UnknownHostException, SocketException {
		String bindTo = conf.getString(IK.BIND_INTERFACE);
		String aos = conf.getString(IK.ADVERTISE_ADDRESS);
		
		InetAddress advertiseOn = null;
		if (aos!=null && !aos.equals("all")) {
			advertiseOn = InetAddress.getByName(aos);
		}
		
		if (bindTo.equals("all")) {
			Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
			while (ifs.hasMoreElements()) {
				advertiseOnInterface(ifs.nextElement(), advertiseOn);
			}
		} else {
			if (bindTo.equals("")) {
				Logger.log("You must specify a bind-interface (or \"all\") in your configuation!\nExiting...");
				return;
			}
			advertiseOnInterface(NetworkInterface.getByName(bindTo), advertiseOn);
		}
	}
	
	private void advertiseOnInterface(NetworkInterface if0, InetAddress advertiseOn) throws SocketException {
		InetSocketAddress addr;
		Enumeration<InetAddress> addrs = if0.getInetAddresses();
		if (addrs.hasMoreElements()) {
			while (addrs.hasMoreElements()) {
				addr = new InetSocketAddress(addrs.nextElement(), ads.getPort());
				if (advertiseOn==null || advertiseOn.equals(addr.getAddress())) {
					advertisers.add(new IndexAdvertiser(addr, ads));
				}
			}
		} else {
			Logger.warn("Not listening on "+if0.getDisplayName()+" it has no addresses.");
			return;
		}
		
	}
	public void shutdown() {
		for (IndexAdvertiser ia : advertisers) {
			ia.shutdown();
		}
	}
	
}
