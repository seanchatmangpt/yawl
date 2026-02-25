package org.yawlfoundation.yawl.mcp.a2a.gregverse.matching;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Availability slot for a service provider.
 */
public record AvailabilitySlot(
    LocalDateTime start,
    LocalDateTime end,
    List<String> serviceTypes // Types available during this slot
) {

    public boolean contains(LocalDateTime time) {
        return !time.isBefore(start) && !time.isAfter(end);
    }
}