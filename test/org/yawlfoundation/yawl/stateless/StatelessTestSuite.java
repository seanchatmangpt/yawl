package org.yawlfoundation.yawl.stateless;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * JUnit test suite for the stateless YAWL engine (YStatelessEngine).
 * Run stateless mode tests before stateful engine tests (EngineTestSuite).
 */
public class StatelessTestSuite extends TestCase {

    public StatelessTestSuite(String name) {
        super(name);
    }

    public static Test suite() {
        TestSuite suite = new TestSuite();
        suite.addTestSuite(TestStatelessEngine.class);
        return suite;
    }
}
