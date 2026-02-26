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

package org.yawlfoundation.yawl.pi;

import org.yawlfoundation.yawl.integration.eventsourcing.WorkflowEventStore;
import org.yawlfoundation.yawl.integration.zai.ZaiService;
import org.yawlfoundation.yawl.observatory.rdf.WorkflowDNAOracle;
import org.yawlfoundation.yawl.pi.predictive.PredictiveModelRegistry;

import java.nio.file.Path;

/**
 * Configuration record for ProcessIntelligenceFacade initialization.
 *
 * <p>Immutable container for all dependencies required by the PI facade.
 * Passed to facade constructors to enable dependency injection.
 *
 * @param eventStore Workflow event storage and retrieval (required)
 * @param dnaOracle Workflow DNA oracle for pattern analysis (required)
 * @param modelRegistry ONNX model registry for predictive inference (required)
 * @param zaiService Z.AI service for RAG and chat capabilities (optional, nullable)
 * @param modelDirectory Directory path for monitoring ONNX model files (required)
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public record PIFacadeConfig(
    WorkflowEventStore eventStore,
    WorkflowDNAOracle dnaOracle,
    PredictiveModelRegistry modelRegistry,
    ZaiService zaiService,
    Path modelDirectory
) {

    /**
     * Construct configuration with validation.
     *
     * @throws NullPointerException if any required parameter is null
     */
    public PIFacadeConfig {
        if (eventStore == null) throw new NullPointerException("eventStore is required");
        if (dnaOracle == null) throw new NullPointerException("dnaOracle is required");
        if (modelRegistry == null) throw new NullPointerException("modelRegistry is required");
        if (modelDirectory == null) throw new NullPointerException("modelDirectory is required");
    }
}
