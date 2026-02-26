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

/**
 * Retrieval-Augmented Generation (RAG) for natural language process analysis.
 *
 * <h2>Core Components</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.pi.rag.ProcessKnowledgeBase}
 *       - Stores and retrieves process facts from mining reports</li>
 *   <li>{@link org.yawlfoundation.yawl.pi.rag.NaturalLanguageQueryEngine}
 *       - Answers natural language questions using GLM-4 LLM</li>
 *   <li>{@link org.yawlfoundation.yawl.pi.rag.ProcessContextRetriever}
 *       - Keyword-based similarity search for fact retrieval</li>
 * </ul>
 *
 * <h2>Data Models</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.pi.rag.KnowledgeEntry}
 *       - Immutable representation of a process fact</li>
 *   <li>{@link org.yawlfoundation.yawl.pi.rag.NlQueryRequest}
 *       - User question with scope and retrieval parameters</li>
 *   <li>{@link org.yawlfoundation.yawl.pi.rag.NlQueryResponse}
 *       - AI-generated answer with source facts and metadata</li>
 * </ul>
 *
 * <h2>Workflow</h2>
 * <ol>
 *   <li>Ingest process mining report into {@link ProcessKnowledgeBase}</li>
 *   <li>Create {@link NaturalLanguageQueryEngine} with knowledge base</li>
 *   <li>Call {@link NaturalLanguageQueryEngine#query(NlQueryRequest)}</li>
 *   <li>Receive {@link NlQueryResponse} with LLM-generated answer</li>
 * </ol>
 *
 * <h2>Key Features</h2>
 * <ul>
 *   <li><b>Thread-safe</b>: All storage uses ReentrantReadWriteLock</li>
 *   <li><b>Graceful degradation</b>: Falls back to raw facts if Z.AI API unavailable</li>
 *   <li><b>Keyword retrieval</b>: No embedding vectors required for initial deployment</li>
 *   <li><b>Grounding</b>: Answers based on process data, not hallucinations</li>
 *   <li><b>Traceability</b>: All answers include source facts for verification</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
package org.yawlfoundation.yawl.pi.rag;
