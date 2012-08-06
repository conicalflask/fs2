package client.platform.updatesources;

import java.io.File;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import client.Version;
/**
 * The standard interface for a source of code updates.
 * @author gary
 */
public abstract class UpdateSource {
	public abstract CodeUpdate getLatestUpdate();
	
	protected static class clientVersionComparator implements Comparator<String> {
		@Override
		public int compare(String o1, String o2) {
			Iterator<Integer> i1 = getVersionBits(o1).iterator();
			Iterator<Integer> i2 = getVersionBits(o2).iterator();
			while (i1.hasNext() || i2.hasNext()) { //Fall out if there are no more fragments.
				//If there are no more bits in i1, then it is smaller:
				if (!i1.hasNext()) return -1;
				//Symmetrically, if there are no more bits in i2, then it is smaller.
				if (!i2.hasNext()) return 1;
				
				//For the current version fragment (bit) that we are on, a difference in value is always important:
				int bit1 = i1.next();
				int bit2 = i2.next();
				if (bit1 > bit2) return 1;
				else if (bit1 < bit2) return -1;
				//If the fragments are equal, then we continue to compare the next fragments.
			}
			return 0;
		}
		
		Pattern clientNamePattern = Pattern.compile("^"+Pattern.quote(Version.FS2_CLIENT_NAME)+"-((?:\\d+\\.)+)jar$");
		
		private LinkedList<Integer> getVersionBits(String name) {
			LinkedList<Integer> bits = new LinkedList<Integer>();
			Matcher m = clientNamePattern.matcher(name);
			if (!m.matches()) return bits;
			for (String verItem : m.group(1).split("\\.")) {
				try {
					bits.add(Integer.parseInt(verItem));
					//This method of getting the numbers out will naturally result in a "" item at the end.
					// we don't care.
				} catch (NumberFormatException dontcare) {}
			}
			return bits;
		}
	}
	
	public static class CodeUpdateComparator implements Comparator<CodeUpdate> {
		private clientVersionComparator cmp = new clientVersionComparator();
		
		@Override
		public int compare(CodeUpdate o1, CodeUpdate o2) {
			if (o1==null) return -1;
			if (o2==null) return 1;
			return cmp.compare(o1.name, o2.name);
		}
	}
	
	public static class CodeUpdateFileComparator implements Comparator<File> {
		private clientVersionComparator cmp = new clientVersionComparator();
		
		public int compare(File o1, File o2) {
			return cmp.compare(o1.getName(), o2.getName());
		}
	}
	
	public static clientVersionComparator versionComparator = new clientVersionComparator();
	public static final String thisJarName = Version.FS2_CLIENT_NAME+"-"+Version.FS2_CLIENT_VERSION()+".jar";
}
