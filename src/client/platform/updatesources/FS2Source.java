package client.platform.updatesources;

/**
 * Checks for updated versions of FS2 on the indexnode that we're connected to for sharing.
 * @author gary
 */
public class FS2Source extends UpdateSource {

	@Override
	public CodeUpdate getLatestUpdate() {
		//TODO: flesh this out when a new indexnode client communicator has been created.
		return null;
	}

}
