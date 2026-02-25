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

import java.util.UUID;

/**
 * Immutable natural language query request to the RAG system.
 *
 * <p>Contains a user question, optional specification scope, and retrieval parameters.</p>
 *
 * @param question natural language question to answer
 * @param specificationId specification to search (null for all specs)
 * @param topK number of relevant facts to retrieve
 * @param requestId unique identifier for this query request
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record NlQueryRequest(
    String question,
    String specificationId,
    int topK,
    String requestId
) {
    /**
     * Create a query with default parameters.
     *
     * @param question the user question
     * @return request with topK=5 and default specificationId
     */
    public static NlQueryRequest of(String question) {
        return new NlQueryRequest(
            question,
            null,
            5,
            UUID.randomUUID().toString()
        );
    }

    /**
     * Create a query for a specific specification.
     *
     * @param question the user question
     * @param specId specification identifier
     * @return request with topK=5
     */
    public static NlQueryRequest of(String question, String specId) {
        return new NlQueryRequest(
            question,
            specId,
            5,
            UUID.randomUUID().toString()
        );
    }
}
