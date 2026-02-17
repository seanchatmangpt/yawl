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
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.interfce.EngineGateway;
import org.yawlfoundation.yawl.engine.interfce.EngineGatewayImpl;
import org.yawlfoundation.yawl.exceptions.YPersistenceException;

import java.rmi.RemoteException;

/**
 * REST API for Interface A (Design operations).
 * Provides specification upload, validation, and management by delegating to
 * the YAWL EngineGateway.
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
@Produces(MediaType.APPLICATION_XML)
@Consumes(MediaType.APPLICATION_XML)
public class InterfaceARestResource {

    private static final Logger _logger = LogManager.getLogger(InterfaceARestResource.class);

    @Context
    private ServletContext _servletContext;

    /**
     * Obtain the engine gateway from servlet context, initialising if necessary.
     * Delegates to the same EngineGatewayImpl used by the servlet-based Interface A server.
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
                _logger.info("EngineGateway initialised by InterfaceARestResource");
            } catch (YPersistenceException e) {
                _logger.fatal("Failed to initialise EngineGateway in InterfaceARestResource: {}", e.getMessage(), e);
                throw new IllegalStateException(
                        "YAWL Engine could not be initialised. Check persistence configuration and database connectivity.", e);
            }
        }
        return engine;
    }

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
        _logger.debug("uploadSpecification called, sessionHandle={}", sessionHandle);
        if (sessionHandle == null || sessionHandle.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("<failure>Session handle is required</failure>")
                    .build();
        }
        if (specXML == null || specXML.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("<failure>Specification XML is required</failure>")
                    .build();
        }
        try {
            String result = getEngine().loadSpecification(specXML, sessionHandle);
            _logger.info("uploadSpecification completed, result length={}", result.length());
            return Response.ok(result).build();
        } catch (RemoteException e) {
            _logger.error("uploadSpecification failed: {}", e.getMessage(), e);
            throw new IllegalStateException("Engine call failed during specification upload: " + e.getMessage(), e);
        }
    }

    /**
     * Get all loaded specifications.
     *
     * @param sessionHandle the session handle
     * @return Response with list of specifications
     */
    @GET
    @Path("/specifications")
    public Response getSpecifications(@QueryParam("sessionHandle") String sessionHandle) {
        _logger.debug("getSpecifications called, sessionHandle={}", sessionHandle);
        if (sessionHandle == null || sessionHandle.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("<failure>Session handle is required</failure>")
                    .build();
        }
        try {
            String result = getEngine().getSpecificationList(sessionHandle);
            _logger.debug("getSpecifications completed");
            return Response.ok(result).build();
        } catch (RemoteException e) {
            _logger.error("getSpecifications failed: {}", e.getMessage(), e);
            throw new IllegalStateException("Engine call failed during specification list retrieval: " + e.getMessage(), e);
        }
    }

    /**
     * Unload a specification from the engine.
     *
     * @param specId the specification identifier
     * @param version the specification version
     * @param uri the specification URI
     * @param sessionHandle the session handle
     * @return Response indicating success or failure
     */
    @DELETE
    @Path("/specifications/{specId}")
    public Response unloadSpecification(
            @PathParam("specId") String specId,
            @QueryParam("version") String version,
            @QueryParam("uri") String uri,
            @QueryParam("sessionHandle") String sessionHandle) {
        _logger.debug("unloadSpecification called, specId={}, version={}, sessionHandle={}", specId, version, sessionHandle);
        if (sessionHandle == null || sessionHandle.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("<failure>Session handle is required</failure>")
                    .build();
        }
        if (specId == null || specId.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("<failure>Specification ID is required</failure>")
                    .build();
        }
        try {
            YSpecificationID specificationID = new YSpecificationID(specId,
                    version != null ? version : "",
                    uri != null ? uri : specId);
            String result = getEngine().unloadSpecification(specificationID, sessionHandle);
            _logger.info("unloadSpecification completed for specId={}", specId);
            return Response.ok(result).build();
        } catch (RemoteException e) {
            _logger.error("unloadSpecification failed for specId={}: {}", specId, e.getMessage(), e);
            throw new IllegalStateException("Engine call failed during specification unload for '" + specId + "': " + e.getMessage(), e);
        }
    }
}
