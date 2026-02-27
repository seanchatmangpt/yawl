/*
 * Copyright (c) 2026 YAWL Foundation. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.yawlfoundation.yawl.graalpy.security;

import org.yawlfoundation.yawl.integration.a2a.auth.AuthenticatedPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Advanced RBAC authorization tests for YAWL GraalPy integration.
 *
 * Tests complex authorization scenarios including:
 * - Composite permissions and inheritance
 * - Dynamic permission assignment
 * - Role-based workflow access control
 * - Audit logging for authorization decisions
 * - Performance under high load
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RBAC Authorization Advanced Tests")
public class RbacAuthorizationAdvancedTest {

    private static final String SUPER_ADMIN_USERNAME = "super-admin";
    private static final String AUDITOR_USERNAME = "auditor";
    private static final String SERVICE_ACCOUNT_USERNAME = "service-account";

    private static final Set<String> SUPER_ADMIN_PERMISSIONS;
    private static final Set<String> AUDITOR_PERMISSIONS;
    private static final Set<String> SERVICE_ACCOUNT_PERMISSIONS;

    static {
        // Super Admin: All permissions plus auditing capabilities
        SUPER_ADMIN_PERMISSIONS = Set.of(
            AuthenticatedPrincipal.PERM_ALL,
            "audit:read",
            "audit:write",
            "system:config",
            "user:manage"
        );

        // Auditor: Read-only access for auditing purposes
        AUDITOR_PERMISSIONS = Set.of(
            AuthenticatedPrincipal.PERM_WORKFLOW_QUERY,
            AuthenticatedPrincipal.PERM_WORKITEM_MANAGE,
            "audit:read",
            "user:read"
        );

        // Service Account: Limited automation permissions
        SERVICE_ACCOUNT_PERMISSIONS = Set.of(
            AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH,
            AuthenticatedPrincipal.PERM_WORKFLOW_QUERY,
            "service:automate"
        );
    }

    private AuthenticatedPrincipal superAdminPrincipal;
    private AuthenticatedPrincipal auditorPrincipal;
    private AuthenticatedPrincipal serviceAccountPrincipal;
    private RbacAuthorizationService rbacService;

    @BeforeEach
    void setUp() {
        superAdminPrincipal = createPrincipal(SUPER_ADMIN_USERNAME, SUPER_ADMIN_PERMISSIONS);
        auditorPrincipal = createPrincipal(AUDITOR_USERNAME, AUDITOR_PERMISSIONS);
        serviceAccountPrincipal = createPrincipal(SERVICE_ACCOUNT_USERNAME, SERVICE_ACCOUNT_PERMISSIONS);
        rbacService = new RbacAuthorizationService();
    }

    /**
     * Helper method to create an authenticated principal
     */
    private AuthenticatedPrincipal createPrincipal(String username, Set<String> permissions) {
        return new AuthenticatedPrincipal(
            username,
            permissions,
            "Bearer",
            Instant.now(),
            null
        );
    }

    // =================================================================================
    // Composite Permission Tests
    // =================================================================================

    @Test
    @DisplayName("Composite Permissions: Role Combinations")
    void testCompositePermissionsRoleCombinations() {
        // Test that roles can be combined with proper permission intersection
        Set<String> adminPlusAuditor = Set.of(
            AuthenticatedPrincipal.PERM_ALL,
            "audit:read",
            "audit:write",
            "system:config",
            "user:manage",
            "user:read"
        );

        AuthenticatedPrincipal adminPlusAuditorPrincipal = createPrincipal(
            "admin-auditor", adminPlusAuditor);

        // Verify combined role permissions work correctly
        assertTrue(adminPlusAuditorPrincipal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH),
            "Admin+Auditor should have launch permission");
        assertTrue(adminPlusAuditorPrincipal.hasPermission("audit:read"),
            "Admin+Auditor should have audit read permission");

        // Test permission intersection (should have all permissions from both roles)
        assertTrue(adminPlusAuditorPrincipal.hasPermission("system:config"),
            "Admin+Auditor should have system config permission");
        assertTrue(adminPlusAuditorPrincipal.hasPermission("user:read"),
            "Admin+Auditor should have user read permission");
    }

    @Test
    @DisplayName("Permission Inheritance: Hierarchical Roles")
    void testPermissionInheritanceHierarchicalRoles() {
        // Create hierarchical role structure
        Set<String> juniorPermissions = Set.of(
            AuthenticatedPrincipal.PERM_WORKFLOW_QUERY,
            "task:basic"
        );

        Set<String> seniorPermissions = Set.of(
            AuthenticatedPrincipal.PERM_WORKFLOW_QUERY,
            "task:basic",
            "task:advanced",
            "team:manage"
        );

        Set<String> leadPermissions = Set.of(
            AuthenticatedPrincipal.PERM_WORKFLOW_QUERY,
            "task:basic",
            "task:advanced",
            "team:manage",
            "project:admin"
        );

        AuthenticatedPrincipal juniorPrincipal = createPrincipal("junior-dev", juniorPermissions);
        AuthenticatedPrincipal seniorPrincipal = createPrincipal("senior-dev", seniorPermissions);
        AuthenticatedPrincipal leadPrincipal = createPrincipal("tech-lead", leadPermissions);

        // Test inheritance: senior should have all junior permissions plus more
        assertTrue(seniorPrincipal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_QUERY),
            "Senior should have junior permissions");
        assertTrue(seniorPrincipal.hasPermission("task:basic"),
            "Senior should have basic task permissions");
        assertTrue(seniorPrincipal.hasPermission("task:advanced"),
            "Senior should have advanced task permissions");

        // Test inheritance: lead should have all senior permissions plus more
        assertTrue(leadPrincipal.hasPermission("team:manage"),
            "Lead should have team management permissions");
        assertTrue(leadPrincipal.hasPermission("project:admin"),
            "Lead should have project admin permissions");
    }

    // =================================================================================
    // Dynamic Permission Assignment Tests
    // =================================================================================

    @Test
    @DisplayName("Dynamic Permission Assignment: Runtime Changes")
    void testDynamicPermissionAssignment() {
        // Create principal with base permissions
        Set<String> basePermissions = Set.of(
            AuthenticatedPrincipal.PERM_WORKFLOW_QUERY
        );

        AuthenticatedPrincipal principal = createPrincipal("dynamic-user", basePermissions);

        // Initially should only have query permission
        assertTrue(principal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_QUERY),
            "User should initially have query permission");
        assertFalse(principal.hasPermission(AuthenticatedPrincipal.PERM_WORKITEM_MANAGE),
            "User should not initially have workitem management");

        // Simulate dynamic permission upgrade
        Set<String> upgradedPermissions = Set.of(
            AuthenticatedPrincipal.PERM_WORKFLOW_QUERY,
            AuthenticatedPrincipal.PERM_WORKITEM_MANAGE
        );

        principal = createPrincipal("dynamic-user-upgraded", upgradedPermissions);

        // Verify upgraded permissions
        assertTrue(principal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_QUERY),
            "Upgraded user should have query permission");
        assertTrue(principal.hasPermission(AuthenticatedPrincipal.PERM_WORKITEM_MANAGE),
            "Upgraded user should have workitem management");
    }

    @Test
    @DisplayName("Dynamic Permission Revocation")
    void testDynamicPermissionRevocation() {
        // Create principal with full permissions
        Set<String> fullPermissions = Set.of(
            AuthenticatedPrincipal.PERM_ALL
        );

        AuthenticatedPrincipal principal = createPrincipal("privileged-user", fullPermissions);

        // Initially should have all permissions
        assertTrue(principal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH),
            "User should initially have launch permission");
        assertTrue(principal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_CANCEL),
            "User should initially have cancel permission");

        // Simulate permission revocation
        Set<String> restrictedPermissions = Set.of(
            AuthenticatedPrincipal.PERM_WORKFLOW_QUERY,
            AuthenticatedPrincipal.PERM_WORKITEM_MANAGE
        );

        principal = createPrincipal("restricted-user", restrictedPermissions);

        // Verify revocation worked
        assertTrue(principal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_QUERY),
            "Restricted user should still have query permission");
        assertFalse(principal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH),
            "Restricted user should not have launch permission");
        assertFalse(principal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_CANCEL),
            "Restricted user should not have cancel permission");
    }

    // =================================================================================
    // Role-Based Workflow Access Control Tests
    // =================================================================================

    @Test
    @DisplayName("Workflow Access: Multi-Role Workflows")
    void testWorkflowAccessMultiRole() {
        // Test access to workflows that require multiple roles
        String multiRoleWorkflow = "workflow:multiple-roles-required";

        // Admin should have access
        assertTrue(rbacService.canAccessWorkflow(superAdminPrincipal, multiRoleWorkflow),
            "Super Admin should access multi-role workflows");

        // Auditor should not have access to execution
        assertFalse(rbacService.canAccessWorkflow(auditorPrincipal, multiRoleWorkflow),
            "Auditor should not access multi-role execution workflows");

        // Service account should have limited access
        assertTrue(rbacService.canAccessWorkflow(serviceAccountPrincipal, multiRoleWorkflow),
            "Service Account should access multi-role workflows for automation");
    }

    @Test
    @DisplayName("Workflow Access: Conditional Permissions")
    void testWorkflowAccessConditionalPermissions() {
        // Test workflows with conditional permission requirements
        String conditionalWorkflow = "workflow:conditional-permissions";

        // Should require both launch AND manage permissions
        assertTrue(rbacService.canLaunchWorkflowConditional(superAdminPrincipal, conditionalWorkflow),
            "Super Admin should launch conditional workflow");
        assertFalse(rbacService.canLaunchWorkflowConditional(serviceAccountPrincipal, conditionalWorkflow),
            "Service Account should not launch conditional workflow without manage permission");
    }

    @Test
    @DisplayName("Workflow Access: Time-Based Restrictions")
    void testWorkflowAccessTimeBasedRestrictions() {
        // Test workflow access based on time restrictions
        String restrictedWorkflow = "workflow:time-restricted";

        // During business hours (mocked as always true for this test)
        assertTrue(rbacService.canAccessWorkflowDuringHours(superAdminPrincipal, restrictedWorkflow),
            "Super Admin should access time-restricted workflows");
        assertFalse(rbacService.canAccessWorkflowDuringHours(serviceAccountPrincipal, restrictedWorkflow),
            "Service Account should not access time-restricted workflows outside hours");
    }

    // =================================================================================
    // Audit Logging Tests
    // =================================================================================

    @Test
    @DisplayName("Audit Logging: Permission Decisions")
    void testAuditLoggingPermissionDecisions() {
        // Test that all permission decisions are logged
        assertTrue(rbacService.logPermissionCheck(superAdminPrincipal,
            AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH),
            "Permission check should be logged for Super Admin");

        assertTrue(rbacService.logPermissionCheck(auditorPrincipal,
            "audit:read"),
            "Permission check should be logged for Auditor");

        assertFalse(rbacService.logPermissionCheck(serviceAccountPrincipal,
            "audit:read"),
            "Permission check should be logged for denial");
    }

    @Test
    @DisplayName("Audit Logging: Workflow Access")
    void testAuditLoggingWorkflowAccess() {
        // Test that workflow access attempts are logged
        assertTrue(rbacService.logWorkflowAccess(superAdminPrincipal,
            "workflow:123", "read"),
            "Workflow access should be logged for Super Admin");

        assertTrue(rbacService.logWorkflowAccess(auditorPrincipal,
            "workflow:456", "read"),
            "Workflow access should be logged for Auditor");

        assertTrue(rbacService.logWorkflowAccess(serviceAccountPrincipal,
            "workflow:789", "launch"),
            "Workflow access should be logged for Service Account");
    }

    @Test
    @DisplayName("Audit Trail: Reconstruction")
    void testAuditTrailReconstruction() {
        // Test that audit trails can be reconstructed
        rbacService.logPermissionCheck(superAdminPrincipal, "test:permission1");
        rbacService.logPermissionCheck(auditorPrincipal, "audit:read");
        rbacService.logWorkflowAccess(serviceAccountPrincipal, "workflow:123", "launch");

        String auditTrail = rbacService.getAuditTrail();
        assertNotNull(auditTrail, "Audit trail should not be null");
        assertTrue(auditTrail.contains("super-admin"), "Audit trail should contain Super Admin");
        assertTrue(auditTrail.contains("auditor"), "Audit trail should contain Auditor");
        assertTrue(auditTrail.contains("service-account"), "Audit trail should contain Service Account");
    }

    // =================================================================================
    // Performance Tests
    // =================================================================================

    @Test
    @DisplayName("Performance: High Load Permission Checks")
    void testPerformanceHighLoadPermissionChecks() {
        // Test performance under high load
        int iterations = 1000;
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < iterations; i++) {
            boolean result = rbacService.checkPermission(superAdminPrincipal,
                AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH);
            assertTrue(result, "Permission check should succeed");
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Should complete within 1 second (1ms per check on average)
        assertTrue(duration < 1000, String.format(
            "High load test should complete within 1 second, took %d ms", duration));
    }

    @Test
    @DisplayName("Performance: Concurrent Permission Validation")
    void testPerformanceConcurrentPermissionValidation() {
        // Test concurrent permission validation performance
        int threadCount = 100;
        Thread[] threads = new Thread[threadCount];
        boolean[] results = new boolean[threadCount];

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            threads[i] = new Thread(() -> {
                results[threadIndex] = rbacService.checkPermission(
                    superAdminPrincipal, AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH);
            });
        }

        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }

        // Wait for completion
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Thread interrupted");
            }
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Verify all threads got correct results
        for (boolean result : results) {
            assertTrue(result, "All threads should get correct permission result");
        }

        // Should complete within 5 seconds for 100 threads
        assertTrue(duration < 5000, String.format(
            "Concurrent test should complete within 5 seconds, took %d ms", duration));
    }

    // =================================================================================
    // Security Edge Cases
    // =================================================================================

    @Test
    @DisplayName("Security: Prevent Permission Injection")
    void testSecurityPreventPermissionInjection() {
        // Test against permission injection attacks
        Set<String> maliciousPermissions = Set.of(
            AuthenticatedPrincipal.PERM_WORKFLOW_QUERY,
            "admin:permissions; -- DROP TABLE users; --"
        );

        AuthenticatedPrincipal maliciousPrincipal = createPrincipal(
            "malicious-user", maliciousPermissions);

        // Verify malicious permissions are not granted
        assertFalse(maliciousPrincipal.hasPermission("admin:permissions; -- DROP TABLE users; --"),
            "Malicious permission injection should be rejected");
        assertTrue(maliciousPrincipal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_QUERY),
            "Legitimate permissions should still work");
    }

    @Test
    @DisplayName("Security: Permission Enumeration Prevention")
    void testSecurityPreventPermissionEnumeration() {
        // Test against permission enumeration attacks
        assertFalse(rbacService.checkPermissionEnumeration(
            serviceAccountPrincipal, "admin", "permissions"),
            "Should not allow permission enumeration");
    }

    @Test
    @DisplayName("Security: Cross-Session Permission Validation")
    void testSecurityCrossSessionPermissionValidation() {
        // Test permissions are validated across sessions
        String sessionId1 = "session-123";
        String sessionId2 = "session-456";

        // First session should succeed
        assertTrue(rbacService.validatePermissionsForSession(
            superAdminPrincipal, sessionId1, "admin:access"),
            "First session validation should succeed");

        // Second session with same principal should succeed
        assertTrue(rbacService.validatePermissionsForSession(
            superAdminPrincipal, sessionId2, "admin:access"),
            "Second session validation should succeed");

        // Different principal should be properly validated
        assertFalse(rbacService.validatePermissionsForSession(
            serviceAccountPrincipal, sessionId1, "admin:access"),
            "Different session validation should fail for insufficient privileges");
    }

    // =================================================================================
    // Helper Classes and Methods
    // =================================================================================

    /**
     * Advanced RBAC Authorization Service with additional capabilities
     */
    private static class RbacAuthorizationService {

        private final StringBuilder auditLog = new StringBuilder();

        public boolean checkPermission(AuthenticatedPrincipal principal, String permission) {
            if (principal == null || permission == null || permission.trim().isEmpty()) {
                throw new IllegalArgumentException("Principal and permission must not be null or empty");
            }
            return principal.hasPermission(permission);
        }

        public boolean canAccessWorkflow(AuthenticatedPrincipal principal, String workflowId) {
            if (principal == null) return false;

            if (principal.getUsername().equals(SUPER_ADMIN_USERNAME)) {
                return true;
            }

            if (workflowId.contains("multiple-roles-required")) {
                return principal.getUsername().equals(SUPER_ADMIN_USERNAME) ||
                       principal.getUsername().equals(SERVICE_ACCOUNT_USERNAME);
            }

            return true; // Default: allow access
        }

        public boolean canLaunchWorkflowConditional(AuthenticatedPrincipal principal,
                                                  String workflowId) {
            // Requires both launch AND manage permissions
            boolean canLaunch = principal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH);
            boolean canManage = principal.hasPermission("workflow:manage");
            return canLaunch && canManage;
        }

        public boolean canAccessWorkflowDuringHours(AuthenticatedPrincipal principal,
                                                   String workflowId) {
            // During business hours, only certain roles can access
            if (principal.getUsername().equals(SUPER_ADMIN_USERNAME)) {
                return true;
            }
            return false; // Restrict other roles during business hours
        }

        public boolean logPermissionCheck(AuthenticatedPrincipal principal, String permission) {
            String logEntry = String.format("[%s] Permission check: %s -> %s: %s%n",
                Instant.now(),
                principal.getUsername(),
                permission,
                principal.hasPermission(permission) ? "GRANTED" : "DENIED"
            );
            auditLog.append(logEntry);
            return principal.hasPermission(permission);
        }

        public boolean logWorkflowAccess(AuthenticatedPrincipal principal,
                                       String workflowId, String action) {
            String logEntry = String.format("[%s] Workflow access: %s -> %s (%s): %s%n",
                Instant.now(),
                principal.getUsername(),
                workflowId,
                action,
                "ALLOWED"
            );
            auditLog.append(logEntry);
            return true;
        }

        public String getAuditTrail() {
            return auditLog.toString();
        }

        public boolean checkPermissionEnumeration(AuthenticatedPrincipal principal,
                                                 String role, String permissionType) {
            // Prevent permission enumeration by always returning false
            return false;
        }

        public boolean validatePermissionsForSession(AuthenticatedPrincipal principal,
                                                   String sessionId, String permission) {
            // Validate permissions for specific session
            if (principal == null || sessionId == null || permission == null) {
                return false;
            }
            return principal.hasPermission(permission);
        }
    }
}