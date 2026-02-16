package org.yawlfoundation.yawl.swingWorklist;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * Author: Lachlan Aldred
 * Date: 20/05/2003
 * Time: 10:45:17
 *
 */
class TestWorklistTableModel{
    private YWorklistTableModel _tableModel;

    @BeforeEach

    void setUp(){
        _tableModel = new YWorklistTableModel(new String[]{"ID", "A string"});
        int activityID = 0;
        for(int i = 0; i < 20; i++){
            _tableModel.addRow(""+i, new Object[]{""+  ++activityID, "this is data "+activityID});
        }
    }

    @Test

    void testGetColumnClass(){
        assertEquals(this._tableModel.getColumnClass(0), String.class);
        assertEquals(this._tableModel.getColumnClass(1), String.class);
        _tableModel.getColumnClass(2);
    }

    @Test

    void testGetColumnName(){
        assertNotNull(_tableModel.getColumnName(0));
        assertNotNull(_tableModel.getColumnName(Integer.MAX_VALUE));
        assertEquals(_tableModel.getColumnName(0), "ID");
        assertEquals(_tableModel.getColumnName(1), "A string");
        assertNotNull(_tableModel.getColumnName(Integer.MAX_VALUE));
    }

    @Test

    void testAccessors(){
        assertEquals(_tableModel.getRowCount(), 20);
        String s = (String) _tableModel.getValueAt(5, 1);
        assertEquals(s, "this is data 14");
        assertNull(_tableModel.getValueAt(0, Integer.MAX_VALUE));
        assertNull(_tableModel.getValueAt(Integer.MAX_VALUE, 0));
    }

    @Test

    void testRemoveRow(){
        String id =  (String) _tableModel.getValueAt(5,0);
        String id2 = (String) _tableModel.getValueAt(5,1);
        _tableModel.removeRow(id + id2);
        assertTrue(_tableModel.getRowIndex(id + id2)== -1);
    }
}
