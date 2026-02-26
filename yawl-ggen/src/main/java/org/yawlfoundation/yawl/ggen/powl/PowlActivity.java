/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.powl;

/**
 * A leaf node in a POWL model representing a single atomic activity.
 * PowlActivity records are immutable and comparable by identity.
 *
 * @param id    the unique identifier for this activity (must not be blank)
 * @param label the human-readable label for this activity (must not be blank)
 */
public record PowlActivity(String id, String label) implements PowlNode {

    /**
     * Compact constructor enforcing non-blank id and label.
     */
    public PowlActivity {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("label must not be blank");
        }
    }
}
