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
 * Core interface for conflict resolution strategies in multi-agent systems.
 *
 * Provides a common contract for different conflict resolution mechanisms including
 * majority voting, escalation to human arbiter, and fallback to human oversight.
 *
 * <p>Conflict resolution is triggered when multiple autonomous agents produce
 * conflicting decisions for the same workflow task or decision point.</p>
 *
 * <p>Implementations must follow the NO STUBS OR MOCKS principle - provide
 * real resolution mechanisms or throw UnsupportedOperationException.</p>
 *
 * @author YAWL Foundation
 * @version 5.2
 * @see MajorityVoteConflictResolver
 * @see EscalatingConflictResolver
 * @see HumanFallbackConflictResolver
 * @see ConflictResolutionService
 */
public interface ConflictResolver {

    /**
     * Resolution strategy identifiers for configuration and logging.
     */
    enum Strategy {
        MAJORITY_VOTE("Majority Vote"),
        ESCALATING("Escalating to Arbiter"),
        HUMAN_FALLBACK("Human Fallback"),
        HYBRID("Hybrid Strategy");

        private final String displayName;

        Strategy(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Conflict severity levels for prioritization and handling.
     */
    enum Severity {
        LOW("Low impact, automatic resolution"),
        MEDIUM("Moderate impact, review needed"),
        HIGH("High impact, immediate attention"),
        CRITICAL("Critical impact, mandatory escalation");

        private final String description;

        Severity(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Resolve a conflict between multiple agent decisions.
     *
     * <p>The conflict resolution process:
     * 1. Analyze the conflicting decisions from multiple agents
     * 2. Apply the resolution strategy specific to this resolver
     * 3. Return a single resolved decision
     * 4. Log the resolution outcome for auditability</p>
     *
     * @param conflictContext The context containing all conflicting decisions
     * @return The resolved decision
     * @throws ConflictResolutionException if resolution fails
     * @throws UnsupportedOperationException if not implemented by this resolver
     */
    Decision resolveConflict(ConflictContext conflictContext) throws ConflictResolutionException;

    /**
     * Check if this resolver can handle the given conflict context.
     *
     * @param conflictContext The conflict to check
     * @return true if this resolver can handle the conflict, false otherwise
     */
    boolean canResolve(ConflictContext conflictContext);

    /**
     * Get the resolution strategy type.
     *
     * @return The strategy type
     */
    Strategy getStrategy();

    /**
     * Get configuration parameters for this resolver.
     *
     * @return Map of configuration key-value pairs
     */
    Map<String, Object> getConfiguration();

    /**
     * Update configuration parameters.
     *
     * @param configuration New configuration parameters
     * @throws IllegalArgumentException if configuration is invalid
     */
    void updateConfiguration(Map<String, Object> configuration);

    /**
     * Health check for the resolver.
     *
     * @return true if the resolver is healthy and ready to resolve conflicts
     */
    boolean isHealthy();
}

