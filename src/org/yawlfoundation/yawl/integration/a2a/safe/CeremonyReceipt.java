/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

package org.yawlfoundation.yawl.integration.a2a.safe;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

/**
 * Immutable receipt object from SAFe ceremony execution.
 *
 * <p>Contains ceremony outcome, participant list, and cryptographic hashes
 * for audit trail and evidence verification.
 *
 * @param ceremonyType       Type of ceremony executed
 * @param artId              Agile Release Train identifier
 * @param sessionId          Unique session identifier
 * @param conductedAt        Timestamp when ceremony was conducted
 * @param participatingAgents List of agents who participated
 * @param outcome            JSON string containing ceremony outcomes
 * @param evidenceHash       SHA-256 hash of input evidence (for audit)
 * @param receiptHash        SHA-256 hash of this receipt (for integrity)
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public record CeremonyReceipt(
    String ceremonyType,
    String artId,
    String sessionId,
    Instant conductedAt,
    List<String> participatingAgents,
    String outcome,
    String evidenceHash,
    String receiptHash
) {

    /**
     * Create a ceremony receipt from a request and outcome.
     *
     * <p>Computes SHA-256 hashes for both the input evidence and the receipt itself,
     * enabling audit trail verification and tamper detection.
     *
     * @param request ceremony request
     * @param outcome JSON string containing ceremony outcomes
     * @return new ceremony receipt with computed hashes
     * @throws IllegalArgumentException if request or outcome is null
     */
    public static CeremonyReceipt create(CeremonyRequest request, String outcome) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (outcome == null || outcome.trim().isEmpty()) {
            throw new IllegalArgumentException("outcome must not be null or empty");
        }

        String evidenceHash = computeSha256(request.sessionId() + request.artId() + outcome);

        CeremonyReceipt receipt = new CeremonyReceipt(
            request.ceremonyType(),
            request.artId(),
            request.sessionId(),
            Instant.now(),
            request.participantAgentIds(),
            outcome,
            evidenceHash,
            "" // Placeholder, will be computed below
        );

        String receiptHash = computeReceiptHash(receipt);
        return new CeremonyReceipt(
            receipt.ceremonyType(),
            receipt.artId(),
            receipt.sessionId(),
            receipt.conductedAt(),
            receipt.participatingAgents(),
            receipt.outcome(),
            receipt.evidenceHash(),
            receiptHash
        );
    }

    /**
     * Compute SHA-256 hash of input string.
     *
     * @param input string to hash
     * @return hex-encoded SHA-256 hash
     * @throws UnsupportedOperationException if SHA-256 is not available
     */
    private static String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new UnsupportedOperationException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Compute hash of receipt in canonical form.
     *
     * @param receipt ceremony receipt
     * @return hex-encoded SHA-256 hash
     */
    private static String computeReceiptHash(CeremonyReceipt receipt) {
        // conductedAt is deliberately excluded: it is set to Instant.now() and
        // must not affect the hash so that same-input calls produce identical hashes.
        String canonical = receipt.ceremonyType() + "|" +
                          receipt.artId() + "|" +
                          receipt.sessionId() + "|" +
                          String.join(",", receipt.participatingAgents()) + "|" +
                          receipt.outcome() + "|" +
                          receipt.evidenceHash();
        return computeSha256(canonical);
    }
}
