package org.yawlfoundation.yawl.exceptions;

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
    TestYConnectivityException.class,
    TestYSyntaxException.class
})
public class ExceptionTestSuite {

    /**
     * JUnit 3 compatible suite() method for backward compatibility
     */
    public static Test suite() {
        TestSuite suite = new TestSuite("Exception Test Suite");
        suite.addTestSuite(TestYConnectivityException.class);
        suite.addTestSuite(TestYSyntaxException.class);
        return suite;
    }
}
