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

package org.yawlfoundation.yawl.engine.receipts;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Receipt: immutable atom of case history under BBB model.
 *
 * A receipt is the output of every case transition: either admission (lawful Δ committed)
 * or rejection (Δ unlawful under policy/schema).
 *
 * Receipt chain properties:
 * - hash: SHA256(canonicalized_form) ensures immutability
 * - parent_hash: links to previous receipt (forms chain)
 * - past is SELECT-only: receipts are never deleted/modified
 *
 * Core fields:
 * - case_id: which case was affected
 * - token_holder: which work item / resource held the token
 * - before_state: case snapshot before Δ
 * - delta: the proposed mutation (≤8 facts for CONSTRUCT8)
 * - after_state: case snapshot after (if committed)
 * - admission: COMMITTED or REJECTED reason
 */
public final class Receipt implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Admission {
        COMMITTED("lawful transition admitted"),
        REJECTED_SCHEMA("Δ violates schema constraints"),
        REJECTED_POLICY("Δ violates authorization policy"),
        REJECTED_SIZE("Δ exceeds CONSTRUCT8 bound (>8 facts)"),
        REJECTED_TOKEN_CONFLICT("token held by conflicting work item"),
        REJECTED_UNKNOWN("unknown rejection reason");

        private final String reason;

        Admission(String reason) {
            this.reason = reason;
        }

        public String getReason() {
            return reason;
        }
    }

    private final long receiptId;          // Primary key
    private final String caseId;
    private final String tokenHolder;      // work_item_id or resource_id
    private final long timestamp;
    private final String beforeState;      // JSON snapshot
    private final String delta;            // JSON array of facts (≤8)
    private final String afterState;       // JSON snapshot (null if rejected)
    private final Admission admission;
    private final String admissionReason;
    private final String validatorId;      // which policy engine validated
    private final String hash;             // SHA256(canonical_form)
    private final String parentHash;       // previous receipt hash (or null for root)
    private final String ingressSource;    // "MCP", "A2A", "API", null for internal

    // Private constructor: use ReceiptBuilder
    private Receipt(long receiptId, String caseId, String tokenHolder, long timestamp,
                   String beforeState, String delta, String afterState,
                   Admission admission, String admissionReason, String validatorId,
                   String hash, String parentHash, String ingressSource) {
        this.receiptId = receiptId;
        this.caseId = caseId;
        this.tokenHolder = tokenHolder;
        this.timestamp = timestamp;
        this.beforeState = beforeState;
        this.delta = delta;
        this.afterState = afterState;
        this.admission = admission;
        this.admissionReason = admissionReason;
        this.validatorId = validatorId;
        this.hash = hash;
        this.parentHash = parentHash;
        this.ingressSource = ingressSource;
    }

    // Accessors (all final fields, immutable)
    public long getReceiptId() {
        return receiptId;
    }

    public String getCaseId() {
        return caseId;
    }

    public String getTokenHolder() {
        return tokenHolder;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getBeforeState() {
        return beforeState;
    }

    public String getDelta() {
        return delta;
    }

    public String getAfterState() {
        return afterState;
    }

    public Admission getAdmission() {
        return admission;
    }

    public String getAdmissionReason() {
        return admissionReason;
    }

    public String getValidatorId() {
        return validatorId;
    }

    public String getHash() {
        return hash;
    }

    public String getParentHash() {
        return parentHash;
    }

    public String getIngressSource() {
        return ingressSource;
    }

    public boolean isCommitted() {
        return admission == Admission.COMMITTED;
    }

    public boolean isRejected() {
        return admission != Admission.COMMITTED;
    }

    /**
     * Canonical form: deterministic JSON representation for hashing.
     * Order matters: same Δ produces same hash.
     */
    public String getCanonicalForm() {
        return String.format(
            "{\"caseId\":\"%s\",\"timestamp\":%d,\"tokenHolder\":\"%s\"," +
            "\"beforeState\":%s,\"delta\":%s,\"afterState\":%s," +
            "\"admission\":\"%s\",\"admissionReason\":\"%s\",\"validatorId\":\"%s\"," +
            "\"ingressSource\":\"%s\",\"parentHash\":\"%s\"}",
            escape(caseId), timestamp, escape(tokenHolder),
            beforeState, delta, afterState,
            admission.name(), escape(admissionReason), escape(validatorId),
            ingressSource != null ? escape(ingressSource) : "null",
            parentHash != null ? escape(parentHash) : "null"
        );
    }

    @Override
    public String toString() {
        return String.format("Receipt{id=%d, case=%s, status=%s, hash=%s}",
            receiptId, caseId, admission.name(), hash.substring(0, 8) + "...");
    }

    // Helper: JSON escape (assumes non-null; caller must check)
    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // Builder for fluent construction
    public static class Builder {
        private final long receiptId;
        private final String caseId;
        private final String tokenHolder;
        private final long timestamp;
        private String beforeState = "{}";
        private String delta = "[]";
        private String afterState = null;
        private Admission admission = Admission.COMMITTED;
        private String admissionReason = "";
        private String validatorId = "engine";
        private String parentHash = null;
        private String ingressSource = null;

        public Builder(long receiptId, String caseId, String tokenHolder, long timestamp) {
            this.receiptId = receiptId;
            this.caseId = caseId;
            this.tokenHolder = tokenHolder;
            this.timestamp = timestamp;
        }

        public Builder beforeState(String beforeState) {
            this.beforeState = beforeState;
            return this;
        }

        public Builder delta(String delta) {
            this.delta = delta;
            return this;
        }

        public Builder afterState(String afterState) {
            this.afterState = afterState;
            return this;
        }

        public Builder admitted() {
            this.admission = Admission.COMMITTED;
            this.admissionReason = "";
            return this;
        }

        public Builder rejected(Admission rejection, String detail) {
            this.admission = rejection;
            this.admissionReason = detail;
            this.afterState = null;  // No state change on rejection
            return this;
        }

        public Builder validatorId(String id) {
            this.validatorId = id;
            return this;
        }

        public Builder parentHash(String hash) {
            this.parentHash = hash;
            return this;
        }

        public Builder ingressSource(String source) {
            this.ingressSource = source;
            return this;
        }

        public Receipt build() {
            Receipt receipt = new Receipt(
                receiptId, caseId, tokenHolder, timestamp,
                beforeState, delta, afterState,
                admission, admissionReason, validatorId,
                null,  // hash computed below
                parentHash, ingressSource
            );

            // Compute and inject hash
            String hash = computeHash(receipt.getCanonicalForm());
            return new Receipt(
                receiptId, caseId, tokenHolder, timestamp,
                beforeState, delta, afterState,
                admission, admissionReason, validatorId,
                hash, parentHash, ingressSource
            );
        }
    }

    private static String computeHash(String canonical) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
