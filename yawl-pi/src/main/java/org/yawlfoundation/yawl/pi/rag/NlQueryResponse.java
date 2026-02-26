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
import java.util.List;

/**
 * Immutable response from natural language query processing.
 *
 * <p>Contains the AI-generated answer, source facts used for grounding,
 * and metadata about the query processing.</p>
 *
 * @param requestId unique identifier matching the original query request
 * @param answer the generated natural language response
 * @param sourceFacts list of fact texts retrieved from knowledge base
 * @param groundedInKnowledgeBase true if answer is based on retrieved facts
 * @param modelUsed name of the language model used (e.g., "GLM-4.7-Flash")
 * @param latencyMs query processing time in milliseconds
 * @param respondedAt timestamp when response was generated
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record NlQueryResponse(
    String requestId,
    String answer,
    List<String> sourceFacts,
    boolean groundedInKnowledgeBase,
    String modelUsed,
    long latencyMs,
    Instant respondedAt
) {}
