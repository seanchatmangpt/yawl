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

/**
 * Enumeration of the five pipeline stages in the data-modelling-sdk pipeline execution.
 *
 * <p>The pipeline processes data through these sequential stages:</p>
 * <ol>
 *   <li><strong>INGEST</strong>: Load raw data from sources (JSON, CSV, databases)</li>
 *   <li><strong>INFER</strong>: Analyze data and infer schema structure</li>
 *   <li><strong>REFINE</strong>: Refine inferred schema with LLM or manual rules</li>
 *   <li><strong>MAP</strong>: Map source schema to target schema</li>
 *   <li><strong>EXPORT</strong>: Export results to ODCS, SQL, or other formats</li>
 * </ol>
 *
 * <p>Each stage has associated configuration and result types accessible via
 * {@link #getConfigClass()} and {@link #getResultClass()}.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public enum PipelineStage {
    /**
     * Stage 1: Ingest raw data from sources.
     * Config: {@link IngestConfig}, Result: {@link IngestResult}
     */
    INGEST("ingest", IngestConfig.class, IngestResult.class),

    /**
     * Stage 2: Infer schema from data.
     * Config: {@link InferenceConfig}, Result: {@link InferenceResult}
     */
    INFER("infer", InferenceConfig.class, InferenceResult.class),

    /**
     * Stage 3: Refine inferred schema.
     * Config: {@link RefineConfig}, Result: {@link RefineResult}
     */
    REFINE("refine", RefineConfig.class, RefineResult.class),

    /**
     * Stage 4: Map source to target schema.
     * Config: {@link MappingConfig}, Result: {@link MappingResult}
     */
    MAP("map", MappingConfig.class, MappingResult.class),

    /**
     * Stage 5: Export results.
     * Config: {@link ExportConfig}, Result: {@link ExportResult}
     */
    EXPORT("export", ExportConfig.class, ExportResult.class);

    private final String stageName;
    private final Class<?> configClass;
    private final Class<?> resultClass;

    PipelineStage(String stageName, Class<?> configClass, Class<?> resultClass) {
        this.stageName = stageName;
        this.configClass = configClass;
        this.resultClass = resultClass;
    }

    /**
     * Returns the stage name used in pipeline orchestration.
     *
     * @return the stage name; never null
     */
    public String getStageName() {
        return stageName;
    }

    /**
     * Returns the configuration class for this stage.
     *
     * @return the config class; never null
     */
    public Class<?> getConfigClass() {
        return configClass;
    }

    /**
     * Returns the result class for this stage.
     *
     * @return the result class; never null
     */
    public Class<?> getResultClass() {
        return resultClass;
    }

    /**
     * Returns the stage enum value for the given stage name (case-insensitive).
     *
     * @param stageName the stage name; must not be null
     * @return the stage enum value; never null
     * @throws IllegalArgumentException if no stage matches the name
     */
    public static PipelineStage fromName(String stageName) {
        if (stageName == null) {
            throw new IllegalArgumentException("stageName must not be null");
        }
        for (PipelineStage stage : values()) {
            if (stage.stageName.equalsIgnoreCase(stageName)) {
                return stage;
            }
        }
        throw new IllegalArgumentException("Unknown pipeline stage: " + stageName);
    }
}
