package client.indexnode.downloadcontroller;

import java.net.URL;

/**
 * A simple container class for download URLs along with the peer alias that they belong to.
 * Note that these are considered equal if their peer aliases are equal (and hashCode behaves equivalently)
 * This is because (although perhaps rubbish) aliases are used to uniquely identify peers.
 * @author gary
 */
public final class DownloadSource {
	public final String peerAlias;
	public final URL location;
	
	public DownloadSource(String peerAlias, URL location) {
		this.peerAlias = peerAlias;
		this.location = location;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((peerAlias == null) ? 0 : peerAlias.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		DownloadSource other = (DownloadSource) obj;
		if (peerAlias == null) {
			if (other.peerAlias != null)
				return false;
		} else if (!peerAlias.equals(other.peerAlias))
			return false;
		return true;
	}
}
