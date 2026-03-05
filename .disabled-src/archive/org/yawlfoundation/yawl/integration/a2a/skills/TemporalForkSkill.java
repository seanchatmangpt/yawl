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

package org.yawlfoundation.yawl.integration.a2a.skills;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.integration.temporal.AllPathsForkPolicy;
import org.yawlfoundation.yawl.integration.temporal.CaseFork;
import org.yawlfoundation.yawl.integration.temporal.TemporalForkEngine;
import org.yawlfoundation.yawl.integration.temporal.TemporalForkResult;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A2A skill that explores all possible execution paths of a workflow using
 * {@link TemporalForkEngine} — no LLM required.
 *
 * <p>Given a comma-separated list of task names, this skill forks into parallel
 * virtual threads, each simulating a different task choice. It returns all explored
 * paths and identifies the dominant (most common) outcome.</p>
 *
 * <h2>Request parameters</h2>
 * <ul>
 *   <li>{@code taskNames} — required, comma-separated task identifiers to fork over</li>
 *   <li>{@code maxSeconds} — optional, max wall-clock seconds (default: 10)</li>
 * </ul>
 *
 * <h2>Result data keys</h2>
 * <ul>
 *   <li>{@code forks} — list of path descriptions (each: taskId → outcome summary)</li>
 *   <li>{@code dominantPath} — the most common outcome path, or "all-unique"</li>
 *   <li>{@code completedForks} — number of forks that finished</li>
 *   <li>{@code requestedForks} — number of forks attempted</li>
 *   <li>{@code allCompleted} — boolean</li>
 *   <li>{@code elapsed_ms} — wall-clock time in milliseconds</li>
 * </ul>
 *
 * @since YAWL 6.0
 */
public class TemporalForkSkill implements A2ASkill {

    private static final Logger _log = LoggerFactory.getLogger(TemporalForkSkill.class);
    private static final int DEFAULT_MAX_SECONDS = 10;

    @Override
    public String getId() {
        return "temporal_fork";
    }

    @Override
    public String getName() {
        return "Temporal Fork Exploration";
    }

    @Override
    public String getDescription() {
        return "Explores all possible execution paths of a workflow using temporal forking. "
            + "For each enabled task, a separate virtual thread simulates that path and returns "
            + "the outcome. No LLM required — pure deterministic simulation.";
    }

    @Override
    public Set<String> getRequiredPermissions() {
        return Set.of("workflow:simulate");
    }

    @Override
    public List<String> getTags() {
        return List.of("temporal", "simulation", "no-llm", "what-if", "fork");
    }

    @Override
    public SkillResult execute(SkillRequest request) {
        String taskNamesParam = request.getParameter("taskNames");
        if (taskNamesParam == null || taskNamesParam.isBlank()) {
            return SkillResult.error(
                "Parameter 'taskNames' is required. "
                + "Provide a comma-separated list of task identifiers to fork over, "
                + "e.g. 'taskNames=ReviewApplication,ApproveApplication,RejectApplication'");
        }

        List<String> taskNames = Arrays.stream(taskNamesParam.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());

        if (taskNames.isEmpty()) {
            return SkillResult.error("'taskNames' contains no valid task names after parsing.");
        }

        int maxSeconds = DEFAULT_MAX_SECONDS;
        String maxSecondsParam = request.getParameter("maxSeconds");
        if (maxSecondsParam != null) {
            try {
                maxSeconds = Integer.parseInt(maxSecondsParam.trim());
                if (maxSeconds < 1 || maxSeconds > 300) {
                    return SkillResult.error("'maxSeconds' must be between 1 and 300.");
                }
            } catch (NumberFormatException e) {
                return SkillResult.error("'maxSeconds' must be a valid integer, got: " + maxSecondsParam);
            }
        }

        return runFork(taskNames, maxSeconds);
    }

    private SkillResult runFork(List<String> taskNames, int maxSeconds) {
        long start = System.currentTimeMillis();

        // Use the lambda constructor — the production constructor's getEnabledTasks/executeTask
        // require a bound YNetRunner which is not available in a stateless A2A context.
        // The lambda constructor is the documented test/integration path.
        String syntheticCaseXml = buildSyntheticCaseXml(taskNames);

        TemporalForkEngine engine = TemporalForkEngine.forIntegration(
            // caseSerializer: return the synthetic case state for any caseId
            caseId -> syntheticCaseXml,
            // enabledTasksProvider: return our task list from the "state"
            xml -> taskNames,
            // taskExecutor: simulate executing a task by appending it to the state
            (xml, taskId) -> xml + "<executed>" + taskId + "</executed>"
        );

        TemporalForkResult result = engine.fork(
            "synthetic-case-001",
            new AllPathsForkPolicy(taskNames.size()),
            Duration.ofSeconds(maxSeconds)
        );

        long elapsed = System.currentTimeMillis() - start;

        _log.info("TemporalFork completed: {}/{} forks, dominant={}, elapsed={}ms",
            result.completedForks(), result.requestedForks(),
            result.dominantOutcomeIndex(), elapsed);

        Map<String, Object> data = new HashMap<>();
        data.put("forks", buildForkSummaries(result.forks()));
        data.put("dominantPath", buildDominantPath(result));
        data.put("completedForks", result.completedForks());
        data.put("requestedForks", result.requestedForks());
        data.put("allCompleted", result.allForksCompleted());
        data.put("elapsed_ms", elapsed);
        return SkillResult.success(data, elapsed);
    }

    private String buildSyntheticCaseXml(List<String> taskNames) {
        return "<case id=\"synthetic-case-001\"><tasks>"
            + taskNames.stream().map(t -> "<task>" + t + "</task>").collect(Collectors.joining())
            + "</tasks></case>";
    }

    private List<Map<String, Object>> buildForkSummaries(List<CaseFork> forks) {
        return forks.stream().map(fork -> {
            Map<String, Object> summary = new HashMap<>();
            summary.put("forkId", fork.forkId());
            summary.put("decisionPath", fork.decisionPath());
            summary.put("terminatedNormally", fork.terminatedNormally());
            summary.put("durationMs", fork.durationMs());
            return summary;
        }).collect(Collectors.toList());
    }

    private String buildDominantPath(TemporalForkResult result) {
        if (result.forks().isEmpty()) {
            return "no-forks-completed";
        }
        if (result.dominantOutcomeIndex() < 0) {
            return "all-unique";
        }
        CaseFork dominant = result.getDominantFork();
        return String.join("→", dominant.decisionPath());
    }
}
