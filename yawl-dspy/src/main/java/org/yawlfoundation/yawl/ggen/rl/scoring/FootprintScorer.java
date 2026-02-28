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

import org.yawlfoundation.yawl.ggen.powl.PowlModel;

/**
 * Stub implementation of FootprintScorer for compilation purposes.
 *
 * This is a placeholder implementation that provides the minimum interface
 * required for compilation. In a complete implementation, this class would
 * perform behavioral footprint scoring for workflow models.
 */
public final class FootprintScorer {

    /**
     * Creates a new FootprintScorer with the specified footprint matrix.
     *
     * @param matrix the footprint matrix to use for scoring
     * @throws UnsupportedOperationException since this is a compilation stub
     */
    public FootprintScorer(FootprintMatrix matrix) {
        throw new UnsupportedOperationException(
            "FootprintScorer constructor requires real implementation. " +
            "This is a compilation stub that needs to be implemented with proper footprint analysis logic."
        );
    }

    /**
     * Scores the behavioral footprint of the specified model against the reference.
     *
     * @param model the model to score
     * @param referenceId the reference identifier (optional)
     * @return the footprint score (0.0 to 1.0)
     * @throws UnsupportedOperationException since this is a compilation stub
     */
    public double score(PowlModel model, String referenceId) {
        throw new UnsupportedOperationException(
            "FootprintScorer.score() requires real implementation. " +
            "This is a compilation stub that needs to be implemented with proper footprint analysis logic."
        );
    }

    /**
     * Gets the footrpint matrix used by this scorer.
     *
     * @return the footprint matrix (throws since this is a stub)
     * @throws UnsupportedOperationException since this is a compilation stub
     */
    public FootprintMatrix getFootprintMatrix() {
        throw new UnsupportedOperationException(
            "FootprintScorer.getFootprintMatrix() requires real implementation. " +
            "This is a compilation stub that needs to be implemented with proper footprint analysis logic."
        );
    }
}