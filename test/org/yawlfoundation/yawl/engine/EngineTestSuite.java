package org.yawlfoundation.yawl.engine;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

/**
 *
 * Author: Lachlan Aldred
 * Date: 9/05/2003
 * Time: 15:57:52
 *
 * JUnit 5 Test Suite for Engine package
 */
@Suite
@SelectClasses({
    TestYEngineInit.class,
    TestCaseCancellation.class,
    TestEngineSystem2.class,
    TestImproperCompletion.class,
    TestOrJoin.class,
    TestRestServiceMethods.class,
    TestSimpleExecutionUseCases.class,
    TestYNetRunner.class,
    TestYWorkItem.class,
    TestYWorkItemID.class,
    TestYWorkItemRepository.class
})
public class EngineTestSuite {
}
