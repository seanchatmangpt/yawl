package org.yawlfoundation.yawl.stateless;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * JUnit 5 test suite for the stateless YAWL engine (YStatelessEngine).
 * Run stateless mode tests before stateful engine tests (EngineTestSuite).
 * Includes JUnit 3 backward compatibility.
 */
@Suite
@SelectClasses({
    TestStatelessEngine.class
})
public class StatelessTestSuite {

    /**
     * JUnit 3 compatible suite() method for backward compatibility
     */
    public static Test suite() {
        TestSuite suite = new TestSuite("Stateless Test Suite");
        suite.addTestSuite(TestStatelessEngine.class);
        return suite;
    }
}
