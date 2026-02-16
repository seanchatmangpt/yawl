package org.yawlfoundation.yawl.logging;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import junit.framework.Test;
import junit.framework.TestSuite;

/**
 *
 * Author: Lachlan Aldred
 * Date: 17/04/2003
 * Time: 14:41:14
 *
 * JUnit 5 Test Suite (with JUnit 3 backward compatibility)
 */
@Suite
@SelectClasses({
    YawlServletTestNextIdNew.class
})
public class LoggingTestSuite {

    /**
     * JUnit 3 compatible suite() method for backward compatibility
     */
    public static Test suite() {
        TestSuite suite = new TestSuite("Logging Test Suite");
        suite.addTestSuite(YawlServletTestNextIdNew.class);
        return suite;
    }
}
