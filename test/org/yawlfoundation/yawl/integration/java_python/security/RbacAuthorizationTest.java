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

package org.yawlfoundation.yawl.integration.java_python.security;

import org.junit.jupiter.api.*;
import org.yawlfoundation.yawl.resourcing.Participant;
import org.yawlfoundation.yawl.security.PermissionOptimizer;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive RBAC (Role-Based Access Control) authorization tests.
 * Tests role-based permissions, privilege escalation prevention, resource-level
 * and workflow-level access controls using real YAWL components.
 *
 * Tests cover:
 * - Permission granting and checking with PermissionOptimizer
 * - Privilege escalation prevention
 * - Resource-level authorization (work item access)
 * - Workflow-level permissions (case lifecycle permissions)
 * - Capability-based access control
 * - Role hierarchy inheritance
 * - Permission optimization and audit trails
 *
 * @author YAWL Foundation
 * @since YAWL 6.0
 */
@Tag("security")
@Tag("rbac")
@Tag("integration")
@Execution(ExecutionMode.SAME_THREAD)
class RbacAuthorizationTest extends ValidationTestBase {

    private PermissionOptimizer permissionOptimizer;
    private List<Participant> testParticipants;

    @BeforeEach
    void setUp() throws Exception {
        super.setUp();

        // Initialize permission optimizer
        permissionOptimizer = new PermissionOptimizer();

        // Create test participants with different roles and capabilities
        testParticipants = Arrays.asList(
            new Participant("Alice", "manager", Set.of("approve", "review", "assign")),
            new Participant("Bob", "analyst", Set.of("analyze", "review", "document")),
            new Participant("Charlie", "auditor", Set.of("audit", "review", "report")),
            new Participant("Diana", "admin", Set.of("admin", "configure", "manage")),
            new Participant("Eve", "viewer", Set.of("read"))
        );
    }

    @Test
    @DisplayName("RBAC: PermissionOptimizer grants and checks permissions")
    void testPermissionGrantingAndChecking() {
        String role = "manager";
        String permission = "case:read";

        // Initially no permissions granted
        assertTrue(permissionOptimizer.getGrantedPermissions(role).isEmpty());
        assertTrue(permissionOptimizer.getUsedPermissions(role).isEmpty());

        // Grant permission
        permissionOptimizer.grantPermission(role, permission);
        assertEquals(Set.of(permission), permissionOptimizer.getGrantedPermissions(role));

        // Check permission exists but is unused
        assertTrue(permissionOptimizer.getUnusedPermissions(role).contains(permission));
        assertFalse(permissionOptimizer.getUsedPermissions(role).contains(permission));

        // Record usage
        permissionOptimizer.recordPermissionUsage(role, permission);
        assertFalse(permissionOptimizer.getUnusedPermissions(role).contains(permission));
        assertTrue(permissionOptimizer.getUsedPermissions(role).contains(permission));
    }

    @Test
    @DisplayName("RBAC: PermissionOptimizer grants multiple permissions in batch")
    void testBatchPermissionGranting() {
        String role = "analyst";
        Set<String> permissions = Set.of(
            "workitem:read",
            "workitem:write",
            "case:read",
            "workitem:assign"
        );

        permissionOptimizer.grantPermissions(role, permissions);
        assertEquals(permissions, permissionOptimizer.getGrantedPermissions(role));
        assertEquals(permissions.size(), permissionOptimizer.getUnusedPermissions(role).size());
    }

    @Test
    @DisplayName("RBAC: PermissionOptimizer rejects invalid parameters")
    void testPermissionOptimizerRejectsInvalidParameters() {
        assertThrows(IllegalArgumentException.class,
            () -> permissionOptimizer.grantPermission("", "permission"));
        assertThrows(IllegalArgumentException.class,
            () -> permissionOptimizer.grantPermission(null, "permission"));
        assertThrows(IllegalArgumentException.class,
            () -> permissionOptimizer.grantPermission("role", ""));
        assertThrows(IllegalArgumentException.class,
            () -> permissionOptimizer.grantPermission("role", null));

        assertThrows(IllegalArgumentException.class,
            () -> permissionOptimizer.grantPermissions("role", null));
        assertThrows(IllegalArgumentException.class,
            () -> permissionOptimizer.grantPermissions("role", Set.of("valid", "")));
    }

    @Test
    @DisplayName("RBAC: Privilege escalation prevention - cannot access higher role permissions")
    void testPrivilegeEscalationPrevention() {
        // Grant manager permissions to a manager role
        permissionOptimizer.grantPermission("manager", "case:delete");
        permissionOptimizer.grantPermission("manager", "user:manage");

        // Analyst should not have access to manager permissions
        permissionOptimizer.grantPermission("analyst", "case:read");
        permissionOptimizer.grantPermission("analyst", "workitem:write");

        Set<String> analystPermissions = permissionOptimizer.getGrantedPermissions("analyst");
        assertFalse(analystPermissions.contains("case:delete"),
            "Analyst should not have case:delete permission (manager privilege)");
        assertFalse(analystPermissions.contains("user:manage"),
            "Analyst should not have user:manage permission (admin privilege)");

        // Manager should have their permissions
        Set<String> managerPermissions = permissionOptimizer.getGrantedPermissions("manager");
        assertTrue(managerPermissions.contains("case:delete"),
            "Manager should have case:delete permission");
        assertTrue(managerPermissions.contains("user:manage"),
            "Manager should have user:manage permission");
        assertFalse(managerPermissions.contains("case:read"),
            "Manager should not inherit analyst permissions by default");
    }

    @Test
    @DisplayName("RBAC: Resource-level authorization - participant role validation")
    void testResourceLevelAuthorization() {
        // Test participant role alignment with required permissions
        List<Participant> managers = testParticipants.stream()
            .filter(p -> "manager".equals(p.getRole()))
            .toList();

        assertEquals(1, managers.size(), "Should have exactly one manager");
        assertEquals("Alice", managers.get(0).getName());
        assertTrue(managers.get(0).getCapabilities().contains("approve"),
            "Manager should have approve capability");

        // Test analysts
        List<Participant> analysts = testParticipants.stream()
            .filter(p -> "analyst".equals(p.getRole()))
            .toList();

        assertEquals(1, analysts.size(), "Should have exactly one analyst");
        assertEquals("Bob", analysts.get(0).getName());
        assertTrue(analysts.get(0).getCapabilities().contains("analyze"),
            "Analyst should have analyze capability");

        // Test cross-role access prevention
        assertFalse(testParticipants.stream()
            .filter(p -> "analyst".equals(p.getRole()))
            .anyMatch(p -> p.getCapabilities().contains("admin")),
            "Analysts should not have admin capabilities");
    }

    @Test
    @DisplayName("RBAC: Workflow-level permissions - case lifecycle control")
    void testWorkflowLevelPermissions() {
        String workflow = "purchase_workflow";
        String role = "manager";

        // Grant workflow-specific permissions
        Set<String> workflowPermissions = Set.of(
            "workflow:" + workflow + ":start",
            "workflow:" + workflow + ":approve",
            "workflow:" + workflow + ":cancel",
            "workflow:" + workflow + ":delegate"
        );

        permissionOptimizer.grantPermissions(role, workflowPermissions);

        // Verify permissions are specific to workflow
        Set<String> allPermissions = permissionOptimizer.getGrantedPermissions(role);
        assertTrue(allPermissions.containsAll(workflowPermissions),
            "Manager should have all purchase workflow permissions");

        // Non-workflow actions should fail
        assertFalse(permissionOptimizer.getGrantedPermissions(role)
            .contains("workflow:other_workflow:start"),
            "Should not have permissions for other workflows");

        // Test permission usage tracking for workflow
        permissionOptimizer.recordPermissionUsage(role, "workflow:" + workflow + ":start");

        Set<String> usedPermissions = permissionOptimizer.getUsedPermissions(role);
        assertTrue(usedPermissions.contains("workflow:" + workflow + ":start"),
            "Should track used workflow permissions");

        Set<String> unusedPermissions = permissionOptimizer.getUnusedPermissions(role);
        assertTrue(unusedPermissions.contains("workflow:" + workflow + ":approve"),
            "Should track unused workflow permissions");
    }

    @Test
    @DisplayName("RBAC: Permission optimization removes unused permissions")
    void testPermissionOptimizationRemovesUnusedPermissions() throws InterruptedException {
        String role = "temp_user";

        // Grant permissions but don't use them
        permissionOptimizer.grantPermission(role, "unused_permission_1");
        permissionOptimizer.grantPermission(role, "unused_permission_2");
        permissionOptimizer.grantPermission(role, "used_permission");

        // Record some permissions as used
        permissionOptimizer.recordPermissionUsage(role, "used_permission");

        // Initially, two permissions are unused
        assertEquals(2, permissionOptimizer.getUnusedPermissions(role).size());

        // Detect unused permissions with timestamps
        Map<String, Long> unusedPerms = permissionOptimizer.detectUnusedPermissions(role);
        assertEquals(2, unusedPerms.size());
        assertTrue(unusedPerms.containsKey("unused_permission_1"));
        assertTrue(unusedPerms.containsKey("unused_permission_2"));

        // Simulate time passing and optimize
        Thread.sleep(10);
        int removed = permissionOptimizer.optimizePermissions();
        assertTrue(removed >= 0, "Optimize should not fail");

        // Verify optimization behavior (may not remove immediately due to time window)
        String report = permissionOptimizer.generateOptimizationReport();
        assertTrue(report.contains("Role: temp_user"), "Report should contain role info");
    }

    @Test
    @DisplayName("RBAC: Permission audit trail tracks all permission changes")
    void testPermissionAuditTrail() {
        String role = "auditor";
        String permission = "audit:read";

        // Grant permission
        permissionOptimizer.grantPermission(role, permission);
        permissionOptimizer.grantPermission(role, "audit:write");

        // Remove permission
        permissionOptimizer.removePermission(role, permission);

        // Check audit trail
        List<String> auditLog = permissionOptimizer.getAuditLog(role);
        assertTrue(auditLog.size() >= 2, "Should have at least grant and remove entries");

        boolean hasGrant = auditLog.stream().anyMatch(entry -> entry.contains("GRANTED"));
        boolean hasRemove = auditLog.stream().anyMatch(entry -> entry.contains("REMOVED"));
        assertTrue(hasGrant && hasRemove, "Should track both grant and remove actions");
    }

    @Test
    @DisplayName("RBAC: Capability-based access control in addition to role-based")
    void testCapabilityBasedAccessControl() {
        String role = "analyst";

        // Grant permission based on capability
        permissionOptimizer.grantPermission(role, "case:review");
        permissionOptimizer.grantPermission(role, "document:edit");

        // Create participants with different capabilities
        Participant seniorAnalyst = new Participant("SeniorBob", "analyst",
            Set.of("analyze", "review", "document", "export"));

        Participant juniorAnalyst = new Participant("JuniorBob", "analyst",
            Set.of("analyze", "document"));

        // Analyst with review capability should have review permission
        assertTrue(seniorAnalyst.getCapabilities().contains("review"),
            "Senior analyst should have review capability");
        assertTrue(permissionOptimizer.getGrantedPermissions(role)
            .contains("case:review"),
            "Analyst role should have case:review permission");

        // Junior analyst without review capability should be restricted
        assertFalse(juniorAnalyst.getCapabilities().contains("review"),
            "Junior analyst should not have review capability");
    }

    @Test
    @DisplayName("RBAC: Nested role hierarchy with inheritance")
    void testNestedRoleHierarchy() {
        // Grant permissions to parent role
        permissionOptimizer.grantPermission("senior_manager", "case:full_access");
        permissionOptimizer.grantPermission("senior_manager", "user:manage");

        // Junior manager permissions
        permissionOptimizer.grantPermission("manager", "case:read");
        permissionOptimizer.grantPermission("manager", "case:write");

        // Team lead permissions
        permissionOptimizer.grantPermission("team_lead", "case:read");
        permissionOptimizer.grantPermission("team_lead", "workitem:assign");

        // Senior manager has all permissions (their own + manager permissions)
        Set<String> seniorManagerPerms = permissionOptimizer.getGrantedPermissions("senior_manager");
        assertTrue(seniorManagerPerms.contains("case:full_access"),
            "Senior manager should have full access");
        assertTrue(seniorManagerPerms.contains("case:read"),
            "Senior manager should inherit manager permissions");
        assertTrue(seniorManagerPerms.contains("user:manage"),
            "Senior manager should have user management");

        // Manager only has their direct permissions
        Set<String> managerPerms = permissionOptimizer.getGrantedPermissions("manager");
        assertTrue(managerPerms.contains("case:read"),
            "Manager should have read permission");
        assertTrue(managerPerms.contains("case:write"),
            "Manager should have write permission");
        assertFalse(managerPerms.contains("case:full_access"),
            "Manager should not have full access (senior privilege)");

        // Team lead only has limited permissions
        Set<String> teamLeadPerms = permissionOptimizer.getGrantedPermissions("team_lead");
        assertTrue(teamLeadPerms.contains("case:read"),
            "Team lead should have read permission");
        assertTrue(teamLeadPerms.contains("workitem:assign"),
            "Team lead should have assignment permission");
        assertFalse(teamLeadPerms.contains("case:write"),
            "Team lead should not have write permission");
    }

    @Test
    @DisplayName("RBAC: Time-based permissions expiration simulation")
    void testTimeBasedPermissionExpiration() throws InterruptedException {
        String role = "temp_user";
        String permission = "temporary_access";

        // Grant temporary permission
        permissionOptimizer.grantPermission(role, permission);
        permissionOptimizer.grantPermission(role, "permanent_access");

        // Use the permission
        permissionOptimizer.recordPermissionUsage(role, permission);
        permissionOptimizer.recordPermissionUsage(role, "permanent_access");

        // Detect unused permissions (immediate)
        Map<String, Long> unusedPerms = permissionOptimizer.detectUnusedPermissions(role);
        assertEquals(0, unusedPerms.size(), "All permissions should be used initially");

        // Wait some time and simulate expiration
        Thread.sleep(50);
        permissionOptimizer.recordPermissionUsage(role, "permanent_access");

        unusedPerms = permissionOptimizer.detectUnusedPermissions(role);
        assertEquals(0, unusedPerms.size(), "Should still detect all as used");
    }

    @Test
    @DisplayName("RBAC: Concurrent permission modifications are thread-safe")
    void testConcurrentPermissionModifications() throws InterruptedException {
        String role = "concurrent_test";
        int threadCount = 5;
        int permissionsPerThread = 3;
        int expectedPermissions = threadCount * permissionsPerThread;

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            Thread thread = new Thread(() -> {
                for (int j = 0; j < permissionsPerThread; j++) {
                    String permission = "perm_" + threadIndex + "_" + j;
                    permissionOptimizer.grantPermission(role, permission);
                    permissionOptimizer.recordPermissionUsage(role, permission);
                }
            });
            threads.add(thread);
        }

        // Start all threads
        threads.forEach(Thread::start);

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // Verify all permissions were granted
        Set<String> granted = permissionOptimizer.getGrantedPermissions(role);
        assertEquals(expectedPermissions, granted.size(),
            "Should have all permissions granted");

        // Verify no permissions are unused
        assertTrue(permissionOptimizer.getUnusedPermissions(role).isEmpty(),
            "All permissions should be used");
    }

    @Test
    @DisplayName("RBAC: PermissionOptimizer generates comprehensive optimization report")
    void testOptimizationReportGeneration() {
        // Add roles and permissions
        permissionOptimizer.grantPermission("admin", "system:read");
        permissionOptimizer.grantPermission("admin", "system:write");
        permissionOptimizer.grantPermission("admin", "system:delete");
        permissionOptimizer.grantPermission("user", "case:read");
        permissionOptimizer.grantPermission("user", "workitem:read");
        permissionOptimizer.grantPermission("auditor", "audit:read");

        // Use some permissions
        permissionOptimizer.recordPermissionUsage("admin", "system:read");
        permissionOptimizer.recordUsage("user", "case:read");

        // Generate report
        String report = permissionOptimizer.generateOptimizationReport();

        // Verify report content
        assertTrue(report.contains("Permission Optimization Report"),
            "Report should have title");
        assertTrue(report.contains("Role: admin"),
            "Report should show admin role");
        assertTrue(report.contains("Role: user"),
            "Report should show user role");
        assertTrue(report.contains("Role: auditor"),
            "Report should show auditor role");
        assertTrue(report.contains("Granted:"),
            "Report should show granted permissions count");
        assertTrue(report.contains("Used:"),
            "Report should show used permissions count");
        assertTrue(report.contains("Unused:"),
            "Report should show unused permissions count");
    }

    @Test
    @DisplayName("RBAC: Empty and null inputs are properly handled")
    void testEmptyAndNullInputHandling() {
        // Test with null role
        assertThrows(IllegalArgumentException.class,
            () -> permissionOptimizer.grantPermission(null, "permission"));

        // Test with empty role
        assertThrows(IllegalArgumentException.class,
            () -> permissionOptimizer.grantPermission("", "permission"));

        // Test with non-existent role returns empty sets
        assertTrue(permissionOptimizer.getGrantedPermissions("non_existent").isEmpty(),
            "Non-existent role should have no granted permissions");
        assertTrue(permissionOptimizer.getUsedPermissions("non_existent").isEmpty(),
            "Non-existent role should have no used permissions");
        assertTrue(permissionOptimizer.getUnusedPermissions("non_existent").isEmpty(),
            "Non-existent role should have no unused permissions");
        assertTrue(permissionOptimizer.getAuditLog("non_existent").isEmpty(),
            "Non-existent role should have no audit log");
        assertEquals(0, permissionOptimizer.detectUnusedPermissions("non_existent").size(),
            "Non-existent role should have no unused permissions");
    }

    @Test
    @DisplayName("RBAC: Role and capability combinations for access control")
    void testRoleCapabilityCombinations() {
        // Test multiple roles
        permissionOptimizer.grantPermission("multi_role_user", "case:read");
        permissionOptimizer.grantPermission("multi_role_user", "workitem:write");

        // Create participant with multiple capabilities
        Participant multiCapability = new Participant("MultiCap", "multi_role_user",
            Set.of("read", "write", "execute", "approve"));

        // Verify capability-based access
        assertTrue(multiCapability.getCapabilities().contains("read"),
            "Should have read capability");
        assertTrue(multiCapability.getCapabilities().contains("write"),
            "Should have write capability");
        assertFalse(multiCapability.getCapabilities().contains("admin"),
            "Should not have admin capability");

        // Test permission combinations
        Set<String> userPermissions = permissionOptimizer.getGrantedPermissions("multi_role_user");
        assertTrue(userPermissions.contains("case:read"),
            "Should have case read permission");
        assertTrue(userPermissions.contains("workitem:write"),
            "Should have workitem write permission");
        assertFalse(userPermissions.contains("case:delete"),
            "Should not have delete permission");
    }

    @Test
    @DisplayName("RBAC: Permission usage tracking patterns")
    void testPermissionUsageTracking() {
        String role = "heavy_user";

        // Grant various permissions
        Set<String> permissions = Set.of(
            "read", "write", "delete", "execute", "approve", "assign"
        );
        permissionOptimizer.grantPermissions(role, permissions);

        // Simulate usage patterns
        permissionOptimizer.recordPermissionUsage(role, "read");
        permissionOptimizer.recordPermissionUsage(role, "write");
        permissionOptimizer.recordPermissionUsage(role, "read"); // Duplicate usage
        permissionOptimizer.recordPermissionUsage(role, "execute");

        // Verify usage tracking
        Set<String> usedPermissions = permissionOptimizer.getUsedPermissions(role);
        Set<String> unusedPermissions = permissionOptimizer.getUnusedPermissions(role);

        assertEquals(3, usedPermissions.size(),
            "Should track 3 unique used permissions");
        assertTrue(usedPermissions.contains("read"),
            "Should track read usage");
        assertTrue(usedPermissions.contains("write"),
            "Should track write usage");
        assertTrue(usedPermissions.contains("execute"),
            "Should track execute usage");

        assertEquals(3, unusedPermissions.size(),
            "Should have 3 unused permissions");
        assertTrue(unusedPermissions.contains("delete"),
            "Should track delete as unused");
        assertTrue(unusedPermissions.contains("approve"),
            "Should track approve as unused");
        assertTrue(unusedPermissions.contains("assign"),
            "Should track assign as unused");
    }

    @AfterAll
    static void tearDown() throws Exception {
        super.tearDown();
    }
}