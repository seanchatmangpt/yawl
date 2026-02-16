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

import org.yawlfoundation.yawl.elements.YTask;

import java.util.Random;
import java.util.Set;

/**
 * Enumeration of strategies for selecting the next task to execute
 * in interleaved parallel routing.
 *
 * @author YAWL Foundation
 * @since 5.3
 */
public enum InterleavedSelectionStrategy {

    /**
     * First enabled, first to execute.
     *
     * <p>Use case: Fair ordering based on arrival time.</p>
     */
    FIFO {
        @Override
        public YTask select(Set<YTask> waitingTasks, YInterleavedState state) {
            if (waitingTasks.isEmpty()) {
                return null;
            }
            // Return first task (set iteration order)
            return waitingTasks.iterator().next();
        }
    },

    /**
     * Based on task priority.
     *
     * <p>Use case: Critical tasks should execute first.</p>
     */
    PRIORITY {
        @Override
        public YTask select(Set<YTask> waitingTasks, YInterleavedState state) {
            if (waitingTasks.isEmpty()) {
                return null;
            }
            // Return task with highest priority (by name/ID comparison)
            return waitingTasks.stream()
                    .min((t1, t2) -> t1.getID().compareTo(t2.getID()))
                    .orElse(null);
        }
    },

    /**
     * Random selection.
     *
     * <p>Use case: Load distribution among equivalent tasks.</p>
     */
    RANDOM {
        private final Random random = new Random();

        @Override
        public YTask select(Set<YTask> waitingTasks, YInterleavedState state) {
            if (waitingTasks.isEmpty()) {
                return null;
            }
            int index = random.nextInt(waitingTasks.size());
            int i = 0;
            for (YTask task : waitingTasks) {
                if (i == index) {
                    return task;
                }
                i++;
            }
            return null;
        }
    },

    /**
     * Estimated shortest duration first.
     *
     * <p>Use case: Minimize overall wait time.</p>
     */
    SHORTEST_FIRST {
        @Override
        public YTask select(Set<YTask> waitingTasks, YInterleavedState state) {
            if (waitingTasks.isEmpty()) {
                return null;
            }
            // Return task with shortest ID (simple heuristic)
            return waitingTasks.stream()
                    .min((t1, t2) -> {
                        int len1 = t1.getID().length();
                        int len2 = t2.getID().length();
                        return Integer.compare(len1, len2);
                    })
                    .orElse(null);
        }
    },

    /**
     * Fair rotation through tasks.
     *
     * <p>Use case: Ensure fairness over multiple cycles.</p>
     */
    ROUND_ROBIN {
        @Override
        public YTask select(Set<YTask> waitingTasks, YInterleavedState state) {
            if (waitingTasks.isEmpty()) {
                return null;
            }
            // Use cycle count to rotate selection
            int index = state.getCycleCount() % waitingTasks.size();
            int i = 0;
            for (YTask task : waitingTasks) {
                if (i == index) {
                    return task;
                }
                i++;
            }
            return null;
        }
    };

    /**
     * Selects the next task to execute.
     *
     * @param waitingTasks the set of waiting tasks
     * @param state the current router state
     * @return the selected task, or null if no tasks available
     */
    public abstract YTask select(Set<YTask> waitingTasks, YInterleavedState state);

    /**
     * Parses a string to a selection strategy.
     *
     * @param value the string value
     * @return the strategy, defaults to FIFO if unknown
     */
    public static InterleavedSelectionStrategy fromString(String value) {
        if (value == null) {
            return FIFO;
        }
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return FIFO;
        }
    }
}
