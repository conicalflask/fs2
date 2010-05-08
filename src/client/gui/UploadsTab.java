package client.gui;

import java.awt.BorderLayout;
import java.awt.Component;

import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableModel;

import client.platform.ClientConfigDefaults.CK;
import client.shareserver.ShareServer;

@SuppressWarnings("serial")
public class UploadsTab extends TabItem {
	
	public class SecureCellRenderer extends DefaultTableCellRenderer {
		@Override
		public Component getTableCellRendererComponent(JTable table,
				Object value, boolean isSelected, boolean hasFocus, int row,
				int column) {
			super.getTableCellRendererComponent(table, value, isSelected, hasFocus,
					row, column);
			
			if ((Boolean)value) {
				setIcon(frame.gui.util.getImage("secure"));
				setText("yes");
			} else {
				setIcon(null);
				setText("no");
			}
			
			return this;
		}
	}
	
	public UploadsTab(JTabbedPane pane, MainFrame frame) {
		super(pane, frame, "Uploads", FS2Tab.UPLOADS, frame.gui.util.getImage("upload"));
		this.setLayout(new BorderLayout());
		
		TableModel uploadModel = frame.gui.ssvr.getUploadsModel();
		JTable uploads = new FancierTable(uploadModel, frame.gui.conf, CK.UPLOADS_COLWIDTHS);
		
		this.add(new JScrollPane(uploads));
		
		uploads.getColumn(uploadModel.getColumnName(ShareServer.PEER_IDX)).setCellRenderer(new PeerCellRenderer(frame));
		uploads.getColumn(uploadModel.getColumnName(ShareServer.PROGRESS_IDX)).setCellRenderer(new UploadProgressCellRenderer());
		uploads.getColumn(uploadModel.getColumnName(ShareServer.SECURE_IDX)).setCellRenderer(new SecureCellRenderer());
		
	}
}
