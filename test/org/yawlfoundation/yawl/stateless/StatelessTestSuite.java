package org.yawlfoundation.yawl.stateless;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

/**
 * JUnit 5 test suite for the stateless YAWL engine (YStatelessEngine).
 * Run stateless mode tests before stateful engine tests (EngineTestSuite).
 */
@Suite
@SelectClasses({
    TestStatelessEngine.class
})
public class StatelessTestSuite {
}
