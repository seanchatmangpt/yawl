/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.elements;

/**
 * A simple version numbering implementation stored as a major part and a minor part
 * (both int) but represented externally as a dotted String (eg 5.12)
 *
 * @author Michael Adams
 *         Date: 18/10/2007
 *         Last Date: 05/06/08
 */

public record YSpecVersion(int major, int minor) implements Comparable<YSpecVersion> {

    /**
     * Compact constructor with validation for non-negative values.
     */
    public YSpecVersion {
        if (major < 0) {
            throw new IllegalArgumentException("Major version must be non-negative, got: " + major);
        }
        if (minor < 0) {
            throw new IllegalArgumentException("Minor version must be non-negative, got: " + minor);
        }
    }

    // Constructor with default starting version (0.1)
    public YSpecVersion() {
        this(0, 1);
    }

    // Constructor as string
    public YSpecVersion(String version) {
        this(parseMajor(version), parseMinor(version));
    }

    private static int parseMajor(String version) {
        if (version == null) return 0;
        try {
            if (version.indexOf('.') > -1) {
                String[] part = version.split("\\.");
                return Integer.parseInt(part[0]);
            } else {
                return Integer.parseInt(version);
            }
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int parseMinor(String version) {
        if (version == null) return 1;
        try {
            if (version.indexOf('.') > -1) {
                String[] part = version.split("\\.");
                return Integer.parseInt(part[1]);
            } else {
                int major = Integer.parseInt(version);
                return major == 0 ? 1 : 0;
            }
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /**
     * Sets the version to the specified major and minor values.
     * For backward compatibility, this returns the string representation.
     * Note: Records are immutable; callers should use the returned value
     * or assign to a new variable if the updated version is needed.
     *
     * @param major the major version number
     * @param minor the minor version number
     * @return the string representation of the version
     */
    public String setVersion(int major, int minor) {
        return new YSpecVersion(major, minor).toString();
    }

    /**
     * Sets the version from a string representation.
     * For backward compatibility, this returns the string representation.
     *
     * @param version the version string (e.g., "5.12" or "5")
     * @return the string representation of the version
     */
    public String setVersion(String version) {
        return new YSpecVersion(version).toString();
    }

    public String getVersion() {
        return toString();
    }

    /**
     * @return the version as a double (legacy method)
     */
    public double toDouble() {
        try {
            return Double.valueOf(toString());
        } catch (Exception e) {
            return 0.1;
        }
    }

    @Override
    public String toString() {
        return String.format("%d.%d", major, minor);
    }

    public int getMajorVersion() {
        return major;
    }

    public int getMinorVersion() {
        return minor;
    }

    /**
     * Increments the minor version.
     * For backward compatibility, returns the string representation.
     *
     * @return the string representation of the incremented version
     */
    public String minorIncrement() {
        return new YSpecVersion(major, minor + 1).toString();
    }

    /**
     * Increments the major version.
     * For backward compatibility, returns the string representation.
     *
     * @return the string representation of the incremented version
     */
    public String majorIncrement() {
        return new YSpecVersion(major + 1, minor).toString();
    }

    /**
     * Decrements the minor version.
     * For backward compatibility, returns the string representation.
     *
     * @return the string representation of the decremented version
     */
    public String minorRollback() {
        return new YSpecVersion(major, minor - 1).toString();
    }

    /**
     * Decrements the major version.
     * For backward compatibility, returns the string representation.
     *
     * @return the string representation of the decremented version
     */
    public String majorRollback() {
        return new YSpecVersion(major - 1, minor).toString();
    }

    @Override
    public int compareTo(YSpecVersion other) {
        if (this.equals(other)) {
            return 0;
        } else if (this.equalsMajorVersion(other)) {
            return this.minor - other.minor;
        } else {
            return this.major - other.major;
        }
    }

    public boolean equalsMajorVersion(YSpecVersion other) {
        return this.major == other.major;
    }

    public boolean equalsMinorVersion(YSpecVersion other) {
        return this.minor == other.minor;
    }

    /**
     * Note: Record automatically provides equals() and hashCode() based on components.
     * This override maintains the exact original behavior for consistency.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof YSpecVersion that)) return false;
        return this.major == that.major && this.minor == that.minor;
    }

    /**
     * Note: Record automatically provides hashCode() based on components.
     * This override maintains the exact original hash algorithm for consistency.
     */
    @Override
    public int hashCode() {
        return (17 * major) * (31 * minor);
    }
}
