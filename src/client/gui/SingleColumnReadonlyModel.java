/**
 * 
 */
package client.gui;

import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;

public class SingleColumnReadonlyModel implements TableModel {

	Object[] data;
	Class<?> colClass;
	String name;
	
	public SingleColumnReadonlyModel(Object[] data, String columnName, Class<?> columnClass) {
		this.data = data;
		this.colClass = columnClass;
		this.name = columnName;
	}
	
	@Override
	public void addTableModelListener(TableModelListener l) {
		//no need as is immutable.
	}

	@Override
	public Class<?> getColumnClass(int columnIndex) {
		return colClass;
	}

	@Override
	public int getColumnCount() {
		return 1;
	}

	@Override
	public String getColumnName(int columnIndex) {
		return name;
	}

	@Override
	public int getRowCount() {
		return data.length;
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		return data[rowIndex];
	}

	@Override
	public boolean isCellEditable(int rowIndex, int columnIndex) {
		return false;
	}

	@Override
	public void removeTableModelListener(TableModelListener l) {
		//nothing.
	}

	@Override
	public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
		//nothing
	}
	
}