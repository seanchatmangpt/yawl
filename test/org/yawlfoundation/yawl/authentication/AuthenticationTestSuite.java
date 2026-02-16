package org.yawlfoundation.yawl.authentication;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

/**
 * Test suite for authentication package.
 * 
 * @author Lachlan Aldred
 * Date: 28/04/2003
 * Time: 11:12:39
 */
@Suite
@SelectClasses({
    TestConnections.class,
    TestCsrfTokenManager.class,
    TestJwtManager.class,
    TestCsrfProtectionFilter.class
})
public class AuthenticationTestSuite {
}
