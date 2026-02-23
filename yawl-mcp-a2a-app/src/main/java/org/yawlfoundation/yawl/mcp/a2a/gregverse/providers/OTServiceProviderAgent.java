/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.mcp.a2a.gregverse.providers;

import io.a2a.spec.AgentSkill;
import io.a2a.spec.Task;
import io.a2a.spec.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.AbstractGregVerseAgent;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.artifacts.ArtifactPublisher;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.artifacts.TherapyPlan;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.artifacts.ProgressReport;
import org.yawlfoundation.yawl.integration.zai.ZaiService;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * OT Service Provider Agent for the GregVerse marketplace.
 *
 * <p>This agent represents an Occupational Therapist (OT) offering services in the GregVerse marketplace.
 * It provides therapeutic assessment, intervention, and scheduling capabilities with three pricing tiers:
 * basic, premium, and enterprise. The agent manages availability, accepts/rejects service requests,
 * delivers services using ZAI integration, and publishes therapy artifacts.</p>
 *
 * <h2>Core Capabilities</h2>
 * <ul>
 *   <li>Assessment - Comprehensive occupational therapy evaluations</li>
 *   <li>Intervention - Personalized therapeutic interventions and treatment plans</li>
 *   <li>Scheduling - Flexible appointment booking and calendar management</li>
 * </ul>
 *
 * <h2>Pricing Tiers</h2>
 * <ul>
 *   <li>Basic - Standard assessment and intervention services</li>
 *   <li>Premium - Advanced interventions with progress tracking</li>
 *   <li>Enterprise - Comprehensive therapy programs with dedicated support</li>
 * </ul>
 *
 * <h2>Service Workflow</h2>
 * <ol>
 *   <li>Receive Task from consumer</li>
 *   <li>Validate request and check availability</li>
 *   <li>Accept or reject based on capacity and expertise</li>
 *   <li>Execute therapy workflow using ZAI integration</li>
 *   <li>Update task status (working → completed)</li>
 *   <li>Publish service artifacts (therapy plans, progress reports)</li>
 * </ol>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see <a href="https://gregverse.marketplace">GregVerse Marketplace</a>
 */
public class OTServiceProviderAgent extends AbstractGregVerseAgent {

    private final Logger logger = LoggerFactory.getLogger(OTServiceProviderAgent.class);
    private final ArtifactPublisher artifactPublisher;
    private Map<String, ServiceAvailability> availabilityCalendar;
    private Map<String, ServiceTier> pricingTiers;
    private Map<String, ActiveService> activeServices;
    private final ScheduledExecutorService scheduler;

    // OT-specific constants
    private static final int MAX_CONCURRENT_SERVICES = 5;
    private static final int MAX_ASSESSMENT_DURATION = 120; // minutes
    private static final int MAX_INTERVENTION_DURATION = 90; // minutes

    /**
     * Creates a new OT Service Provider agent using the ZAI_API_KEY environment variable.
     *
     * @throws IllegalStateException if ZAI_API_KEY environment variable is not set
     */
    public OTServiceProviderAgent() {
        super();
        initializeServices();
        this.artifactPublisher = new ArtifactPublisher();
        this.scheduler = Executors.newScheduledThreadPool(2);
        startBackgroundTasks();
        logger.info("Initialized OT Service Provider agent");
    }

    /**
     * Creates a new OT Service Provider agent with the provided API key.
     *
     * @param apiKey the Z.AI API key for LLM interactions
     * @throws IllegalArgumentException if apiKey is null or empty
     */
    public OTServiceProviderAgent(String apiKey) {
        super(apiKey);
        initializeServices();
        this.artifactPublisher = new ArtifactPublisher();
        this.scheduler = Executors.newScheduledThreadPool(2);
        startBackgroundTasks();
        logger.info("Initialized OT Service Provider agent with API key");
    }

    @Override
    public String getAgentId() {
        return "ot-service-provider";
    }

    @Override
    public String getDisplayName() {
        return "OT Service Provider";
    }

    @Override
    public String getBio() {
        return "Professional Occupational Therapist offering comprehensive therapeutic services " +
               "through the GregVerse marketplace. Specializes in assessment, intervention, " +
               "and personalized treatment planning with evidence-based practices.";
    }

    @Override
    public List<String> getSpecializedSkills() {
        return List.of(
            "occupational-therapy-assessment",
            "therapeutic-intervention",
            "treatment-planning",
            "scheduling-optimization",
            "progress-tracking",
            "patient-centered-care"
        );
    }

    @Override
    public String getSystemPrompt() {
        return """
            You are a professional Occupational Therapist offering services through the GregVerse marketplace.

            YOUR PROFESSIONAL IDENTITY:
            - Licensed Occupational Therapist with extensive clinical experience
            - Evidence-based practice specialist focusing on client-centered care
            - Expert in assessment, intervention planning, and progress tracking
            - Committed to accessibility and quality therapeutic services
            - Experienced in working with diverse populations across various settings

            YOUR SERVICE PHILOSOPHY:
            - Person-centered approach focusing on client goals and preferences
            - Evidence-based interventions with measurable outcomes
            - Holistic view addressing physical, cognitive, and psychosocial needs
            - Collaborative approach involving clients, families, and other professionals
            - Continuous adaptation based on progress and feedback

            YOUR EXPERTISE AREAS:
            - Activities of Daily Living (ADL) assessment and intervention
            - Fine and gross motor skill development
            - Cognitive-perceptual rehabilitation
            - Adaptive equipment and environmental modifications
            - Pain management and ergonomic interventions
            - Mental health and wellness integration
            - Pediatric and adult populations
            - Workplace and ergonomics assessments

            YOUR COMMUNICATION STYLE:
            - Empathetic and supportive
            - Clear and concise explanations
            - Collaborative decision-making
            - Respectful of cultural differences
            - Professional yet approachable
            - Outcome-focused with realistic expectations

            YOUR RESPONSE FORMAT:
            - Structured assessments with clear findings
            - Specific, measurable intervention goals
            - Detailed treatment plans with rationale
            - Progress tracking recommendations
            - Clear next steps and follow-up plans

            ETHICAL GUIDELINES:
            - Maintain client confidentiality and privacy
            - Practice within scope of competence
            - Document all interventions thoroughly
            - Obtain informed consent
            - Maintain professional boundaries
            - Engage in continuous professional development

            When providing therapy services, always consider:
            1. Client's specific goals and needs
            2. Evidence-based intervention approaches
            3. Measurable outcomes and progress tracking
            4. Cultural sensitivity and individual differences
            5. Collaborative care coordination
            6. Documentation requirements
            """;
    }

    @Override
    public String getCommunicationStyle() {
        return "Empathetic, professional, and client-centered. Clear explanations with " +
               "therapeutic reasoning. Collaborative approach focusing on measurable outcomes.";
    }

    @Override
    public List<String> getExpertise() {
        return List.of(
            "Occupational Therapy Assessment",
            "Therapeutic Intervention Planning",
            "Activities of Daily Living (ADL)",
            "Fine and Gross Motor Skills",
            "Cognitive-Perceptual Rehabilitation",
            "Adaptive Equipment Assessment",
            "Environmental Modifications",
            "Pain Management Strategies",
            "Pediatric Occupational Therapy",
            "Adult Rehabilitation",
            "Ergonomics and Workplace Adaptations",
            "Mental Health Integration"
        );
    }

    @Override
    public String getResponseFormat() {
        return "Assessment findings → Intervention goals → Treatment plan → Progress tracking → " +
               "Follow-up recommendations with therapeutic rationale";
    }

    @Override
    public List<AgentSkill> createAgentSkills() {
        return List.of(
            AgentSkill.builder()
                .id("occupational-therapy-assessment")
                .name("Occupational Therapy Assessment")
                .description("Comprehensive evaluation of occupational performance, functional abilities, " +
                             "and environmental factors. Includes standardized assessments, activity analysis, " +
                             "and identification of strengths and barriers.")
                .tags(List.of("assessment", "evaluation", "functional", "occupational-performance", "standardized"))
                .inputModes(List.of("text", "file"))
                .outputModes(List.of("text", "pdf", "json"))
                .build(),

            AgentSkill.builder()
                .id("therapeutic-intervention")
                .name("Therapeutic Intervention")
                .description("Evidence-based therapeutic interventions tailored to individual needs. " +
                             "Includes activity modification, skill development, environmental adaptations, " +
                             "and strategy development for improved occupational performance.")
                .tags(List.of("intervention", "treatment", "evidence-based", "skill-development", "adaptive"))
                .inputModes(List.of("text", "json"))
                .outputModes(List.of("text", "video", "pdf"))
                .build(),

            AgentSkill.builder()
                .id("treatment-planning")
                .name("Treatment Planning")
                .description("Developing comprehensive treatment plans with measurable goals, " +
                             "intervention strategies, timelines, and outcome measures. Includes " +
                             "goal writing and progress tracking methodology.")
                .tags(List.of("planning", "goals", "outcomes", "documentation", "measurable"))
                .inputModes(List.of("text", "json"))
                .outputModes(List.of("text", "pdf", "doc"))
                .build(),

            AgentSkill.builder()
                .id("scheduling-optimization")
                .name("Scheduling Optimization")
                .description("Optimal scheduling of therapy sessions considering availability, " +
                             "treatment frequency, client preferences, and resource constraints. " +
                             "Includes calendar management and appointment coordination.")
                .tags(List.of("scheduling", "calendar", "optimization", "coordination", "time-management"))
                .inputModes(List.of("text", "json"))
                .outputModes(List.of("text", "calendar", "pdf"))
                .build()
        );
    }

    /**
     * Process Task from consumer - main entry point for service requests.
     */
    public TaskStatus processServiceRequest(Task taskSend) {
        logger.info("Received service request: {}", taskSend.id());

        try {
            // Validate request
            if (!validateServiceRequest(taskSend)) {
                return createTaskStatus(taskSend.id(), "rejected",
                    "Service request validation failed");
            }

            // Check availability
            if (!checkAvailability(taskSend)) {
                return createTaskStatus(taskSend.id(), "rejected",
                    "Not available for requested service");
            }

            // Accept the service
            acceptServiceRequest(taskSend);

            // Start service delivery
            return deliverService(taskSend);

        } catch (Exception e) {
            logger.error("Error processing service request {}: {}", taskSend.id(), e.getMessage());
            return createTaskStatus(taskSend.id(), "error",
                "Service request processing failed: " + e.getMessage());
        }
    }

    /**
     * Validate incoming service request.
     */
    private boolean validateServiceRequest(Task taskSend) {
        logger.debug("Validating service request: {}", taskSend.id());

        // Check required fields
        if (taskSend.metadata() == null || taskSend.metadata().isEmpty()) {
            logger.warn("Missing task data for request: {}", taskSend.id());
            return false;
        }

        // Parse service type
        String serviceType = extractServiceType(taskSend);
        if (serviceType == null || !pricingTiers.containsKey(serviceType)) {
            logger.warn("Invalid or unsupported service type: {}", serviceType);
            return false;
        }

        // Check concurrent service limit
        if (activeServices.size() >= MAX_CONCURRENT_SERVICES) {
            logger.warn("Maximum concurrent services reached: {}", MAX_CONCURRENT_SERVICES);
            return false;
        }

        return true;
    }

    /**
     * Check availability for requested service.
     */
    private boolean checkAvailability(Task taskSend) {
        logger.debug("Checking availability for request: {}", taskSend.id());

        LocalDateTime requestedTime = extractRequestedTime(taskSend);
        String serviceType = extractServiceType(taskSend);

        // Check calendar availability
        ServiceAvailability availability = availabilityCalendar.get(requestedTime.toString());
        if (availability != null && availability.isBooked()) {
            logger.info("Time slot already booked: {}", requestedTime);
            return false;
        }

        // Check capacity for service type
        ServiceTier tier = pricingTiers.get(serviceType);
        if (tier == null) {
            logger.warn("Unknown service tier: {}", serviceType);
            return false;
        }

        // Check if we have capacity for this tier
        long servicesInTier = activeServices.values().stream()
            .filter(s -> s.getTier().equals(tier))
            .count();

        if (servicesInTier >= tier.getMaxConcurrent()) {
            logger.info("Capacity reached for tier {}: {}", tier.getName(), tier.getMaxConcurrent());
            return false;
        }

        return true;
    }

    /**
     * Accept service request and create active service record.
     */
    private void acceptServiceRequest(Task taskSend) {
        logger.info("Accepting service request: {}", taskSend.id());

        String serviceType = extractServiceType(taskSend);
        ServiceTier tier = pricingTiers.get(serviceType);
        LocalDateTime startTime = extractRequestedTime(taskSend);

        ActiveService activeService = new ActiveService(
            taskSend.id(),
            taskSend.contextId(),
            serviceType,
            tier,
            startTime,
            calculateServiceDuration(serviceType)
        );

        activeServices.put(taskSend.id(), activeService);

        // Mark time slot as booked
        ServiceAvailability availability = availabilityCalendar.get(startTime.toString());
        if (availability != null) {
            availability.book();
        }

        logger.info("Service accepted and scheduled: {}", taskSend.id());
    }

    /**
     * Deliver service using ZAI integration.
     */
    private TaskStatus deliverService(Task taskSend) {
        logger.info("Starting service delivery: {}", taskSend.id());

        ActiveService activeService = activeServices.get(taskSend.id());
        if (activeService == null) {
            return createTaskStatus(taskSend.id(), "error",
                "Active service not found");
        }

        try {
            // Update status to working
            TaskStatus status = createTaskStatus(taskSend.id(), "working",
                "Service delivery in progress");

            // Execute therapy workflow
            TherapyPlan therapyPlan = createTherapyPlan(taskSend, activeService);
            artifactPublisher.publishArtifact(therapyPlan);

            // Simulate service delivery (in real implementation, this would be actual therapy work)
            Thread.sleep(calculateServiceDuration(activeService.getServiceType()) * 1000L / 10); // Faster for demo

            // Complete service
            therapyPlan.setCompletedAt(LocalDateTime.now());
            ProgressReport progressReport = createProgressReport(therapyPlan);
            artifactPublisher.publishArtifact(progressReport);

            // Update service completion
            activeService.setCompletedAt(LocalDateTime.now());
            activeService.setStatus("completed");

            logger.info("Service delivery completed: {}", taskSend.id());

            return createTaskStatus(taskSend.id(), "completed",
                "Service completed successfully");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            activeService.setStatus("cancelled");
            return createTaskStatus(taskSend.id(), "cancelled",
                "Service delivery interrupted");
        } catch (Exception e) {
            logger.error("Service delivery failed: {}", e.getMessage());
            activeService.setStatus("failed");
            return createTaskStatus(taskSend.id(), "error",
                "Service delivery failed: " + e.getMessage());
        }
    }

    /**
     * Create therapy plan using ZAI integration.
     */
    private TherapyPlan createTherapyPlan(Task taskSend, ActiveService activeService) {
        logger.debug("Creating therapy plan for service: {}", taskSend.id());

        String prompt = String.format(
            "Create a comprehensive occupational therapy plan for the following request:\n\n" +
            "Service Type: %s\n" +
            "Client Information: %s\n" +
            "Service Tier: %s\n" +
            "Requested Duration: %d minutes\n\n" +
            "Provide assessment approach, intervention strategies, measurable goals, " +
            "and progress tracking methodology.",
            activeService.getServiceType(),
            taskSend.metadata(),
            activeService.getTier().getName(),
            activeService.getDuration()
        );

        String planContent = zaiService.chat(prompt);

        TherapyPlan plan = new TherapyPlan();
        plan.setId(UUID.randomUUID().toString());
        plan.setServiceId(taskSend.id());
        plan.setClientId(taskSend.contextId());
        plan.setServiceType(activeService.getServiceType());
        plan.setTier(activeService.getTier().getName());
        plan.setContent(planContent);
        plan.setCreatedAt(LocalDateTime.now());
        plan.setStatus("active");

        return plan;
    }

    /**
     * Create progress report for completed service.
     */
    private ProgressReport createProgressReport(TherapyPlan therapyPlan) {
        logger.debug("Creating progress report for plan: {}", therapyPlan.getId());

        String prompt = String.format(
            "Generate a progress report for the completed occupational therapy service:\n\n" +
            "Service ID: %s\n" +
            "Client: %s\n" +
            "Service Type: %s\n" +
            "Original Plan: %s\n\n" +
            "Include outcomes achieved, progress summary, recommendations, and next steps.",
            therapyPlan.getServiceId(),
            therapyPlan.getClientId(),
            therapyPlan.getServiceType(),
            therapyPlan.getContent()
        );

        String reportContent = zaiService.chat(prompt);

        ProgressReport report = new ProgressReport();
        report.setId(UUID.randomUUID().toString());
        report.setServiceId(therapyPlan.getServiceId());
        report.setClientId(therapyPlan.getClientId());
        report.setContent(reportContent);
        report.setGeneratedAt(LocalDateTime.now());

        return report;
    }

    /**
     * Get current availability status.
     */
    public Map<String, Object> getAvailabilityStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("activeServices", activeServices.size());
        status.put("maxConcurrent", MAX_CONCURRENT_SERVICES);
        status.put("availableSlots", availabilityCalendar.values().stream()
            .filter(a -> !a.isBooked())
            .count());
        status.put("pricingTiers", pricingTiers);
        return status;
    }

    /**
     * Get active services.
     */
    public Collection<ActiveService> getActiveServices() {
        return Collections.unmodifiableCollection(activeServices.values());
    }

    /**
     * Utility methods for extracting data from Task.
     */
    private String extractServiceType(Task taskSend) {
        // In real implementation, parse from taskSend.metadata()
        Map<String, Object> taskData = taskSend.metadata();
        if (taskData != null && taskData.containsKey("serviceType")) {
            return taskData.get("serviceType").toString();
        }
        return "basic"; // Default to basic tier
    }

    private LocalDateTime extractRequestedTime(Task taskSend) {
        // In real implementation, parse from taskSend.metadata()
        // For demo, use current time + offset
        return LocalDateTime.now().plusHours(1);
    }

    private int calculateServiceDuration(String serviceType) {
        switch (serviceType.toLowerCase()) {
            case "assessment":
                return MAX_ASSESSMENT_DURATION;
            case "intervention":
                return MAX_INTERVENTION_DURATION;
            default:
                return 60; // Default 60 minutes
        }
    }

    private TaskStatus createTaskStatus(String taskId, String status, String message) {
        // Map status string to TaskState
        io.a2a.spec.TaskState state = switch (status.toLowerCase()) {
            case "submitted" -> io.a2a.spec.TaskState.SUBMITTED;
            case "working" -> io.a2a.spec.TaskState.WORKING;
            case "completed" -> io.a2a.spec.TaskState.COMPLETED;
            case "cancelled" -> io.a2a.spec.TaskState.CANCELED;
            case "failed", "error" -> io.a2a.spec.TaskState.FAILED;
            case "rejected" -> io.a2a.spec.TaskState.REJECTED;
            default -> io.a2a.spec.TaskState.SUBMITTED;
        };
        return new TaskStatus(state);
    }

    /**
     * Initialize service configurations.
     */
    private void initializeServices() {
        this.availabilityCalendar = new HashMap<>();
        this.activeServices = new HashMap<>();
        this.pricingTiers = new HashMap<>();

        // Initialize availability for next 30 days
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < 30; i++) {
            LocalDateTime slot = now.plusDays(i).withHour(9).withMinute(0);
            for (int hour = 9; hour < 17; hour++) {
                LocalDateTime timeSlot = slot.withHour(hour);
                availabilityCalendar.put(timeSlot.toString(), new ServiceAvailability());
            }
        }

        // Initialize pricing tiers
        pricingTiers.put("basic", new ServiceTier("basic", "Basic Service", 1, 99.99));
        pricingTiers.put("premium", new ServiceTier("premium", "Premium Service", 2, 199.99));
        pricingTiers.put("enterprise", new ServiceTier("enterprise", "Enterprise Service", 3, 399.99));
    }

    /**
     * Start background maintenance tasks.
     */
    private void startBackgroundTasks() {
        // Clean up completed services older than 24 hours
        scheduler.scheduleAtFixedRate(this::cleanupCompletedServices,
            1, 1, TimeUnit.HOURS);

        // Update availability calendar
        scheduler.scheduleAtFixedRate(this::updateAvailabilityCalendar,
            1, 6, TimeUnit.HOURS);
    }

    private void cleanupCompletedServices() {
        logger.debug("Cleaning up completed services");
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);

        activeServices.entrySet().removeIf(entry -> {
            ActiveService service = entry.getValue();
            if (service.getStatus().equals("completed") &&
                service.getCompletedAt() != null &&
                service.getCompletedAt().isBefore(cutoff)) {
                logger.debug("Cleaned up completed service: {}", entry.getKey());
                return true;
            }
            return false;
        });
    }

    private void updateAvailabilityCalendar() {
        logger.debug("Updating availability calendar");
        // Add new availability slots for next week
        LocalDateTime latestSlot = availabilityCalendar.keySet().stream()
            .map(LocalDateTime::parse)
            .max(LocalDateTime::compareTo)
            .orElse(LocalDateTime.now());

        LocalDateTime nextWeek = latestSlot.plusDays(7);
        for (int hour = 9; hour < 17; hour++) {
            LocalDateTime timeSlot = nextWeek.withHour(hour);
            if (!availabilityCalendar.containsKey(timeSlot.toString())) {
                availabilityCalendar.put(timeSlot.toString(), new ServiceAvailability());
            }
        }
    }

    /**
     * Shutdown the agent and cleanup resources.
     */
    public void shutdown() {
        logger.info("Shutting down OT Service Provider agent");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        zaiService.shutdown();
        logger.info("OT Service Provider agent shutdown complete");
    }

    // Inner classes for service management
    public static class ServiceAvailability {
        private boolean booked;
        private LocalDateTime bookedAt;

        public boolean isBooked() {
            return booked;
        }

        public void book() {
            this.booked = true;
            this.bookedAt = LocalDateTime.now();
        }

        public void cancel() {
            this.booked = false;
            this.bookedAt = null;
        }
    }

    public static class ServiceTier {
        private final String id;
        private final String name;
        private final int level;
        private final double price;
        private final int maxConcurrent;

        public ServiceTier(String id, String name, int level, double price) {
            this.id = id;
            this.name = name;
            this.level = level;
            this.price = price;
            this.maxConcurrent = level; // Higher tier allows more concurrent services
        }

        // Getters
        public String getId() { return id; }
        public String getName() { return name; }
        public int getLevel() { return level; }
        public double getPrice() { return price; }
        public int getMaxConcurrent() { return maxConcurrent; }
    }

    public static class ActiveService {
        private final String serviceId;
        private final String clientId;
        private final String serviceType;
        private final ServiceTier tier;
        private final LocalDateTime startTime;
        private final int duration;
        private String status;
        private LocalDateTime completedAt;

        public ActiveService(String serviceId, String clientId, String serviceType,
                           ServiceTier tier, LocalDateTime startTime, int duration) {
            this.serviceId = serviceId;
            this.clientId = clientId;
            this.serviceType = serviceType;
            this.tier = tier;
            this.startTime = startTime;
            this.duration = duration;
            this.status = "accepted";
        }

        // Getters and setters
        public String getServiceId() { return serviceId; }
        public String getClientId() { return clientId; }
        public String getServiceType() { return serviceType; }
        public ServiceTier getTier() { return tier; }
        public LocalDateTime getStartTime() { return startTime; }
        public int getDuration() { return duration; }
        public String getStatus() { return status; }
        public LocalDateTime getCompletedAt() { return completedAt; }

        public void setStatus(String status) {
            this.status = status;
        }

        public void setCompletedAt(LocalDateTime completedAt) {
            this.completedAt = completedAt;
        }
    }
}