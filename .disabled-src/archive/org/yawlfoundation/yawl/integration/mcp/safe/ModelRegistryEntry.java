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

package org.yawlfoundation.yawl.integration.mcp.safe;

import java.time.Instant;
import java.util.List;

/**
 * Model registry entry for SAFe Responsible AI governance.
 *
 * Records a versioned AI model with its dataset lineage, model card,
 * evaluation suite reference, and promotion status. Used by SafeMcpToolRegistry
 * to track model versions through the promotion pipeline.
 *
 * @param modelId unique identifier for the model (e.g., "text-classifier-v1")
 * @param version semantic version of this model (e.g., "1.2.3")
 * @param datasetLineage dataset origins and transformations leading to model
 * @param modelCard model behavior documentation (e.g., performance, limitations)
 * @param evalSuiteRef reference to evaluation test suite (e.g., URI or test ID)
 * @param versionHash content hash of model artifact for integrity verification
 * @param responsibleAiEvidence list of ResponsibleAiReceipt evidence items
 * @param status current promotion status (CANDIDATE, PROMOTED, ROLLED_BACK, REJECTED)
 * @param registeredAt timestamp when entry was registered
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record ModelRegistryEntry(
    String modelId,
    String version,
    String datasetLineage,
    String modelCard,
    String evalSuiteRef,
    String versionHash,
    List<String> responsibleAiEvidence,
    PromotionStatus status,
    Instant registeredAt
) {
    /**
     * Promotion lifecycle status for AI models.
     */
    public enum PromotionStatus {
        /** Model is a candidate; not yet promoted to production */
        CANDIDATE,
        /** Model is promoted and actively serving */
        PROMOTED,
        /** Model was rolled back to prior version */
        ROLLED_BACK,
        /** Model was explicitly rejected */
        REJECTED
    }
}
