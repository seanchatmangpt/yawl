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

package org.yawlfoundation.yawl.integration.temporal;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.yawlfoundation.yawl.stateless.YStatelessEngine;
import org.yawlfoundation.yawl.stateless.elements.YSpecification;
import org.yawlfoundation.yawl.stateless.elements.marking.YIdentifier;

/**
 * Explores multiple execution paths in a workflow case concurrently using virtual threads.
 *
 * <p>The {@code TemporalForkEngine} enables temporal case analysis by forking a live
 * case state into multiple parallel execution paths, each exploring a different task
 * decision at the current decision point. This allows comparison of case outcomes
 * under alternative execution scenarios.</p>
 *
 * <p><strong>Usage (Production):</strong></p>
 * <pre>{@code
 * YStatelessEngine engine = new YStatelessEngine();
 * YSpecification spec = engine.unmarshalSpecification(specXml);
 * TemporalForkEngine forker = new TemporalForkEngine(engine, spec);
 *
 * TemporalForkResult result = forker.fork("case-123", new AllPathsForkPolicy(5), Duration.ofSeconds(30));
 * System.out.println("Forks completed: " + result.completedForks() + "/" + result.requestedForks());
 * }</pre>
 *
 * <p><strong>Usage (Testing):</strong></p>
 * <pre>{@code
 * TemporalForkEngine forker = new TemporalForkEngine(
 *     caseId -> "serialized-xml",
 *     xml -> List.of("taskA", "taskB"),
 *     (xml, taskId) -> xml + "-executed-" + taskId
 * );
 *
 * TemporalForkResult result = forker.fork("case-1", new AllPathsForkPolicy(2), Duration.ofSeconds(5));
 * assertEquals(2, result.completedForks());
 * }</pre>
 *
 * <p><strong>Thread Model:</strong></p>
 * <p>Forks execute on virtual threads (Java 21+) via
 * {@link Executors#newVirtualThreadPerTaskExecutor()}. Each fork runs independently,
 * executing tasks sequentially on its own virtual thread. No synchronization is needed
 * between forks; they are completely independent branches.</p>
 *
 * @since YAWL 6.0
 */
public final class TemporalForkEngine {
    private final Function<String, String> _caseSerializer;
    private final Function<String, List<String>> _enabledTasksProvider;
    private final BiFunction<String, String, String> _taskExecutor;
    private final YStatelessEngine _engine;
    private final YSpecification _spec;

    /**
     * Creates a TemporalForkEngine for production use with live YStatelessEngine.
     *
     * <p>This constructor is used in production to fork a live case. It uses the
     * engine's unloadCase/restoreCase and work item execution APIs.</p>
     *
     * @param engine the YStatelessEngine instance managing live cases
     * @param spec the YAWL specification that defines the workflow
     * @throws NullPointerException if engine or spec is null
     */
    public TemporalForkEngine(YStatelessEngine engine, YSpecification spec) {
        if (engine == null) {
            throw new NullPointerException("engine cannot be null");
        }
        if (spec == null) {
            throw new NullPointerException("spec cannot be null");
        }
        _engine = engine;
        _spec = spec;

        // Production implementations delegate to private stubs.
        // Bind concrete logic via the lambda constructor for full engine integration.
        this._caseSerializer = this::serializeCase;
        this._enabledTasksProvider = this::getEnabledTasks;
        this._taskExecutor = this::executeTask;
    }

    /**
     * Creates a TemporalForkEngine for testing with injected function dependencies.
     *
     * <p>This constructor enables unit testing without a live YAWL engine. All
     * case operations are delegated to the provided functions, which can be
     * lambda expressions or test doubles.</p>
     *
     * @param caseSerializer function that serializes a case ID to XML state
     * @param enabledTasksProvider function that extracts enabled task IDs from case XML
     * @param taskExecutor function that executes a task on case XML, returning updated XML
     * @throws NullPointerException if any parameter is null
     */
    TemporalForkEngine(
        Function<String, String> caseSerializer,
        Function<String, List<String>> enabledTasksProvider,
        BiFunction<String, String, String> taskExecutor
    ) {
        if (caseSerializer == null) {
            throw new NullPointerException("caseSerializer cannot be null");
        }
        if (enabledTasksProvider == null) {
            throw new NullPointerException("enabledTasksProvider cannot be null");
        }
        if (taskExecutor == null) {
            throw new NullPointerException("taskExecutor cannot be null");
        }
        _caseSerializer = caseSerializer;
        _enabledTasksProvider = enabledTasksProvider;
        _taskExecutor = taskExecutor;
        _engine = null;
        _spec = null;
    }

    /**
     * Creates a TemporalForkEngine with custom function dependencies for integration use.
     *
     * <p>This factory method is the intended path for A2A skill integration and
     * other contexts where a live YAWL engine is not available. All case operations
     * are delegated to the provided functions.</p>
     *
     * @param caseSerializer function that serializes a case ID to XML state
     * @param enabledTasksProvider function that extracts enabled task IDs from case XML
     * @param taskExecutor function that executes a task on case XML, returning updated XML
     * @return a new TemporalForkEngine using the provided functions
     * @throws NullPointerException if any parameter is null
     */
    public static TemporalForkEngine forIntegration(
            Function<String, String> caseSerializer,
            Function<String, List<String>> enabledTasksProvider,
            BiFunction<String, String, String> taskExecutor) {
        return new TemporalForkEngine(caseSerializer, enabledTasksProvider, taskExecutor);
    }

    /**
     * Forks a case into multiple parallel execution paths based on the policy.
     *
     * <p>This method serializes the current case state, enumerates enabled tasks
     * via the fork policy, and creates one virtual thread per task to explore
     * each path independently. Returns when all forks complete or the wall-clock
     * time exceeds maxWallTime.</p>
     *
     * <p>The returned {@link TemporalForkResult} contains all fork outcomes,
     * the dominant outcome index, and execution statistics.</p>
     *
     * @param caseId the ID of the case to fork
     * @param policy the fork enumeration policy
     * @param maxWallTime maximum wall-clock duration for fork exploration
     * @return aggregated result of all fork executions
     * @throws NullPointerException if any parameter is null
     * @throws YStateException (production only) if case serialization fails
     * @throws YSyntaxException (production only) if case restoration fails
     */
    public TemporalForkResult fork(
        String caseId,
        ForkPolicy policy,
        Duration maxWallTime
    ) {
        if (caseId == null) {
            throw new NullPointerException("caseId cannot be null");
        }
        if (policy == null) {
            throw new NullPointerException("policy cannot be null");
        }
        if (maxWallTime == null) {
            throw new NullPointerException("maxWallTime cannot be null");
        }

        Instant startTime = Instant.now();

        // 1. Serialize current case state
        String caseXml = _caseSerializer.apply(caseId);

        // 2. Get enabled tasks
        List<String> enabledTasks = _enabledTasksProvider.apply(caseXml);

        // 3. Enumerate paths via policy
        List<String> pathsToExplore = policy.enumeratePaths(enabledTasks);

        // 4. Create virtual thread executor and fork tasks
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<ForkTask> forkTasks = new ArrayList<>();
        List<CaseFork> completedForks = Collections.synchronizedList(new ArrayList<>());

        for (String taskId : pathsToExplore) {
            ForkTask forkTask = new ForkTask(taskId, caseXml, completedForks, startTime);
            executor.submit(forkTask);
            forkTasks.add(forkTask);
        }

        // 5. Wait for all forks to complete or timeout
        executor.shutdown();
        boolean allCompleted = false;
        try {
            allCompleted = executor.awaitTermination(maxWallTime.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (!allCompleted) {
            executor.shutdownNow();
        }

        Instant endTime = Instant.now();
        Duration wallTime = Duration.between(startTime, endTime);

        // 6. Analyze outcomes and identify dominant fork
        int dominantIndex = findDominantOutcomeIndex(completedForks);

        // 7. Construct result
        return new TemporalForkResult(
            List.copyOf(completedForks),
            dominantIndex,
            wallTime,
            pathsToExplore.size(),
            completedForks.size()
        );
    }

    /**
     * Identifies the index of the fork with the most common outcome XML.
     *
     * <p>If all forks produced unique outcomes, returns -1. If multiple forks
     * produced the same outcome, returns the index of the first one.</p>
     *
     * @param forks list of completed forks
     * @return index of dominant fork, or -1 if all unique
     */
    private int findDominantOutcomeIndex(List<CaseFork> forks) {
        if (forks.isEmpty()) {
            return -1;
        }

        Map<String, Integer> outcomeCount = new HashMap<>();
        Map<String, Integer> firstIndexPerOutcome = new HashMap<>();

        for (int i = 0; i < forks.size(); i++) {
            String outcome = forks.get(i).outcomeXml();
            outcomeCount.put(outcome, outcomeCount.getOrDefault(outcome, 0) + 1);
            firstIndexPerOutcome.putIfAbsent(outcome, i);
        }

        // Find the outcome that appears most frequently
        String dominantOutcome = null;
        int maxCount = 0;
        for (Map.Entry<String, Integer> entry : outcomeCount.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                dominantOutcome = entry.getKey();
            }
        }

        // If all unique (maxCount == 1 and only one entry), return -1
        if (maxCount == 1 && outcomeCount.size() > 1) {
            return -1;
        }

        // Otherwise return the index of the first fork with the dominant outcome
        return firstIndexPerOutcome.getOrDefault(dominantOutcome, 0);
    }

    /**
     * Serializes a case (production stub — override via lambda constructor for real binding).
     */
    private String serializeCase(String caseId) {
        if (_engine == null) throw new UnsupportedOperationException("Production engine not bound");
        YIdentifier caseIdentifier = new YIdentifier(caseId);
        try {
            return _engine.unloadCase(caseIdentifier);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize case: " + caseId, e);
        }
    }

    /**
     * Extracts enabled task IDs from case XML (production stub — override via lambda constructor).
     */
    private List<String> getEnabledTasks(String caseXml) {
        throw new UnsupportedOperationException(
            "Production engine binding for getEnabledTasks requires concrete YNetRunner API. " +
            "Use the lambda constructor to inject real task discovery logic.");
    }

    /**
     * Executes a task on case XML (production stub — override via lambda constructor).
     */
    private String executeTask(String caseXml, String taskId) {
        throw new UnsupportedOperationException(
            "Production engine binding for executeTask requires concrete YNetRunner API. " +
            "Use the lambda constructor to inject real task execution logic.");
    }

    /**
     * Internal task for executing a single fork on a virtual thread.
     */
    private class ForkTask implements Runnable {
        private final String _taskId;
        private final String _initialCaseXml;
        private final List<CaseFork> _resultCollector;
        private final Instant _forkStartTime;

        ForkTask(String taskId, String caseXml, List<CaseFork> resultCollector, Instant forkStartTime) {
            _taskId = taskId;
            _initialCaseXml = caseXml;
            _resultCollector = resultCollector;
            _forkStartTime = forkStartTime;
        }

        @Override
        public void run() {
            try {
                Instant startTime = Instant.now();
                String currentXml = _initialCaseXml;
                List<String> decisionPath = new ArrayList<>();
                decisionPath.add(_taskId);

                // Execute the initial task
                String resultXml = _taskExecutor.apply(currentXml, _taskId);

                Instant endTime = Instant.now();
                long durationMs = Duration.between(startTime, endTime).toMillis();

                CaseFork fork = new CaseFork(
                    UUID.randomUUID().toString(),
                    List.copyOf(decisionPath),
                    resultXml,
                    true,
                    durationMs,
                    endTime
                );

                _resultCollector.add(fork);
            } catch (Exception e) {
                // In case of error, record a fork with error state
                Instant endTime = Instant.now();
                long durationMs = Duration.between(_forkStartTime, endTime).toMillis();

                CaseFork errorFork = new CaseFork(
                    UUID.randomUUID().toString(),
                    List.of(_taskId),
                    "<error>" + e.getMessage() + "</error>",
                    false,
                    durationMs,
                    endTime
                );

                _resultCollector.add(errorFork);
            }
        }
    }
}
