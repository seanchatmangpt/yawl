/*
 * Copyright (c) 2004-2025 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.elements.patterns;

import java.util.Set;

/**
 * Enumeration of milestone guard combination operators.
 *
 * <p>Defines how multiple milestone guards are combined to determine
 * whether a guarded task can execute.</p>
 *
 * @author YAWL Foundation
 * @since 5.3
 */
public enum MilestoneGuardOperator {

    /**
     * All milestones must be reached.
     *
     * <p>Use case: Ship only when payment AND inventory confirmed.</p>
     */
    AND {
        @Override
        public boolean evaluate(Set<Boolean> milestoneStates) {
            if (milestoneStates.isEmpty()) {
                return false;
            }
            return milestoneStates.stream().allMatch(b -> b);
        }
    },

    /**
     * Any milestone must be reached.
     *
     * <p>Use case: Proceed when fast-track OR normal approval received.</p>
     */
    OR {
        @Override
        public boolean evaluate(Set<Boolean> milestoneStates) {
            return milestoneStates.stream().anyMatch(b -> b);
        }
    },

    /**
     * Exactly one milestone must be reached.
     *
     * <p>Use case: Process with single approval path.</p>
     */
    XOR {
        @Override
        public boolean evaluate(Set<Boolean> milestoneStates) {
            return milestoneStates.stream().filter(b -> b).count() == 1;
        }
    };

    /**
     * Evaluates the milestone states according to this operator.
     *
     * @param milestoneStates the set of milestone reached states
     * @return true if the guard condition is satisfied
     */
    public abstract boolean evaluate(Set<Boolean> milestoneStates);

    /**
     * Parses a string to a guard operator.
     *
     * @param value the string value
     * @return the guard operator, defaults to AND if unknown
     */
    public static MilestoneGuardOperator fromString(String value) {
        if (value == null) {
            return AND;
        }
        switch (value.toUpperCase()) {
            case "OR":
                return OR;
            case "XOR":
                return XOR;
            default:
                return AND;
        }
    }
}
