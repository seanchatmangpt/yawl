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
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.ggen.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Cancellation region definition - YAWL-specific feature.
 *
 * <p>Cancellation regions define tasks/regions that get cancelled when
 * a specific trigger task completes. This is a YAWL-specific pattern
 * not found in BPMN or Petri nets.
 *
 * @param triggerTask Task that triggers cancellation when it completes
 * @param cancelledTasks Tasks to be cancelled when trigger fires
 * @param cancelledRegions Regions to be cancelled (optional)
 * @param condition Optional guard condition for cancellation
 * @param metadata Additional metadata (optional)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CancellationRegionDef(
    @JsonProperty("trigger_task") String triggerTask,
    @JsonProperty("cancelled_tasks") List<String> cancelledTasks,
    @JsonProperty("cancelled_regions") List<String> cancelledRegions,
    String condition,
    Map<String, Object> metadata
) {

    public CancellationRegionDef {
        cancelledTasks = cancelledTasks != null ? List.copyOf(cancelledTasks) : List.of();
        cancelledRegions = cancelledRegions != null ? List.copyOf(cancelledRegions) : List.of();
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    /**
     * Create a simple cancellation region.
     */
    public static CancellationRegionDef of(String triggerTask, List<String> cancelledTasks) {
        return new CancellationRegionDef(triggerTask, cancelledTasks, List.of(), null, Map.of());
    }

    /**
     * Create a conditional cancellation region.
     */
    public static CancellationRegionDef conditional(String triggerTask, List<String> cancelledTasks, String condition) {
        return new CancellationRegionDef(triggerTask, cancelledTasks, List.of(), condition, Map.of());
    }

    /**
     * Check if this is a conditional cancellation.
     */
    public boolean isConditional() {
        return condition != null && !condition.isEmpty();
    }
}
