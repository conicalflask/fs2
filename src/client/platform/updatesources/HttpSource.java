package client.platform.updatesources;

import java.net.URL;

import client.Version;

import common.HttpUtil;
import common.Logger;

public class HttpSource extends UpdateSource {

	URL repo; //The location of the update server
	
	public HttpSource(URL repo) {
		this.repo = repo;
	}
	
	@Override
	public CodeUpdate getLatestUpdate() {
		try {
			//1) Get name of newest update.
			String serverVer = HttpUtil.simpleDownloadToString(repo).trim();
			String updateJar = Version.FS2_CLIENT_NAME+"-"+serverVer+".jar";

			if (versionComparator.compare(updateJar, thisJarName)<=0) {
				return null;
			}
	
			Logger.log("There is a new version available: "+serverVer);
			//2) Download the update and the description of the update.
			String desc = HttpUtil.simpleDownloadToString(new URL(repo.toString()+"/"+serverVer+".description"));
			//3) Return them.
			CodeUpdate update = new CodeUpdate(new URL(repo.toString()+"/"+updateJar), updateJar, desc, repo.toString(), serverVer);
			return update;
		} catch (Exception e) {
			Logger.warn("Updates could not be obtained from: "+repo+", "+e);
			return null;
		}
	}

}
