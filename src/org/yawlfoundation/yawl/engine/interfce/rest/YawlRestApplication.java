/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.engine.interfce.rest;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import java.util.HashSet;
import java.util.Set;

/**
 * JAX-RS Application configuration for YAWL REST APIs.
 * Registers all REST resource classes for standardized HTTP endpoints.
 *
 * Provides modern REST APIs alongside legacy servlet-based interfaces:
 * - Interface A (Design): Specification upload, validation
 * - Interface B (Client): Case and work item management
 * - Interface E (Events): Event subscriptions and notifications
 * - Interface X (Extended): Advanced engine operations
 *
 * @author Michael Adams
 * @date 16/02/2026
 */
@ApplicationPath("/api")
public class YawlRestApplication extends Application {

    @Override
    public Set<Class<?>> getClasses() {
        Set<Class<?>> resources = new HashSet<>();

        // Interface B - Client API (work items, cases)

        // Interface A - Design API (specifications)
        resources.add(InterfaceARestResource.class);

        // Interface E - Events API (subscriptions)
        resources.add(InterfaceERestResource.class);

        // Interface X - Extended API (advanced operations)
        resources.add(InterfaceXRestResource.class);

        // Exception mappers for consistent error handling
        resources.add(YawlExceptionMapper.class);

        return resources;
    }
}
