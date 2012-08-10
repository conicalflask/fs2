package client.platform.updatesources;

import java.io.File;
import java.net.MalformedURLException;
import java.util.Collections;
import java.util.LinkedList;

import common.Logger;

import client.platform.Platform;

/**
 * Finds the latest version in the local code udpates cache.
 * @author gary
 *
 */
public class CacheSource extends UpdateSource {
	@Override
	public CodeUpdate getLatestUpdate() {
		File[] codeUpdates = Platform.getUpdatesDirectory().listFiles();
		
		LinkedList<File> potentials = new LinkedList<File>();
		for (File f : codeUpdates) {
			//Only consider updates that are newer than the currently executing version of FS2.
			if (UpdateSource.versionComparator.compare(f.getName(), UpdateSource.thisJarName)>0) {
				potentials.add(f);
			}
		}
		Collections.sort(potentials,new CodeUpdateFileComparator());
		if (potentials.size()==0) return null;
		
		try {
			return new CodeUpdate(potentials.getLast().toURI().toURL(),potentials.getLast().getName(),"Latest cached update. No further information available.", "Local update cache", "?.?.?");
		} catch (MalformedURLException e) {
			Logger.log(e);
			return null;
		}
	}
}
