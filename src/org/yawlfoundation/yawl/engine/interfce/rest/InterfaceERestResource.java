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
 * REST API for Interface E (Event operations).
 * Provides event subscriptions and notifications.
 *
 * Interface E endpoints for workflow event monitoring:
 * - Subscribe to events
 * - Unsubscribe from events
 * - Query active subscriptions
 *
 * @author Michael Adams
 * @date 16/02/2026
 */
@Path("/ie")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InterfaceERestResource {

    /**
     * Subscribe to engine events.
     *
     * @param sessionHandle the session handle
     * @param subscription subscription details
     * @return Response indicating success or failure
     */
    @POST
    @Path("/subscriptions")
    public Response subscribe(
            @QueryParam("sessionHandle") String sessionHandle,
            String subscription) {
        throw new UnsupportedOperationException(
                "Interface E REST endpoints not yet implemented. Use YLogGateway servlet.");
    }

    /**
     * Get all active event subscriptions.
     *
     * @param sessionHandle the session handle
     * @return Response with list of subscriptions or error
     */
    @GET
    @Path("/subscriptions")
    public Response getSubscriptions(@QueryParam("sessionHandle") String sessionHandle) {
        throw new UnsupportedOperationException(
                "Interface E REST endpoints not yet implemented. Use YLogGateway servlet.");
    }

    /**
     * Unsubscribe from events.
     *
     * @param subscriptionId the subscription ID
     * @param sessionHandle the session handle
     * @return Response indicating success or failure
     */
    @DELETE
    @Path("/subscriptions/{subscriptionId}")
    public Response unsubscribe(
            @PathParam("subscriptionId") String subscriptionId,
            @QueryParam("sessionHandle") String sessionHandle) {
        throw new UnsupportedOperationException(
                "Interface E REST endpoints not yet implemented. Use YLogGateway servlet.");
    }
}
