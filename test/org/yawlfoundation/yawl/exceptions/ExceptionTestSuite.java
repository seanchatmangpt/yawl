package org.yawlfoundation.yawl.exceptions;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

/**
 * 
 * Author: Lachlan Aldred
 * Date: 17/04/2003
 * Time: 14:41:14
 * 
 */
@Suite
@SelectClasses({
    TestYConnectivityException.class,
    TestYSyntaxException.class
})
public class ExceptionTestSuite {
}
