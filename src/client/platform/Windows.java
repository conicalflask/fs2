package client.platform;

import java.util.Collections;
import java.util.List;

public class Windows implements PlatformOS {

//	@Override
//	public List<TaskDescription> getActiveTasks() {
//		ArrayList<TaskDescription> res = new ArrayList<TaskDescription>();
//		try {
//			Process tl = Runtime.getRuntime().exec(new String[] {"tasklist.exe", "/V", "/FO", "list"}); 
//			
//			InputStream is = tl.getInputStream();
//			try {
//				BufferedReader tlout = new BufferedReader(new InputStreamReader(new BufferedInputStream(is)));
//				
//				String nextLine;
//				String imageName = null;
//				while ((nextLine = tlout.readLine())!=null) {
//					Matcher in = Pattern.compile("Image Name:\\s+(\\S.+)").matcher(nextLine);
//					if (in.matches()) {
//						//woo image name line!
//						imageName = in.group(1);
//						continue;
//					}
//					Matcher wt = Pattern.compile("Window Title:\\s+(\\S.+)").matcher(nextLine);
//					if (wt.matches()) {
//						res.add(new TaskDescription(imageName, wt.group(1)));
//					}
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

	@Override
	public List<TaskDescription> getActiveTasks() {
		return Collections.emptyList();
	}
	
	@Override
	public String getJVMExecutablePath() {
		return System.getProperty("java.home")+"/bin/java.exe";
	}
	
	
}
