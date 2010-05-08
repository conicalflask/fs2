package client.platform.updatesources;

import java.net.URL;

public final class CodeUpdate {
	public final URL location;
	public final String description;
	public final String name;
	public final String locationDescription;
	public final String version;
	
	public CodeUpdate(URL location, String name, String description, String locationDescription, String version) {
		this.location = location;
		this.description = description;
		this.name = name;
		this.locationDescription = locationDescription;
		this.version = version;
	}
}
