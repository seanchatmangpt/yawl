package org.yawlfoundation.yawl.intelligence;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Append-only Blake3 receipt chain with integrity validation.
 * Uses Java 25 features and virtual threads for high concurrency.
 */
public class ReceiptChain {

    private final List<Receipt> receipts;
    private final ReceiptStorage storage;
    private final ReentrantLock lock = new ReentrantLock();

    public ReceiptChain() {
        this.receipts = new CopyOnWriteArrayList<>();
        this.storage = new ReceiptStorage();
    }

    /**
     * Creates the genesis receipt if chain is empty
     */
    public void createGenesis(List<String> delta) {
        lock.lock();
        try {
            if (!receipts.isEmpty()) {
                throw new IllegalStateException("Receipt chain already has entries");
            }

            Receipt genesis = Receipt.createGenesis(delta);
            receipts.add(genesis);
            storage.append(genesis);

            System.out.println("Genesis receipt created: " + genesis.hash());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Appends a new receipt to the chain in a virtual thread
     */
    public void appendReceipt(List<String> delta) {
        if (delta == null) {
            throw new NullPointerException("Delta cannot be null");
        }

        lock.lock();
        try {
            Receipt prior = receipts.isEmpty() ? null : receipts.get(receipts.size() - 1);
            Receipt receipt = Receipt.create(delta, prior);

            // Add to in-memory chain
            receipts.add(receipt);

            // Persist in virtual thread
            Thread.startVirtualThread(() -> {
                try {
                    storage.append(receipt);
                } catch (Exception e) {
                    System.err.println("Failed to persist receipt: " + e.getMessage());
                    // Don't throw - receipt is already in memory
                }
            });

            System.out.println("Receipt appended: " + receipt.hash());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Validates the entire chain integrity
     */
    public boolean validateChain() {
        lock.lock();
        try {
            if (receipts.isEmpty()) {
                return false;
            }

            // Check genesis receipt
            Receipt genesis = receipts.get(0);
            if (genesis.priorHash() != null) {
                return false;
            }
            if (!genesis.validateHash()) {
                return false;
            }

            // Check subsequent receipts
            for (int i = 1; i < receipts.size(); i++) {
                Receipt current = receipts.get(i);
                Receipt previous = receipts.get(i - 1);

                // Check hash link
                if (!current.priorHash().equals(previous.hash())) {
                    System.err.println("Hash link broken at index " + i +
                                     ": expected " + previous.hash() +
                                     ", got " + current.priorHash());
                    return false;
                }

                // Check hash calculation
                if (!current.validateHash()) {
                    System.err.println("Invalid hash at index " + i);
                    return false;
                }
            }

            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Loads receipts from storage
     */
    public void loadFromStorage() throws IOException {
        lock.lock();
        try {
            List<Receipt> loadedReceipts = storage.loadAll();
            this.receipts.clear();
            this.receipts.addAll(loadedReceipts);

            System.out.println("Loaded " + loadedReceipts.size() + " receipts from storage");
        } finally {
            lock.unlock();
        }
    }

    /**
     * Validates storage integrity
     */
    public boolean validateStorage() throws IOException {
        return storage.validateFileIntegrity();
    }

    /**
     * Gets the head receipt (most recent)
     */
    public Receipt getHead() {
        lock.lock();
        try {
            return receipts.isEmpty() ? null : receipts.get(receipts.size() - 1);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Gets all receipts
     */
    public List<Receipt> getReceipts() {
        lock.lock();
        try {
            return List.copyOf(receipts);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Gets chain size
     */
    public int size() {
        lock.lock();
        try {
            return receipts.size();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Checks if chain is empty
     */
    public boolean isEmpty() {
        lock.lock();
        try {
            return receipts.isEmpty();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Clears the entire chain
     */
    public void clear() throws IOException {
        lock.lock();
        try {
            receipts.clear();
            storage.clear();
            System.out.println("Receipt chain cleared");
        } finally {
            lock.unlock();
        }
    }

    /**
     * Gets the genesis receipt
     */
    public Receipt getGenesis() {
        lock.lock();
        try {
            return receipts.isEmpty() ? null : receipts.get(0);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Validates hash chain continuity
     */
    public boolean validateHashChain() {
        lock.lock();
        try {
            if (receipts.size() < 2) {
                return true;
            }

            for (int i = 1; i < receipts.size(); i++) {
                Receipt current = receipts.get(i);
                Receipt previous = receipts.get(i - 1);

                if (!current.priorHash().equals(previous.hash())) {
                    return false;
                }
            }

            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Generates a summary of the chain
     */
    public String getSummary() {
        lock.lock();
        try {
            if (receipts.isEmpty()) {
                return "Empty chain";
            }

            Receipt head = getHead();
            Receipt genesis = getGenesis();

            StringBuilder summary = new StringBuilder();
            summary.append("Receipt Chain Summary:\n");
            summary.append("  Total receipts: ").append(size()).append("\n");
            summary.append("  Genesis hash: ").append(genesis.hash()).append("\n");
            summary.append("  Head hash: ").append(head.hash()).append("\n");
            summary.append("  Genesis timestamp: ").append(genesis.timestamp()).append("\n");
            summary.append("  Head timestamp: ").append(head.timestamp()).append("\n");
            summary.append("  Chain valid: ").append(validateChain()).append("\n");

            return summary.toString();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Creates a work item receipt for YAWL workflows
     */
    public void createWorkItemReceipt(String caseId, String workItemId, String taskId, String status, String... additionalData) {
        List<String> delta = new java.util.ArrayList<>();
        delta.add("workitem_event");
        delta.add("case_id:" + caseId);
        delta.add("workitem_id:" + workItemId);
        delta.add("task_id:" + taskId);
        delta.add("status:" + status);
        delta.add("timestamp:" + Instant.now());

        if (additionalData != null) {
            for (String data : additionalData) {
                delta.add(data);
            }
        }

        appendReceipt(delta);
    }

    /**
     * Creates an error receipt for YAWL workflows
     */
    public void createErrorReceipt(String caseId, String errorType, String errorDetails, String... additionalData) {
        List<String> delta = new java.util.ArrayList<>();
        delta.add("error_event");
        delta.add("case_id:" + caseId);
        delta.add("error_type:" + errorType);
        delta.add("error_details:" + errorDetails);
        delta.add("timestamp:" + Instant.now());

        if (additionalData != null) {
            for (String data : additionalData) {
                delta.add(data);
            }
        }

        appendReceipt(delta);
    }

    /**
     * Creates a performance receipt for YAWL workflows
     */
    public void createPerformanceReceipt(String metricName, long durationMs, String context, String... additionalData) {
        List<String> delta = new java.util.ArrayList<>();
        delta.add("performance_metric");
        delta.add("metric:" + metricName);
        delta.add("duration_ms:" + durationMs);
        delta.add("context:" + context);
        delta.add("timestamp:" + Instant.now());

        if (additionalData != null) {
            for (String data : additionalData) {
                delta.add(data);
            }
        }

        appendReceipt(delta);
    }
}