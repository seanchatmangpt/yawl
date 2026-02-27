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

package org.yawlfoundation.yawl.integration.factory;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.YSpecificationValidator;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.interfce.interfaceA.InterfaceA_EnvironmentBasedClient;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.integration.processmining.ProcessMiningFacade;
import org.yawlfoundation.yawl.integration.zai.SpecificationGenerator;

/**
 * Conversational workflow factory: Converts natural language descriptions to live
 * YAWL workflows in <30 seconds, then continuously monitors and improves conformance.
 *
 * <p>This class orchestrates the complete workflow lifecycle:
 * <ol>
 *   <li>Generate YAWL specification from natural language via Z.AI</li>
 *   <li>Validate against YAWL schema</li>
 *   <li>Upload to engine via InterfaceA</li>
 *   <li>Launch a case instance via InterfaceB</li>
 *   <li>Monitor execution conformance via process mining (every 60s after 10+ executions)</li>
 *   <li>Refine specification based on conformance feedback</li>
 * </ol>
 * </p>
 *
 * <h2>Example Usage</h2>
 * <pre>
 *   try (var factory = new ConversationalWorkflowFactory(
 *       specGenerator, interfaceAClient, interfaceBClient, processMining, sessionHandle)) {
 *
 *       var deployed = factory.generateAndDeploy(
 *           "Procurement workflow: request approval, then fulfillment");
 *
 *       if (deployed instanceof FactoryResult.Deployed d) {
 *           System.out.println("Spec deployed: " + d.specId());
 *           System.out.println("Case launched: " + d.caseId());
 *
 *           Thread.sleep(120000);  // Wait for conformance assessment
 *           var health = factory.getHealth(d.specId());
 *           if (health.needsRefinement()) {
 *               System.out.println("Recommendation: " + health.recommendation());
 *           }
 *       }
 *   }
 * </pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class ConversationalWorkflowFactory implements AutoCloseable {

    private static final Logger _log = LogManager.getLogger(ConversationalWorkflowFactory.class);

    private static final double CONFORMANCE_THRESHOLD = 0.90;
    private static final int MIN_EXECUTIONS_FOR_ASSESSMENT = 10;

    private final SpecificationGenerator specGenerator;
    private final InterfaceA_EnvironmentBasedClient interfaceAClient;
    private final InterfaceB_EnvironmentBasedClient interfaceBClient;
    private final ProcessMiningFacade processMiningFacade;
    private final String sessionHandle;

    private final ConcurrentHashMap<String, AtomicInteger> executionCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> conformanceScores = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> originalDescriptions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> specXmlCache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService conformanceScheduler;

    /**
     * Creates a new ConversationalWorkflowFactory.
     *
     * @param specGenerator the specification generator (Z.AI-backed)
     * @param interfaceAClient the InterfaceA client for uploading specs
     * @param interfaceBClient the InterfaceB client for launching cases
     * @param processMiningFacade the process mining facade for conformance assessment
     * @param sessionHandle the active YAWL session handle
     */
    public ConversationalWorkflowFactory(
            SpecificationGenerator specGenerator,
            InterfaceA_EnvironmentBasedClient interfaceAClient,
            InterfaceB_EnvironmentBasedClient interfaceBClient,
            ProcessMiningFacade processMiningFacade,
            String sessionHandle) {

        this.specGenerator = Objects.requireNonNull(specGenerator, "specGenerator must not be null");
        this.interfaceAClient = Objects.requireNonNull(interfaceAClient, "interfaceAClient must not be null");
        this.interfaceBClient = Objects.requireNonNull(interfaceBClient, "interfaceBClient must not be null");
        this.processMiningFacade = Objects.requireNonNull(processMiningFacade, "processMiningFacade must not be null");
        this.sessionHandle = Objects.requireNonNull(sessionHandle, "sessionHandle must not be null");

        this.conformanceScheduler = java.util.concurrent.Executors.newScheduledThreadPool(
            1, Thread.ofVirtual().name("factory-conformance-", 0).factory());
    }

    /**
     * Generates and deploys a workflow from a natural language description.
     *
     * <p>This method:
     * <ol>
     *   <li>Calls Z.AI to generate YAWL XML from description</li>
     *   <li>Validates against YAWL schema</li>
     *   <li>Uploads to engine via InterfaceA</li>
     *   <li>Launches a case instance via InterfaceB</li>
     *   <li>Schedules conformance assessment (every 60s after 10+ executions)</li>
     * </ol>
     * </p>
     *
     * @param nlDescription natural language workflow description
     * @return FactoryResult indicating success (Deployed) or failure (ValidationFailed)
     * @throws FactoryException if generation, validation, or deployment fails
     * @throws NullPointerException if nlDescription is null
     */
    public FactoryResult.Deployed generateAndDeploy(String nlDescription) throws FactoryException {
        Objects.requireNonNull(nlDescription, "nlDescription must not be null");

        _log.info("Generating workflow from description: {}", truncate(nlDescription, 100));

        // Step 1: Generate specification
        YSpecification spec;
        try {
            spec = specGenerator.generateFromDescription(nlDescription);
        } catch (Exception e) {
            throw new FactoryException("Generation failed: " + e.getMessage(), e);
        }

        // Step 2: Validate specification
        YSpecificationValidator validator = new YSpecificationValidator(spec);
        if (!validator.validate()) {
            List<String> errors = validator.getErrors()
                .stream()
                .map(err -> err.getMessage())
                .collect(Collectors.toList());
            throw new FactoryException("Validation failed: " + String.join(", ", errors));
        }

        // Step 3: Marshal to XML
        String specXml;
        try {
            specXml = spec.toXML();
        } catch (Exception e) {
            throw new FactoryException("Failed to marshal specification to XML: " + e.getMessage(), e);
        }

        // Step 4: Upload to engine
        String specId;
        try {
            String uploadResponse = interfaceAClient.uploadSpecification(specXml, sessionHandle);
            if (uploadResponse == null || uploadResponse.contains("fail")) {
                throw new FactoryException("Upload failed: " + uploadResponse);
            }
            specId = spec.getURI();
            _log.info("Specification uploaded: {}", specId);
        } catch (Exception e) {
            throw new FactoryException("Upload failed: " + e.getMessage(), e);
        }

        // Step 5: Launch case
        String caseId;
        try {
            YSpecificationID ySpecId = new YSpecificationID(specId);
            caseId = interfaceBClient.launchCase(ySpecId, null, null, sessionHandle);
            if (caseId == null || caseId.contains("fail")) {
                throw new FactoryException("Case launch failed: " + caseId);
            }
            _log.info("Case launched: {} for spec {}", caseId, specId);
        } catch (Exception e) {
            throw new FactoryException("Case launch failed: " + e.getMessage(), e);
        }

        // Step 6: Register in maps and schedule assessment
        executionCounts.putIfAbsent(specId, new AtomicInteger(0));
        conformanceScores.putIfAbsent(specId, -1.0);
        originalDescriptions.put(specId, nlDescription);
        specXmlCache.put(specId, specXml);

        scheduleConformanceAssessment(specId, spec);

        return new FactoryResult.Deployed(specId, caseId, Instant.now());
    }

    /**
     * Refines a specification based on feedback, then revalidates and redeployes.
     *
     * @param specId the specification ID to refine
     * @param feedback improvement feedback describing desired changes
     * @return FactoryResult indicating success (Refined) or failure (ValidationFailed)
     * @throws FactoryException if refinement or redeployment fails
     */
    public FactoryResult.Refined refine(String specId, String feedback) throws FactoryException {
        Objects.requireNonNull(specId, "specId must not be null");
        Objects.requireNonNull(feedback, "feedback must not be null");

        _log.info("Refining spec {} with feedback: {}", specId, truncate(feedback, 100));

        // Get cached XML and description
        String existingXml = specXmlCache.get(specId);
        String originalDesc = originalDescriptions.get(specId);

        if (existingXml == null || originalDesc == null) {
            throw new FactoryException("Specification " + specId + " not found in registry");
        }

        // Improve specification
        YSpecification improvedSpec;
        try {
            improvedSpec = specGenerator.improveSpecification(existingXml, feedback);
        } catch (Exception e) {
            throw new FactoryException("Refinement failed: " + e.getMessage(), e);
        }

        // Validate improved specification
        YSpecificationValidator validator = new YSpecificationValidator(improvedSpec);
        if (!validator.validate()) {
            List<String> errors = validator.getErrors()
                .stream()
                .map(err -> err.getMessage())
                .collect(Collectors.toList());
            throw new FactoryException("Validation failed after refinement: " + String.join(", ", errors));
        }

        // Measure previous conformance for comparison
        double previousConformance = conformanceScores.getOrDefault(specId, -1.0);

        // Upload refined spec (replace existing)
        String improvedXml;
        try {
            improvedXml = improvedSpec.toXML();
            String uploadResponse = interfaceAClient.uploadSpecification(improvedXml, sessionHandle);
            if (uploadResponse == null || uploadResponse.contains("fail")) {
                throw new FactoryException("Re-upload failed: " + uploadResponse);
            }
            _log.info("Refined specification uploaded: {}", specId);
        } catch (Exception e) {
            throw new FactoryException("Re-upload failed: " + e.getMessage(), e);
        }

        // Update cache and restart conformance assessment
        specXmlCache.put(specId, improvedXml);
        executionCounts.put(specId, new AtomicInteger(0));  // Reset counter
        conformanceScores.put(specId, -1.0);  // Reset score

        scheduleConformanceAssessment(specId, improvedSpec);

        return new FactoryResult.Refined(specId, previousConformance, -1.0, Instant.now());
    }

    /**
     * Gets the health status of a workflow specification.
     *
     * <p>Returns information about execution count, conformance score, and whether
     * the specification needs refinement (conformance < 0.90).</p>
     *
     * @param specId the specification ID
     * @return WorkflowHealth record with current metrics and recommendation
     */
    public WorkflowHealth getHealth(String specId) {
        Objects.requireNonNull(specId, "specId must not be null");

        int executionCount = executionCounts.getOrDefault(specId, new AtomicInteger(0)).get();
        double conformance = conformanceScores.getOrDefault(specId, -1.0);
        boolean needsRefinement = conformance >= 0 && conformance < CONFORMANCE_THRESHOLD;

        String recommendation = switch ((int) (conformance * 10)) {
            case -1 -> "No conformance data yet; wait for " + (MIN_EXECUTIONS_FOR_ASSESSMENT - executionCount) + " more executions";
            case 0, 1, 2, 3, 4, 5, 6, 7, 8 -> "Conformance below threshold; refine specification with feedback from execution traces";
            default -> "Conformance acceptable; continue monitoring";
        };

        return new WorkflowHealth(specId, executionCount, conformance, needsRefinement, Instant.now(), recommendation);
    }

    /**
     * Records a task execution for a specification.
     *
     * <p>This increments the execution counter. When counter reaches 10+,
     * the conformance scheduler will begin periodic assessments.</p>
     *
     * @param specId the specification ID
     */
    public void recordExecution(String specId) {
        if (specId != null) {
            executionCounts.computeIfAbsent(specId, k -> new AtomicInteger(0)).incrementAndGet();
        }
    }

    /**
     * Closes the factory and shuts down background tasks.
     *
     * <p>This stops the conformance scheduler without waiting for currently running tasks.
     * Call this when the factory is no longer needed.</p>
     */
    @Override
    public void close() {
        conformanceScheduler.shutdown();
        _log.info("ConversationalWorkflowFactory closed");
    }

    /**
     * Schedules periodic conformance assessment for a specification.
     *
     * <p>The assessment runs every 60 seconds, but only executes once execution count
     * reaches 10+. Exceptions in the background task are logged but not propagated.</p>
     *
     * @param specId the specification ID
     * @param spec the specification object
     */
    private void scheduleConformanceAssessment(String specId, YSpecification spec) {
        conformanceScheduler.scheduleWithFixedDelay(() -> {
            try {
                int execCount = executionCounts.getOrDefault(specId, new AtomicInteger(0)).get();
                if (execCount >= MIN_EXECUTIONS_FOR_ASSESSMENT) {
                    double fitness = measureConformance(specId, spec);
                    if (fitness >= 0) {
                        conformanceScores.put(specId, fitness);
                        _log.info("Conformance assessment for {}: {}", specId, fitness);
                    }
                }
            } catch (Exception e) {
                _log.warn("Conformance assessment failed for {}: {}", specId, e.getMessage());
            }
        }, 60, 60, java.util.concurrent.TimeUnit.SECONDS);
    }

    /**
     * Measures conformance via process mining token-based replay.
     *
     * @param specId the specification ID
     * @param spec the specification
     * @return conformance fitness score (0.0 to 1.0), or -1.0 on failure
     */
    private double measureConformance(String specId, YSpecification spec) {
        try {
            YSpecificationID ySpecId = new YSpecificationID(specId);
            ProcessMiningFacade.ProcessMiningReport report =
                processMiningFacade.analyze(ySpecId, spec.getRootNet(), false);

            if (report != null && report.conformance != null) {
                return report.conformance.computeFitness();
            }
        } catch (Exception e) {
            _log.debug("Failed to measure conformance for {}: {}", specId, e.getMessage());
        }
        return -1.0;
    }

    private static String truncate(String s, int maxLength) {
        if (s == null) return "[null]";
        return s.length() > maxLength ? s.substring(0, maxLength) + "..." : s;
    }

    // =========================================================================
    // Result Types
    // =========================================================================

    /**
     * Sealed interface for factory operation results.
     */
    public sealed interface FactoryResult permits
        FactoryResult.Deployed,
        FactoryResult.ValidationFailed,
        FactoryResult.Refined {

        /**
         * Specification successfully deployed and case launched.
         *
         * @param specId the specification ID
         * @param caseId the launched case ID
         * @param deployedAt timestamp of deployment
         */
        record Deployed(String specId, String caseId, Instant deployedAt) implements FactoryResult {}

        /**
         * Specification failed validation.
         *
         * @param errors list of validation error messages
         */
        record ValidationFailed(List<String> errors) implements FactoryResult {}

        /**
         * Specification successfully refined and redeployed.
         *
         * @param specId the specification ID
         * @param previousConformance conformance score before refinement
         * @param newConformance conformance score after refinement (may be -1.0 if pending)
         * @param refinedAt timestamp of refinement
         */
        record Refined(String specId, double previousConformance, double newConformance, Instant refinedAt)
            implements FactoryResult {}
    }

    // =========================================================================
    // Health Record
    // =========================================================================

    /**
     * Workflow health assessment.
     *
     * @param specId the specification ID
     * @param executionCount number of times this workflow has been executed
     * @param conformanceScore token-based replay fitness (0.0-1.0), or -1.0 if not yet assessed
     * @param needsRefinement true if conformance < 0.90
     * @param lastAssessedAt timestamp of last assessment (or creation)
     * @param recommendation actionable recommendation for improvement
     */
    public record WorkflowHealth(String specId, int executionCount, double conformanceScore,
        boolean needsRefinement, Instant lastAssessedAt, String recommendation) {}

    // =========================================================================
    // Exception Type
    // =========================================================================

    /**
     * Exception thrown when factory operations fail.
     */
    public static final class FactoryException extends Exception {
        /**
         * Creates a new FactoryException with a message.
         *
         * @param message the error message
         */
        public FactoryException(String message) {
            super(message);
        }

        /**
         * Creates a new FactoryException with a message and cause.
         *
         * @param message the error message
         * @param cause the underlying exception
         */
        public FactoryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
