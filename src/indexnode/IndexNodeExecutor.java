package indexnode;
import java.io.File;

import common.Config;
import common.Logger;


/*
 * Includes the stuff to actually make the indexnode executable.
 */
public class IndexNodeExecutor {
	
	public static void main(String[] args) throws Exception {
		Logger.setLoggingEnabled(true);
		File confFile = new File("indexnode_conf.xml");
		Logger.log("Using configuration file: "+confFile.getAbsolutePath());
		new IndexNode(new Config(new IndexConfigDefaults(), confFile));
	}

}
