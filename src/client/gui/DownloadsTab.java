package client.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;

import common.FS2Constants;
import common.Logger;
import common.Util;
import common.Util.Deferrable;

import client.gui.MainFrame.StatusHint;
import client.indexnode.downloadcontroller.DownloadCompleteListener;
import client.indexnode.downloadcontroller.DownloadController;
import client.indexnode.downloadcontroller.DownloadQueue;
import client.indexnode.downloadcontroller.DownloadQueue.DownloadDirectory;
import client.indexnode.downloadcontroller.DownloadQueue.DownloadFile;
import client.indexnode.downloadcontroller.DownloadQueue.DownloadItem;
import client.platform.ClientConfigDefaults.CK;

@SuppressWarnings("serial")
public class DownloadsTab extends TabItem implements TreeExpansionListener, ActionListener, TreeModelListener, PropertyChangeListener, ListSelectionListener, DownloadCompleteListener {

	DownloadQueue q;
	DownloadController dc;
	
	public DownloadsTab(JTabbedPane pane, MainFrame frame) {
		super(pane, frame, "Downloads", FS2Tab.DOWNLOADS, frame.gui.util.getImage("download"));
		setClosable(false);
		
		
		q = frame.gui.dc.getQueue();
		dc = frame.gui.dc;
		
		q.addDownloadCompleteListener(this);
		
		this.setLayout(new BorderLayout());
		this.add(getSplitSection(), BorderLayout.CENTER);
	}

	JTable chunksTable;
	private JScrollPane getChunkTable() {
		chunksTable = new FancierTable(dc, frame.gui.conf, CK.DOWNLOADCHUNKS_TABLE_COLWITDHS);
		
		chunksTable.getColumn(dc.getColumnName(DownloadController.PEER_IDX)).setCellRenderer(new PeerCellRenderer(frame));
		chunksTable.getColumn(dc.getColumnName(DownloadController.PROGRESS_IDX)).setCellRenderer(new CellProgressBar());
		
		chunksTable.getSelectionModel().addListSelectionListener(this);
		
		JScrollPane ret = new JScrollPane(chunksTable);
		ret.setMinimumSize(new Dimension(100, 100));
		return ret;
	}
	
	JSplitPane splitPane;
	JLabel dlQLabel;
	private JSplitPane getSplitSection() {
		splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
		
		JPanel dlQueue = new JPanel(new BorderLayout());
		dlQueue.add(dlQLabel=new JLabel("", frame.gui.util.getImage("download") ,JLabel.LEFT), BorderLayout.NORTH);
		setDownloadQueueLabel();
		dlQueue.add(getQueueTree(), BorderLayout.CENTER);
		dlQueue.add(getButtons(), BorderLayout.SOUTH);
		
		splitPane.setTopComponent(dlQueue);
		splitPane.setContinuousLayout(true);
		splitPane.setResizeWeight(1.0);
		
		JPanel chunkPanel = new JPanel(new BorderLayout());
		chunkPanel.add(getChunkTable(), BorderLayout.CENTER);
		chunkPanel.add(new JLabel("Active downloads", frame.gui.util.getImage("activetransfers"), JLabel.LEFT), BorderLayout.NORTH);
		
		splitPane.setBottomComponent(chunkPanel);
		
		splitPane.setDividerLocation(frame.gui.conf.getInt(CK.DOWNLOADS_DIVIDER_LOCATION));
		splitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, this);
		
		return splitPane;
	}
	
	void setDownloadQueueLabel() {
		dlQLabel.setText("Download Queue"+(dc.getDownloadTracker().getRemaining()>0 ? " - "+Util.niceSize(dc.getDownloadTracker().getRemaining())+", "+dc.getDownloadTracker().describeTimeRemaining()+" remaining." : ""));
	}
	
	JTree queue;
	JButton removeAll;
	JButton openldDir;
	JButton openldFile;
	
	private JPanel getButtons() {
		JPanel ret = new JPanel(new FlowLayout());
		
		removeAll = new JButton("Clear queue", frame.gui.util.getImage("delete"));
		removeAll.addActionListener(this);
		
		ret.add(removeAll);
		
		openldDir = new JButton("Open folder", frame.gui.util.getImage("type-dir"));
		openldDir.setEnabled(false);
		openldDir.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent ae) {
				try {
					Desktop.getDesktop().open(displayedFile.getFile().getParentFile());
				} catch (IOException e) {
					Logger.log("Couldn't open file browser: "+e);
					Logger.log(e);
				}
			}
		});
		
		ret.add(openldDir);
		
		openldFile = new JButton("Open file", frame.gui.util.getImage("type-unknown"));
		openldFile.setEnabled(false);
		openldFile.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent ae) {
				try {
					Desktop.getDesktop().open(displayedFile.getFile());
				} catch (IOException e) {
					Logger.log("Couldn't open file browser: "+e);
					Logger.log(e);
				}
			}
		});
		
		ret.add(openldFile);
		
		return ret;
	}
	
	private class QueuePopup extends EasierPopup {
		EPopup sourcesInfoItem;
		EPopup fileItem;
		File selectedFile;
		EPopup newDispatchItem;
		public QueuePopup(final MainFrame frame) {
			super(frame);
			
			fileItem = new EPopup("file name info", new PopupAction() {
				@Override
				public void doAction(ActionEvent e) {
					if (Desktop.isDesktopSupported()) {
						if (selectedFile.isDirectory())
							try {
								Desktop.getDesktop().open(selectedFile);
							} catch (IOException e1) {
								Logger.warn("Can't open: "+selectedFile+" in a browser");
								frame.setStatusHint(new StatusHint(frame.gui.util.getImage("error"), selectedFile.getName()+" couldn't be opened."));
							}
					}
				}
			}, null, "This is the file name that this item will have on disk, click to open directories.");
			
			addSeparator();
			
			sourcesInfoItem = new EPopup("info", null, null, "If there are no sources, this item will be ignored until more peers come online.");
			sourcesInfoItem.setEnabled(false);
			
			newDispatchItem = new EPopup("Look for more sources as soon as possible", new PopupAction() {
				@Override
				public void doAction(ActionEvent e) {
					for (TreePath tp : queue.getSelectionPaths()) {
						DownloadItem item = (DownloadItem) tp.getLastPathComponent();
						item.resetDispatchId();
					}
				}
			}, frame.gui.util.getImage("refresh"), "Allows the selection (or selection's children) to be retried without waiting for new network peers.");
			newDispatchItem.setEnabled(false);
			
			addSeparator();
			
			new EPopup("Promote", new PopupAction() {
				
				@Override
				public void doAction(ActionEvent e) {
					//1) collapse selection paths to ensure that no selection is contained within another.
					for (TreePath tp : queue.getSelectionPaths()) {
						queue.collapsePath(tp);
					}
					//2) promote the selected paths
					for (TreePath tp : queue.getSelectionPaths()) {
						DownloadItem item = (DownloadItem) tp.getLastPathComponent();
						item.promote();
					}
					//3) notify the downloadcontroller that the queue has changed and that it should do something about it right now...
					dc.requeue();
				}
			}, frame.gui.util.getImage("promote"), "Moves the selection to the top of the queue");
			
			new EPopup("Unqueue", new PopupAction() {
				@Override
				public void doAction(ActionEvent e) {
					Thread unqueueThread = new Thread(new Runnable() {
						@Override
						public void run() {
							//1) collapse selection paths to ensure that no selection is contained within another.
							for (TreePath tp : queue.getSelectionPaths()) {
								queue.collapsePath(tp);
							}
							//2) remove the selected paths.
							for (TreePath tp : queue.getSelectionPaths()) {
								DownloadItem item = (DownloadItem) tp.getLastPathComponent();
								item.cancel();
							}
						}
					}, "Unqueuer");
					unqueueThread.start();
					frame.setStatusHint(new StatusHint(frame.gui.util.getImage("delete"), "Removed selected items... may take a few seconds to update if your download queue is huge."));
				}
			}, frame.gui.util.getImage("delete"), "Removes the selected items from the download queue");
		}
		
		@Override
		protected boolean popupDecision(MouseEvent e) {
			if (queue.getSelectionCount()==0) return false;
			
			DownloadItem item = (DownloadItem) queue.getSelectionPath().getLastPathComponent();
			selectedFile = item.getFile();
			fileItem.setEnabled(selectedFile.isDirectory());
			fileItem.setText(selectedFile.getAbsolutePath());
			
			newDispatchItem.setEnabled(false);
			if (item instanceof DownloadFile) {
				DownloadFile file = (DownloadFile)item;
				if (file.hasNoSources()) {
					sourcesInfoItem.setText("This file has no sources on the network");
					newDispatchItem.setEnabled(true);
				} else {
					sourcesInfoItem.setText("Sources might be available for this file");
				}
			} else {
				sourcesInfoItem.setText("Directories do not have sources");
				newDispatchItem.setEnabled(true);
			}
			
			return true;
		}
	}
	
	JScrollPane getQueueTree() {
		queue = new JTree(q);
		queue.setRootVisible(false);
		queue.addTreeExpansionListener(this);
		queue.setCellRenderer(new DLQRenderer());
	
		q.addTreeModelListener(this);
		
		for (DownloadDirectory d : q.getRootDownloadDirectories()) {
			queue.expandPath(d.getPath());
		}
		
		queue.addMouseListener(new QueuePopup(frame));
		
		return new JScrollPane(queue);
	}
	
	private class DLQRenderer extends DefaultTreeCellRenderer {
		@Override
		public Component getTreeCellRendererComponent(JTree tree, Object value,
				boolean sel, boolean expanded, boolean leaf, int row,
				boolean hasFocus) {
			super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf,
					row, hasFocus);
			
			DownloadItem item = (DownloadItem) value;
			if (item instanceof DownloadDirectory) {
				if (expanded) {
					setIcon(openIcon);
				} else setIcon(closedIcon);
			} else if (item instanceof DownloadFile) {
				DownloadFile f = (DownloadFile)item;
				if (f.hasNoSources()) {
					setIcon(frame.gui.util.getImage("nosources"));
				} else if (f.isError()) {
					setIcon(frame.gui.util.getImage("error"));
				} else if (f.isDownloading()) { 
					if (f.isSecure()) {
						setIcon(frame.gui.util.getImage("secure"));
					} else {
						setIcon(frame.gui.util.getImage("activetransfers"));
					}
				} else {
					setIcon(frame.gui.util.getIconForType(frame.gui.util.guessType(f.getName())));
				}
			}
			
			return this;
		}
		
	}
	
	/**
	 * Prevents 'roots' from being collapsed.
	 */
	@Override
	public void treeCollapsed(TreeExpansionEvent event) {
		if (event.getPath().getPath().length==2) {
			queue.expandPath(event.getPath());
		}
	}
	
	@Override
	public void treeExpanded(TreeExpansionEvent event) {}

	@Override
	public void actionPerformed(ActionEvent e) {
		if (e.getSource()==removeAll) {
			//copy the array to avoid weirdness:
			ArrayList<DownloadItem> items = new ArrayList<DownloadItem>(q.getRootDownloadDirectories());
			for (DownloadItem d : items) {
				d.cancel();
			}
			frame.setStatusHint(new StatusHint(frame.gui.util.getImage("tick"), "Download queue purged!"));
		}
	}

	/**
	 * Expands tree items that are interesting:
	 */
	@Override
	public void treeNodesChanged(TreeModelEvent e) {
		setDownloadQueueLabel();
		for (Object changed : e.getChildren()) {
			if (changed instanceof DownloadFile) {
				DownloadFile f = (DownloadFile)changed;
				if (f.isError() /*|| f.hasNoSources()*/) {
					queue.expandPath((f.getPath().getParentPath()));
				}
			}
		}
	}

	@Override
	public void treeNodesInserted(TreeModelEvent e) {
		setDownloadQueueLabel();
		if (e.getPath().length==2) {
			queue.expandPath(e.getTreePath());
		}
	}

	@Override
	public void treeNodesRemoved(TreeModelEvent e) {
		setDownloadQueueLabel();
	}

	@Override
	public void treeStructureChanged(TreeModelEvent e) {
		setDownloadQueueLabel();
	}

	public void propertyChange(PropertyChangeEvent evt) {
		if (evt.getSource()==splitPane) {
			frame.gui.conf.putInt(CK.DOWNLOADS_DIVIDER_LOCATION, splitPane.getDividerLocation());
		}
	}

	@Override
	public void valueChanged(ListSelectionEvent arg0) {
		for (int rowidx : chunksTable.getSelectedRows()) {
			DownloadFile f = dc.getFileForRow(chunksTable.convertRowIndexToModel(rowidx));
			queue.setSelectionPath(f.getPath());
		}
	}

	/** The file most recently downloaded */
	private DownloadFile mostRecentlyFinished;
	
	//The gui updating is throttled to prevent extreme cpu usage when downloading a load of files.
	// We cache the last filename displayed so if a user clicks during a fast update the correct file is opened.
	private DownloadFile displayedFile;
	private DownloadCompleteGuiUpdater dcgu = new DownloadCompleteGuiUpdater();
	private class DownloadCompleteGuiUpdater implements Deferrable {

		@Override
		public void run() {
			Utilities.edispatch(new Runnable() {
				@Override
				public void run() {
					displayedFile = mostRecentlyFinished;
					openldDir.setText("Open "+displayedFile.getFile().getParent());
					openldDir.setEnabled(true);
					openldFile.setText("Open "+displayedFile.getFile().getName());
					openldFile.setIcon(frame.gui.util.getIconForType(frame.gui.util.guessType(displayedFile.getFile().getName())));
					openldFile.setEnabled(true);
				}
			});
		}
		
	}
	
	
	/**
	 * Called when a download has finished successfully. It is not called in a swing thread. This is to allow us to keep track of the most recently downloaded file.
	 */
	@Override
	public void downloadComplete(DownloadFile file) {
		mostRecentlyFinished = file;
		Util.scheduleExecuteNeverFasterThan(FS2Constants.CLIENT_EVENT_MIN_INTERVAL, dcgu);
	}
}
