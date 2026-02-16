package org.yawlfoundation.yawl.swingWorklist;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

/**
 * 
 * Author: Lachlan Aldred
 * Date: 9/05/2003
 * Time: 16:00:43
 * 
 */
@Suite
@SelectClasses({
    TestWorklistTableModel.class,
    TestYWorkAvailablePanel.class
})
public class WorklistTestSuite {
}
