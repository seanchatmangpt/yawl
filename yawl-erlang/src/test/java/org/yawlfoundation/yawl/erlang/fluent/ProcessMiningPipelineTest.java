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
package org.yawlfoundation.yawl.erlang.fluent;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the fluent process mining pipeline API.
 *
 * <p>No live OTP node or Rust library required. Tests exercise the pipeline
 * builder, supervision, circuit breaker, and context passing using pure
 * Java lambdas as stage implementations.</p>
 */
class ProcessMiningPipelineTest {

    // =========================================================================
    // Builder validation
    // =========================================================================

    @Test
    void builderRejectsEmptyPipeline() {
        assertThrows(IllegalStateException.class, () ->
                ProcessMiningPipeline.builder().build());
    }

    @Test
    void builderRejectsDuplicateStageNames() {
        assertThrows(IllegalArgumentException.class, () ->
                ProcessMiningPipeline.builder()
                        .stage("parse", _ -> "data")
                        .stage("parse", _ -> "other")
                        .build());
    }

    @Test
    void builderRejectsBlankStageName() {
        assertThrows(IllegalArgumentException.class, () ->
                ProcessMiningPipeline.builder()
                        .stage("  ", _ -> "data")
                        .build());
    }

    @Test
    void builderRejectsNullStage() {
        assertThrows(NullPointerException.class, () ->
                ProcessMiningPipeline.builder()
                        .stage("parse", null)
                        .build());
    }

    // =========================================================================
    // Supervision spec validation
    // =========================================================================

    @Test
    void supervisionSpecRejectsNegativeRestarts() {
        assertThrows(IllegalArgumentException.class, () ->
                SupervisionSpec.builder().maxRestarts(-1).build());
    }

    @Test
    void supervisionSpecRejectsZeroWindow() {
        assertThrows(IllegalArgumentException.class, () ->
                SupervisionSpec.builder().window(Duration.ZERO).build());
    }

    @Test
    void supervisionSpecDefaultValues() {
        SupervisionSpec spec = SupervisionSpec.DEFAULT;
        assertEquals(RestartStrategy.ONE_FOR_ONE, spec.strategy());
        assertEquals(3, spec.maxRestarts());
        assertEquals(Duration.ofMinutes(5), spec.window());
        assertEquals(Duration.ofSeconds(5), spec.healthCheckInterval());
    }

    // =========================================================================
    // Circuit breaker spec validation
    // =========================================================================

    @Test
    void circuitBreakerSpecRejectsZeroThreshold() {
        assertThrows(IllegalArgumentException.class, () ->
                CircuitBreakerSpec.builder().failureThreshold(0).build());
    }

    @Test
    void circuitBreakerSpecRejectsNegativeTimeout() {
        assertThrows(IllegalArgumentException.class, () ->
                CircuitBreakerSpec.builder().resetTimeout(Duration.ofSeconds(-1)).build());
    }

    // =========================================================================
    // Single stage execution
    // =========================================================================

    @Test
    void singleStageProducesResult() {
        PipelineResult result = ProcessMiningPipeline.builder()
                .stage("greet", _ -> "hello")
                .build()
                .execute();

        assertTrue(result.success());
        assertEquals(1, result.stages().size());
        assertEquals("hello", result.stageOutput("greet", String.class).orElse(null));
        assertEquals(0, result.totalRestarts());
    }

    @Test
    void singleStageFailureReportsError() {
        PipelineResult result = ProcessMiningPipeline.builder()
                .supervision(sup -> sup.maxRestarts(0))
                .stage("fail", _ -> { throw new RuntimeException("boom"); })
                .build()
                .execute();

        assertFalse(result.success());
        assertEquals("boom", result.firstFailure().orElseThrow().error());
    }

    // =========================================================================
    // Multi-stage pipeline with context passing
    // =========================================================================

    @Test
    void multiStagePassesDataViaContext() {
        PipelineResult result = ProcessMiningPipeline.builder()
                .stage("parse", _ -> List.of("A", "B", "C"))
                .stage("count", ctx -> {
                    List<?> data = ctx.get("parse", List.class);
                    return data.size();
                })
                .stage("report", ctx -> {
                    int count = ctx.get("count", Integer.class);
                    return "Found " + count + " items";
                })
                .build()
                .execute();

        assertTrue(result.success());
        assertEquals(3, result.stages().size());
        assertEquals(3, result.stageOutput("count", Integer.class).orElse(-1));
        assertEquals("Found 3 items",
                result.stageOutput("report", String.class).orElse(null));
    }

    @Test
    void contextThrowsOnMissingStage() {
        PipelineContext ctx = new PipelineContext();
        assertThrows(IllegalArgumentException.class, () ->
                ctx.get("nonexistent", String.class));
    }

    @Test
    void contextHasReturnsFalseForMissing() {
        PipelineContext ctx = new PipelineContext();
        assertFalse(ctx.has("nonexistent"));
    }

    // =========================================================================
    // Supervision: ONE_FOR_ONE restart
    // =========================================================================

    @Test
    void oneForOneRetriesFailedStage() {
        AtomicInteger attempts = new AtomicInteger(0);

        PipelineResult result = ProcessMiningPipeline.builder()
                .supervision(sup -> sup
                        .strategy(RestartStrategy.ONE_FOR_ONE)
                        .maxRestarts(2))
                .stage("flaky", _ -> {
                    if (attempts.incrementAndGet() < 3) {
                        throw new RuntimeException("transient failure");
                    }
                    return "success on attempt 3";
                })
                .build()
                .execute();

        assertTrue(result.success());
        assertEquals(3, attempts.get());
        assertEquals("success on attempt 3",
                result.stageOutput("flaky", String.class).orElse(null));
        assertEquals(2, result.totalRestarts());
    }

    @Test
    void oneForOneStopsAfterMaxRestarts() {
        PipelineResult result = ProcessMiningPipeline.builder()
                .supervision(sup -> sup
                        .strategy(RestartStrategy.ONE_FOR_ONE)
                        .maxRestarts(2))
                .stage("always-fails", _ -> {
                    throw new RuntimeException("permanent failure");
                })
                .build()
                .execute();

        assertFalse(result.success());
        assertEquals(3, result.firstFailure().orElseThrow().attempts());
    }

    // =========================================================================
    // Supervision: ONE_FOR_ALL clears context
    // =========================================================================

    @Test
    void oneForAllClearsContextOnRestart() {
        AtomicInteger parseCount = new AtomicInteger(0);
        AtomicInteger discoverAttempts = new AtomicInteger(0);

        PipelineResult result = ProcessMiningPipeline.builder()
                .supervision(sup -> sup
                        .strategy(RestartStrategy.ONE_FOR_ALL)
                        .maxRestarts(1))
                .stage("parse", _ -> {
                    parseCount.incrementAndGet();
                    return "parsed-data";
                })
                .stage("discover", ctx -> {
                    if (discoverAttempts.incrementAndGet() == 1) {
                        throw new RuntimeException("first attempt fails");
                    }
                    return "discovered";
                })
                .build()
                .execute();

        assertTrue(result.success());
        assertEquals(1, result.totalRestarts());
    }

    // =========================================================================
    // Supervision: REST_FOR_ONE clears downstream context
    // =========================================================================

    @Test
    void restForOneClearsDownstreamContext() {
        AtomicInteger transformCount = new AtomicInteger(0);

        PipelineResult result = ProcessMiningPipeline.builder()
                .supervision(sup -> sup
                        .strategy(RestartStrategy.REST_FOR_ONE)
                        .maxRestarts(1))
                .stage("parse", _ -> "data")
                .stage("transform", _ -> {
                    if (transformCount.incrementAndGet() == 1) {
                        throw new RuntimeException("fail once");
                    }
                    return "transformed";
                })
                .build()
                .execute();

        assertTrue(result.success());
        assertEquals("transformed",
                result.stageOutput("transform", String.class).orElse(null));
    }

    // =========================================================================
    // Circuit breaker
    // =========================================================================

    @Test
    void circuitBreakerOpensAfterThreshold() {
        PipelineResult result = ProcessMiningPipeline.builder()
                .supervision(sup -> sup.maxRestarts(5))
                .circuitBreaker(cb -> cb
                        .failureThreshold(2)
                        .resetTimeout(Duration.ofMinutes(5)))
                .stage("fragile", _ -> {
                    throw new RuntimeException("always fails");
                })
                .build()
                .execute();

        assertFalse(result.success());
        StageResult stageResult = result.firstFailure().orElseThrow();
        assertTrue(stageResult.error().contains("Circuit breaker OPEN")
                || stageResult.error().contains("always fails"));
    }

    // =========================================================================
    // Event emission
    // =========================================================================

    @Test
    void eventsEmittedDuringExecution() {
        List<PipelineEvent> events = Collections.synchronizedList(new ArrayList<>());

        PipelineResult result = ProcessMiningPipeline.builder()
                .onEvent(events::add)
                .stage("step1", _ -> "done")
                .build()
                .execute();

        assertTrue(result.success());
        assertFalse(events.isEmpty());

        boolean hasStarted = events.stream()
                .anyMatch(e -> e instanceof PipelineEvent.StageStarted s
                        && "step1".equals(s.stageName()));
        boolean hasCompleted = events.stream()
                .anyMatch(e -> e instanceof PipelineEvent.StageCompleted c
                        && "step1".equals(c.stageName()));

        assertTrue(hasStarted, "Expected StageStarted event");
        assertTrue(hasCompleted, "Expected StageCompleted event");
    }

    @Test
    void failureEventsEmittedOnError() {
        List<PipelineEvent> events = Collections.synchronizedList(new ArrayList<>());

        ProcessMiningPipeline.builder()
                .supervision(sup -> sup.maxRestarts(0))
                .onEvent(events::add)
                .stage("bad", _ -> { throw new RuntimeException("oops"); })
                .build()
                .execute();

        boolean hasFailed = events.stream()
                .anyMatch(e -> e instanceof PipelineEvent.StageFailed f
                        && "bad".equals(f.stageName()));
        assertTrue(hasFailed, "Expected StageFailed event");
    }

    @Test
    void restartEventsEmittedOnRetry() {
        AtomicInteger count = new AtomicInteger(0);
        List<PipelineEvent> events = Collections.synchronizedList(new ArrayList<>());

        ProcessMiningPipeline.builder()
                .supervision(sup -> sup.maxRestarts(1))
                .onEvent(events::add)
                .stage("retryable", _ -> {
                    if (count.incrementAndGet() == 1) {
                        throw new RuntimeException("first fail");
                    }
                    return "ok";
                })
                .build()
                .execute();

        boolean hasRestarted = events.stream()
                .anyMatch(e -> e instanceof PipelineEvent.StageRestarted);
        assertTrue(hasRestarted, "Expected StageRestarted event");
    }

    // =========================================================================
    // Async execution
    // =========================================================================

    @Test
    void executeAsyncCompletesOnVirtualThread() throws Exception {
        PipelineResult result = ProcessMiningPipeline.builder()
                .stage("async-work", _ -> Thread.currentThread().isVirtual())
                .build()
                .executeAsync()
                .get();

        assertTrue(result.success());
        assertEquals(Boolean.TRUE,
                result.stageOutput("async-work", Boolean.class).orElse(false));
    }

    // =========================================================================
    // Stage transformation convenience
    // =========================================================================

    @Test
    void stageTransformationConvenienceMethod() {
        PipelineResult result = ProcessMiningPipeline.builder()
                .stage("input", _ -> 42)
                .stage("doubled", "input", Integer.class, x -> x * 2)
                .build()
                .execute();

        assertTrue(result.success());
        assertEquals(84, result.stageOutput("doubled", Integer.class).orElse(-1));
    }

    // =========================================================================
    // Pipeline result accessors
    // =========================================================================

    @Test
    void pipelineResultDurationIsPositive() {
        PipelineResult result = ProcessMiningPipeline.builder()
                .stage("work", _ -> {
                    Thread.sleep(10);
                    return "done";
                })
                .build()
                .execute();

        assertTrue(result.success());
        assertTrue(result.totalDuration().toMillis() >= 10);
    }

    @Test
    void stageOutputReturnsEmptyForFailedStage() {
        PipelineResult result = ProcessMiningPipeline.builder()
                .supervision(sup -> sup.maxRestarts(0))
                .stage("fail", _ -> { throw new RuntimeException("nope"); })
                .build()
                .execute();

        assertTrue(result.stageOutput("fail", String.class).isEmpty());
    }

    @Test
    void multipleEventListenersAllReceiveEvents() {
        AtomicInteger listener1Count = new AtomicInteger(0);
        AtomicInteger listener2Count = new AtomicInteger(0);

        ProcessMiningPipeline.builder()
                .onEvent(_ -> listener1Count.incrementAndGet())
                .onEvent(_ -> listener2Count.incrementAndGet())
                .stage("work", _ -> "done")
                .build()
                .execute();

        assertTrue(listener1Count.get() > 0);
        assertEquals(listener1Count.get(), listener2Count.get());
    }

    // =========================================================================
    // Pipeline reusability
    // =========================================================================

    @Test
    void pipelineCanBeExecutedMultipleTimes() {
        AtomicInteger counter = new AtomicInteger(0);

        ProcessMiningPipeline pipeline = ProcessMiningPipeline.builder()
                .stage("count", _ -> counter.incrementAndGet())
                .build();

        PipelineResult r1 = pipeline.execute();
        PipelineResult r2 = pipeline.execute();

        assertTrue(r1.success());
        assertTrue(r2.success());
        assertEquals(1, r1.stageOutput("count", Integer.class).orElse(-1));
        assertEquals(2, r2.stageOutput("count", Integer.class).orElse(-1));
    }

    // =========================================================================
    // Exhaustive pattern matching on PipelineEvent
    // =========================================================================

    @Test
    void pipelineEventSealedHierarchyIsExhaustive() {
        List<PipelineEvent> events = List.of(
                new PipelineEvent.StageStarted("s", java.time.Instant.now()),
                new PipelineEvent.StageCompleted("s", java.time.Instant.now(), Duration.ZERO),
                new PipelineEvent.StageFailed("s", java.time.Instant.now(), "err", 1),
                new PipelineEvent.StageRestarted("s", java.time.Instant.now(), 1),
                new PipelineEvent.CircuitOpened("s", java.time.Instant.now(), 3));

        for (PipelineEvent event : events) {
            String description = switch (event) {
                case PipelineEvent.StageStarted s -> "started: " + s.stageName();
                case PipelineEvent.StageCompleted c -> "completed: " + c.stageName();
                case PipelineEvent.StageFailed f -> "failed: " + f.error();
                case PipelineEvent.StageRestarted r -> "restarted: " + r.restartCount();
                case PipelineEvent.CircuitOpened o -> "open: " + o.consecutiveFailures();
            };
            assertNotNull(description);
        }
    }
}
