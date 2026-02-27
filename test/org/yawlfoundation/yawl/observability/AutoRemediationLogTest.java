package org.yawlfoundation.yawl.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD: Real remediation logging with Micrometer metrics.
 * Tests AutoRemediationLog with actual event capture and root cause analysis.
 */
class AutoRemediationLogTest {

    private AutoRemediationLog log;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        log = new AutoRemediationLog(meterRegistry);
    }

    @Test
    void testLogTimeoutRecovery_Successful() {
        log.logTimeoutRecovery("item-1", 5000, "retry_with_backoff", true);

        assertTrue(log.getTotalRemediations() > 0);
    }

    @Test
    void testLogTimeoutRecovery_Failed() {
        log.logTimeoutRecovery("item-1", 5000, "retry_escalate", false);

        assertTrue(log.getTotalRemediations() > 0);
    }

    @Test
    void testLogResourceMitigation_ContentionType() {
        log.logResourceMitigation("db_pool", "connection_exhaustion", "increase_pool_size", true);

        assertTrue(log.getTotalRemediations() > 0);
    }

    @Test
    void testLogResourceMitigation_Failed() {
        log.logResourceMitigation("memory", "oom", "garbage_collect", false);

        assertTrue(log.getTotalRemediations() > 0);
    }

    @Test
    void testLogDeadlockResolution_Successful() {
        log.logDeadlockResolution("case-1", "task_a_to_task_b", "compensate_task_a", true);

        assertTrue(log.getTotalRemediations() > 0);
    }

    @Test
    void testLogDeadlockResolution_Failed() {
        log.logDeadlockResolution("case-1", "circular_dependency", "rollback_case", false);

        assertTrue(log.getTotalRemediations() > 0);
    }

    @Test
    void testLogStateReconciliation_Successful() {
        log.logStateReconciliation("item-1", "inconsistent_status", "query_database_and_sync", true);

        assertTrue(log.getTotalRemediations() > 0);
    }

    @Test
    void testLogStateReconciliation_Failed() {
        log.logStateReconciliation("item-2", "version_mismatch", "rebuild_from_log", false);

        assertTrue(log.getTotalRemediations() > 0);
    }

    @Test
    void testLogRemediation_CustomType_Success() {
        Map<String, Object> context = new HashMap<>();
        context.put("custom_field", "value123");
        context.put("attempt", 1);

        log.logRemediation("custom_recovery", "special_action", true, context);

        assertEquals(1, log.getTotalRemediations());
    }

    @Test
    void testLogRemediation_CustomType_Failure() {
        Map<String, Object> context = new HashMap<>();
        context.put("reason", "timeout");

        log.logRemediation("custom_recovery", "special_action", false, context);

        assertEquals(1, log.getTotalRemediations());
    }

    @Test
    void testSuccessRate_AllSuccessful() {
        log.logRemediation("timeout_recovery", "retry", true, new HashMap<>());
        log.logRemediation("timeout_recovery", "retry", true, new HashMap<>());
        log.logRemediation("timeout_recovery", "retry", true, new HashMap<>());

        double rate = log.getSuccessRate("timeout_recovery");
        assertEquals(1.0, rate);
    }

    @Test
    void testSuccessRate_AllFailed() {
        log.logRemediation("timeout_recovery", "retry", false, new HashMap<>());
        log.logRemediation("timeout_recovery", "retry", false, new HashMap<>());

        double rate = log.getSuccessRate("timeout_recovery");
        assertEquals(0.0, rate);
    }

    @Test
    void testSuccessRate_Mixed() {
        log.logRemediation("resource_mitigation", "throttle", true, new HashMap<>());
        log.logRemediation("resource_mitigation", "throttle", true, new HashMap<>());
        log.logRemediation("resource_mitigation", "throttle", false, new HashMap<>());

        double rate = log.getSuccessRate("resource_mitigation");
        assertTrue(rate > 0.5 && rate < 1.0);
    }

    @Test
    void testSuccessRate_NoRemediations_Zero() {
        double rate = log.getSuccessRate("nonexistent_type");
        assertEquals(0.0, rate);
    }

    @Test
    void testTotalRemediations_Count() {
        log.logRemediation("type_1", "action_1", true, new HashMap<>());
        log.logRemediation("type_2", "action_2", true, new HashMap<>());
        log.logRemediation("type_1", "action_3", false, new HashMap<>());

        assertEquals(3, log.getTotalRemediations());
    }

    @Test
    void testReset_ClearsAllCounters() {
        log.logRemediation("type_1", "action_1", true, new HashMap<>());
        log.logRemediation("type_2", "action_2", true, new HashMap<>());

        assertEquals(2, log.getTotalRemediations());

        log.reset();

        assertEquals(0, log.getTotalRemediations());
        assertEquals(0.0, log.getSuccessRate("type_1"));
    }

    @Test
    void testRemediationScenario_StartScenario() {
        AutoRemediationLog.RemediationScenario scenario = log.startRemediationScenario(
                "scenario-1", "timeout_recovery"
        );

        assertNotNull(scenario);
        assertEquals("scenario-1", scenario.getScenarioId());
        assertFalse(scenario.isCompleted());
    }

    @Test
    void testRemediationScenario_CompleteSuccessfully() {
        AutoRemediationLog.RemediationScenario scenario = log.startRemediationScenario(
                "scenario-1", "timeout_recovery"
        );

        Map<String, Object> stepData = new HashMap<>();
        stepData.put("retry_count", 3);
        scenario.recordStep("attempt_retry", stepData, true);

        scenario.complete();

        assertTrue(scenario.isCompleted());
        assertTrue(log.getTotalRemediations() > 0);
    }

    @Test
    void testRemediationScenario_FailWithRollback() {
        AutoRemediationLog.RemediationScenario scenario = log.startRemediationScenario(
                "scenario-1", "deadlock_resolution"
        );

        scenario.recordStep("detect_deadlock", new HashMap<>(), true);
        scenario.fail("Rollback executed, case returned to previous state");

        assertTrue(scenario.isCompleted());
        assertTrue(log.getTotalRemediations() > 0);
    }

    @Test
    void testRemediationScenario_MultipleSteps() {
        AutoRemediationLog.RemediationScenario scenario = log.startRemediationScenario(
                "complex-scenario", "state_reconciliation"
        );

        Map<String, Object> step1 = new HashMap<>();
        step1.put("action", "verify_database");
        scenario.recordStep("step_1_verify", step1, true);

        Map<String, Object> step2 = new HashMap<>();
        step2.put("action", "rebuild_index");
        scenario.recordStep("step_2_rebuild", step2, true);

        scenario.complete();

        assertTrue(scenario.isCompleted());
    }

    @Test
    void testMultipleRemediationTypes_IndependentTracking() {
        log.logTimeoutRecovery("item-1", 5000, "retry", true);
        log.logResourceMitigation("resource-1", "exhaustion", "scale_up", true);
        log.logDeadlockResolution("case-1", "circular", "compensate", true);

        assertEquals(3, log.getTotalRemediations());
        assertEquals(1.0, log.getSuccessRate("timeout_recovery"));
        assertEquals(1.0, log.getSuccessRate("resource_mitigation"));
        assertEquals(1.0, log.getSuccessRate("deadlock_resolution"));
    }

    @Test
    void testLogRemediation_NullType_Rejected() {
        assertThrows(NullPointerException.class, () -> {
            log.logRemediation(null, "action", true, new HashMap<>());
        });
    }

    @Test
    void testLogRemediation_NullAction_Rejected() {
        assertThrows(NullPointerException.class, () -> {
            log.logRemediation("type", null, true, new HashMap<>());
        });
    }

    @Test
    void testLogRemediation_NullContext_Rejected() {
        assertThrows(NullPointerException.class, () -> {
            log.logRemediation("type", "action", true, null);
        });
    }

    @Test
    void testLogTimeoutRecovery_NullItemId_Rejected() {
        assertThrows(NullPointerException.class, () -> {
            log.logTimeoutRecovery(null, 5000, "retry", true);
        });
    }

    @Test
    void testLogTimeoutRecovery_NullAction_Rejected() {
        assertThrows(NullPointerException.class, () -> {
            log.logTimeoutRecovery("item-1", 5000, null, true);
        });
    }

    @Test
    void testContextPropagation_LargeContext() {
        Map<String, Object> context = new HashMap<>();
        for (int i = 0; i < 50; i++) {
            context.put("field_" + i, "value_" + i);
        }

        log.logRemediation("complex_type", "complex_action", true, context);

        assertEquals(1, log.getTotalRemediations());
    }

    @Test
    void testMetricsExport_SuccessCounter() {
        log.logRemediation("timeout_recovery", "retry", true, new HashMap<>());
        log.logRemediation("timeout_recovery", "retry", true, new HashMap<>());

        // Should be able to query Micrometer metrics
        long count = meterRegistry.find("yawl.remediation.success")
                .tag("remediation_type", "timeout_recovery")
                .counter()
                .map(c -> (long) c.count())
                .orElse(0L);

        assertEquals(2, count);
    }

    @Test
    void testMetricsExport_FailureCounter() {
        log.logRemediation("timeout_recovery", "retry", false, new HashMap<>());

        long count = meterRegistry.find("yawl.remediation.failure")
                .tag("remediation_type", "timeout_recovery")
                .counter()
                .map(c -> (long) c.count())
                .orElse(0L);

        assertEquals(1, count);
    }

    @Test
    void testScenario_NullScenarioId_Rejected() {
        assertThrows(NullPointerException.class, () -> {
            log.startRemediationScenario(null, "type");
        });
    }

    @Test
    void testScenario_NullRemediationType_Rejected() {
        assertThrows(NullPointerException.class, () -> {
            log.startRemediationScenario("scenario-1", null);
        });
    }

    @Test
    void testRemediationEdgeCases_ZeroTimeTimeout() {
        log.logTimeoutRecovery("item-1", 0, "immediate_escalate", true);
        assertEquals(1, log.getTotalRemediations());
    }

    @Test
    void testRemediationEdgeCases_LargeTimeout() {
        log.logTimeoutRecovery("item-1", Long.MAX_VALUE, "ultimate_escalate", false);
        assertEquals(1, log.getTotalRemediations());
    }
}
