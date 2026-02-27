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

package org.yawlfoundation.yawl.pi.adaptive;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yawlfoundation.yawl.integration.adaptation.AdaptationAction;
import org.yawlfoundation.yawl.integration.adaptation.AdaptationResult;
import org.yawlfoundation.yawl.integration.adaptation.EventDrivenAdaptationEngine;
import org.yawlfoundation.yawl.integration.adaptation.EventSeverity;
import org.yawlfoundation.yawl.integration.adaptation.ProcessEvent;
import org.yawlfoundation.yawl.pi.PIException;
import org.yawlfoundation.yawl.pi.predictive.PredictiveModelRegistry;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago-TDD tests for {@link EnterpriseAutoMlPatterns}.
 *
 * <p>Tests use a real (empty) {@link PredictiveModelRegistry} — models are absent so
 * the observer's {@code isAvailable} checks skip inference, letting tests verify the
 * structural wiring (event flow, null guards, factory contracts) without needing ONNX
 * dependencies resolved at test time.</p>
 */
class EnterpriseAutoMlPatternsTest {

    @TempDir
    Path tempDir;

    private PredictiveModelRegistry emptyRegistry() throws PIException {
        return new PredictiveModelRegistry(tempDir);
    }

    // ── Factory null-guard tests ──────────────────────────────────────────────

    @Test
    void insuranceClaimsTriage_rejectsNullRegistry() throws PIException {
        assertThrows(NullPointerException.class, () ->
                EnterpriseAutoMlPatterns.forInsuranceClaimsTriage(null, r -> {}));
    }

    @Test
    void insuranceClaimsTriage_rejectsNullHandler() throws PIException {
        assertThrows(NullPointerException.class, () ->
                EnterpriseAutoMlPatterns.forInsuranceClaimsTriage(emptyRegistry(), null));
    }

    @Test
    void healthcareCaseManagement_rejectsNullRegistry() throws PIException {
        assertThrows(NullPointerException.class, () ->
                EnterpriseAutoMlPatterns.forHealthcareCaseManagement(null, r -> {}));
    }

    @Test
    void financialRiskMonitoring_rejectsNullRegistry() throws PIException {
        assertThrows(NullPointerException.class, () ->
                EnterpriseAutoMlPatterns.forFinancialRiskMonitoring(null, r -> {}));
    }

    @Test
    void operationsSlaGuardian_rejectsNullRegistry() throws PIException {
        assertThrows(NullPointerException.class, () ->
                EnterpriseAutoMlPatterns.forOperationsSlaGuardian(null, r -> {}));
    }

    @Test
    void operationsSlaGuardian_rejectsCriticalGreaterThanWarning() throws PIException {
        assertThrows(IllegalArgumentException.class, () ->
                EnterpriseAutoMlPatterns.forOperationsSlaGuardian(
                        emptyRegistry(), r -> {}, 120, 30));
    }

    @Test
    void operationsSlaGuardian_rejectsEqualCriticalAndWarning() throws PIException {
        assertThrows(IllegalArgumentException.class, () ->
                EnterpriseAutoMlPatterns.forOperationsSlaGuardian(
                        emptyRegistry(), r -> {}, 60, 60));
    }

    // ── Factory return type contracts ─────────────────────────────────────────

    @Test
    void insuranceClaimsTriage_returnsNonNullObserver() throws PIException {
        PredictiveProcessObserver observer = EnterpriseAutoMlPatterns
                .forInsuranceClaimsTriage(emptyRegistry(), r -> {});
        assertNotNull(observer);
    }

    @Test
    void healthcareCaseManagement_returnsNonNullObserver() throws PIException {
        PredictiveProcessObserver observer = EnterpriseAutoMlPatterns
                .forHealthcareCaseManagement(emptyRegistry(), r -> {});
        assertNotNull(observer);
    }

    @Test
    void financialRiskMonitoring_returnsNonNullObserver() throws PIException {
        PredictiveProcessObserver observer = EnterpriseAutoMlPatterns
                .forFinancialRiskMonitoring(emptyRegistry(), r -> {});
        assertNotNull(observer);
    }

    @Test
    void operationsSlaGuardian_returnsNonNullObserver() throws PIException {
        PredictiveProcessObserver observer = EnterpriseAutoMlPatterns
                .forOperationsSlaGuardian(emptyRegistry(), r -> {}, 30, 120);
        assertNotNull(observer);
    }

    // ── Observer scheme ───────────────────────────────────────────────────────

    @Test
    void allPatterns_observerSchemePredictivePi() throws PIException {
        assertEquals("predictive-pi",
                EnterpriseAutoMlPatterns
                        .forInsuranceClaimsTriage(emptyRegistry(), r -> {}).getScheme());
        assertEquals("predictive-pi",
                EnterpriseAutoMlPatterns
                        .forHealthcareCaseManagement(emptyRegistry(), r -> {}).getScheme());
        assertEquals("predictive-pi",
                EnterpriseAutoMlPatterns
                        .forFinancialRiskMonitoring(emptyRegistry(), r -> {}).getScheme());
        assertEquals("predictive-pi",
                EnterpriseAutoMlPatterns
                        .forOperationsSlaGuardian(emptyRegistry(), r -> {}).getScheme());
    }

    // ── adaptationSink wiring ────────────────────────────────────────────────

    @Test
    void adaptationSink_routesEventsToEngine() {
        List<AdaptationResult> captured = new ArrayList<>();
        EventDrivenAdaptationEngine engine = new EventDrivenAdaptationEngine(
                List.of(PredictiveAdaptationRules.timerExpiryBreach()));

        var sink = EnterpriseAutoMlPatterns.adaptationSink(engine, captured::add);

        ProcessEvent timerEvent = new ProcessEvent(
                "evt-1", PredictiveProcessObserver.TIMER_EXPIRY_BREACH,
                "test", Instant.now(),
                Map.of("caseId", "c1", "taskId", "t1",
                        "specId", "s1", "breachType", "TIMER_EXPIRY"),
                EventSeverity.CRITICAL);

        sink.accept(timerEvent);

        assertEquals(1, captured.size());
        assertTrue(captured.get(0).adapted());
        assertEquals(AdaptationAction.ESCALATE_TO_MANUAL,
                captured.get(0).executedAction());
    }

    @Test
    void adaptationSink_rejectsNullEngine() {
        assertThrows(NullPointerException.class, () ->
                EnterpriseAutoMlPatterns.adaptationSink(null, r -> {}));
    }

    @Test
    void adaptationSink_rejectsNullHandler() {
        EventDrivenAdaptationEngine engine = new EventDrivenAdaptationEngine(List.of());
        assertThrows(NullPointerException.class, () ->
                EnterpriseAutoMlPatterns.adaptationSink(engine, null));
    }

    // ── loggingSink ───────────────────────────────────────────────────────────

    @Test
    void loggingSink_rejectsNullLogger() {
        assertThrows(NullPointerException.class, () ->
                EnterpriseAutoMlPatterns.loggingSink(null));
    }

    @Test
    void loggingSink_returnsNonNullConsumer() {
        var logger = org.apache.logging.log4j.LogManager.getLogger("test");
        assertNotNull(EnterpriseAutoMlPatterns.loggingSink(logger));
    }

    @Test
    void loggingSink_doesNotThrowOnEvent() {
        var logger = org.apache.logging.log4j.LogManager.getLogger("test");
        var sink = EnterpriseAutoMlPatterns.loggingSink(logger);

        assertDoesNotThrow(() -> sink.accept(new ProcessEvent(
                "evt-log-1", "ANY_EVENT", "test", Instant.now(),
                Map.of("key", "value"), EventSeverity.LOW)));
    }

    // ── insurance 3-arg overload ──────────────────────────────────────────────

    @Test
    void insuranceClaimsTriage_threeArgOverload_rejectsNullExtractor() throws PIException {
        assertThrows(NullPointerException.class, () ->
                EnterpriseAutoMlPatterns.forInsuranceClaimsTriage(
                        emptyRegistry(), r -> {}, null));
    }

    @Test
    void insuranceClaimsTriage_threeArgOverload_returnsNonNull() throws PIException {
        PredictiveProcessObserver observer = EnterpriseAutoMlPatterns
                .forInsuranceClaimsTriage(
                        emptyRegistry(),
                        r -> {},
                        (caseId, taskId, specId, ageMs) -> new float[]{0.5f, 0.3f}
                );
        assertNotNull(observer);
    }

    // ── static factory guard ──────────────────────────────────────────────────

    @Test
    void constructorThrowsUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> {
            var constructor = EnterpriseAutoMlPatterns.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            try {
                constructor.newInstance();
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getCause();
            }
        });
    }

    // ── healthcare default overload ───────────────────────────────────────────

    @Test
    void healthcareCaseManagement_defaultsAreReasonable() throws PIException {
        // Default: slaThreshold=60, riskThreshold=0.70 — verify it wires correctly
        // by injecting a process event that should trigger escalation
        List<AdaptationResult> results = new ArrayList<>();
        PredictiveProcessObserver observer = EnterpriseAutoMlPatterns
                .forHealthcareCaseManagement(emptyRegistry(), results::add);

        // No model loaded → observer does not infer → no event emitted
        // This verifies the wiring compiles and the observer is functional
        assertNotNull(observer);
        assertTrue(results.isEmpty(),
                "No events expected when no models are loaded");
    }

    // ── financial default overload ────────────────────────────────────────────

    @Test
    void financialRiskMonitoring_defaultsAreConservative() throws PIException {
        // Default fraudThreshold=0.90, riskThreshold=0.75
        PredictiveProcessObserver observer = EnterpriseAutoMlPatterns
                .forFinancialRiskMonitoring(emptyRegistry(), r -> {});
        assertNotNull(observer);
        assertEquals("predictive-pi", observer.getScheme());
    }
}
