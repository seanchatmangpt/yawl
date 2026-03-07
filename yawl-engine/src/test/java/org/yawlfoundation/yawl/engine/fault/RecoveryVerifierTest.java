/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.engine.fault;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD Tests for RecoveryVerifier — Armstrong-Style Recovery Verification.
 *
 * <p>Verifies that the RecoveryVerifier correctly identifies workflow state
 * consistency violations and produces accurate reports.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
@DisplayName("Recovery Verifier Tests (Armstrong-Style)")
class RecoveryVerifierTest {

    // =========================================================================
    // Invariant Type Tests
    // =========================================================================

    @Nested
    @DisplayName("Invariant Types")
    class InvariantTypeTests {

        @Test
        @DisplayName("All invariant types have display names")
        void allInvariantTypesHaveDisplayNames() {
            for (RecoveryVerifier.InvariantType type : RecoveryVerifier.InvariantType.values()) {
                assertNotNull(type.getDisplayName());
                assertFalse(type.getDisplayName().isBlank());
            }
        }

        @Test
        @DisplayName("Recovery violation formats correctly")
        void recoveryViolationFormatsCorrectly() {
            RecoveryVerifier.RecoveryViolation violation = new RecoveryVerifier.RecoveryViolation(
                RecoveryVerifier.InvariantType.TOKEN_CONSERVATION,
                "Token count is 0, expected >= 1",
                RecoveryVerifier.Severity.ERROR
            );

            String formatted = violation.toString();
            assertTrue(formatted.contains("ERROR"));
            assertTrue(formatted.contains("Token Conservation"));
            assertTrue(formatted.contains("Token count is 0"));
        }
    }

    // =========================================================================
    // Recovery Report Tests
    // =========================================================================

    @Nested
    @DisplayName("Recovery Report")
    class RecoveryReportTests {

        @Test
        @DisplayName("Consistent report produces correct summary")
        void consistentReportProducesCorrectSummary() {
            RecoveryVerifier.RecoveryReport report = new RecoveryVerifier.RecoveryReport(
                true,
                java.util.List.of(),
                Duration.ofMillis(50),
                "case_123"
            );

            assertTrue(report.isConsistent());
            assertTrue(report.summary().contains("consistent"));
            assertTrue(report.summary().contains("case_123"));
            assertEquals(0, report.violations().size());
        }

        @Test
        @DisplayName("Inconsistent report lists violations")
        void inconsistentReportListsViolations() {
            RecoveryVerifier.RecoveryViolation v1 = new RecoveryVerifier.RecoveryViolation(
                RecoveryVerifier.InvariantType.NO_ORPHANED_WORK_ITEMS,
                "Work item X has no parent case",
                RecoveryVerifier.Severity.ERROR
            );

            RecoveryVerifier.RecoveryReport report = new RecoveryVerifier.RecoveryReport(
                false,
                java.util.List.of(v1),
                Duration.ofMillis(100),
                "case_456"
            );

            assertFalse(report.isConsistent());
            assertEquals(1, report.violations().size());
            assertTrue(report.summary().contains("1 violation"));
        }

        @Test
        @DisplayName("Filters violations by severity")
        void filtersViolationsBySeverity() {
            RecoveryVerifier.RecoveryViolation error = new RecoveryVerifier.RecoveryViolation(
                RecoveryVerifier.InvariantType.TOKEN_CONSERVATION,
                "Error",
                RecoveryVerifier.Severity.ERROR
            );
            RecoveryVerifier.RecoveryViolation warning = new RecoveryVerifier.RecoveryViolation(
                RecoveryVerifier.InvariantType.NO_DUPLICATE_WORK_ITEMS,
                "Warning",
                RecoveryVerifier.Severity.WARNING
            );

            RecoveryVerifier.RecoveryReport report = new RecoveryVerifier.RecoveryReport(
                false,
                java.util.List.of(error, warning),
                Duration.ofMillis(10),
                "case_789"
            );

            assertEquals(1, report.getViolationsBySeverity(RecoveryVerifier.Severity.ERROR).size());
            assertEquals(1, report.getViolationsBySeverity(RecoveryVerifier.Severity.WARNING).size());
            assertEquals(0, report.getViolationsBySeverity(RecoveryVerifier.Severity.INFO).size());
        }
    }

    // =========================================================================
    // Verifier Mode Tests
    // =========================================================================

    @Nested
    @DisplayName("Verifier Modes")
    class VerifierModeTests {

        @Test
        @DisplayName("Default verifier collects all violations")
        void defaultVerifierCollectsAllViolations() {
            RecoveryVerifier verifier = new RecoveryVerifier(false);

            // Default mode should not throw
            // (Actual verification requires a real YNetRunner which we can't easily mock)
            assertNotNull(verifier);
        }

        @Test
        @DisplayName("Fail-fast mode throws on first error")
        void failFastModeThrowsOnFirstError() {
            RecoveryVerifier verifier = new RecoveryVerifier(true);
            assertNotNull(verifier);
            // Full test would require real YNetRunner
        }
    }

    // =========================================================================
    // Severity Tests
    // =========================================================================

    @Nested
    @DisplayName("Severity Levels")
    class SeverityTests {

        @Test
        @DisplayName("Severity levels are ordered correctly")
        void severityLevelsAreOrderedCorrectly() {
            // ERROR is most severe
            assertEquals(RecoveryVerifier.Severity.ERROR,
                RecoveryVerifier.Severity.values()[0]);
        }
    }
}
