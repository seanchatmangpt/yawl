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

package org.yawlfoundation.yawl.dspy.adaptation;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable snapshot of workflow state for runtime adaptation decision-making.
 *
 * <p>WorkflowAdaptationContext captures the essential metrics and state needed
 * by the {@link RuntimeAdaptationAgent} to decide what adaptations to suggest
 * for a running workflow. It includes task states, performance metrics,
 * bottlenecks, and resource availability.</p>
 *
 * <h2>Architecture</h2>
 * <p>This context is built from multiple sources:</p>
 * <ul>
 *   <li>Event triggers (bottleneck detection, exceptions)</li>
 *   <li>YNetRunner execution state (enabled tasks, pending work items)</li>
 *   <li>BottleneckDetector metrics (queue depth, latency)</li>
 *   <li>Resource allocation state (available agents)</li>
 * </ul>
 *
 * <h2>Usage in ReAct Loop</h2>
 * <pre>{@code
 * WorkflowAdaptationContext context = WorkflowAdaptationContext.builder()
 *     .caseId("case-123")
 *     .specId("loan-approval")
 *     .bottleneckScore(0.85)
 *     .enabledTasks(List.of("ApproveApplication", "RequestDocumentation"))
 *     .queueDepth(15)
 *     .avgTaskLatencyMs(2500)
 *     .availableAgents(3)
 *     .build();
 *
 * AdaptationAction action = dspyBridge.executeReActAgent(context);
 * }</pre>
 *
 * @author YAWL Foundation
 * @since 6.0
 */
public final class WorkflowAdaptationContext {
    private final String caseId;
    private final String specId;
    private final double bottleneckScore;
    private final List<String> enabledTasks;
    private final List<String> busyTasks;
    private final long queueDepth;
    private final long avgTaskLatencyMs;
    private final int availableAgents;
    private final Map<String, Object> eventPayload;
    private final @Nullable String eventType;
    private final Instant timestamp;

    private WorkflowAdaptationContext(Builder builder) {
        this.caseId = Objects.requireNonNull(builder.caseId, "caseId must not be null");
        this.specId = Objects.requireNonNull(builder.specId, "specId must not be null");
        this.bottleneckScore = builder.bottleneckScore;
        this.enabledTasks = Collections.unmodifiableList(builder.enabledTasks);
        this.busyTasks = Collections.unmodifiableList(builder.busyTasks);
        this.queueDepth = builder.queueDepth;
        this.avgTaskLatencyMs = builder.avgTaskLatencyMs;
        this.availableAgents = builder.availableAgents;
        this.eventPayload = Collections.unmodifiableMap(builder.eventPayload);
        this.eventType = builder.eventType;
        this.timestamp = Objects.requireNonNull(builder.timestamp, "timestamp must not be null");
    }

    /**
     * Case identifier associated with this workflow adaptation event.
     *
     * @return case ID
     */
    public String caseId() {
        return caseId;
    }

    /**
     * Specification identifier of the workflow being adapted.
     *
     * @return spec ID
     */
    public String specId() {
        return specId;
    }

    /**
     * Bottleneck severity score (0.0 = no bottleneck, 1.0 = critical).
     *
     * <p>Computed by BottleneckDetector as the fraction of total time
     * consumed by the slowest task.</p>
     *
     * @return score between 0.0 and 1.0
     */
    public double bottleneckScore() {
        return bottleneckScore;
    }

    /**
     * Tasks currently enabled and ready to execute.
     *
     * @return immutable list of task names
     */
    public List<String> enabledTasks() {
        return enabledTasks;
    }

    /**
     * Tasks currently executing (in progress).
     *
     * @return immutable list of task names
     */
    public List<String> busyTasks() {
        return busyTasks;
    }

    /**
     * Number of work items waiting to be processed.
     *
     * @return queue depth
     */
    public long queueDepth() {
        return queueDepth;
    }

    /**
     * Average task latency in milliseconds.
     *
     * @return latency in ms
     */
    public long avgTaskLatencyMs() {
        return avgTaskLatencyMs;
    }

    /**
     * Number of available agents that can be allocated to tasks.
     *
     * @return agent count
     */
    public int availableAgents() {
        return availableAgents;
    }

    /**
     * Payload from the triggering event (bottleneck, exception, etc.).
     *
     * <p>May include additional context like risk scores, user decisions,
     * or system metrics that led to the adaptation trigger.</p>
     *
     * @return immutable event payload map
     */
    public Map<String, Object> eventPayload() {
        return eventPayload;
    }

    /**
     * Type of event that triggered adaptation (nullable).
     *
     * @return event type or null if not from an event trigger
     */
    public @Nullable String eventType() {
        return eventType;
    }

    /**
     * When this context was created.
     *
     * @return timestamp
     */
    public Instant timestamp() {
        return timestamp;
    }

    /**
     * Creates a builder for constructing WorkflowAdaptationContext instances.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for WorkflowAdaptationContext.
     */
    public static final class Builder {
        private String caseId;
        private String specId;
        private double bottleneckScore = 0.0;
        private List<String> enabledTasks = Collections.emptyList();
        private List<String> busyTasks = Collections.emptyList();
        private long queueDepth = 0;
        private long avgTaskLatencyMs = 0;
        private int availableAgents = 0;
        private Map<String, Object> eventPayload = Collections.emptyMap();
        private @Nullable String eventType;
        private Instant timestamp = Instant.now();

        /**
         * Sets the case identifier.
         *
         * @param caseId case ID (must not be null)
         * @return this builder
         */
        public Builder caseId(String caseId) {
            this.caseId = Objects.requireNonNull(caseId, "caseId must not be null");
            return this;
        }

        /**
         * Sets the specification identifier.
         *
         * @param specId spec ID (must not be null)
         * @return this builder
         */
        public Builder specId(String specId) {
            this.specId = Objects.requireNonNull(specId, "specId must not be null");
            return this;
        }

        /**
         * Sets the bottleneck score.
         *
         * @param bottleneckScore score (0.0 to 1.0)
         * @return this builder
         */
        public Builder bottleneckScore(double bottleneckScore) {
            this.bottleneckScore = Math.max(0.0, Math.min(1.0, bottleneckScore));
            return this;
        }

        /**
         * Sets the list of enabled tasks.
         *
         * @param enabledTasks task names (must not be null)
         * @return this builder
         */
        public Builder enabledTasks(List<String> enabledTasks) {
            this.enabledTasks = Objects.requireNonNull(enabledTasks, "enabledTasks must not be null");
            return this;
        }

        /**
         * Sets the list of busy tasks.
         *
         * @param busyTasks task names (must not be null)
         * @return this builder
         */
        public Builder busyTasks(List<String> busyTasks) {
            this.busyTasks = Objects.requireNonNull(busyTasks, "busyTasks must not be null");
            return this;
        }

        /**
         * Sets the queue depth.
         *
         * @param queueDepth number of pending work items
         * @return this builder
         */
        public Builder queueDepth(long queueDepth) {
            this.queueDepth = Math.max(0, queueDepth);
            return this;
        }

        /**
         * Sets the average task latency.
         *
         * @param avgTaskLatencyMs latency in milliseconds
         * @return this builder
         */
        public Builder avgTaskLatencyMs(long avgTaskLatencyMs) {
            this.avgTaskLatencyMs = Math.max(0, avgTaskLatencyMs);
            return this;
        }

        /**
         * Sets the number of available agents.
         *
         * @param availableAgents agent count
         * @return this builder
         */
        public Builder availableAgents(int availableAgents) {
            this.availableAgents = Math.max(0, availableAgents);
            return this;
        }

        /**
         * Sets the event payload.
         *
         * @param eventPayload payload map (must not be null)
         * @return this builder
         */
        public Builder eventPayload(Map<String, Object> eventPayload) {
            this.eventPayload = Objects.requireNonNull(eventPayload, "eventPayload must not be null");
            return this;
        }

        /**
         * Sets the event type.
         *
         * @param eventType event type (nullable)
         * @return this builder
         */
        public Builder eventType(@Nullable String eventType) {
            this.eventType = eventType;
            return this;
        }

        /**
         * Sets the timestamp.
         *
         * @param timestamp context creation time (must not be null)
         * @return this builder
         */
        public Builder timestamp(Instant timestamp) {
            this.timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
            return this;
        }

        /**
         * Builds the WorkflowAdaptationContext.
         *
         * @return a new immutable context
         * @throws NullPointerException if required fields are not set
         */
        public WorkflowAdaptationContext build() {
            return new WorkflowAdaptationContext(this);
        }
    }

    @Override
    public String toString() {
        return String.format(
            "WorkflowAdaptationContext{caseId=%s, specId=%s, bottleneckScore=%.2f, " +
            "enabled=%d, busy=%d, queueDepth=%d, avgLatency=%dms, agents=%d}",
            caseId, specId, bottleneckScore,
            enabledTasks.size(), busyTasks.size(), queueDepth, avgTaskLatencyMs, availableAgents
        );
    }
}
