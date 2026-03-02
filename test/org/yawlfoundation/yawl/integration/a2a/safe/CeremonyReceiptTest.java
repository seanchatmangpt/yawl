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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CeremonyReceipt.
 *
 * @author YAWL Foundation
 * @version 6.0
 */
class CeremonyReceiptTest {

    private CeremonyRequest request;

    @BeforeEach
    void setUp() {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("key1", "value1");

        request = new CeremonyRequest(
            "PI_PLANNING",
            "ART-001",
            "session-123",
            List.of("agent-1", "agent-2"),
            inputs,
            Instant.now()
        );
    }

    @Test
    void create_computesNonNullHashes() {
        String outcome = "{\"objectives\": [\"Obj1\", \"Obj2\"]}";
        CeremonyReceipt receipt = CeremonyReceipt.create(request, outcome);

        assertNotNull(receipt.evidenceHash());
        assertNotNull(receipt.receiptHash());
        assertFalse(receipt.evidenceHash().isEmpty());
        assertFalse(receipt.receiptHash().isEmpty());
    }

    @Test
    void create_differentInputs_differentHashes() {
        String outcome1 = "{\"objectives\": [\"Obj1\"]}";
        String outcome2 = "{\"objectives\": [\"Obj2\"]}";

        CeremonyReceipt receipt1 = CeremonyReceipt.create(request, outcome1);
        CeremonyReceipt receipt2 = CeremonyReceipt.create(request, outcome2);

        assertNotEquals(receipt1.receiptHash(), receipt2.receiptHash());
    }

    @Test
    void create_sameInputs_sameHashes() {
        String outcome = "{\"objectives\": [\"Obj1\", \"Obj2\"]}";

        CeremonyReceipt receipt1 = CeremonyReceipt.create(request, outcome);
        CeremonyReceipt receipt2 = CeremonyReceipt.create(request, outcome);

        assertEquals(receipt1.receiptHash(), receipt2.receiptHash());
        assertEquals(receipt1.evidenceHash(), receipt2.evidenceHash());
    }

    @Test
    void create_nullRequest_throwsException() {
        String outcome = "{\"objectives\": []}";

        assertThrows(IllegalArgumentException.class,
            () -> CeremonyReceipt.create(null, outcome));
    }

    @Test
    void create_nullOutcome_throwsException() {
        assertThrows(IllegalArgumentException.class,
            () -> CeremonyReceipt.create(request, null));
    }

    @Test
    void create_preservesRequestData() {
        String outcome = "{\"status\": \"complete\"}";
        CeremonyReceipt receipt = CeremonyReceipt.create(request, outcome);

        assertEquals(request.ceremonyType(), receipt.ceremonyType());
        assertEquals(request.artId(), receipt.artId());
        assertEquals(request.sessionId(), receipt.sessionId());
        assertEquals(request.participantAgentIds(), receipt.participatingAgents());
        assertEquals(outcome, receipt.outcome());
    }

    @Test
    void create_hasNonNullTimestamp() {
        String outcome = "{\"status\": \"complete\"}";
        CeremonyReceipt receipt = CeremonyReceipt.create(request, outcome);

        assertNotNull(receipt.conductedAt());
    }
}
