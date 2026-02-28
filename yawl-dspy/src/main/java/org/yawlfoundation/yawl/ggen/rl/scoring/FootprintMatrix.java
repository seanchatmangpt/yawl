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

package org.yawlfoundation.yawl.ggen.rl.scoring;

/**
 * Stub implementation of FootprintMatrix for compilation purposes.
 *
 * This is a placeholder implementation that provides the minimum interface
 * required for compilation. In a complete implementation, this class would
 * represent a behavioral footprint matrix for workflow patterns.
 */
public final class FootprintMatrix {

    /**
     * Creates a new FootprintMatrix instance.
     *
     * @throws UnsupportedOperationException since this is a compilation stub
     */
    public FootprintMatrix() {
        throw new UnsupportedOperationException(
            "FootprintMatrix constructor requires real implementation. " +
            "This is a compilation stub that needs to be implemented with proper footprint matrix representation."
        );
    }

    /**
     * Gets the footprint value for the specified pattern.
     *
     * @param pattern the workflow pattern identifier
     * @return the footprint value (throws since this is a stub)
     * @throws UnsupportedOperationException since this is a compilation stub
     */
    public double getFootprintValue(String pattern) {
        throw new UnsupportedOperationException(
            "FootprintMatrix.getFootprintValue() requires real implementation. " +
            "This is a compilation stub that needs to be implemented with proper footprint matrix logic."
        );
    }

    /**
     * Sets the footprint value for the specified pattern.
     *
     * @param pattern the workflow pattern identifier
     * @param value the footprint value to set
     * @throws UnsupportedOperationException since this is a compilation stub
     */
    public void setFootprintValue(String pattern, double value) {
        throw new UnsupportedOperationException(
            "FootprintMatrix.setFootprintValue() requires real implementation. " +
            "This is a compilation stub that needs to be implemented with proper footprint matrix logic."
        );
    }

    /**
     * Calculates the footprint agreement between two patterns.
     *
     * @param pattern1 the first pattern
     * @param pattern2 the second pattern
     * @return the agreement score (0.0 to 1.0)
     * @throws UnsupportedOperationException since this is a compilation stub
     */
    public double calculateAgreement(String pattern1, String pattern2) {
        throw new UnsupportedOperationException(
            "FootprintMatrix.calculateAgreement() requires real implementation. " +
            "This is a compilation stub that needs to be implemented with proper footprint matrix logic."
        );
    }
}