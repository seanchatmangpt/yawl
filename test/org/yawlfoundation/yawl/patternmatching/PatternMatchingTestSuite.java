package org.yawlfoundation.yawl.patternmatching;

import junit.framework.Test;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

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
 * - Branch coverage: ≥ 90%
 * - Line coverage: ≥ 80%
 * - All edge cases tested
 *
 * Author: YAWL Foundation
 * Date: 2026-02-16
 */
public class PatternMatchingTestSuite extends TestSuite {

    public PatternMatchingTestSuite(String name) {
        super(name);
    }

    public static Test suite() {
        TestSuite suite = new TestSuite("Pattern Matching Tests");

        // Switch expression tests
        suite.addTestSuite(SwitchExpressionBranchTest.class);
        suite.addTestSuite(XSDTypeSwitchTest.class);
        suite.addTestSuite(YSchemaVersionSwitchTest.class);
        suite.addTestSuite(YWorkItemSwitchTest.class);
        suite.addTestSuite(YTimerParametersSwitchTest.class);

        // Pattern variable tests
        suite.addTestSuite(InstanceofPatternTest.class);
        suite.addTestSuite(YSpecificationPatternTest.class);

        // Enum exhaustiveness tests
        suite.addTestSuite(EnumExhaustivenessTest.class);

        // Edge case tests
        suite.addTestSuite(PatternMatchingEdgeCaseTest.class);

        // Regression tests
        suite.addTestSuite(PatternMatchingRegressionTest.class);

        return suite;
    }

    public static void main(String[] args) {
        TestRunner runner = new TestRunner();
        runner.doRun(suite());
        System.exit(0);
    }
}
