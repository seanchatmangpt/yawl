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
 * YAWL Workflow Analytics Engine — QLever-backed process mining for YAWL workflows.
 *
 * <p>This package makes every workflow execution queryable as an RDF graph.
 * The pipeline has three stages:</p>
 *
 * <ol>
 *   <li><b>Publish</b>: {@link org.yawlfoundation.yawl.integration.autonomous.analytics.WorkflowEventPublisher}
 *       subscribes to the {@code WorkflowEventBus} and converts each lifecycle event
 *       (case start/complete/cancel, task status change) to a SPARQL INSERT DATA update
 *       posted to QLever.</li>
 *
 *   <li><b>Query</b>: {@link org.yawlfoundation.yawl.integration.autonomous.analytics.WorkflowQueryService}
 *       provides 10 pre-built SPARQL queries for bypass detection, throughput,
 *       bottleneck analysis, SLA reporting, and full case path traces — all executed
 *       against the embedded QLever engine via
 *       {@link org.yawlfoundation.yawl.integration.autonomous.marketplace.QLeverEmbeddedEngineAdapter}.</li>
 *
 *   <li><b>Present</b>: {@link org.yawlfoundation.yawl.integration.autonomous.analytics.WorkflowAnalytics}
 *       provides typed Java 25 record types ({@code TaskSummary}, {@code CaseSummary},
 *       {@code SlaReport}, {@code Throughput}, {@code TaskTransition}, {@code PathTrace})
 *       and plain-Java parsers for the raw SPARQL result strings.</li>
 * </ol>
 *
 * <h2>RDF Vocabulary</h2>
 * <p>All RDF terms use the namespace {@code http://yawlfoundation.org/yawl/analytics#}
 * defined in {@link org.yawlfoundation.yawl.integration.autonomous.analytics.WorkflowEventVocabulary}.</p>
 *
 * <h2>Resilience</h2>
 * <p>QLever unavailability never disrupts the workflow engine. The publisher catches
 * all {@code SparqlEngineException}s and logs at WARN. The query service returns
 * {@code Optional.empty()} when QLever is down.</p>
 *
 * <h2>Performance</h2>
 * <p>QLever handles sub-100ms CONSTRUCT queries at 1 billion triples. The analytics
 * layer adds zero overhead to the hot workflow execution path — all SPARQL writes
 * happen asynchronously on virtual threads via the {@code FlowWorkflowEventBus}.</p>
 *
 * @since YAWL 6.0
 */
package org.yawlfoundation.yawl.integration.autonomous.analytics;
