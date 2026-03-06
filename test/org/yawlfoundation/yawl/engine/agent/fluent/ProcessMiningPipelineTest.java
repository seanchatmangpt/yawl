package org.yawlfoundation.yawl.engine.agent.fluent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.yawlfoundation.yawl.engine.agent.core.Supervisor;
import org.yawlfoundation.yawl.engine.agent.core.VirtualThreadRuntime;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class ProcessMiningPipelineTest {

    @Test
    @Timeout(10)
    void singleStagePipeline() throws Exception {
        try (var pipeline = ProcessMiningPipeline.builder()
                .stage("upper", input -> ((String) input).toUpperCase())
                .build()) {

            Object result = pipeline.submitAndWait("hello", Duration.ofSeconds(5));
            assertEquals("HELLO", result);
        }
    }

    @Test
    @Timeout(10)
    void threeStageProcessMiningPipeline() throws Exception {
        // Simulates parse -> discover -> conform pipeline
        try (var pipeline = ProcessMiningPipeline.builder()
                .runtime(new VirtualThreadRuntime())
                .supervisor(s -> s
                    .strategy(Supervisor.SupervisorStrategy.ONE_FOR_ONE)
                    .maxRestarts(3))
                .stage("parse", json -> {
                    // Simulate OCEL2 parsing: return event count
                    String input = (String) json;
                    return input.length(); // "event count"
                })
                .stage("discover", eventCount -> {
                    // Simulate DFG discovery: return graph summary
                    int count = (Integer) eventCount;
                    return "DFG[events=" + count + ",edges=" + (count - 1) + "]";
                })
                .stage("conform", dfgSummary -> {
                    // Simulate conformance check: return fitness
                    return "ConformanceReport[fitness=0.95,input=" + dfgSummary + "]";
                })
                .build()) {

            assertEquals(List.of("parse", "discover", "conform"),
                pipeline.stageNames());

            Object result = pipeline.submitAndWait(
                "{\"events\":[{\"id\":\"e1\"},{\"id\":\"e2\"}]}",
                Duration.ofSeconds(5));

            String report = (String) result;
            assertTrue(report.contains("fitness=0.95"),
                "Final stage should produce conformance report");
            assertTrue(report.contains("DFG["),
                "Report should reference DFG discovery output");
        }
    }

    @Test
    @Timeout(10)
    void multipleConcurrentSubmissions() throws Exception {
        try (var pipeline = ProcessMiningPipeline.builder()
                .stage("double", input -> ((Integer) input) * 2)
                .stage("square", input -> {
                    int val = (Integer) input;
                    return val * val;
                })
                .build()) {

            CompletableFuture<Object> f1 = pipeline.submit(3);
            CompletableFuture<Object> f2 = pipeline.submit(5);
            CompletableFuture<Object> f3 = pipeline.submit(7);

            // 3 -> double(6) -> square(36)
            assertEquals(36, f1.get(5, TimeUnit.SECONDS));
            // 5 -> double(10) -> square(100)
            assertEquals(100, f2.get(5, TimeUnit.SECONDS));
            // 7 -> double(14) -> square(196)
            assertEquals(196, f3.get(5, TimeUnit.SECONDS));

            assertEquals(0, pipeline.pendingCount());
        }
    }

    @Test
    @Timeout(10)
    void stageFailurePropagatesException() {
        try (var pipeline = ProcessMiningPipeline.builder()
                .stage("fail", input -> {
                    throw new RuntimeException("Parse error: invalid OCEL2");
                })
                .build()) {

            CompletableFuture<Object> future = pipeline.submit("bad-json");

            ExecutionException ex = assertThrows(ExecutionException.class,
                () -> future.get(5, TimeUnit.SECONDS));
            assertTrue(ex.getCause().getMessage().contains("invalid OCEL2"));
        }
    }

    @Test
    @Timeout(10)
    void middleStageFailurePropagates() {
        try (var pipeline = ProcessMiningPipeline.builder()
                .stage("parse", input -> "parsed:" + input)
                .stage("discover", input -> {
                    throw new RuntimeException("DFG discovery failed");
                })
                .stage("conform", input -> "report:" + input)
                .build()) {

            CompletableFuture<Object> future = pipeline.submit("data");

            ExecutionException ex = assertThrows(ExecutionException.class,
                () -> future.get(5, TimeUnit.SECONDS));
            assertTrue(ex.getCause().getMessage().contains("DFG discovery failed"));
        }
    }

    @Test
    void emptyPipelineThrows() {
        assertThrows(IllegalStateException.class, () ->
            ProcessMiningPipeline.builder().build());
    }

    @Test
    @Timeout(10)
    void lookupStageReturnsActorRef() {
        try (var pipeline = ProcessMiningPipeline.builder()
                .stage("alpha", input -> input)
                .stage("beta", input -> input)
                .build()) {

            assertTrue(pipeline.lookupStage("alpha").isPresent());
            assertTrue(pipeline.lookupStage("beta").isPresent());
            assertTrue(pipeline.lookupStage("gamma").isEmpty());
        }
    }

    @Test
    @Timeout(10)
    void closeCancelsPendingFutures() {
        var pipeline = ProcessMiningPipeline.builder()
            .stage("slow", input -> {
                try { Thread.sleep(60_000); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return input;
            })
            .build();

        CompletableFuture<Object> future = pipeline.submit("data");
        pipeline.close();

        assertTrue(future.isCompletedExceptionally());
    }
}
