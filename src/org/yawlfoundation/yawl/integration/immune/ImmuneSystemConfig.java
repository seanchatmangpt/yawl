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

package org.yawlfoundation.yawl.integration.immune;

import java.util.Collections;
import java.util.Set;

/**
 * Configuration record for the WorkflowImmuneSystem behavior.
 *
 * <p>Allows callers to customize:
 * <ul>
 *   <li>Whether to automatically compensate when deadlock is predicted</li>
 *   <li>How many steps ahead to analyze in the workflow net</li>
 *   <li>Which deadlock patterns to ignore (e.g., non-critical warnings)</li>
 * </ul>
 *
 * @param autoCompensate  if true, emit compensating actions when deadlock detected
 * @param lookaheadDepth  how many task firing steps ahead to analyze (1..5)
 * @param ignoredPatterns set of finding type names to ignore (e.g., "MISSING_OR_JOIN")
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record ImmuneSystemConfig(
    boolean autoCompensate,
    int lookaheadDepth,
    Set<String> ignoredPatterns
) {
    /**
     * Creates a default configuration:
     * <ul>
     *   <li>autoCompensate = false (no automatic compensation)</li>
     *   <li>lookaheadDepth = 3 (analyze up to 3 steps ahead)</li>
     *   <li>ignoredPatterns = empty (check all patterns)</li>
     * </ul>
     *
     * @return default configuration instance
     */
    public static ImmuneSystemConfig defaults() {
        return new ImmuneSystemConfig(false, 3, Set.of());
    }

    /**
     * Canonical constructor that makes ignoredPatterns immutable.
     */
    public ImmuneSystemConfig {
        if (ignoredPatterns == null) {
            ignoredPatterns = Set.of();
        } else {
            ignoredPatterns = Collections.unmodifiableSet(ignoredPatterns);
        }
    }
}
