package org.yawlfoundation.yawl.authentication;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * Test suite for authentication package.
 *
 * @author Lachlan Aldred
 * Date: 28/04/2003
 * Time: 11:12:39
 *
 * JUnit 5 Test Suite
 */
@Suite
@SuiteDisplayName("Authentication Test Suite")
@SelectClasses({
    TestConnections.class,
    TestJwtManager.class,
    TestCsrfTokenManager.class,
    TestCsrfProtectionFilter.class,
    V6SecurityTest.class
})
public class AuthenticationTestSuite {
    // JUnit 5 suite uses annotations - no main method needed
}
