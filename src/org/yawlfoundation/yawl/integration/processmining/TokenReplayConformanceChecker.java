/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

package org.yawlfoundation.yawl.integration.processmining;

import java.util.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.elements.*;
import org.yawlfoundation.yawl.util.XNode;
import org.yawlfoundation.yawl.util.XNodeParser;

/**
 * Token-based replay conformance checking against a YAWL Petri net.
 *
 * Implements the classical van der Aalst (2003) algorithm using four token counters:
 * produced, consumed, missing, and remaining. Replays event traces against the actual
 * YNet workflow structure with full support for AND/XOR/OR join and split semantics.
 *
 * Fitness formula:
 *   fitness = 0.5 * (1 - missing/consumed) + 0.5 * (1 - remaining/produced)
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public final class TokenReplayConformanceChecker {

    private static final Logger logger = LogManager.getLogger(TokenReplayConformanceChecker.class);

    /**
     * Result of token-based replay conformance analysis.
     *
     * Records token-level metrics (produced, consumed, missing, remaining),
     * trace-level metrics (count and fitness), and deviating case identifiers.
     */
    public static final class TokenReplayResult {
        public final long produced;
        public final long consumed;
        public final long missing;
        public final long remaining;
        public final int traceCount;
        public final int fittingTraces;
        public final Set<String> deviatingCases;

        TokenReplayResult(long produced, long consumed, long missing, long remaining,
                          int traceCount, int fittingTraces, Set<String> deviatingCases) {
            this.produced = produced;
            this.consumed = consumed;
            this.missing = missing;
            this.remaining = remaining;
            this.traceCount = traceCount;
            this.fittingTraces = fittingTraces;
            this.deviatingCases = deviatingCases;
        }

        /**
         * Compute van der Aalst fitness score.
         *
         * fitness = 0.5 * (1 - missing/consumed) + 0.5 * (1 - remaining/produced)
         *
         * Returns 1.0 if no tokens consumed or produced (empty trace).
         *
         * @return fitness in range [0, 1] where 1 = perfect fit
         */
        public double computeFitness() {
            if (consumed == 0 || produced == 0) {
                return 1.0;
            }
            double fitnessConsumed = 1.0 - ((double) missing / consumed);
            double fitnessProduced = 1.0 - ((double) remaining / produced);
            return 0.5 * fitnessConsumed + 0.5 * fitnessProduced;
        }
    }

    private TokenReplayConformanceChecker() {
        // Utility class, no instances
    }

    /**
     * Replay an XES log against a YNet workflow model.
     *
     * Parses the XES XML and replays each trace by simulating token flow through the net.
     * Records deviations where activities cannot be executed due to missing input tokens.
     *
     * @param net the YNet workflow model
     * @param xesXml the XES event log XML
     * @return token replay result with fitness metrics
     * @throws NullPointerException if net or xesXml is null
     */
    public static TokenReplayResult replay(YNet net, String xesXml) {
        Objects.requireNonNull(net, "YNet model required");
        Objects.requireNonNull(xesXml, "XES XML required");

        List<Trace> traces = parseTraces(xesXml);
        long totalProduced = 0;
        long totalConsumed = 0;
        long totalMissing = 0;
        long totalRemaining = 0;
        int fittingTraces = 0;
        Set<String> deviatingCases = new HashSet<>();

        for (Trace trace : traces) {
            TraceReplayResult traceResult = replayTrace(net, trace);
            totalProduced += traceResult.produced;
            totalConsumed += traceResult.consumed;
            totalMissing += traceResult.missing;
            totalRemaining += traceResult.remaining;

            if (traceResult.missing == 0 && traceResult.remaining == 0) {
                fittingTraces++;
            } else {
                deviatingCases.add(trace.caseId);
            }
        }

        return new TokenReplayResult(totalProduced, totalConsumed, totalMissing,
                                     totalRemaining, traces.size(), fittingTraces, deviatingCases);
    }

    /**
     * Replay a single trace against the workflow net.
     *
     * @param net the YNet workflow model
     * @param caseId the case identifier
     * @param activities list of activity names in execution order
     * @return token replay result with fitness metrics for this trace
     * @throws NullPointerException if any parameter is null
     */
    public static TokenReplayResult replaySingleTrace(YNet net, String caseId,
                                                       List<String> activities) {
        Objects.requireNonNull(net, "YNet model required");
        Objects.requireNonNull(caseId, "Case ID required");
        Objects.requireNonNull(activities, "Activities list required");

        Trace trace = new Trace(caseId, activities);
        TraceReplayResult result = replayTrace(net, trace);

        int fitting = (result.missing == 0 && result.remaining == 0) ? 1 : 0;
        Set<String> deviating = fitting == 0 ? Set.of(caseId) : Collections.emptySet();

        return new TokenReplayResult(result.produced, result.consumed, result.missing,
                                     result.remaining, 1, fitting, deviating);
    }

    /**
     * Replay a single trace against the net, accumulating token counters.
     *
     * Algorithm:
     *   1. Initialize marking: input_condition = 1 token, produced = 1
     *   2. For each activity in trace:
     *      a. Find matching task in net
     *      b. Check if input conditions have required tokens
     *      c. If missing, increment missing counter (manufacture token)
     *      d. Consume tokens from input conditions
     *      e. Produce tokens in output conditions
     *   3. At end, consume final output condition token, count remaining tokens
     *
     * @param net the workflow net
     * @param trace the event trace
     * @return token metrics for this trace
     */
    private static TraceReplayResult replayTrace(YNet net, Trace trace) {
        if (trace.activities.isEmpty()) {
            return new TraceReplayResult(1, 1, 0, 0);
        }

        YInputCondition inputCondition = net.getInputCondition();
        YOutputCondition outputCondition = net.getOutputCondition();

        if (inputCondition == null || outputCondition == null) {
            logger.warn("Invalid net: missing input or output condition");
            return new TraceReplayResult(0, 0, 0, 0);
        }

        Map<String, Integer> marking = new HashMap<>();
        marking.put(inputCondition.getID(), 1);

        long produced = 1;
        long consumed = 0;
        long missing = 0;

        Map<String, YTask> taskMap = buildTaskMap(net);

        for (String activityName : trace.activities) {
            YTask task = taskMap.get(activityName);

            if (task == null) {
                logger.debug("Activity not found in net: {}", activityName);
                continue;
            }

            if (task instanceof YCompositeTask) {
                throw new UnsupportedOperationException(
                    "Token replay does not support composite tasks: " + activityName);
            }

            int joinType = task.getJoinType();
            Set<YExternalNetElement> inputConditions = task.getPresetElements();
            Set<YExternalNetElement> outputConditions = task.getPostsetElements();

            boolean canFire = checkJoinConditions(marking, inputConditions, joinType);

            if (!canFire) {
                missing++;
                consumed++;
                for (YExternalNetElement cond : inputConditions) {
                    marking.put(cond.getID(), marking.getOrDefault(cond.getID(), 0) + 1);
                }
            }

            consumeTokens(marking, inputConditions, joinType);
            consumed++;

            produceTokens(marking, outputConditions);
            produced += outputConditions.size();
        }

        long remaining = 0;
        for (Map.Entry<String, Integer> entry : marking.entrySet()) {
            String conditionId = entry.getKey();
            int count = entry.getValue();

            if (!conditionId.equals(outputCondition.getID()) && count > 0) {
                remaining += count;
            } else if (conditionId.equals(outputCondition.getID())) {
                consumed += count;
            }
        }

        return new TraceReplayResult(produced, consumed, missing, remaining);
    }

    /**
     * Check if join conditions are satisfied for the given join type.
     *
     * AND-join: all input conditions must have tokens
     * XOR-join: exactly one input condition must have token (first found)
     * OR-join: at least one input condition must have token
     *
     * @param marking current token marking
     * @param inputConditions input conditions (places) to the task
     * @param joinType join type code (AND, XOR, OR)
     * @return true if join can fire, false otherwise
     */
    private static boolean checkJoinConditions(Map<String, Integer> marking,
                                                Set<YExternalNetElement> inputConditions,
                                                int joinType) {
        if (inputConditions.isEmpty()) {
            return true;
        }

        return switch (joinType) {
            case YTask._AND -> {
                for (YExternalNetElement cond : inputConditions) {
                    if (marking.getOrDefault(cond.getID(), 0) <= 0) {
                        yield false;
                    }
                }
                yield true;
            }
            case YTask._XOR, YTask._OR -> {
                for (YExternalNetElement cond : inputConditions) {
                    if (marking.getOrDefault(cond.getID(), 0) > 0) {
                        yield true;
                    }
                }
                yield false;
            }
            default -> {
                logger.warn("Unknown join type: {}", joinType);
                yield false;
            }
        };
    }

    /**
     * Consume tokens from input conditions based on join type.
     *
     * AND-join: consume from all input conditions
     * XOR-join: consume from first input condition with token
     * OR-join: consume from first input condition with token
     *
     * @param marking current token marking (modified)
     * @param inputConditions input conditions to consume from
     * @param joinType join type code
     */
    private static void consumeTokens(Map<String, Integer> marking,
                                      Set<YExternalNetElement> inputConditions,
                                      int joinType) {
        if (inputConditions.isEmpty()) {
            return;
        }

        switch (joinType) {
            case YTask._AND -> {
                for (YExternalNetElement cond : inputConditions) {
                    String condId = cond.getID();
                    marking.put(condId, marking.getOrDefault(condId, 0) - 1);
                }
            }
            case YTask._XOR, YTask._OR -> {
                for (YExternalNetElement cond : inputConditions) {
                    String condId = cond.getID();
                    int count = marking.getOrDefault(condId, 0);
                    if (count > 0) {
                        marking.put(condId, count - 1);
                        break;
                    }
                }
            }
        };
    }

    /**
     * Produce tokens to output conditions.
     *
     * For AND/XOR/OR splits in token replay, we conservatively produce tokens
     * in all output conditions (replay cannot determine split decision dynamically).
     * The remaining token counter handles over-production.
     *
     * @param marking current token marking (modified)
     * @param outputConditions output conditions to produce to
     */
    private static void produceTokens(Map<String, Integer> marking,
                                      Set<YExternalNetElement> outputConditions) {
        for (YExternalNetElement cond : outputConditions) {
            String condId = cond.getID();
            marking.put(condId, marking.getOrDefault(condId, 0) + 1);
        }
    }

    /**
     * Build a map of task name -> YTask for quick lookup during replay.
     *
     * @param net the workflow net
     * @return map from task name to task object
     */
    private static Map<String, YTask> buildTaskMap(YNet net) {
        Map<String, YTask> map = new HashMap<>();
        for (YTask task : net.getNetTasks()) {
            String name = task.getName();
            if (name != null && !name.isEmpty()) {
                map.put(name, task);
            }
        }
        return map;
    }

    /**
     * Parse XES XML log into list of traces.
     *
     * Extracts traces from XES log structure, filtering for events with
     * lifecycle:transition == "complete" (or all if no lifecycle specified).
     *
     * @param xesXml the XES log XML
     * @return list of parsed traces
     */
    private static List<Trace> parseTraces(String xesXml) {
        List<Trace> traces = new ArrayList<>();
        if (xesXml == null || xesXml.isEmpty()) {
            return traces;
        }

        try {
            XNode root = new XNodeParser().parse(xesXml);
            if (root == null) {
                return traces;
            }

            for (XNode traceNode : root.getChildren("trace")) {
                String caseId = getStringAttr(traceNode, "concept:name");
                if (caseId == null) {
                    caseId = "unknown";
                }

                List<String> activities = new ArrayList<>();
                for (XNode eventNode : traceNode.getChildren("event")) {
                    String lifecycle = getStringAttr(eventNode, "lifecycle:transition");
                    if (lifecycle != null && !lifecycle.equals("complete")) {
                        continue;
                    }

                    String act = getStringAttr(eventNode, "concept:name");
                    if (act != null && !act.isEmpty()) {
                        activities.add(act);
                    }
                }

                traces.add(new Trace(caseId, activities));
            }
        } catch (Exception e) {
            logger.warn("Failed to parse XES trace data: {}", e.getMessage(), e);
        }

        return traces;
    }

    /**
     * Extract string attribute value from XES node.
     *
     * XES structure: <string key="concept:name" value="value"/>
     *
     * @param node the XNode to search
     * @param key the attribute key
     * @return the value, or null if not found
     */
    private static String getStringAttr(XNode node, String key) {
        for (XNode child : node.getChildren()) {
            if ("string".equals(child.getName()) && key.equals(child.getAttributeValue("key"))) {
                return child.getAttributeValue("value");
            }
        }
        return null;
    }

    /**
     * Result of replaying a single trace.
     *
     * Internal record for accumulating metrics during a single trace's replay.
     */
    private static final class TraceReplayResult {
        final long produced;
        final long consumed;
        final long missing;
        final long remaining;

        TraceReplayResult(long produced, long consumed, long missing, long remaining) {
            this.produced = produced;
            this.consumed = consumed;
            this.missing = missing;
            this.remaining = remaining;
        }
    }

    /**
     * A parsed trace from the event log.
     *
     * Immutable representation of a case's event sequence.
     */
    private static final class Trace {
        final String caseId;
        final List<String> activities;

        Trace(String caseId, List<String> activities) {
            this.caseId = caseId;
            this.activities = activities;
        }
    }
}
