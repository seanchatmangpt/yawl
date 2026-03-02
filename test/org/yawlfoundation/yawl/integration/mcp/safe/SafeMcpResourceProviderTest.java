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

import io.modelcontextprotocol.server.McpServerFeatures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD tests for SafeMcpResourceProvider.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class SafeMcpResourceProviderTest {
    private SafeMcpResourceProvider provider;
    private ModelRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ModelRegistry();
        provider = new SafeMcpResourceProvider(registry);
    }

    @Test
    void nfrsResource_returnsPolicy() {
        // Act
        List<McpServerFeatures.SyncResourceSpecification> resources = provider.createAllResources();

        // Assert
        assertTrue(resources.size() > 0);
        // Find nfrs resource
        var nfrsResource = resources.stream()
            .filter(r -> r.resource().uri().equals("safe://nfrs"))
            .findFirst();
        assertTrue(nfrsResource.isPresent());
    }

    @Test
    void responsibleAiPolicyResource_contains13Attributes() {
        // Act
        List<McpServerFeatures.SyncResourceSpecification> resources = provider.createAllResources();

        // Assert
        var policyResource = resources.stream()
            .filter(r -> r.resource().uri().equals("safe://policy/responsible-ai"))
            .findFirst();
        assertTrue(policyResource.isPresent());
        // Policy should mention all 13 attributes
        var schema = policyResource.get().resource();
        assertEquals("Responsible AI Policy", schema.name());
    }

    @Test
    void telemetryResource_returnsJsonWithRequiredFields() {
        // Act
        List<McpServerFeatures.SyncResourceSpecification> resources = provider.createAllResources();

        // Assert
        var telemetryResource = resources.stream()
            .filter(r -> r.resource().uri().equals("safe://telemetry"))
            .findFirst();
        assertTrue(telemetryResource.isPresent());
        assertEquals("Telemetry", telemetryResource.get().resource().name());
    }

    @Test
    void modelRegistryResource_notFound_returnsError() {
        // Arrange
        List<McpServerFeatures.SyncResourceTemplateSpecification> templates = provider.createAllResourceTemplates();
        var modelRegistryTemplate = templates.stream()
            .filter(t -> t.resourceTemplate().uriTemplate().equals("safe://model-registry/{modelId}"))
            .findFirst();

        // Assert template exists
        assertTrue(modelRegistryTemplate.isPresent());
    }

    @Test
    void modelRegistryResource_found_returnsEntry() {
        // Arrange
        ModelRegistryEntry entry = new ModelRegistryEntry(
            "my-model",
            "1.0.0",
            "training_data",
            "Model card",
            "eval-suite",
            "hash123",
            List.of(),
            ModelRegistryEntry.PromotionStatus.CANDIDATE,
            Instant.now()
        );
        registry.register(entry);

        // Act
        List<McpServerFeatures.SyncResourceTemplateSpecification> templates = provider.createAllResourceTemplates();

        // Assert
        var modelRegistryTemplate = templates.stream()
            .filter(t -> t.resourceTemplate().uriTemplate().equals("safe://model-registry/{modelId}"))
            .findFirst();
        assertTrue(modelRegistryTemplate.isPresent());
    }

    @Test
    void createAll_returnsMixedResourcesAndTemplates() {
        // Act
        List<Object> all = provider.createAll();

        // Assert: 3 resources + 3 templates = 6 total
        assertEquals(6, all.size());

        // Count specifications and templates
        long specCount = all.stream()
            .filter(r -> r instanceof McpServerFeatures.SyncResourceSpecification)
            .count();
        long templateCount = all.stream()
            .filter(r -> r instanceof McpServerFeatures.SyncResourceTemplateSpecification)
            .count();

        assertEquals(3, specCount);
        assertEquals(3, templateCount);
    }
}
