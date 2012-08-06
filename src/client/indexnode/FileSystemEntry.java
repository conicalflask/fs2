package client.indexnode;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.TimerTask;
import java.util.Map.Entry;

import javax.swing.event.TableModelEvent;
import javax.swing.event.TreeModelEvent;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import client.gui.Utilities;

import common.FS2Constants;
import common.Logger;
import common.Util;

/**
 * A class to represent a file or directory available on some indexnode.
 * 
 * This class is more sophisticated than a mere filesystem node:
 * 1) It provides cached access to this item on the indexnode,
 * 2) access to cached, sorted child entries.
 * 3) Support for callbacks if it is likely that children are going to change in the near future.
 * 4) Supplies events to the filesystem's main treemodel. (for efficient representation using a JTree)
 * 
 * These are intended to be the nodes actually used in a JTree.
 * </br>
 * Note: This class has a natural ordering inconsistent with equals. This is because items are sorted by filename but equality depends on other attributes.
 * 
 * @author gary
 *
 */
public class FileSystemEntry implements TreeNode, Comparable<FileSystemEntry>, ListableEntry {
	
	/**
	 * This class can be scheduled with an Executor to 
	 * update this directory at a later date.
	 * 
	 * The run method scrapes the indexnode for children of this node
	 * and updates this node in two stages, deletions, then additions in order.
	 * 
	 * Events are generated after each stage.
	 * 
	 * This is awkward and long winded because it cannot be done in one stage. (thanks to the events)
	 * The removal event is computed, the children structures are made consistent,
	 * then the insertion event is computed.
	 * 
	 * @author gary
	 */
	private class UpdateNode implements Runnable {
		@Override
		public void run() {
			try {
				if (childDirectories==null) return; //shouldn't take the mutex whilst looking up children. if childDirectories is null then it means this node is no longer needed.
				
				//Get the new list: (this might take a while!)
				final LinkedList<FileSystemEntry> newChildren = fs.comm.lookupChildren(FileSystemEntry.this);
				
				//Separate it into the component lists (files and directories)
				final ArrayList<FileSystemEntry> newFiles = new ArrayList<FileSystemEntry>();
				final ArrayList<FileSystemEntry> newDirs = new ArrayList<FileSystemEntry>();
				
				//This next section must be done in the swing thread to avoid deadlocks :)
				Utilities.dispatch(new Runnable() {
					@Override
					public void run() {
						//rather than do this a clever, efficient one-pass way, i'm going to do it a brain-lazy but simple way with hash tables.
						// why do something in one pass when you can do it in five...
						synchronized (childrenMutex) {  //We're gonna hold this lock for a long while!
							if (childDirectories==null) return; //test again as the fetch may have taken a clock cycle ;)
							
							LinkedHashMap<FileSystemEntry, Integer> toDeleteDirs = new LinkedHashMap<FileSystemEntry, Integer>(childDirectoryIndices);
							LinkedHashMap<FileSystemEntry, Integer> toDeleteFiles = new LinkedHashMap<FileSystemEntry, Integer>();
							int c = childDirectories.size();
							for (FileSystemEntry f : childFiles) {
								toDeleteFiles.put(f, c++);
							}
							
							//1) build lists of new files and directories:
							for (FileSystemEntry f : newChildren) {
								if (f.isDirectory()) {
									newDirs.add(f);
	
									//It was here already, so remove it from the delete list:
									//but keep it on the list if the old entry was a leaf (no children) but the new one isn't.
									// This way the gui/interactive layer will know to update.
									Integer onIndex = toDeleteDirs.get(f);
									if (onIndex!=null && !(childDirectories.get(onIndex).linkcount==2
										&& f.linkcount>2)) {
										toDeleteDirs.remove(f);
									}
									//If we already knew about this item, then update its size (which may have changed for directories)
									if (onIndex!=null) {
										childDirectories.get(onIndex).size = f.size;
									}
								} else {
									newFiles.add(f);
									
									toDeleteFiles.remove(f);
								}
							}

							Boolean reloadTable = false;
							if (!childFiles.equals(newFiles)) {
								reloadTable = true;
								childFiles = newFiles;
							}
							
							
							//Generate arrays needed for treenode removal event, and table removal event:
							TreeNode[] deadDirs = new TreeNode[toDeleteDirs.size()];
							int[] deadDirIndices = new int[toDeleteDirs.size()];
							
							generateTreeNodeRemovalEvent(toDeleteDirs,
									deadDirs, deadDirIndices);
							
							//Now find the insertions:
							LinkedHashMap<FileSystemEntry, Integer> insertedDirs = new LinkedHashMap<FileSystemEntry, Integer>();
							for (int i=0; i<newDirs.size(); i++) {
								if (!childDirectoryIndices.containsKey(newDirs.get(i))) insertedDirs.put(newDirs.get(i), i);
							}
							
							//Generate arrays needed for the event:
							TreeNode[] insDirs = new TreeNode[insertedDirs.size()];
							int[] insDirIndices = new int[insertedDirs.size()];
							
							generateTreeNodeInsertionEvent(newDirs, insertedDirs,
									insDirs, insDirIndices);
							
							if (reloadTable || !toDeleteDirs.isEmpty() || !insertedDirs.isEmpty()) {
								if (fs.selected==FileSystemEntry.this) fs.tableChanged(new TableModelEvent(fs));
							}
							
							reconsiderer = new ReconsiderTask();
							try {
								fs.cacheExpirer.schedule(reconsiderer, FS2Constants.CLIENT_REFRESH_FILESYSTEM_CACHE_INTERVAL);
							} catch (IllegalStateException e) {
								Logger.warn("Couldn't schedule cache reconsideration on cancelled timer: "+e);
							}
						} 
					}
					
					/**
					 * Creates arrays of child dirs inserted and then issues the events.
					 * @param newDirs
					 * @param inserted
					 * @param insDirs
					 * @param insDirIndices
					 */
					private void generateTreeNodeInsertionEvent(
							final ArrayList<FileSystemEntry> newDirs,
							LinkedHashMap<FileSystemEntry, Integer> inserted,
							TreeNode[] insDirs, int[] insDirIndices) {
						if (!inserted.isEmpty()) {
							int counter = 0;
							for (Entry<FileSystemEntry, Integer> e : inserted.entrySet()) {
								insDirs[counter] = e.getKey();
								insDirIndices[counter++] = e.getValue();
							}
							
							//Make the structures consistent:
							childDirectoryIndices.clear();
							for (int i = 0; i<newDirs.size(); i++) {
								childDirectoryIndices.put(newDirs.get(i), i);
							}
							childDirectories.clear();
							childDirectories.addAll(newDirs);
							
							//Trigger the insertion event:
							fs.treeNodesInserted(new TreeModelEvent(FileSystemEntry.this, getPath(), insDirIndices, insDirs));
						}
					}
					
					/**
					 * Creates arrays of dirs that are going to go, issue the events and update the childDirectories structures.
					 */
					private void generateTreeNodeRemovalEvent(
							LinkedHashMap<FileSystemEntry, Integer> toDeleteDirs,
							TreeNode[] deadDirs, int[] deadDirIndices) {
						if (!toDeleteDirs.isEmpty()) {
							int counter = 0;
							for (Entry<FileSystemEntry, Integer> e : toDeleteDirs.entrySet()) {
								deadDirs[counter] = e.getKey();
								deadDirIndices[counter++] = e.getValue();
							}
							
							//Re-assemble the childDirectories but without the deleted nodes... 
							ArrayList<FileSystemEntry> cdp = new ArrayList<FileSystemEntry>();
							counter = 0;
							childDirectoryIndices.clear();
							for (FileSystemEntry f : childDirectories) {
								if (!toDeleteDirs.containsKey(f)) {
									cdp.add(f);
									childDirectoryIndices.put(f, counter++);
								}
							}
							//Now re-fill childDirectories with cdp:
							childDirectories.clear();
							childDirectories.addAll(cdp);
							
							//Trigger the nodes removed event...
							fs.treeNodesRemoved(new TreeModelEvent(FileSystemEntry.this, getPath(), deadDirIndices, deadDirs));
							for (FileSystemEntry f : toDeleteDirs.keySet()) {
								f.purge();  //prevent them from performing future updates.
							}
						}
					}
				});
			} catch (Exception t) {
				Logger.warn("Exception caught in indexnode query thread: "+t);
				t.printStackTrace();
			}
		}
	}
	
	/**
	 * This timer task is used to schedule a test at a later date:
	 * Update this directory or purge it from the cache?
	 * 
	 * We might want to update the cache rather than trash it if it is the CWD of a processes or visible in a tree, for example.
	 * 
	 * @author gary
	 *
	 */
	private class ReconsiderTask extends TimerTask {
		
		@Override
		public void run() {
			//to be on the safe side of things (although these tasks should not be scheduled periodically anyway:
			this.cancel();
			
			if (fs.pathRequired(getPath())) {
				//This path is still in active use, so refresh it.
				fs.updatePool.submit(new UpdateNode());
				//Logger.log("Indexnode Request Queue: "+((ThreadPoolExecutor)fs.updatePool).getQueue().size());
			} else {
				//purge it, this has to happen in the swing thread due to it causing events that need locking.
				try {
					Utilities.dispatch(new Runnable() {
						@Override
						public void run() {
							purge();
						}
					});
				} catch (Exception e) {
					Logger.warn("Exception during purge() dispatch: "+e);
					e.printStackTrace();
				}
			}
		}
		
	}
	
	/** The indexnode that this item resides on. (if any) */
	private IndexNode node; 
	
	/**The main filesystem that this item belongs to.
	 * used to help with cache expiry choices
	 * and for notification when things change.
	 */
	private FileSystem fs;
	
	private ReconsiderTask reconsiderer;
	
	private TreeNode parent;

	private Object childrenMutex = new Object();
	private ArrayList<FileSystemEntry> childDirectories; //the sorted list of children. This is left null until the node is initialised.
	private LinkedHashMap<FileSystemEntry, Integer> childDirectoryIndices;  //provides constant time lookup of indices for child directories, conviniently it will also iterate in order if filled in order. (This is needed for awkward swing events)
	private ArrayList<FileSystemEntry> childFiles; //The files within this directory.
	
	private boolean directory = false;  //true iff this entry represents a directory.
	private boolean search = false;     //true iff this entry represents a search.
	private String name;     //this entry's textual representation: unique within its parent. This can be the search terms if this is a search.
	private String hash; 	 //for files the pseudo-unique identifier for this file.
	private long size;       //for files their size in bytes.
	/**
	 * for directories the number of child directories it contains+2;
	 * In the tree model this is just used to determine if there are child directories, not the precice number.
	 * 
	 * This should only be used to poll if a directory is empty without having to download a list of its children.
	 */
	private int linkcount;   
	private String oneAlias = ""; //The single client alias returned by the indexnode for this file.
	private int altCount;    //The number of alternatives 
	private String indexNodePath; //The path to this directory on its indexnode.
	public boolean isLoadingNode = false;
	
	/**
	 * Returns true iff this node represents a search root.
	 * @return
	 */
	public boolean isSearch() {
		return search;
	}
	
	/**
	 * Returns true iff this node represents a directory.
	 * @return
	 */
	public boolean isDirectory() {
		return directory;
	}
	
	/**
	 * Returns the name of this node.
	 * @return
	 */
	public String getName() {
		return name;
	}
	
	/**
	 * Returns the search terms if this is a searchroot.
	 * @return
	 */
	public String getSearchTerms() {
		if (!search) throw new IllegalStateException("getSearchTerms() is only defined for searchroots.");
		return name;
	}
	
	/**
	 * Returns the hash of this file as a string.
	 * @return
	 */
	public String getHash() {
		if (directory) throw new IllegalStateException("getHash() is only defined for files.");
		return hash;
	}
	
	/**
	 * Gets the alias of a client that owns this file. It is undefined which client this will be if there are more than one sharing this file.
	 * It is the empty string for directories.
	 * @return
	 */
	public String getAlias() {
		return oneAlias;
	}
	
	/**
	 * Returns the size in bytes of this item if it is a file, or the recursive size of this directory.
	 * @return
	 */
	public long getSize() {
		if ((isDirectory() && indexNodePath.equals("")) || isSearch()) {
			long ret = 0;
			
			synchronized (childrenMutex) {
				if (childDirectories!=null) {
					for (FileSystemEntry fse : childDirectories) {
						ret+=fse.getSize();
					}
					for (FileSystemEntry fse : childFiles) {
						ret+=fse.getSize();
					}
				}
			}
			
			return ret;
		} else {
			return size;
		}
	}
	
	/**
	 * Returns the link count of this directory. Although not very meaningful in FS2 it retains its unix meaning
	 * of 2+number of child directories. This is useful for determining if the directory has children without
	 * actually querying the indexnode ( as getChildCount() would ).
	 * 
	 * Unless you have a compelling reason to use this (such as drawing a partial tree structure interactively), dont.
	 * 
	 * @return
	 */
	public long getLinkCount() {
		if (!directory) throw new IllegalStateException("getLinkCount() is only defined for directories.");
		if (childDirectories!=null) {
			return childDirectories.size()+2;
		}
		return linkcount;
	}
	
	/**
	 * Gets the path to this directory on the given indexnode in the same form as the indexnode originally specified it.
	 * @return
	 */
	public String getIndexNodePath() {
		if (!directory) throw new IllegalStateException("getIndexNodePath() is only defined for directories.");
		return indexNodePath;
	}
	
	/**
	 * Returns the indexnode that this item resides on.
	 * @return
	 */
	public IndexNode getIndexNode() {
		return node;
	}
	
	/**
	 * This method generates a '(loading)' child node and schedules a refresh from the indexnode.
	 */
	void initialiseNode() {
		synchronized (childrenMutex) {
			childDirectories = new ArrayList<FileSystemEntry>();
		
			childDirectoryIndices = new LinkedHashMap<FileSystemEntry, Integer>();
			childFiles = new ArrayList<FileSystemEntry>();
			
			FileSystemEntry lc = generateLoadingChild();
			childDirectories.add(lc);
			childDirectoryIndices.put(lc, 0);
			
			fs.updatePool.submit(new UpdateNode());

			fs.treeNodesInserted(new TreeModelEvent(this, getPath(), new int[] {0}, new TreeNode[] {childDirectories.get(0)}));
			if (fs.selected==this) fs.tableChanged(new TableModelEvent(fs));
		}
	}
	
	/**
	 * Indicates that this entry's scheduled refresh should be moved forward to right now.
	 * This will effectively cancel the cache-refresh for this node and submit the refresh task to the pool immediately.
	 */
	void updateNow() {
		if (reconsiderer!=null) reconsiderer.run();
	}
	
	@Override
	public Enumeration<FileSystemEntry> children() {
		synchronized (childrenMutex) {
			if (childDirectories==null) initialiseNode();
			return new Util.EnumerationWrapper<FileSystemEntry>(new ArrayList<FileSystemEntry>(childDirectories));
		}
	}
	
	/**
	 * Gets the nth child (from a sorted list) of children including both files and directories.
	 * Note that all directories come before files.
	 * @param index
	 * @return the entry at this index.
	 * @throws IndexOutOfBoundsException if the request is not within the acceptable range.
	 */
	public FileSystemEntry getFromAllChildren(int index) {
		synchronized (childrenMutex) {
//			try {
				if (childDirectories==null) initialiseNode();
				if (index<0) throw new IndexOutOfBoundsException("Request: "+index+", have: "+(childDirectories.size()+childFiles.size()));
				if (index<childDirectories.size()) {
					return childDirectories.get(index);
				} else if (index<childFiles.size()+childDirectories.size()) {
					return childFiles.get(index-childDirectories.size());
				} else {
					throw new IndexOutOfBoundsException("Request: "+index+", have: "+(childDirectories.size()+childFiles.size()));
				}
//			} catch (IndexOutOfBoundsException e) {
//				Logger.warn("filesTable: "+e);
//				return null;
//			}
		}
	}
	
	/**
	 * Returns an arraylist of all children nodes. This is expensive, don't do it often.
	 * @return
	 */
	public ArrayList<FileSystemEntry> getAllChildren() {
		synchronized (childrenMutex) {
			ArrayList<FileSystemEntry> ret = new ArrayList<FileSystemEntry>();
			if (childDirectories!=null) {
				ret.addAll(childDirectories);
				ret.addAll(childFiles);
			}
			return ret;
		}
	}
	
	@Override
	public boolean getAllowsChildren() {
		return directory;
	}
	
	@Override
	public int getChildCount() {
		synchronized (childrenMutex) {
			if (linkcount==2) return 0; else {
				if (childDirectories==null) {
					if (fs.pathRequired(getPath())) initialiseNode(); else return 1; //
				}
				return childDirectories.size();
			}
		}
	}
	
	/**
	 * Returns the number of child directories+ the number of files.
	 * @return
	 */
	public int getAllChildrenCount() {
		synchronized (childrenMutex) {
			if (childDirectories==null) initialiseNode();
			return childDirectories.size()+childFiles.size();
		}
	}
	
	@Override
	public TreeNode getChildAt(int childIndex) {
		synchronized (childrenMutex) {
			if (childDirectories==null) initialiseNode();
			return childDirectories.get(childIndex);
		}
	}
	
	@Override
	public int getIndex(TreeNode node) {
		synchronized (childrenMutex) {
			if (childDirectories==null) initialiseNode();
			return (childDirectoryIndices.containsKey(node) ? childDirectoryIndices.get(node) : -1);
		}
	}
	
	@Override
	public TreeNode getParent() {
		return parent;
	}
	
	@Override
	public boolean isLeaf() {
		return (linkcount==2);
	}
	
	/**
	 * Gets the files contained within this directory, or null if uninitialised.
	 * @return
	 */
	public ArrayList<FileSystemEntry> getFiles() {
		return childFiles;
	}
	
	/**
	 * Returns the number of identical files on this single indexnode.
	 * The full alternatives count would require a request to every other indexnode.
	 * @return
	 */
	public int getAlternativesCount() {
		return altCount;
	}
	
	static FileSystemEntry generateBrowseRoot(TreeNode parent, FileSystem fs) {
		FileSystemEntry node = new FileSystemEntry();
		node.name = "(everything)";
		node.directory = true;
		node.linkcount = 3; //It is expandable always.
		node.parent = parent;
		node.fs = fs;
		node.indexNodePath = "";
		return node;
	}
	
	/**
	 * Generates a new search node.
	 * Don't forget to initialise it!
	 * @param parent
	 * @param fs
	 * @param terms
	 * @return
	 */
	static FileSystemEntry generateSearchNode(TreeNode parent, FileSystem fs, String terms) {
		FileSystemEntry node = new FileSystemEntry();
		node.name = terms;
		node.directory = true;
		node.search = true;
		node.linkcount = 3; //It is expandable always.
		node.parent = parent;
		node.fs = fs;
		node.indexNodePath = "";
		return node;
	}
	
	FileSystemEntry generateLoadingChild() {
		FileSystemEntry ret = generateUninitialisedChildDirectory("(loading)", this.node, 2 /*empty*/, "", 0);
		ret.isLoadingNode = true;
		return ret;
	}
	
	/**
	 * Returns a new child directory of this node that is uninitialised (ie, has no children nor scheduled children checks yet)
	 * @return
	 */
	FileSystemEntry generateUninitialisedChildDirectory(String name, IndexNode onNode, int linkCount, String path, long size) {
		FileSystemEntry ret = new FileSystemEntry();
		ret.name = name;
		ret.directory = true;
		ret.linkcount = linkCount;
		ret.parent = this;
		ret.fs = fs;
		ret.node = onNode;
		ret.indexNodePath = path;
		ret.size = size;
		return ret;
	}
	
	/**
	 * Returns a new child file of this directory.
	 * @param name
	 * @param onNode
	 * @param size
	 * @param hash
	 * @param alias
	 * @param altCount
	 * @return
	 */
	FileSystemEntry generateChildFile(String name, IndexNode onNode, long size, String hash, String alias, int altCount) {
		FileSystemEntry ret = new FileSystemEntry();
		ret.name = name;
		ret.parent = this;
		ret.fs = fs;
		ret.node = onNode;
		ret.size = size;
		ret.hash = hash;
		ret.oneAlias = alias;
		ret.altCount = altCount;
		return ret;
	}
	
	@Override
	/**
	 * Determines if this filesystem entry is equal to another in a filesystem sense, rather than a java object sense.
	 * 
	 * Directories are equal if their names, indexnodes, and finally, indexnodepaths are equal.
	 * Note: two identical searches are not permitted! :)
	 * 
	 * Files are 'equal' if their fs2-hashes match.
	 */
	public boolean equals(Object obj) {
		if (obj==this) return true;
		if (obj==null) return false;
		if (!(obj instanceof FileSystemEntry)) return false;
		FileSystemEntry other = (FileSystemEntry) obj;
		
		if (other.directory!=this.directory) return false;
		if (directory) {
			//return this.node==other.node && samePath(this.getPath(), other.getPath());
			return this.name.equals(other.name) && this.node==other.node && this.indexNodePath.equals(other.indexNodePath);
		} else {
			return this.hash.equals(other.hash);
		}
	}
	
	@Override
	public int hashCode() {
		int out;
		if (directory) {
			out = (node!=null ? node.hashCode() : 1);
			out *= 127;
			out += System.identityHashCode(parent);
			out *= 127;
			out += name.hashCode();
		} else {
			out = hash.hashCode();
		}
		return out;
	}
	
	public String toString() {
		return this.getName();
	};
	
	TreePath cachedPath;
	
	/**
	 * Gets the tree path that represents the path to this node from the root of the tree model.
	 * This is MORE than just the path in FS2 as the treemodel adds a 'master root' to contain searches as subtrees too.
	 * @return the path to this filesystem node in the treemodel.
	 */
	public TreePath getPath() {
		if (cachedPath!=null) return cachedPath;
		LinkedList<Object> path = new LinkedList<Object>();
		
		path.push(this);
		
		TreeNode p = parent;
		while (p!=null) {
			path.push(p);
			p = p.getParent();
		}
		
		cachedPath = new TreePath(path.toArray());
		return cachedPath;
	}

	/**
	 * Recurse through all child directories (depth first search) and cancel any refresh timers, and clear children (dirs and files) hash tables, arrays etc.
	 * This node will become 'uninitialised' again, and the children will become orphans (and hopefully garbage collected).
	 */
	void purge() {
		synchronized (childrenMutex) {
			if (reconsiderer!=null) reconsiderer.cancel();
			if (childDirectories!=null) {
				for (FileSystemEntry f : childDirectories) {
					f.purge();
				}
				
				int[] deadIndices = new int[childDirectoryIndices.size()];
				TreeNode[] deadNodes = childDirectories.toArray(new TreeNode[] {});
				for (int i=0; i<deadIndices.length; i++) deadIndices[i]=i;
				
				fs.treeNodesRemoved(new TreeModelEvent(this, getPath(), deadIndices, deadNodes));
				
				childDirectories.clear();
				childDirectoryIndices.clear();
				childFiles.clear();
				
				if (fs.selected==this) fs.tableChanged(new TableModelEvent(fs));
				
				childDirectories = null;
				childDirectoryIndices = null;
				childFiles = null;
			}
		}
	}

	@Override
	public int compareTo(FileSystemEntry o) {
		return this.name.compareToIgnoreCase(o.name);
	}
	
}
