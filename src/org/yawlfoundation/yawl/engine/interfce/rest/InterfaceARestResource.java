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
 * REST API for Interface A (Design operations).
 * Provides specification upload, validation, and management.
 *
 * Interface A endpoints for workflow design-time operations:
 * - Upload specifications
 * - Validate YAWL specifications
 * - Load/unload specifications
 * - Query loaded specifications
 *
 * @author Michael Adams
 * @date 16/02/2026
 */
@Path("/ia")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InterfaceARestResource {

    /**
     * Upload a YAWL specification to the engine.
     *
     * @param sessionHandle the session handle
     * @param specXML the specification XML
     * @return Response indicating success or failure
     */
    @POST
    @Path("/specifications")
    @Consumes(MediaType.APPLICATION_XML)
    public Response uploadSpecification(
            @QueryParam("sessionHandle") String sessionHandle,
            String specXML) {
        throw new UnsupportedOperationException(
                "Interface A REST endpoints not yet implemented. Use InterfaceA_EngineBasedServer servlet.");
    }

    /**
     * Get all loaded specifications.
     *
     * @param sessionHandle the session handle
     * @return Response with list of specifications or error
     */
    @GET
    @Path("/specifications")
    public Response getSpecifications(@QueryParam("sessionHandle") String sessionHandle) {
        throw new UnsupportedOperationException(
                "Interface A REST endpoints not yet implemented. Use InterfaceA_EngineBasedServer servlet.");
    }

    /**
     * Unload a specification from the engine.
     *
     * @param specId the specification ID
     * @param sessionHandle the session handle
     * @return Response indicating success or failure
     */
    @DELETE
    @Path("/specifications/{specId}")
    public Response unloadSpecification(
            @PathParam("specId") String specId,
            @QueryParam("sessionHandle") String sessionHandle) {
        throw new UnsupportedOperationException(
                "Interface A REST endpoints not yet implemented. Use InterfaceA_EngineBasedServer servlet.");
    }
}
