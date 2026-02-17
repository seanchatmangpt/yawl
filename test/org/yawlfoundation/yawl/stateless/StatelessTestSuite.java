package org.yawlfoundation.yawl.stateless;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * JUnit 5 test suite for the stateless YAWL engine (YStatelessEngine).
 * Run stateless mode tests before stateful engine tests (EngineTestSuite).
 *
 * YStatelessEngineSuspendResumeTest covers suspend/resume, cancel, marshal/restore,
 * listener management, and case monitoring - previously untested feature surface.
 */
@Suite
@SuiteDisplayName("Stateless Test Suite")
@SelectClasses({
    TestStatelessEngine.class,
    StatelessEngineCaseMonitorTest.class,
    YStatelessEngineSuspendResumeTest.class
})
public class StatelessTestSuite {
    // JUnit 5 suite uses annotations - no main method needed
}
