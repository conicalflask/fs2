package indexnode;

import indexnode.IndexNode.Client;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import common.Config;

/**
 * Represents the indexnode's user database.
 * @author gary
 */
public class UserDatabase {
	
	public class User {
		int uid;
		String alias;
		boolean admin;
		boolean authorised;
		boolean localonly;
		String password; //MD5'd
		
		public User(int uid, String alias, boolean admin, boolean authorised, boolean localonly, String password) {
			this.uid = uid;
			this.alias = alias;
			this.admin = admin;
			this.authorised = authorised;
			this.localonly = localonly;
			this.password = password;
		}
	}
	
	// alias->User object.
	Map<String, User> softCopy = new HashMap<String, User>();
	Config hardCopy;
	int maxUID = 0;
	IndexNode node;
	
	public UserDatabase(File onDisk, IndexNode node) throws Exception {
		hardCopy = new Config(new AuthDatabseDefaults(), onDisk);
		//Load the database into memory:
		for (String uidKey : hardCopy.getChildKeys("")) {
			int nuid = Integer.parseInt(uidKey.substring(2));
			if (nuid>maxUID) maxUID=nuid;
			softCopy.put(hardCopy.getString(uidKey+"/alias"),
					     new User(nuid,
					    		  hardCopy.getString(uidKey+"/alias"),
					    		  hardCopy.getBoolean(uidKey+"/admin"),
					    		  hardCopy.getBoolean(uidKey+"/authorised"),
					    		  hardCopy.getBoolean(uidKey+"/localonly"),
					    		  hardCopy.getString(uidKey+"/password")));
		}
		
		node.getChat().registerCommandable("userlist", new UserListCommand());
		node.getChat().registerCommandable("userenable", new UserEnableCommand());
		node.getChat().registerCommandable("useradmin", new UserAdminCommand());
	}
	
	/**
	 * Checks to see if this login request is correct. It creates the user if need be.
	 * @param alias
	 * @param password
	 * @return
	 */
	public synchronized boolean isValidAuthRequest(String alias, String password) {
		User requested = softCopy.get(alias);
		if (requested==null) {
			addUnauthorised(alias, password);
			return false;
		} else {
			return (requested.authorised && requested.password.equals(password));
		}
	}
	
	public synchronized boolean isAdmin(String alias) {
		if (!softCopy.containsKey(alias)) return false;
		return softCopy.get(alias).admin;
	}
	
	/**
	 * Local only aliases are reserved even in insecure mode.
	 * @param alias
	 * @return
	 */
	public synchronized boolean isLocalonly(String alias) {
		if (!softCopy.containsKey(alias)) return false;
		return softCopy.get(alias).localonly;
	}
	
	void addUnauthorised(String alias, String password) {
		addUser(++maxUID, alias, false, false, false, password);
	}
	
	void addUser(int uid, String alias, boolean admin, boolean authorised, boolean localonly, String password) {
		String userKey = "u"+uid;
		hardCopy.putString(userKey+"/alias", alias);
		hardCopy.putBoolean(userKey+"/admin", admin);
		hardCopy.putBoolean(userKey+"/localonly", localonly);
		hardCopy.putBoolean(userKey+"/authorised", authorised);
		hardCopy.putString(userKey+"/password", password);
		hardCopy.doSave(); //hardcopy non-trivially changed... so save now...
		softCopy.put(alias, new User(uid, alias, admin, authorised, localonly, password));
	}
	
	void setAdmin(User u, boolean admin) {
		u.admin = admin;
		hardCopy.putBoolean("u"+u.uid+"/admin", u.admin);
		hardCopy.doSave();
	}
	
	void setAuthorised(User u, boolean authorised) {
		u.authorised = authorised;
		hardCopy.putBoolean("u"+u.uid+"/authorised", u.authorised);
		hardCopy.doSave();
	}
	
	
	public class UserListCommand implements ChatCommandable {
		@Override
		public String doCommand(ChatDatabase chat, Client cl, String command, String args) {
			synchronized (UserDatabase.this) {
				
				if (!isAdmin(cl.getAlias()) || (!cl.isSecure() && !cl.isLocal())) return "Access denied.";
				StringBuilder ret = new StringBuilder();
				
				ret.append("Users known to the indexnode: \n");
				
				int maxAliasLength = 0;
				for (User u : softCopy.values()) maxAliasLength = Math.max(maxAliasLength, u.alias.length());
				maxAliasLength+=2;
				
				for (User u : softCopy.values()) {
					ret.append(u.uid+": ");
					ret.append(u.alias);
					for (int spi = 0; spi<(maxAliasLength-u.alias.length()); spi++) ret.append(" ");
					if (u.authorised) ret.append(" enabled");
					if (u.admin) ret.append(" admin");
					ret.append("\n");
				}
				
				return ret.toString();
			}
		}
	}
	
	/**
	 * Tries to guess which user an admin meant in a command.
	 * Their description can be a uid or a prefix of an alias.
	 * @param userDesc
	 * @return
	 */
	User guessUser(String userDesc) {
		userDesc = userDesc.toLowerCase();
		
		//it can be an int (ideally) for a user id:
		int uid = Integer.MAX_VALUE;
		try {
			uid = Integer.parseInt(userDesc);
		} catch (NumberFormatException ohwell) {}
		

		for (User u : softCopy.values()) {
			if (uid==u.uid) return u;
			if (u.alias.toLowerCase().startsWith(userDesc)) return u;
		}
		
		//then it's probably nothing at all:
		return null;
	}
	
	public class UserEnableCommand implements ChatCommandable {
		@Override
		public String doCommand(ChatDatabase chat, Client cl, String command, String args) {
			synchronized (UserDatabase.this) {
				if (!isAdmin(cl.getAlias()) || (!cl.isSecure() && !cl.isLocal())) return "Access denied.";
				String uedesc =  "/userenable sets the enabled property for an account.\nIt requires two args: {user description} {true/false}\n";
				if (args==null) return uedesc;
				String[] sargs = args.split(" ");
				if (sargs.length!=2) return uedesc;
				User found = guessUser(sargs[0]);
				if (found==null) return "User not found, you can identify users by their UID or by a case-insensitive prefix of their alias";
				
				setAuthorised(found, Boolean.parseBoolean(sargs[1]));
				
				return "User '"+found.alias+"' enabled is now: "+found.authorised;
			}
		}
	}
	
	public class UserAdminCommand implements ChatCommandable {
		@Override
		public String doCommand(ChatDatabase chat, Client cl, String command, String args) {
			synchronized (UserDatabase.this) {
				if (!isAdmin(cl.getAlias()) || (!cl.isSecure() && !cl.isLocal())) return "Access denied.";
				String uedesc =  "/useradmin sets the admin property for an account.\nIt requires two args: {user description} {true/false}\n";
				if (args==null) return uedesc;
				String[] sargs = args.split(" ");
				if (sargs.length!=2) return uedesc;
				User found = guessUser(sargs[0]);
				if (found==null) return "User not found, you can identify users by their UID or by a case-insensitive prefix of their alias";
				
				setAdmin(found, Boolean.parseBoolean(sargs[1]));
				
				return "User '"+found.alias+"' admin is now: "+found.admin;
			}
		}
	}
}
