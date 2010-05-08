package common;

import java.util.Map;

public interface ConfigDefaults {
	
	public Map<String, String> getDefaults();
	public Map<String, String> getComments();
	public String getRootElementName();
	
}
