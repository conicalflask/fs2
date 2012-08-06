package indexnode;

import indexnode.IndexNode.Client;
import indexnode.IndexNode.Share;

import java.util.Collection;

import org.w3c.dom.Element;

import common.FileList.Item;

public interface Filesystem {

	/**
	 * Imports a filelist specified by a client into the filesystem.
	 * This method is for an XML file list.
	 * @param root The root of the share to import.
	 * @param share The share that logically represents this.
	 */
	public abstract void importShare(Element root, Share share);

	/**
	 * Imports a filelist specified by a client into the filesystem.
	 * This method is for a FileList type share.
	 * @param root The root of the share to import.
	 * @param share The share that logically represents this.
	 */
	public abstract void importShare(Item root, Share share);
	
	/**
	 * Removes the specified share from the filesystem.
	 * @param share
	 * @throws SQLException 
	 */
	public abstract void delistShare(Share share);

	/**
	 * Creates a client directory in the top level of the filesystem.
	 * @param client
	 * @return the entry for the created directory.
	 */
	public abstract FilesystemEntry registerClient(Client client);

	/**
	 * Removes this client's top level directory
	 * @param entry the client's alias directory.
	 */
	public abstract void deregisterClient(FilesystemEntry entry);

	/**
	 * Performs a filesystem search for files with the specified hash.
	 * @param hash
	 * @return 
	 */
	public abstract Collection<? extends FilesystemEntry> searchForHash(String hash);

	/**
	 * Performs a filesystem search for files that contain all of the keywords specified in their name.
	 * The keywords are specified by a space or punctuation.
	 * 
	 * @param query The list of keywords, space-separated in a string.
	 * @return
	 */
	public abstract Collection<? extends FilesystemEntry> searchForName(String query);

	/**
	 * Returns the most popular files (the files that most people possess)
	 * @param limit the number of results to return at most.
	 * @return
	 */
	public abstract Collection<? extends FilesystemEntry> getPopularFiles(int limit);

	/**
	 * Returns the number of files (not directories) in the filesystem.
	 * @return
	 */
	public abstract int countFiles();

	/**
	 * Returns the number of unique files in the filesystem
	 */
	public abstract int countUniqueFiles();
	
	/**
	 * Returns the root entry of the filesystem.
	 * @return
	 */
	public abstract FilesystemEntry getRootEntry();

	/**
	 * Looks up a filesystem entry from the path given.
	 * @param path
	 * @return the entry found, null if the path does not point to an entry.
	 */
	public abstract FilesystemEntry lookupFromPath(String path);

	public abstract long totalSize();
	
	/**
	 * Returns the total size of the filesystem for only unique files.
	 * @return
	 */
	public abstract long uniqueSize();
	
	/**
	 * Adds bytes to an estimated total network transfer count.
	 */
	public abstract void incrementSent(long addSize);
	
	/**
	 * Gets the estimated total transfer count
	 * @return
	 */
	public abstract long getEstimatedTransfer();

}