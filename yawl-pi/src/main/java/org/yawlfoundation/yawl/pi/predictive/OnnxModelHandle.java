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

package org.yawlfoundation.yawl.pi.predictive;

import java.nio.file.Path;
import java.time.Instant;

/**
 * Immutable metadata for a loaded ONNX model.
 *
 * <p>Tracks model location, file size, hash, and load timestamp for caching
 * and validation purposes.
 *
 * @param taskName Task or use case this model predicts for (e.g., "case_outcome")
 * @param modelPath File system path to .onnx model file
 * @param modelHash SHA-256 hash of model file for integrity validation
 * @param fileSizeBytes Size of model file in bytes
 * @param loadedAt Instant when this model was loaded into memory
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public record OnnxModelHandle(
    String taskName,
    Path modelPath,
    String modelHash,
    long fileSizeBytes,
    Instant loadedAt
) {

    /**
     * Construct with validation.
     *
     * @throws NullPointerException if any parameter is null
     * @throws IllegalArgumentException if fileSizeBytes is negative
     */
    public OnnxModelHandle {
        if (taskName == null) throw new NullPointerException("taskName is required");
        if (modelPath == null) throw new NullPointerException("modelPath is required");
        if (modelHash == null) throw new NullPointerException("modelHash is required");
        if (loadedAt == null) throw new NullPointerException("loadedAt is required");
        if (fileSizeBytes < 0) {
            throw new IllegalArgumentException("fileSizeBytes must be non-negative");
        }
    }
}
