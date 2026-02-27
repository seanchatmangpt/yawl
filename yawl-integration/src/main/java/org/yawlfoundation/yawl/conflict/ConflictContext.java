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

package org.yawlfoundation.yawl.integration.conflict;

import java.util.List;
import java.util.Map;

/**
 * Data class representing a conflict context with all necessary information for resolution.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class ConflictContext {
    private final String conflictId;
    private final String workflowId;
    private final String taskId;
    private final ConflictResolver.Severity severity;
    private final List<AgentDecision> conflictingDecisions;
    private final Map<String, Object> contextData;
    private final long timestamp;

    public ConflictContext(String conflictId, String workflowId, String taskId,
                         ConflictResolver.Severity severity,
                         List<AgentDecision> conflictingDecisions,
                         Map<String, Object> contextData) {
        this.conflictId = conflictId;
        this.workflowId = workflowId;
        this.taskId = taskId;
        this.severity = severity;
        this.conflictingDecisions = conflictingDecisions;
        this.contextData = contextData;
        this.timestamp = System.currentTimeMillis();
    }

    // Getters
    public String getConflictId() { return conflictId; }
    public String getWorkflowId() { return workflowId; }
    public String getTaskId() { return taskId; }
    public ConflictResolver.Severity getSeverity() { return severity; }
    public List<AgentDecision> getConflictingDecisions() { return conflictingDecisions; }
    public Map<String, Object> getContextData() { return contextData; }
    public long getTimestamp() { return timestamp; }
}