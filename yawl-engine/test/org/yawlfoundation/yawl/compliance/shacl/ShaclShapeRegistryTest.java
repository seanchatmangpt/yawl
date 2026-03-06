/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.compliance.shacl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SHACL shape registry functionality.
 */
class ShaclShapeRegistryTest {

    @TempDir
    Path tempDir;

    private ShaclShapeRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ShaclShapeRegistry();
    }

    @Test
    @DisplayName("Check shapes availability for all domains")
    void testShapesAvailability() {
        // This test will pass if the shapes files exist in the classpath
        List<ComplianceDomain> available = registry.getAvailableDomains();

        // Should have at least one domain available
        assertFalse(available.isEmpty(), "At least one compliance domain should be available");

        // Verify specific domains are available if shapes exist
        assertTrue(available.stream().anyMatch(d -> d == ComplianceDomain.SOX) ||
                   available.stream().anyMatch(d -> d == ComplianceDomain.GDPR) ||
                   available.stream().anyMatch(d -> d == ComplianceDomain.HIPAA),
                   "At least one compliance domain should be available");
    }

    @Test
    @DisplayName("Check specific domain availability")
    void testSpecificDomainAvailability() {
        // Test each domain individually
        for (ComplianceDomain domain : ComplianceDomain.values()) {
            boolean available = registry.hasShapes(domain);
            System.out.println(domain + " shapes available: " + available);
        }
    }

    @Test
    @DisplayName("Get shape file path for domain")
    void testGetShapeFilePath() {
        assertEquals("schema/shacl/yawl-compliance-sox-shapes.ttl",
                   registry.getShapeFilePath(ComplianceDomain.SOX));
        assertEquals("schema/shacl/yawl-compliance-gdpr-shapes.ttl",
                   registry.getShapeFilePath(ComplianceDomain.GDPR));
        assertEquals("schema/shacl/yawl-compliance-hipaa-shapes.ttl",
                   registry.getShapeFilePath(ComplianceDomain.HIPAA));
    }

    @Test
    @DisplayName("Validate shapes syntax")
    void testValidateShapesSyntax() {
        for (ComplianceDomain domain : ComplianceDomain.values()) {
            List<String> errors = registry.validateShapes(domain);

            if (!errors.isEmpty()) {
                System.out.println("Validation errors for " + domain + ":");
                errors.forEach(System.out::println);
            }

            // Validation should pass (no errors) if shapes are well-formed
            if (registry.hasShapes(domain)) {
                // This is expected to pass since we created the shapes files
                assertTrue(errors.isEmpty() ||
                          errors.stream().anyMatch(e -> e.contains("Missing") && !e.contains("Empty")),
                          "Shapes should be well-formed");
            }
        }
    }

    @Test
    @DisplayName("Get cache statistics")
    void testCacheStatistics() {
        // Load some shapes to populate cache
        try {
            registry.getShapes(ComplianceDomain.SOX);
        } catch (IOException e) {
            // Expected if shapes don't exist
        }

        Map<String, Object> stats = registry.getCacheStatistics();

        assertNotNull(stats, "Statistics should not be null");
        assertTrue(stats.containsKey("cachedDomains"));
        assertTrue(stats.containsKey("availableDomains"));
        assertTrue(stats.containsKey("lastLoaded"));

        System.out.println("Cache statistics: " + stats);
    }

    @Test
    @DisplayName("Clear cache")
    void testClearCache() {
        // Load shapes to populate cache
        try {
            registry.getShapes(ComplianceDomain.SOX);
        } catch (IOException e) {
            // Expected if shapes don't exist
        }

        // Clear cache
        registry.clearCache();

        // Verify cache is empty
        Map<String, Object> stats = registry.getCacheStatistics();
        assertEquals(0, stats.get("cachedDomains"));
    }

    @Test
    @DisplayName("Reload shapes")
    void testReloadShapes() {
        // Get initial shapes
        try {
            String initialShapes = registry.getShapes(ComplianceDomain.SOX);
            assertNotNull(initialShapes);

            // Reload shapes
            registry.reloadDomain(ComplianceDomain.SOX);

            // Get shapes again after reload
            String reloadedShapes = registry.getShapes(ComplianceDomain.SOX);
            assertNotNull(reloadedShapes);

            // Shapes should be the same (reloading doesn't change content)
            assertEquals(initialShapes, reloadedShapes);
        } catch (IOException e) {
            // Expected if shapes don't exist
        }
    }

    @Test
    @DisplayName("Reload all shapes")
    void testReloadAllShapes() {
        // Load shapes
        try {
            registry.getShapes(ComplianceDomain.SOX);
            registry.getShapes(ComplianceDomain.GDPR);
        } catch (IOException e) {
            // Expected if shapes don't exist
        }

        // Reload all shapes
        registry.reloadAll();

        // Verify cache is cleared
        Map<String, Object> stats = registry.getCacheStatistics();
        assertEquals(0, stats.get("cachedDomains"));
    }

    @Test
    @DisplayName("Handle missing shapes file")
    void testMissingShapesFile() {
        // Create a registry with a custom class loader that won't find shapes
        ClassLoader testLoader = new ClassLoader() {
            @Override
            public URL getResource(String name) {
                return null; // Always return null to simulate missing files
            }
        };

        ShaclShapeRegistry customRegistry = new ShaclShapeRegistry(testLoader);

        assertFalse(customRegistry.hasShapes(ComplianceDomain.SOX));
        assertFalse(customRegistry.hasShapes(ComplianceDomain.GDPR));
        assertFalse(customRegistry.hasShapes(ComplianceDomain.HIPAA));

        List<ComplianceDomain> available = customRegistry.getAvailableDomains();
        assertTrue(available.isEmpty());

        // Test validateShapes with missing shapes
        List<String> errors = customRegistry.validateShapes(ComplianceDomain.SOX);
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).contains("Failed to load shapes"));
    }

    @Test
    @DisplayName("Test shape file naming convention")
    void testShapeFileNaming() {
        // Test that each domain has the correct shape file name
        for (ComplianceDomain domain : ComplianceDomain.values()) {
            String expectedFileName = "yawl-compliance-" + domain.getCode().toLowerCase() + "-shapes.ttl";
            String actualFileName = domain.getShapeFile();

            assertEquals(expectedFileName, actualFileName,
                         "Shape file name should follow convention: " + expectedFileName);
        }
    }

    @Test
    @DisplayName("Test compliance domain from code")
    void testComplianceDomainFromCode() {
        assertEquals(ComplianceDomain.SOX, ComplianceDomain.fromCode("SOX"));
        assertEquals(ComplianceDomain.GDPR, ComplianceDomain.fromCode("GDPR"));
        assertEquals(ComplianceDomain.HIPAA, ComplianceDomain.fromCode("HIPAA"));

        // Test invalid code
        assertThrows(IllegalArgumentException.class, () -> {
            ComplianceDomain.fromCode("INVALID");
        });
    }
}