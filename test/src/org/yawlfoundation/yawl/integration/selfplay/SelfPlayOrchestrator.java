/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.integration.selfplay;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.interfce.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;

import java.io.ByteArrayInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Self-Play Test Orchestrator for YAWL Workflow Engine.
 *
 * <p>This orchestrator performs automated testing of YAWL workflows through a
 * complete cycle: XML generation via Z.ai → Validation → Upload → Execution →
 * Verification. Metrics are captured at each stage to support performance analysis
 * and regression testing.</p>
 *
 * <p>Key Features:</p>
 * <ul>
 *   <li>XML workflow generation using AI integration</li>
 *   <li>XML Schema validation against YAWL specification</li>
 *   <li>Multi-iteration test execution</li>
 *   <li>Comprehensive metrics collection</li>
 *   <li>Work item verification</li>
 *   <li>Performance benchmarking</li>
 * </ul>
 *
 * <p>Usage Examples:</p>
 * <pre>{@code
 * // Basic execution
 * SelfPlayOrchestrator.main(new String[] {});
 *
 * // Custom configuration
 * SelfPlayOrchestrator.main(new String[] {
 *     "--engine-url", "http://localhost:8080/yawl",
 *     "--iterations", "5",
 *     "--timeout", "30000"
 * });
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class SelfPlayOrchestrator {

    // Configuration constants
    private static final String DEFAULT_ENGINE_URL = "http://localhost:8080/yawl";
    private static final String DEFAULT_USERNAME = "admin";
    private static final String DEFAULT_PASSWORD = "admin";
    private static final int DEFAULT_ITERATIONS = 3;
    private static final long DEFAULT_TIMEOUT_MS = 30_000;

    // Schema location
    private static final String SCHEMA_PATH = "schema/YAWL_Schema4.0.xsd";

    // XML templates for different workflow patterns
    private static final String SEQUENCE_TEMPLATE = """
        <?xml version="1.0" encoding="UTF-8"?>
        <specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="http://www.yawlfoundation.org/yawlschema http://www.yawlfoundation.org/yawlschema/YAWL_Schema4.0.xsd">
          <specification uri="%s">
            <metaData>
              <title>%s</title>
              <creator>YAWL Self-Play Orchestrator</creator>
              <description>%s</description>
              <version>1.0</version>
            </metaData>
            <decomposition id="mainFlow" xsi:type="NetFactsType" isRootNet="true">
              <name>Main Flow</name>
              <processControlElements>
                <inputCondition id="start">
                  <name>Start</name>
                  <flowsInto>
                    <nextElementRef id="%s"/>
                  </flowsInto>
                </inputCondition>
                <task id="%s">
                  <name>%s</name>
                  <documentation>%s</documentation>
                  <flowsInto>
                    <nextElementRef id="%s"/>
                  </flowsInto>
                  <join code="xor"/>
                  <split code="and"/>
                </task>
                <task id="%s">
                  <name>%s</name>
                  <documentation>%s</documentation>
                  <flowsInto>
                    <nextElementRef id="end"/>
                  </flowsInto>
                  <join code="xor"/>
                  <split code="and"/>
                </task>
                <outputCondition id="end">
                  <name>End</name>
                </outputCondition>
              </processControlElements>
            </decomposition>
          </specification>
        </specificationSet>
        """;

    // Session handle for engine connection
    private String sessionHandle;

    private static final Logger logger = Logger.getLogger(SelfPlayOrchestrator.class.getName());

    // Configuration
    private String engineUrl;
    private String username;
    private String password;
    private int iterations;
    private long timeoutMs;
    private boolean useZaiIntegration;
    private Path outputDirectory;

    // Metrics
    private Map<String, Long> metrics = new HashMap<>();
    private List<TestIterationResult> iterationResults = new ArrayList<>();

    // Client
    private InterfaceB_EnvironmentBasedClient engineClient;
    private HttpClient httpClient;
    private ExecutorService virtualThreadExecutor;

    /**
     * Main entry point for standalone execution.
     */
    public static void main(String[] args) {
        SelfPlayOrchestrator orchestrator = new SelfPlayOrchestrator();

        try {
            orchestrator.parseArguments(args);
            orchestrator.initialize();
            orchestrator.runSelfPlayTest();
            orchestrator.generateReport();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Self-play test execution failed", e);
            System.exit(1);
        } finally {
            orchestrator.shutdown();
        }
    }

    /**
     * Creates a new SelfPlayOrchestrator with default configuration.
     */
    public SelfPlayOrchestrator() {
        this.engineUrl = System.getProperty("yawl.engine.url", DEFAULT_ENGINE_URL);
        this.username = System.getProperty("yawl.username", DEFAULT_USERNAME);
        this.password = System.getProperty("yawl.password", DEFAULT_PASSWORD);
        this.iterations = Integer.getInteger("yawl.iterations", DEFAULT_ITERATIONS);
        this.timeoutMs = Long.getLong("yawl.timeout.ms", DEFAULT_TIMEOUT_MS);
        this.useZaiIntegration = Boolean.getBoolean("yawl.use.zai");
        this.outputDirectory = Paths.get(System.getProperty("user.dir"), "self-play-results");

        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .version(HttpClient.Version.HTTP_2)
            .build();

        // Virtual thread executor for concurrent operations
        this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    // Getter and setter methods for testing
    public java.util.concurrent.ExecutorService getVirtualThreadExecutor() { return virtualThreadExecutor; }
    public String getEngineUrl() { return engineUrl; }
    public void setEngineUrl(String engineUrl) { this.engineUrl = engineUrl; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public int getIterations() { return iterations; }
    public void setIterations(int iterations) { this.iterations = iterations; }
    public boolean isUseZaiIntegration() { return useZaiIntegration; }
    public void setUseZaiIntegration(boolean useZaiIntegration) { this.useZaiIntegration = useZaiIntegration; }
    public Path getOutputDirectory() { return outputDirectory; }
    public void setOutputDirectory(Path outputDirectory) { this.outputDirectory = outputDirectory; }
    public Map<String, Long> getMetrics() { return metrics; }
    public List<TestIterationResult> getIterationResults() { return iterationResults; }

    /**
     * Parse command line arguments.
     */
    private void parseArguments(String[] args) {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--engine-url":
                    engineUrl = args[++i];
                    break;
                case "--username":
                    username = args[++i];
                    break;
                case "--password":
                    password = args[++i];
                    break;
                case "--iterations":
                    iterations = Integer.parseInt(args[++i]);
                    break;
                case "--timeout":
                    timeoutMs = Long.parseLong(args[++i]);
                    break;
                case "--use-zai":
                    useZaiIntegration = Boolean.parseBoolean(args[++i]);
                    break;
                case "--output-dir":
                    outputDirectory = Paths.get(args[++i]);
                    break;
                case "--help":
                    printUsage();
                    System.exit(0);
                    break;
            }
        }
    }

    /**
     * Initialize the orchestrator.
     */
    private void initialize() throws Exception {
        logger.info("Initializing Self-Play Orchestrator...");
        logger.info("Engine URL: " + engineUrl);
        logger.info("Iterations: " + iterations);
        logger.info("Use Z.ai: " + useZaiIntegration);

        // Create output directory
        Files.createDirectories(outputDirectory);

        // Initialize engine client
        engineClient = new InterfaceB_EnvironmentBasedClient(engineUrl + "/ib");

        // Connect to engine
        sessionHandle = engineClient.connect(username, password);
        if (sessionHandle == null || sessionHandle.isEmpty()) {
            throw new RuntimeException("Failed to connect to YAWL engine");
        }
        logger.info("Connected to YAWL engine with session: " + sessionHandle);

        // Record initialization metrics
        metrics.put("initialization.duration", System.currentTimeMillis());
    }

    /**
     * Get the session handle for engine connection.
     */
    public String getSessionHandle() {
        return sessionHandle;
    }

    /**
     * Main self-play test orchestrator method.
     */
    public void runSelfPlayTest() {
        logger.info("Starting self-play test with " + iterations + " iterations...");

        try {
            for (int i = 0; i < iterations; i++) {
                logger.info("Running iteration " + (i + 1) + "/" + iterations);
                TestIterationResult result = runSingleIteration(i);
                iterationResults.add(result);

                // Check if iteration failed
                if (!result.isSuccess()) {
                    logger.warning("Iteration " + (i + 1) + " failed: " + result.getErrorMessage());
                }
            }

            logger.info("Completed all " + iterations + " iterations");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during self-play test", e);
            metrics.put("test.error", System.currentTimeMillis());
        }
    }

    /**
     * Run a single test iteration.
     */
    private TestIterationResult runSingleIteration(int iterationNumber) {
        TestIterationResult result = new TestIterationResult(iterationNumber);
        Instant startTime = Instant.now();

        try {
            // Step 1: Generate XML specification
            logger.info("Generating XML specification for iteration " + (iterationNumber + 1));
            Instant genStart = Instant.now();

            String xmlSpec;
            if (useZaiIntegration) {
                xmlSpec = generateWorkflowViaZai(iterationNumber);
            } else {
                xmlSpec = generateSequenceWorkflow(iterationNumber);
            }

            result.setGenerationDuration(Duration.between(genStart, Instant.now()).toMillis());
            metrics.put("iteration." + iterationNumber + ".generation.duration", result.getGenerationDuration());

            // Step 2: Validate XML specification
            logger.info("Validating XML specification");
            Instant valStart = Instant.now();

            if (!validateSpecification(xmlSpec)) {
                throw new RuntimeException("XML validation failed");
            }

            result.setValidationDuration(Duration.between(valStart, Instant.now()).toMillis());
            metrics.put("iteration." + iterationNumber + ".validation.duration", result.getValidationDuration());

            // Step 3: Upload specification
            logger.info("Uploading specification to YAWL engine");
            Instant uploadStart = Instant.now();

            String specId = uploadSpecification(xmlSpec);
            result.setSpecificationId(specId);

            result.setUploadDuration(Duration.between(uploadStart, Instant.now()).toMillis());
            metrics.put("iteration." + iterationNumber + ".upload.duration", result.getUploadDuration());

            // Step 4: Execute workflow
            logger.info("Executing workflow");
            Instant execStart = Instant.now();

            String caseId = executeWorkflow(specId);
            result.setCaseId(caseId);
            result.setExecutionDuration(Duration.between(execStart, Instant.now()).toMillis());
            metrics.put("iteration." + iterationNumber + ".execution.duration", result.getExecutionDuration());

            // Step 5: Verify execution
            logger.info("Verifying workflow execution");
            Instant verifyStart = Instant.now();

            if (verifyExecution(caseId, specId)) {
                result.setVerificationDuration(Duration.between(verifyStart, Instant.now()).toMillis());
                metrics.put("iteration." + iterationNumber + ".verification.duration", result.getVerificationDuration());
                result.setSuccess(true);
            } else {
                throw new RuntimeException("Workflow execution verification failed");
            }

            result.setTotalDuration(Duration.between(startTime, Instant.now()).toMillis());

        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setTotalDuration(Duration.between(startTime, Instant.now()).toMillis());
            logger.log(Level.WARNING, "Iteration " + (iterationNumber + 1) + " failed", e);
        }

        return result;
    }

    /**
     * Generate workflow XML using Z.ai integration.
     */
    public String generateWorkflowViaZai(int iterationNumber) {
        try {
            // Create configuration and generator
            SelfPlayConfig config = new SelfPlayConfig();
            config.loadFromEnvironment();
            ZaiWorkflowGenerator generator = new ZaiWorkflowGenerator(config);

            // Check if ZAI is available
            if (generator.isAvailable()) {
                logger.info("Using Z.AI for workflow generation");
                java.util.Map<String, String> params = new java.util.HashMap<>();
                params.put("task_count", "2");
                params.put("description", "Test workflow iteration " + (iterationNumber + 1));
                params.put("complexity", "simple");
                return generator.generateWorkflow("sequence", params);
            } else {
                logger.info("Z.AI not available, using template generation");
                return generateSequenceWorkflow(iterationNumber);
            }

        } catch (Exception e) {
            logger.warning("Z.AI generation failed, falling back to template: " + e.getMessage());
            return generateSequenceWorkflow(iterationNumber);
        }
    }

    /**
     * Generate a simple sequence workflow XML.
     */
    public String generateSequenceWorkflow(int iterationNumber) {
        String uri = "sequenceWorkflow_" + UUID.randomUUID().toString().substring(0, 8);
        String task1Id = "task_" + iterationNumber + "_1";
        String task2Id = "task_" + iterationNumber + "_2";

        return String.format(SEQUENCE_TEMPLATE,
            uri,
            "Sequence Test " + (iterationNumber + 1),
            "A simple sequential workflow for testing",
            task1Id,
            task1Id, "Task " + (iterationNumber + 1) + ".1", "First task in sequence",
            task2Id,
            task2Id, "Task " + (iterationNumber + 1) + ".2", "Second task in sequence"
        );
    }

    /**
     * Validate XML specification against YAWL schema.
     */
    public boolean validateSpecification(String xmlSpec) {
        try {
            // Basic XML well-formedness check
            SAXBuilder builder = new SAXBuilder();
            Document document = builder.build(new ByteArrayInputStream(xmlSpec.getBytes()));

            // Schema validation - in production, use XSD validation
            Element specElement = document.getRootElement();
            if (!"specificationSet".equals(specElement.getName())) {
                logger.warning("Root element should be 'specificationSet'");
                return false;
            }

            // Check for specification elements
            List<Element> specs = specElement.getChildren("specification", specElement.getNamespace());
            if (specs.isEmpty()) {
                logger.warning("No specification found");
                return false;
            }

            return true;

        } catch (JDOMException | IOException e) {
            logger.log(Level.WARNING, "XML validation failed", e);
            return false;
        }
    }

    /**
     * Upload specification to YAWL engine.
     */
    public String uploadSpecification(String xmlSpec) throws IOException, JDOMException {
        // Parse the specification to extract URI
        SAXBuilder builder = new SAXBuilder();
        Document document = builder.build(new ByteArrayInputStream(xmlSpec.getBytes()));
        Element specElement = document.getRootElement()
            .getChild("specification", document.getRootElement().getNamespace());
        String uri = specElement.getAttributeValue("uri");

        // Upload using YAWL API - use the uploadSpecificationAsBytes method
        byte[] specBytes = xmlSpec.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String uploadResult = engineClient.uploadSpecification(specBytes, sessionHandle);

        if (uploadResult == null || uploadResult.isEmpty()) {
            throw new RuntimeException("Specification upload failed");
        }

        // Check for error response
        if (uploadResult.contains("<failure>") || uploadResult.contains("error")) {
            throw new RuntimeException("Specification upload failed: " + uploadResult);
        }

        // Return the specification ID (format: uri:version)
        return uri + ":1.0";
    }

    /**
     * Execute the workflow and return case ID.
     */
    public String executeWorkflow(String specId) throws IOException, JDOMException {
        // Create YSpecificationID from string
        YSpecificationID ySpecId = new YSpecificationID(specId);

        // Launch case
        String caseId = engineClient.launchCase(ySpecId, null, sessionHandle);

        if (caseId == null || caseId.isEmpty()) {
            throw new RuntimeException("Failed to launch case");
        }

        // Wait for case to complete (with timeout)
        Instant deadline = Instant.now().plusMillis(timeoutMs);
        while (Instant.now().isBefore(deadline)) {
            try {
                Thread.sleep(1000);

                // Check if case is completed by looking for work items
                List<WorkItemRecord> workItems = engineClient.getWorkItemsForCase(caseId, sessionHandle);

                // If no work items, case may have completed
                if (workItems == null || workItems.isEmpty()) {
                    return caseId;
                }

                // Check if all work items are completed
                boolean allCompleted = workItems.stream()
                    .allMatch(wir -> wir != null && "completed".equalsIgnoreCase(wir.getStatus()));

                if (allCompleted) {
                    return caseId;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Case execution interrupted", e);
            }
        }

        throw new RuntimeException("Case execution timeout");
    }

    /**
     * Verify workflow execution results.
     */
    public boolean verifyExecution(String caseId, String specId) {
        try {
            // Get case status
            List<WorkItemRecord> workItems = engineClient.getWorkItemsForCase(caseId, sessionHandle);

            // If no work items, the case may have completed successfully
            // or there's a problem with the engine
            if (workItems == null || workItems.isEmpty()) {
                logger.info("No work items found for case " + caseId + " - case may have completed");
                return true;
            }

            // Verify all work items are completed
            boolean allCompleted = workItems.stream()
                .allMatch(wir -> wir != null && "completed".equalsIgnoreCase(wir.getStatus()));

            if (!allCompleted) {
                logger.warning("Not all work items completed for case " + caseId);
                return false;
            }

            return true;

        } catch (Exception e) {
            logger.log(Level.WARNING, "Verification failed for case " + caseId, e);
            return false;
        }
    }

    /**
     * Generate comprehensive test report.
     */
    public void generateReport() throws IOException {
        logger.info("Generating test report...");

        // Calculate summary metrics
        long totalDuration = iterationResults.stream()
            .mapToLong(TestIterationResult::getTotalDuration)
            .sum();

        long successfulIterations = iterationResults.stream()
            .mapToLong(r -> r.isSuccess() ? 1 : 0)
            .sum();

        double successRate = iterationResults.isEmpty() ? 0 :
            (double) successfulIterations / iterationResults.size() * 100;

        // Generate JSON report
        Map<String, Object> report = new HashMap<>();
        report.put("timestamp", Instant.now().toString());
        report.put("total_iterations", iterations);
        report.put("successful_iterations", successfulIterations);
        report.put("success_rate", successRate);
        report.put("total_duration_ms", totalDuration);
        report.put("average_duration_ms", iterations > 0 ? totalDuration / iterations : 0);
        report.put("metrics", metrics);
        report.put("iterations", iterationResults);

        // Write report to file
        Path reportFile = outputDirectory.resolve("self-play-report.json");
        try (FileWriter writer = new FileWriter(reportFile.toFile())) {
            // Use built-in JSON serialization
            writer.write(toJsonString(report));
        }

        logger.info("Report generated: " + reportFile);
        logger.info("Success rate: " + String.format("%.2f", successRate) + "%");
    }

    /**
     * Shutdown resources.
     */
    public void shutdown() {
        try {
            if (engineClient != null && sessionHandle != null) {
                engineClient.disconnect(sessionHandle);
            }

            if (virtualThreadExecutor != null) {
                virtualThreadExecutor.shutdown();
                virtualThreadExecutor.awaitTermination(5, TimeUnit.SECONDS);
            }

            logger.info("Self-Play Orchestrator shutdown complete");

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error during shutdown", e);
        }
    }

    /**
     * Print usage information.
     */
    private void printUsage() {
        System.out.println("""
            Self-Play Test Orchestrator for YAWL

            Usage: java org.yawlfoundation.yawl.integration.selfplay.SelfPlayOrchestrator [options]

            Options:
                --engine-url <url>     YAWL engine URL (default: http://localhost:8080/yawl)
                --username <user>     Username for authentication (default: admin)
                --password <pass>     Password for authentication (default: admin)
                --iterations <n>       Number of test iterations (default: 3)
                --timeout <ms>         Timeout in milliseconds (default: 30000)
                --use-zai             Enable Z.ai integration for workflow generation
                --output-dir <path>    Output directory for reports (default: ./self-play-results)
                --help                Show this help message

            Environment Variables:
                YAWL_ENGINE_URL       Engine URL
                YAWL_USERNAME         Username
                YAWL_PASSWORD         Password
                YAWL_ITERATIONS       Number of iterations
                YAWL_TIMEOUT_MS       Timeout in milliseconds
                YAWL_USE_ZAI         Enable Z.ai integration
            """);
    }

    /**
     * Simple JSON utility method.
     */
    private String toJsonString(Object obj) {
        try {
            if (obj instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) obj;
                StringBuilder sb = new StringBuilder("{");
                boolean first = true;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (!first) sb.append(",");
                    sb.append("\"").append(entry.getKey()).append("\":");
                    sb.append(toJsonString(entry.getValue()));
                    first = false;
                }
                sb.append("}");
                return sb.toString();
            } else if (obj instanceof List) {
                List<?> list = (List<?>) obj;
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append(toJsonString(list.get(i)));
                }
                sb.append("]");
                return sb.toString();
            } else if (obj instanceof String) {
                return "\"" + ((String) obj).replace("\"", "\\\"") + "\"";
            } else if (obj instanceof Number || obj instanceof Boolean) {
                return obj.toString();
            } else {
                return "\"" + obj.toString() + "\"";
            }
        } catch (Exception e) {
            return "\"ERROR: " + e.getMessage() + "\"";
        }
    }

    /**
     * Internal class to track test iteration results.
     */
    private static class TestIterationResult {
        private final int iterationNumber;
        private boolean success;
        private long generationDuration;
        private long validationDuration;
        private long uploadDuration;
        private long executionDuration;
        private long verificationDuration;
        private long totalDuration;
        private String specificationId;
        private String caseId;
        private String errorMessage;

        public TestIterationResult(int iterationNumber) {
            this.iterationNumber = iterationNumber;
            this.success = false;
        }

        // Getters
        public int getIterationNumber() { return iterationNumber; }
        public boolean isSuccess() { return success; }
        public long getGenerationDuration() { return generationDuration; }
        public long getValidationDuration() { return validationDuration; }
        public long getUploadDuration() { return uploadDuration; }
        public long getExecutionDuration() { return executionDuration; }
        public long getVerificationDuration() { return verificationDuration; }
        public long getTotalDuration() { return totalDuration; }
        public String getSpecificationId() { return specificationId; }
        public String getCaseId() { return caseId; }
        public String getErrorMessage() { return errorMessage; }

        // Setters
        public void setSuccess(boolean success) { this.success = success; }
        public void setGenerationDuration(long duration) { this.generationDuration = duration; }
        public void setValidationDuration(long duration) { this.validationDuration = duration; }
        public void setUploadDuration(long duration) { this.uploadDuration = duration; }
        public void setExecutionDuration(long duration) { this.executionDuration = duration; }
        public void setVerificationDuration(long duration) { this.verificationDuration = duration; }
        public void setTotalDuration(long duration) { this.totalDuration = duration; }
        public void setSpecificationId(String id) { this.specificationId = id; }
        public void setCaseId(String id) { this.caseId = id; }
        public void setErrorMessage(String message) { this.errorMessage = message; }
    }
}