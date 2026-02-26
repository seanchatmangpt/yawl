/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.powl;

import java.time.Instant;
import java.util.Objects;

/**
 * A complete POWL (Partially Ordered Workflow Language) model.
 * Contains a root node representing the entire workflow and metadata about generation.
 *
 * @param id          the unique identifier for this POWL model (must not be blank)
 * @param root        the root node of the workflow tree (must not be null)
 * @param generatedAt the timestamp when this model was created (must not be null)
 */
public record PowlModel(String id, PowlNode root, Instant generatedAt) {

    /**
     * Compact constructor enforcing non-blank id and non-null root/generatedAt.
     */
    public PowlModel {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        Objects.requireNonNull(root, "root must not be null");
        Objects.requireNonNull(generatedAt, "generatedAt must not be null");
    }

    /**
     * Factory method creating a PowlModel with the current timestamp.
     *
     * @param id   the unique identifier for the model
     * @param root the root node of the workflow
     * @return a new PowlModel with generatedAt set to Instant.now()
     */
    public static PowlModel of(String id, PowlNode root) {
        return new PowlModel(id, root, Instant.now());
    }
}
