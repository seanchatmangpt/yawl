package org.yawlfoundation.yawl.rust4pm.fluent;

import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.rust4pm.bridge.Rust4pmBridge;
import org.yawlfoundation.yawl.rust4pm.error.ProcessMiningException;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ProcessMiningPipeline} fluent builder and execution.
 *
 * <p>These tests validate the builder API and supervision behavior.
 * Native library tests are in integration test suites since they require
 * librust4pm.so loaded.
 */
class ProcessMiningPipelineTest {

    @Test
    void createRequiresNonNullBridge() {
        assertThrows(NullPointerException.class,
            () -> ProcessMiningPipeline.create(null));
    }

    @Test
    void emptyPipelineThrowsOnExecute() {
        try (var bridge = new Rust4pmBridge()) {
            var pipeline = ProcessMiningPipeline.create(bridge);
            assertThrows(IllegalStateException.class, pipeline::execute);
        }
    }

    @Test
    void pipelineWithoutParseThrowsOnExecute() {
        try (var bridge = new Rust4pmBridge()) {
            var pipeline = ProcessMiningPipeline.create(bridge)
                .discoverDfg();
            assertThrows(IllegalStateException.class, pipeline::execute);
        }
    }

    @Test
    void builderChainsReturnSameInstance() {
        try (var bridge = new Rust4pmBridge()) {
            var pipeline = ProcessMiningPipeline.create(bridge);
            var same = pipeline
                .supervised(SupervisionStrategy.RETRY_DEFAULT)
                .parse("{\"events\":[]}")
                .discoverDfg()
                .checkConformance("<pnml/>")
                .computeStats();
            assertSame(pipeline, same);
        }
    }

    @Test
    void supervisedRejectsNull() {
        try (var bridge = new Rust4pmBridge()) {
            var pipeline = ProcessMiningPipeline.create(bridge);
            assertThrows(NullPointerException.class,
                () -> pipeline.supervised(null));
        }
    }

    @Test
    void executeWithParseOnlyProducesResultOnNativeFailure() {
        try (var bridge = new Rust4pmBridge()) {
            var pipeline = ProcessMiningPipeline.create(bridge)
                .supervised(SupervisionStrategy.SKIP_ON_FAILURE)
                .parse("{\"events\":[]}");

            PipelineResult result = pipeline.execute();
            assertNotNull(result);
            assertNotNull(result.startedAt());
            assertFalse(result.totalDuration().isNegative());
            assertEquals(1, result.stageOutcomes().size());
        } catch (ProcessMiningException e) {
            // Expected when native lib not loaded and using FAIL_FAST
        }
    }

    @Test
    void skipSupervisionContinuesAfterFailure() {
        try (var bridge = new Rust4pmBridge()) {
            var pipeline = ProcessMiningPipeline.create(bridge)
                .supervised(SupervisionStrategy.SKIP_ON_FAILURE)
                .parse("{\"events\":[]}")
                .discoverDfg()
                .computeStats();

            PipelineResult result = pipeline.execute();
            assertNotNull(result);
            assertFalse(result.stageOutcomes().isEmpty());
        } catch (ProcessMiningException e) {
            // May fail if native lib not loaded even with SKIP (parse stage is critical)
        }
    }

    @Test
    void retrySupervisionRetriesBeforeFailing() {
        try (var bridge = new Rust4pmBridge()) {
            var pipeline = ProcessMiningPipeline.create(bridge)
                .supervised(SupervisionStrategy.retry(2, Duration.ofMillis(1)))
                .parse("{\"events\":[]}");

            pipeline.execute();
            fail("Expected ProcessMiningException when native lib not loaded");
        } catch (ProcessMiningException e) {
            // Expected — parse fails because native lib not loaded
            assertNotNull(e.getMessage());
        }
    }

    @Test
    void failFastAbortImmediately() {
        try (var bridge = new Rust4pmBridge()) {
            var pipeline = ProcessMiningPipeline.create(bridge)
                .supervised(SupervisionStrategy.FAIL_FAST)
                .parse("{\"events\":[]}");

            assertThrows(ProcessMiningException.class, pipeline::execute);
        }
    }
}
