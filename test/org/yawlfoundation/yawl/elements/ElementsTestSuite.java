package org.yawlfoundation.yawl.elements;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 *
 * Author: Lachlan Aldred
 * Date: 28/04/2003
 * Time: 11:12:39
 *
 */
@Suite
@SuiteDisplayName("Elements Test Suite")
@SelectClasses({
    TestDataParsing.class,
    TestYAtomicTask.class,
    TestYCompositeTask.class,
    TestYExternalCondition.class,
    TestYExternalNetElement.class,
    TestYExternalTask.class,
    TestYFlowsInto.class,
    TestYInputCondition.class,
    TestYMultiInstanceAttributes.class,
    TestYNet.class,
    TestYNetElement.class,
    TestYOutputCondition.class,
    TestYSpecification.class
})
public class ElementsTestSuite {
    // JUnit 5 suite uses annotations - no main method needed
}
