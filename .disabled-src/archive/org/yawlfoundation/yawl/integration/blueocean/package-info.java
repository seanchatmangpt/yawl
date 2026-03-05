/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 */

/**
 * Blue Ocean Enhancement Phase 6 — Data Lineage, Contract Validation, and Metrics.
 *
 * <p>This package provides production-grade implementations for:</p>
 *
 * <ul>
 *   <li><b>lineage</b>: RDF-based data lineage tracking with Lucene indexing
 *   <li><b>validation</b>: Guard standards enforcement and data contract validation
 *   <li><b>instrumentation</b>: OpenTelemetry metrics for monitoring
 * </ul>
 *
 * <h2>Key Components</h2>
 *
 * <dl>
 *   <dt>RdfLineageStore</dt>
 *   <dd>Records and queries data flow through workflows using Apache Jena RDF and TDB2.
 *   Supports table-level and column-level lineage, with Lucene indexing for fast queries.</dd>
 *
 *   <dt>HyperStandardsValidator</dt>
 *   <dd>Validates Java code against 7 critical Fortune 5 standards (H-Guards):
 *   H_TODO (deferred work), H_MOCK (spurious implementations), H_STUB (trivial returns),
 *   H_EMPTY (unimplemented bodies), H_FALLBACK (swallowed exceptions),
 *   H_LIE (documentation mismatches), H_SILENT (error suppression).</dd>
 *
 *   <dt>DataContractValidator</dt>
 *   <dd>Enforces task preconditions before workflow execution. Validates input types,
 *   required fields, lineage satisfaction, and SLA preconditions. Throws exceptions
 *   on contract violations (no silent fallbacks).</dd>
 *
 *   <dt>OpenTelemetryMetricsInstrumentation</dt>
 *   <dd>Prometheus metrics collection for lineage queries, table access latency,
 *   schema drift, guard violations, and contract violations. Includes structured
 *   logging with OpenTelemetry context propagation.</dd>
 * </ul>
 *
 * <h2>Integration Points</h2>
 *
 * <ul>
 *   <li>DataModellingBridge: Add lineage recording hooks on schema operations
 *   <li>ExternalDataGateway: Inject contract validation on data access
 *   <li>YWorkItem: Track execution metadata and data provenance
 *   <li>YTask: Support @DataContract annotations for preconditions
 * </ul>
 *
 * <h2>Quality Standards</h2>
 *
 * <p>All implementations follow YAWL production standards:</p>
 * <ul>
 *   <li>No TODO/FIXME markers (deferred work blocked by hooks)
 *   <li>No mock/stub/fake code (real implementations or exceptions)
 *   <li>Thread-safe concurrent operations (ReentrantLock, ConcurrentHashMap)
 *   <li>Comprehensive error handling with clear messages
 *   <li>Full test coverage (unit, integration, performance)
 *   <li>Structured logging with OpenTelemetry context
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
package org.yawlfoundation.yawl.integration.blueocean;
