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

package org.yawlfoundation.yawl.integration.coordination.events;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Event for conflict detection and resolution tracking in coordination scenarios.
 *
 * <p>This event captures the critical moments when conflicts are detected between
 * agents, work items, or coordination policies. It includes context about the
 * nature of the conflict, affected agents, and resolution strategies.
 *
 * <h2>Conflict Types</h2>
 * <ul>
 *   <li><b>Resource Conflicts</b> - Multiple agents competing for same resources</li>
 *   <li><b>Priority Conflicts</b> - Conflicting priority assignments</li>
 *   <li><b>Timing Conflicts</b> - Schedule overlaps or deadline violations</li>
 *   <li><b>Policy Conflicts</b> - Business rule violations</li>
 *   <li><b>Dependency Conflicts</b> - Circular dependencies or deadlocks</li>
 * </ul>
 *
 * <h2>JSON Schema</h2>
 * <pre>
 * {
 *   "conflictId": "550e8400-e29b-41d4-a716-446655440001",
 *   "conflictType": "RESOURCE",
 *   "severity": "HIGH",
 *   "description": "Multiple agents competing for the same resource",
 *   "affectedAgents": ["agent-1", "agent-2"],
 *   "affectedWorkItems": ["wi-123", "wi-456"],
 *   "conflictRules": ["YAWL-POLICY-001"],
 *   "context": {"resource": "server-1", "requestedAt": "2026-02-17T10:00:00Z"},
 *   "resolutionStrategy": "PRIORITY_BASED",
 *   "resolutionAgent": "coordinator-service",
 *   "timestamp": "2026-02-17T10:00:01Z"
 * }
 * </pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class ConflictEvent {

    public enum ConflictType {
        RESOURCE,      // Resource contention conflicts
        PRIORITY,      // Priority assignment conflicts
        TIMING,        // Schedule/timing conflicts
        POLICY,        // Business rule/policy violations
        DEPENDENCY,    // Circular dependency/deadlock conflicts
        AUTHORITY,     // Authority/responsibility conflicts
        DATA,          // Data consistency conflicts
        COMMUNICATION  // Message routing conflicts
    }

    public enum Severity {
        LOW,      // Minor impact, automatic resolution possible
        MEDIUM,   // Moderate impact, requires attention
        HIGH,     // Significant impact, blocks progress
        CRITICAL  // Critical impact, system-wide consequences
    }

    public enum ResolutionStrategy {
        PRIORITY_BASED,     // Resolve based on priority rules
        FIFO,               // First-in-first-out resolution
        ROUND_ROBIN,        // Round-robin assignment
        CONSENSUS,          // Multi-agent consensus required
        MANUAL,             // Human intervention required
        ESCALATION,         // Escalate to higher authority
        TIMEOUT,            // Timeout-based resolution
        RANDOM              // Random selection (fallback)
    }

    private final String conflictId;
    private final ConflictType conflictType;
    private final Severity severity;
    private final String description;
    private final String[] affectedAgents;
    private final String[] affectedWorkItems;
    private final String[] conflictRules;
    private final Map<String, String> context;
    private final ResolutionStrategy resolutionStrategy;
    private final String resolutionAgent;
    private final Instant timestamp;

    /**
     * Create a conflict event with the required fields.
     *
     * @param conflictId unique identifier for this conflict instance
     * @param conflictType the type of conflict (must not be null)
     * @param severity the severity level (must not be null)
     * @param description human-readable description (must not be blank)
     * @param affectedAgents array of affected agent IDs (may be empty)
     * @param affectedWorkItems array of affected work item IDs (may be empty)
     * @param conflictRules array of violated rule identifiers (may be empty)
     * @param context additional context data (may be empty)
     * @param resolutionStrategy the resolution strategy used (must not be null)
     * @param resolutionAgent the agent that resolved the conflict (must not be blank)
     * @param timestamp when the conflict was detected (must not be null)
     */
    public ConflictEvent(String conflictId, ConflictType conflictType, Severity severity,
                        String description, String[] affectedAgents, String[] affectedWorkItems,
                        String[] conflictRules, Map<String, String> context,
                        ResolutionStrategy resolutionStrategy, String resolutionAgent,
                        Instant timestamp) {
        this.conflictId = Objects.requireNonNull(conflictId, "conflictId");
        this.conflictType = Objects.requireNonNull(conflictType, "conflictType");
        this.severity = Objects.requireNonNull(severity, "severity");
        this.description = Objects.requireNonNull(description, "description");
        this.affectedAgents = affectedAgents != null ? affectedAgents : new String[0];
        this.affectedWorkItems = affectedWorkItems != null ? affectedWorkItems : new String[0];
        this.conflictRules = conflictRules != null ? conflictRules : new String[0];
        this.context = context != null ? Map.copyOf(context) : Map.of();
        this.resolutionStrategy = Objects.requireNonNull(resolutionStrategy, "resolutionStrategy");
        this.resolutionAgent = Objects.requireNonNull(resolutionAgent, "resolutionAgent");
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp");
    }

    /**
     * Generate a new conflict event with a generated UUID.
     */
    public static ConflictEvent detected(ConflictType conflictType, Severity severity,
                                       String description, String[] affectedAgents,
                                       String[] affectedWorkItems, String[] conflictRules,
                                       Map<String, String> context, Instant timestamp) {
        String conflictId = java.util.UUID.randomUUID().toString();
        return new ConflictEvent(conflictId, conflictType, severity, description,
                             affectedAgents, affectedWorkItems, conflictRules, context,
                             null, null, timestamp);
    }

    /**
     * Create a resolved conflict event based on a detected conflict.
     */
    public ConflictEvent resolved(ResolutionStrategy resolutionStrategy, String resolutionAgent,
                                Instant timestamp) {
        return new ConflictEvent(this.conflictId, this.conflictType, this.severity,
                              this.description, this.affectedAgents, this.affectedWorkItems,
                              this.conflictRules, this.context, resolutionStrategy,
                              resolutionAgent, timestamp);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** Unique identifier for this conflict instance. */
    public String getConflictId() { return conflictId; }

    /** Type of conflict detected. */
    public ConflictType getConflictType() { return conflictType; }

    /** Severity level of the conflict. */
    public Severity getSeverity() { return severity; }

    /** Human-readable description of the conflict. */
    public String getDescription() { return description; }

    /** Array of affected agent IDs. */
    public String[] getAffectedAgents() { return affectedAgents.clone(); }

    /** Array of affected work item IDs. */
    public String[] getAffectedWorkItems() { return affectedWorkItems.clone(); }

    /** Array of violated rule identifiers. */
    public String[] getConflictRules() { return conflictRules.clone(); }

    /** Additional context data about the conflict. */
    public Map<String, String> getContext() { return context; }

    /** Resolution strategy used (null if not yet resolved). */
    public ResolutionStrategy getResolutionStrategy() { return resolutionStrategy; }

    /** Agent that resolved the conflict (null if not yet resolved). */
    public String getResolutionAgent() { return resolutionAgent; }

    /** Timestamp when the conflict was detected or resolved. */
    public Instant getTimestamp() { return timestamp; }

    /** Returns true if the conflict has been resolved. */
    public boolean isResolved() {
        return resolutionStrategy != null && resolutionAgent != null;
    }

    /** The case ID associated with this conflict. */
    public String getCaseId() {
        // This should be stored in the context or passed during construction
        // For now, we'll look for it in the context
        return context.get("caseId");
    }

    // -------------------------------------------------------------------------
    // Serialization Support
    // -------------------------------------------------------------------------

    /**
     * Convert this event to a map for JSON serialization.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("conflictId", conflictId);
        map.put("conflictType", conflictType.name());
        map.put("severity", severity.name());
        map.put("description", description);
        map.put("affectedAgents", List.of(affectedAgents));
        map.put("affectedWorkItems", List.of(affectedWorkItems));
        map.put("conflictRules", List.of(conflictRules));
        map.put("context", context);
        map.put("resolutionStrategy", resolutionStrategy != null ? resolutionStrategy.name() : null);
        map.put("resolutionAgent", resolutionAgent);
        map.put("timestamp", timestamp.toString());
        return map;
    }

    /**
     * Create a ConflictEvent from a map (deserialization).
     */
    public static ConflictEvent fromMap(Map<String, Object> map) {
        String conflictId = (String) map.get("conflictId");
        ConflictType conflictType = ConflictType.valueOf((String) map.get("conflictType"));
        Severity severity = Severity.valueOf((String) map.get("severity"));
        String description = (String) map.get("description");

        @SuppressWarnings("unchecked")
        Map<String, String> context = (Map<String, String>) map.get("context");

        ResolutionStrategy resolutionStrategy = null;
        String resolutionAgent = null;
        if (map.get("resolutionAgent") != null) {
            resolutionStrategy = ResolutionStrategy.valueOf((String) map.get("resolutionStrategy"));
            resolutionAgent = (String) map.get("resolutionAgent");
        }

        String timestampStr = (String) map.get("timestamp");
        Instant timestamp = Instant.parse(timestampStr);

        @SuppressWarnings("unchecked")
        String[] affectedAgents = ((List<String>) map.get("affectedAgents")).toArray(new String[0]);
        @SuppressWarnings("unchecked")
        String[] affectedWorkItems = ((List<String>) map.get("affectedWorkItems")).toArray(new String[0]);
        @SuppressWarnings("unchecked")
        String[] conflictRules = ((List<String>) map.get("conflictRules")).toArray(new String[0]);

        return new ConflictEvent(conflictId, conflictType, severity, description,
                              affectedAgents, affectedWorkItems, conflictRules, context,
                              resolutionStrategy, resolutionAgent, timestamp);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConflictEvent)) return false;
        ConflictEvent that = (ConflictEvent) o;
        return Objects.equals(conflictId, that.conflictId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(conflictId);
    }

    @Override
    public String toString() {
        return "ConflictEvent{conflictId='" + conflictId + "', type=" + conflictType +
               ", severity=" + severity + ", resolved=" + isResolved() + "}";
    }
}