package indexnode;

import java.util.HashMap;
import java.util.Map;

import common.ConfigDefaults;

public class AuthDatabseDefaults implements ConfigDefaults {
	
	private HashMap<String, String> defaults = new HashMap<String, String>();
	private HashMap<String, String> comments = new HashMap<String, String>();
	
	public AuthDatabseDefaults() {
		defaults.put("u0/alias", "fs2");     //the user's alias
		defaults.put("u0/admin", "true");  	//simply put, they are fully trusted by the indexnode and can do all sorts of extra things.
		defaults.put("u0/authorised", "true"); //may connect
		defaults.put("u0/localonly", "true");  //only from localhost, and reserved at all other times.
		defaults.put("u0/password", "cae77ea3e63387ccb530138d16bb2bfd");  //md5 of the password (this example is '')
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
		return "indexnode-users";
	}

}
