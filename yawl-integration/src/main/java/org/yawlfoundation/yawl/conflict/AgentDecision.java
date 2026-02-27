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

import java.util.Map;

/**
 * Data class representing a decision from an autonomous agent.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class AgentDecision {
    private final String agentId;
    private final String decision;
    private final Map<String, Object> metadata;
    private final double confidence;
    private final String rationale;

    public AgentDecision(String agentId, String decision, Map<String, Object> metadata,
                        double confidence, String rationale) {
        this.agentId = agentId;
        this.decision = decision;
        this.metadata = metadata;
        this.confidence = confidence;
        this.rationale = rationale;
    }

    // Getters
    public String getAgentId() { return agentId; }
    public String getDecision() { return decision; }
    public Map<String, Object> getMetadata() { return metadata; }
    public double getConfidence() { return confidence; }
    public String getRationale() { return rationale; }
}