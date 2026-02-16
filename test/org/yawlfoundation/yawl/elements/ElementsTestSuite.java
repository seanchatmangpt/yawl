package org.yawlfoundation.yawl.elements;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

/**
 *
 * Author: Lachlan Aldred
 * Date: 28/04/2003
 * Time: 11:12:39
 *
 * JUnit 5 Test Suite for Elements package
 */
@Suite
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
}
