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

package org.yawlfoundation.yawl.engine;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.engine.spi.GlobalCaseRegistry;
import org.yawlfoundation.yawl.engine.spi.LocalCaseRegistry;

import java.util.HashSet;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * Provides tenant isolation and authorization context for multi-tenant YAWL deployments.
 * Ensures complete data isolation between tenants and prevents unauthorized access.
 *
 * <p><b>Security Properties</b>:
 * <ul>
 *   <li>Complete isolation of case data per tenant</li>
 *   <li>Tenant authorization checks on all case operations</li>
 *   <li>Prevents cross-tenant data leakage (Customer A cannot read Customer B data)</li>
 *   <li>Thread-safe concurrent tenant operations</li>
 * </ul>
 *
 * <p><b>Usage</b>:
 * <pre>
 *   TenantContext context = new TenantContext("customer-123");
 *   context.registerCase(caseID);
 *   if (context.isAuthorized(caseID)) {
 *       // Safe to process case
 *   }
 * </pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class TenantContext {

    private static final Logger LOG = LogManager.getLogger(TenantContext.class);

    // Tenant identifier (GCP customer ID or account ID)
    private final String tenantId;

    // Cases owned by this tenant
    private final Set<String> authorizedCases;

    // Specifications accessible by this tenant
    private final Set<String> authorizedSpecifications;

    // Pluggable global case→tenant registry (default: LocalCaseRegistry / 1M-entry ConcurrentHashMap).
    // Swap in RedisGlobalCaseRegistry at runtime by placing yawl-redis-adapter on the classpath.
    private static final GlobalCaseRegistry CASE_REGISTRY =
            ServiceLoader.load(GlobalCaseRegistry.class)
                         .findFirst()
                         .orElseGet(LocalCaseRegistry::new);

    /**
     * Creates a new TenantContext for a specific tenant.
     *
     * @param tenantId The unique identifier for the tenant (e.g., GCP customer ID)
     * @throws IllegalArgumentException if tenantId is null or empty
     */
    public TenantContext(String tenantId) {
        if (tenantId == null || tenantId.trim().isEmpty()) {
            throw new IllegalArgumentException("tenantId cannot be null or empty");
        }
        this.tenantId = tenantId.trim();
        this.authorizedCases = new ConcurrentSkipListSet<>();
        this.authorizedSpecifications = new ConcurrentSkipListSet<>();

        LOG.debug("Created TenantContext for tenant: {}", this.tenantId);
    }

    /**
     * Returns the tenant ID.
     *
     * @return the unique tenant identifier
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Registers a case as belonging to this tenant.
     * Must be called when a new case is created.
     *
     * @param caseID The case identifier to register
     * @throws IllegalArgumentException if caseID is null or empty
     */
    public void registerCase(String caseID) {
        if (caseID == null || caseID.trim().isEmpty()) {
            throw new IllegalArgumentException("caseID cannot be null or empty");
        }

        String normalizedCaseID = caseID.trim();
        authorizedCases.add(normalizedCaseID);
        CASE_REGISTRY.register(normalizedCaseID, tenantId);

        LOG.debug("Registered case {} for tenant {}", normalizedCaseID, tenantId);
    }

    /**
     * Registers a case (YIdentifier form).
     *
     * @param caseID The case identifier to register
     */
    public void registerCase(YIdentifier caseID) {
        if (caseID != null) {
            registerCase(caseID.toString());
        }
    }

    /**
     * Checks if a case is authorized for this tenant.
     * Returns true only if the case was registered by this tenant.
     *
     * @param caseID The case identifier to check
     * @return true if this tenant owns the case, false otherwise
     */
    public boolean isAuthorized(String caseID) {
        if (caseID == null || caseID.trim().isEmpty()) {
            return false;
        }

        String normalizedCaseID = caseID.trim();

        // Check local authorization first (fast path)
        if (authorizedCases.contains(normalizedCaseID)) {
            return true;
        }

        // Check global case-to-tenant mapping
        String owner = CASE_REGISTRY.lookupTenant(normalizedCaseID);
        if (owner != null && owner.equals(tenantId)) {
            // Cache locally for faster future lookups
            authorizedCases.add(normalizedCaseID);
            return true;
        }

        LOG.warn("Unauthorized access attempt: tenant {} tried to access case {}",
                tenantId, normalizedCaseID);
        return false;
    }

    /**
     * Checks if a case is authorized (YIdentifier form).
     *
     * @param caseID The case identifier to check
     * @return true if this tenant owns the case, false otherwise
     */
    public boolean isAuthorized(YIdentifier caseID) {
        return caseID != null && isAuthorized(caseID.toString());
    }

    /**
     * Deregisters a case (typically when case is archived/deleted).
     * Removes the case from both local and global authorization mappings.
     *
     * @param caseID The case identifier to deregister
     */
    public void deregisterCase(String caseID) {
        if (caseID == null || caseID.trim().isEmpty()) {
            return;
        }

        String normalizedCaseID = caseID.trim();
        authorizedCases.remove(normalizedCaseID);
        CASE_REGISTRY.deregister(normalizedCaseID);

        LOG.debug("Deregistered case {} from tenant {}", normalizedCaseID, tenantId);
    }

    /**
     * Registers a specification as accessible by this tenant.
     * Called when tenant deploys a specification.
     *
     * @param specID The specification identifier to register
     */
    public void registerSpecification(String specID) {
        if (specID == null || specID.trim().isEmpty()) {
            throw new IllegalArgumentException("specID cannot be null or empty");
        }

        String normalizedSpecID = specID.trim();
        authorizedSpecifications.add(normalizedSpecID);

        LOG.debug("Registered specification {} for tenant {}", normalizedSpecID, tenantId);
    }

    /**
     * Checks if a specification is authorized for this tenant.
     *
     * @param specID The specification identifier to check
     * @return true if tenant has access to this specification
     */
    public boolean isSpecificationAuthorized(String specID) {
        if (specID == null || specID.trim().isEmpty()) {
            return false;
        }

        return authorizedSpecifications.contains(specID.trim());
    }

    /**
     * Deregisters a specification.
     * Called when specification is undeployed.
     *
     * @param specID The specification identifier to deregister
     */
    public void deregisterSpecification(String specID) {
        if (specID == null || specID.trim().isEmpty()) {
            return;
        }

        String normalizedSpecID = specID.trim();
        authorizedSpecifications.remove(normalizedSpecID);

        LOG.debug("Deregistered specification {} from tenant {}", normalizedSpecID, tenantId);
    }

    /**
     * Returns the number of cases owned by this tenant.
     *
     * @return case count
     */
    public int getCaseCount() {
        return authorizedCases.size();
    }

    /**
     * Returns all cases owned by this tenant.
     *
     * @return unmodifiable set of case IDs
     */
    public Set<String> getAuthorizedCases() {
        return new HashSet<>(authorizedCases);
    }

    /**
     * Clears all authorization data for this tenant.
     * WARNING: Use only during cleanup/testing.
     */
    public void clearAll() {
        authorizedCases.forEach(caseID -> CASE_REGISTRY.deregister(caseID));
        authorizedCases.clear();
        authorizedSpecifications.clear();

        LOG.warn("Cleared all authorization data for tenant {}", tenantId);
    }

    /**
     * Static method: Get the tenant that owns a given case.
     * Used for validation across tenants.
     *
     * @param caseID The case identifier
     * @return the owning tenant ID, or null if not found
     */
    public static String getTenantForCase(String caseID) {
        return CASE_REGISTRY.lookupTenant(caseID);
    }

    /**
     * Static method: Validate that a tenant owns a case.
     * Used by YEngine for authorization checks.
     *
     * @param tenantId The tenant identifier
     * @param caseID The case identifier
     * @return true if tenant owns the case
     * @throws IllegalArgumentException if either parameter is null/empty
     */
    public static boolean validateTenantOwnership(String tenantId, String caseID) {
        if (tenantId == null || tenantId.trim().isEmpty()) {
            throw new IllegalArgumentException("tenantId cannot be null or empty");
        }
        if (caseID == null || caseID.trim().isEmpty()) {
            throw new IllegalArgumentException("caseID cannot be null or empty");
        }

        String owner = CASE_REGISTRY.lookupTenant(caseID.trim());
        boolean authorized = tenantId.trim().equals(owner);

        if (!authorized) {
            LOG.warn("Tenant ownership validation failed: {} attempted to access case {} (owned by {})",
                    tenantId, caseID, owner);
        }

        return authorized;
    }

    @Override
    public String toString() {
        return "TenantContext{" +
                "tenantId='" + tenantId + '\'' +
                ", casesCount=" + authorizedCases.size() +
                ", specificationsCount=" + authorizedSpecifications.size() +
                '}';
    }
}
