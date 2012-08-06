package client.platform;

import java.util.Collections;
import java.util.List;

public class MacOSX implements PlatformOS {

	@Override
	public List<TaskDescription> getActiveTasks() {
		return Collections.emptyList();
	}

	@Override
	public String getJVMExecutablePath() {
		return System.getProperty("java.home")+"/bin/java";
	}
	
	
}
