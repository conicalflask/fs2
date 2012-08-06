package indexnode;

import indexnode.IndexNode.Share;

import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.Map;

public interface FilesystemEntry {

	public abstract boolean isRoot();

	public abstract String getName();

	public abstract long getSize();

	public abstract int getLinkCount();

	public abstract FilesystemEntry getParent();

	public abstract String getHash();

	public abstract Share getShare();

	public abstract boolean isDirectory();

	/**
	 * returns a list of all files with the same hash.
	 * @return
	 */
	public abstract Collection<? extends FilesystemEntry> getAlternatives();

	/**
	 * Returns all entries that are children of this node.
	 * @return
	 */
	public abstract Map<String, ? extends FilesystemEntry> getChildren();

	/**
	 * Gets the child with the name specified.
	 * @param name The name of the child entry to lookup
	 * @return The entry for this child, or null if it does not exist.
	 */
	public abstract FilesystemEntry getNamedChild(String name);

	/**
	 * Returns the string path to this file in the filesystem.
	 * @return a / seperated path from the root of fs2 to this file.
	 * @throws UnsupportedEncodingException 
	 */
	public abstract String getPath(boolean urlEncode, boolean includeOwner) throws
			UnsupportedEncodingException;

	/**
	 * Generate a URL to the file on the client hosting this.
	 * @return
	 */
	public abstract String getURL();

	/**
	 * Sets the new size of this entry.
	 * Directories have the size that is the sum of all their file descendants.
	 * Atomic.
	 * @param value
	 * @throws SQLException 
	 */
	public abstract void adjustSize(long size);

	/**
	 * Adds the count given to the entry's link count.
	 * Directories have a link count of 2+{number of direct child directories}
	 * Files always have 1 in FS2.
	 * Atomic.
	 * @param count the value to add to the current link count. 
	 * @throws SQLException 
	 */
	public abstract void adjustLinkCount(int count);

	/**
	 * Creates an entry as a child of this node, saved.
	 * @param name The filename of the new entry.
	 * @param hash The hash of the item (if a file).
	 * @param size The size in bytes of the new item.
	 * @param links The link count of the item. 1 for files, 2 for new directories.
	 * @param share The share that this item belongs to.
	 * @return the new entry
	 */
	public abstract FilesystemEntry createChildEntry(String name, String hash, long size,
			int links, Share share);

	public abstract FilesystemEntry createChildDirectory(String name, Share share);

	public abstract void erase();

	public abstract String getOwnerAlias();

	/**
	 * Renames this entry to the new name supplied.
	 * @param newName
	 */
	public abstract void rename(String newName);

}