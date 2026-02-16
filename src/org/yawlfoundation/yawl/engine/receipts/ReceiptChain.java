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

import java.util.List;
import java.util.Optional;

/**
 * ReceiptChain: main API for BBB coordination.
 *
 * Responsibilities:
 * - Record every case transition as either COMMITTED (lawful Δ) or REJECTED (policy violation)
 * - Maintain hash chain for tamper detection
 * - Support audit queries and state recomputation
 * - Be the single source of truth for case history
 *
 * Usage (from YEngine):
 *   // At transition time:
 *   Receipt.Builder b = new Receipt.Builder(caseId, workItemId, System.currentTimeMillis());
 *   b.beforeState(serializedCaseState)
 *    .delta(deltaJson)
 *    .validatorId("YNetRunner")
 *    .admitted();  // or .rejected(Admission.REJECTED_POLICY, "reason")
 *
 *   receiptChain.commit(b);  // Appends to ledger
 *
 * Property: No DELETE, only INSERT. "Past is SELECT-only."
 */
public class ReceiptChain {
    private static final ReceiptChain INSTANCE = new ReceiptChain();

    private final ReceiptStore store;

    private ReceiptChain() {
        this.store = new ReceiptStore();
    }

    public static ReceiptChain getInstance() {
        return INSTANCE;
    }

    /**
     * Commit a receipt to the chain.
     * Automatically:
     * - Assigns receipt ID
     * - Computes and injects hash
     * - Links to parent (previous receipt in case)
     * - Stores immutably
     */
    public long commit(String caseId, Receipt.Builder builder) {
        Optional<Receipt> latest = store.getLatestForCase(caseId);
        if (latest.isPresent()) {
            builder.parentHash(latest.get().getHash());
        }
        return store.append(builder);
    }

    /**
     * Retrieve receipt chain for a case (audit trail).
     */
    public List<Receipt> getChain(String caseId) {
        return store.getReceiptsByCase(caseId);
    }

    /**
     * Verify chain integrity (tamper detection).
     * Returns false if any parent hash doesn't match.
     */
    public boolean verifyChain(String caseId) {
        return store.verifyChainForCase(caseId);
    }

    /**
     * Retrieve a single receipt by ID.
     */
    public Optional<Receipt> getReceipt(long receiptId) {
        return store.getReceiptById(receiptId);
    }

    /**
     * Retrieve a receipt by hash (chain traversal, verification).
     */
    public Optional<Receipt> getReceiptByHash(String hash) {
        return store.getReceiptByHash(hash);
    }

    /**
     * Compute authoritative case state by replaying receipts.
     * Truth = recomputation (not derived from in-memory objects).
     */
    public String getCaseState(String caseId) {
        return store.computeCaseState(caseId);
    }

    /**
     * Query admitted transitions only (lawful progress).
     */
    public List<Receipt> getAdmitted(String caseId) {
        return store.getAdmittedReceipts(caseId);
    }

    /**
     * Query rejected transitions (policy violations, debugging).
     */
    public List<Receipt> getRejected(String caseId) {
        return store.getRejectedReceipts(caseId);
    }

    /**
     * Query by time range (incident response, compliance audits).
     */
    public List<Receipt> getByTimeRange(String caseId, long startMs, long endMs) {
        return store.getReceiptsByTimeRange(caseId, startMs, endMs);
    }

    /**
     * Receipt statistics (monitoring, compliance dashboards).
     */
    public ReceiptStore.ReceiptStats getStats(String caseId) {
        return store.getStats(caseId);
    }

    /**
     * Total receipts in ledger (for capacity monitoring).
     */
    public long getTotalCount() {
        return store.getTotalReceiptCount();
    }
}

