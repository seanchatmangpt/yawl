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

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * REST API for Interface X (Extended operations).
 * Provides advanced engine operations and exception handling.
 *
 * Interface X endpoints for advanced workflow operations:
 * - Exception handling
 * - Work item suspension/resumption
 * - Dynamic task creation
 * - Advanced case management
 *
 * @author Michael Adams
 * @date 16/02/2026
 */
@Path("/ix")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InterfaceXRestResource {

    /**
     * Handle an exception for a work item.
     *
     * @param itemId the work item ID
     * @param sessionHandle the session handle
     * @param exceptionData exception details
     * @return Response indicating success or failure
     */
    @POST
    @Path("/workitems/{itemId}/exceptions")
    public Response handleException(
            @PathParam("itemId") String itemId,
            @QueryParam("sessionHandle") String sessionHandle,
            String exceptionData) {
        throw new UnsupportedOperationException(
                "Interface X REST endpoints not yet implemented. Use InterfaceX_EngineSideServer servlet.");
    }

    /**
     * Suspend a work item.
     *
     * @param itemId the work item ID
     * @param sessionHandle the session handle
     * @return Response indicating success or failure
     */
    @POST
    @Path("/workitems/{itemId}/suspend")
    public Response suspendWorkItem(
            @PathParam("itemId") String itemId,
            @QueryParam("sessionHandle") String sessionHandle) {
        throw new UnsupportedOperationException(
                "Interface X REST endpoints not yet implemented. Use InterfaceX_EngineSideServer servlet.");
    }

    /**
     * Resume a suspended work item.
     *
     * @param itemId the work item ID
     * @param sessionHandle the session handle
     * @return Response indicating success or failure
     */
    @POST
    @Path("/workitems/{itemId}/resume")
    public Response resumeWorkItem(
            @PathParam("itemId") String itemId,
            @QueryParam("sessionHandle") String sessionHandle) {
        throw new UnsupportedOperationException(
                "Interface X REST endpoints not yet implemented. Use InterfaceX_EngineSideServer servlet.");
    }
}
