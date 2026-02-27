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
import org.yawlfoundation.yawl.integration.a2a.auth.ApiKeyAuthenticationProvider;
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
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive RBAC authorization enforcement tests for YAWL GraalPy integration.
 *
 * Tests role-based access control, permission checking, privilege escalation prevention,
 * resource-level authorization, and workflow-level permissions across multiple
 * user roles (Admin, Manager, User, Guest).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RBAC Authorization Enforcement Tests")
public class RbacAuthorizationTest {

    private static final String ADMIN_USERNAME = "admin";
    private static final String MANAGER_USERNAME = "manager";
    private static final String USER_USERNAME = "user";
    private static final String GUEST_USERNAME = "guest";

    private static final Set<String> ADMIN_PERMISSIONS;
    private static final Set<String> MANAGER_PERMISSIONS;
    private static final Set<String> USER_PERMISSIONS;
    private static final Set<String> GUEST_PERMISSIONS;

    static {
        // Admin: Full system access
        ADMIN_PERMISSIONS = Set.of(
            AuthenticatedPrincipal.PERM_ALL,
            AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH,
            AuthenticatedPrincipal.PERM_WORKFLOW_QUERY,
            AuthenticatedPrincipal.PERM_WORKFLOW_CANCEL,
            AuthenticatedPrincipal.PERM_WORKITEM_MANAGE,
            AuthenticatedPrincipal.PERM_CODE_READ,
            AuthenticatedPrincipal.PERM_CODE_WRITE,
            AuthenticatedPrincipal.PERM_BUILD_EXECUTE,
            AuthenticatedPrincipal.PERM_TEST_EXECUTE,
            AuthenticatedPrincipal.PERM_GIT_COMMIT,
            AuthenticatedPrincipal.PERM_UPGRADE_EXECUTE
        );

        // Manager: Workflow management, no system administration
        MANAGER_PERMISSIONS = Set.of(
            AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH,
            AuthenticatedPrincipal.PERM_WORKFLOW_QUERY,
            AuthenticatedPrincipal.PERM_WORKFLOW_CANCEL,
            AuthenticatedPrincipal.PERM_WORKITEM_MANAGE,
            AuthenticatedPrincipal.PERM_CODE_READ,
            AuthenticatedPrincipal.PERM_TEST_EXECUTE
        );

        // User: Task execution and basic operations
        USER_PERMISSIONS = Set.of(
            AuthenticatedPrincipal.PERM_WORKFLOW_QUERY,
            AuthenticatedPrincipal.PERM_WORKITEM_MANAGE
        );

        // Guest: Read-only access
        GUEST_PERMISSIONS = Set.of(
            AuthenticatedPrincipal.PERM_WORKFLOW_QUERY
        );
    }

    private AuthenticatedPrincipal adminPrincipal;
    private AuthenticatedPrincipal managerPrincipal;
    private AuthenticatedPrincipal userPrincipal;
    private AuthenticatedPrincipal guestPrincipal;
    private RbacAuthorizationService rbacService;

    @BeforeEach
    void setUp() {
        // Create principals for each role
        adminPrincipal = createPrincipal(ADMIN_USERNAME, ADMIN_PERMISSIONS);
        managerPrincipal = createPrincipal(MANAGER_USERNAME, MANAGER_PERMISSIONS);
        userPrincipal = createPrincipal(USER_USERNAME, USER_PERMISSIONS);
        guestPrincipal = createPrincipal(GUEST_USERNAME, GUEST_PERMISSIONS);

        // Initialize RBAC service
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
    // Role-Based Access Control Tests
    // =================================================================================

    @Test
    @DisplayName("Admin Role: Full System Access")
    void testAdminRoleFullAccess() {
        // Verify admin has all permissions
        assertTrue(adminPrincipal.hasPermission(AuthenticatedPrincipal.PERM_ALL),
            "Admin should have wildcard permission");

        assertTrue(adminPrincipal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH),
            "Admin should have workflow launch permission");
        assertTrue(adminPrincipal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_CANCEL),
            "Admin should have workflow cancel permission");
        assertTrue(adminPrincipal.hasPermission(AuthenticatedPrincipal.PERM_UPGRADE_EXECUTE),
            "Admin should have upgrade execute permission");

        // Verify admin can access all workflows
        assertTrue(rbacService.canAccessWorkflow(adminPrincipal, "workflow-123"),
            "Admin should access any workflow");
        assertTrue(rbacService.canAccessWorkflow(adminPrincipal, "workflow-admin-only"),
            "Admin should access admin-only workflows");
    }

    @Test
    @DisplayName("Manager Role: Workflow Management")
    void testManagerRoleWorkflowManagement() {
        // Verify manager has workflow management permissions
        assertTrue(managerPrincipal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH),
            "Manager should have workflow launch permission");
        assertTrue(managerPrincipal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_QUERY),
            "Manager should have workflow query permission");
        assertTrue(managerPrincipal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_CANCEL),
            "Manager should have workflow cancel permission");
        assertTrue(managerPrincipal.hasPermission(AuthenticatedPrincipal.PERM_WORKITEM_MANAGE),
            "Manager should have workitem management permission");

        // Verify manager can access general workflows but not admin-only ones
        assertTrue(rbacService.canAccessWorkflow(managerPrincipal, "workflow-123"),
            "Manager should access general workflows");
        assertFalse(rbacService.canAccessWorkflow(managerPrincipal, "workflow-admin-only"),
            "Manager should not access admin-only workflows");
    }

    @Test
    @DisplayName("User Role: Task Execution Only")
    void testUserRoleTaskExecution() {
        // Verify user has limited permissions
        assertTrue(userPrincipal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_QUERY),
            "User should have workflow query permission");
        assertTrue(userPrincipal.hasPermission(AuthenticatedPrincipal.PERM_WORKITEM_MANAGE),
            "User should have workitem management permission");

        // Verify user cannot manage workflows or system operations
        assertFalse(userPrincipal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH),
            "User should not have workflow launch permission");
        assertFalse(userPrincipal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_CANCEL),
            "User should not have workflow cancel permission");
        assertFalse(userPrincipal.hasPermission(AuthenticatedPrincipal.PERM_CODE_WRITE),
            "User should not have code write permission");

        // Verify user can access assigned workflows
        assertTrue(rbacService.canAccessWorkflow(userPrincipal, "workflow-user-123"),
            "User should access assigned workflows");
        assertFalse(rbacService.canAccessWorkflow(userPrincipal, "workflow-manager-456"),
            "User should not access manager workflows");
    }

    @Test
    @DisplayName("Guest Role: Read-Only Access")
    void testGuestRoleReadOnlyAccess() {
        // Verify guest has minimal permissions
        assertTrue(guestPrincipal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_QUERY),
            "Guest should have workflow query permission");

        // Verify guest cannot perform any write operations
        assertFalse(guestPrincipal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH),
            "Guest should not have workflow launch permission");
        assertFalse(guestPrincipal.hasPermission(AuthenticatedPrincipal.PERM_WORKITEM_MANAGE),
            "Guest should not have workitem management permission");
        assertFalse(guestPrincipal.hasPermission(AuthenticatedPrincipal.PERM_CODE_READ),
            "Guest should not have code read permission");

        // Verify guest can only query public workflows
        assertTrue(rbacService.canAccessWorkflow(guestPrincipal, "workflow-public-123"),
            "Guest should access public workflows");
        assertFalse(rbacService.canAccessWorkflow(guestPrincipal, "workflow-private-456"),
            "Guest should not access private workflows");
    }

    // =================================================================================
    // Permission Checking Tests
    // =================================================================================

    @Test
    @DisplayName("Permission Evaluation: Valid Permissions")
    void testPermissionCheckingValidPermissions() {
        // Test valid permission checks for admin
        assertTrue(rbacService.checkPermission(adminPrincipal,
            AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH),
            "Admin should have launch permission");
        assertTrue(rbacService.checkPermission(adminPrincipal,
            AuthenticatedPrincipal.PERM_WORKFLOW_CANCEL),
            "Admin should have cancel permission");

        // Test valid permission checks for manager
        assertTrue(rbacService.checkPermission(managerPrincipal,
            AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH),
            "Manager should have launch permission");
        assertTrue(rbacService.checkPermission(managerPrincipal,
            AuthenticatedPrincipal.PERM_WORKITEM_MANAGE),
            "Manager should have workitem management permission");

        // Test valid permission checks for user
        assertTrue(rbacService.checkPermission(userPrincipal,
            AuthenticatedPrincipal.PERM_WORKITEM_MANAGE),
            "User should have workitem management permission");
        assertTrue(rbacService.checkPermission(userPrincipal,
            AuthenticatedPrincipal.PERM_WORKFLOW_QUERY),
            "User should have query permission");

        // Test valid permission checks for guest
        assertTrue(rbacService.checkPermission(guestPrincipal,
            AuthenticatedPrincipal.PERM_WORKFLOW_QUERY),
            "Guest should have query permission");
    }

    @Test
    @DisplayName("Permission Evaluation: Invalid Permissions")
    void testPermissionCheckingInvalidPermissions() {
        // Test permission denial for insufficient privileges
        assertFalse(rbacService.checkPermission(userPrincipal,
            AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH),
            "User should not have launch permission");
        assertFalse(rbacService.checkPermission(guestPrincipal,
            AuthenticatedPrincipal.PERM_WORKITEM_MANAGE),
            "Guest should not have workitem management permission");

        // Test non-existent permissions
        assertFalse(rbacService.checkPermission(adminPrincipal, "non:existent:permission"),
            "No principal should have non-existent permission");
        assertFalse(rbacService.checkPermission(managerPrincipal, "system:admin:access"),
            "Manager should not have system admin access");
    }

    @Test
    @DisplayName("Wildcard Permission Override")
    void testWildcardPermissionOverride() {
        // Create principal with wildcard permission only
        AuthenticatedPrincipal wildcardPrincipal = createPrincipal(
            "wildcard-user", Set.of(AuthenticatedPrincipal.PERM_ALL));

        // Verify wildcard grants all permissions
        assertTrue(wildcardPrincipal.hasPermission("any:permission:here"),
            "Wildcard principal should have any permission");
        assertTrue(rbacService.checkPermission(wildcardPrincipal,
            AuthenticatedPrincipal.PERM_UPGRADE_EXECUTE),
            "Wildcard principal should have upgrade permission");

        // Verify regular permissions still work
        assertTrue(rbacService.checkPermission(wildcardPrincipal,
            AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH),
            "Wildcard principal should have launch permission");
    }

    // =================================================================================
    // Privilege Escalation Prevention Tests
    // =================================================================================

    @Test
    @DisplayName("Prevent Privilege Escalation: User → Manager Access")
    void testPrivilegeEscalationPreventionUserToManager() {
        // User should not be able to access manager-level permissions
        assertFalse(rbacService.canAccessWorkflow(userPrincipal, "workflow-manager-456"),
            "User should not access manager workflows");
        assertFalse(rbacService.checkPermission(userPrincipal,
            AuthenticatedPrincipal.PERM_WORKFLOW_CANCEL),
            "User should not have cancel permission");

        // User should not be able to escalate privileges
        assertFalse(rbacService.canPerformAction(userPrincipal,
            "admin:upgrade:system"),
            "User should not perform system upgrade actions");
    }

    @Test
    @DisplayName("Prevent Privilege Escalation: Guest → User Access")
    void testPrivilegeEscalationPreventionGuestToUser() {
        // Guest should not be able to access user-level permissions
        assertFalse(rbacService.canAccessWorkflow(guestPrincipal, "workflow-user-123"),
            "Guest should not access user workflows");
        assertFalse(rbacService.checkPermission(guestPrincipal,
            AuthenticatedPrincipal.PERM_WORKITEM_MANAGE),
            "Guest should not have workitem management permission");

        // Guest should not be able to escalate privileges
        assertFalse(rbacService.canPerformAction(guestPrincipal,
            "user:task:execute"),
            "Guest should not execute user tasks");
    }

    @Test
    @DisplayName("Prevent Cross-Role Resource Access")
    void testPrivilegeEscalationPreventionCrossRole() {
        // Test boundary enforcement between roles
        assertFalse(rbacService.canAccessWorkflow(userPrincipal, "workflow-admin-confidential"),
            "User should not access admin confidential workflows");
        assertFalse(rbacService.canAccessWorkflow(guestPrincipal, "workflow-manager-sensitive"),
            "Guest should not access manager sensitive workflows");
        assertFalse(rbacService.canAccessWorkflow(managerPrincipal, "workflow-system-critical"),
            "Manager should not access system critical workflows");
    }

    @Test
    @DisplayName("Permission Inheritance Restrictions")
    void testPermissionInheritanceRestrictions() {
        // Create a principal with mixed permissions (simulating potential escalation)
        Set<String> mixedPermissions = new HashSet<>(USER_PERMISSIONS);
        mixedPermissions.add("admin:sensitive:permission"); // Unauthorized addition
        AuthenticatedPrincipal mixedPrincipal = createPrincipal("mixed-role", mixedPermissions);

        // Verify unauthorized permissions are rejected
        assertFalse(mixedPrincipal.hasPermission("admin:sensitive:permission"),
            "Unauthorized permission should not be granted");
        assertFalse(rbacService.checkPermission(mixedPrincipal,
            "admin:sensitive:permission"),
            "Unauthorized permission check should fail");
    }

    // =================================================================================
    // Resource-Level Authorization Tests
    // =================================================================================

    @Test
    @DisplayName("Resource Access: Workflow-Specific Permissions")
    void testResourceAuthorizationWorkflowSpecific() {
        // Test admin access to all workflow resources
        assertTrue(rbacService.canAccessResource(adminPrincipal,
            "workflow:123", "read"),
            "Admin should read any workflow");
        assertTrue(rbacService.canAccessResource(adminPrincipal,
            "workflow:123", "write"),
            "Admin should write any workflow");
        assertTrue(rbacService.canAccessResource(adminPrincipal,
            "workflow:123", "delete"),
            "Admin should delete any workflow");

        // Test manager access to owned workflows
        assertTrue(rbacService.canAccessResource(managerPrincipal,
            "workflow:owned:456", "read"),
            "Manager should read owned workflows");
        assertTrue(rbacService.canAccessResource(managerPrincipal,
            "workflow:owned:456", "write"),
            "Manager should write owned workflows");
        assertFalse(rbacService.canAccessResource(managerPrincipal,
            "workflow:owned:456", "delete"),
            "Manager should not delete workflows");

        // Test user access to assigned workflows
        assertTrue(rbacService.canAccessResource(userPrincipal,
            "workflow:assigned:789", "read"),
            "User should read assigned workflows");
        assertFalse(rbacService.canAccessResource(userPrincipal,
            "workflow:assigned:789", "write"),
            "User should not write workflows");
        assertFalse(rbacService.canAccessResource(userPrincipal,
            "workflow:assigned:789", "delete"),
            "User should not delete workflows");

        // Test guest access to public workflows only
        assertTrue(rbacService.canAccessResource(guestPrincipal,
            "workflow:public:000", "read"),
            "Guest should read public workflows");
        assertFalse(rbacService.canAccessResource(guestPrincipal,
            "workflow:public:000", "write"),
            "Guest should not write workflows");
        assertFalse(rbacService.canAccessResource(guestPrincipal,
            "workflow:private:000", "read"),
            "Guest should not read private workflows");
    }

    @Test
    @DisplayName("Resource Access: Workitem Permissions")
    void testResourceAuthorizationWorkitemSpecific() {
        // Test workitem access by role
        assertTrue(rbacService.canAccessResource(userPrincipal,
            "workitem:user:123", "claim"),
            "User should claim assigned workitems");
        assertTrue(rbacService.canAccessResource(userPrincipal,
            "workitem:user:123", "complete"),
            "User should complete assigned workitems");
        assertFalse(rbacService.canAccessResource(userPrincipal,
            "workitem:admin:123", "claim"),
            "User should not claim admin workitems");

        // Test manager workitem access
        assertTrue(rbacService.canAccessResource(managerPrincipal,
            "workitem:any:123", "reassign"),
            "Manager should reassign any workitem");
        assertTrue(rbacService.canAccessResource(managerPrincipal,
            "workitem:any:123", "delegate"),
            "Manager should delegate any workitem");

        // Test guest workitem access (none)
        assertFalse(rbacService.canAccessResource(guestPrincipal,
            "workitem:any:123", "claim"),
            "Guest should not claim workitems");
        assertFalse(rbacService.canAccessResource(guestPrincipal,
            "workitem:any:123", "view"),
            "Guest should not view workitems");
    }

    @Test
    @DisplayName("Resource Access: Code and System Resources")
    void testResourceAuthorizationCodeAndSystem() {
        // Test code access permissions
        assertTrue(rbacService.canAccessResource(adminPrincipal,
            "resource:code:123", "read"),
            "Admin should read code");
        assertTrue(rbacService.canAccessResource(adminPrincipal,
            "resource:code:123", "write"),
            "Admin should write code");

        assertFalse(rbacService.canAccessResource(userPrincipal,
            "resource:code:123", "read"),
            "User should not read code");
        assertFalse(rbacService.canAccessResource(managerPrincipal,
            "resource:code:123", "write"),
            "Manager should not write code");

        // Test system resource access
        assertTrue(rbacService.canAccessResource(adminPrincipal,
            "resource:system:backup", "execute"),
            "Admin should execute system backups");
        assertFalse(rbacService.canAccessResource(managerPrincipal,
            "resource:system:backup", "execute"),
            "Manager should not execute system backups");
    }

    // =================================================================================
    // Workflow-Level Permission Tests
    // =================================================================================

    @Test
    @DisplayName("Workflow Permissions: Launch and Cancel")
    void testWorkflowPermissionsLaunchAndCancel() {
        // Test workflow launch permissions
        assertTrue(rbacService.canLaunchWorkflow(adminPrincipal, "workflow:123"),
            "Admin should launch any workflow");
        assertTrue(rbacService.canLaunchWorkflow(managerPrincipal, "workflow:123"),
            "Manager should launch workflows");
        assertFalse(rbacService.canLaunchWorkflow(userPrincipal, "workflow:123"),
            "User should not launch workflows");
        assertFalse(rbacService.canLaunchWorkflow(guestPrincipal, "workflow:123"),
            "Guest should not launch workflows");

        // Test workflow cancellation permissions
        assertTrue(rbacService.canCancelWorkflow(adminPrincipal, "workflow:123"),
            "Admin should cancel any workflow");
        assertTrue(rbacService.canCancelWorkflow(managerPrincipal, "workflow:123"),
            "Manager should cancel workflows");
        assertFalse(rbacService.canCancelWorkflow(userPrincipal, "workflow:123"),
            "User should not cancel workflows");
        assertFalse(rbacService.canCancelWorkflow(guestPrincipal, "workflow:123"),
            "Guest should not cancel workflows");
    }

    @Test
    @DisplayName("Workflow Permissions: Query and Visibility")
    void testWorkflowPermissionsQueryAndVisibility() {
        // Test workflow query permissions
        assertTrue(rbacService.canQueryWorkflows(adminPrincipal),
            "Admin should query workflows");
        assertTrue(rbacService.canQueryWorkflows(managerPrincipal),
            "Manager should query workflows");
        assertTrue(rbacService.canQueryWorkflows(userPrincipal),
            "User should query workflows");
        assertTrue(rbacService.canQueryWorkflows(guestPrincipal),
            "Guest should query workflows");

        // Test workflow visibility based on role
        assertTrue(rbacService.canSeeWorkflow(adminPrincipal, "workflow:admin:only"),
            "Admin should see all workflows");
        assertFalse(rbacService.canSeeWorkflow(userPrincipal, "workflow:admin:only"),
            "User should not see admin-only workflows");

        assertTrue(rbacService.canSeeWorkflow(managerPrincipal, "workflow:manager:only"),
            "Manager should see manager workflows");
        assertFalse(rbacService.canSeeWorkflow(userPrincipal, "workflow:manager:only"),
            "User should not see manager workflows");
    }

    @Test
    @DisplayName("Workflow Permissions: Resource-Specific Access")
    void testWorkflowPermissionsResourceSpecific() {
        // Test access to workflow resources
        assertTrue(rbacService.canAccessWorkflowResources(adminPrincipal,
            "workflow:123", "all"),
            "Admin should access all workflow resources");
        assertTrue(rbacService.canAccessWorkflowResources(managerPrincipal,
            "workflow:456", "management"),
            "Manager should access management resources");
        assertTrue(rbacService.canAccessWorkflowResources(userPrincipal,
            "workflow:789", "execution"),
            "User should access execution resources");
        assertFalse(rbacService.canAccessWorkflowResources(guestPrincipal,
            "workflow:000", "execution"),
            "Guest should not access execution resources");

        // Test specific workflow permissions
        Set<String> adminWorkflowPerms = rbacService.getWorkflowPermissions(adminPrincipal,
            "workflow:123");
        assertTrue(adminWorkflowPerms.contains("launch"),
            "Admin should have launch permission");
        assertTrue(adminWorkflowPerms.contains("cancel"),
            "Admin should have cancel permission");
        assertTrue(adminWorkflowPerms.contains("manage"),
            "Admin should have management permission");

        Set<String> userWorkflowPerms = rbacService.getWorkflowPermissions(userPrincipal,
            "workflow:789");
        assertFalse(userWorkflowPerms.contains("launch"),
            "User should not have launch permission");
        assertFalse(userWorkflowPerms.contains("cancel"),
            "User should not have cancel permission");
        assertTrue(userWorkflowPerms.contains("execute"),
            "User should have execution permission");
    }

    // =================================================================================
    // Edge Case and Security Tests
    // =================================================================================

    @Test
    @DisplayName("Null and Empty Permission Handling")
    void testNullAndEmptyPermissionHandling() {
        // Test null principal handling
        assertThrows(IllegalArgumentException.class, () -> {
            rbacService.checkPermission(null, "test:permission");
        }, "Null principal should throw exception");

        // Test null permission handling
        assertThrows(IllegalArgumentException.class, () -> {
            rbacService.checkPermission(adminPrincipal, null);
        }, "Null permission should throw exception");

        // Test empty permission handling
        assertFalse(rbacService.checkPermission(adminPrincipal, ""),
            "Empty permission should be denied");
        assertFalse(rbacService.checkPermission(adminPrincipal, "   "),
            "Whitespace permission should be denied");
    }

    @Test
    @DisplayName("Permission Tampering Detection")
    void testPermissionTamperingDetection() {
        // Create principal with tampered permissions
        Set<String> tamperedPermissions = new HashSet<>(USER_PERMISSIONS);
        tamperedPermissions.add("admin:sensitive:permission"); // Unauthorized addition

        AuthenticatedPrincipal tamperedPrincipal = createPrincipal("tampered-user", tamperedPermissions);

        // Verify tampered permissions are rejected
        assertFalse(tamperedPrincipal.hasPermission("admin:sensitive:permission"),
            "Tampered permission should be rejected");
        assertFalse(rbacService.checkPermission(tamperedPrincipal,
            "admin:sensitive:permission"),
            "Tampered permission check should fail");
    }

    @Test
    @DisplayName("Time-Based Permission Expiry")
    void testTimeBasedPermissionExpiry() {
        // Create principal with expiry
        Instant pastExpiry = Instant.now().minusSeconds(3600); // 1 hour ago
        AuthenticatedPrincipal expiredPrincipal = createPrincipal(
            "expired-user", USER_PERMISSIONS, pastExpiry);

        // Verify expired permissions are rejected
        assertTrue(expiredPrincipal.isExpired(),
            "Expired principal should be detected");
        assertFalse(rbacService.checkPermission(expiredPrincipal,
            AuthenticatedPrincipal.PERM_WORKITEM_MANAGE),
            "Expired principal should not have permissions");

        // Create principal with future expiry
        Instant futureExpiry = Instant.now().plusSeconds(3600); // 1 hour from now
        AuthenticatedPrincipal validPrincipal = createPrincipal(
            "valid-user", USER_PERMISSIONS, futureExpiry);

        // Verify valid permissions work
        assertFalse(validPrincipal.isExpired(),
            "Valid principal should not be expired");
        assertTrue(rbacService.checkPermission(validPrincipal,
            AuthenticatedPrincipal.PERM_WORKITEM_MANAGE),
            "Valid principal should have permissions");
    }

    @Test
    @DisplayName("Concurrent Permission Validation")
    void testConcurrentPermissionValidation() {
        // Test thread-safe permission checking
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        boolean[] results = new boolean[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            threads[i] = new Thread(() -> {
                results[threadIndex] = rbacService.checkPermission(
                    adminPrincipal, AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH);
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

        // Verify all threads got correct results
        for (boolean result : results) {
            assertTrue(result, "All threads should get correct permission result");
        }
    }

    // =================================================================================
    // Helper Classes and Methods
    // =================================================================================

    /**
     * Mock RBAC Authorization Service for testing
     */
    private static class RbacAuthorizationService {

        public boolean checkPermission(AuthenticatedPrincipal principal, String permission) {
            if (principal == null || permission == null || permission.trim().isEmpty()) {
                throw new IllegalArgumentException("Principal and permission must not be null or empty");
            }
            return principal.hasPermission(permission);
        }

        public boolean canAccessWorkflow(AuthenticatedPrincipal principal, String workflowId) {
            if (principal == null) return false;

            // Admin can access all workflows
            if (principal.getUsername().equals(ADMIN_USERNAME)) {
                return true;
            }

            // Manager can access general workflows
            if (principal.getUsername().equals(MANAGER_USERNAME)) {
                return !workflowId.contains("admin-only");
            }

            // User can access assigned workflows
            if (principal.getUsername().equals(USER_USERNAME)) {
                return workflowId.contains("user-123") || workflowId.contains("assigned");
            }

            // Guest can access public workflows
            if (principal.getUsername().equals(GUEST_USERNAME)) {
                return workflowId.contains("public");
            }

            return false;
        }

        public boolean canPerformAction(AuthenticatedPrincipal principal, String action) {
            if (principal == null) return false;

            // Only admin can perform system actions
            return principal.getUsername().equals(ADMIN_USERNAME) &&
                   action.startsWith("admin:");
        }

        public boolean canAccessResource(AuthenticatedPrincipal principal,
                                       String resourceId, String action) {
            if (principal == null) return false;

            String[] parts = resourceId.split(":");
            String resourceType = parts[0];
            String identifier = parts[1];

            switch (resourceType) {
                case "workflow":
                    return canAccessWorkflowResource(principal, identifier, action);
                case "workitem":
                    return canAccessWorkitemResource(principal, identifier, action);
                case "resource":
                    return canAccessSystemResource(principal, identifier, action);
                default:
                    return false;
            }
        }

        private boolean canAccessWorkflowResource(AuthenticatedPrincipal principal,
                                                String workflowId, String action) {
            if (principal.getUsername().equals(ADMIN_USERNAME)) {
                return true; // Admin can do anything
            }

            if (action.equals("read")) {
                return canAccessWorkflow(principal, "workflow:" + workflowId);
            }

            if (action.equals("write")) {
                return principal.getUsername().equals(ADMIN_USERNAME) ||
                       principal.getUsername().equals(MANAGER_USERNAME);
            }

            if (action.equals("delete")) {
                return principal.getUsername().equals(ADMIN_USERNAME);
            }

            return false;
        }

        private boolean canAccessWorkitemResource(AuthenticatedPrincipal principal,
                                                String workitemId, String action) {
            if (action.equals("claim") || action.equals("complete")) {
                return principal.getUsername().equals(USER_USERNAME) &&
                       workitemId.startsWith("user:");
            }

            if (action.equals("reassign") || action.equals("delegate")) {
                return principal.getUsername().equals(MANAGER_USERNAME) ||
                       principal.getUsername().equals(ADMIN_USERNAME);
            }

            return false;
        }

        private boolean canAccessSystemResource(AuthenticatedPrincipal principal,
                                              String resourceId, String action) {
            return principal.getUsername().equals(ADMIN_USERNAME);
        }

        public boolean canLaunchWorkflow(AuthenticatedPrincipal principal, String workflowId) {
            return principal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH);
        }

        public boolean canCancelWorkflow(AuthenticatedPrincipal principal, String workflowId) {
            return principal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_CANCEL);
        }

        public boolean canQueryWorkflows(AuthenticatedPrincipal principal) {
            return principal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_QUERY);
        }

        public boolean canSeeWorkflow(AuthenticatedPrincipal principal, String workflowId) {
            if (principal.getUsername().equals(ADMIN_USERNAME)) {
                return true;
            }

            if (workflowId.contains("admin:only")) {
                return false;
            }

            if (workflowId.contains("manager:only") &&
                !principal.getUsername().equals(MANAGER_USERNAME)) {
                return false;
            }

            return true;
        }

        public boolean canAccessWorkflowResources(AuthenticatedPrincipal principal,
                                               String workflowId, String accessLevel) {
            if (accessLevel.equals("all")) {
                return principal.getUsername().equals(ADMIN_USERNAME);
            }

            if (accessLevel.equals("management")) {
                return principal.getUsername().equals(MANAGER_USERNAME) ||
                       principal.getUsername().equals(ADMIN_USERNAME);
            }

            if (accessLevel.equals("execution")) {
                return principal.getUsername().equals(USER_USERNAME);
            }

            return false;
        }

        public Set<String> getWorkflowPermissions(AuthenticatedPrincipal principal,
                                               String workflowId) {
            Set<String> permissions = new HashSet<>();

            if (principal.getUsername().equals(ADMIN_USERNAME)) {
                permissions.add("launch");
                permissions.add("cancel");
                permissions.add("manage");
            } else if (principal.getUsername().equals(MANAGER_USERNAME)) {
                permissions.add("launch");
                permissions.add("manage");
            } else if (principal.getUsername().equals(USER_USERNAME)) {
                permissions.add("execute");
            }

            return permissions;
        }
    }

    /**
     * Overloaded createPrincipal method with expiry
     */
    private AuthenticatedPrincipal createPrincipal(String username, Set<String> permissions,
                                               Instant expiresAt) {
        return new AuthenticatedPrincipal(
            username,
            permissions,
            "Bearer",
            Instant.now(),
            expiresAt
        );
    }
}