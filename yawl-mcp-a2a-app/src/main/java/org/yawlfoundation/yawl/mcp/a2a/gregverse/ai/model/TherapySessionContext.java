/*
 * Copyright 2026 YAWL Foundation
 */
package org.yawlfoundation.yawl.mcp.a2a.gregverse.ai.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Therapy session context containing session details.
 *
 * @param sessionId unique session identifier
 * @param therapyArea therapy area/specialization
 * @param sessionNumber session number in treatment plan
 * @param durationMinutes session duration in minutes
 * @param focusAreas areas of focus for this session
 * @param sessionTimestamp session timestamp
 */
public record TherapySessionContext(
    String sessionId,
    String therapyArea,
    int sessionNumber,
    int durationMinutes,
    List<String> focusAreas,
    long sessionTimestamp
) {
    public TherapySessionContext {
        Objects.requireNonNull(sessionId, "Session ID cannot be null");
        Objects.requireNonNull(therapyArea, "Therapy area cannot be null");
        if (focusAreas == null) {
            focusAreas = List.of();
        }
        if (sessionTimestamp == 0) {
            sessionTimestamp = Instant.now().toEpochMilli();
        }
    }
}
