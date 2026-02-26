/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.rl.scoring;

import java.util.List;
import java.util.Set;

/**
 * A footprint matrix capturing the behavioral relationships between activities.
 * Used for process discovery and conformance checking.
 *
 * @param directSuccession  set of pairs (a, b) where a directly precedes b in sequence
 * @param concurrency       set of pairs (a, b) where a and b execute concurrently
 * @param exclusive         set of pairs (a, b) where a and b are mutually exclusive (XOR choice)
 */
public record FootprintMatrix(
        Set<List<String>> directSuccession,
        Set<List<String>> concurrency,
        Set<List<String>> exclusive
) {

    /**
     * Compact constructor making defensive copies of all sets.
     */
    public FootprintMatrix {
        directSuccession = Set.copyOf(directSuccession);
        concurrency = Set.copyOf(concurrency);
        exclusive = Set.copyOf(exclusive);
    }

    /**
     * Factory method for an empty FootprintMatrix.
     *
     * @return a FootprintMatrix with no relationships
     */
    public static FootprintMatrix empty() {
        return new FootprintMatrix(Set.of(), Set.of(), Set.of());
    }
}
