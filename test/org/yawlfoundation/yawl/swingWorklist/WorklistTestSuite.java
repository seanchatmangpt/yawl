package org.yawlfoundation.yawl.swingWorklist;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import junit.framework.Test;
import junit.framework.TestSuite;

/**
 *
 * Author: Lachlan Aldred
 * Date: 9/05/2003
 * Time: 16:00:43
 *
 * JUnit 5 Test Suite (with JUnit 3 backward compatibility)
 */
@Suite
@SelectClasses({
    TestWorklistTableModel.class,
    TestYWorkAvailablePanel.class
})
public class WorklistTestSuite {

    /**
     * JUnit 3 compatible suite() method for backward compatibility
     */
    public static Test suite() {
        TestSuite suite = new TestSuite("Worklist Test Suite");
        suite.addTestSuite(TestWorklistTableModel.class);
        suite.addTestSuite(TestYWorkAvailablePanel.class);
        return suite;
    }
}
