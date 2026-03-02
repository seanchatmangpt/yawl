/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.integration.a2a.safe;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Immutable request object for SAFe ceremony execution.
 *
 * <p>Contains ceremony type, ART identifier, participants, and ceremony-specific inputs.
 *
 * @param ceremonyType         Type of ceremony (PI_PLANNING, SYSTEM_DEMO, INSPECT_ADAPT,
 *                             PORTFOLIO_SYNC, STRATEGIC_PORTFOLIO_REVIEW)
 * @param artId                Agile Release Train identifier
 * @param sessionId            Unique session identifier
 * @param participantAgentIds  List of agent IDs participating in ceremony
 * @param inputs               Ceremony-specific input data
 * @param requestedAt          Timestamp when ceremony was requested
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public record CeremonyRequest(
    String ceremonyType,
    String artId,
    String sessionId,
    List<String> participantAgentIds,
    Map<String, Object> inputs,
    Instant requestedAt
) {

    /**
     * Compact constructor for validation.
     */
    public CeremonyRequest {
        if (ceremonyType == null || ceremonyType.trim().isEmpty()) {
            throw new IllegalArgumentException("ceremonyType must not be null or empty");
        }
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("sessionId must not be null or empty");
        }
        if (participantAgentIds == null || participantAgentIds.isEmpty()) {
            throw new IllegalArgumentException("participantAgentIds must not be empty");
        }
        if (requestedAt == null) {
            throw new IllegalArgumentException("requestedAt must not be null");
        }
    }
}
