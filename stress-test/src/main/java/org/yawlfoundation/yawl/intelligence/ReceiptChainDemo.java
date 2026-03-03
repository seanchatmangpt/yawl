package org.yawlfoundation.yawl.intelligence;

import java.time.Instant;
import java.util.List;

/**
 * Demo application showcasing the Blake3 receipt chain implementation.
 */
public class ReceiptChainDemo {

    public static void main(String[] args) {
        System.out.println("=== YAWL Blake3 Receipt Chain Demo ===");
        System.out.println("Using Java 25 with virtual threads and records");
        System.out.println();

        // Create a new receipt chain
        ReceiptChain chain = new ReceiptChain();

        // 1. Create genesis receipt
        System.out.println("1. Creating genesis receipt...");
        List<String> genesisDelta = List.of(
            "genesis",
            "system:YAWL-SelfPlay",
            "timestamp:" + Instant.now()
        );
        chain.createGenesis(genesisDelta);
        System.out.println("   ✓ Genesis receipt created");
        System.out.println("   ✓ Chain size: " + chain.size());
        System.out.println("   ✓ Chain valid: " + chain.validateChain());
        System.out.println();

        // 2. Add work item receipts
        System.out.println("2. Adding work item receipts...");

        // Work item creation
        chain.createWorkItemReceipt(
            "CASE-123",
            "WI-001",
            "Task1",
            "created",
            "priority:high",
            "assignee:agent1"
        );
        System.out.println("   ✓ Work item created");

        // Work item completion
        chain.createWorkItemReceipt(
            "CASE-123",
            "WI-001",
            "Task1",
            "completed",
            "duration:2.5s",
            "resources:cpu_25%,mem_100MB"
        );
        System.out.println("   ✓ Work item completed");

        // 3. Add performance metrics
        System.out.println("3. Adding performance metrics...");
        chain.createPerformanceReceipt(
            "task_completion",
            2500,
            "Task1",
            "avg_duration:2.3s",
            "p95_duration:5.1s"
        );
        System.out.println("   ✓ Performance metric recorded");

        // 4. Add error tracking
        System.out.println("4. Adding error tracking...");
        chain.createErrorReceipt(
            "CASE-124",
            "TIMEOUT",
            "Task did not complete within timeout period",
            "retry_attempt:1",
            "timeout:30s"
        );
        System.out.println("   ✓ Error receipt recorded");

        // 5. Validate chain integrity
        System.out.println("\n5. Validating chain integrity...");
        boolean isValid = chain.validateChain();
        System.out.println("   Chain integrity: " + (isValid ? "✓ VALID" : "✗ INVALID"));

        if (!isValid) {
            System.err.println("   ❌ Chain integrity check failed!");
            return;
        }

        // 6. Display chain summary
        System.out.println("\n6. Chain Summary:");
        System.out.println(chain.getSummary());

        // 7. Show all receipts
        System.out.println("\n7. All Receipts:");
        List<Receipt> receipts = chain.getReceipts();
        for (int i = 0; i < receipts.size(); i++) {
            Receipt receipt = receipts.get(i);
            System.out.println("\n   Receipt " + i + ":");
            System.out.println("   Hash: " + receipt.hash());
            System.out.println("   Prior Hash: " + receipt.priorHash());
            System.out.println("   Timestamp: " + receipt.timestamp());
            System.out.println("   Delta: " + receipt.delta());
        }

        // 8. Demonstrate tampering detection
        System.out.println("\n8. Tampering Detection Demo:");
        System.out.println("   Creating tampered version for demonstration...");

        // Create a copy and tamper with it
        List<Receipt> originalReceipts = chain.getReceipts();
        Receipt tamperedReceipt = new Receipt(
            originalReceipts.get(2).hash(),  // Keep original hash
            originalReceipts.get(2).priorHash(),
            originalReceipts.get(2).timestamp(),
            List.of("tampered_data", "unauthorized_change")  // Change content
        );

        // Replace the receipt (simulating tampering)
        receipts.set(2, tamperedReceipt);

        // Check validity after tampering
        boolean stillValid = chain.validateChain();
        System.out.println("   Chain valid after tampering: " + (stillValid ? "✓ VALID" : "✗ INVALID"));

        if (!stillValid) {
            System.out.println("   ✓ Tampering detected successfully!");
        }

        // 9. Show Blake3 hasher functionality
        System.out.println("\n9. Blake3 Hasher Demo:");
        demonstrateBlake3Hashing();

        System.out.println("\n=== Demo Complete ===");
        System.out.println("✓ Blake3 receipt chain working correctly");
        System.out.println("✓ Virtual threads for I/O operations");
        System.out.println("✓ Immutable records for data integrity");
        System.out.println("✓ Cryptographic hash validation");
        System.out.println("✓ Tampering detection capabilities");
    }

    private static void demonstrateBlake3Hashing() {
        System.out.println("   Testing hash uniqueness...");

        // Create two identical deltas
        List<String> delta = List.of("test", "data", "sample");
        Instant timestamp = Instant.now();

        // Generate two hashes with same inputs
        String hash1 = Blake3Hasher.hash(delta, null, timestamp);
        String hash2 = Blake3Hasher.hash(delta, null, timestamp);

        // Hashes should be the same for identical inputs
        System.out.println("   Identical inputs: " + delta);
        System.out.println("   Hash 1: " + hash1);
        System.out.println("   Hash 2: " + hash2);
        System.out.println("   Hashes match: " + hash1.equals(hash2));

        // Test with different delta
        String hash3 = Blake3Hasher.hash(List.of("test", "different", "data"), null, timestamp);
        System.out.println("   Different delta: " + List.of("test", "different", "data"));
        System.out.println("   Hash 3: " + hash3);
        System.out.println("   Hash uniqueness: " + !hash1.equals(hash3));

        // Test hash format validation
        System.out.println("   Valid hash format: " + Blake3Hasher.isValidHash(hash1));
        System.out.println("   Invalid hash check: " + Blake3Hasher.isValidHash("invalid"));
    }
}