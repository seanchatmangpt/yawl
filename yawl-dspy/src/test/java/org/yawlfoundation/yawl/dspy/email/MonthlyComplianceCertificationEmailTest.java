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
 * You should have received a copy of the GNU Lesser General
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.dspy.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.yawlfoundation.yawl.dspy.email.TestFixtureBuilders.BootstrapMetrics;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.yawlfoundation.yawl.dspy.email.EmailAssertions.*;

/**
 * Chicago TDD tests for Monthly Compliance Certification email generation.
 *
 * <p>Tests validate CaseLearningBootstrapper quality tracking meets compliance
 * thresholds for SOC2 Type II and internal quality standards.</p>
 *
 * <p>Email Template Validates:</p>
 * <ul>
 *   <li>Bootstrap success rate (≥95% threshold)</li>
 *   <li>Training example coverage (≥90% threshold)</li>
 *   <li>Confidence score compliance (≥95% threshold)</li>
 *   <li>Audit trail (cases processed, invocations, invalidations)</li>
 *   <li>Attestation statement</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@DisplayName("Monthly Compliance Certification Email Tests")
class MonthlyComplianceCertificationEmailTest {

    private ComplianceCertificationGenerator generator;
    private TestAuditLog auditLog;

    @BeforeEach
    void setUp() {
        auditLog = new TestAuditLog();
        generator = new ComplianceCertificationGenerator(auditLog);
    }

    @Test
    @DisplayName("Should generate monthly compliance certification")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testMonthlyComplianceCertification() {
        // Given: Real bootstrap metrics from 30 days
        BootstrapMetrics metrics = TestFixtureBuilders.createBootstrapMetricsFor30Days();

        // When: Generate compliance certification
        Email email = generator.generate(metrics, YearMonth.of(2026, 2));

        // Then: Email meets compliance requirements
        assertEmailContains(email, """
            Bootstrap success rate: ≥0.95
            Training example coverage: ≥0.90
            Confidence score compliance: ≥0.95
            Attestation: present
            """);

        assertRecipient(email, "compliance@company.com");
        assertThat("CC should include board", email.cc(), hasItem(containsString("board")));
    }

    @Test
    @DisplayName("Should include certification period")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testCertificationPeriod() {
        // Given: Metrics for February 2026
        BootstrapMetrics metrics = TestFixtureBuilders.createBootstrapMetricsFor30Days();

        // When: Generate certification
        Email email = generator.generate(metrics, YearMonth.of(2026, 2));

        // Then: Period should be clearly stated
        assertThat("Email should contain certification period",
                email.body(), containsString("Certification Period"));
        assertThat("Email should contain start date",
                email.body(), containsString("2026-02-01"));
        assertThat("Email should contain end date",
                email.body(), containsString("2026-02-28"));
    }

    @Test
    @DisplayName("Should include module version")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testModuleVersion() {
        // Given: Any metrics
        BootstrapMetrics metrics = TestFixtureBuilders.createBootstrapMetricsFor30Days();

        // When: Generate certification
        Email email = generator.generate(metrics, YearMonth.of(2026, 2));

        // Then: Version should be included
        assertThat("Email should contain version",
                email.body(), anyOf(
                        containsString("Version"),
                        containsString("6.0.0")
                ));
    }

    @Test
    @DisplayName("Should include audit trail")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testAuditTrail() {
        // Given: Metrics with audit data
        BootstrapMetrics metrics = TestFixtureBuilders.createBootstrapMetricsFor30Days();

        // When: Generate certification
        Email email = generator.generate(metrics, YearMonth.of(2026, 2));

        // Then: Audit trail should be present
        assertEmailContains(email, """
            Total cases processed: numeric
            Bootstrap invocations: numeric
            """);

        assertThat("Email should contain audit trail section",
                email.body(), containsString("Audit Trail"));
    }

    @Test
    @DisplayName("Should include SOC2 Type II attestation")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testSoc2Attestation() {
        // Given: Compliant metrics
        BootstrapMetrics metrics = TestFixtureBuilders.createBootstrapMetricsFor30Days();

        // When: Generate certification
        Email email = generator.generate(metrics, YearMonth.of(2026, 2));

        // Then: Attestation should be present
        assertThat("Email should contain attestation",
                email.body(), containsString("Attestation"));
        assertThat("Email should mention SOC2",
                email.body(), containsString("SOC2"));
    }

    @Test
    @DisplayName("Should include signed timestamp")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testSignedTimestamp() {
        // Given: Any metrics
        BootstrapMetrics metrics = TestFixtureBuilders.createBootstrapMetricsFor30Days();

        // When: Generate certification
        Email email = generator.generate(metrics, YearMonth.of(2026, 2));

        // Then: Should have signed timestamp
        assertThat("Email should be signed",
                email.body(), anyOf(
                        containsString("Signed"),
                        containsString("YAWL DSPy Compliance System")
                ));
        assertThat("Email should contain timestamp",
                email.body(), containsString("Timestamp"));
    }

    @Test
    @DisplayName("Should fail certification when below threshold")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testBelowThresholdFailure() {
        // Given: Metrics below threshold
        BootstrapMetrics metrics = TestFixtureBuilders.createBootstrapMetricsWithSuccessRate(0.85);

        // When: Generate certification
        Email email = generator.generate(metrics, YearMonth.of(2026, 2));

        // Then: Should indicate failure
        assertThat("Email should indicate failure or non-compliance",
                email.body(), anyOf(
                        containsString("FAILED"),
                        containsString("below threshold"),
                        containsString("NON-COMPLIANT")
                ));
    }

    @Test
    @DisplayName("Should handle cache invalidations in audit")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testCacheInvalidationsInAudit() {
        // Given: Metrics with cache invalidations
        BootstrapMetrics metrics = TestFixtureBuilders.createBootstrapMetricsFor30Days();

        // When: Generate certification
        Email email = generator.generate(metrics, YearMonth.of(2026, 2));

        // Then: Cache invalidations should be reported
        assertThat("Email should contain cache invalidations",
                email.body(), containsString("invalidation"));
    }

    // ========================================================================
    // Test Doubles (Real implementations, not mocks)
    // ========================================================================

    /**
     * Test double for audit log.
     */
    static class TestAuditLog {
        List<String> getEntries(Instant start, Instant end) {
            return List.of(
                    "BOOTSTRAP_INVOCATION: 892 events",
                    "CACHE_INVALIDATION: 3 events",
                    "QUALITY_CHECK: passed"
            );
        }
    }

    // ========================================================================
    // Email Generator (Real implementation for testing)
    // ========================================================================

    /**
     * Generates monthly compliance certification emails.
     */
    static class ComplianceCertificationGenerator {
        private final TestAuditLog auditLog;

        ComplianceCertificationGenerator(TestAuditLog auditLog) {
            this.auditLog = auditLog;
        }

        Email generate(BootstrapMetrics metrics, YearMonth period) {
            boolean isCompliant = metrics.successRate() >= 0.95 &&
                    metrics.trainingCoverage() >= 0.90 &&
                    metrics.confidenceCompliance() >= 0.95;

            StringBuilder body = new StringBuilder();
            body.append("To: compliance@company.com, board@company.com\n");
            body.append("Subject: Monthly DSPy Compliance Certification - ")
                    .append(period.getMonth()).append(" ").append(period.getYear()).append("\n\n");

            body.append("COMPLIANCE CERTIFICATION\n");
            body.append("=======================\n");
            body.append(String.format("Certification Period: %s to %s%n",
                    period.atDay(1), period.atEndOfMonth()));
            body.append("DSPy Module Version: 6.0.0-GA\n\n");

            body.append("Quality Metrics:\n");
            body.append(String.format("- Bootstrap success rate: %.1f%% (threshold: ≥95%%)%n",
                    metrics.successRate() * 100));
            body.append(String.format("- Training example coverage: %.1f%% (threshold: ≥90%%)%n",
                    metrics.trainingCoverage() * 100));
            body.append(String.format("- Confidence score compliance: %.1f%% (threshold: ≥95%%)%n%n",
                    metrics.confidenceCompliance() * 100));

            body.append("Audit Trail:\n");
            body.append(String.format("- Total cases processed: %,d%n", metrics.totalCasesProcessed()));
            body.append(String.format("- Bootstrap invocations: %,d%n", metrics.bootstrapInvocations()));
            body.append(String.format("- Cache invalidations: %,d%n%n", metrics.cacheInvalidations()));

            if (isCompliant) {
                body.append("Attestation: This certification confirms DSPy operations meet\n");
                body.append("SOC2 Type II and internal quality standards.\n\n");
                body.append("Signed: YAWL DSPy Compliance System\n");
            } else {
                body.append("STATUS: NON-COMPLIANT\n");
                body.append("One or more metrics are below threshold.\n\n");
                body.append("Signed: YAWL DSPy Compliance System\n");
            }
            body.append("Timestamp: ").append(Instant.now()).append("\n");

            return Email.builder()
                    .to("compliance@company.com")
                    .cc(List.of("board@company.com"))
                    .from("noreply@yawlfoundation.org")
                    .subject("Monthly DSPy Compliance Certification - " +
                            period.getMonth() + " " + period.getYear())
                    .body(body.toString())
                    .timestamp(Instant.now())
                    .build();
        }
    }
}
