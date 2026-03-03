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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SHACL validation result functionality.
 */
class ShaclValidationResultTest {

    @Test
    @DisplayName("Create successful validation result")
    void testCreateSuccess() {
        Map<String, Object> metadata = Map.of("key", "value");
        ShaclValidationResult result = ShaclValidationResult.success(
            ComplianceDomain.SOX, "test-spec", 50L, metadata);

        assertTrue(result.valid());
        assertEquals(ComplianceDomain.SOX, result.complianceDomain());
        assertEquals("test-spec", result.target());
        assertEquals(0, result.getViolationCount());
        assertEquals(ViolationSeverity.NONE, result.getHighestSeverity());
        assertEquals(50L, result.validationTime());
        assertNotNull(result.timestamp());
        assertEquals(metadata, result.metadata());

        // Convert to JSON
        String json = result.toJson();
        assertTrue(json.contains("\"valid\":true"));
        assertTrue(json.contains("\"domain\":\"SOX\""));
        assertTrue(json.contains("\"violations\":0"));
    }

    @Test
    @DisplayName("Create failed validation result")
    void testCreateFailure() {
        Map<String, Object> metadata = Map.of("key", "value");
        ShaclViolation violation = ShaclViolation.high("test-node", "test-constraint",
            "Test violation", "/test/path");

        ShaclValidationResult result = ShaclValidationResult.failure(
            ComplianceDomain.GDPR, "test-spec", List.of(violation), 75L, metadata);

        assertFalse(result.valid());
        assertEquals(ComplianceDomain.GDPR, result.complianceDomain());
        assertEquals("test-spec", result.target());
        assertEquals(1, result.getViolationCount());
        assertEquals(ViolationSeverity.HIGH, result.getHighestSeverity());
        assertEquals(75L, result.validationTime());
        assertNotNull(result.timestamp());
        assertEquals(metadata, result.metadata());

        // Verify violation details
        assertEquals(1, result.getViolations().size());
        assertEquals(violation, result.getViolations().get(0));
        assertEquals(1, result.getViolationsBySeverity(ViolationSeverity.HIGH).size());
        assertEquals(1, result.getViolationsByFocusNode("test-node").size());

        // Convert to JSON
        String json = result.toJson();
        assertTrue(json.contains("\"valid\":false"));
        assertTrue(json.contains("\"domain\":\"GDPR\""));
        assertTrue(json.contains("\"violations\":1"));
    }

    @Test
    @DisplayName("Get violation count")
    void testGetViolationCount() {
        // Test with no violations
        ShaclValidationResult result1 = ShaclValidationResult.success(
            ComplianceDomain.HIPAA, "test-spec", 30L, Map.of());
        assertEquals(0, result1.getViolationCount());

        // Test with violations
        ShaclViolation violation1 = ShaclViolation.medium("node1", "constraint1", "message1", "/path1");
        ShaclViolation violation2 = ShaclViolation.low("node2", "constraint2", "message2", "/path2");

        ShaclValidationResult result2 = ShaclValidationResult.failure(
            ComplianceDomain.HIPAA, "test-spec", List.of(violation1, violation2), 60L, Map.of());
        assertEquals(2, result2.getViolationCount());
    }

    @Test
    @DisplayName("Get highest severity")
    void testGetHighestSeverity() {
        // Test with no violations
        ShaclValidationResult result1 = ShaclValidationResult.success(
            ComplianceDomain.SOX, "test-spec", 25L, Map.of());
        assertEquals(ViolationSeverity.NONE, result1.getHighestSeverity());

        // Test with mixed severity violations
        ShaclViolation low = ShaclViolation.low("node1", "constraint1", "message1", "/path1");
        ShaclViolation high = ShaclViolation.high("node2", "constraint2", "message2", "/path2");
        ShaclViolation medium = ShaclViolation.medium("node3", "constraint3", "message3", "/path3");

        ShaclValidationResult result2 = ShaclValidationResult.failure(
            ComplianceDomain.GDPR, "test-spec", List.of(low, high, medium), 45L, Map.of());
        assertEquals(ViolationSeverity.HIGH, result2.getHighestSeverity());

        // Test with single severity
        ShaclValidationResult result3 = ShaclValidationResult.failure(
            ComplianceDomain.SOX, "test-spec", List.of(low, low), 30L, Map.of());
        assertEquals(ViolationSeverity.LOW, result3.getHighestSeverity());
    }

    @Test
    @DisplayName("Get violations by severity")
    void testGetViolationsBySeverity() {
        ShaclViolation low = ShaclViolation.low("node1", "constraint1", "message1", "/path1");
        ShaclViolation high = ShaclViolation.high("node2", "constraint2", "message2", "/path2");
        ShaclViolation medium = ShaclViolation.medium("node3", "constraint3", "message3", "/path3");
        ShaclViolation low2 = ShaclViolation.low("node4", "constraint4", "message4", "/path4");

        ShaclValidationResult result = ShaclValidationResult.failure(
            ComplianceDomain.HIPAA, "test-spec",
            List.of(low, high, medium, low2), 55L, Map.of());

        // Get low severity violations
        List<ShaclViolation> lowViolations = result.getViolationsBySeverity(ViolationSeverity.LOW);
        assertEquals(2, lowViolations.size());
        assertTrue(lowViolations.contains(low));
        assertTrue(lowViolations.contains(low2));

        // Get high severity violations
        List<ShaclViolation> highViolations = result.getViolationsBySeverity(ViolationSeverity.HIGH);
        assertEquals(1, highViolations.size());
        assertEquals(high, highViolations.get(0));

        // Get medium severity violations
        List<ShaclViolation> mediumViolations = result.getViolationsBySeverity(ViolationSeverity.MEDIUM);
        assertEquals(1, mediumViolations.size());
        assertEquals(medium, mediumViolations.get(0));

        // Get non-existent severity
        List<ShaclViolation> noneViolations = result.getViolationsBySeverity(ViolationSeverity.NONE);
        assertTrue(noneViolations.isEmpty());
    }

    @Test
    @DisplayName("Get violations by focus node")
    void testGetViolationsByFocusNode() {
        ShaclViolation node1 = ShaclViolation.high("focus-node1", "constraint1", "message1", "/path1");
        ShaclViolation node2 = ShaclViolation.medium("focus-node2", "constraint2", "message2", "/path2");
        ShaclViolation node1Again = ShaclViolation.low("focus-node1", "constraint3", "message3", "/path3");

        ShaclValidationResult result = ShaclValidationResult.failure(
            ComplianceDomain.SOX, "test-spec", List.of(node1, node2, node1Again), 40L, Map.of());

        // Get violations for focus-node1
        List<ShaclViolation> node1Violations = result.getViolationsByFocusNode("focus-node1");
        assertEquals(2, node1Violations.size());
        assertTrue(node1Violations.contains(node1));
        assertTrue(node1Violations.contains(node1Again));

        // Get violations for focus-node2
        List<ShaclViolation> node2Violations = result.getViolationsByFocusNode("focus-node2");
        assertEquals(1, node2Violations.size());
        assertEquals(node2, node2Violations.get(0));

        // Get violations for non-existent node
        List<ShaclViolation> noViolations = result.getViolationsByFocusNode("non-existent");
        assertTrue(noViolations.isEmpty());
    }

    @Test
    @DisplayName("Violations severity ordering")
    void testViolationSeverityOrdering() {
        // Test severity comparison
        ViolationSeverity[] severities = ViolationSeverity.values();

        // Check ordering
        assertEquals(0, severities[0].getLevel()); // NONE
        assertEquals(1, severities[1].getLevel()); // LOW
        assertEquals(2, severities[2].getLevel()); // MEDIUM
        assertEquals(3, severities[3].getLevel()); // HIGH

        // Test isHigherThan
        assertTrue(ViolationSeverity.HIGH.isHigherThan(ViolationSeverity.MEDIUM));
        assertTrue(ViolationSeverity.MEDIUM.isHigherThan(ViolationSeverity.LOW));
        assertTrue(ViolationSeverity.LOW.isHigherThan(ViolationSeverity.NONE));

        // Test isLowerThan
        assertTrue(ViolationSeverity.NONE.isLowerThan(ViolationSeverity.LOW));
        assertTrue(ViolationSeverity.LOW.isLowerThan(ViolationSeverity.MEDIUM));
        assertTrue(ViolationSeverity.MEDIUM.isLowerThan(ViolationSeverity.HIGH));

        // Test isAtLeast
        assertTrue(ViolationSeverity.HIGH.isAtLeast(ViolationSeverity.MEDIUM));
        assertTrue(ViolationSeverity.MEDIUM.isAtLeast(ViolationSeverity.MEDIUM));
        assertFalse(ViolationSeverity.LOW.isAtLeast(ViolationSeverity.HIGH));

        // Test isAtMost
        assertTrue(ViolationSeverity.NONE.isAtMost(ViolationSeverity.MEDIUM));
        assertTrue(ViolationSeverity.MEDIUM.isAtMost(ViolationSeverity.MEDIUM));
        assertFalse(ViolationSeverity.HIGH.isAtMost(ViolationSeverity.LOW));
    }

    @Test
    @DisplayName("Violation factory methods")
    void testViolationFactoryMethods() {
        // Test high severity factory
        ShaclViolation high = ShaclViolation.high("node1", "constraint1", "message1", "/path1");
        assertEquals(ViolationSeverity.HIGH, high.severity());
        assertEquals("node1", high.focusNode());
        assertEquals("constraint1", high.constraint());
        assertEquals("message1", high.message());
        assertEquals("/path1", high.path());
        assertNull(high.value());
        assertNull(high.expectedValue());
        assertTrue(high.details().isEmpty());

        // Test medium severity factory
        ShaclViolation medium = ShaclViolation.medium("node2", "constraint2", "message2", "/path2");
        assertEquals(ViolationSeverity.MEDIUM, medium.severity());

        // Test low severity factory
        ShaclViolation low = ShaclViolation.low("node3", "constraint3", "message3", "/path3");
        assertEquals(ViolationSeverity.LOW, low.severity());

        // Test value factory
        ShaclViolation withValue = ShaclViolation.withValue(
            ViolationSeverity.HIGH, "node4", "constraint4", "message4", "/path4", "actual", "expected");
        assertEquals(ViolationSeverity.HIGH, withValue.severity());
        assertEquals("actual", withValue.value());
        assertEquals("expected", withValue.expectedValue());

        // Test details factory
        Map<String, Object> details = Map.of("detail1", "value1", "detail2", "value2");
        ShaclViolation withDetails = ShaclViolation.withDetails(
            ViolationSeverity.MEDIUM, "node5", "constraint5", "message5", "/path5", details);
        assertEquals(ViolationSeverity.MEDIUM, withDetails.severity());
        assertEquals(2, withDetails.details().size());
        assertEquals("value1", withDetails.details().get("detail1"));
        assertEquals("value2", withDetails.details().get("detail2"));
    }

    @Test
    @DisplayName("Violation toString method")
    void testViolationToString() {
        ShaclViolation violation = ShaclViolation.high("focus-node", "constraint", "message", "/path");
        String toString = violation.toString();

        assertTrue(toString.contains("[HIGH]"));
        assertTrue(toString.contains("focus-node"));
        assertTrue(toString.contains("message"));
        assertTrue(toString.contains("/path"));
    }
}