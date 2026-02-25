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

package org.yawlfoundation.yawl.pi.rag;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable entry in the process knowledge base.
 *
 * <p>Represents a single fact about a process specification, such as
 * performance metrics, conformance data, or variant information.
 * Each entry can be embedded for vector-based similarity search.</p>
 *
 * @param entryId unique identifier for this knowledge entry
 * @param specificationId ID of the specification this fact relates to
 * @param factText human-readable fact text (e.g., "Average flow time is 450ms")
 * @param factType categorization (performance, conformance, variant, bottleneck)
 * @param embedding vector embedding for similarity search (null if not yet computed)
 * @param ingestedAt timestamp when this entry was added to the knowledge base
 * @param metadata additional structured data associated with this fact
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record KnowledgeEntry(
    String entryId,
    String specificationId,
    String factText,
    String factType,
    float[] embedding,
    Instant ingestedAt,
    Map<String, Object> metadata
) {
    /**
     * Copy this entry with a new embedding vector.
     *
     * @param newEmbedding vector to set
     * @return new KnowledgeEntry with updated embedding
     */
    public KnowledgeEntry withEmbedding(float[] newEmbedding) {
        return new KnowledgeEntry(
            entryId,
            specificationId,
            factText,
            factType,
            newEmbedding,
            ingestedAt,
            metadata
        );
    }
}
