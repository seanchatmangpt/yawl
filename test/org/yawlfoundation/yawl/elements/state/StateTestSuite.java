package org.yawlfoundation.yawl.elements.state;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 *
 * Author: Lachlan Aldred
 * Date: 9/05/2003
 * Time: 15:52:04
 *
 * JUnit 5 Test Suite for State elements
 */
@Suite
@SuiteDisplayName("State Test Suite")
@SelectClasses({
    TestYIdentifier.class,
    TestYMarking.class,
    TestYSetOfMarkings.class
})
public class StateTestSuite {
    // JUnit 5 suite uses annotations - no main method needed
}
