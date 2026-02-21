package org.yawlfoundation.yawl.patternmatching;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * Test Suite for Pattern Matching Conversions
 *
 * Tests all Java 25 pattern matching features:
 * - Switch expressions (59 conversions)
 * - Pattern variables (~100 conversions)
 * - Branch coverage for all cases
 * - Edge cases (null, invalid types, boundaries)
 *
 * Target Coverage:
 * - Branch coverage: >= 90%
 * - Line coverage: >= 80%
 * - All edge cases tested
 *
 * Author: YAWL Foundation
 * Date: 2026-02-16
 */
@Suite
@SuiteDisplayName("Pattern Matching Tests")
@SelectClasses({
    SwitchExpressionBranchTest.class,
    XSDTypeSwitchTest.class,
    YSchemaVersionSwitchTest.class,
    YWorkItemSwitchTest.class,
    YTimerParametersSwitchTest.class,
    InstanceofPatternTest.class,
    YSpecificationPatternTest.class,
    EnumExhaustivenessTest.class,
    PatternMatchingEdgeCaseTest.class,
    PatternMatchingRegressionTest.class,
    PatternMatchingIntegrationTest.class
})
public class PatternMatchingTestSuite {
    // JUnit 5 suite uses annotations - no main method needed
}
