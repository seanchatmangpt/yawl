package org.yawlfoundation.yawl.engine;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Iterator;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.elements.YAtomicTask;
import org.yawlfoundation.yawl.elements.YTask;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.exceptions.YPersistenceException;

/**
 *
 * Author: Lachlan Aldred
 * Date: 30/05/2003
 * Time: 15:32:26
 *
 */
class TestYWorkItemRepository {
    private YWorkItemRepository _workitemRepository;
    private YWorkItem _parentWorkItem;
    private YTask _task;

    @BeforeEach

    void setUp() throws Exception {
        _workitemRepository = YEngine.getInstance().getWorkItemRepository();
        _workitemRepository.clear();
        _task = new YAtomicTask("task-123", YTask._XOR, YTask._AND, null);
        YIdentifier identifier = new YIdentifier(null);
        YWorkItemID workItemID = new YWorkItemID(identifier, "task-123");
        _parentWorkItem = new YWorkItem(null, new YSpecificationID("ASpecID"), _task, workItemID, false, false);
        for (int i = 0; i < 5; i++) {
            _parentWorkItem.createChild(null, identifier.createChild(null));
        }
    }

    @Test

    void testGetItem() throws YPersistenceException {
        assertTrue(_workitemRepository.getEnabledWorkItems().size() == 0);
        new YWorkItem(null, new YSpecificationID("ASpecID"), _task, new YWorkItemID(new YIdentifier(null), "task4321"), false, false);
        assertEquals(
                _workitemRepository.get(
                        _parentWorkItem.getCaseID().toString(), _parentWorkItem.getTaskID()),
                _parentWorkItem);
        _workitemRepository.removeWorkItemFamily(_parentWorkItem);
        assertNull(_workitemRepository.get(_parentWorkItem.getCaseID().toString(), _parentWorkItem.getTaskID()));
    }

    @Test

    void testChildren() {
        Set enabledItems = _workitemRepository.getFiredWorkItems();
        Iterator iter = _parentWorkItem.getChildren().iterator();
        while (iter.hasNext()) {
            YWorkItem child = (YWorkItem) iter.next();
            assertEquals(_parentWorkItem, child.getParent());
        }
    }
}
