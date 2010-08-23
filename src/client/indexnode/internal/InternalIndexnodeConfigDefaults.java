package client.indexnode.internal;

import client.platform.ClientConfigDefaults.CK;
import common.Config;

import indexnode.IndexConfigDefaults;

/**
 * Might be handy at some point.
 * @author gp
 */
public class InternalIndexnodeConfigDefaults extends IndexConfigDefaults {
	
	class IIK extends IK {
		
	}
	
	public InternalIndexnodeConfigDefaults(Config clientConfig) {
		super();
		
		defaults.put(IIK.ALIAS, clientConfig.getString(CK.ALIAS));
		
	}
}
