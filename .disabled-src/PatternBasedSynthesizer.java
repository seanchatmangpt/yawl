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

package org.yawlfoundation.yawl.integration.synthesis;

import org.yawlfoundation.yawl.util.XNode;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Offline pattern-based workflow synthesizer that generates valid YAWL XML
 * from the 5 foundational Workflow Control-flow Patterns (WCPs).
 *
 * <p>Unlike the SPARQL-based {@link IntentSynthesizer} or the Z.AI-powered
 * {@code ConversationalWorkflowFactory}, this synthesizer requires no external
 * services. It applies structural patterns directly to task lists, producing
 * valid YAWL specificationSet XML.</p>
 *
 * <p>Supported patterns (sealed hierarchy):</p>
 * <ul>
 *   <li>{@link PatternSpec.Sequential} — WCP-1: tasks execute in strict order</li>
 *   <li>{@link PatternSpec.Parallel} — WCP-2: all branches execute concurrently</li>
 *   <li>{@link PatternSpec.Exclusive} — WCP-4: exactly one branch chosen at runtime</li>
 *   <li>{@link PatternSpec.Loop} — WCP-21: structured loop with exit condition</li>
 *   <li>{@link PatternSpec.MultiInstance} — WCP-12: parallel execution of N instances</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class PatternBasedSynthesizer {

    private static final String YAWL_NS =
        "http://www.yawlfoundation.org/yawlschema";
    private static final String XSI_NS =
        "http://www.w3.org/2001/XMLSchema-instance";
    private static final String SCHEMA_LOC =
        "http://www.yawlfoundation.org/yawlschema "
        + "http://www.yawlfoundation.org/yawlschema/YAWL_Schema4.0.xsd";

    /**
     * Sealed hierarchy of supported workflow patterns.
     * Each variant carries the task names and any pattern-specific parameters.
     */
    public sealed interface PatternSpec {

        /** The task names involved in this pattern. */
        List<String> tasks();

        /** WCP identifier (e.g., "WCP-1"). */
        String wcpId();

        /** WCP-1: Sequence — tasks execute one after another. */
        record Sequential(List<String> tasks) implements PatternSpec {
            public Sequential {
                Objects.requireNonNull(tasks, "tasks must not be null");
                if (tasks.size() < 2) {
                    throw new IllegalArgumentException(
                        "Sequential pattern requires at least 2 tasks");
                }
            }
            @Override public String wcpId() { return "WCP-1"; }
        }

        /** WCP-2: Parallel Split — all tasks execute concurrently after a fork. */
        record Parallel(List<String> tasks) implements PatternSpec {
            public Parallel {
                Objects.requireNonNull(tasks, "tasks must not be null");
                if (tasks.size() < 2) {
                    throw new IllegalArgumentException(
                        "Parallel pattern requires at least 2 tasks");
                }
            }
            @Override public String wcpId() { return "WCP-2"; }
        }

        /** WCP-4: Exclusive Choice — exactly one branch selected at runtime. */
        record Exclusive(List<String> tasks) implements PatternSpec {
            public Exclusive {
                Objects.requireNonNull(tasks, "tasks must not be null");
                if (tasks.size() < 2) {
                    throw new IllegalArgumentException(
                        "Exclusive pattern requires at least 2 tasks");
                }
            }
            @Override public String wcpId() { return "WCP-4"; }
        }

        /** WCP-21: Structured Loop — repeats body tasks until exit condition. */
        record Loop(List<String> tasks) implements PatternSpec {
            public Loop {
                Objects.requireNonNull(tasks, "tasks must not be null");
                if (tasks.isEmpty()) {
                    throw new IllegalArgumentException(
                        "Loop pattern requires at least 1 task");
                }
            }
            @Override public String wcpId() { return "WCP-21"; }
        }

        /** WCP-12: Multiple Instances — parallel execution of N instances of a task. */
        record MultiInstance(List<String> tasks, int minInstances,
                             int maxInstances, int threshold) implements PatternSpec {
            public MultiInstance {
                Objects.requireNonNull(tasks, "tasks must not be null");
                if (tasks.isEmpty()) {
                    throw new IllegalArgumentException(
                        "MultiInstance pattern requires at least 1 task");
                }
                if (minInstances < 1) {
                    throw new IllegalArgumentException(
                        "minInstances must be >= 1");
                }
                if (maxInstances < minInstances) {
                    throw new IllegalArgumentException(
                        "maxInstances must be >= minInstances");
                }
                if (threshold < 1 || threshold > maxInstances) {
                    throw new IllegalArgumentException(
                        "threshold must be between 1 and maxInstances");
                }
            }
            @Override public String wcpId() { return "WCP-12"; }
        }
    }

    /**
     * Synthesize a YAWL specification from a pattern specification.
     *
     * @param spec the pattern specification describing the workflow structure
     * @return a {@link SynthesisResult} containing valid YAWL XML
     * @throws NullPointerException if spec is null
     */
    public SynthesisResult synthesize(PatternSpec spec) {
        Objects.requireNonNull(spec, "PatternSpec must not be null");
        Instant start = Instant.now();

        String xml = switch (spec) {
            case PatternSpec.Sequential s -> buildSequential(s);
            case PatternSpec.Parallel p -> buildParallel(p);
            case PatternSpec.Exclusive e -> buildExclusive(e);
            case PatternSpec.Loop l -> buildLoop(l);
            case PatternSpec.MultiInstance m -> buildMultiInstance(m);
        };

        Duration elapsed = Duration.between(start, Instant.now());
        return new SynthesisResult(xml, null, List.of(spec.wcpId()), elapsed, true);
    }

    /**
     * Parse a natural-language description to infer a pattern and task list.
     *
     * <p>Uses keyword matching to select the appropriate pattern:</p>
     * <ul>
     *   <li>"parallel", "concurrent", "simultaneously" → Parallel</li>
     *   <li>"exclusive", "either", "choose", "decision" → Exclusive</li>
     *   <li>"loop", "repeat", "iterate", "cycle" → Loop</li>
     *   <li>"multiple instance", "multi-instance", "batch" → MultiInstance</li>
     *   <li>Default → Sequential</li>
     * </ul>
     *
     * @param description natural-language description of the workflow
     * @param tasks explicit task names (if empty, extracted from description)
     * @return a PatternSpec inferred from the description
     * @throws IllegalArgumentException if no tasks can be determined
     */
    public PatternSpec parseDescription(String description, List<String> tasks) {
        Objects.requireNonNull(description, "description must not be null");
        String lower = description.toLowerCase(Locale.ROOT);

        List<String> resolvedTasks = (tasks != null && !tasks.isEmpty())
            ? tasks
            : extractTasksFromDescription(description);

        if (resolvedTasks.isEmpty()) {
            throw new IllegalArgumentException(
                "Cannot determine tasks from description: " + description);
        }

        if (containsAny(lower, "multiple instance", "multi-instance", "batch")) {
            return new PatternSpec.MultiInstance(resolvedTasks, 1,
                Math.max(3, resolvedTasks.size()), 1);
        }
        if (containsAny(lower, "parallel", "concurrent", "simultaneously", "fork")) {
            return new PatternSpec.Parallel(resolvedTasks);
        }
        if (containsAny(lower, "exclusive", "either", "choose", "decision",
                "one of", "branch")) {
            return new PatternSpec.Exclusive(resolvedTasks);
        }
        if (containsAny(lower, "loop", "repeat", "iterate", "cycle", "until")) {
            return new PatternSpec.Loop(resolvedTasks);
        }

        // Default: sequential
        if (resolvedTasks.size() < 2) {
            // Pad to minimum 2 tasks for sequential
            List<String> padded = new ArrayList<>(resolvedTasks);
            padded.add("Complete");
            return new PatternSpec.Sequential(padded);
        }
        return new PatternSpec.Sequential(resolvedTasks);
    }

    // =========================================================================
    // WCP-1: Sequential
    // =========================================================================

    private String buildSequential(PatternSpec.Sequential spec) {
        String procName = "SequentialProcess";
        XNode specSet = createSpecSetRoot();
        XNode specNode = addSpecification(specSet, procName,
            "Sequential workflow (WCP-1): tasks execute in strict order");
        XNode pce = addDecomposition(specNode, procName);

        List<String> tasks = spec.tasks();

        // InputCondition → first task
        XNode inputCond = pce.addChild("inputCondition");
        inputCond.addAttribute("id", "InputCondition");
        addFlowsInto(inputCond, sanitizeId(tasks.getFirst()));

        // Chain tasks sequentially
        for (int i = 0; i < tasks.size(); i++) {
            String taskId = sanitizeId(tasks.get(i));
            XNode task = pce.addChild("task");
            task.addAttribute("id", taskId);
            task.addChild("name", tasks.get(i));

            if (i < tasks.size() - 1) {
                addFlowsInto(task, sanitizeId(tasks.get(i + 1)));
            } else {
                addFlowsInto(task, "OutputCondition");
            }

            task.addChild("join").addAttribute("code", "xor");
            task.addChild("split").addAttribute("code", "and");
            addResourcing(task);
            addDecomposesTo(task, taskId);
        }

        // OutputCondition
        XNode outputCond = pce.addChild("outputCondition");
        outputCond.addAttribute("id", "OutputCondition");

        return xmlHeader() + specSet.toString();
    }

    // =========================================================================
    // WCP-2: Parallel Split + Synchronization
    // =========================================================================

    private String buildParallel(PatternSpec.Parallel spec) {
        String procName = "ParallelProcess";
        XNode specSet = createSpecSetRoot();
        XNode specNode = addSpecification(specSet, procName,
            "Parallel workflow (WCP-2): all branches execute concurrently");
        XNode pce = addDecomposition(specNode, procName);

        List<String> tasks = spec.tasks();

        // InputCondition → fork task
        XNode inputCond = pce.addChild("inputCondition");
        inputCond.addAttribute("id", "InputCondition");
        addFlowsInto(inputCond, "Fork");

        // Fork task: AND-split to all parallel branches
        XNode fork = pce.addChild("task");
        fork.addAttribute("id", "Fork");
        fork.addChild("name", "Fork");
        for (String t : tasks) {
            addFlowsInto(fork, sanitizeId(t));
        }
        fork.addChild("join").addAttribute("code", "xor");
        fork.addChild("split").addAttribute("code", "and");
        addResourcing(fork);
        addDecomposesTo(fork, "Fork");

        // Parallel branch tasks → join task
        for (String t : tasks) {
            String taskId = sanitizeId(t);
            XNode task = pce.addChild("task");
            task.addAttribute("id", taskId);
            task.addChild("name", t);
            addFlowsInto(task, "Join");
            task.addChild("join").addAttribute("code", "xor");
            task.addChild("split").addAttribute("code", "and");
            addResourcing(task);
            addDecomposesTo(task, taskId);
        }

        // Join task: AND-join then → OutputCondition
        XNode join = pce.addChild("task");
        join.addAttribute("id", "Join");
        join.addChild("name", "Join");
        addFlowsInto(join, "OutputCondition");
        join.addChild("join").addAttribute("code", "and");
        join.addChild("split").addAttribute("code", "and");
        addResourcing(join);
        addDecomposesTo(join, "Join");

        XNode outputCond = pce.addChild("outputCondition");
        outputCond.addAttribute("id", "OutputCondition");

        return xmlHeader() + specSet.toString();
    }

    // =========================================================================
    // WCP-4: Exclusive Choice + Simple Merge
    // =========================================================================

    private String buildExclusive(PatternSpec.Exclusive spec) {
        String procName = "ExclusiveProcess";
        XNode specSet = createSpecSetRoot();
        XNode specNode = addSpecification(specSet, procName,
            "Exclusive choice workflow (WCP-4): exactly one branch selected");
        XNode pce = addDecomposition(specNode, procName);

        List<String> tasks = spec.tasks();

        // InputCondition → decision task
        XNode inputCond = pce.addChild("inputCondition");
        inputCond.addAttribute("id", "InputCondition");
        addFlowsInto(inputCond, "Decision");

        // Decision task: XOR-split to branches
        XNode decision = pce.addChild("task");
        decision.addAttribute("id", "Decision");
        decision.addChild("name", "Decision");
        for (String t : tasks) {
            addFlowsInto(decision, sanitizeId(t));
        }
        decision.addChild("join").addAttribute("code", "xor");
        decision.addChild("split").addAttribute("code", "xor");
        addResourcing(decision);
        addDecomposesTo(decision, "Decision");

        // Branch tasks → merge task
        for (String t : tasks) {
            String taskId = sanitizeId(t);
            XNode task = pce.addChild("task");
            task.addAttribute("id", taskId);
            task.addChild("name", t);
            addFlowsInto(task, "Merge");
            task.addChild("join").addAttribute("code", "xor");
            task.addChild("split").addAttribute("code", "and");
            addResourcing(task);
            addDecomposesTo(task, taskId);
        }

        // Merge task: XOR-join → OutputCondition
        XNode merge = pce.addChild("task");
        merge.addAttribute("id", "Merge");
        merge.addChild("name", "Merge");
        addFlowsInto(merge, "OutputCondition");
        merge.addChild("join").addAttribute("code", "xor");
        merge.addChild("split").addAttribute("code", "and");
        addResourcing(merge);
        addDecomposesTo(merge, "Merge");

        XNode outputCond = pce.addChild("outputCondition");
        outputCond.addAttribute("id", "OutputCondition");

        return xmlHeader() + specSet.toString();
    }

    // =========================================================================
    // WCP-21: Structured Loop
    // =========================================================================

    private String buildLoop(PatternSpec.Loop spec) {
        String procName = "LoopProcess";
        XNode specSet = createSpecSetRoot();
        XNode specNode = addSpecification(specSet, procName,
            "Structured loop workflow (WCP-21): repeats until exit condition");
        XNode pce = addDecomposition(specNode, procName);

        List<String> tasks = spec.tasks();

        // InputCondition → first loop body task
        XNode inputCond = pce.addChild("inputCondition");
        inputCond.addAttribute("id", "InputCondition");
        addFlowsInto(inputCond, sanitizeId(tasks.getFirst()));

        // Loop body tasks chain sequentially
        for (int i = 0; i < tasks.size(); i++) {
            String taskId = sanitizeId(tasks.get(i));
            XNode task = pce.addChild("task");
            task.addAttribute("id", taskId);
            task.addChild("name", tasks.get(i));

            if (i < tasks.size() - 1) {
                addFlowsInto(task, sanitizeId(tasks.get(i + 1)));
            } else {
                // Last task → LoopDecision
                addFlowsInto(task, "LoopDecision");
            }

            task.addChild("join").addAttribute("code", "xor");
            task.addChild("split").addAttribute("code", "and");
            addResourcing(task);
            addDecomposesTo(task, taskId);
        }

        // LoopDecision: XOR-split back to start or to OutputCondition
        XNode loopDec = pce.addChild("task");
        loopDec.addAttribute("id", "LoopDecision");
        loopDec.addChild("name", "LoopDecision");
        addFlowsInto(loopDec, sanitizeId(tasks.getFirst()));  // loop back
        addFlowsInto(loopDec, "OutputCondition");              // exit
        loopDec.addChild("join").addAttribute("code", "xor");
        loopDec.addChild("split").addAttribute("code", "xor");
        addResourcing(loopDec);
        addDecomposesTo(loopDec, "LoopDecision");

        // First task has XOR-join to accept loop-back flows
        // (already has xor join from above)

        XNode outputCond = pce.addChild("outputCondition");
        outputCond.addAttribute("id", "OutputCondition");

        return xmlHeader() + specSet.toString();
    }

    // =========================================================================
    // WCP-12: Multiple Instances (without synchronization)
    // =========================================================================

    private String buildMultiInstance(PatternSpec.MultiInstance spec) {
        String procName = "MultiInstanceProcess";
        XNode specSet = createSpecSetRoot();
        XNode specNode = addSpecification(specSet, procName,
            "Multiple-instance workflow (WCP-12): N parallel instances of tasks");
        XNode pce = addDecomposition(specNode, procName);

        List<String> tasks = spec.tasks();

        // InputCondition → first task
        XNode inputCond = pce.addChild("inputCondition");
        inputCond.addAttribute("id", "InputCondition");
        addFlowsInto(inputCond, sanitizeId(tasks.getFirst()));

        // Tasks chain sequentially, each with multi-instance attributes
        for (int i = 0; i < tasks.size(); i++) {
            String taskId = sanitizeId(tasks.get(i));
            XNode task = pce.addChild("task");
            task.addAttribute("id", taskId);
            task.addChild("name", tasks.get(i));

            // Multi-instance configuration
            XNode mi = task.addChild("multiInstanceAttributes");
            mi.addChild("minimum", spec.minInstances());
            mi.addChild("maximum", spec.maxInstances());
            mi.addChild("threshold", spec.threshold());
            mi.addChild("creationMode", "static");

            if (i < tasks.size() - 1) {
                addFlowsInto(task, sanitizeId(tasks.get(i + 1)));
            } else {
                addFlowsInto(task, "OutputCondition");
            }

            task.addChild("join").addAttribute("code", "xor");
            task.addChild("split").addAttribute("code", "and");
            addResourcing(task);
            addDecomposesTo(task, taskId);
        }

        XNode outputCond = pce.addChild("outputCondition");
        outputCond.addAttribute("id", "OutputCondition");

        return xmlHeader() + specSet.toString();
    }

    // =========================================================================
    // Shared XML builders (same structure as XesToYawlSpecGenerator)
    // =========================================================================

    private XNode createSpecSetRoot() {
        XNode specSet = new XNode("specificationSet");
        specSet.addAttribute("xmlns", YAWL_NS);
        specSet.addAttribute("xmlns:xsi", XSI_NS);
        specSet.addAttribute("version", "4.0");
        specSet.addAttribute("xsi:schemaLocation", SCHEMA_LOC);
        return specSet;
    }

    private XNode addSpecification(XNode specSet, String procName, String desc) {
        XNode spec = specSet.addChild("specification");
        spec.addAttribute("uri", sanitizeId(procName));

        XNode meta = spec.addChild("metaData");
        meta.addChild("creator", "YAWL Pattern-Based Synthesizer v6.0");
        meta.addChild("description", desc);
        meta.addChild("coverage", "6.0");
        meta.addChild("version", "1.0");
        meta.addChild("persistent", "false");
        meta.addChild("identifier",
            "UID_pattern_" + sanitizeId(procName) + "_" + System.currentTimeMillis());

        XNode xsSchema = spec.addChild("xs:schema");
        xsSchema.addAttribute("xmlns:xs", "http://www.w3.org/2001/XMLSchema");

        return spec;
    }

    private XNode addDecomposition(XNode spec, String procName) {
        XNode decomposition = spec.addChild("decomposition");
        decomposition.addAttribute("id", sanitizeId(procName));
        decomposition.addAttribute("isRootNet", "true");
        decomposition.addAttribute("xsi:type", "NetFactsType");
        return decomposition.addChild("processControlElements");
    }

    private void addFlowsInto(XNode source, String targetId) {
        XNode flowsInto = source.addChild("flowsInto");
        XNode nextRef = flowsInto.addChild("nextElementRef");
        nextRef.addAttribute("id", targetId);
    }

    private void addResourcing(XNode task) {
        XNode resourcing = task.addChild("resourcing");
        resourcing.addChild("offer").addAttribute("initiator", "user");
        resourcing.addChild("allocate").addAttribute("initiator", "user");
        resourcing.addChild("start").addAttribute("initiator", "user");
    }

    private void addDecomposesTo(XNode task, String taskId) {
        XNode decomposesTo = task.addChild("decomposesTo");
        decomposesTo.addAttribute("id", taskId);
    }

    private String xmlHeader() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";
    }

    // =========================================================================
    // NL parsing helpers
    // =========================================================================

    private List<String> extractTasksFromDescription(String description) {
        List<String> tasks = new ArrayList<>();
        // Split on common delimiters: "then", "and then", commas, semicolons
        String[] parts = description.split(
            "(?i)\\b(?:then|and then|followed by|after that|next)\\b|[,;]");
        for (String part : parts) {
            String trimmed = part.trim()
                .replaceAll("(?i)^(first|finally|lastly|also|and)\\b\\s*", "");
            if (!trimmed.isBlank() && trimmed.length() <= 80) {
                tasks.add(capitalize(trimmed));
            }
        }
        return tasks;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    private String capitalize(String s) {
        if (s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * Converts a name to a valid XML identifier.
     * Same algorithm as {@code XesToYawlSpecGenerator.sanitizeId()}.
     */
    static String sanitizeId(String name) {
        if (name == null || name.isBlank()) {
            return "_task";
        }
        String sanitized = name.replaceAll("[^a-zA-Z0-9_\\-.]", "_");
        if (!sanitized.isEmpty() && !Character.isLetter(sanitized.charAt(0))
                && sanitized.charAt(0) != '_') {
            sanitized = "_" + sanitized;
        }
        return sanitized.isBlank() ? "_task" : sanitized;
    }
}
