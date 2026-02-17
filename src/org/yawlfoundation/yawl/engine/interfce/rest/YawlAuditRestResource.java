/*
 * Copyright (c) 2004-2025 The YAWL Foundation. All rights reserved.
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.engine.receipts.Receipt;
import org.yawlfoundation.yawl.engine.receipts.ReceiptChain;
import org.yawlfoundation.yawl.engine.receipts.ReceiptStore;

import java.util.*;

/**
 * Receipt Query REST API: audit, compliance, and provenance access.
 *
 * BBB model endpoints:
 * - GET /audit/case/{caseId}/receipts - full chain (SELECT-only past)
 * - GET /audit/case/{caseId}/state - computed case state (recomputation = truth)
 * - GET /audit/case/{caseId}/stats - admission/rejection metrics
 * - GET /audit/receipt/{receiptId} - single receipt + provenance
 * - GET /audit/verify/{caseId} - chain integrity check
 * - GET /audit/case/{caseId}/time-range - query by timestamp
 *
 * Security: all endpoints require capability.audit.read (not implemented here).
 * Future: add signing, compliance reports, incident response exports.
 */
@Path("/audit")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class YawlAuditRestResource {
    private static final Logger logger = LogManager.getLogger(YawlAuditRestResource.class);
    private final ReceiptChain receipts;

    public YawlAuditRestResource() {
        this.receipts = ReceiptChain.getInstance();
    }

    /**
     * Retrieve full receipt chain for a case.
     * Returns in chronological order (oldest first).
     *
     * Example: GET /audit/case/case-001/receipts
     */
    @GET
    @Path("/case/{caseId}/receipts")
    public Response getChain(@PathParam("caseId") String caseId) {
        try {
            logger.info("Querying receipt chain for case: {}", caseId);

            List<Receipt> chain = receipts.getChain(caseId);
            List<Map<String, Object>> receiptsJson = chain.stream()
                .map(this::receiptToJson)
                .toList();

            Map<String, Object> response = new HashMap<>();
            response.put("caseId", caseId);
            response.put("receiptCount", receiptsJson.size());
            response.put("receipts", receiptsJson);
            response.put("chainValid", receipts.verifyChain(caseId));

            return Response.ok(response).build();
        } catch (Exception e) {
            logger.error("Error querying receipt chain", e);
            return errorResponse("Failed to query receipt chain: " + e.getMessage(), 500);
        }
    }

    /**
     * Retrieve computed case state (truth by recomputation).
     *
     * Example: GET /audit/case/case-001/state
     */
    @GET
    @Path("/case/{caseId}/state")
    public Response getCaseState(@PathParam("caseId") String caseId) {
        try {
            logger.info("Computing case state from receipts for case: {}", caseId);

            String state = receipts.getCaseState(caseId);
            Map<String, Object> response = new HashMap<>();
            response.put("caseId", caseId);
            response.put("computedState", state);
            response.put("method", "recomputation-from-receipts");

            return Response.ok(response).build();
        } catch (Exception e) {
            logger.error("Error computing case state", e);
            return errorResponse("Failed to compute case state: " + e.getMessage(), 500);
        }
    }

    /**
     * Retrieve admission/rejection statistics.
     *
     * Example: GET /audit/case/case-001/stats
     */
    @GET
    @Path("/case/{caseId}/stats")
    public Response getStats(@PathParam("caseId") String caseId) {
        try {
            logger.info("Getting statistics for case: {}", caseId);

            ReceiptStore.ReceiptStats stats = receipts.getStats(caseId);
            Map<String, Object> response = new HashMap<>();
            response.put("caseId", caseId);
            response.put("committedTransitions", stats.committedCount);
            response.put("rejectedTransitions", stats.rejectedCount);
            response.put("firstTransition", new Date(stats.minTimestamp));
            response.put("lastTransition", new Date(stats.maxTimestamp));
            response.put("durationMs", stats.maxTimestamp - stats.minTimestamp);

            return Response.ok(response).build();
        } catch (Exception e) {
            logger.error("Error retrieving statistics", e);
            return errorResponse("Failed to retrieve statistics: " + e.getMessage(), 500);
        }
    }

    /**
     * Retrieve a single receipt by ID.
     *
     * Example: GET /audit/receipt/1
     */
    @GET
    @Path("/receipt/{receiptId}")
    public Response getReceipt(@PathParam("receiptId") long receiptId) {
        try {
            logger.info("Retrieving receipt: {}", receiptId);

            Optional<Receipt> receipt = receipts.getReceipt(receiptId);
            if (receipt.isEmpty()) {
                return errorResponse("Receipt not found: " + receiptId, 404);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("receipt", receiptToJson(receipt.get()));
            response.put("provenance", Map.of(
                "hash", receipt.get().getHash(),
                "parentHash", receipt.get().getParentHash(),
                "chainValid", receipt.get().getParentHash() != null ?
                    "parent-linked" : "chain-root"
            ));

            return Response.ok(response).build();
        } catch (Exception e) {
            logger.error("Error retrieving receipt", e);
            return errorResponse("Failed to retrieve receipt: " + e.getMessage(), 500);
        }
    }

    /**
     * Verify chain integrity (tamper detection).
     *
     * Example: GET /audit/verify/case-001
     */
    @GET
    @Path("/verify/{caseId}")
    public Response verifyChain(@PathParam("caseId") String caseId) {
        try {
            logger.info("Verifying chain integrity for case: {}", caseId);

            boolean valid = receipts.verifyChain(caseId);
            List<Receipt> chain = receipts.getChain(caseId);

            Map<String, Object> response = new HashMap<>();
            response.put("caseId", caseId);
            response.put("chainValid", valid);
            response.put("receiptCount", chain.size());
            response.put("result", valid ? "CHAIN_INTEGRITY_VERIFIED" : "CHAIN_INTEGRITY_VIOLATED");

            return Response.ok(response).build();
        } catch (Exception e) {
            logger.error("Error verifying chain", e);
            return errorResponse("Failed to verify chain: " + e.getMessage(), 500);
        }
    }

    /**
     * Query receipts by time range (incident response).
     *
     * Example: GET /audit/case/case-001/time-range?start=1708080000000&end=1708166400000
     */
    @GET
    @Path("/case/{caseId}/time-range")
    public Response getByTimeRange(
        @PathParam("caseId") String caseId,
        @QueryParam("start") long startMs,
        @QueryParam("end") long endMs) {
        try {
            logger.info("Querying receipts for case {} in time range [{}, {}]",
                caseId, startMs, endMs);

            if (startMs < 0 || endMs < 0 || startMs > endMs) {
                return errorResponse("Invalid time range", 400);
            }

            List<Receipt> receipts = this.receipts.getByTimeRange(caseId, startMs, endMs);
            List<Map<String, Object>> receiptsJson = receipts.stream()
                .map(this::receiptToJson)
                .toList();

            Map<String, Object> response = new HashMap<>();
            response.put("caseId", caseId);
            response.put("timeRange", Map.of("start", startMs, "end", endMs));
            response.put("receiptCount", receiptsJson.size());
            response.put("receipts", receiptsJson);

            return Response.ok(response).build();
        } catch (Exception e) {
            logger.error("Error querying by time range", e);
            return errorResponse("Failed to query by time range: " + e.getMessage(), 500);
        }
    }

    /**
     * Query admitted (lawful) transitions.
     *
     * Example: GET /audit/case/case-001/admitted
     */
    @GET
    @Path("/case/{caseId}/admitted")
    public Response getAdmitted(@PathParam("caseId") String caseId) {
        try {
            logger.info("Querying admitted transitions for case: {}", caseId);

            List<Receipt> admitted = receipts.getAdmitted(caseId);
            List<Map<String, Object>> receiptsJson = admitted.stream()
                .map(this::receiptToJson)
                .toList();

            Map<String, Object> response = new HashMap<>();
            response.put("caseId", caseId);
            response.put("admittedCount", receiptsJson.size());
            response.put("receipts", receiptsJson);

            return Response.ok(response).build();
        } catch (Exception e) {
            logger.error("Error querying admitted transitions", e);
            return errorResponse("Failed to query admitted transitions: " + e.getMessage(), 500);
        }
    }

    /**
     * Query rejected (policy violation) transitions (debugging).
     *
     * Example: GET /audit/case/case-001/rejected
     */
    @GET
    @Path("/case/{caseId}/rejected")
    public Response getRejected(@PathParam("caseId") String caseId) {
        try {
            logger.info("Querying rejected transitions for case: {}", caseId);

            List<Receipt> rejected = receipts.getRejected(caseId);
            List<Map<String, Object>> receiptsJson = rejected.stream()
                .map(this::receiptToJson)
                .toList();

            Map<String, Object> response = new HashMap<>();
            response.put("caseId", caseId);
            response.put("rejectedCount", receiptsJson.size());
            response.put("receipts", receiptsJson);

            return Response.ok(response).build();
        } catch (Exception e) {
            logger.error("Error querying rejected transitions", e);
            return errorResponse("Failed to query rejected transitions: " + e.getMessage(), 500);
        }
    }

    // Helper: convert Receipt to JSON map
    private Map<String, Object> receiptToJson(Receipt r) {
        return Map.ofEntries(
            Map.entry("receiptId", r.getReceiptId()),
            Map.entry("caseId", r.getCaseId()),
            Map.entry("tokenHolder", r.getTokenHolder()),
            Map.entry("timestamp", r.getTimestamp()),
            Map.entry("beforeState", r.getBeforeState()),
            Map.entry("delta", r.getDelta()),
            Map.entry("afterState", r.getAfterState()),
            Map.entry("admission", r.getAdmission().name()),
            Map.entry("admissionReason", r.getAdmissionReason()),
            Map.entry("validatorId", r.getValidatorId()),
            Map.entry("hash", r.getHash()),
            Map.entry("parentHash", r.getParentHash()),
            Map.entry("ingressSource", r.getIngressSource())
        );
    }

    // Helper: error response
    private Response errorResponse(String message, int status) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", message);
        error.put("timestamp", System.currentTimeMillis());
        return Response.status(status).entity(error).build();
    }
}
