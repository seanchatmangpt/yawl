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

package org.yawlfoundation.yawl.schema;

import org.yawlfoundation.yawl.engine.YNetRunner;

/**
 * Entry point for the {@code yawl-schema-contracts} module.
 *
 * <p>Call {@link #install(YNetRunner)} once at engine startup to wire schema
 * contract validation into the task execution pipeline. After installation, any
 * task whose decomposition has {@code schema.input} or {@code schema.output}
 * attributes will be validated automatically at task boundaries.</p>
 *
 * <h2>Usage</h2>
 * <pre>
 * // In engine startup (e.g., YEngine initialisation):
 * SchemaContractRegistry registry = SchemaContractsModule.install(netRunner);
 *
 * // Optional: preload contracts at startup to detect missing files early
 * registry.preload(List.of("contracts/orders-v2.yaml", "contracts/fulfillment-v1.yaml"));
 * </pre>
 *
 * @since 6.0.0
 */
public final class SchemaContractsModule {

    private SchemaContractsModule() {}

    /**
     * Installs a {@link SchemaValidationInterceptor} into the given {@link YNetRunner}.
     *
     * <p>Creates a new {@link SchemaContractRegistry} backed by the native data-modelling
     * service, wraps it in a {@link SchemaValidationInterceptor}, and registers the
     * interceptor with the runner. Interceptors are called synchronously at task boundaries.</p>
     *
     * @param runner the net runner to install the interceptor into
     * @return the registry used by the interceptor (caller may {@link SchemaContractRegistry#preload}
     *         contract paths or inspect the cache)
     * @throws UnsupportedOperationException if the native data-modelling library is absent
     */
    public static SchemaContractRegistry install(YNetRunner runner) {
        SchemaContractRegistry registry = new SchemaContractRegistry();
        SchemaValidationInterceptor interceptor = new SchemaValidationInterceptor(registry);
        runner.registerSchemaInterceptor(interceptor);
        return registry;
    }
}
