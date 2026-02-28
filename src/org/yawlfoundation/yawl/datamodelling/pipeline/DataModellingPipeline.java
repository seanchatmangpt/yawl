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

package org.yawlfoundation.yawl.datamodelling.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.datamodelling.DataModellingBridge;
import org.yawlfoundation.yawl.datamodelling.DataModellingException;
import org.yawlfoundation.yawl.datamodelling.models.DataModellingWorkspace;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Orchestrates end-to-end data pipeline execution through the 5 stages:
 * INGEST → INFER → REFINE → MAP → EXPORT.
 *
 * <p>Coordinates data ingestion, schema inference, refinement, field mapping,
 * and final export, managing state and results across stages.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * try (DataModellingBridge bridge = new DataModellingBridge();
 *      DataModellingPipeline pipeline = new DataModellingPipeline(bridge)) {
 *
 *     // Execute full pipeline
 *     IngestResult ingestResult = pipeline.executeStage(PipelineStage.INGEST,
 *         ingestConfig);
 *
 *     InferenceResult inferResult = pipeline.executeStage(PipelineStage.INFER,
 *         inferenceConfig);
 *
 *     RefineResult refineResult = pipeline.executeStage(PipelineStage.REFINE,
 *         refineConfig);
 *
 *     MappingResult mapResult = pipeline.executeStage(PipelineStage.MAP,
 *         mappingConfig);
 *
 *     ExportResult exportResult = pipeline.executeStage(PipelineStage.EXPORT,
 *         exportConfig);
 * }
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class DataModellingPipeline implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DataModellingPipeline.class);

    private final DataModellingBridge bridge;
    private final Map<PipelineStage, Object> stageResults; // results cache
    private final StagingDatabase stagingDatabase;
    private boolean closed;

    /**
     * Constructs a DataModellingPipeline with the given bridge.
     *
     * @param bridge the DataModellingBridge for WASM operations; must not be null
     * @throws IllegalArgumentException if bridge is null
     */
    public DataModellingPipeline(DataModellingBridge bridge) {
        if (bridge == null) {
            throw new IllegalArgumentException("DataModellingBridge must not be null");
        }
        this.bridge = bridge;
        this.stageResults = new HashMap<>();
        this.stagingDatabase = new StagingDatabase(bridge);
        this.closed = false;
        log.info("DataModellingPipeline initialized");
    }

    /**
     * Executes a specific pipeline stage with the given configuration.
     *
     * <p>Stages must generally be executed in order (INGEST → INFER → REFINE → MAP → EXPORT),
     * though individual stages can be re-run for testing or refinement.</p>
     *
     * @param stage the pipeline stage to execute; must not be null
     * @param config stage-specific configuration; must not be null and must match stage type
     * @return stage result (type depends on stage); never null
     * @throws DataModellingException if execution fails
     * @throws IllegalArgumentException if config type doesn't match stage
     * @throws IllegalStateException if pipeline is closed
     */
    public Object executeStage(PipelineStage stage, Object config) {
        checkNotClosed();
        if (stage == null) {
            throw new IllegalArgumentException("PipelineStage must not be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("Config must not be null");
        }

        validateConfigType(stage, config);

        try {
            Object result = switch (stage) {
                case INGEST -> executeIngestStage((IngestConfig) config);
                case INFER -> executeInferStage((InferenceConfig) config);
                case REFINE -> executeRefineStage((RefineConfig) config);
                case MAP -> executeMapStage((MappingConfig) config);
                case EXPORT -> executeExportStage((ExportConfig) config);
            };

            stageResults.put(stage, result);
            log.info("Completed pipeline stage: {}", stage.getStageName());
            return result;

        } catch (Exception e) {
            log.error("Pipeline stage {} failed: {}", stage.getStageName(), e.getMessage(), e);
            throw new DataModellingException(
                    "Pipeline stage " + stage.getStageName() + " failed: " + e.getMessage(),
                    DataModellingException.ErrorKind.EXECUTION_ERROR, e);
        }
    }

    /**
     * Executes the full pipeline sequentially from INGEST through EXPORT.
     *
     * <p>This convenience method runs all 5 stages in order, passing data between stages.</p>
     *
     * @param ingestConfig configuration for INGEST stage; must not be null
     * @param inferenceConfig configuration for INFER stage; must not be null
     * @param refineConfig configuration for REFINE stage; may be null (uses defaults)
     * @param mappingConfig configuration for MAP stage; must not be null
     * @param exportConfig configuration for EXPORT stage; must not be null
     * @return final export result; never null
     * @throws DataModellingException if any stage fails
     * @throws IllegalStateException if pipeline is closed
     */
    public ExportResult executeFullPipeline(
            IngestConfig ingestConfig,
            InferenceConfig inferenceConfig,
            RefineConfig refineConfig,
            MappingConfig mappingConfig,
            ExportConfig exportConfig) {

        checkNotClosed();

        log.info("Starting full pipeline execution");

        // Stage 1: INGEST
        IngestResult ingestResult = (IngestResult) executeStage(PipelineStage.INGEST, ingestConfig);

        // Stage 2: INFER
        InferenceResult inferResult = (InferenceResult) executeStage(PipelineStage.INFER, inferenceConfig);

        // Stage 3: REFINE (optional, use defaults if config is null)
        RefineResult refineResult;
        if (refineConfig != null) {
            refineResult = (RefineResult) executeStage(PipelineStage.REFINE, refineConfig);
        } else {
            refineResult = new RefineResult(inferResult.getInferredSchema(), false);
            stageResults.put(PipelineStage.REFINE, refineResult);
            log.info("Skipped REFINE stage (no config provided)");
        }

        // Stage 4: MAP
        // Update mapping config with refined schema if available
        mappingConfig.setSourceSchema(refineResult.getRefinedSchema());
        MappingResult mapResult = (MappingResult) executeStage(PipelineStage.MAP, mappingConfig);

        // Stage 5: EXPORT
        ExportResult exportResult = (ExportResult) executeStage(PipelineStage.EXPORT, exportConfig);

        log.info("Full pipeline execution completed successfully");
        return exportResult;
    }

    /**
     * Returns the result of a previously executed stage.
     *
     * @param stage the pipeline stage; must not be null
     * @return the stage result, or null if not yet executed
     */
    public Object getStageResult(PipelineStage stage) {
        checkNotClosed();
        return stageResults.get(stage);
    }

    /**
     * Returns all stage results collected so far.
     *
     * @return unmodifiable map of stage → result
     */
    public Map<PipelineStage, Object> getAllResults() {
        checkNotClosed();
        return Map.copyOf(stageResults);
    }

    /**
     * Returns the staging database for advanced operations (profiling, validation, etc.).
     *
     * @return the StagingDatabase; never null
     */
    public StagingDatabase getStagingDatabase() {
        checkNotClosed();
        return stagingDatabase;
    }

    /**
     * Closes the pipeline and all associated resources.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        try {
            stagingDatabase.close();
            log.info("DataModellingPipeline closed");
        } finally {
            closed = true;
        }
    }

    /**
     * Checks that the pipeline is not closed.
     *
     * @throws IllegalStateException if closed
     */
    private void checkNotClosed() {
        if (closed) {
            throw new IllegalStateException("DataModellingPipeline is closed");
        }
    }

    /**
     * Validates that config type matches stage expectations.
     *
     * @param stage the pipeline stage; must not be null
     * @param config the configuration object; must not be null
     * @throws IllegalArgumentException if types don't match
     */
    private void validateConfigType(PipelineStage stage, Object config) {
        Class<?> expectedClass = stage.getConfigClass();
        if (!expectedClass.isInstance(config)) {
            throw new IllegalArgumentException(
                    "Stage " + stage.getStageName() + " expects config of type " +
                    expectedClass.getSimpleName() + " but got " +
                    config.getClass().getSimpleName());
        }
    }

    // ── Stage Implementations ──────────────────────────────────────────────

    /**
     * Executes the INGEST stage.
     *
     * <p>Ingests raw data from the configured source into the staging database.</p>
     */
    private IngestResult executeIngestStage(IngestConfig config) {
        String sourceType = config.getSourceType();

        return switch (sourceType) {
            case "json" -> stagingDatabase.ingestFromJson(
                    config.getSourcePath(), // Simplified: assume sourcePath contains data
                    config);
            case "csv" -> stagingDatabase.ingestFromCsv(
                    config.getSourcePath(),
                    config);
            case "database", "iceberg", "parquet" -> throw new UnsupportedOperationException(
                    "Data source type '" + sourceType + "' requires WASM bridge support for remote data ingestion. "
                    + "Implement via DataModellingBridge.ingest" + capitalize(sourceType) + "Data() when SDK exposes it.");
            default -> throw new IllegalArgumentException("Unknown source type: " + sourceType);
        };
    }

    /**
     * Executes the INFER stage.
     *
     * <p>Infers schema structure from ingested data with type detection and constraint analysis.</p>
     */
    private InferenceResult executeInferStage(InferenceConfig config) {
        IngestResult priorIngest = (IngestResult) stageResults.get(PipelineStage.INGEST);
        if (priorIngest == null) {
            throw new IllegalStateException("INFER stage requires INGEST stage to be executed first");
        }

        throw new UnsupportedOperationException(
                "Schema inference requires WASM bridge support. "
                + "Implement via DataModellingBridge.inferSchemaFromStaging(stagingLocation, config) "
                + "when SDK exposes it. "
                + "Expected return: InferenceResult with inferredSchema, columnCount, and confidence score.");
    }

    /**
     * Executes the REFINE stage.
     *
     * <p>Refines inferred schema with LLM enhancement, manual rules, or pattern detection.</p>
     */
    private RefineResult executeRefineStage(RefineConfig config) {
        InferenceResult priorInference = (InferenceResult) stageResults.get(PipelineStage.INFER);
        if (priorInference == null) {
            throw new IllegalStateException("REFINE stage requires INFER stage to be executed first");
        }

        throw new UnsupportedOperationException(
                "Schema refinement requires WASM bridge support. "
                + "Implement via DataModellingBridge.refineSchema(schema, config) "
                + "when SDK exposes it. "
                + "Expected return: RefineResult with refinedSchema and confidence score.");
    }

    /**
     * Executes the MAP stage.
     *
     * <p>Maps source schema fields to target schema with confidence scoring and transformation generation.</p>
     */
    private MappingResult executeMapStage(MappingConfig config) {
        RefineResult priorRefine = (RefineResult) stageResults.get(PipelineStage.REFINE);
        if (priorRefine == null) {
            throw new IllegalStateException("MAP stage requires REFINE stage to be executed first");
        }

        throw new UnsupportedOperationException(
                "Field mapping requires WASM bridge support. "
                + "Implement via DataModellingBridge.mapSchemas(sourceSchema, targetSchema, config) "
                + "when SDK exposes it. "
                + "Expected return: MappingResult with fieldMappings and transformationScript.");
    }

    /**
     * Executes the EXPORT stage.
     *
     * <p>Exports final schema and transformations to the target format and location.</p>
     */
    private ExportResult executeExportStage(ExportConfig config) {
        MappingResult priorMapping = (MappingResult) stageResults.get(PipelineStage.MAP);
        if (priorMapping == null) {
            throw new IllegalStateException("EXPORT stage requires MAP stage to be executed first");
        }

        throw new UnsupportedOperationException(
                "Schema export requires WASM bridge support. "
                + "Implement via DataModellingBridge.exportSchema(schema, config) "
                + "when SDK exposes it. "
                + "Expected return: ExportResult with exportedContent in the target format.");
    }

    /**
     * Capitalizes first letter of a string.
     *
     * @param s the string to capitalize; must not be null
     * @return capitalized string
     */
    private String capitalize(String s) {
        if (s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    @Override
    public String toString() {
        return "DataModellingPipeline{" +
                "stages_completed=" + stageResults.size() +
                ", closed=" + closed +
                '}';
    }
}
