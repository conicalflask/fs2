package client.indexnode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.table.TableModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import client.gui.Utilities;
import client.indexnode.FileSystem.FileSystemRoot.SearchesRoot;


import common.FS2Constants;
import common.Logger;
import common.NamedThreadFactory;
import common.Util;
import common.Util.FileSize;

/**
 * The root class that deals with the client application's view of the FS2 filesystem.
 * This reads from indexnodes and maintains a local, cached, sortable copy.
 * 
 * This also implements the TreeModel interface so that it may be used in a JTree.
 * The TreeModel interface can also be used to efficiently implement a filesystem/other representation
 * of FS2 using this object as the data and event source.
 * However, this treemodel makes heavy use of asynchronous updates so unless your presentation layer
 * is capable of asynchronous events (FSNotify?) this may not be very useful.
 * 
 * When requesting the children of an uncached directory it will immediately return
 * a single empty child directory with the name "(loading)". Once the actual contents of the
 * directory have been loaded a {@link TreeModelEvent} will be triggered erasing the "(loading)" node
 * and adding the real contents of the directory. 
 * 
 * This also implements a {@link TableModel} which will supply events if any of the files or directories
 * change within a filesystem entry, you can chose which entry with the setSelectedEntry() method.
 * The columns supplied in the table model include: name, size, alias.
 * 
 * @author gary
 */
public class FileSystem implements TreeModel, TableModel {

	/**
	 * This is the magic (hidden) root node of the FS2 tree model.
	 * It contains two items always, one for the magic master root of browsing and one for all of searches.
	 * @author gary
	 */
	public class FileSystemRoot implements TreeNode {

		ArrayList<TreeNode> subtrees = new ArrayList<TreeNode>();
		private FileSystemEntry root = FileSystemEntry.generateBrowseRoot(this, FileSystem.this);
		SearchesRoot searches = new SearchesRoot();
		
		/**
		 * The searches node.
		 * @author gary
		 */
		public class SearchesRoot implements TreeNode, ListableEntry {

			private SearchesRoot() {}
			
			ArrayList<FileSystemEntry> searches = new ArrayList<FileSystemEntry>();
			
			@Override
			public Enumeration<TreeNode> children() {
				synchronized (searches) {
					return new Util.EnumerationWrapper<TreeNode>(new ArrayList<TreeNode>(searches));
				}
			}

			@Override
			public boolean getAllowsChildren() {
				return true;
			}

			@Override
			public TreeNode getChildAt(int childIndex) {
				synchronized (searches) {
					return searches.get(childIndex);
				}
			}

			@Override
			public int getChildCount() {
				synchronized (searches) {
					return searches.size();
				}
			}

			@Override
			public int getIndex(TreeNode node) {
				synchronized (searches) {
					return searches.indexOf(node);
				}
			}

			@Override
			public TreeNode getParent() {
				return FileSystemRoot.this;
			}

			@Override
			public boolean isLeaf() {
				return false;
			}
			
			public FileSystemEntry newSearch(String searchTerms) {
				synchronized (searches) {
					//Return the existing search if it's already there.
					for (FileSystemEntry f : searches) {
						if (f.isSearch() && searchTerms.equals(f.getSearchTerms())) {
							return f;
						}
					}
					
					FileSystemEntry search = FileSystemEntry.generateSearchNode(this, FileSystem.this, searchTerms);
					searches.add(search);
					treeNodesInserted(new TreeModelEvent(this, getPath(), new int[] {searches.size()-1}, new Object[] {search}));
					if (selected==SearchesRoot.this) tableChanged(new TableModelEvent(FileSystem.this));
					search.initialiseNode();
					return search;
				}
			}
			
			public void removeSearch(FileSystemEntry search) {
				if (!search.isSearch()) throw new IllegalArgumentException("Only searches can be removed from the master root.");
				synchronized (searches) {
					int oldIdx = searches.indexOf(search);
					searches.remove(oldIdx);
					treeNodesRemoved(new TreeModelEvent(this, getPath(), new int[] {oldIdx}, new Object[] {search}));
					if (selected==SearchesRoot.this) tableChanged(new TableModelEvent(FileSystem.this));
				}
			}
			
			public TreePath getPath() {
				return new TreePath(new Object[] {FileSystemRoot.this, this});
			}
			
			@Override
			public String toString() {
				return "(searches)";
			}

			@Override
			public int getAllChildrenCount() {
				return getChildCount();
			}

			@Override
			public FileSystemEntry getFromAllChildren(int index) {
				return (FileSystemEntry) getChildAt(index);
			}

			@Override
			public long getSize() {
				long ret = 0;
				synchronized (searches) {
					for (FileSystemEntry s : searches) {
						ret+=s.getSize();
					}
				}
				return ret;
			}
			
			@Override
			public String getName() {
				return toString();
			}

			@Override
			public Collection<FileSystemEntry> getAllChildren() {
				return searches;
			}
		}
		
		private FileSystemRoot() {
			subtrees.add(root);
			subtrees.add(searches);
		}
		
		@SuppressWarnings({ "unchecked", "rawtypes" })
		@Override
		public Enumeration children() {
			//Copy the array whilst synchronized, stupid enumerations :@
			synchronized (subtrees) {
				return new Util.EnumerationWrapper(new ArrayList<TreeNode>(subtrees));
			}
		}

		@Override
		public boolean getAllowsChildren() {
			return true;
		}

		@Override
		public TreeNode getChildAt(int childIndex) {
			synchronized (subtrees) {
				return subtrees.get(childIndex);
			}
		}

		@Override
		public int getChildCount() {
			synchronized (subtrees) {
				return subtrees.size();
			}
		}

		@Override
		public int getIndex(TreeNode node) {
			synchronized (subtrees) {
				return subtrees.indexOf(node);
			}
		}

		@Override
		public TreeNode getParent() {
			return null; //I'm the god damned ROOT :D
		}

		@Override
		public boolean isLeaf() {
			return false;
		}

		public FileSystemEntry getBrowseRoot() {
			return root;
		}
		
		@Override
		public String toString() {
			return "I should be hidden. (Master root)";
		}

		public SearchesRoot getSearchRoot() {
			return searches;
		}
	}
	
	public interface PathRequired {
		/**
		 * Determines if the view layer is still using the tree path specified.
		 * 
		 * This allows the cache to make more educated decisions about whether to
		 * update from the indexnode again or simply disgard the cached directory information.
		 * 
		 * @param path the path in question.
		 * @return Return true if the path is still in use and should preferentially be updated rather than destroyed.
		 */
		public boolean required(TreePath path);
	}
	
	Timer cacheExpirer = new Timer("FileSystem Cache Expirer", true);
	ExecutorService updatePool = Executors.newFixedThreadPool(FS2Constants.CLIENT_MAX_CONCURRENT_FILESYSTEM_REQUESTS, new NamedThreadFactory(true, "Indexnode FileSystem Request"));
	IndexNodeCommunicator comm;
	HashSet<TreeModelListener> listeners = new HashSet<TreeModelListener>();
	FileSystemRoot root = new FileSystemRoot();
	PathRequired pathRequired;
	
	/**
	 * Constructs a new filesystem using the specified communicator to access the indexnodes.
	 * @param comm
	 */
	public FileSystem(IndexNodeCommunicator comm) {
		this.comm = comm;
	}
	
	/**
	 * When using this filesystem for an interactive view a PathRequired object should be provided.
	 * This allows the cache to decide between updating and disgarding information.
	 * @param pathRequired
	 */
	public void setPathRequired(PathRequired pathRequired) {
		this.pathRequired = pathRequired;
	}
	
	/**
	 * For internal convinience.
	 * The selected directory is always needed.
	 */
	boolean pathRequired(TreePath path) {
		if (path.getLastPathComponent()==selected) return true;
		if (pathRequired==null) return false; else return pathRequired.required(path);
	}
	
	@Override
	public void addTreeModelListener(TreeModelListener l) {
		synchronized (listeners) {
			listeners.add(l);
		}
	}

	@Override
	public Object getChild(Object parent, int index) {
		return ((TreeNode) parent).getChildAt(index);
	}

	@Override
	public int getChildCount(Object parent) {
		return ((TreeNode) parent).getChildCount();
	}

	@Override
	public int getIndexOfChild(Object parent, Object child) {
		return ((TreeNode) parent).getIndex((TreeNode)child);
	}

	@Override
	public Object getRoot() {
		return root;
	}

	@Override
	public boolean isLeaf(Object node) {
		return ((TreeNode) node).isLeaf();
	}

	@Override
	public void removeTreeModelListener(TreeModelListener l) {
		synchronized (listeners) {
			listeners.remove(l);
		}
	}

	@Override
	public void valueForPathChanged(TreePath path, Object newValue) {
		//Do nothing. Our tree is not user modifiable.
	}
	
	public void shutdown() {
		cacheExpirer.cancel();
		updatePool.shutdown();
	}
	
	/**
	 * Gets the root directory in FS2. This will be the merged directories from all indexnodes.
	 * As such there may be some name clashes. The presentation/view layer may wish to disambiguate these.
	 * @return
	 */
	public FileSystemEntry getBrowseRoot() {
		return root.getBrowseRoot();
	}
	
	/**
	 * Gets the root of searches in the filesystem
	 * @return
	 */
	public SearchesRoot getSearchRoot() {
		return root.getSearchRoot();
	}
	
	
	/**
	 * Searches the indexnodes for the given terms (a non-alphanumeric separated list of keywords)
	 * @param terms
	 * @return
	 */
	public FileSystemEntry newSearch(String terms) {
		return root.searches.newSearch(terms);
	}
	
	/**
	 * Removes the given search term from the overall root node.
	 * This will also prevent it from being updated in the future.
	 * @param search
	 */
	public void removeSearch(FileSystemEntry search) {
		root.searches.removeSearch(search);
	}
	
	/**
	 * Removes all searches from the filesystem.
	 */
	public void removeAllSearches() {
		List<FileSystemEntry> searchcopy = new ArrayList<FileSystemEntry>(getSearchRoot().searches);
		for (FileSystemEntry fse : searchcopy) removeSearch(fse);
	}

	/**
	 * Notifies {@link TreeModelListener}s that this filesystem has changed, in the swing thread.
	 * @param e
	 */
	void treeNodesInserted(final TreeModelEvent e) {
		try {
			Utilities.dispatch(new Runnable() {
				@Override
				public void run() {
					synchronized (listeners) {
						for (TreeModelListener l : listeners) {
							l.treeNodesInserted(e);
						}
					}
				}
			});
		}  catch (Exception e1) {
			Logger.warn("Exception during treeNodesInserted() dispatch: "+e1);
			e1.printStackTrace();
		}
	}

	/**
	 * Notifies {@link TreeModelListener}s that this filesystem has changed, in the swing thread.
	 * @param e
	 */
	void treeNodesRemoved(final TreeModelEvent e) {
		try {
			Utilities.dispatch(new Runnable() {
				@Override
				public void run() {
					synchronized (listeners) {
						for (TreeModelListener l : listeners) {
							l.treeNodesRemoved(e);
						}
					}
				}
			});
		}  catch (Exception e1) {
			Logger.warn("Exception during treeNodesRemoved() dispatch: "+e1);
			e1.printStackTrace();
		}
	}

	ListableEntry selected;
	
	/**
	 * Sets the filesystem entry that will cause table-model events.
	 * When this is changed a {@link TableModelEvent} will be triggered to completely replace the table contents.
	 * @param selected
	 */
	public void setSelectedEntry(ListableEntry selected) {
		if (selected!=this.selected) {
			this.selected = selected; //It's critical that this is set before the tableChanged event is triggered!
			tableChanged(new TableModelEvent(this));
		}
		this.selected = selected;
	}
	
	public class Source implements Comparable<Source> {
		Integer sourcesCount;
		String oneAlias;
		
		public Source(int count, String oneAlias) {
			this.oneAlias = oneAlias;
			this.sourcesCount = count;
		}
		
		@Override
		public int compareTo(Source o) {
			return sourcesCount.compareTo(o.sourcesCount);
		}
		
		@Override
		public String toString() {
			return (sourcesCount>1 ? sourcesCount.toString()+" sources" : oneAlias);
		}
	}
	
	String[] tableColumns = { "Name", "Size", "Source"};
	Class<?>[] tableClasses = { String.class, FileSize.class, Source.class};
	public static final int NAME_IDX = 0;
	public static final int SIZE_IDX = 1;
	public static final int SOURCE_IDX = 2;
	
	@Override
	public void addTableModelListener(TableModelListener l) {
		synchronized (tableModelListeners) {
			tableModelListeners.add(l);
		}
	}

	@Override
	public Class<?> getColumnClass(int columnIndex) {
		return tableClasses[columnIndex];
	}

	@Override
	public int getColumnCount() {
		return tableColumns.length;
	}

	@Override
	public String getColumnName(int columnIndex) {
		return tableColumns[columnIndex];
	}

	@Override
	public int getRowCount() {
		if (selected!=null){
			return selected.getAllChildrenCount();
		} else {
			return 0;
		}
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		if (selected!=null) {
			FileSystemEntry fse = selected.getFromAllChildren(rowIndex);
			//if (fse==null) return null;
			if (columnIndex==NAME_IDX) {
				return fse.getName();
			} else if (columnIndex==SIZE_IDX) {
				return new FileSize(fse.getSize());
			} else {
				return new Source(fse.getAlternativesCount(), fse.getAlias());
			}
		} else {
			return null;
		}
	}

	public FileSystemEntry getEntryForRow(int row) {
		if (selected!=null) {
			return selected.getFromAllChildren(row);
		} else return null;
	}
	
	@Override
	public boolean isCellEditable(int rowIndex, int columnIndex) {
		return false;
	}

	@Override
	public void removeTableModelListener(TableModelListener l) {
		synchronized (tableModelListeners) {
			tableModelListeners.remove(l);
		}
	}
	
	@Override
	public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
		throw new UnsupportedOperationException("Table not editable");
	}
	
	HashSet<TableModelListener> tableModelListeners = new HashSet<TableModelListener>();
	
	void tableChanged(final TableModelEvent e) {
		try {
			Utilities.dispatch(new Runnable() {
				@Override
				public void run() {
					synchronized (tableModelListeners) {
						for (TableModelListener l : tableModelListeners) {
							l.tableChanged(e);
						}
					}
				}
			});
		} catch (Exception e1) {
			Logger.warn("Exception during tableChanged() dispatch: "+e1);
			e1.printStackTrace();
		}
	}
	
	/**
	 * Indicates to the filesystem that the indexnodes have changed so the files available are likely to have changed too.
	 */
	public void notifyNewIndexnode() {
		root.root.updateNow();
	}
	
}
