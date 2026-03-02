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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD tests for ModelRegistry.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class ModelRegistryTest {
    private ModelRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ModelRegistry();
    }

    @Test
    void register_storesEntry() {
        // Arrange
        ModelRegistryEntry entry = new ModelRegistryEntry(
            "text-classifier",
            "1.0.0",
            "training_data_v2",
            "Classifies text into 5 categories",
            "eval-suite-001",
            "abc123",
            List.of(),
            ModelRegistryEntry.PromotionStatus.CANDIDATE,
            Instant.now()
        );

        // Act
        registry.register(entry);

        // Assert
        var result = registry.getEntry("text-classifier");
        assertTrue(result.isPresent());
        assertEquals("1.0.0", result.get().version());
    }

    @Test
    void promote_requiresResponsibleAiReceipt() {
        // Arrange
        ModelRegistryEntry entry = new ModelRegistryEntry(
            "text-classifier",
            "1.0.0",
            "training_data_v2",
            "Classifies text",
            "eval-suite-001",
            "abc123",
            List.of(),
            ModelRegistryEntry.PromotionStatus.CANDIDATE,
            Instant.now()
        );
        registry.register(entry);

        // Act & Assert: null receipt throws
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> registry.promote("text-classifier", "1.0.0", null));
        assertTrue(ex.getMessage().contains("ResponsibleAiReceipt proof"));

        // Blank receipt also throws
        ex = assertThrows(IllegalStateException.class,
            () -> registry.promote("text-classifier", "1.0.0", "  "));
        assertTrue(ex.getMessage().contains("ResponsibleAiReceipt proof"));
    }

    @Test
    void promote_withProof_returnsPromoted() {
        // Arrange
        ModelRegistryEntry entry = new ModelRegistryEntry(
            "text-classifier",
            "1.0.0",
            "training_data_v2",
            "Classifies text",
            "eval-suite-001",
            "abc123",
            List.of(),
            ModelRegistryEntry.PromotionStatus.CANDIDATE,
            Instant.now()
        );
        registry.register(entry);

        // Act
        ModelRegistryEntry promoted = registry.promote("text-classifier", "1.0.0",
            "{\"evaluation\": \"passed\", \"metrics\": {\"accuracy\": 0.95}}");

        // Assert
        assertEquals(ModelRegistryEntry.PromotionStatus.PROMOTED, promoted.status());
        assertEquals(1, promoted.responsibleAiEvidence().size());
        assertTrue(promoted.responsibleAiEvidence().get(0).contains("evaluation"));
    }

    @Test
    void rollback_revertsToCandidate() {
        // Arrange
        Instant now = Instant.now();
        ModelRegistryEntry v1 = new ModelRegistryEntry(
            "text-classifier",
            "1.0.0",
            "training_data_v1",
            "v1",
            "eval-001",
            "hash1",
            List.of(),
            ModelRegistryEntry.PromotionStatus.CANDIDATE,
            now
        );
        registry.register(v1);

        // Promote v1
        registry.promote("text-classifier", "1.0.0",
            "{\"evaluation\": \"passed\"}");

        // Register and promote v2
        ModelRegistryEntry v2 = new ModelRegistryEntry(
            "text-classifier",
            "1.1.0",
            "training_data_v2",
            "v2",
            "eval-002",
            "hash2",
            List.of(),
            ModelRegistryEntry.PromotionStatus.CANDIDATE,
            now.plusSeconds(60)
        );
        registry.register(v2);
        registry.promote("text-classifier", "1.1.0",
            "{\"evaluation\": \"passed\"}");

        // Act
        ModelRegistryEntry rolledBack = registry.rollback("text-classifier");

        // Assert
        assertEquals("1.0.0", rolledBack.version());
        assertEquals(ModelRegistryEntry.PromotionStatus.CANDIDATE, rolledBack.status());
    }

    @Test
    void getHistory_orderedByRegisteredAt() {
        // Arrange
        Instant now = Instant.now();
        for (int i = 0; i < 3; i++) {
            ModelRegistryEntry entry = new ModelRegistryEntry(
                "text-classifier",
                "1." + i + ".0",
                "training_data_v" + i,
                "v" + i,
                "eval-" + i,
                "hash" + i,
                List.of(),
                ModelRegistryEntry.PromotionStatus.CANDIDATE,
                now.plusSeconds(i * 60)
            );
            registry.register(entry);
        }

        // Act
        List<ModelRegistryEntry> history = registry.getHistory("text-classifier");

        // Assert
        assertEquals(3, history.size());
        // Newest first
        assertEquals("1.2.0", history.get(0).version());
        assertEquals("1.1.0", history.get(1).version());
        assertEquals("1.0.0", history.get(2).version());
    }
}
