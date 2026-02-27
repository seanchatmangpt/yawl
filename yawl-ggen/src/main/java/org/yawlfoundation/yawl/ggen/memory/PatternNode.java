/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.memory;

/**
 * A vertex in the {@link ProcessKnowledgeGraph} representing a discovered process pattern.
 *
 * <p>The structural fingerprint is a canonical, order-independent string derived by
 * collecting all activity labels in a POWL model tree, sorting them lexicographically,
 * and joining with " → ". Fingerprints are used as stable node identities across
 * GRPO optimization rounds.
 *
 * @param id                 unique node identifier (fingerprint hash)
 * @param fingerprint        sorted activity labels joined by " → "
 * @param totalReward        accumulated reward across all GRPO hits
 * @param hitCount           number of times this pattern was reinforced
 */
public record PatternNode(String id, String fingerprint, double totalReward, int hitCount) {

    public PatternNode {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("id must not be blank");
        if (fingerprint == null || fingerprint.isBlank())
            throw new IllegalArgumentException("fingerprint must not be blank");
        if (hitCount < 0)
            throw new IllegalArgumentException("hitCount must be >= 0");
    }

    /** Returns the running average reward across all reinforcements. */
    public double averageReward() {
        return hitCount == 0 ? 0.0 : totalReward / hitCount;
    }

    /** Returns a new node with this hit's reward accumulated. */
    public PatternNode withReward(double reward) {
        return new PatternNode(id, fingerprint, totalReward + reward, hitCount + 1);
    }

    /** Factory: creates an initial PatternNode from its fingerprint. */
    public static PatternNode of(String fingerprint) {
        String id = "pn_" + Integer.toHexString(fingerprint.hashCode());
        return new PatternNode(id, fingerprint, 0.0, 0);
    }
}
