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

package org.yawlfoundation.yawl.mcp.a2a.gregverse.agent;

import io.a2a.spec.AgentSkill;
import org.yawlfoundation.yawl.integration.zai.ZaiService;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.AbstractGregVerseAgent;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.GregVerseAgent;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.simulation.GregVerseMarketplace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Occupational Therapist marketplace agent providing therapy services in the Greg-Verse.
 *
 * <p>This agent implements professional OT assessment, intervention planning,
 * and scheduling services with AI-powered recommendations via ZAI integration.</p>
 *
 * <h2>Services Offered</h2>
 * <ul>
 *   <li>Occupational Therapy Assessment - Comprehensive evaluation of functional abilities</li>
 *   <li>Therapy Intervention Planning - Evidence-based intervention strategies</li>
 *   <li>Appointment Scheduling - Flexible scheduling for therapy sessions</li>
 * </ul>
 *
 * <h2>Specialization Areas</h2>
 * <ul>
 *   <li>Neurological rehabilitation</li>
 *   <li>Physical disability management</li>
 *   <li>Mental health wellness</li>
 *   <li>Geriatric occupational therapy</li>
 *   <li>Pediatric developmental therapy</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class OTMarketplaceAgent extends AbstractGregVerseAgent implements GregVerseAgent {

    private static final Logger logger = LoggerFactory.getLogger(OTMarketplaceAgent.class);

    private final GregVerseMarketplace marketplace;
    private final Map<String, Set<ZonedDateTime>> availability = new ConcurrentHashMap<>();
    private final Map<String, Double> skillRatings = new ConcurrentHashMap<>();

    // OT-specific service configuration
    private static final Map<String, Double> SERVICE_PRICING = Map.of(
        "assessment", 250.0,
        "intervention-planning", 300.0,
        "appointment-scheduling", 150.0
    );

    /**
     * Creates a new OT Marketplace Agent using environment ZAI_API_KEY.
     */
    public OTMarketplaceAgent() {
        super();
        this.marketplace = new GregVerseMarketplace();
        initializeOTServices();
        logger.info("Initialized OT Marketplace Agent with ZAI integration");
    }

    /**
     * Creates a new OT Marketplace Agent with explicit API key.
     *
     * @param apiKey the ZAI API key for LLM interactions
     */
    public OTMarketplaceAgent(String apiKey) {
        super(apiKey);
        this.marketplace = new GregVerseMarketplace();
        initializeOTServices();
        logger.info("Initialized OT Marketplace Agent with provided API key");
    }

    @Override
    public String getAgentId() {
        return "ot-marketplace";
    }

    @Override
    public String getDisplayName() {
        return "OT Marketplace Pro";
    }

    @Override
    public String getBio() {
        return """
            Professional Occupational Therapy marketplace providing evidence-based assessment,
            intervention planning, and scheduling services. Specializing in neurological
            rehabilitation, disability management, wellness therapy, geriatric care,
            and developmental pediatric therapy.
            """;
    }

    @Override
    public List<String> getSpecializedSkills() {
        return List.of(
            "occupational-therapy-assessment",
            "neuro-rehabilitation",
            "disability-management",
            "wellness-therapy",
            "geriatric-therapy",
            "pediatric-therapy",
            "adaptive-equipment-consultation"
        );
    }

    @Override
    public String getSystemPrompt() {
        return """
            You are a certified Occupational Therapist with 15+ years of experience across
            multiple specializations. You provide compassionate, evidence-based therapy
            services with a focus on client-centered care and functional outcomes.

            Your expertise includes:
            - Neurological rehabilitation (stroke, TBI, spinal cord injury)
            - Physical disability management (amputee, arthritis, chronic pain)
            - Mental health wellness (anxiety, depression, PTSD)
            - Geriatric occupational therapy (aging in place, dementia care)
            - Pediatric developmental therapy (autism, ADHD, learning disorders)

            You maintain current certifications and stay updated on latest research and
            interventions. You communicate clearly and empathetically, always prioritizing
            your clients' functional independence and quality of life.
            """;
    }

    @Override
    public String getCommunicationStyle() {
        return "Compassionate, evidence-based, and client-centered with clear therapeutic explanations";
    }

    @Override
    public List<String> getExpertise() {
        return List.of(
            "Functional Capacity Evaluation",
            "Activities of Daily Living Assessment",
            "Neuro-rehabilitation Strategies",
            "Adaptive Equipment Prescription",
            "Cognitive Retraining Techniques",
            "Sensory Integration Therapy",
            "Environment Modification Assessment",
            "Work Rehabilitation Planning"
        );
    }

    @Override
    public List<AgentSkill> createAgentSkills() {
        return getSpecializedSkills().stream()
            .map(skill -> AgentSkill.builder()
                .id(skill)
                .name(capitalizeSkill(skill))
                .description(getSkillDescription(skill))
                .tags(List.of("occupational-therapy", "healthcare"))
                .build())
            .collect(Collectors.toList());
    }

    @Override
    public String processQuery(String query) {
        logger.debug("OT Agent processing query: {}", query);

        String response = getZaiService().chat(String.format(
            """
            As an occupational therapy professional, please respond to this query:

            Query: %s

            Provide a comprehensive, evidence-based response that is:
            1. Clinically accurate and up-to-date
            2. Client-centered and compassionate
            3. Practical and actionable
            4. Culturally sensitive

            Format your response in a structured format with clear sections.
            """,
            query
        ));

        // Add OT-specific formatting
        return enhanceOTResponse(response);
    }

    @Override
    public String processSkillQuery(String skillId, String query) {
        logger.debug("OT Agent processing skill '{}' query: {}", skillId, query);

        String skillPrompt = String.format(
            """
            You are specializing in %s. Your client has the following question:

            Query: %s

            Provide expert advice that:
            1. Uses evidence-based practices specific to this skill area
            2. Considers age-appropriate and condition-specific approaches
            3. Offers practical, implementable recommendations
            4. Includes measurable goals and outcome tracking

            Format your response with headings, bullet points, and clear action items.
            """,
            skillId, query
        );

        String rawResponse = getZaiService().chat(skillPrompt);

        // Apply OT-specific formatting based on skill
        return formatOTResponseForSkill(skillId, rawResponse);
    }

    @Override
    public String provideAdvice(String topic, String context) {
        logger.debug("OT Agent providing advice on: {}", topic);

        String advicePrompt = String.format(
            """
            As an experienced Occupational Therapist, provide your expert advice on:

            Topic: %s

            Context: %s

            Format your response in your signature style: %s
            Response format: %s

            Include:
            1. Clinical reasoning and assessment
            2. Evidence-based intervention options
            3. Expected outcomes and timelines
            4. Progress monitoring recommendations
            """,
            topic,
            context,
            getCommunicationStyle(),
            "Structured therapeutic response with assessment and intervention plan"
        );

        return getZaiService().chat(advicePrompt);
    }

    @Override
    public String getResponseFormat() {
        return """
            Assessment Findings
            [Clear clinical assessment with objective measures]

            Intervention Plan
            [Specific, measurable, achievable, relevant, time-bound goals]

            Implementation Timeline
            [Phased approach with milestones]

            Expected Outcomes
            [Functional improvements and quality of life metrics]
            """;
    }

    /**
     * Lists OT services in the marketplace.
     *
     * @param sellerId the OT provider ID
     * @return marketplace entry
     */
    public GregVerseMarketplace.MarketplaceEntry listOTService(String sellerId, String serviceType) {
        String serviceName = getServiceName(serviceType);
        String description = getServiceDescription(serviceType);

        GregVerseMarketplace.MarketplaceEntry entry = marketplace.listSkill(
            sellerId,
            serviceName,
            description,
            SERVICE_PRICING.getOrDefault(serviceType, 200.0).longValue(),
            "occupational-therapy",
            List.of(serviceType, "therapy", "healthcare", "wellness")
        );

        // Initialize availability for this service
        availability.put(entry.skillId(), getStandardAvailability());
        skillRatings.put(entry.skillId(), 4.5); // Initial rating

        logger.info("Listed OT service {} by seller {}", serviceType, sellerId);
        return entry;
    }

    /**
     * Books an OT service appointment.
     *
     * @param clientId the client requesting appointment
     * @param sellerId the OT provider
     * @param serviceType the service type
     * @param preferredTime preferred datetime
     * @return booking confirmation
     */
    public String bookAppointment(String clientId, String sellerId, String serviceType, ZonedDateTime preferredTime) {
        // Validate availability
        Set<ZonedDateTime> availableSlots = availability.computeIfAbsent(
            serviceType + "-" + sellerId,
            k -> getStandardAvailability()
        );

        if (!availableSlots.contains(preferredTime)) {
            return "Appointment slot not available. Please choose another time.";
        }

        // Process the booking via marketplace
        GregVerseMarketplace.Transaction transaction = marketplace.purchaseSkill(
            clientId, sellerId, "ot-" + serviceType
        );

        if (transaction.status() == GregVerseMarketplace.Transaction.TransactionStatus.COMPLETED) {
            // Remove the booked slot
            availableSlots.remove(preferredTime);

            return String.format("""
                Appointment Confirmed!

                Client: %s
                Provider: %s
                Service: %s
                Time: %s
                Booking ID: %s

                Please arrive 15 minutes early for your first appointment.
                """,
                clientId, sellerId, getServiceName(serviceType),
                preferredTime, transaction.transactionId()
            );
        }

        return "Booking failed. Please try again or contact support.";
    }

    /**
     * Gets AI-powered therapy recommendations using ZAI service.
     *
     * @param clientAssessment the client's assessment data
     * @return personalized therapy recommendations
     */
    public String getTherapyRecommendations(String clientAssessment) {
        String prompt = String.format(
            """
            Based on the following occupational therapy assessment, provide evidence-based
            therapy recommendations:

            Assessment Summary:
            %s

            Please provide:
            1. Primary intervention focus areas
            2. Specific therapeutic activities and modalities
            3. Equipment or assistive technology recommendations
            4. Progress measurement strategies
            5. Expected timeline for functional improvements
            6. Home program recommendations

            Format your response as a structured therapy plan.
            """,
            clientAssessment
        );

        return getZaiService().chat(prompt);
    }

    /**
     * Updates OT provider availability.
     *
     * @param sellerId the OT provider
     * @param availableSlots new available time slots
     */
    public void updateAvailability(String sellerId, Set<ZonedDateTime> availableSlots) {
        availability.put(sellerId, Set.copyOf(availableSlots));
        logger.info("Updated availability for OT provider {}", sellerId);
    }

    /**
     * Adds a service rating from a client.
     *
     * @param skillId the skill being rated
     * @param rating the rating (1-5)
     * @return updated average rating
     */
    public GregVerseMarketplace.SkillRating rateService(String skillId, int rating) {
        return marketplace.rateSkill(skillId, rating);
    }

    /**
     * Gets current OT marketplace listings.
     *
     * @return list of active OT service listings
     */
    public List<GregVerseMarketplace.MarketplaceEntry> getOTListings() {
        return marketplace.discoverByCategory("occupational-therapy");
    }

    // Helper methods

    private void initializeOTServices() {
        // Initialize core OT services in the marketplace
        logger.info("Initializing OT services in marketplace");
    }

    private String capitalizeSkill(String skill) {
        return Arrays.stream(skill.replace("-", " ").split(" "))
                   .map(word -> word.substring(0, 1).toUpperCase() +
                                word.substring(1).toLowerCase())
                   .collect(Collectors.joining(" "));
    }

    private String getSkillDescription(String skillId) {
        return switch(skillId) {
            case "occupational-therapy-assessment" ->
                "Comprehensive evaluation of functional abilities and daily living skills";
            case "neuro-rehabilitation" ->
                "Specialized therapy for neurological conditions including stroke, TBI, and spinal cord injury";
            case "disability-management" ->
                "Strategies for managing physical disabilities and optimizing functional independence";
            case "wellness-therapy" ->
                "Mental health and wellness-focused occupational therapy interventions";
            case "geriatric-therapy" ->
                "Age-appropriate therapy for older adults focusing on aging in place and quality of life";
            case "pediatric-therapy" ->
                "Developmental therapy for children addressing fine motor, sensory, and social skills";
            case "adaptive-equipment-consultation" ->
                "Assessment and prescription of assistive technology and adaptive equipment";
            default -> "Professional occupational therapy service";
        };
    }

    private String getServiceName(String serviceType) {
        return switch(serviceType) {
            case "assessment" -> "Occupational Therapy Assessment";
            case "intervention-planning" -> "Therapy Intervention Planning";
            case "appointment-scheduling" -> "Appointment Scheduling";
            default -> "OT Professional Service";
        };
    }

    private String getServiceDescription(String serviceType) {
        return switch(serviceType) {
            case "assessment" ->
                "Comprehensive functional assessment including ADL/IADL evaluation, " +
                "sensory processing assessment, and home safety evaluation. " +
                "Results in detailed report with functional recommendations.";
            case "intervention-planning" ->
                "Evidence-based therapy plan development based on assessment findings. " +
                "Includes specific therapeutic activities, modalities, and measurable goals.";
            case "appointment-scheduling" ->
                "Flexible scheduling of therapy sessions with consideration of client " +
                "availability, treatment frequency requirements, and insurance constraints.";
            default -> "Professional occupational therapy service";
        };
    }

    private Set<ZonedDateTime> getStandardAvailability() {
        ZonedDateTime now = ZonedDateTime.now();
        return Set.of(
            now.plusDays(1).withHour(9).withMinute(0),
            now.plusDays(1).withHour(11).withMinute(0),
            now.plusDays(1).withHour(14).withMinute(0),
            now.plusDays(1).withHour(16).withMinute(0),
            now.plusDays(2).withHour(10).withMinute(0),
            now.plusDays(2).withHour(13).withMinute(0),
            now.plusDays(2).withHour(15).withMinute(0),
            now.plusDays(3).withHour(9).withMinute(0)
        );
    }

    private String enhanceOTResponse(String response) {
        // Add OT-specific formatting and validation
        if (!response.contains("Assessment") && !response.contains("Intervention")) {
            return "Assessment Findings\n" + response + "\n\n" + "Intervention Considerations\n" +
                   "Based on the information provided, consider consulting with a qualified occupational therapist for personalized assessment and intervention planning.";
        }
        return response;
    }

    private String formatOTResponseForSkill(String skillId, String response) {
        // Apply skill-specific formatting
        switch(skillId) {
            case "neuro-rehabilitation":
                return "Neurological Assessment\n" + response +
                       "\n\nProgress Monitoring\n" +
                       "Regular assessment using standardized measures to track functional improvements.";
            case "pediatric-therapy":
                return "Developmental Assessment\n" + response +
                       "\n\nParent/Caregiver Involvement\n" +
                       "Home program activities for continued skill development.";
            default:
                return response;
        }
    }
}