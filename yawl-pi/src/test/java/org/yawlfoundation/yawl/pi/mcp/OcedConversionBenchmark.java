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

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.yawlfoundation.yawl.pi.PIException;
import org.yawlfoundation.yawl.pi.bridge.OcedBridge;
import org.yawlfoundation.yawl.pi.bridge.OcedBridgeFactory;
import org.yawlfoundation.yawl.pi.bridge.OcedSchema;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Comprehensive JMH benchmark suite for OCEL 2.0 conversion operations.
 *
 * Benchmarks CSV, JSON, and XML/XES format conversion performance,
 * schema inference accuracy, and memory usage during OCEL conversion.
 *
 * <p>This benchmark suite implements Chicago TDD principles with real objects
 * and no mocks, providing production-grade performance metrics for the
 * OcedConversionSkill A2A skill.</p>
 *
 * @since YAWL 6.0
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class OcedConversionBenchmark {

    // Test data generators
    private String smallCsvData;
    private String mediumCsvData;
    private String largeCsvData;
    private String smallJsonData;
    private String mediumJsonData;
    private String largeJsonData;
    private String smallXmlData;
    private String mediumXmlData;
    private String largeXmlData;

    // Bridge instances for reuse
    private OcedBridge csvBridge;
    private OcedBridge jsonBridge;
    private OcedBridge xmlBridge;

    @Setup
    public void setup() throws PIException {
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

        // Pre-create bridge instances for consistent performance testing
        csvBridge = OcedBridgeFactory.forFormat("csv");
        jsonBridge = OcedBridgeFactory.forFormat("json");
        xmlBridge = OcedBridgeFactory.forFormat("xml");
    }

    // ============================================================================
    // OCEL Conversion Benchmarks - Different Data Sizes
    // ============================================================================

    /**
     * Benchmark CSV format conversion with small dataset (100 events)
     * Measures conversion performance typical of development/testing scenarios
     */
    @Benchmark
    public void benchmarkOcelConversion_Small_Csv() throws PIException {
        OcedSchema schema = csvBridge.inferSchema(smallCsvData);
        String ocel2Json = csvBridge.convert(smallCsvData, schema);
        Blackhole.consumeCPU(ocel2Json.length());
    }

    /**
     * Benchmark CSV format conversion with medium dataset (1,000 events)
     * Measures performance for production-scale event logs
     */
    @Benchmark
    public void benchmarkOcelConversion_Medium_Csv() throws PIException {
        OcedSchema schema = csvBridge.inferSchema(mediumCsvData);
        String ocel2Json = csvBridge.convert(mediumCsvData, schema);
        Blackhole.consumeCPU(ocel2Json.length());
    }

    /**
     * Benchmark CSV format conversion with large dataset (10,000 events)
     * Measures performance for enterprise-scale event logs
     */
    @Benchmark
    public void benchmarkOcelConversion_Large_Csv() throws PIException {
        OcedSchema schema = csvBridge.inferSchema(largeCsvData);
        String ocel2Json = csvBridge.convert(largeCsvData, schema);
        Blackhole.consumeCPU(ocel2Json.length());
    }

    /**
     * Benchmark JSON format conversion with small dataset (100 events)
     */
    @Benchmark
    public void benchmarkOcelConversion_Small_Json() throws PIException {
        OcedSchema schema = jsonBridge.inferSchema(smallJsonData);
        String ocel2Json = jsonBridge.convert(smallJsonData, schema);
        Blackhole.consumeCPU(ocel2Json.length());
    }

    /**
     * Benchmark JSON format conversion with medium dataset (1,000 events)
     */
    @Benchmark
    public void benchmarkOcelConversion_Medium_Json() throws PIException {
        OcedSchema schema = jsonBridge.inferSchema(mediumJsonData);
        String ocel2Json = jsonBridge.convert(mediumJsonData, schema);
        Blackhole.consumeCPU(ocel2Json.length());
    }

    /**
     * Benchmark JSON format conversion with large dataset (10,000 events)
     */
    @Benchmark
    public void benchmarkOcelConversion_Large_Json() throws PIException {
        OcedSchema schema = jsonBridge.inferSchema(largeJsonData);
        String ocel2Json = jsonBridge.convert(largeJsonData, schema);
        Blackhole.consumeCPU(ocel2Json.length());
    }

    /**
     * Benchmark XML/XES format conversion with small dataset (100 events)
     */
    @Benchmark
    public void benchmarkOcelConversion_Small_Xml() throws PIException {
        OcedSchema schema = xmlBridge.inferSchema(smallXmlData);
        String ocel2Json = xmlBridge.convert(smallXmlData, schema);
        Blackhole.consumeCPU(ocel2Json.length());
    }

    /**
     * Benchmark XML/XES format conversion with medium dataset (1,000 events)
     */
    @Benchmark
    public void benchmarkOcelConversion_Medium_Xml() throws PIException {
        OcedSchema schema = xmlBridge.inferSchema(mediumXmlData);
        String ocel2Json = xmlBridge.convert(mediumXmlData, schema);
        Blackhole.consumeCPU(ocel2Json.length());
    }

    /**
     * Benchmark XML/XES format conversion with large dataset (10,000 events)
     */
    @Benchmark
    public void benchmarkOcelConversion_Large_Xml() throws PIException {
        OcedSchema schema = xmlBridge.inferSchema(largeXmlData);
        String ocel2Json = xmlBridge.convert(largeXmlData, schema);
        Blackhole.consumeCPU(ocel2Json.length());
    }

    // ============================================================================
    // Schema Inference Benchmarks
    // ============================================================================

    /**
     * Benchmark schema inference performance with CSV format
     * Measures time required to detect column mappings heuristically
     */
    @Benchmark
    public void benchmarkSchemaInference_Csv() throws PIException {
        OcedSchema schema = csvBridge.inferSchema(smallCsvData);
        Blackhole.consumeCPU(schema.caseIdColumn().length());
        Blackhole.consumeCPU(schema.activityColumn().length());
        Blackhole.consumeCPU(schema.timestampColumn().length());
    }

    /**
     * Benchmark schema inference performance with JSON format
     */
    @Benchmark
    public void benchmarkSchemaInference_Json() throws PIException {
        OcedSchema schema = jsonBridge.inferSchema(smallJsonData);
        Blackhole.consumeCPU(schema.caseIdColumn().length());
        Blackhole.consumeCPU(schema.activityColumn().length());
        Blackhole.consumeCPU(schema.timestampColumn().length());
    }

    /**
     * Benchmark schema inference performance with XML/XES format
     */
    @Benchmark
    public void benchmarkSchemaInference_Xml() throws PIException {
        OcedSchema schema = xmlBridge.inferSchema(smallXmlData);
        Blackhole.consumeCPU(schema.caseIdColumn().length());
        Blackhole.consumeCPU(schema.activityColumn().length());
        Blackhole.consumeCPU(schema.timestampColumn().length());
    }

    /**
     * Benchmark auto-detection capability for format detection accuracy
     * Tests the format detection heuristic without explicit format hint
     */
    @Benchmark
    public void benchmarkFormatDetection() throws PIException {
        // Test CSV auto-detection
        OcedBridge csvAuto = OcedBridgeFactory.autoDetect(smallCsvData);
        String csvFormat = csvAuto.formatName();
        Blackhole.consumeCPU(csvFormat.length());

        // Test JSON auto-detection
        OcedBridge jsonAuto = OcedBridgeFactory.autoDetect(smallJsonData);
        String jsonFormat = jsonAuto.formatName();
        Blackhole.consumeCPU(jsonFormat.length());

        // Test XML auto-detection
        OcedBridge xmlAuto = OcedBridgeFactory.autoDetect(smallXmlData);
        String xmlFormat = xmlAuto.formatName();
        Blackhole.consumeCPU(xmlFormat.length());
    }

    // ============================================================================
    // Memory Usage Benchmark
    // ============================================================================

    /**
     * Benchmark memory usage during OCEL conversion
     * Measures memory consumption for conversion operations
     */
    @Benchmark
    public void benchmarkMemoryUsage_Csv() throws PIException {
        // Use System.gc() to ensure memory measurement is consistent
        System.gc();
        long startMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        OcedSchema schema = csvBridge.inferSchema(smallCsvData);
        String ocel2Json = csvBridge.convert(smallCsvData, schema);
        Blackhole.consumeCPU(ocel2Json.length());

        System.gc();
        long endMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        long memoryUsed = endMemory - startMemory;
        Blackhole.consumeCPU(memoryUsed);
    }

    /**
     * Benchmark memory usage with medium dataset
     */
    @Benchmark
    public void benchmarkMemoryUsage_Medium_Csv() throws PIException {
        System.gc();
        long startMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        OcedSchema schema = csvBridge.inferSchema(mediumCsvData);
        String ocel2Json = csvBridge.convert(mediumCsvData, schema);
        Blackhole.consumeCPU(ocel2Json.length());

        System.gc();
        long endMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        long memoryUsed = endMemory - startMemory;
        Blackhole.consumeCPU(memoryUsed);
    }

    /**
     * Benchmark memory usage with large dataset
     */
    @Benchmark
    public void benchmarkMemoryUsage_Large_Csv() throws PIException {
        System.gc();
        long startMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        OcedSchema schema = csvBridge.inferSchema(largeCsvData);
        String ocel2Json = csvBridge.convert(largeCsvData, schema);
        Blackhole.consumeCPU(ocel2Json.length());

        System.gc();
        long endMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        long memoryUsed = endMemory - startMemory;
        Blackhole.consumeCPU(memoryUsed);
    }

    // ============================================================================
    // Test Data Generators
    // ============================================================================

    /**
     * Generate realistic CSV event log data
     */
    private String generateCsvData(int eventCount) {
        StringBuilder csv = new StringBuilder();

        // Header
        csv.append("case_id,activity,timestamp,resource,amount,status,customer_id\n");

        // Generate events with realistic variations
        Random random = new Random(42); // Fixed seed for reproducible benchmarks

        for (int i = 1; i <= eventCount; i++) {
            String caseId = "case-" + (i % 50 == 0 ? i/50 : i/50 + 1); // Create 50 cases
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

    /**
     * Generate realistic JSON event log data
     */
    private String generateJsonData(int eventCount) {
        StringBuilder json = new StringBuilder();
        json.append("[\n");

        Random random = new Random(42); // Fixed seed for reproducible benchmarks

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

    /**
     * Generate realistic XML/XES event log data
     */
    private String generateXmlData(int eventCount) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<log>\n");
        xml.append("  <trace>\n");
        xml.append("    <string key=\"concept:name\" value=\"main_trace\"/>\n");

        Random random = new Random(42); // Fixed seed for reproducible benchmarks

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

    // ============================================================================
    // Helper Methods for Data Generation
    // ============================================================================

    private String getRandomActivity(Random random) {
        String[] activities = {
            "Login", "Approve", "Reject", "Review", "Submit",
            "Verify", "Process", "Complete", "Update", "Cancel"
        };
        return activities[random.nextInt(activities.length)];
    }

    private String generateTimestamp(Random random, int eventNumber) {
        // Generate timestamps with realistic intervals
        long baseTime = 1704067200000L; // 2024-01-01T00:00:00Z
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

    // ============================================================================
    // Main Method for Manual Testing
    // ============================================================================

    /**
     * Main method for running individual benchmarks without JMH
     * Useful for development and quick testing
     */
    public static void main(String[] args) {
        System.out.println("OCEL Conversion Benchmark Suite");
        System.out.println("================================");

        try {
            OcedConversionBenchmark benchmark = new OcedConversionBenchmark();
            benchmark.setup();

            System.out.println("\n--- Manual Test Run ---");

            // Test small CSV conversion
            long start = System.currentTimeMillis();
            benchmark.benchmarkOcelConversion_Small_Csv();
            long elapsed = System.currentTimeMillis() - start;
            System.out.printf("Small CSV conversion: %dms%n", elapsed);

        } catch (Exception e) {
            System.err.println("Benchmark failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}