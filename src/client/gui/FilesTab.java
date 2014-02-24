package client.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.image.ImageObserver;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Stack;
import java.util.WeakHashMap;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.ProgressMonitor;
import javax.swing.Timer;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;

import common.FS2Constants;
import common.ProgressTracker;
import common.Util;
import client.indexnode.FileSystem;
import client.indexnode.FileSystemEntry;
import client.indexnode.ListableEntry;
import client.indexnode.FileSystem.FileSystemRoot;
import client.indexnode.FileSystem.PathRequired;
import client.indexnode.downloadcontroller.DownloadQueue.DownloadSubmissionListener;
import client.platform.ClientConfigDefaults.CK;
import client.gui.MainFrame.StatusHint;

@SuppressWarnings("serial")
public class FilesTab extends TabItem implements 	ActionListener,
													MouseListener, 
													TreeSelectionListener, 
													MouseMotionListener, 
													PropertyChangeListener,
													TreeExpansionListener,
													ListSelectionListener {

	private JTextField searchQuery;
	private JButton searchQueryButton;

	public FilesTab(JTabbedPane pane, MainFrame frame) {
		super(pane, frame, "Files", FS2Tab.FILES, frame.gui.util.getImage("files"));
		setClosable(false);
		setLayout(new BorderLayout());
		add(createSearchBar(), BorderLayout.NORTH);
		add(createBrowseSection(), BorderLayout.CENTER);
		setupPopups();
		
		collapseTimer = new Timer(FS2Constants.CLIENT_BROWSETREE_COLLAPSE_POLL_INTERVAL, this);
		collapseTimer.start();
	}
	
	private class TreePopup extends EasierPopup {
		
		EPopup infoItem, removeSearch;
		
		public TreePopup(final MainFrame frame) {
			super(frame);
			
			infoItem = new EPopup("size?", null, null, "Basic information about the selection");
			infoItem.setEnabled(false);
			
			this.addSeparator();
			
			removeSearch = new EPopup("Remove selected searches", new PopupAction() {
				@Override
				public void doAction(ActionEvent e) {
					for (TreePath path : browseTree.getSelectionPaths()) {
						ListableEntry selected = (ListableEntry) path.getLastPathComponent();
						if ((selected instanceof FileSystemEntry) && ((FileSystemEntry)selected).isSearch()) {
							fs.removeSearch((FileSystemEntry) selected);
						}
					}
				}
			}, frame.gui.util.getImage("removesearch"), "Removes this search, reducing load on the indexnodes");
			
			this.addSeparator();
			
			new EPopup("Download", new PopupAction() {
				@Override
				public void doAction(ActionEvent e) {
					for (TreePath path : browseTree.getSelectionPaths()) {
						ListableEntry selected = (ListableEntry) path.getLastPathComponent();
						downloadListableToDirectory(selected, null);
					}
				}
			}, frame.gui.util.getImage("download"), "Downloads all of the one selected item to the default download directory");
			
			new EPopup("Download to...", new PopupAction() {
				@Override
				public void doAction(ActionEvent e) {
					JFileChooser fc = new JFileChooser(new File(frame.gui.conf.getString(CK.LAST_CUSTOM_DOWNLOAD_DIRECTORY)));
					fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
					if (fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
						frame.gui.conf.putString(CK.LAST_CUSTOM_DOWNLOAD_DIRECTORY, fc.getSelectedFile().getPath());
						for (TreePath path : browseTree.getSelectionPaths()) {
							ListableEntry selected = (ListableEntry) path.getLastPathComponent();
							downloadListableToDirectory(selected, fc.getSelectedFile());
						}
					}
				}
			}, frame.gui.util.getImage("downloadto"), "Downloads all of the one selected item to a directory of your choice");
		}
		
		@Override
		protected boolean popupDecision(MouseEvent e) {
			int count = browseTree.getSelectionCount();
			if (count==0) return false;
			
			boolean canRemoveSearches = false;
			long totalSize = 0;
			ListableEntry lastSelected = null;
			for (TreePath path : browseTree.getSelectionPaths()) {
				lastSelected = (ListableEntry) path.getLastPathComponent();
				totalSize+=lastSelected.getSize();
				canRemoveSearches |= ((lastSelected instanceof FileSystemEntry) && ((FileSystemEntry)lastSelected).isSearch());
			}
			removeSearch.setEnabled(canRemoveSearches);
			
			infoItem.setText((count==1 ? lastSelected.getName() : "Multiple items")+": "+Util.niceSize(totalSize));

			return true;
		}
	}
	
	/**
	 * Queues the selected downloads in the Download Controller's queue.
	 * 
	 * This will popup a magic progress dialog if it will take more than half a second.
	 * 
	 * @param toDownload The filesystementries to download.
	 * @param target the directory to download them into, or null to use the default.
	 */
	void downloadToDirectory(Collection<FileSystemEntry> toDownload, String prefixDirectory, File target) {
		long totalSize = 0;
		for (FileSystemEntry e : toDownload) {
			totalSize+=e.getSize();
		}
		
		DownloadSubmissionListener dsl = getDSLForDownloadBatch(totalSize, prefixDirectory==null ? "Queueing downloads..." : "Queueing downloads into '"+prefixDirectory+"'...");
		if (target==null) {
			frame.gui.dc.getQueue().submitToDefault(toDownload, prefixDirectory, dsl);
		} else {
			frame.gui.dc.getQueue().submit(target, toDownload, prefixDirectory, dsl);
		}
	}
	
	/**
	 * Downloads a listable entry (i.e. something that appears in the download tree)
	 * It is assumed it will be prefixed with the listable entry's name.
	 * @param entry
	 * @param target
	 */
	void downloadListableToDirectory(ListableEntry entry, File target) {
		frame.gui.dc.getQueue().submit(target, entry, entry.getName(), getDSLForDownloadBatch(entry.getSize(), "Queueing downloads..."));
	}
	
	private DownloadSubmissionListener getDSLForDownloadBatch(final long totalSize, String barCaption) {
		final ProgressMonitor progressBar = new ProgressMonitor(frame, barCaption, "Starting...", 0, 10000);
		Notifications.fixupPM(progressBar, true);
		
		
		final ProgressTracker tracker = new ProgressTracker();
		tracker.setExpectedMaximum(totalSize);
		return new DownloadSubmissionListener() {
			@Override
			public void fileSubmitted(FileSystemEntry file) {
				tracker.progress(file.getSize());
				progressBar.setProgress((int)(tracker.percentComplete()*100f));
				progressBar.setNote(tracker.describe());
			}
			
			@Override
			public boolean isCancelled() {
				return progressBar.isCanceled();
			}
			
			@Override
			public void complete() {
				progressBar.close();
			}
		};
	}
	
	private class TablePopup extends EasierPopup {
		
		EPopup info;
		
		public TablePopup(final MainFrame frame) {
			super(frame);
			
			info = new EPopup("info", null, null, "Basic information about the selection");
			info.setEnabled(false);
			
			addSeparator();
			
			new EPopup("Download", new PopupAction() {
				@Override
				public void doAction(ActionEvent e) {
					downloadToDirectory(getSelectedTableEntries(), null, null);
				}
			}, frame.gui.util.getImage("download"), "Downloads all of the one selected item to the default download directory");
			
			new EPopup("Download to...", new PopupAction() {
				@Override
				public void doAction(ActionEvent e) {
					JFileChooser fc = new JFileChooser(new File(frame.gui.conf.getString(CK.LAST_CUSTOM_DOWNLOAD_DIRECTORY)));
					fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
					if (fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
						frame.gui.conf.putString(CK.LAST_CUSTOM_DOWNLOAD_DIRECTORY, fc.getSelectedFile().getPath());
						downloadToDirectory(getSelectedTableEntries(), null, fc.getSelectedFile());
					}
				}
			}, frame.gui.util.getImage("downloadto"), "Downloads all of the one selected item to a directory of your choice");
		}
		
		public List<FileSystemEntry> getSelectedTableEntries() {
			ArrayList<FileSystemEntry> items = new ArrayList<FileSystemEntry>();
			for (int i : filesTable.getSelectedRows()) {
				items.add(fs.getEntryForRow(filesTable.convertRowIndexToModel(i)));
			}
			return items;
		}
		
		@Override
		protected boolean popupDecision(MouseEvent e) {
			if (filesTable.getSelectedRowCount()==0) return false;
			int[] indices = filesTable.getSelectedRows();
			long size=0;
			for (int i : indices) {
				size+=fs.getEntryForRow(filesTable.convertRowIndexToModel(i)).getSize();
			}
			info.setText(Util.niceSize(size)+" in "+indices.length+" items");
			return true;
		}
	}
	
	private void setupPopups() {
		browseTree.addMouseListener(new TreePopup(frame));
		filesTable.addMouseListener(new TablePopup(frame));
	}
	
	JPanel createSearchBar() {
		//Setup the searchbox/button at the top:
		JPanel searchQueryPanel = new JPanel();
		JLabel searchQueryLabel = new JLabel("Search: ");
		searchQuery = new JTextField();
		searchQueryButton = new JButton("Search", frame.gui.util.getImage("newsearch"));
		
		// Add the action listeners
		searchQuery.setActionCommand("search");
		searchQuery.addActionListener(this);
		searchQuery.addMouseListener(this);
		searchQueryButton.setActionCommand("search");
		searchQueryButton.addActionListener(this);
		searchQueryButton.addMouseListener(this);
		
		// Add the components to the query pane
		searchQueryPanel.setLayout(new BorderLayout());
		searchQueryPanel.add(searchQueryLabel, BorderLayout.WEST);
		searchQueryPanel.add(searchQuery, BorderLayout.CENTER);
		searchQueryPanel.add(searchQueryButton, BorderLayout.EAST);
		return searchQueryPanel;
	}
	
	JTree browseTree;
	FileSystem fs;
	JSplitPane splitPane;
	JTable filesTable;
	LoadingAnimationHelper spinner;
	JButton upButton;
	JLabel currentDirectory;
	
	/**
	 * Records the time at which tree nodes were expanded so that they may be collapsed if idle after a defined period.
	 */
	WeakHashMap<Object, Long> expandedTimes = new WeakHashMap<Object, Long>();
	Timer collapseTimer;
	
	BrowseTreeCellRenderer browseTreeRenderer;

	JSplitPane createBrowseSection() {
		spinner = new LoadingAnimationHelper();
		splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
		splitPane.setContinuousLayout(true);
		fs = frame.gui.ssvr.getIndexNodeCommunicator().getFileSystem();
		
		browseTree = new JTree(fs);
		
		//Allows the filesystem to keep refreshing directories we have visible:
		fs.setPathRequired(new PathRequired() {
			@Override
			public boolean required(TreePath path) {
				return browseTree.isExpanded(path);
			}
		});
		fs.addTreeModelListener(spinner);
		
		currentDirectory = new JLabel();
		
		browseTree.setCellRenderer(browseTreeRenderer = new BrowseTreeCellRenderer());
		browseTree.addTreeSelectionListener(this);
		browseTree.addMouseMotionListener(this);
		browseTree.addMouseListener(this);
		browseTree.addTreeExpansionListener(this);
		browseTree.setRootVisible(false);
		browseTree.expandPath(fs.getBrowseRoot().getPath());
		
		JScrollPane treeView = new JScrollPane(browseTree);
		treeView.setMinimumSize(new Dimension(100, 100));
		splitPane.setLeftComponent(treeView);
		
		filesTable = new FancierTable(fs, frame.gui.conf, CK.FILES_TABLE_COLWIDTHS);
		filesTable.addMouseListener(this);
		filesTable.getSelectionModel().addListSelectionListener(this);
		filesTable.getColumn(fs.getColumnName(0)).setCellRenderer(new FilesTableNameRenderer());

		JScrollPane filesView = new JScrollPane(filesTable);
		
		upButton = new JButton("Up");
		upButton.addActionListener(this);
		
		JPanel directoryPanel = new JPanel(new FlowLayout(FlowLayout.LEADING));
		directoryPanel.add(upButton);
		directoryPanel.add(currentDirectory);
		
		JPanel filesViewPanel = new JPanel(new BorderLayout());
		filesViewPanel.add(directoryPanel, BorderLayout.NORTH);
		filesViewPanel.add(filesView, BorderLayout.CENTER);
		
		splitPane.setRightComponent(filesViewPanel);
		splitPane.setDividerLocation(frame.gui.conf.getInt(CK.FILES_DIVIDER_LOCATION));
		splitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, this);
		
		return splitPane;
	}

	static ImageIcon search;
	private class BrowseTreeCellRenderer extends DefaultTreeCellRenderer {
		
		public BrowseTreeCellRenderer() {
			search = frame.gui.util.getImage("search");
		}
		
		@Override
		public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
			super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
			
			if (value instanceof FileSystemEntry) {
				FileSystemEntry fse = (FileSystemEntry)value;
				if (fse.isLoadingNode) {
					setIcon(spinner.getSpinner());
				} else {
					if (fse.isSearch()) {
						setSearch(fse);
					} else {
						if (expanded) {
							setIcon(openIcon);
						} else setIcon(closedIcon);
					}
				}
			} else if (value instanceof FileSystemRoot.SearchesRoot) {
				setSearch((ListableEntry) value);
			} else {
				setIcon(openIcon);
			}
			return this;
		}
		
		void setSearch(ListableEntry l) {
			if (l==lastInspectedFSE) {
				if (pointOnIcon(lastMouse)) {
					setIcon(frame.gui.util.getImage("delete"));
				} else {
					setIcon(frame.gui.util.getImage("removesearch"));
				}
			} else {
				setIcon(search);
			}
		}
		
//		@Override
//		public void paint(Graphics g) {
//			// TODO Auto-generated method stub
//			super.paint(g);
//			
//			g.setColor(Color.black);
//			g.drawRect(0, 0, g.getClipBounds().width-1, g.getClipBounds().height-1);
//		}
		
		/**
		 * Returns true if the point specified is on an icon in the browseTree.
		 * @param p The point relative to the browseTree.
		 * @return
		 */
		boolean pointOnIcon(Point p) {
			TreePath tp = browseTree.getPathForLocation(p.x, p.y);
			if (tp!=null) {
				Rectangle pb = browseTree.getPathBounds(tp);
				pb.width = 16; //turn the path bounds into the icon bounds.
				pb.height = 16;
				return pb.contains(p);
			}
			return false;
		}
		
		Point lastMouse;
		/**
		 * Notifies the renderer where the mouse was last so that mouse-overs for icons can work correctly.
		 * @param p
		 */
		void setMousePointInTree(Point p) {
			lastMouse = p;
		}
	}
	
	/**
	 * Listens to tree events and only cause animated gif induced updates when there are actually some loading nodes in existence.
	 * @author gary
	 */
	private class LoadingAnimationHelper implements ImageObserver, TreeModelListener {
		int loadingNodeCount = 0;
		ImageIcon spinner;
		
		public ImageIcon getSpinner() {
			return spinner;
		}
		
		public LoadingAnimationHelper() {
			spinner = new ImageIcon(frame.gui.util.getImageFullname("loading.gif").getImage());
			spinner.setImageObserver(this);
		}
		
		@Override
		public boolean imageUpdate(Image img, int infoflags, int x, int y,
				int width, int height) {
			if (loadingNodeCount>0) {
				browseTree.imageUpdate(img, infoflags, x, y, width, height);
				filesTable.imageUpdate(img, infoflags, x, y, width, height);
			}
			return true;
		}

		@Override
		public void treeNodesChanged(TreeModelEvent e) {}

		@Override
		public void treeNodesInserted(TreeModelEvent e) {
			for (Object t : e.getChildren()) {
				if (((FileSystemEntry)t).isLoadingNode) {
					loadingNodeCount++;
				}
			}
		}

		@Override
		public void treeNodesRemoved(TreeModelEvent e) {
			for (Object t : e.getChildren()) {
				if (((FileSystemEntry)t).isLoadingNode) {
					loadingNodeCount--;
				}
			}
		}

		@Override
		public void treeStructureChanged(TreeModelEvent e) {}
	}
	
	static ImageIcon dir;
	private class FilesTableNameRenderer extends DefaultTableCellRenderer {
		public FilesTableNameRenderer() {
			dir = frame.gui.util.getImage("type-dir");
		}
		
		@Override
		public Component getTableCellRendererComponent(JTable table,
				Object value, boolean isSelected, boolean hasFocus, int row,
				int column) {
			super.getTableCellRendererComponent(table, value, isSelected, hasFocus,
					row, column);
			
			FileSystemEntry fse = fs.getEntryForRow(filesTable.convertRowIndexToModel(row));
			if (fse.isLoadingNode) {
				setIcon(spinner.getSpinner());
			} else {
				if (fse.isDirectory()) {
					if (fse.isSearch()) {
						setIcon(search);
					} else {
						setIcon(dir);
					}
				} else {
					setIcon(frame.gui.util.getIconForType(frame.gui.util.guessType(value.toString())));
				}
			}
			return this;
		}
		
	}
	
	@Override
	public void actionPerformed(ActionEvent e) {
		if (e.getSource()==collapseTimer) {
			collapseOldNodes();
		} else if (e.getSource() == upButton) {
			TreePath path = browseTree.getSelectionPath().getParentPath();
			if (path != null && path.getPathCount() > 1) {
				browseTree.setSelectionPath(path);
				browseTree.collapsePath(path);
			}
		} else if (e.getActionCommand().equals("search")) {
			if (searchQuery.getText().equals("")) return;
			TreePath path = fs.newSearch(searchQuery.getText()).getPath();
			browseTree.expandPath(path);
			browseTree.setSelectionPath(path);
		}
	}

	@Override
	public void mouseClicked(MouseEvent e) {
		if (e.getSource()==filesTable) {
			if (e.getClickCount()==2 && e.getModifiersEx()==0) { //the modifiers test is to prevent absurd clicks like "b1, b3" registering as double-clicks.
				if (filesTable.getSelectedRow()>=0) {
					FileSystemEntry clicked = fs.getEntryForRow(filesTable.convertRowIndexToModel(filesTable.getSelectedRow()));
					if (clicked.isDirectory()) {
						//Go into the directory:
						TreePath pathToClicked = clicked.getPath();
						browseTree.setSelectionPath(pathToClicked);
						browseTree.scrollPathToVisible(pathToClicked);
						browseTree.setSelectionPath(pathToClicked); //the first one may have expanded the node and changed the selection, so ensure the node we want is actually selected.
					} else  {
						downloadToDirectory(Arrays.asList(new FileSystemEntry[] {clicked}), null, null);
						frame.setStatusHint(new StatusHint(frame.gui.util.getImage("tick"), clicked.getName()+" queued for download!"));
					}
				}
			}
		}
		if (e.getSource()==browseTree) {
			//remove a search if they clicked on the searches icon.
			if (lastOnIcon &&
				lastInspectedFSE instanceof FileSystemEntry &&
				((FileSystemEntry)lastInspectedFSE).isSearch()) {
				fs.removeSearch((FileSystemEntry) lastInspectedFSE);
				
				lastInspectedFSE = null; //doesn't exist anymore.
				lastOnIcon = false;
				mouseMoved(new MouseEvent(browseTree, 0, System.currentTimeMillis(), 0, e.getX(), e.getY(), 0, false)); //simulate the mouse moving to update status bar and icons.
			}
			//remove all searches if the clicked the searchroot:
			if (lastInspectedFSE == fs.getSearchRoot()) {
				fs.removeAllSearches();
			}
		}
	}

	@Override
	public void mouseEntered(MouseEvent e) {
		if (e.getSource()==searchQueryButton) {
			frame.setStatusHint("Click here to search for these terms");
		} else if (e.getSource()==searchQuery) {
			frame.setStatusHint("Type keywords to search for in here. Punctuation and spaces cannot be searched for.");
		} else if (e.getSource()==filesTable) {
			setTableStatusHint();
		}
	}

	void setTableStatusHint() {
		if (browseTree.getSelectionPath() == null){
			return;
		}
		
		if (!(browseTree.getSelectionPath().getLastPathComponent() instanceof FileSystemEntry)) return;
		FileSystemEntry fse = ((FileSystemEntry)browseTree.getSelectionPath().getLastPathComponent());
		
		int[] selected = filesTable.getSelectedRows();
		if (selected==null || selected.length==0) {
			if (fse.getFiles()!=null) frame.setStatusHint("Directories: "+fse.getChildCount()+", Files: "+fse.getFiles().size()+", Total size: "+Util.niceSize(fse.getSize()));
		} else {
			long size=0;
			int dirs=0;
			int files=0;
			for (int i : selected) {
				FileSystemEntry current = fs.getEntryForRow(filesTable.convertRowIndexToModel(i));
				size+=current.getSize();
				if (current.isDirectory()) dirs++; else files++;
			}
			frame.setStatusHint("(selection) Directories: "+dirs+", Files: "+files+", Total size: "+Util.niceSize(size));
		}
	}
	
	@Override
	public void mouseExited(MouseEvent e) {}

	@Override
	public void mousePressed(MouseEvent e) {}

	@Override
	public void mouseReleased(MouseEvent e) {}

	@Override
	public void valueChanged(TreeSelectionEvent e) {
		if (e.getPath().getLastPathComponent() instanceof ListableEntry) {
			fs.setSelectedEntry((ListableEntry) e.getPath().getLastPathComponent());
		}
		if (e.getPath().getLastPathComponent() instanceof FileSystemEntry) {
			FileSystemEntry fse = (FileSystemEntry) e.getPath().getLastPathComponent();
			if (fse.isSearch()) {
				currentDirectory.setText(fse.getSearchTerms());
			} else if (fse.isDirectory()) {
				currentDirectory.setText(fse.getIndexNodePath());
			} else {
				currentDirectory.setText("");
			}
		}
	}

	@Override
	public void mouseDragged(MouseEvent e) {}

	ListableEntry lastInspectedFSE;
	boolean lastOnIcon = false;
	@Override
	public void mouseMoved(MouseEvent e) {
		if (e.getSource()==browseTree) {
			browseTreeRenderer.setMousePointInTree(e.getPoint());
			
			TreePath path = browseTree.getPathForLocation(e.getX(), e.getY());
			if (path!=null) {
				if (!(path.getLastPathComponent() instanceof ListableEntry)) return;
				
				ListableEntry fse = (ListableEntry)path.getLastPathComponent();
				if (fse!=lastInspectedFSE || lastOnIcon!=browseTreeRenderer.pointOnIcon(e.getPoint())) {
					long size = fse.getSize();
					frame.setStatusHint(fse.getName()+": "+Util.niceSize(size));
					if (lastInspectedFSE!=null) browseTree.repaint(browseTree.getPathBounds(lastInspectedFSE.getPath()));
					lastInspectedFSE = fse;
					lastOnIcon = browseTreeRenderer.pointOnIcon(e.getPoint());
					browseTree.repaint(browseTree.getPathBounds(path));
				}
			} else {
				if (lastInspectedFSE!=null) {
					Rectangle r = browseTree.getPathBounds(lastInspectedFSE.getPath());
					lastInspectedFSE = null;
					browseTree.repaint(r);
				}
			}
		}
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if (evt.getSource()==splitPane) {
			frame.gui.conf.putInt(CK.FILES_DIVIDER_LOCATION, splitPane.getDividerLocation());
		}
	}

	@Override
	public void treeCollapsed(TreeExpansionEvent event) {
		if (event.getPath().getPath().length==2) {
			browseTree.expandPath(event.getPath());
		}
	}

	@Override
	public void treeExpanded(TreeExpansionEvent event) {
		browseTree.setSelectionPath(event.getPath());
		
		Object node = event.getPath().getLastPathComponent();
		
		if (!expandedTimes.containsKey(node)) {
			for (Object nodeToRenew : event.getPath().getPath()) {
				expandedTimes.put(nodeToRenew, System.currentTimeMillis());
			}
		}
	}

	@Override
	public void valueChanged(ListSelectionEvent e) {
		setTableStatusHint();
	}
	
	/**
	 * When called it will traverse all visible leaves in the browseTree and collapse them if they have been open for too long and are not selected.
	 */
	private void collapseOldNodes() {
		Stack<ListableEntry> toConsider = new Stack<ListableEntry>(); //this is a stack of expanded nodes that need their children (and themselves) to be considered.
		toConsider.push(fs.getBrowseRoot()); //we know these are always expanded, and uncollapsable.
		toConsider.push(fs.getSearchRoot()); // "               "                    "        
		
		//a) mark all uncollapsable nodes
		TreePath[] selecteds = browseTree.getSelectionPaths();
		if (selecteds!=null) {
			for (TreePath selected : selecteds) {
				for (Object toMark : selected.getPath()) { //a selected nodes and their ancestors are not collapsable. 
					expandedTimes.put(toMark, System.currentTimeMillis());
				}
			}
		}
		
		
		//b) collapse old expanded nodes.
		while (!toConsider.empty()) {
			for (FileSystemEntry child : toConsider.pop().getAllChildren()) {
				if (browseTree.isExpanded(child.getPath())) {
					toConsider.push(child);
					if (System.currentTimeMillis()-expandedTimes.get(child)>FS2Constants.CLIENT_BROWSETREE_COLLAPSE_INTERVAL) {
						browseTree.collapsePath(child.getPath());
						expandedTimes.remove(child);
					}
				}
			}
		}
	}
}
