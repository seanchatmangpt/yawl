package org.yawlfoundation.yawl.elements.state;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import junit.framework.Test;
import junit.framework.TestSuite;

/**
 *
 * Author: Lachlan Aldred
 * Date: 9/05/2003
 * Time: 15:52:04
 *
 * JUnit 5 Test Suite for State elements (with JUnit 3 backward compatibility)
 */
@Suite
@SelectClasses({
    TestYIdentifier.class,
    TestYMarking.class,
    TestYSetOfMarkings.class
})
public class StateTestSuite {

    /**
     * JUnit 3 compatible suite() method for backward compatibility with test runners
     */
    public static Test suite() {
        TestSuite suite = new TestSuite("State Test Suite");
        suite.addTestSuite(TestYIdentifier.class);
        suite.addTestSuite(TestYMarking.class);
        suite.addTestSuite(TestYSetOfMarkings.class);
        return suite;
    }
}
