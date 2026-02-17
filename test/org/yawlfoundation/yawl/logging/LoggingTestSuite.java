package org.yawlfoundation.yawl.logging;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 *
 * Author: Lachlan Aldred
 * Date: 17/04/2003
 * Time: 14:41:14
 *
 * JUnit 5 Test Suite
 */
@Suite
@SuiteDisplayName("Logging Test Suite")
@SelectClasses({
    TestYLogDataItem.class
})
public class LoggingTestSuite {
    // JUnit 5 suite uses annotations - no main method needed
}
