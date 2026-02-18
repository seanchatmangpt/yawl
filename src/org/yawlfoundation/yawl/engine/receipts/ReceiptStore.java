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

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ReceiptStore: persistent ledger of case history.
 *
 * Properties (BBB model):
 * - immutable: receipts are never deleted or modified
 * - chained: each receipt links to parent (forms hash chain)
 * - atomic: append-only; no transactions needed
 * - queryable: efficient lookups by case_id, timestamp, admission status
 *
 * Current implementation: in-memory store (thread-safe via ConcurrentHashMap).
 * Production: backed by persistent DB (PostgreSQL, H2, etc).
 */
public class ReceiptStore {
    private final Map<String, List<Receipt>> receiptsByCase;  // case_id -> receipts
    private final Map<Long, Receipt> receiptsById;             // receipt_id -> receipt
    private final Map<String, Receipt> receiptsByHash;         // hash -> receipt
    private final AtomicLong nextReceiptId;

    public ReceiptStore() {
        this.receiptsByCase = Collections.synchronizedMap(new LinkedHashMap<>());
        this.receiptsById = Collections.synchronizedMap(new LinkedHashMap<>());
        this.receiptsByHash = Collections.synchronizedMap(new LinkedHashMap<>());
        this.nextReceiptId = new AtomicLong(1L);
    }

    /**
     * Append a receipt to the ledger (immutable write).
     * Returns the receipt ID assigned by the store.
     */
    public long append(Receipt.Builder builder) {
        long id = nextReceiptId.getAndIncrement();
        Receipt receipt = builder.build();

        // Store by all indices (immutable)
        receiptsById.put(id, receipt);
        receiptsByHash.put(receipt.getHash(), receipt);
        receiptsByCase.computeIfAbsent(receipt.getCaseId(), k -> new ArrayList<>())
            .add(receipt);

        return id;
    }

    /**
     * Retrieve all receipts for a case (immutable read).
     * Returns in chronological order (oldest first).
     */
    public List<Receipt> getReceiptsByCase(String caseId) {
        return Collections.unmodifiableList(
            receiptsByCase.getOrDefault(caseId, Collections.emptyList())
        );
    }

    /**
     * Retrieve a single receipt by ID.
     */
    public Optional<Receipt> getReceiptById(long receiptId) {
        return Optional.ofNullable(receiptsById.get(receiptId));
    }

    /**
     * Retrieve a receipt by its hash (chain integrity check).
     */
    public Optional<Receipt> getReceiptByHash(String hash) {
        return Optional.ofNullable(receiptsByHash.get(hash));
    }

    /**
     * Retrieve the most recent receipt for a case (last transition or rejection).
     */
    public Optional<Receipt> getLatestForCase(String caseId) {
        List<Receipt> receipts = receiptsByCase.get(caseId);
        if (receipts == null || receipts.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(receipts.get(receipts.size() - 1));
    }

    /**
     * Verify chain integrity: validate parent hashes link back correctly.
     * Returns false if chain is broken (tamper detection).
     */
    public boolean verifyChainForCase(String caseId) {
        List<Receipt> receipts = receiptsByCase.get(caseId);
        if (receipts == null || receipts.isEmpty()) {
            return true;  // Empty chain is valid
        }

        // Root receipt must have null parent
        if (receipts.get(0).getParentHash() != null) {
            return false;
        }

        // Each receipt's parent must match previous receipt's hash
        for (int i = 1; i < receipts.size(); i++) {
            Receipt current = receipts.get(i);
            Receipt previous = receipts.get(i - 1);
            if (!previous.getHash().equals(current.getParentHash())) {
                return false;
            }
        }

        return true;
    }

    /**
     * Query receipts by status (admitted vs rejected).
     */
    public List<Receipt> getAdmittedReceipts(String caseId) {
        return Collections.unmodifiableList(
            receiptsByCase.getOrDefault(caseId, Collections.emptyList())
                .stream()
                .filter(Receipt::isCommitted)
                .toList()
        );
    }

    public List<Receipt> getRejectedReceipts(String caseId) {
        return Collections.unmodifiableList(
            receiptsByCase.getOrDefault(caseId, Collections.emptyList())
                .stream()
                .filter(Receipt::isRejected)
                .toList()
        );
    }

    /**
     * Query receipts by time range (for audits, incident response).
     */
    public List<Receipt> getReceiptsByTimeRange(String caseId, long startMs, long endMs) {
        return Collections.unmodifiableList(
            receiptsByCase.getOrDefault(caseId, Collections.emptyList())
                .stream()
                .filter(r -> r.getTimestamp() >= startMs && r.getTimestamp() <= endMs)
                .toList()
        );
    }

    /**
     * Compute case state from receipts (recomputation = truth).
     * Replays admitted transitions to derive authoritative state.
     */
    public String computeCaseState(String caseId) {
        List<Receipt> admitted = getAdmittedReceipts(caseId);
        if (admitted.isEmpty()) {
            return "{}";  // Root state (no transitions yet)
        }
        // Last admitted receipt contains authoritative after_state
        Receipt last = admitted.get(admitted.size() - 1);
        return last.getAfterState() != null ? last.getAfterState() : "{}";
    }

    /**
     * Statistics for monitoring (audit/compliance).
     */
    public ReceiptStats getStats(String caseId) {
        List<Receipt> receipts = receiptsByCase.get(caseId);
        if (receipts == null) {
            return new ReceiptStats(0, 0, 0, 0);
        }
        long committed = receipts.stream().filter(Receipt::isCommitted).count();
        long rejected = receipts.stream().filter(Receipt::isRejected).count();
        long minTime = receipts.stream().mapToLong(Receipt::getTimestamp).min().orElse(0);
        long maxTime = receipts.stream().mapToLong(Receipt::getTimestamp).max().orElse(0);
        return new ReceiptStats(committed, rejected, minTime, maxTime);
    }

    /**
     * Statistics DTO.
     */
    public static class ReceiptStats {
        public final long committedCount;
        public final long rejectedCount;
        public final long minTimestamp;
        public final long maxTimestamp;

        public ReceiptStats(long committed, long rejected, long minTime, long maxTime) {
            this.committedCount = committed;
            this.rejectedCount = rejected;
            this.minTimestamp = minTime;
            this.maxTimestamp = maxTime;
        }

        @Override
        public String toString() {
            return String.format("ReceiptStats{admitted=%d, rejected=%d, span=%dms}",
                committedCount, rejectedCount, maxTimestamp - minTimestamp);
        }
    }

    /**
     * Total receipt count (for diagnostics).
     */
    public long getTotalReceiptCount() {
        return receiptsById.size();
    }

    /**
     * Clear all receipts (for testing only).
     * This method should only be used in test setUp/tearDown to ensure
     * test isolation. In production, receipts are immutable and never cleared.
     */
    public void clear() {
        receiptsByCase.clear();
        receiptsById.clear();
        receiptsByHash.clear();
        nextReceiptId.set(1L);
    }
}
