/*
 * Simple demo of OCEL conversion benchmark structure
 */

import java.util.Random;

public class OcedConversionBenchmarkDemoSimple {

    public static void main(String[] args) {
        System.out.println("OCEL Conversion Benchmark Demo");
        System.out.println("=============================");
        System.out.println("This demo shows the benchmark structure without dependencies.");
        System.out.println();

        // Generate test data
        String smallData = generateCsvData(100);
        String mediumData = generateCsvData(1000);
        String largeData = generateCsvData(10000);

        System.out.println("Running demo benchmarks...\n");

        // Time the benchmarks
        long startTime = System.currentTimeMillis();

        // Simulate benchmarks
        System.out.println("Running OCEL Conversion Benchmarks:");

        long smallTime = runBenchmark("Small CSV", 100);
        long mediumTime = runBenchmark("Medium CSV", 1000);
        long largeTime = runBenchmark("Large CSV", 10000);

        System.out.printf("\nResults:\n");
        System.out.printf("Small CSV (100 events):  %,d ms\n", smallTime);
        System.out.printf("Medium CSV (1K events):  %,d ms\n", mediumTime);
        System.out.printf("Large CSV (10K events): %,d ms\n", largeTime);

        // Show scaling behavior
        System.out.printf("\nScaling Analysis:\n");
        System.out.printf("Small -> Medium: %.2fx increase (%.0f events/ms)\n",
            (double)mediumTime/smallTime, 1000.0/mediumTime);
        System.out.printf("Medium -> Large: %.2fx increase (%.0f events/ms)\n",
            (double)largeTime/mediumTime, 10000.0/largeTime);
        System.out.printf("Linear scaling factor: %.2fx\n",
            (double)largeTime/smallTime / 100.0);

        // Additional benchmarks
        System.out.println("\nRunning Additional Benchmarks:");
        long schemaTime = runBenchmark("Schema Inference", 1);
        System.out.printf("Schema Inference: %d ms\n", schemaTime);

        long formatTime = runBenchmark("Format Detection", 1);
        System.out.printf("Format Detection: %d ms\n", formatTime);

        long memoryTime = runBenchmark("Memory Usage", 1);
        System.out.printf("Memory Usage: %d ms\n", memoryTime);

        System.out.println("\nDemo completed successfully!");
        System.out.println("\nThe real JMH benchmark (OcedConversionBenchmark.java) will provide:");
        System.out.println("- Statistical analysis with JMH");
        System.out.println("- Microbenchmark accuracy");
        System.out.println("- Memory profiling");
        System.out.println("- Throughput calculations");
        System.out.println("- Multiple format support (CSV, JSON, XML)");
    }

    private static long runBenchmark(String name, int events) {
        long startTime = System.nanoTime();

        // Simulate processing
        try {
            // Processing time scales with data size
            long duration = 10 + (long)(events * 0.1); // 10ms base + 0.1ms per event
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1_000_000;

        System.out.printf("- %s: %,d ms (%.0f events/ms)\n",
            name, durationMs, events / (double)durationMs);

        return durationMs;
    }

    private static String generateCsvData(int eventCount) {
        StringBuilder csv = new StringBuilder();
        csv.append("case_id,activity,timestamp,resource,amount,status,customer_id\n");

        Random random = new Random(42);

        for (int i = 1; i <= eventCount; i++) {
            String caseId = "case-" + (i % 50 == 0 ? i/50 : i/50 + 1);
            String activity = getRandomActivity(random);
            String timestamp = generateTimestamp(random, i);
            String resource = getRandomResource(random);
            double amount = random.nextDouble() * 10000;
            String status = getRandomStatus(random);
            String customerId = "cust-" + (random.nextInt(1000) + 1);

            csv.append(String.format("%s,%s,%s,%s,%.2f,%s,%s\n",
                caseId, activity, timestamp, resource, amount, status, customerId));
        }

        return csv.toString();
    }

    private static String getRandomActivity(Random random) {
        String[] activities = {
            "Login", "Approve", "Reject", "Review", "Submit",
            "Verify", "Process", "Complete", "Update", "Cancel"
        };
        return activities[random.nextInt(activities.length)];
    }

    private static String generateTimestamp(Random random, int eventNumber) {
        long baseTime = 1704067200000L; // 2024-01-01T00:00:00Z
        long timestamp = baseTime + (eventNumber * 60000L) + (random.nextInt(30000) - 15000);
        return java.time.Instant.ofEpochMilli(timestamp).toString();
    }

    private static String getRandomResource(Random random) {
        String[] resources = {
            "alice", "bob", "charlie", "diana", "eve", "frank", "grace", "henry"
        };
        return resources[random.nextInt(resources.length)];
    }

    private static String getRandomStatus(Random random) {
        String[] statuses = {"pending", "completed", "failed", "approved", "rejected"};
        return statuses[random.nextInt(statuses.length)];
    }
}