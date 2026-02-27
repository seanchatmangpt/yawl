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

package org.yawlfoundation.yawl.engine.spi;

import org.yawlfoundation.yawl.elements.state.YIdentifier;

/**
 * SPI for a cluster-wide registry mapping case IDs to tenant IDs.
 *
 * <p><b>Default implementation</b>: {@link LocalCaseRegistry} uses a
 * {@code ConcurrentHashMap} (1M entries × ~120 bytes ≈ 120 MB heap). Sufficient
 * for single-node deployments.</p>
 *
 * <p><b>Optional adapter</b>: {@code RedisGlobalCaseRegistry} (in
 * {@code yawl-redis-adapter}) uses Spring Data Redis {@code HashOperations} for
 * multi-node deployments where different nodes process cases for the same tenant.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see LocalCaseRegistry
 */
public interface GlobalCaseRegistry {

    /**
     * Registers a new case with its owning tenant.
     *
     * @param caseId   the case identifier as a string; must not be {@code null}
     * @param tenantId the tenant identifier; must not be {@code null}
     */
    void register(String caseId, String tenantId);

    /**
     * Looks up the tenant that owns a given case.
     *
     * @param caseId the case identifier as a string; must not be {@code null}
     * @return the tenant ID, or {@code null} if the case is not registered
     */
    String lookupTenant(String caseId);

    /**
     * Removes the registration for a completed or cancelled case.
     *
     * @param caseId the case identifier as a string; must not be {@code null}
     */
    void deregister(String caseId);

    /**
     * Returns the number of active cases in the registry.
     *
     * @return active case count
     */
    long size();

    // --- YIdentifier convenience overloads ---

    default void register(YIdentifier caseId, String tenantId) {
        if (caseId == null) throw new NullPointerException("caseId must not be null");
        register(caseId.toString(), tenantId);
    }

    default String lookupTenant(YIdentifier caseId) {
        if (caseId == null) throw new NullPointerException("caseId must not be null");
        return lookupTenant(caseId.toString());
    }

    default void deregister(YIdentifier caseId) {
        if (caseId == null) throw new NullPointerException("caseId must not be null");
        deregister(caseId.toString());
    }
}
