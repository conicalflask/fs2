package client.indexnode;

import java.util.Collection;

import javax.swing.tree.TreePath;

public interface ListableEntry {

	/**
	 * Return the number of child nodes this entry has.
	 * @return
	 */
	public int getAllChildrenCount();
	
	/**
	 * Return the filesystem entry at index through this entry. This should include both directories and files!
	 * @param index
	 * @return
	 */
	public FileSystemEntry getFromAllChildren(int index);
	
	/**
	 * Returns a collection of all the children of this node.
	 * @return
	 */
	public Collection<FileSystemEntry> getAllChildren();
	
	/**
	 * Get the recursive size for this item.
	 * @return
	 */
	public long getSize();
	
	/**
	 * Returns the name of this item
	 */
	public String getName();
	
	/**
	 * Returns the path to this entry in the filesystem.
	 * @return
	 */
	public TreePath getPath();
	
}
