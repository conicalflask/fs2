package client.gui.settings;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableModel;

import client.gui.FancierTable;
import client.gui.MainFrame;
import client.gui.SingleColumnReadonlyModel;
import client.gui.TabItem;
import client.platform.ClientConfigDefaults.CK;

/**
 * The graphical settings manager for FS2
 * 
 * @author gp
 */
@SuppressWarnings("serial")
public class SettingsTab extends TabItem implements PropertyChangeListener,
													ListSelectionListener {

	ArrayList<SettingsPanel> settings = new ArrayList<SettingsPanel>();
	JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
	CardLayout cards = new CardLayout();
	JPanel rightHand;
	FancierTable ft;
	
	class SettingsRenderer extends DefaultTableCellRenderer {
		@Override
		public Component getTableCellRendererComponent(JTable table,
				Object value, boolean isSelected, boolean hasFocus, int row,
				int column) {
			super.getTableCellRendererComponent(table, value, isSelected, hasFocus,
					row, column);
			
			SettingsPanel item = (SettingsPanel)value;
			setText(item.getSettingName());
			setIcon(item.getIcon());
			
			return this;
		}
	}
	
	public SettingsTab(JTabbedPane pane, MainFrame frame) {
		super(pane, frame, "Settings", FS2Tab.SETTINGS, frame.getGui().getUtil().getImage("settings"));
		
		setLayout(new BorderLayout());
		
		//##########################
		//1) Populate settings panels (done in order we would like them to appear)
		settings.add(new BasicSettings(frame));
		settings.add(new ShareSettings(frame));
		settings.add(new IndexnodeSettings(frame));
		settings.add(new AdvancedSettings(frame));
		
		//##########################
		
		
		
		//2) Build the ui for the settings panel
		//2a) construct a table model for the category selection (single column)
		TableModel tm = new SingleColumnReadonlyModel(settings.toArray(), "Category", SettingsPanel.class);
		
		//2b) split pane
		this.add(split, BorderLayout.CENTER);
		split.setContinuousLayout(true);
		split.setDividerLocation(frame.getGui().getConf().getInt(CK.SETTINGS_DIVIDER_LOCATION));
		split.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, this);
		
		//2c) scrollpaned fancy table to the right.
		ft = new FancierTable(tm, frame.getGui().getConf(), CK.SETTINGS_COLWIDTHS);
		ft.getSelectionModel().addListSelectionListener(this);
		ft.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		
		ft.getColumn(tm.getColumnName(0)).setCellRenderer(new SettingsRenderer());
		
		JScrollPane sp = new JScrollPane(ft);
		split.setLeftComponent(sp);
		
		//2d) card layout right hand side
		rightHand = new JPanel(cards);
		for (SettingsPanel setting : settings) {
			rightHand.add(setting, setting.getClass().getSimpleName());
		}
		split.setRightComponent(rightHand);
		
		ft.getSelectionModel().setSelectionInterval(0, 0);
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if (evt.getSource()==split) {
			frame.getGui().getConf().putInt(CK.SETTINGS_DIVIDER_LOCATION, split.getDividerLocation());
		}
	}

	@Override
	public void valueChanged(ListSelectionEvent e) {
		if (ft.getSelectedRowCount()>0) {
			cards.show(rightHand, settings.get(ft.getSelectedRow()).getClass().getSimpleName());
		}
	}
}
