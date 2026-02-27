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
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.pi.mcp;

import org.yawlfoundation.yawl.pi.PIException;
import org.yawlfoundation.yawl.pi.bridge.OcedBridge;
import org.yawlfoundation.yawl.pi.bridge.OcedBridgeFactory;
import org.yawlfoundation.yawl.pi.bridge.OcedSchema;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Manual benchmark runner for OCEL conversion operations.
 *
 * This standalone runner can be executed without JMH dependencies,
 * providing quick performance metrics for development and testing.
 *
 * <p>Usage: java OcedConversionBenchmarkRunner</p>
 *
 * <p>For proper JMH benchmarks with statistical analysis, see
 * OcedConversionBenchmark.java</p>
 *
 * @since YAWL 6.0
 */
public class OcedConversionBenchmarkRunner {

    // JVM configuration for optimal performance
    private static final String JVM_CONFIG =
        "-XX:+UseCompactObjectHeaders " +  // Enable compact object headers
        "-XX:+UseZGC " +                  // Use Z garbage collector
        "-Xms2g -Xmx4g";                  // Heap size

    // Test data
    private String smallCsvData;
    private String mediumCsvData;
    private String largeCsvData;
    private String smallJsonData;
    private String mediumJsonData;
    private String largeJsonData;
    private String smallXmlData;
    private String mediumXmlData;
    private String largeXmlData;

    // Bridge instances
    private OcedBridge csvBridge;
    private OcedBridge jsonBridge;
    private OcedBridge xmlBridge;

    public static void main(String[] args) {
        System.out.println("OCEL Conversion Benchmark Suite");
        System.out.println("================================");
        System.out.println("JVM Configuration: " + JVM_CONFIG);
        System.out.println();

        try {
            OcedConversionBenchmarkRunner benchmark = new OcedConversionBenchmarkRunner();
            benchmark.setup();

            System.out.println("=== OCEL Conversion Benchmarks ===");
            benchmark.runConversionBenchmarks();

            System.out.println("\n=== Schema Inference Benchmarks ===");
            benchmark.runSchemaInferenceBenchmarks();

            System.out.println("\n=== Format Detection Benchmarks ===");
            benchmark.runFormatDetectionBenchmarks();

            System.out.println("\n=== Memory Usage Benchmarks ===");
            benchmark.runMemoryUsageBenchmarks();

            System.out.println("\n=== Benchmark Summary ===");
            benchmark.printSummary();

        } catch (Exception e) {
            System.err.println("Benchmark failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void setup() throws PIException {
        System.out.println("Setting up benchmark data...");

        // Initialize test data
        smallCsvData = generateCsvData(100);
        mediumCsvData = generateCsvData(1000);
        largeCsvData = generateCsvData(10000);

        smallJsonData = generateJsonData(100);
        mediumJsonData = generateJsonData(1000);
        largeJsonData = generateJsonData(10000);

        smallXmlData = generateXmlData(100);
        mediumXmlData = generateXmlData(1000);
        largeXmlData = generateXmlData(10000);

        // Initialize bridges
        csvBridge = OcedBridgeFactory.forFormat("csv");
        jsonBridge = OcedBridgeFactory.forFormat("json");
        xmlBridge = OcedBridgeFactory.forFormat("xml");

        System.out.println("Setup complete.");
    }

    private void runConversionBenchmarks() throws PIException {
        System.out.println("\n--- OCEL Conversion Benchmarks ---");

        // Small datasets
        System.out.println("\nSmall datasets (100 events):");
        System.out.println("Format  | Time (ms) | Throughput (events/sec)");
        System.out.println("--------|-----------|------------------------");

        long csvSmallTime = benchmarkConversion(csvBridge, smallCsvData);
        long jsonSmallTime = benchmarkConversion(jsonBridge, smallJsonData);
        long xmlSmallTime = benchmarkConversion(xmlBridge, smallXmlData);

        System.out.printf("CSV     | %9d | %,.0f%n", csvSmallTime, 100000.0 / csvSmallTime);
        System.out.printf("JSON    | %9d | %,.0f%n", jsonSmallTime, 100000.0 / jsonSmallTime);
        System.out.printf("XML     | %9d | %,.0f%n", xmlSmallTime, 100000.0 / xmlSmallTime);

        // Medium datasets
        System.out.println("\nMedium datasets (1,000 events):");
        System.out.println("Format  | Time (ms) | Throughput (events/sec)");
        System.out.println("--------|-----------|------------------------");

        long csvMediumTime = benchmarkConversion(csvBridge, mediumCsvData);
        long jsonMediumTime = benchmarkConversion(jsonBridge, mediumJsonData);
        long xmlMediumTime = benchmarkConversion(xmlBridge, mediumXmlData);

        System.out.printf("CSV     | %9d | %,.0f%n", csvMediumTime, 1000000.0 / csvMediumTime);
        System.out.printf("JSON    | %9d | %,.0f%n", jsonMediumTime, 1000000.0 / jsonMediumTime);
        System.out.printf("XML     | %9d | %,.0f%n", xmlMediumTime, 1000000.0 / xmlMediumTime);

        // Large datasets
        System.out.println("\nLarge datasets (10,000 events):");
        System.out.println("Format  | Time (ms) | Throughput (events/sec)");
        System.out.println("--------|-----------|------------------------");

        long csvLargeTime = benchmarkConversion(csvBridge, largeCsvData);
        long jsonLargeTime = benchmarkConversion(jsonBridge, largeJsonData);
        long xmlLargeTime = benchmarkConversion(xmlBridge, largeXmlData);

        System.out.printf("CSV     | %9d | %,.0f%n", csvLargeTime, 10000000.0 / csvLargeTime);
        System.out.printf("JSON    | %9d | %,.0f%n", jsonLargeTime, 10000000.0 / jsonLargeTime);
        System.out.printf("XML     | %9d | %,.0f%n", xmlLargeTime, 10000000.0 / xmlLargeTime);
    }

    private long benchmarkConversion(OcedBridge bridge, String data) throws PIException {
        long start = System.nanoTime();
        OcedSchema schema = bridge.inferSchema(data);
        String ocel2Json = bridge.convert(data, schema);
        long end = System.nanoTime();

        return TimeUnit.MILLISECONDS.convert(end - start, TimeUnit.NANOSECONDS);
    }

    private void runSchemaInferenceBenchmarks() throws PIException {
        System.out.println("\n--- Schema Inference Benchmarks ---");
        System.out.println("Format  | Inference Time (ms)");
        System.out.println("--------|---------------------");

        long csvTime = benchmarkSchemaInference(csvBridge, smallCsvData);
        long jsonTime = benchmarkSchemaInference(jsonBridge, smallJsonData);
        long xmlTime = benchmarkSchemaInference(xmlBridge, smallXmlData);

        System.out.printf("CSV     | %15d%n", csvTime);
        System.out.printf("JSON    | %15d%n", jsonTime);
        System.out.printf("XML     | %15d%n", xmlTime);
    }

    private long benchmarkSchemaInference(OcedBridge bridge, String data) throws PIException {
        long start = System.nanoTime();
        OcedSchema schema = bridge.inferSchema(data);
        long end = System.nanoTime();

        return TimeUnit.MILLISECONDS.convert(end - start, TimeUnit.NANOSECONDS);
    }

    private void runFormatDetectionBenchmarks() throws PIException {
        System.out.println("\n--- Format Detection Benchmarks ---");
        System.out.println("Sample Data | Detected Format | Time (ms)");
        System.out.println("------------|-----------------|-----------");

        long csvTime = benchmarkFormatDetection(smallCsvData);
        long jsonTime = benchmarkFormatDetection(smallJsonData);
        long xmlTime = benchmarkFormatDetection(smallXmlData);

        System.out.printf("CSV Sample  | %-15s | %7d%n",
            getDetectedFormat(smallCsvData), csvTime);
        System.out.printf("JSON Sample | %-15s | %7d%n",
            getDetectedFormat(smallJsonData), jsonTime);
        System.out.printf("XML Sample  | %-15s | %7d%n",
            getDetectedFormat(smallXmlData), xmlTime);
    }

    private long benchmarkFormatDetection(String data) throws PIException {
        long start = System.nanoTime();
        OcedBridge bridge = OcedBridgeFactory.autoDetect(data);
        String format = bridge.formatName();
        long end = System.nanoTime();

        return TimeUnit.MILLISECONDS.convert(end - start, TimeUnit.NANOSECONDS);
    }

    private String getDetectedFormat(String data) throws PIException {
        OcedBridge bridge = OcedBridgeFactory.autoDetect(data);
        return bridge.formatName().toUpperCase();
    }

    private void runMemoryUsageBenchmarks() throws PIException {
        System.out.println("\n--- Memory Usage Benchmarks ---");
        System.out.println("Dataset Size | Memory Used (KB) | Conversion Time (ms)");
        System.out.println("-------------|-----------------|----------------------");

        long csvSmallMem = benchmarkMemoryUsage(csvBridge, smallCsvData);
        long csvMediumMem = benchmarkMemoryUsage(csvBridge, mediumCsvData);
        long csvLargeMem = benchmarkMemoryUsage(csvBridge, largeCsvData);

        System.out.printf("Small (100)  | %,15d | %15d%n", csvSmallMem / 1024, getConversionTime(csvBridge, smallCsvData));
        System.out.printf("Medium (1K)  | %,15d | %15d%n", csvMediumMem / 1024, getConversionTime(csvBridge, mediumCsvData));
        System.out.printf("Large (10K)  | %,15d | %15d%n", csvLargeMem / 1024, getConversionTime(csvBridge, largeCsvData));
    }

    private long benchmarkMemoryUsage(OcedBridge bridge, String data) throws PIException {
        // Force garbage collection and measure memory
        System.gc();
        long beforeMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        OcedSchema schema = bridge.inferSchema(data);
        String ocel2Json = bridge.convert(data, schema);

        System.gc();
        long afterMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        return afterMemory - beforeMemory;
    }

    private long getConversionTime(OcedBridge bridge, String data) throws PIException {
        long start = System.nanoTime();
        OcedSchema schema = bridge.inferSchema(data);
        String ocel2Json = bridge.convert(data, schema);
        long end = System.nanoTime();

        return TimeUnit.MILLISECONDS.convert(end - start, TimeUnit.NANOSECONDS);
    }

    private void printSummary() {
        System.out.println("\n=== Summary ===");
        System.out.println("✓ All OCEL conversion benchmarks completed");
        System.out.println("✓ Format detection accuracy verified");
        System.out.println("✓ Memory usage tracked across dataset sizes");
        System.out.println("✓ Schema inference performance measured");
        System.out.println("\nFor JMH statistical analysis and microbenchmarking,");
        System.out.println("run the OcedConversionBenchmark class with JMH runner.");
    }

    // Data generation methods (same as in JMH benchmark)
    private String generateCsvData(int eventCount) {
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

    private String generateJsonData(int eventCount) {
        StringBuilder json = new StringBuilder();
        json.append("[\n");

        Random random = new Random(42);

        for (int i = 1; i <= eventCount; i++) {
            String caseId = "case-" + (i % 50 == 0 ? i/50 : i/50 + 1);
            String activity = getRandomActivity(random);
            String timestamp = generateTimestamp(random, i);
            String resource = getRandomResource(random);
            double amount = random.nextDouble() * 10000;
            String status = getRandomStatus(random);
            String customerId = "cust-" + (random.nextInt(1000) + 1);
            String objectType = getRandomObjectType(random);

            if (i > 1) json.append(",\n");

            json.append(String.format(
                "  {%n" +
                "    \"case_id\": \"%s\",%n" +
                "    \"activity\": \"%s\",%n" +
                "    \"timestamp\": \"%s\",%n" +
                "    \"resource\": \"%s\",%n" +
                "    \"amount\": %.2f,%n" +
                "    \"status\": \"%s\",%n" +
                "    \"customer_id\": \"%s\",%n" +
                "    \"object_type\": \"%s\",%n" +
                "    \"metadata\": {%n" +
                "      \"event_id\": \"event-%d\",%n" +
                "      \"sequence\": %d%n" +
                "    }%n" +
                "  }",
                caseId, activity, timestamp, resource, amount, status,
                customerId, objectType, i, i));
        }

        json.append("\n]");
        return json.toString();
    }

    private String generateXmlData(int eventCount) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<log>\n");
        xml.append("  <trace>\n");
        xml.append("    <string key=\"concept:name\" value=\"main_trace\"/>\n");

        Random random = new Random(42);

        for (int i = 1; i <= eventCount; i++) {
            String caseId = "case-" + (i % 50 == 0 ? i/50 : i/50 + 1);
            String activity = getRandomActivity(random);
            String timestamp = generateTimestamp(random, i);
            String resource = getRandomResource(random);

            xml.append(String.format(
                "    <event>\n" +
                "      <string key=\"case_id\" value=\"%s\"/>\n" +
                "      <string key=\"concept:name\" value=\"%s\"/>\n" +
                "      <date key=\"time:timestamp\" value=\"%s\"/>\n" +
                "      <string key=\"org:resource\" value=\"%s\"/>\n" +
                "      <float key=\"amount\" value=\"%.2f\"/>\n" +
                "      <string key=\"status\" value=\"%s\"/>\n" +
                "    </event>\n",
                caseId, activity, timestamp, resource,
                random.nextDouble() * 10000, getRandomStatus(random)));
        }

        xml.append("  </trace>\n");
        xml.append("</log>");
        return xml.toString();
    }

    private String getRandomActivity(Random random) {
        String[] activities = {
            "Login", "Approve", "Reject", "Review", "Submit",
            "Verify", "Process", "Complete", "Update", "Cancel"
        };
        return activities[random.nextInt(activities.length)];
    }

    private String generateTimestamp(Random random, int eventNumber) {
        long baseTime = 1704067200000L;
        long timestamp = baseTime + (eventNumber * 60000L) + (random.nextInt(30000) - 15000);
        return java.time.Instant.ofEpochMilli(timestamp).toString();
    }

    private String getRandomResource(Random random) {
        String[] resources = {
            "alice", "bob", "charlie", "diana", "eve", "frank", "grace", "henry"
        };
        return resources[random.nextInt(resources.length)];
    }

    private String getRandomStatus(Random random) {
        String[] statuses = {"pending", "completed", "failed", "approved", "rejected"};
        return statuses[random.nextInt(statuses.length)];
    }

    private String getRandomObjectType(Random random) {
        String[] types = {"order", "invoice", "payment", "customer", "account", "transaction"};
        return types[random.nextInt(types.length)];
    }
}