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
import org.yawlfoundation.yawl.logging.YLogServer;

import java.rmi.RemoteException;

/**
 * REST API for Interface E (Event/Log operations).
 * Provides process log queries and YAWL engine event access by delegating to
 * the YAWL YLogServer and EngineGateway.
 *
 * Interface E endpoints for workflow event monitoring:
 * - Query all specifications with logged data
 * - Query case instances and execution data
 * - Access YAWL process log records
 *
 * @author Michael Adams
 * @date 16/02/2026
 */
@Path("/ie")
@Produces(MediaType.APPLICATION_XML)
@Consumes(MediaType.APPLICATION_XML)
public class InterfaceERestResource {

    private static final Logger _logger = LogManager.getLogger(InterfaceERestResource.class);

    @Context
    private ServletContext _servletContext;

    /**
     * Obtain the engine gateway from servlet context, initialising if necessary.
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
                _logger.info("EngineGateway initialised by InterfaceERestResource");
            } catch (YPersistenceException e) {
                _logger.fatal("Failed to initialise EngineGateway in InterfaceERestResource: {}", e.getMessage(), e);
                throw new IllegalStateException(
                        "YAWL Engine could not be initialised. Check persistence configuration and database connectivity.", e);
            }
        }
        return engine;
    }

    /**
     * Get all specifications with their logged data.
     * Delegates to YLogServer for process log access.
     *
     * @param sessionHandle the session handle
     * @return Response with XML list of logged specifications
     */
    @GET
    @Path("/specifications")
    public Response getLoggedSpecifications(@QueryParam("sessionHandle") String sessionHandle) {
        _logger.debug("getLoggedSpecifications called, sessionHandle={}", sessionHandle);
        if (sessionHandle == null || sessionHandle.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("<failure>Session handle is required</failure>")
                    .build();
        }
        try {
            EngineGateway engine = getEngine();
            String connectionCheck = engine.checkConnection(sessionHandle);
            if (connectionCheck != null && connectionCheck.contains("failure")) {
                _logger.warn("Invalid session handle for getLoggedSpecifications: {}", sessionHandle);
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity("<failure>Invalid or expired session handle</failure>")
                        .build();
            }
            synchronized (YLogServer.getInstance().getPersistenceManager()) {
                boolean isLocalTransaction = YLogServer.getInstance().startTransaction();
                try {
                    String result = YLogServer.getInstance().getAllSpecifications();
                    _logger.debug("getLoggedSpecifications completed");
                    return Response.ok(result).build();
                } finally {
                    if (isLocalTransaction) {
                        YLogServer.getInstance().commitTransaction();
                    }
                }
            }
        } catch (RemoteException e) {
            _logger.error("getLoggedSpecifications failed during session check: {}", e.getMessage(), e);
            throw new IllegalStateException("Engine session check failed: " + e.getMessage(), e);
        }
    }

    /**
     * Get all case instances for a given specification key.
     * Delegates to YLogServer for process log access.
     *
     * @param specKey the specification log key
     * @param sessionHandle the session handle
     * @return Response with XML list of case instances
     */
    @GET
    @Path("/specifications/{specKey}/cases")
    public Response getCasesForSpecification(
            @PathParam("specKey") String specKey,
            @QueryParam("sessionHandle") String sessionHandle) {
        _logger.debug("getCasesForSpecification called, specKey={}, sessionHandle={}", specKey, sessionHandle);
        if (sessionHandle == null || sessionHandle.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("<failure>Session handle is required</failure>")
                    .build();
        }
        if (specKey == null || specKey.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("<failure>Specification key is required</failure>")
                    .build();
        }
        try {
            EngineGateway engine = getEngine();
            String connectionCheck = engine.checkConnection(sessionHandle);
            if (connectionCheck != null && connectionCheck.contains("failure")) {
                _logger.warn("Invalid session handle for getCasesForSpecification: {}", sessionHandle);
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity("<failure>Invalid or expired session handle</failure>")
                        .build();
            }
            long key;
            try {
                key = Long.parseLong(specKey);
            } catch (NumberFormatException e) {
                _logger.warn("Invalid specification key format '{}': {}", specKey, e.getMessage());
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("<failure>Specification key must be a numeric log key</failure>")
                        .build();
            }
            synchronized (YLogServer.getInstance().getPersistenceManager()) {
                boolean isLocalTransaction = YLogServer.getInstance().startTransaction();
                try {
                    String result = YLogServer.getInstance().getNetInstancesOfSpecification(key);
                    _logger.debug("getCasesForSpecification completed for specKey={}", specKey);
                    return Response.ok(result).build();
                } finally {
                    if (isLocalTransaction) {
                        YLogServer.getInstance().commitTransaction();
                    }
                }
            }
        } catch (RemoteException e) {
            _logger.error("getCasesForSpecification failed for specKey={}: {}", specKey, e.getMessage(), e);
            throw new IllegalStateException("Engine session check failed: " + e.getMessage(), e);
        }
    }

    /**
     * Get the active InterfaceX listeners registered with the engine.
     * These are observer URIs for extended exception handling.
     *
     * @param sessionHandle the session handle
     * @return Response with XML list of listener URIs
     */
    @GET
    @Path("/listeners")
    public Response getListeners(@QueryParam("sessionHandle") String sessionHandle) {
        _logger.debug("getListeners called, sessionHandle={}", sessionHandle);
        if (sessionHandle == null || sessionHandle.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("<failure>Session handle is required</failure>")
                    .build();
        }
        try {
            EngineGateway engine = getEngine();
            String connectionCheck = engine.checkConnection(sessionHandle);
            if (connectionCheck != null && connectionCheck.contains("failure")) {
                _logger.warn("Invalid session handle for getListeners: {}", sessionHandle);
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity("<failure>Invalid or expired session handle</failure>")
                        .build();
            }
            // Return registered YAWL services as the observable event listeners
            String result = engine.getYAWLServices(sessionHandle);
            _logger.debug("getListeners completed");
            return Response.ok(result).build();
        } catch (RemoteException e) {
            _logger.error("getListeners failed: {}", e.getMessage(), e);
            throw new IllegalStateException("Engine call failed during listener retrieval: " + e.getMessage(), e);
        }
    }
}
