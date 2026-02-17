package org.yawlfoundation.yawl.util;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * JUnit 5 suite for the util package.
 *
 * Author: Lachlan Aldred
 * Date: 9/05/2003
 * Time: 16:00:43
 */
@Suite
@SuiteDisplayName("Util Test Suite")
@SelectClasses({
        TestSafeNumberParser.class,
        TestNullCheckModernizer.class
})
public class UtilTestSuite {
    // JUnit 5 suite uses annotations - no main method needed
}
