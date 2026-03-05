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

package org.yawlfoundation.yawl.safe.agents;

import java.util.List;

/**
 * SAFe user story representation (Java 25 record).
 *
 * <p>Immutable data class for user stories with:
 * <ul>
 *   <li>Story ID and title</li>
 *   <li>Acceptance criteria</li>
 *   <li>Story points (estimation)</li>
 *   <li>Priority and status</li>
 *   <li>Dependencies on other stories</li>
 * </ul>
 *
 * @param id unique story identifier
 * @param title human-readable story title
 * @param description detailed story description
 * @param acceptanceCriteria list of acceptance criteria
 * @param storyPoints effort estimation
 * @param priority numeric priority (lower = higher priority)
 * @param status current status (backlog, ready, in-progress, complete, blocked)
 * @param dependsOn list of story IDs this story depends on
 * @param assigneeId agent ID if assigned, null otherwise
 * @since YAWL 6.0
 */
public record UserStory(
        String id,
        String title,
        String description,
        List<String> acceptanceCriteria,
        int storyPoints,
        int priority,
        String status,
        List<String> dependsOn,
        String assigneeId
) {

    /**
     * Canonical constructor with validation.
     */
    public UserStory {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Story id is required");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Story title is required");
        }
        if (storyPoints < 0) {
            throw new IllegalArgumentException("Story points must be non-negative");
        }
        if (acceptanceCriteria == null) {
            acceptanceCriteria = List.of();
        }
        if (dependsOn == null) {
            dependsOn = List.of();
        }
    }

    /**
     * Check if story is blocked (has unmet dependencies).
     *
     * @param completedStories IDs of completed stories
     * @return true if any dependency is not in completed list
     */
    public boolean isBlocked(List<String> completedStories) {
        return dependsOn.stream()
                .anyMatch(dep -> !completedStories.contains(dep));
    }

    /**
     * Check if all acceptance criteria are met.
     *
     * @param acceptedCriteria indices of accepted criteria
     * @return true if all criteria are accepted
     */
    public boolean allCriteriaMet(List<Integer> acceptedCriteria) {
        if (acceptanceCriteria.isEmpty()) {
            return true;
        }
        return acceptedCriteria.size() == acceptanceCriteria.size();
    }
}
