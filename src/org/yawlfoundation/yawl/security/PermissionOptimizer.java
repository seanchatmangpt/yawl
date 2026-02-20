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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Automatic permission optimization using least-privilege enforcement.
 *
 * Monitors actual permission usage and automatically reduces unnecessary grants:
 * - Tracks which permissions are actually used by each role
 * - Identifies unused permission grants
 * - Proposes permission reductions
 * - Enforces least-privilege by removing unused permissions
 * - Maintains audit trail of permission adjustments
 *
 * Permission categories:
 * - Scope: "workitem", "case", "spec", "admin"
 * - Action: "read", "write", "execute", "delete", "admin"
 *
 * Optimization flow:
 * 1. Observe actual permission usage (recordPermissionUsage)
 * 2. Analyze gaps (getUnusedPermissions)
 * 3. Auto-remove unused permissions after observation window (default: 7 days)
 * 4. Log all adjustments for compliance
 *
 * Integration:
 * - Call recordPermissionUsage() on each authorized action
 * - Run optimizePermissions() on scheduled intervals
 * - Review audit trail via getAuditLog()
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class PermissionOptimizer {

    private static final Logger log = LogManager.getLogger(PermissionOptimizer.class);

    private static final long OBSERVATION_WINDOW_DAYS = 7;
    private static final long OBSERVATION_WINDOW_SECONDS = OBSERVATION_WINDOW_DAYS * 24 * 3600;

    /**
     * Audit record for permission adjustments.
     */
    private static class PermissionAudit {
        private final long timestampEpochSeconds;
        private final String role;
        private final String action; // "UNUSED_DETECTED", "REMOVED", "GRANTED"
        private final Set<String> permissions;
        private final String reason;

        PermissionAudit(long timestamp, String role, String action, Set<String> permissions, String reason) {
            this.timestampEpochSeconds = timestamp;
            this.role = Objects.requireNonNull(role);
            this.action = Objects.requireNonNull(action);
            this.permissions = new HashSet<>(Objects.requireNonNull(permissions));
            this.reason = reason;
        }

        @Override
        public String toString() {
            return String.format("[%d] %s: %s %s - %s",
                    timestampEpochSeconds, role, action, permissions, reason);
        }
    }

    /**
     * Role's permission state.
     */
    private static class RolePermissions {
        private final String role;
        private final Set<String> grantedPermissions; // Actual grants
        private final Set<String> usedPermissions;    // Observed usage
        private final Map<String, Long> lastUsedTime; // Timestamp of last usage
        private final Deque<PermissionAudit> auditLog;

        RolePermissions(String role) {
            this.role = Objects.requireNonNull(role);
            this.grantedPermissions = new HashSet<>();
            this.usedPermissions = new HashSet<>();
            this.lastUsedTime = new ConcurrentHashMap<>();
            this.auditLog = new LinkedList<>();
        }

        void grant(String permission) {
            grantedPermissions.add(Objects.requireNonNull(permission));
        }

        void recordUsage(String permission) {
            if (grantedPermissions.contains(permission)) {
                usedPermissions.add(permission);
                lastUsedTime.put(permission, System.currentTimeMillis() / 1000);
            }
        }

        Set<String> getUnusedPermissions() {
            return grantedPermissions.stream()
                    .filter(p -> !usedPermissions.contains(p))
                    .collect(Collectors.toSet());
        }

        Set<String> getUnusedPermissionsSince(long olderThanSeconds) {
            long cutoffTime = System.currentTimeMillis() / 1000 - olderThanSeconds;
            return grantedPermissions.stream()
                    .filter(p -> !usedPermissions.contains(p) ||
                            (lastUsedTime.containsKey(p) && lastUsedTime.get(p) < cutoffTime))
                    .collect(Collectors.toSet());
        }

        void removePermissions(Set<String> toRemove, String reason) {
            grantedPermissions.removeAll(toRemove);
            usedPermissions.removeAll(toRemove);
            toRemove.forEach(lastUsedTime::remove);

            PermissionAudit audit = new PermissionAudit(
                    System.currentTimeMillis() / 1000,
                    role,
                    "REMOVED",
                    toRemove,
                    reason
            );
            auditLog.addLast(audit);

            if (auditLog.size() > 100) {
                auditLog.removeFirst();
            }
        }

        void logUnusedDetection(Set<String> unused, String reason) {
            PermissionAudit audit = new PermissionAudit(
                    System.currentTimeMillis() / 1000,
                    role,
                    "UNUSED_DETECTED",
                    unused,
                    reason
            );
            auditLog.addLast(audit);

            if (auditLog.size() > 100) {
                auditLog.removeFirst();
            }
        }

        Set<String> getGrantedPermissions() {
            return new HashSet<>(grantedPermissions);
        }

        Set<String> getUsedPermissions() {
            return new HashSet<>(usedPermissions);
        }

        Deque<PermissionAudit> getAuditLog() {
            return new LinkedList<>(auditLog);
        }
    }

    private final Map<String, RolePermissions> roles;

    /**
     * Creates a new PermissionOptimizer with no initial roles.
     */
    public PermissionOptimizer() {
        this.roles = new ConcurrentHashMap<>();
    }

    /**
     * Grants a permission to a role.
     *
     * @param role unique identifier for the role
     * @param permission permission identifier (e.g., "workitem:read", "case:delete")
     * @throws IllegalArgumentException if role or permission is null/empty
     */
    public void grantPermission(String role, String permission) {
        Objects.requireNonNull(role, "role cannot be null");
        Objects.requireNonNull(permission, "permission cannot be null");

        if (role.isEmpty()) {
            throw new IllegalArgumentException("role cannot be empty");
        }
        if (permission.isEmpty()) {
            throw new IllegalArgumentException("permission cannot be empty");
        }

        RolePermissions rolePerms = roles.computeIfAbsent(role, RolePermissions::new);
        rolePerms.grant(permission);
        log.debug("Permission granted: {} -> {}", role, permission);
    }

    /**
     * Grants multiple permissions to a role in batch.
     *
     * @param role unique identifier for the role
     * @param permissions set of permission identifiers
     * @throws IllegalArgumentException if role is null/empty or permissions contains null/empty
     */
    public void grantPermissions(String role, Set<String> permissions) {
        Objects.requireNonNull(role, "role cannot be null");
        Objects.requireNonNull(permissions, "permissions cannot be null");

        if (role.isEmpty()) {
            throw new IllegalArgumentException("role cannot be empty");
        }

        for (String permission : permissions) {
            Objects.requireNonNull(permission, "permission cannot be null");
            if (permission.isEmpty()) {
                throw new IllegalArgumentException("permission cannot be empty");
            }
            grantPermission(role, permission);
        }
    }

    /**
     * Records actual usage of a permission by a role.
     * Only records if permission was previously granted.
     *
     * @param role unique identifier for the role
     * @param permission permission that was used
     * @throws IllegalArgumentException if role or permission is null/empty
     */
    public void recordPermissionUsage(String role, String permission) {
        Objects.requireNonNull(role, "role cannot be null");
        Objects.requireNonNull(permission, "permission cannot be null");

        if (role.isEmpty()) {
            throw new IllegalArgumentException("role cannot be empty");
        }
        if (permission.isEmpty()) {
            throw new IllegalArgumentException("permission cannot be empty");
        }

        RolePermissions rolePerms = roles.computeIfAbsent(role, RolePermissions::new);
        rolePerms.recordUsage(permission);
    }

    /**
     * Gets permissions granted to a role but never used.
     *
     * @param role unique identifier for the role
     * @return set of unused permission identifiers
     */
    public Set<String> getUnusedPermissions(String role) {
        Objects.requireNonNull(role, "role cannot be null");

        RolePermissions rolePerms = roles.get(role);
        if (rolePerms == null) {
            return Collections.emptySet();
        }

        return rolePerms.getUnusedPermissions();
    }

    /**
     * Gets all granted permissions for a role.
     *
     * @param role unique identifier for the role
     * @return set of granted permission identifiers
     */
    public Set<String> getGrantedPermissions(String role) {
        Objects.requireNonNull(role, "role cannot be null");

        RolePermissions rolePerms = roles.get(role);
        if (rolePerms == null) {
            return Collections.emptySet();
        }

        return rolePerms.getGrantedPermissions();
    }

    /**
     * Gets all used permissions for a role.
     *
     * @param role unique identifier for the role
     * @return set of used permission identifiers
     */
    public Set<String> getUsedPermissions(String role) {
        Objects.requireNonNull(role, "role cannot be null");

        RolePermissions rolePerms = roles.get(role);
        if (rolePerms == null) {
            return Collections.emptySet();
        }

        return rolePerms.getUsedPermissions();
    }

    /**
     * Automatically optimizes permissions by removing unused grants older than observation window.
     * This is the main enforcement point for least-privilege.
     *
     * @return count of permissions removed across all roles
     */
    public int optimizePermissions() {
        int totalRemoved = 0;

        for (RolePermissions rolePerms : roles.values()) {
            Set<String> unused = rolePerms.getUnusedPermissionsSince(OBSERVATION_WINDOW_SECONDS);

            if (!unused.isEmpty()) {
                rolePerms.removePermissions(unused, "Automatically removed - unused for " +
                        OBSERVATION_WINDOW_DAYS + " days");
                totalRemoved += unused.size();

                log.info("Optimized permissions for role {}: removed {} unused permissions",
                        rolePerms.role, unused.size());
            }
        }

        if (totalRemoved > 0) {
            log.info("Permission optimization complete: {} total permissions removed", totalRemoved);
        }

        return totalRemoved;
    }

    /**
     * Manually removes a permission from a role.
     *
     * @param role unique identifier for the role
     * @param permission permission identifier to remove
     * @throws IllegalArgumentException if role is null/empty or permission is null/empty
     */
    public void removePermission(String role, String permission) {
        Objects.requireNonNull(role, "role cannot be null");
        Objects.requireNonNull(permission, "permission cannot be null");

        if (role.isEmpty()) {
            throw new IllegalArgumentException("role cannot be empty");
        }
        if (permission.isEmpty()) {
            throw new IllegalArgumentException("permission cannot be empty");
        }

        RolePermissions rolePerms = roles.get(role);
        if (rolePerms != null) {
            rolePerms.removePermissions(Set.of(permission), "Manual removal");
            log.info("Permission removed: {} <- {}", role, permission);
        }
    }

    /**
     * Detects unused permissions for a role without removing them.
     * Useful for review before auto-removal.
     *
     * @param role unique identifier for the role
     * @return map of unused permission -> last used timestamp (0 if never used)
     */
    public Map<String, Long> detectUnusedPermissions(String role) {
        Objects.requireNonNull(role, "role cannot be null");

        RolePermissions rolePerms = roles.get(role);
        if (rolePerms == null) {
            return Collections.emptyMap();
        }

        Map<String, Long> result = new HashMap<>();
        for (String permission : rolePerms.getUnusedPermissions()) {
            result.put(permission, rolePerms.lastUsedTime.getOrDefault(permission, 0L));
        }

        return result;
    }

    /**
     * Gets the audit log for a role's permission changes.
     *
     * @param role unique identifier for the role
     * @return list of audit records
     */
    public List<String> getAuditLog(String role) {
        Objects.requireNonNull(role, "role cannot be null");

        RolePermissions rolePerms = roles.get(role);
        if (rolePerms == null) {
            return Collections.emptyList();
        }

        return rolePerms.getAuditLog().stream()
                .map(PermissionAudit::toString)
                .toList();
    }

    /**
     * Gets count of roles under permission management.
     *
     * @return number of roles
     */
    public int getRoleCount() {
        return roles.size();
    }

    /**
     * Generates a permission optimization report for compliance.
     *
     * @return human-readable report of permission state across all roles
     */
    public String generateOptimizationReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== Permission Optimization Report ===\n");
        report.append("Generated: ").append(new Date()).append("\n\n");

        for (RolePermissions rolePerms : roles.values()) {
            int granted = rolePerms.getGrantedPermissions().size();
            int used = rolePerms.getUsedPermissions().size();
            int unused = rolePerms.getUnusedPermissions().size();

            report.append("Role: ").append(rolePerms.role).append("\n");
            report.append("  Granted: ").append(granted).append("\n");
            report.append("  Used: ").append(used).append("\n");
            report.append("  Unused: ").append(unused).append("\n");

            if (unused > 0) {
                report.append("  Unused permissions: ").append(rolePerms.getUnusedPermissions()).append("\n");
            }
            report.append("\n");
        }

        return report.toString();
    }
}
