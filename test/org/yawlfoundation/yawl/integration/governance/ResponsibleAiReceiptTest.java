/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.integration.governance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for ResponsibleAiReceipt.
 *
 * @since YAWL 6.0
 */
@DisplayName("ResponsibleAiReceipt Test Suite")
class ResponsibleAiReceiptTest {

    @Test
    @DisplayName("create_allChecksPassed_trueWhenAllGreen")
    void testAllChecksPassedWhenAllGreen() {
        ResponsibleAiReceipt receipt = ResponsibleAiReceipt.create(
            "gpt-oss-20b", "1.0.0",
            true,      // fairnessPassed
            0.1,       // biasScore < 0.2
            true,      // privacyPassed
            true,      // securityPassed
            true,      // transparencyDocPresent
            "business-owner-001"  // accountabilitySignerId
        );

        assertTrue(receipt.allChecksPassed(),
            "allChecksPassed() should return true when all checks pass and bias < 0.2");
    }

    @Test
    @DisplayName("create_allChecksPassed_falseWhenBiasHigh")
    void testAllChecksPassedFalseWhenBiasHigh() {
        ResponsibleAiReceipt receipt = ResponsibleAiReceipt.create(
            "gpt-oss-20b", "1.0.0",
            true,      // fairnessPassed
            0.5,       // biasScore >= 0.2 (HIGH)
            true,      // privacyPassed
            true,      // securityPassed
            true,      // transparencyDocPresent
            "business-owner-001"
        );

        assertFalse(receipt.allChecksPassed(),
            "allChecksPassed() should return false when biasScore >= 0.2");
    }

    @Test
    @DisplayName("create_allChecksPassed_falseWhenFairnessFailedEvenIfBiasLow")
    void testAllChecksPassedFalseWhenFairnessFailedEvenIfBiasLow() {
        ResponsibleAiReceipt receipt = ResponsibleAiReceipt.create(
            "gpt-oss-20b", "1.0.0",
            false,     // fairnessPassed (FAILED)
            0.1,       // biasScore is OK but fairness failed
            true,      // privacyPassed
            true,      // securityPassed
            true,      // transparencyDocPresent
            "business-owner-001"
        );

        assertFalse(receipt.allChecksPassed(),
            "allChecksPassed() should return false when fairnessPassed is false");
    }

    @Test
    @DisplayName("create_evidenceHashNonNull")
    void testEvidenceHashNonNull() {
        ResponsibleAiReceipt receipt = ResponsibleAiReceipt.create(
            "gpt-oss-20b", "1.0.0",
            true, 0.1, true, true, true, "business-owner-001"
        );

        assertNotNull(receipt.evidenceHash(),
            "evidenceHash should not be null");
        assertFalse(receipt.evidenceHash().isBlank(),
            "evidenceHash should not be blank");
        assertTrue(receipt.evidenceHash().length() == 64,
            "evidenceHash should be 64-character SHA-256 hex");
    }

    @Test
    @DisplayName("create_differentInputs_differentHash")
    void testDifferentInputsDifferentHash() {
        ResponsibleAiReceipt receipt1 = ResponsibleAiReceipt.create(
            "gpt-oss-20b", "1.0.0",
            true, 0.1, true, true, true, "signer-001"
        );

        ResponsibleAiReceipt receipt2 = ResponsibleAiReceipt.create(
            "gpt-oss-20b", "1.0.1",  // Different version
            true, 0.1, true, true, true, "signer-001"
        );

        assertNotEquals(receipt1.evidenceHash(), receipt2.evidenceHash(),
            "Different inputs should produce different hashes");
    }

    @Test
    @DisplayName("toJson_containsAllChecksPassed")
    void testToJsonContainsAllChecksPassed() {
        ResponsibleAiReceipt receipt = ResponsibleAiReceipt.create(
            "gpt-oss-20b", "1.0.0",
            true, 0.1, true, true, true, "signer-001"
        );

        String json = receipt.toJson();
        assertTrue(json.contains("allChecksPassed"),
            "JSON should contain allChecksPassed field");
        assertTrue(json.contains("true"),
            "JSON should contain allChecksPassed: true when all passed");
        assertTrue(json.contains("gpt-oss-20b"),
            "JSON should contain modelId");
        assertTrue(json.contains("evidenceHash"),
            "JSON should contain evidenceHash");
    }

    @Test
    @DisplayName("toJson_returnsParsableJson")
    void testToJsonReturnsParsableJson() {
        ResponsibleAiReceipt receipt = ResponsibleAiReceipt.create(
            "gpt-oss-20b", "1.0.0",
            true, 0.1, true, true, true, "signer-001"
        );

        String json = receipt.toJson();
        assertTrue(json.startsWith("{") && json.endsWith("}"),
            "JSON should be valid JSON object");
        assertTrue(json.contains("\""), "JSON should contain quotes");
    }
}
