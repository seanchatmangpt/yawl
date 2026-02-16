package org.yawlfoundation.yawl.authentication;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

/**
 * 
 * Author: Lachlan Aldred
 * Date: 28/04/2003
 * Time: 11:12:39
 * 
 */
@Suite
@SelectClasses({
    TestConnections.class
})
public class AuthenticationTestSuite {
}
