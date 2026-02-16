package org.yawlfoundation.yawl.engine;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import junit.framework.Test;
import junit.framework.TestSuite;

/**
 *
 * Author: Lachlan Aldred
 * Date: 9/05/2003
 * Time: 15:57:52
 *
 * JUnit 5 Test Suite for Engine package (with JUnit 3 backward compatibility)
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

    /**
     * JUnit 3 compatible suite() method for backward compatibility with test runners
     */
    public static Test suite() {
        TestSuite suite = new TestSuite("Engine Test Suite");
        suite.addTestSuite(TestYEngineInit.class);
        suite.addTestSuite(TestCaseCancellation.class);
        suite.addTestSuite(TestEngineSystem2.class);
        suite.addTestSuite(TestImproperCompletion.class);
        suite.addTestSuite(TestOrJoin.class);
        suite.addTestSuite(TestRestServiceMethods.class);
        suite.addTestSuite(TestSimpleExecutionUseCases.class);
        suite.addTestSuite(TestYNetRunner.class);
        suite.addTestSuite(TestYWorkItem.class);
        suite.addTestSuite(TestYWorkItemID.class);
        suite.addTestSuite(TestYWorkItemRepository.class);
        return suite;
    }
}
