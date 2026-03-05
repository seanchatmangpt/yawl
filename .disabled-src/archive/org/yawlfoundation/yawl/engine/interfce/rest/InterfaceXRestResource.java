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

import jakarta.servlet.ServletContext;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.engine.interfce.EngineGateway;
import org.yawlfoundation.yawl.engine.interfce.EngineGatewayImpl;
import org.yawlfoundation.yawl.exceptions.YPersistenceException;

import java.rmi.RemoteException;

/**
 * REST API for Interface X (Extended operations).
 * Provides advanced engine operations and exception handling by delegating to
 * the YAWL EngineGateway.
 *
 * Interface X endpoints for advanced workflow operations:
 * - Exception handling and work item cancellation
 * - Work item suspension and resumption
 * - Work item data updates
 * - InterfaceX listener management
 *
 * @author Michael Adams
 * @date 16/02/2026
 */
@Path("/ix")
@Produces(MediaType.APPLICATION_XML)
@Consumes(MediaType.APPLICATION_XML)
public class InterfaceXRestResource {

    private static final Logger _logger = LogManager.getLogger(InterfaceXRestResource.class);

    @Context
    private ServletContext _servletContext;

    /**
     * Obtain the engine gateway from servlet context, initialising if necessary.
     * Delegates to the same EngineGatewayImpl used by InterfaceX_EngineSideServer.
     *
     * @return the EngineGateway instance
     * @throws IllegalStateException if the engine cannot be initialised
     */
    private EngineGateway getEngine() {
        EngineGateway engine = (EngineGateway) _servletContext.getAttribute("engine");
        if (engine == null) {
            try {
                String persistOn = _servletContext.getInitParameter("EnablePersistence");
                boolean enablePersist = "true".equalsIgnoreCase(persistOn);
                engine = new EngineGatewayImpl(enablePersist);
                _servletContext.setAttribute("engine", engine);
                _logger.info("EngineGateway initialised by InterfaceXRestResource");
            } catch (YPersistenceException e) {
                _logger.fatal("Failed to initialise EngineGateway in InterfaceXRestResource: {}", e.getMessage(), e);
                throw new IllegalStateException(
                        "YAWL Engine could not be initialised. Check persistence configuration and database connectivity.", e);
            }
        }
        return engine;
    }

    /**
     * Cancel a work item with optional exception data.
     * Delegates to EngineGateway.cancelWorkItem.
     *
     * @param itemId the work item ID
     * @param sessionHandle the session handle
     * @param fail whether to mark the cancellation as a failure ("true"/"false")
     * @param data optional work item data XML
     * @return Response indicating success or failure
     */
    @POST
    @Path("/workitems/{itemId}/cancel")
    public Response cancelWorkItem(
            @PathParam("itemId") String itemId,
            @QueryParam("sessionHandle") String sessionHandle,
            @QueryParam("fail") @DefaultValue("false") String fail,
            String data) {
        _logger.debug("cancelWorkItem called, itemId={}, sessionHandle={}", itemId, sessionHandle);
        if (sessionHandle == null || sessionHandle.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("<failure>Session handle is required</failure>")
                    .build();
        }
        if (itemId == null || itemId.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("<failure>Work item ID is required</failure>")
                    .build();
        }
        try {
            String result = getEngine().cancelWorkItem(itemId, data, fail, sessionHandle);
            _logger.info("cancelWorkItem completed for itemId={}", itemId);
            return Response.ok(result).build();
        } catch (RemoteException e) {
            _logger.error("cancelWorkItem failed for itemId={}: {}", itemId, e.getMessage(), e);
            throw new IllegalStateException("Engine call failed during work item cancellation for '" + itemId + "': " + e.getMessage(), e);
        }
    }

    /**
     * Suspend a work item.
     * Delegates to EngineGateway.suspendWorkItem.
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
        _logger.debug("suspendWorkItem called, itemId={}, sessionHandle={}", itemId, sessionHandle);
        if (sessionHandle == null || sessionHandle.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("<failure>Session handle is required</failure>")
                    .build();
        }
        if (itemId == null || itemId.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("<failure>Work item ID is required</failure>")
                    .build();
        }
        try {
            String result = getEngine().suspendWorkItem(itemId, sessionHandle);
            _logger.info("suspendWorkItem completed for itemId={}", itemId);
            return Response.ok(result).build();
        } catch (RemoteException e) {
            _logger.error("suspendWorkItem failed for itemId={}: {}", itemId, e.getMessage(), e);
            throw new IllegalStateException("Engine call failed during work item suspension for '" + itemId + "': " + e.getMessage(), e);
        }
    }

    /**
     * Resume a suspended work item.
     * Delegates to EngineGateway.unsuspendWorkItem.
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
        _logger.debug("resumeWorkItem called, itemId={}, sessionHandle={}", itemId, sessionHandle);
        if (sessionHandle == null || sessionHandle.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("<failure>Session handle is required</failure>")
                    .build();
        }
        if (itemId == null || itemId.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("<failure>Work item ID is required</failure>")
                    .build();
        }
        try {
            String result = getEngine().unsuspendWorkItem(itemId, sessionHandle);
            _logger.info("resumeWorkItem completed for itemId={}", itemId);
            return Response.ok(result).build();
        } catch (RemoteException e) {
            _logger.error("resumeWorkItem failed for itemId={}: {}", itemId, e.getMessage(), e);
            throw new IllegalStateException("Engine call failed during work item resumption for '" + itemId + "': " + e.getMessage(), e);
        }
    }

    /**
     * Update work item data.
     * Delegates to EngineGateway.updateWorkItemData.
     *
     * @param itemId the work item ID
     * @param sessionHandle the session handle
     * @param data the updated data XML
     * @return Response indicating success or failure
     */
    @PUT
    @Path("/workitems/{itemId}/data")
    public Response updateWorkItemData(
            @PathParam("itemId") String itemId,
            @QueryParam("sessionHandle") String sessionHandle,
            String data) {
        _logger.debug("updateWorkItemData called, itemId={}, sessionHandle={}", itemId, sessionHandle);
        if (sessionHandle == null || sessionHandle.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("<failure>Session handle is required</failure>")
                    .build();
        }
        if (itemId == null || itemId.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("<failure>Work item ID is required</failure>")
                    .build();
        }
        if (data == null || data.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("<failure>Data XML is required</failure>")
                    .build();
        }
        String result = getEngine().updateWorkItemData(itemId, data, sessionHandle);
        _logger.info("updateWorkItemData completed for itemId={}", itemId);
        return Response.ok(result).build();
    }

    /**
     * Register an InterfaceX listener URI with the engine.
     * Delegates to EngineGateway.addInterfaceXListener.
     *
     * @param sessionHandle the session handle
     * @param listenerUri the URI to register as an InterfaceX observer
     * @return Response indicating success or failure
     */
    @POST
    @Path("/listeners")
    @Consumes(MediaType.TEXT_PLAIN)
    public Response addListener(
            @QueryParam("sessionHandle") String sessionHandle,
            String listenerUri) {
        _logger.debug("addListener called, listenerUri={}, sessionHandle={}", listenerUri, sessionHandle);
        if (sessionHandle == null || sessionHandle.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("<failure>Session handle is required</failure>")
                    .build();
        }
        if (listenerUri == null || listenerUri.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("<failure>Listener URI is required</failure>")
                    .build();
        }
        String result = getEngine().addInterfaceXListener(listenerUri);
        _logger.info("addListener completed for listenerUri={}", listenerUri);
        return Response.ok(result).build();
    }

    /**
     * Unregister an InterfaceX listener URI from the engine.
     * Delegates to EngineGateway.removeInterfaceXListener.
     *
     * @param sessionHandle the session handle
     * @param listenerUri the URI to remove from InterfaceX observers
     * @return Response indicating success or failure
     */
    @DELETE
    @Path("/listeners")
    public Response removeListener(
            @QueryParam("sessionHandle") String sessionHandle,
            @QueryParam("uri") String listenerUri) {
        _logger.debug("removeListener called, listenerUri={}, sessionHandle={}", listenerUri, sessionHandle);
        if (sessionHandle == null || sessionHandle.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("<failure>Session handle is required</failure>")
                    .build();
        }
        if (listenerUri == null || listenerUri.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("<failure>Listener URI is required</failure>")
                    .build();
        }
        String result = getEngine().removeInterfaceXListener(listenerUri);
        _logger.info("removeListener completed for listenerUri={}", listenerUri);
        return Response.ok(result).build();
    }
}
