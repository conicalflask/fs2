package client.platform;

import java.util.Collections;
import java.util.List;


public class Linux implements PlatformOS {
	
	@Override
	public String getJVMExecutablePath() {
		return System.getProperty("java.home")+"/bin/java";
	}
	
	@Override
	public List<TaskDescription> getActiveTasks() {
		return Collections.emptyList();
	}

//	public static List<TaskDescription> getActiveTasksOnUnix() {
//		ArrayList<TaskDescription> res = new ArrayList<TaskDescription>();
//		try {
//			Process ps = Runtime.getRuntime().exec(new String[] {"ps", "ux"});
//			InputStream is = ps.getInputStream();
//			try {
//				BufferedReader psout = new BufferedReader(new InputStreamReader(new BufferedInputStream(is)));
//				
//				String nextLine;
//				while ((nextLine = psout.readLine())!=null) {
//					String[] psbits = nextLine.split("\\s+");
//					res.add(new TaskDescription(psbits[10], null)); //it is assumed the interesting process bit will be the 10th element of the ps ux output.
//				}
//			} finally {
//				is.close();
//			}
//		} catch (IOException e) {
//			Logger.warn("Unable to scrape for processes on a unix-like OS: "+e);
//			e.printStackTrace();
//		}
//		return res;
//	}
}
