package client.platform;

import java.util.List;

/**
 * Abstracts operating system functions that Java does not have builtin.
 * @author gary
 *
 */
public interface PlatformOS {
	
	public class TaskDescription {
		public final String processName;
		public final String windowTitle;
		public TaskDescription(String processName, String windowTitle) {
			this.processName = processName;
			this.windowTitle = windowTitle;
		}
		@Override
		public String toString() {
			return processName+(windowTitle!=null ? " ("+windowTitle+")" : "");
		}
	};
	
	/**
	 * Should return a list of "applications" currently running.
	 * This is intended to be a list of tasks running for the current user.
	 * @return
	 */
	public List<TaskDescription> getActiveTasks();
	
	/**
	 * Will return the path to the java virtual machine executable on this platform.
	 * @return
	 */
	public String getJVMExecutablePath();
}
