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

import java.util.concurrent.ConcurrentHashMap;

/**
 * Default in-JVM implementation of {@link GlobalCaseRegistry}.
 *
 * <p>Uses a {@link ConcurrentHashMap} pre-sized for 1M entries.
 * At ~120 bytes per entry (UUID string + tenant string + map overhead),
 * the map requires approximately 120 MB of heap at 1M cases — acceptable.</p>
 *
 * <p>This implementation is suitable for single-node deployments. For multi-node
 * deployments where different nodes must look up tenants for cases they don't own,
 * replace with {@code RedisGlobalCaseRegistry} (in {@code yawl-redis-adapter}).</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see GlobalCaseRegistry
 */
public final class LocalCaseRegistry implements GlobalCaseRegistry {

    /** Pre-sized for 1M entries with 0.75 load factor → 1.33M buckets. */
    private final ConcurrentHashMap<String, String> _map =
            new ConcurrentHashMap<>(1_333_334);

    /**
     * {@inheritDoc}
     *
     * @throws NullPointerException if caseId or tenantId is null
     */
    @Override
    public void register(String caseId, String tenantId) {
        if (caseId == null) throw new NullPointerException("caseId must not be null");
        if (tenantId == null) throw new NullPointerException("tenantId must not be null");
        _map.put(caseId, tenantId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String lookupTenant(String caseId) {
        if (caseId == null) throw new NullPointerException("caseId must not be null");
        return _map.get(caseId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deregister(String caseId) {
        if (caseId == null) throw new NullPointerException("caseId must not be null");
        _map.remove(caseId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long size() {
        return _map.size();
    }
}
