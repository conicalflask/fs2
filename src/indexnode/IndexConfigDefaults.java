package indexnode;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import common.ConfigDefaults;

public class IndexConfigDefaults implements ConfigDefaults {

	/**
	 * The indexnode configuration keys
	 * @author gary
	 */
	public static class IK {
		public static final String PORT = "network/bind_port";
		public static final String ADVERTISE = "network/advertise";
		public static final String BIND_INTERFACE = "network/bind-interface";
		public static final String ADVERTISE_ADDRESS = "network/advertise-address";
		public static final String ADVERTUID = "network/advert-uid";
		
		public static final String ALIAS = "alias";
		
		public static final String AVATAR_CACHE_PATH = "avatar-cache-path";
		
		public static final String FILESYSTEM_UPDATE_POOLSIZE = "tuning/fs_update_poolsize";
		
		public static final String SECURE_MODE = "security/secure-mode";
		public static final String DHANON_TLS = "security/allow-dh-anon";
		public static final String RESERVED_ALIASES = "security/reserved-aliases";
		public static final String USER_DATABSE = "security/users-file";
	}
	
	protected HashMap<String, String> defaults = new HashMap<String, String>();
	protected HashMap<String, String> comments = new HashMap<String, String>();
	
	public IndexConfigDefaults() {
		defaults.put(IK.PORT, "1337");
		comments.put(IK.PORT, "the insecure indexnode sockets will be bound to this port. Secure indexnode sockets will be bound to this number+1");
		defaults.put(IK.ADVERTISE, Boolean.TRUE.toString());
		defaults.put(IK.ADVERTISE_ADDRESS, "all");
		comments.put(IK.ADVERTISE_ADDRESS, "can be \"all\" to advertise on all available addresses on the bound interfaces, or the textual address (e.g. 192.168.0.1) of the single address to advertise on.");
		defaults.put(IK.BIND_INTERFACE, "all");
		comments.put(IK.BIND_INTERFACE, "can be \"all\" to listen/advertise on all available interfaces, or the name of the single interface to bind on.");
		defaults.put(IK.ADVERTUID, Long.toString((new Random()).nextLong()));
		comments.put(IK.ADVERTUID, "You'll leave this alone if you know what's good for you");
		
		defaults.put(IK.ALIAS, "Dr. n00b's private reserve");
		comments.put(IK.ALIAS, "Name your indexnode something nice.");
		
		defaults.put(IK.AVATAR_CACHE_PATH, "avatars");
		comments.put(IK.AVATAR_CACHE_PATH, "specifies where avatars for peers are stored on disk");
		
		defaults.put(IK.FILESYSTEM_UPDATE_POOLSIZE, "2");
		comments.put(IK.FILESYSTEM_UPDATE_POOLSIZE, "The number of simultanious filesystem share-imports that can happen.");
		
		defaults.put(IK.SECURE_MODE, Boolean.FALSE.toString());
		comments.put(IK.SECURE_MODE, "when true connections will only be accepted using secure sockets and clients must be registered.");
		
		defaults.put(IK.DHANON_TLS, Boolean.TRUE.toString());
		comments.put(IK.DHANON_TLS, "(recommended, until PKI is implemented) uses anonymous Diffie-Hellman key exchange between the client and this indexnode. If disabled clients will need this indexnode's certificate, or this indexnode will need a publicly signed certificate.");
		
		defaults.put(IK.USER_DATABSE, "users.xml");
		comments.put(IK.USER_DATABSE, "the file that users are stored in. This file is almost ignored unless secure mode is on. LocalOnly users have their names reserved and may never be used by anybody even in insecure mode.");
	}
	
	@Override
	public Map<String, String> getComments() {
		return comments;
	}

	@Override
	public Map<String, String> getDefaults() {
		return defaults;
	}

	@Override
	public String getRootElementName() {
		return "indexnode-config";
	}

}
