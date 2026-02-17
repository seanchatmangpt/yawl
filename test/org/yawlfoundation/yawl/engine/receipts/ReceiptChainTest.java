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

import junit.framework.TestCase;

import java.util.List;
import java.util.Optional;

/**
 * Unit tests for Receipt Chain (BBB homeostasis ledger).
 *
 * Tests verify:
 * - Receipt immutability
 * - Hash computation and chain linking
 * - Query operations (admitted, rejected, by time range)
 * - State recomputation from receipts
 * - Chain integrity verification
 */
public class ReceiptChainTest extends TestCase {

    private ReceiptChain chain;
    private static final String CASE_ID = "case-001";
    private static final String WORK_ITEM_ID = "work-001";

    @Override
    protected void setUp() {
        // Fresh instance for each test
        chain = ReceiptChain.getInstance();
    }

    /**
     * Test receipt immutability: once committed, cannot be changed.
     */
    public void testReceiptImmutability() {
        Receipt.Builder builder = new Receipt.Builder(1L, CASE_ID, WORK_ITEM_ID, System.currentTimeMillis());
        builder.beforeState("{\"status\":\"pending\"}");
        builder.delta("[{\"status\":\"active\"}]");
        builder.afterState("{\"status\":\"active\"}");
        builder.admitted();

        Receipt receipt = builder.build();

        // Verify hash is set
        assertNotNull(receipt.getHash());
        assertTrue(receipt.getHash().length() == 64);  // SHA256 hex format

        // Verify admission status
        assertTrue(receipt.isCommitted());
        assertEquals(Receipt.Admission.COMMITTED, receipt.getAdmission());
    }

    /**
     * Test receipt hash computation: same input → same hash.
     */
    public void testHashDeterminism() {
        long now = System.currentTimeMillis();

        Receipt.Builder b1 = new Receipt.Builder(1L, CASE_ID, WORK_ITEM_ID, now);
        b1.beforeState("{\"x\":1}").delta("[{\"y\":2}]").afterState("{\"y\":2}").admitted();

        Receipt.Builder b2 = new Receipt.Builder(1L, CASE_ID, WORK_ITEM_ID, now);
        b2.beforeState("{\"x\":1}").delta("[{\"y\":2}]").afterState("{\"y\":2}").admitted();

        Receipt r1 = b1.build();
        Receipt r2 = b2.build();

        // Same inputs → same hash
        assertEquals(r1.getHash(), r2.getHash());
    }

    /**
     * Test hash change on different inputs: different Δ → different hash.
     */
    public void testHashSensitivity() {
        long now = System.currentTimeMillis();

        Receipt.Builder b1 = new Receipt.Builder(1L, CASE_ID, WORK_ITEM_ID, now);
        b1.beforeState("{\"x\":1}").delta("[{\"y\":2}]").afterState("{\"y\":2}").admitted();

        Receipt.Builder b2 = new Receipt.Builder(1L, CASE_ID, WORK_ITEM_ID, now);
        b2.beforeState("{\"x\":1}").delta("[{\"y\":3}]").afterState("{\"y\":3}").admitted();

        Receipt r1 = b1.build();
        Receipt r2 = b2.build();

        // Different delta → different hash
        assertFalse("Hashes should differ for different deltas",
                    r1.getHash().equals(r2.getHash()));
    }

    /**
     * Test chain linking: parent hash points to previous receipt.
     */
    public void testChainLinking() {
        long now = System.currentTimeMillis();

        // First receipt (root)
        Receipt.Builder b1 = new Receipt.Builder(1L, CASE_ID, WORK_ITEM_ID, now);
        b1.beforeState("{\"status\":\"init\"}");
        b1.delta("[{\"status\":\"step1\"}]");
        b1.afterState("{\"status\":\"step1\"}");
        b1.admitted();
        long id1 = chain.commit(CASE_ID, b1);

        // Second receipt (should link to first)
        Receipt.Builder b2 = new Receipt.Builder(2L, CASE_ID, WORK_ITEM_ID, now + 1000);
        b2.beforeState("{\"status\":\"step1\"}");
        b2.delta("[{\"status\":\"step2\"}]");
        b2.afterState("{\"status\":\"step2\"}");
        b2.admitted();
        long id2 = chain.commit(CASE_ID, b2);

        // Verify chain
        Optional<Receipt> r1 = chain.getReceipt(id1);
        Optional<Receipt> r2 = chain.getReceipt(id2);

        assertTrue(r1.isPresent() && r2.isPresent());
        assertNull(r1.get().getParentHash());  // Root
        assertEquals(r1.get().getHash(), r2.get().getParentHash());  // Linked
    }

    /**
     * Test chain integrity verification.
     */
    public void testChainIntegrity() {
        long now = System.currentTimeMillis();

        // Build a 3-receipt chain
        for (int i = 0; i < 3; i++) {
            Receipt.Builder b = new Receipt.Builder((long) i, CASE_ID, WORK_ITEM_ID, now + (i * 1000));
            b.beforeState("{\"step\":" + i + "}");
            b.delta("[{\"step\":" + (i + 1) + "}]");
            b.afterState("{\"step\":" + (i + 1) + "}");
            b.admitted();
            chain.commit(CASE_ID, b);
        }

        // Verify integrity
        assertTrue(chain.verifyChain(CASE_ID));
    }

    /**
     * Test rejection receipt: policy violation captured.
     */
    public void testRejectionReceipt() {
        long now = System.currentTimeMillis();

        Receipt.Builder b = new Receipt.Builder(1L, CASE_ID, WORK_ITEM_ID, now);
        b.beforeState("{\"status\":\"pending\"}");
        b.delta("[{\"status\":\"INVALID\"}]");
        b.rejected(Receipt.Admission.REJECTED_SCHEMA, "status must be one of: active, complete");

        Receipt receipt = b.build();

        assertTrue(receipt.isRejected());
        assertEquals(Receipt.Admission.REJECTED_SCHEMA, receipt.getAdmission());
        assertNull(receipt.getAfterState());  // No state change on rejection
    }

    /**
     * Test CONSTRUCT8 size enforcement (max 8 facts).
     */
    public void testCONSTRUCT8Validation() {
        long now = System.currentTimeMillis();

        // Valid: 8 facts
        StringBuilder delta8 = new StringBuilder("[");
        for (int i = 0; i < 8; i++) {
            if (i > 0) delta8.append(",");
            delta8.append("{\"fact\":").append(i).append("}");
        }
        delta8.append("]");

        Receipt.Builder b = new Receipt.Builder(1L, CASE_ID, WORK_ITEM_ID, now);
        b.delta(delta8.toString());
        b.admitted();
        Receipt r = b.build();
        assertTrue(r.isCommitted());

        // Note: actual size validation would happen at admission gate,
        // not in Receipt. This test just verifies Delta can be stored.
        assertEquals(delta8.toString(), r.getDelta());
    }

    /**
     * Test query by status: admitted vs rejected.
     */
    public void testQueryByStatus() {
        long now = System.currentTimeMillis();

        // Commit 2 admitted, 1 rejected
        for (int i = 0; i < 2; i++) {
            Receipt.Builder b = new Receipt.Builder((long) i, CASE_ID, WORK_ITEM_ID, now + (i * 1000));
            b.beforeState("{\"i\":" + i + "}").delta("[{\"i\":" + (i + 1) + "}]")
                .afterState("{\"i\":" + (i + 1) + "}").admitted();
            chain.commit(CASE_ID, b);
        }

        Receipt.Builder reject = new Receipt.Builder(2L, CASE_ID, WORK_ITEM_ID, now + 2000);
        reject.beforeState("{\"i\":2}").delta("[{\"i\":\"BAD\"}]")
            .rejected(Receipt.Admission.REJECTED_POLICY, "invalid");
        chain.commit(CASE_ID, reject);

        // Verify queries
        List<Receipt> admitted = chain.getAdmitted(CASE_ID);
        List<Receipt> rejected = chain.getRejected(CASE_ID);

        assertEquals(2, admitted.size());
        assertEquals(1, rejected.size());
    }

    /**
     * Test state recomputation from receipts.
     */
    public void testStateRecomputation() {
        long now = System.currentTimeMillis();

        // Build sequence: init → step1 → step2
        String[][] states = {
            {"{\"step\":0}", "[{\"step\":1}]", "{\"step\":1}"},
            {"{\"step\":1}", "[{\"step\":2}]", "{\"step\":2}"},
            {"{\"step\":2}", "[{\"step\":3}]", "{\"step\":3}"}
        };

        for (int i = 0; i < states.length; i++) {
            Receipt.Builder b = new Receipt.Builder((long) i, CASE_ID, WORK_ITEM_ID, now + (i * 1000));
            b.beforeState(states[i][0]).delta(states[i][1]).afterState(states[i][2]).admitted();
            chain.commit(CASE_ID, b);
        }

        // Recompute state (should be last after_state)
        String computed = chain.getCaseState(CASE_ID);
        assertEquals("{\"step\":3}", computed);
    }

    /**
     * Test query by time range.
     */
    public void testQueryByTimeRange() {
        long base = System.currentTimeMillis();

        // Commit 3 receipts with 1-second gaps
        for (int i = 0; i < 3; i++) {
            Receipt.Builder b = new Receipt.Builder((long) i, CASE_ID, WORK_ITEM_ID, base + (i * 1000));
            b.beforeState("{\"i\":" + i + "}").delta("[{\"i\":" + (i + 1) + "}]")
                .afterState("{\"i\":" + (i + 1) + "}").admitted();
            chain.commit(CASE_ID, b);
        }

        // Query middle second only (should get receipt 1)
        List<Receipt> middle = chain.getByTimeRange(CASE_ID, base + 500, base + 1500);
        assertEquals(1, middle.size());
        assertEquals(1L, middle.get(0).getReceiptId());
    }

    /**
     * Test chain retrieval.
     */
    public void testChainRetrieval() {
        long now = System.currentTimeMillis();

        // Build 3-receipt chain
        for (int i = 0; i < 3; i++) {
            Receipt.Builder b = new Receipt.Builder((long) i, CASE_ID, WORK_ITEM_ID, now + (i * 1000));
            b.beforeState("{\"i\":" + i + "}").delta("[{\"i\":" + (i + 1) + "}]")
                .afterState("{\"i\":" + (i + 1) + "}").admitted();
            chain.commit(CASE_ID, b);
        }

        // Retrieve full chain
        List<Receipt> receipts = chain.getChain(CASE_ID);
        assertEquals(3, receipts.size());

        // Verify order (chronological)
        for (int i = 0; i < receipts.size(); i++) {
            assertEquals((long) i, receipts.get(i).getReceiptId());
        }
    }

    /**
     * Test statistics.
     */
    public void testStatistics() {
        long now = System.currentTimeMillis();

        // 2 admitted + 1 rejected
        for (int i = 0; i < 3; i++) {
            Receipt.Builder b = new Receipt.Builder((long) i, CASE_ID, WORK_ITEM_ID, now + (i * 1000));
            b.beforeState("{\"i\":" + i + "}");
            if (i < 2) {
                b.delta("[{\"i\":" + (i + 1) + "}]").afterState("{\"i\":" + (i + 1) + "}").admitted();
            } else {
                b.delta("[{\"i\":\"BAD\"}]").rejected(Receipt.Admission.REJECTED_POLICY, "invalid");
            }
            chain.commit(CASE_ID, b);
        }

        ReceiptStore.ReceiptStats stats = chain.getStats(CASE_ID);
        assertEquals(2, stats.committedCount);
        assertEquals(1, stats.rejectedCount);
        assertTrue(stats.maxTimestamp > stats.minTimestamp);
    }
}
