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

package org.yawlfoundation.yawl.compliance.shacl;

/**
 * Enumeration of violation severity levels.
 *
 * <p>Severity levels indicate the criticality of compliance violations:</p>
 *
 * <ul>
 *   <li><b>HIGH</b> - Critical violations that may cause legal or regulatory issues</li>
 *   <li><b>MEDIUM</b> - Important violations that should be addressed</li>
 *   <li><b>LOW</b> - Minor violations that are best practices</li>
 *   <li><b>NONE</b> - No violations</li>
 * </ul>
 */
public enum ViolationSeverity implements Comparable<ViolationSeverity> {
    /**
     * No violations - validation passed.
     */
    NONE(0, "None", "No violations found"),

    /**
     * Low severity violations - best practices or minor issues.
     */
    LOW(1, "Low", "Minor compliance issues"),

    /**
     * Medium severity violations - should be addressed but not critical.
     */
    MEDIUM(2, "Medium", "Compliance violations requiring attention"),

    /**
     * High severity violations - critical issues that may cause legal/regulatory problems.
     */
    HIGH(3, "High", "Critical compliance violations");

    private final int level;
    private final String name;
    private final String description;

    ViolationSeverity(int level, String name, String description) {
        this.level = level;
        this.name = name;
        this.description = description;
    }

    /**
     * Gets the severity level.
     */
    public int getLevel() {
        return level;
    }

    /**
     * Gets the severity name.
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the severity description.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Checks if this severity is higher than the given severity.
     */
    public boolean isHigherThan(ViolationSeverity other) {
        return this.level > other.level;
    }

    /**
     * Checks if this severity is lower than the given severity.
     */
    public boolean isLowerThan(ViolationSeverity other) {
        return this.level < other.level;
    }

    /**
     * Checks if this severity is equal to or higher than the given severity.
     */
    public boolean isAtLeast(ViolationSeverity other) {
        return this.level >= other.level;
    }

    /**
     * Checks if this severity is equal to or lower than the given severity.
     */
    public boolean isAtMost(ViolationSeverity other) {
        return this.level <= other.level;
    }
}