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

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceBInterop;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API for Interface B (Client operations).
 * Provides modern JAX-RS endpoints for work item and case management.
 *
 * This class implements the standard Interface B operations using JAX-RS annotations,
 * replacing legacy servlet-based implementations while maintaining backward compatibility.
 *
 * Key endpoints:
 * - Session management (connect, disconnect)
 * - Work item operations (get, checkout, checkin, complete)
 * - Case operations (launch, cancel, get data)
 * - Query operations (list work items by case, spec, task)
 *
 * @author Michael Adams
 * @date 16/02/2026
 */
@Path("/ib")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InterfaceBRestResource {

    private static final Logger _logger = LogManager.getLogger(InterfaceBRestResource.class);
    private final InterfaceBInterop _ibInterop;
    private final ObjectMapper _mapper;

    /**
     * Constructor. Initializes Interface B interop and JSON mapper.
     */
    public InterfaceBRestResource() {
        _ibInterop = new InterfaceBInterop();
        _mapper = new ObjectMapper();
    }

    /**
     * Connect to the engine and obtain a session handle.
     *
     * @param credentials JSON object with 'userid' and 'password' fields
     * @return Response with session handle or error
     */
    @POST
    @Path("/connect")
    public Response connect(String credentials) {
        try {
            Map<String, String> creds = _mapper.readValue(credentials, Map.class);
            String userid = creds.get("userid");
            String password = creds.get("password");

            if (userid == null || password == null) {
                return badRequest("Missing userid or password");
            }

            String sessionHandle = _ibInterop.connect(userid, password);

            if (sessionHandle != null && !sessionHandle.contains("failure")) {
                Map<String, String> result = new HashMap<>();
                result.put("sessionHandle", sessionHandle);
                return Response.ok(_mapper.writeValueAsString(result)).build();
            } else {
                return unauthorized("Invalid credentials");
            }

        } catch (IOException e) {
            _logger.error("Connection error", e);
            return serverError("Connection failed: " + e.getMessage());
        }
    }

    /**
     * Disconnect a session from the engine.
     *
     * @param sessionHandle the session handle to disconnect
     * @return Response indicating success or failure
     */
    @POST
    @Path("/disconnect")
    public Response disconnect(@QueryParam("sessionHandle") String sessionHandle) {
        try {
            if (!validateSession(sessionHandle)) {
                return unauthorized("Invalid or missing session handle");
            }

            String result = _ibInterop.disconnect(sessionHandle);

            if (successful(result)) {
                return Response.ok("{\"status\": \"disconnected\"}").build();
            } else {
                return serverError("Disconnect failed: " + result);
            }

        } catch (IOException e) {
            _logger.error("Disconnect error", e);
            return serverError("Disconnect failed: " + e.getMessage());
        }
    }

    /**
     * Get all live work items in the engine.
     *
     * @param sessionHandle the session handle
     * @return Response with list of work items or error
     */
    @GET
    @Path("/workitems")
    public Response getWorkItems(@QueryParam("sessionHandle") String sessionHandle) {
        try {
            if (!validateSession(sessionHandle)) {
                return unauthorized("Invalid or missing session handle");
            }

            List<WorkItemRecord> workItems = _ibInterop.getLiveWorkItems(sessionHandle);

            return Response.ok(_mapper.writeValueAsString(workItems)).build();

        } catch (IOException e) {
            _logger.error("Error getting work items", e);
            return serverError("Failed to get work items: " + e.getMessage());
        }
    }

    /**
     * Get work items for a specific case.
     *
     * @param caseId the case ID
     * @param sessionHandle the session handle
     * @return Response with list of work items or error
     */
    @GET
    @Path("/cases/{caseId}/workitems")
    public Response getWorkItemsForCase(
            @PathParam("caseId") String caseId,
            @QueryParam("sessionHandle") String sessionHandle) {
        try {
            if (!validateSession(sessionHandle)) {
                return unauthorized("Invalid or missing session handle");
            }

            List<WorkItemRecord> workItems = _ibInterop.getWorkItemsForCase(caseId, sessionHandle);

            return Response.ok(_mapper.writeValueAsString(workItems)).build();

        } catch (IOException e) {
            _logger.error("Error getting work items for case " + caseId, e);
            return serverError("Failed to get work items: " + e.getMessage());
        }
    }

    /**
     * Get a specific work item by ID.
     *
     * @param itemId the work item ID
     * @param sessionHandle the session handle
     * @return Response with work item or error
     */
    @GET
    @Path("/workitems/{itemId}")
    public Response getWorkItem(
            @PathParam("itemId") String itemId,
            @QueryParam("sessionHandle") String sessionHandle) {
        try {
            if (!validateSession(sessionHandle)) {
                return unauthorized("Invalid or missing session handle");
            }

            String workItemXML = _ibInterop.getWorkItem(itemId, sessionHandle);

            if (successful(workItemXML)) {
                return Response.ok(workItemXML)
                        .type(MediaType.APPLICATION_XML)
                        .build();
            } else {
                return notFound("Work item not found: " + itemId);
            }

        } catch (IOException e) {
            _logger.error("Error getting work item " + itemId, e);
            return serverError("Failed to get work item: " + e.getMessage());
        }
    }

    /**
     * Check out a work item (start working on it).
     *
     * @param itemId the work item ID
     * @param sessionHandle the session handle
     * @return Response with work item data or error
     */
    @POST
    @Path("/workitems/{itemId}/checkout")
    public Response checkoutWorkItem(
            @PathParam("itemId") String itemId,
            @QueryParam("sessionHandle") String sessionHandle) {
        try {
            if (!validateSession(sessionHandle)) {
                return unauthorized("Invalid or missing session handle");
            }

            String result = _ibInterop.startWorkItem(itemId, sessionHandle);

            if (successful(result)) {
                return Response.ok(result).type(MediaType.APPLICATION_XML).build();
            } else {
                return badRequest("Failed to checkout work item: " + result);
            }

        } catch (IOException e) {
            _logger.error("Error checking out work item " + itemId, e);
            return serverError("Checkout failed: " + e.getMessage());
        }
    }

    /**
     * Check in a work item (update data without completing).
     *
     * @param itemId the work item ID
     * @param sessionHandle the session handle
     * @param dataString the data to check in (XML format)
     * @return Response indicating success or failure
     */
    @POST
    @Path("/workitems/{itemId}/checkin")
    @Consumes(MediaType.APPLICATION_XML)
    public Response checkinWorkItem(
            @PathParam("itemId") String itemId,
            @QueryParam("sessionHandle") String sessionHandle,
            String dataString) {
        try {
            if (!validateSession(sessionHandle)) {
                return unauthorized("Invalid or missing session handle");
            }

            String result = _ibInterop.updateWorkItemData(itemId, dataString, sessionHandle);

            if (successful(result)) {
                return Response.ok("{\"status\": \"checked-in\"}").build();
            } else {
                return badRequest("Failed to checkin work item: " + result);
            }

        } catch (IOException e) {
            _logger.error("Error checking in work item " + itemId, e);
            return serverError("Checkin failed: " + e.getMessage());
        }
    }

    /**
     * Complete a work item.
     *
     * @param itemId the work item ID
     * @param sessionHandle the session handle
     * @param dataString the completion data (XML format)
     * @return Response indicating success or failure
     */
    @POST
    @Path("/workitems/{itemId}/complete")
    @Consumes(MediaType.APPLICATION_XML)
    public Response completeWorkItem(
            @PathParam("itemId") String itemId,
            @QueryParam("sessionHandle") String sessionHandle,
            String dataString) {
        try {
            if (!validateSession(sessionHandle)) {
                return unauthorized("Invalid or missing session handle");
            }

            String result = _ibInterop.completeWorkItem(itemId, dataString, sessionHandle);

            if (successful(result)) {
                return Response.ok("{\"status\": \"completed\"}").build();
            } else {
                return badRequest("Failed to complete work item: " + result);
            }

        } catch (IOException e) {
            _logger.error("Error completing work item " + itemId, e);
            return serverError("Completion failed: " + e.getMessage());
        }
    }

    /**
     * Get case data for a specific case.
     *
     * @param caseId the case ID
     * @param sessionHandle the session handle
     * @return Response with case data or error
     */
    @GET
    @Path("/cases/{caseId}")
    public Response getCaseData(
            @PathParam("caseId") String caseId,
            @QueryParam("sessionHandle") String sessionHandle) {
        try {
            if (!validateSession(sessionHandle)) {
                return unauthorized("Invalid or missing session handle");
            }

            String caseData = _ibInterop.getCaseData(caseId, sessionHandle);

            if (successful(caseData)) {
                return Response.ok(caseData).type(MediaType.APPLICATION_XML).build();
            } else {
                return notFound("Case not found: " + caseId);
            }

        } catch (IOException e) {
            _logger.error("Error getting case data for " + caseId, e);
            return serverError("Failed to get case data: " + e.getMessage());
        }
    }

    /**
     * Cancel a case.
     *
     * @param caseId the case ID
     * @param sessionHandle the session handle
     * @return Response indicating success or failure
     */
    @POST
    @Path("/cases/{caseId}/cancel")
    public Response cancelCase(
            @PathParam("caseId") String caseId,
            @QueryParam("sessionHandle") String sessionHandle) {
        try {
            if (!validateSession(sessionHandle)) {
                return unauthorized("Invalid or missing session handle");
            }

            String result = _ibInterop.cancelCase(caseId, sessionHandle);

            if (successful(result)) {
                return Response.ok("{\"status\": \"cancelled\"}").build();
            } else {
                return badRequest("Failed to cancel case: " + result);
            }

        } catch (IOException e) {
            _logger.error("Error cancelling case " + caseId, e);
            return serverError("Cancel failed: " + e.getMessage());
        }
    }

    // Helper methods

    private boolean validateSession(String sessionHandle) {
        return sessionHandle != null && !sessionHandle.isEmpty();
    }

    private boolean successful(String result) {
        return result != null && !result.contains("<failure>");
    }

    private Response badRequest(String message) {
        try {
            Map<String, String> error = new HashMap<>();
            error.put("error", message);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(_mapper.writeValueAsString(error))
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"Bad request\"}")
                    .build();
        }
    }

    private Response unauthorized(String message) {
        try {
            Map<String, String> error = new HashMap<>();
            error.put("error", message);
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(_mapper.writeValueAsString(error))
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("{\"error\": \"Unauthorized\"}")
                    .build();
        }
    }

    private Response notFound(String message) {
        try {
            Map<String, String> error = new HashMap<>();
            error.put("error", message);
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(_mapper.writeValueAsString(error))
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\": \"Not found\"}")
                    .build();
        }
    }

    private Response serverError(String message) {
        try {
            Map<String, String> error = new HashMap<>();
            error.put("error", message);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(_mapper.writeValueAsString(error))
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"Internal server error\"}")
                    .build();
        }
    }
}
