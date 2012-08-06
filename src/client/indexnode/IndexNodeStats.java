package client.indexnode;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;

/**
 * A container to represent stats about an indexnode.
 * @author gp
 */
public class IndexNodeStats {
	
	public static class IndexNodeClient implements Comparable<IndexNodeClient> {
		private final String alias;
		private Long totalShareSize;
		private String avatarhash;
		private BufferedImage cachedAvatar;
		private final IndexNode onNode;
		
		public void setCachedAvatar(BufferedImage cachedAvatar) {
			this.cachedAvatar = cachedAvatar;
		}
		
		public InputStream getIconStreamFromIndexNode() throws IOException {
			return onNode.getClientAvatarStream(avatarhash);
		}
		
		public void setAvatarhash(String avatarhash) {
			if (!this.avatarhash.equals(avatarhash)) cachedAvatar = null;
			this.avatarhash = avatarhash;
		}
		
		public String getAvatarhash() {
			return avatarhash;
		}
		
		public BufferedImage getCachedAvatar() {
			return cachedAvatar;
		}
		
		public void setTotalShareSize(Long totalShareSize) {
			this.totalShareSize = totalShareSize;
		}
		
		IndexNodeClient(String alias, long totalShareSize, String avatarhash, IndexNode onNode) {
			this.alias = alias;
			this.totalShareSize = totalShareSize;
			this.avatarhash = avatarhash;
			this.onNode = onNode;
		}
		
		public String getAlias() {
			return alias;
		}
		public long getTotalShareSize() {
			return totalShareSize;
		}
		
		//enable meaningful set membership:
		@Override
		public int hashCode() {
			return alias.hashCode();
		}
		
		@Override
		public boolean equals(Object obj) {
			return alias.equals(((IndexNodeClient)obj).alias);
		}
		
		@Override
		public int compareTo(IndexNodeClient o) {
			return this.totalShareSize.compareTo(o.totalShareSize);
		}
		
		@Override
		public String toString() {
			return alias;
		}
	}
	
	Date started = new Date();
	int indexedFiles = 0;
	int uniqueFiles = 0;
	long totalRequestedBytes = 0l;
	long size = 0;
	long uniqueSize = 0;
	HashMap<String, IndexNodeClient> peers = new LinkedHashMap<String, IndexNodeClient>();
	
	public int getIndexedFiles() {
		return indexedFiles;
	}
	public HashMap<String, IndexNodeClient> getPeers() {
		return peers;
	}
	public Date getStarted() {
		return started;
	}
	public long getTotalRequestedBytes() {
		return totalRequestedBytes;
	}
	public int getUniqueFiles() {
		return uniqueFiles;
	}
	
	public long getSize() {
		return size;
	}
	
	public long getUniqueSize() {
		return uniqueSize;
	}
}
