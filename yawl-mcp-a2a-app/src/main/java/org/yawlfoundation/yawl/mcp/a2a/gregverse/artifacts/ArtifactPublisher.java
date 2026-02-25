/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.mcp.a2a.gregverse.artifacts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Artifact Publisher for GregVerse marketplace artifacts.
 *
 * <p>This component handles the publishing of service artifacts including therapy plans,
 * progress reports, and other documents to the marketplace. It provides a standardized
 * interface for artifact management with support for different formats and storage backends.</p>
 *
 * <h2>Publishing Workflow</h2>
 * <ol>
 *   <li>Create artifact with metadata</li>
 *   <li>Validate artifact content</li>
 *   <li>Store in configured backend</li>
 *   <li>Update marketplace catalog</li>
 *   <li>Notify stakeholders</li>
 * </ol>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class ArtifactPublisher {

    private final Logger logger = LoggerFactory.getLogger(ArtifactPublisher.class);

    /**
     * Publish an artifact to the marketplace.
     *
     * @param artifact the artifact to publish
     * @throws ArtifactPublicationException if publication fails
     */
    public void publishArtifact(Artifact artifact) throws ArtifactPublicationException {
        logger.info("Publishing artifact: {}", artifact.getClass().getSimpleName());

        try {
            // Validate artifact
            validateArtifact(artifact);

            // Store artifact
            storeArtifact(artifact);

            // Update marketplace catalog
            updateMarketplaceCatalog(artifact);

            // Notify stakeholders
            notifyStakeholders(artifact);

            logger.info("Successfully published artifact: {}", artifact.getId());

        } catch (Exception e) {
            logger.error("Failed to publish artifact: {}", e.getMessage());
            throw new ArtifactPublicationException(
                "Failed to publish artifact: " + e.getMessage(), e);
        }
    }

    /**
     * Validate artifact before publication.
     */
    private void validateArtifact(Artifact artifact) throws ArtifactValidationException {
        if (artifact == null) {
            throw new ArtifactValidationException("Artifact cannot be null");
        }

        if (artifact.getId() == null || artifact.getId().isEmpty()) {
            artifact.setId(UUID.randomUUID().toString());
        }

        if (artifact.getCreatedAt() == null) {
            artifact.setCreatedAt(LocalDateTime.now());
        }

        // Validate specific artifact types
        if (artifact instanceof TherapyPlan) {
            validateTherapyPlan((TherapyPlan) artifact);
        } else if (artifact instanceof ProgressReport) {
            validateProgressReport((ProgressReport) artifact);
        }
    }

    /**
     * Store artifact in configured backend.
     */
    private void storeArtifact(Artifact artifact) {
        // In a real implementation, this would store in:
        // - File system
        // - Database
        // - Cloud storage
        // - Distributed ledger
        logger.debug("Storing artifact: {}", artifact.getId());
    }

    /**
     * Update marketplace catalog with new artifact.
     */
    private void updateMarketplaceCatalog(Artifact artifact) {
        // In a real implementation, this would:
        // - Update search index
        // - Update catalog metadata
        // - Invalidate caches
        logger.debug("Updating marketplace catalog for artifact: {}", artifact.getId());
    }

    /**
     * Notify stakeholders about new artifact.
     */
    private void notifyStakeholders(Artifact artifact) {
        // In a real implementation, this would:
        // - Send notifications to relevant parties
        // - Update activity feeds
        // - Trigger webhook events
        logger.debug("Notifying stakeholders about artifact: {}", artifact.getId());
    }

    /**
     * Validate therapy plan artifact.
     */
    private void validateTherapyPlan(TherapyPlan plan) throws ArtifactValidationException {
        if (plan.getServiceId() == null || plan.getServiceId().isEmpty()) {
            throw new ArtifactValidationException("Therapy plan must have service ID");
        }

        if (plan.getClientId() == null || plan.getClientId().isEmpty()) {
            throw new ArtifactValidationException("Therapy plan must have client ID");
        }

        if (plan.getServiceType() == null || plan.getServiceType().isEmpty()) {
            throw new ArtifactValidationException("Therapy plan must have service type");
        }

        if (plan.getContent() == null || plan.getContent().isEmpty()) {
            throw new ArtifactValidationException("Therapy plan must have content");
        }
    }

    /**
     * Validate progress report artifact.
     */
    private void validateProgressReport(ProgressReport report) throws ArtifactValidationException {
        if (report.getServiceId() == null || report.getServiceId().isEmpty()) {
            throw new ArtifactValidationException("Progress report must have service ID");
        }

        if (report.getClientId() == null || report.getClientId().isEmpty()) {
            throw new ArtifactValidationException("Progress report must have client ID");
        }

        if (report.getContent() == null || report.getContent().isEmpty()) {
            throw new ArtifactValidationException("Progress report must have content");
        }
    }
}

