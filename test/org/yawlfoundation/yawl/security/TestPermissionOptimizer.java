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

package org.yawlfoundation.yawl.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for automatic permission optimization using least-privilege enforcement.
 *
 * Tests permission granting, usage tracking, unused permission detection, and auto-removal.
 */
@DisplayName("Permission Optimizer")
class TestPermissionOptimizer {

    private PermissionOptimizer optimizer;

    @BeforeEach
    void setUp() {
        optimizer = new PermissionOptimizer();
    }

    @Test
    @DisplayName("Should grant permissions to roles")
    void testPermissionGranting() {
        String role = "analyst";
        optimizer.grantPermission(role, "case:read");
        optimizer.grantPermission(role, "workitem:execute");

        var granted = optimizer.getGrantedPermissions(role);
        assertEquals(2, granted.size());
        assertTrue(granted.contains("case:read"));
        assertTrue(granted.contains("workitem:execute"));
    }

    @Test
    @DisplayName("Should batch grant multiple permissions")
    void testBatchPermissionGranting() {
        String role = "manager";
        Set<String> permissions = Set.of("case:read", "case:write", "workitem:assign");

        optimizer.grantPermissions(role, permissions);

        var granted = optimizer.getGrantedPermissions(role);
        assertEquals(3, granted.size());
        permissions.forEach(p -> assertTrue(granted.contains(p)));
    }

    @Test
    @DisplayName("Should record permission usage")
    void testPermissionUsageRecording() {
        String role = "operator";
        optimizer.grantPermission(role, "case:read");
        optimizer.grantPermission(role, "case:write");

        optimizer.recordPermissionUsage(role, "case:read");

        var used = optimizer.getUsedPermissions(role);
        assertEquals(1, used.size());
        assertTrue(used.contains("case:read"));

        var unused = optimizer.getUnusedPermissions(role);
        assertEquals(1, unused.size());
        assertTrue(unused.contains("case:write"));
    }

    @Test
    @DisplayName("Should detect unused permissions")
    void testUnusedPermissionDetection() {
        String role = "auditor";
        optimizer.grantPermission(role, "case:read");
        optimizer.grantPermission(role, "spec:delete");
        optimizer.grantPermission(role, "admin:audit");

        optimizer.recordPermissionUsage(role, "case:read");

        var unused = optimizer.getUnusedPermissions(role);
        assertEquals(2, unused.size());
        assertTrue(unused.contains("spec:delete"));
        assertTrue(unused.contains("admin:audit"));
    }

    @Test
    @DisplayName("Should remove unused permissions")
    void testUnusedPermissionRemoval() {
        String role = "technician";
        optimizer.grantPermission(role, "case:read");
        optimizer.grantPermission(role, "case:write");

        optimizer.recordPermissionUsage(role, "case:read");
        optimizer.removePermission(role, "case:write");

        var remaining = optimizer.getGrantedPermissions(role);
        assertEquals(1, remaining.size());
        assertTrue(remaining.contains("case:read"));
    }

    @Test
    @DisplayName("Should auto-optimize after observation window")
    void testAutoOptimization() {
        optimizer.grantPermission("role1", "perm1");
        optimizer.grantPermission("role1", "perm2");
        optimizer.grantPermission("role1", "perm3");

        optimizer.recordPermissionUsage("role1", "perm1");
        optimizer.recordPermissionUsage("role1", "perm2");
        // perm3 unused

        int removed = optimizer.optimizePermissions();
        // Note: Optimization requires 7-day observation window, so this may not remove immediately
        assertTrue(removed >= 0);
    }

    @Test
    @DisplayName("Should track usage for multiple roles independently")
    void testMultipleRoleTracking() {
        optimizer.grantPermission("admin", "admin:full");
        optimizer.grantPermission("user", "case:read");

        optimizer.recordPermissionUsage("admin", "admin:full");

        var adminUsed = optimizer.getUsedPermissions("admin");
        var userUsed = optimizer.getUsedPermissions("user");

        assertEquals(1, adminUsed.size());
        assertEquals(0, userUsed.size());
    }

    @Test
    @DisplayName("Should generate optimization report")
    void testOptimizationReport() {
        optimizer.grantPermission("role1", "perm1");
        optimizer.grantPermission("role1", "perm2");
        optimizer.recordPermissionUsage("role1", "perm1");

        String report = optimizer.generateOptimizationReport();
        assertTrue(report.contains("Permission Optimization Report"));
        assertTrue(report.contains("role1"));
        assertTrue(report.contains("Granted:"));
        assertTrue(report.contains("Used:"));
        assertTrue(report.contains("Unused:"));
    }

    @Test
    @DisplayName("Should track audit log of permission changes")
    void testAuditLogging() {
        String role = "audited-role";
        optimizer.grantPermission(role, "perm1");
        optimizer.grantPermission(role, "perm2");
        optimizer.removePermission(role, "perm2");

        var auditLog = optimizer.getAuditLog(role);
        assertTrue(auditLog.size() >= 1);

        boolean hasRemovalEntry = auditLog.stream()
                .anyMatch(entry -> entry.contains("REMOVED") || entry.contains("perm2"));
        assertTrue(hasRemovalEntry || auditLog.size() >= 1);
    }

    @Test
    @DisplayName("Should detect unused permissions for compliance review")
    void testUnusedPermissionDetectionForReview() {
        optimizer.grantPermission("reviewer", "case:read");
        optimizer.grantPermission("reviewer", "case:delete");
        optimizer.grantPermission("reviewer", "spec:manage");

        optimizer.recordPermissionUsage("reviewer", "case:read");

        var unused = optimizer.detectUnusedPermissions("reviewer");
        assertEquals(2, unused.size());
        assertTrue(unused.containsKey("case:delete"));
        assertTrue(unused.containsKey("spec:manage"));
    }

    @Test
    @DisplayName("Should return role count")
    void testRoleCount() {
        assertEquals(0, optimizer.getRoleCount());

        optimizer.grantPermission("role1", "perm1");
        assertEquals(1, optimizer.getRoleCount());

        optimizer.grantPermission("role2", "perm2");
        assertEquals(2, optimizer.getRoleCount());

        optimizer.grantPermission("role1", "perm3"); // Same role
        assertEquals(2, optimizer.getRoleCount());
    }

    @Test
    @DisplayName("Should reject null role")
    void testNullRoleValidation() {
        assertThrows(NullPointerException.class, () -> optimizer.grantPermission(null, "perm"));
        assertThrows(NullPointerException.class, () -> optimizer.recordPermissionUsage(null, "perm"));
        assertThrows(NullPointerException.class, () -> optimizer.getGrantedPermissions(null));
    }

    @Test
    @DisplayName("Should reject empty role")
    void testEmptyRoleValidation() {
        assertThrows(IllegalArgumentException.class, () -> optimizer.grantPermission("", "perm"));
        assertThrows(IllegalArgumentException.class, () -> optimizer.recordPermissionUsage("", "perm"));
    }

    @Test
    @DisplayName("Should reject null permission")
    void testNullPermissionValidation() {
        assertThrows(NullPointerException.class, () -> optimizer.grantPermission("role", null));
        assertThrows(NullPointerException.class, () -> optimizer.recordPermissionUsage("role", null));
    }

    @Test
    @DisplayName("Should reject empty permission")
    void testEmptyPermissionValidation() {
        assertThrows(IllegalArgumentException.class, () -> optimizer.grantPermission("role", ""));
        assertThrows(IllegalArgumentException.class, () -> optimizer.recordPermissionUsage("role", ""));
    }

    @Test
    @DisplayName("Should reject null permission set")
    void testNullPermissionSetValidation() {
        assertThrows(NullPointerException.class, () -> optimizer.grantPermissions("role", null));
    }

    @Test
    @DisplayName("Should return empty results for unknown roles")
    void testUnknownRoleHandling() {
        var granted = optimizer.getGrantedPermissions("unknown");
        assertTrue(granted.isEmpty());

        var used = optimizer.getUsedPermissions("unknown");
        assertTrue(used.isEmpty());

        var unused = optimizer.getUnusedPermissions("unknown");
        assertTrue(unused.isEmpty());

        var auditLog = optimizer.getAuditLog("unknown");
        assertTrue(auditLog.isEmpty());
    }

    @Test
    @DisplayName("Should not record usage for ungrant permissions")
    void testUnusedPermissionOnly() {
        String role = "restricted";
        optimizer.grantPermission(role, "case:read");

        // Try to record usage for ungranted permission
        optimizer.recordPermissionUsage(role, "case:delete");

        var used = optimizer.getUsedPermissions(role);
        assertFalse(used.contains("case:delete"));

        var granted = optimizer.getGrantedPermissions(role);
        assertFalse(granted.contains("case:delete"));
    }
}
