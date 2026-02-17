package org.yawlfoundation.yawl.swingWorklist;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 *
 * Author: Lachlan Aldred
 * Date: 9/05/2003
 * Time: 16:00:43
 *
 * JUnit 5 Test Suite
 */
@Suite
@SuiteDisplayName("Worklist Test Suite")
@SelectClasses({
    TestWorklistTableModel.class,
    TestYWorkAvailablePanel.class
})
public class WorklistTestSuite {
    // JUnit 5 suite uses annotations - no main method needed
}
