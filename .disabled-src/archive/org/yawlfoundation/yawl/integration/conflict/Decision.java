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
 * Data class representing the final resolved decision.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class Decision {
    private final String resolvedValue;
    private final ConflictResolver.Severity severity;
    private final List<String> participatingAgents;
    private final String resolutionStrategy;
    private final Map<String, Object> resolutionMetadata;
    private final long resolutionTimestamp;

    public Decision(String resolvedValue, ConflictResolver.Severity severity,
                   List<String> participatingAgents, String resolutionStrategy,
                   Map<String, Object> resolutionMetadata) {
        this.resolvedValue = resolvedValue;
        this.severity = severity;
        this.participatingAgents = participatingAgents;
        this.resolutionStrategy = resolutionStrategy;
        this.resolutionMetadata = resolutionMetadata;
        this.resolutionTimestamp = System.currentTimeMillis();
    }

    // Getters
    public String getResolvedValue() { return resolvedValue; }
    public ConflictResolver.Severity getSeverity() { return severity; }
    public List<String> getParticipatingAgents() { return participatingAgents; }
    public String getResolutionStrategy() { return resolutionStrategy; }
    public Map<String, Object> getResolutionMetadata() { return resolutionMetadata; }
    public long getResolutionTimestamp() { return resolutionTimestamp; }
}