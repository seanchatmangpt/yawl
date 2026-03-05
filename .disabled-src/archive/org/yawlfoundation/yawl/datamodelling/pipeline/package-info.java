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
 * Data pipeline orchestration and integration layer.
 *
 * <h2>Overview</h2>
 * This package provides a type-safe Java wrapper around the data-modelling-sdk's
 * end-to-end data pipeline, enabling seamless schema inference, transformation,
 * and export from within YAWL workflow execution.
 *
 * <h2>Architecture</h2>
 * The pipeline consists of 5 sequential stages:
 * <ol>
 *   <li><strong>INGEST</strong> ({@link IngestConfig}, {@link IngestResult})
 *       — Load raw data from JSON, CSV, databases</li>
 *   <li><strong>INFER</strong> ({@link InferenceConfig}, {@link InferenceResult})
 *       — Auto-detect schema structure, types, constraints</li>
 *   <li><strong>REFINE</strong> ({@link RefineConfig}, {@link RefineResult})
 *       — LLM enhancement, manual rules, pattern detection</li>
 *   <li><strong>MAP</strong> ({@link MappingConfig}, {@link MappingResult})
 *       — Map source to target schema with transformation generation</li>
 *   <li><strong>EXPORT</strong> ({@link ExportConfig}, {@link ExportResult})
 *       — Export results to ODCS, SQL, JSON Schema, or other formats</li>
 * </ol>
 *
 * <h2>Main Entry Points</h2>
 *
 * <h3>Full Pipeline Execution</h3>
 * <pre>{@code
 * try (DataModellingBridge bridge = new DataModellingBridge();
 *      DataModellingPipeline pipeline = new DataModellingPipeline(bridge)) {
 *
 *     ExportResult result = pipeline.executeFullPipeline(
 *         ingestConfig, inferenceConfig, refineConfig, mappingConfig, exportConfig);
 *
 *     System.out.println(result.getExportedContent());
 * }
 * }</pre>
 *
 * <h3>Individual Stage Execution</h3>
 * <pre>{@code
 * IngestResult ingestResult = pipeline.executeStage(PipelineStage.INGEST, ingestConfig);
 * InferenceResult inferResult = pipeline.executeStage(PipelineStage.INFER, inferenceConfig);
 *
 * // Bridge to Phase 1 Type-Safe Models
 * DataModellingWorkspace workspace = inferResult.asWorkspace();
 * }</pre>
 *
 * <h3>Staging Database Operations</h3>
 * <pre>{@code
 * StagingDatabase staging = pipeline.getStagingDatabase();
 * IngestResult result = staging.ingestFromJson(jsonData, ingestConfig);
 * String profile = staging.profileData(result.getStagingLocation());
 * }</pre>
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link DataModellingPipeline} — Main orchestrator</li>
 *   <li>{@link StagingDatabase} — DuckDB staging wrapper</li>
 *   <li>{@link PipelineStage} — Enum of pipeline stages</li>
 * </ul>
 *
 * <h2>Configuration Classes</h2>
 * <ul>
 *   <li>{@link IngestConfig} — INGEST stage parameters</li>
 *   <li>{@link InferenceConfig} — INFER stage parameters</li>
 *   <li>{@link RefineConfig} — REFINE stage parameters (LLM, rules, patterns)</li>
 *   <li>{@link MappingConfig} — MAP stage parameters (matching, transformation)</li>
 *   <li>{@link ExportConfig} — EXPORT stage parameters (format, location)</li>
 * </ul>
 *
 * <h2>Result Classes</h2>
 * <ul>
 *   <li>{@link IngestResult} — INGEST stage output (staging location, row counts)</li>
 *   <li>{@link InferenceResult} — INFER stage output (inferred schema, confidence)
 *       <ul>
 *         <li><strong>Phase 1 Bridge</strong>: {@code asWorkspace()} converts to
 *           {@link org.yawlfoundation.yawl.datamodelling.models.DataModellingWorkspace}</li>
 *       </ul></li>
 *   <li>{@link RefineResult} — REFINE stage output (refined schema, metadata)</li>
 *   <li>{@link MappingResult} — MAP stage output (field mappings, transformation script)
 *       <ul>
 *         <li><strong>Nested Class</strong>:
 *           {@link MappingResult.FieldMapping} for individual source→target mappings</li>
 *       </ul></li>
 *   <li>{@link ExportResult} — EXPORT stage output (exported content, format)</li>
 * </ul>
 *
 * <h2>Integration with Phase 1 Type-Safe Models</h2>
 * <p>Phase 2 seamlessly bridges to Phase 1 via:</p>
 * <pre>{@code
 * InferenceResult inferResult = pipeline.executeStage(PipelineStage.INFER, config);
 *
 * // Convert raw JSON to Phase 1 workspace
 * DataModellingWorkspace workspace = inferResult.asWorkspace();
 * workspace.getTables();     // Full Phase 1 API
 * workspace.getRelationships();
 * }</pre>
 *
 * <h2>Error Handling</h2>
 * <p>All stage execution throws:</p>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.datamodelling.DataModellingException}
 *       — WASM bridge execution failures</li>
 *   <li>{@link UnsupportedOperationException} — WASM SDK capability gaps
 *       (with clear implementation guidance)</li>
 *   <li>{@link IllegalArgumentException} — Invalid configuration or arguments</li>
 *   <li>{@link IllegalStateException} — Pipeline already closed</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>All pipeline operations are thread-safe via the underlying
 * {@link org.yawlfoundation.yawl.datamodelling.DataModellingBridge}
 * context pool. Multiple threads can execute different stages in parallel.</p>
 *
 * <h2>Resource Management</h2>
 * <p>Both {@link DataModellingPipeline} and {@link StagingDatabase}
 * implement {@code AutoCloseable} for proper resource cleanup:</p>
 * <pre>{@code
 * try (DataModellingPipeline pipeline = new DataModellingPipeline(bridge)) {
 *     // Pipeline executes here
 * } // Resources automatically cleaned up
 * }</pre>
 *
 * <h2>WASM SDK Dependencies</h2>
 * <p>This package wraps the data-modelling-sdk v2.3.0 (MIT license).
 * Full functionality requires WASM SDK to expose:</p>
 * <ul>
 *   <li>{@code infer_schema_from_json()}</li>
 *   <li>{@code map_schemas()}</li>
 *   <li>{@code generate_transform()}</li>
 *   <li>DuckDB staging operations</li>
 * </ul>
 * <p>Until then, these methods throw {@code UnsupportedOperationException}
 * with clear implementation guidance.</p>
 *
 * <h2>Configuration Format</h2>
 * <p>All Config classes support YAML serialization via Jackson:</p>
 * <pre>{@code
 * // YAML input
 * String yaml = """
 *     sourceType: json
 *     sourcePath: /data/customers.json
 *     format: json
 *     sampleSize: 1000
 *     deduplicationStrategy: exact
 *     """;
 *
 * // Convert to Java
 * ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
 * IngestConfig config = mapper.readValue(yaml, IngestConfig.class);
 *
 * // Execute
 * IngestResult result = pipeline.executeStage(PipelineStage.INGEST, config);
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see DataModellingPipeline
 * @see StagingDatabase
 * @see org.yawlfoundation.yawl.datamodelling.DataModellingBridge
 * @see org.yawlfoundation.yawl.datamodelling.models.DataModellingWorkspace
 */
package org.yawlfoundation.yawl.datamodelling.pipeline;

import org.yawlfoundation.yawl.datamodelling.DataModellingBridge;
import org.yawlfoundation.yawl.datamodelling.models.DataModellingWorkspace;
