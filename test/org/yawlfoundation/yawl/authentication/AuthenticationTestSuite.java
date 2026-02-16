package org.yawlfoundation.yawl.authentication;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * Test suite for authentication package.
 *
 * @author Lachlan Aldred
 * Date: 28/04/2003
 * Time: 11:12:39
 *
 * JUnit 5 Test Suite (with JUnit 3 backward compatibility)
 */
@Suite
@SelectClasses({
    TestConnections.class,
    TestJwtManager.class
})
public class AuthenticationTestSuite {

    /**
     * JUnit 3 compatible suite() method for backward compatibility
     */
    public static Test suite() {
        TestSuite suite = new TestSuite("Authentication Test Suite");
        suite.addTestSuite(TestConnections.class);
        suite.addTestSuite(TestJwtManager.class);
        return suite;
    }
}
