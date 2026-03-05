/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.swingWorklist;

import java.awt.*;
import java.io.IOException;
import java.util.*;

import javax.swing.table.AbstractTableModel;

import org.jdom2.JDOMException;

/**
 * 
 * @author Lachlan Aldred
 * Date: 15/05/2003
 * Time: 13:51:11
 * 
 */
public class YWorklistTableModel extends AbstractTableModel {
    private static final long serialVersionUID = 1L;
    protected Map<String, Object[]> _rows = new TreeMap<>();
    private String[] _colNames;

    public YWorklistTableModel(String[] colNames) {
        _colNames = colNames;
    }


    /**
     * Returns the number of rows in the model. A
     * <code>JTable</code> uses this method to determine how many rows it
     * should display.  This method should be quick, as it
     * is called frequently during rendering.
     *
     * @return the number of rows in the model
     * @see #getColumnCount
     */
    public synchronized int getRowCount() {
        return _rows.size();
    }


    /**
     * Returns the number of columns in the model. A
     * <code>JTable</code> uses this method to determine how many columns it
     * should create and display by default.
     *
     * @return the number of columns in the model
     * @see #getRowCount
     */
    public synchronized int getColumnCount() {
        if (_colNames != null) {
            return _colNames.length;
        }
        return 0;
    }


    /**
     * Returns the value for the cell at <code>columnIndex</code> and
     * <code>rowIndex</code>.
     *
     * @param	rowIndex	the row whose value is to be queried
     * @param	columnIndex 	the column whose value is to be queried
     * @return	the value Object at the specified cell
     */
    public synchronized Object getValueAt(int rowIndex, int columnIndex) {
        if (rowIndex < _rows.size()) {
            Object[] row = new ArrayList<>(_rows.values()).get(rowIndex);
            if (row.length > columnIndex) {
                return row[columnIndex];
            }
        }
        return null;
    }


    public synchronized void addRow(String key, Object[] rowValues) {
        _rows.put(key, rowValues);
        final int position = new ArrayList<>(_rows.keySet()).indexOf(key);
        EventQueue.invokeLater(() -> fireTableRowsInserted(position, position));
    }


    public synchronized String getColumnName(int columnIndex) {
        if (this._colNames != null && _colNames.length > 0) {
            return _colNames[columnIndex % _colNames.length];
        } else
            return super.getColumnName(columnIndex);
    }


    public synchronized void removeRow(Object caseAndTaskID) {
        final int rowIndex = getRowIndex(caseAndTaskID);
        if (rowIndex >= 0) {
            _rows.remove(caseAndTaskID);
            EventQueue.invokeLater(() -> fireTableRowsDeleted(rowIndex, rowIndex));
        }
    }


    public synchronized Class<?> getColumnClass(int c) {
        Object o = getValueAt(0, c);
        return o != null ? o.getClass() : null;
    }


    public synchronized int getRowIndex(Object caseAndTaskID) {
        return new ArrayList<>(_rows.keySet()).indexOf(caseAndTaskID);
    }


    public String[] getColumnNames() {
        return _colNames;
    }

    public Map<String, Object[]> getRowMap() {
        return _rows;
    }

    public String getOutputData(String caseIDStr, String taskID) throws JDOMException, IOException {
        String outputParamsData = null;

        Object[] row = (Object[]) _rows.get(caseIDStr + taskID);
        if (row != null && row.length > 9) {
            outputParamsData = (String) row[9];
        }

        return outputParamsData;
    }
}