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

package org.yawlfoundation.yawl.mcp.a2a.gregverse.consumers;

import io.a2a.A2A;
import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentSkill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.AbstractGregVerseAgent;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.simulation.GregVerseMarketplace;
import org.yawlfoundation.yawl.mcp.a2a.therapy.domain.OTPatient;
import org.yawlfoundation.yawl.mcp.a2a.therapy.domain.OccupationalProfile;
import org.yawlfoundation.yawl.mcp.a2a.therapy.domain.TherapySession;
import org.yawlfoundation.yawl.mcp.a2a.a2a.YawlA2AExecutor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.AbstractMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.Comparator;

/**
 * Patient Consumer Agent for the GregVerse marketplace.
 *
 * <p>Enables patients to discover, compare, book, and receive occupational therapy services
 * through an intelligent marketplace interface. The agent manages the full patient journey
 * from service discovery to therapy completion and rating.</p>
 *
 * <h2>Core Capabilities</h2>
 * <ul>
 *   <li><strong>Marketplace Discovery</strong> - Search OT services by specialization</li>
 *   <li><strong>Provider Comparison</strong> - Compare by rating, price, availability</li>
 *   <li><strong>Service Booking</strong> - Book services via A2A TaskSend</li>
 *   <li><strong>Session Management</strong> - Monitor and receive therapy artifacts</li>
 *   <li><strong>Provider Rating</strong> - Rate providers after service delivery</li>
 *   <li><strong>Therapy History</strong> - Complete session and provider history</li>
 * </ul>
 *
 * <h2>Workflow</h2>
 * <ol>
 *   <li>Patient query with profile and needs</li>
 *   <li>Search marketplace for matching OT services</li>
 *   <li>Compare providers using N-dimensional matching</li>
 *   <li>Send task request with patient profile</li>
 *   <li>Monitor task status and receive artifacts</li>
 *   <li>Rate provider and update history</li>
 * </ol>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@Component
public class PatientConsumerAgent extends AbstractGregVerseAgent {

    private static final Logger logger = LoggerFactory.getLogger(PatientConsumerAgent.class);

    private final GregVerseMarketplace marketplace;
    private final YawlA2AExecutor yawlExecutor;
    private final VirtualThreadTaskExecutor virtualThreadExecutor;

    // Patient data store
    private final Map<String, OTPatient> patientProfiles = new ConcurrentHashMap<>();
    private final Map<String, ServiceProviderRating> providerRatings = new ConcurrentHashMap<>();
    private final Map<String, TherapySession> activeSessions = new ConcurrentHashMap<>();
    private final Map<String, ServiceProviderSearchResult> searchHistory = new ConcurrentHashMap<>();

    // Market configuration
    private static final List<String> OT_SPECIALIZATIONS = List.of(
        "adl-training", "cognitive-retraining", "motor-rehabilitation",
        "paediatric-ot", "geriatric-ot", "mental-health-ot",
        "neuro-rehabilitation", "hand-therapy", "workplace-assessment"
    );

    /**
     * Creates a new Patient Consumer Agent.
     *
     * @param marketplace the GregVerse marketplace instance
     * @param yawlExecutor the A2A task executor
     * @param virtualThreadExecutor virtual thread executor for async operations
     */
    public PatientConsumerAgent(
            GregVerseMarketplace marketplace,
            YawlA2AExecutor yawlExecutor,
            VirtualThreadTaskExecutor virtualThreadExecutor) {
        this.marketplace = marketplace;
        this.yawlExecutor = yawlExecutor;
        this.virtualThreadExecutor = virtualThreadExecutor;
        logger.info("Initialized PatientConsumerAgent with marketplace and A2A integration");
    }

    @Override
    public String getAgentId() {
        return "patient-consumer";
    }

    @Override
    public String getDisplayName() {
        return "Patient Service Consumer";
    }

    @Override
    public String getBio() {
        return "Intelligent marketplace interface for patients seeking occupational therapy services. " +
               "Discover, compare, book, and receive therapy services through an intuitive platform.";
    }

    @Override
    public List<String> getSpecializedSkills() {
        return List.of("service-discovery", "provider-comparison", "service-booking",
                      "session-management", "provider-rating", "therapy-history");
    }

    @Override
    public String getSystemPrompt() {
        return """
            You are Patient Consumer Agent, an intelligent marketplace interface for occupational therapy services.
            Your role is to help patients discover, compare, book, and receive therapy services.

            Core responsibilities:
            1. Service Discovery - Search marketplace for OT services matching patient needs
            2. Provider Comparison - Compare providers by rating, price, availability, specialization
            3. Service Booking - Book services through A2A protocol
            4. Session Management - Monitor therapy sessions and receive artifacts
            5. Provider Rating - Rate providers after service completion
            6. Therapy History - Maintain complete patient service history

            Communication style:
            - Empathetic and patient-focused
            - Clear explanations of service options
            - Transparent about pricing and availability
            - Proactive about session status updates
            - Encouraging of patient feedback

            Always prioritize patient needs and provide personalized recommendations.
            """;
    }

    @Override
    public String getCommunicationStyle() {
        return "Empathetic, clear, and patient-focused with transparent service information";
    }

    @Override
    public List<String> getExpertise() {
        return List.of("patient-centric healthcare", "service marketplace operations",
                      "occupational therapy services", "provider rating systems",
                      "patient journey optimization");
    }

    @Override
    public List<AgentSkill> createAgentSkills() {
        return List.of(
            AgentSkill.builder()
                .id("service-discovery")
                .name("Service Discovery")
                .description("Search and discover occupational therapy services by specialization")
                .tags(List.of("discovery", "search", "ot-services", "specialization"))
                .inputModes(List.of("text"))
                .outputModes(List.of("text", "json"))
                .build(),
            AgentSkill.builder()
                .id("provider-comparison")
                .name("Provider Comparison")
                .description("Compare providers using multi-criteria analysis including rating, price, availability")
                .tags(List.of("comparison", "evaluation", "provider-selection", "multi-criteria"))
                .inputModes(List.of("text"))
                .outputModes(List.of("text", "json"))
                .build(),
            AgentSkill.builder()
                .id("service-booking")
                .name("Service Booking")
                .description("Book occupational therapy services through A2A protocol")
                .tags(List.of("booking", "reservation", "a2a-protocol", "service-acquisition"))
                .inputModes(List.of("text"))
                .outputModes(List.of("text", "json"))
                .build(),
            AgentSkill.builder()
                .id("session-management")
                .name("Session Management")
                .description("Monitor therapy sessions and receive artifacts")
                .tags(List.of("monitoring", "tracking", "artifacts", "therapy-sessions"))
                .inputModes(List.of("text"))
                .outputModes(List.of("text", "json"))
                .build(),
            AgentSkill.builder()
                .id("provider-rating")
                .name("Provider Rating")
                .description("Rate providers after service completion with feedback")
                .tags(List.of("rating", "feedback", "review", "provider-evaluation"))
                .inputModes(List.of("text"))
                .outputModes(List.of("text", "json"))
                .build(),
            AgentSkill.builder()
                .id("therapy-history")
                .name("Therapy History")
                .description("Access and analyze complete patient service history")
                .tags(List.of("history", "analytics", "patient-journey", "service-tracking"))
                .inputModes(List.of("text"))
                .outputModes(List.of("text", "json"))
                .build()
        );
    }

    @Override
    public String getResponseFormat() {
        return "Structured responses with clear options, personalized recommendations, and action items";
    }

    /**
     * Register a patient profile for service discovery and booking.
     *
     * @param patient the patient profile to register
     * @return patient ID for future reference
     */
    public String registerPatientProfile(OTPatient patient) {
        String patientId = patient.id();
        patientProfiles.put(patientId, patient);

        logger.info("Registered patient profile: {} - {} ({} years, {})",
            patientId, patient.name(), patient.age(), patient.condition());

        return patientId;
    }

    /**
     * Discover occupational therapy services by specialization.
     *
     * @param specialization the OT specialization to search for
     * @param maxResults maximum number of results to return
     * @return list of matching service providers
     */
    public CompletableFuture<List<ServiceProviderSearchResult>> discoverServices(
            String specialization, int maxResults) {

        return CompletableFuture.supplyAsync(() -> {
            if (!OT_SPECIALIZATIONS.contains(specialization)) {
                throw new IllegalArgumentException("Unknown OT specialization: " + specialization);
            }

            logger.info("Discovering {} services for max {} results", specialization, maxResults);

            // Search marketplace for services
            var marketResults = marketplace.discoverSkills(specialization);

            // Filter and rank providers
            return marketResults.stream()
                .limit(maxResults)
                .map(entry -> ServiceProviderSearchResult.fromMarketplaceEntry(entry, marketplace))
                .sorted(Comparator.comparingInt((ServiceProviderSearchResult r) -> r.relevanceScore()).reversed())
                .collect(Collectors.toList());
        }, virtualThreadExecutor);
    }

    /**
     * Compare providers using N-dimensional matching algorithm.
     *
     * @param patient the patient with needs
     * @param providerResults list of providers to compare
     * @return ranked comparison with detailed scores
     */
    public CompletableFuture<ProviderComparisonResult> compareProviders(
            OTPatient patient, List<ServiceProviderSearchResult> providerResults) {

        return CompletableFuture.supplyAsync(() -> {
            logger.info("Comparing {} providers for patient {}", providerResults.size(), patient.id());

            var comparisonScores = new ArrayList<ProviderScore>();

            for (var provider : providerResults) {
                var score = calculateProviderScore(patient, provider);
                comparisonScores.add(score);
            }

            // Sort by overall score
            comparisonScores.sort(Comparator.comparingInt(ProviderScore::overallScore).reversed());

            return new ProviderComparisonResult(
                patient.id(),
                comparisonScores,
                calculateBestMatch(patient, comparisonScores),
                Instant.now()
            );
        }, virtualThreadExecutor);
    }

    /**
     * Book a service with the selected provider.
     *
     * @param patientId the patient ID
     * @param providerId the selected provider ID
     * @param serviceId the service to book
     * @param preferences booking preferences (date, time, etc.)
     * @return booking confirmation with session details
     */
    public CompletableFuture<BookingConfirmation> bookService(
            String patientId, String providerId, String serviceId, BookingPreferences preferences) {

        return CompletableFuture.supplyAsync(() -> {
            var patient = patientProfiles.get(patientId);
            if (patient == null) {
                throw new IllegalArgumentException("Patient not found: " + patientId);
            }

            logger.info("Booking service {} for patient {} with provider {}",
                serviceId, patientId, providerId);

            // Validate availability and create booking
            var session = createTherapySession(patientId, providerId, serviceId, preferences);
            activeSessions.put(session.id(), session);

            // Send task via A2A protocol
            var taskResult = sendBookingTask(patient, providerId, serviceId, session, preferences);

            return new BookingConfirmation(
                session.id(),
                patientId,
                providerId,
                serviceId,
                session.scheduledDate(),
                session.durationMinutes(),
                taskResult,
                Instant.now()
            );
        }, virtualThreadExecutor);
    }

    /**
     * Monitor therapy session status.
     *
     * @param sessionId the session to monitor
     * @return current session status and artifacts
     */
    public CompletableFuture<SessionStatus> monitorSession(String sessionId) {
        return CompletableFuture.supplyAsync(() -> {
            var session = activeSessions.get(sessionId);
            if (session == null) {
                throw new IllegalArgumentException("Session not found: " + sessionId);
            }

            logger.debug("Monitoring session status: {}", sessionId);

            // Check if session is complete and update status
            if (session.isCompleted()) {
                // Generate summary artifacts
                var artifacts = generateSessionArtifacts(session);
                return new SessionStatus(sessionId, session.status(), artifacts, Instant.now());
            }

            return new SessionStatus(sessionId, session.status(), List.of(), Instant.now());
        }, virtualThreadExecutor);
    }

    /**
     * Rate a provider after service completion.
     *
     * @param patientId the patient providing the rating
     * @param providerId the provider to rate
     * @param serviceId the service that was delivered
     * @param rating the rating (1-5)
     * @param feedback additional feedback
     * @return updated provider rating
     */
    public CompletableFuture<ProviderRatingResult> rateProvider(
            String patientId, String providerId, String serviceId, int rating, String feedback) {

        return CompletableFuture.supplyAsync(() -> {
            if (rating < 1 || rating > 5) {
                throw new IllegalArgumentException("Rating must be 1-5");
            }

            logger.info("Patient {} rating provider {} for service {}: {} stars",
                patientId, providerId, serviceId, rating);

            // Update provider rating in marketplace
            marketplace.rateSkill(serviceId, rating);

            // Store patient-specific rating
            var ratingKey = patientId + "-" + providerId + "-" + serviceId;
            var currentRating = providerRatings.getOrDefault(ratingKey,
                new ServiceProviderRating(patientId, providerId, serviceId, 0, BigDecimal.ZERO, new ArrayList<>()));

            var updatedRating = currentRating.addRating(rating, feedback);
            providerRatings.put(ratingKey, updatedRating);

            return new ProviderRatingResult(
                patientId,
                providerId,
                serviceId,
                rating,
                feedback,
                updatedRating,
                Instant.now()
            );
        }, virtualThreadExecutor);
    }

    /**
     * Get patient therapy history.
     *
     * @param patientId the patient ID
     * @return complete therapy history
     */
    public CompletableFuture<TherapyHistory> getTherapyHistory(String patientId) {
        return CompletableFuture.supplyAsync(() -> {
            var patient = patientProfiles.get(patientId);
            if (patient == null) {
                throw new IllegalArgumentException("Patient not found: " + patientId);
            }

            // Get all sessions for this patient
            var patientSessions = activeSessions.values().stream()
                .filter(s -> s.patientId().equals(patientId))
                .collect(Collectors.toList());

            // Get all ratings by this patient
            var patientRatings = providerRatings.values().stream()
                .filter(r -> r.patientId().equals(patientId))
                .collect(Collectors.toList());

            // Get search history
            var searches = searchHistory.values().stream()
                .filter(s -> s.entryId().contains(patientId))
                .collect(Collectors.toList());

            return new TherapyHistory(
                patientId,
                patient,
                patientSessions,
                patientRatings,
                searches,
                Instant.now()
            );
        }, virtualThreadExecutor);
    }

    /**
     * Search for services based on patient profile and preferences.
     *
     * @param patient the patient with needs
     * @param preferences search criteria
     * @return smart search results
     */
    public CompletableFuture<SmartSearchResult> smartSearch(OTPatient patient, SearchPreferences preferences) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Performing smart search for patient {} with preferences {}",
                patient.id(), preferences);

            // Find relevant specializations based on patient condition
            var relevantSpecializations = findRelevantSpecializations(patient.condition());

            // Search across all relevant specializations
            var allResults = new ArrayList<ServiceProviderSearchResult>();

            for (var spec : relevantSpecializations) {
                var specResults = marketplace.discoverSkills(spec);
                allResults.addAll(specResults.stream()
                    .map(entry -> ServiceProviderSearchResult.fromMarketplaceEntry(entry, marketplace))
                    .collect(Collectors.toList()));
            }

            // Filter and rank based on preferences
            var scoredResults = allResults.stream()
                .filter(result -> matchesPreferences(result, preferences))
                .map(result -> {
                    ProviderScore score = calculateProviderScore(patient, result);
                    return new AbstractMap.SimpleEntry<>(result, score);
                })
                .sorted(Map.Entry.comparingByValue(Comparator.comparingInt(ProviderScore::overallScore).reversed()))
                .limit(preferences.maxResults())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

            // Generate personalized recommendations
            var recommendations = generatePersonalizedRecommendations(patient, scoredResults);

            return new SmartSearchResult(
                patient.id(),
                scoredResults,
                recommendations,
                relevantSpecializations,
                Instant.now()
            );
        }, virtualThreadExecutor);
    }

    // Helper methods for calculations

    private ProviderScore calculateProviderScore(OTPatient patient, ServiceProviderSearchResult provider) {
        var ageMatchScore = 0;
        if (patient.isPaediatric() && provider.hasSpecialization("paediatric-ot")) {
            ageMatchScore = 30;
        } else if (patient.isGeriatric() && provider.hasSpecialization("geriatric-ot")) {
            ageMatchScore = 30;
        }

        var conditionScore = provider.treatsCondition(patient.condition()) ? 25 : 0;

        var ratingScore = provider.averageRating().multiply(BigDecimal.valueOf(20)).intValue();

        var maxPrice = new BigDecimal(500);
        var priceScore = maxPrice.subtract(provider.priceInCredits())
            .divide(maxPrice, 2, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(15))
            .intValue();

        var availabilityScore = provider.hasAvailability() ? 10 : 0;

        var overallScore = ageMatchScore + conditionScore + ratingScore + priceScore + availabilityScore;

        var detailedScores = new HashMap<String, Integer>();
        detailedScores.put("ageMatch", ageMatchScore);
        detailedScores.put("condition", conditionScore);
        detailedScores.put("rating", ratingScore);
        detailedScores.put("price", priceScore);
        detailedScores.put("availability", availabilityScore);

        return new ProviderScore(
            provider.providerId(),
            overallScore,
            ageMatchScore,
            conditionScore,
            ratingScore,
            priceScore,
            availabilityScore,
            0, // specialtyScore
            detailedScores
        );
    }

    private ProviderScore calculateBestMatch(OTPatient patient, List<ProviderScore> scores) {
        return scores.isEmpty() ? null : scores.get(0);
    }

    private TherapySession createTherapySession(
            String patientId, String providerId, String serviceId, BookingPreferences preferences) {

        var sessionId = "session-" + UUID.randomUUID().toString().substring(0, 8);
        var scheduledDate = preferences.scheduledDate() != null ?
            preferences.scheduledDate() : Instant.now().toString().substring(0, 10);

        return new TherapySession(
            sessionId,
            patientId,
            serviceId,
            scheduledDate,
            preferences.durationMinutes(),
            TherapySession.STATUS_SCHEDULED,
            0.0
        );
    }

    private TaskResult sendBookingTask(
            OTPatient patient, String providerId, String serviceId,
            TherapySession session, BookingPreferences preferences) {

        // Simulate A2A task sending
        // In real implementation, this would use the yawlExecutor to send tasks
        logger.info("Sending booking task to provider {} for service {}", providerId, serviceId);

        return new TaskResult(
            "task-" + UUID.randomUUID().toString().substring(0, 8),
            "service-booking",
            "SENT",
            "Booking request sent to provider",
            Instant.now()
        );
    }

    private List<String> findRelevantSpecializations(String condition) {
        return switch (condition.toLowerCase()) {
            case "stroke", "brain injury" -> List.of("neuro-rehabilitation", "cognitive-retraining", "adl-training");
            case "depression", "anxiety" -> List.of("mental-health-ot", "cognitive-retraining");
            case "arthritis", "chronic pain" -> List.of("adl-training", "motor-rehabilitation", "hand-therapy");
            case "fracture", "post-surgical" -> List.of("motor-rehabilitation", "adl-training");
            default -> List.of("cognitive-retraining", "adl-training", "motor-rehabilitation");
        };
    }

    private boolean matchesPreferences(ServiceProviderSearchResult provider, SearchPreferences preferences) {
        if (preferences.maxPrice() != null &&
            provider.priceInCredits().compareTo(preferences.maxPrice()) > 0) {
            return false;
        }

        if (preferences.minRating() != null &&
            provider.averageRating().compareTo(preferences.minRating()) < 0) {
            return false;
        }

        return true;
    }

    private List<String> generatePersonalizedRecommendations(OTPatient patient, List<ServiceProviderSearchResult> results) {
        var recommendations = new ArrayList<String>();

        if (results.isEmpty()) {
            recommendations.add("No suitable providers found for your specific needs");
            return recommendations;
        }

        var topProvider = results.get(0);
        recommendations.add(String.format(
            "Top recommendation: %s (Rating: %.1f, Price: %d credits)",
            topProvider.providerName(),
            topProvider.averageRating(),
            topProvider.priceInCredits()
        ));

        if (patient.isPaediatric()) {
            recommendations.add("Consider providers with paediatric specialization for age-appropriate care");
        }

        if (patient.isGeriatric()) {
            recommendations.add("Look for geriatric specialists with experience in chronic conditions");
        }

        return recommendations;
    }

    private List<String> generateSessionArtifacts(TherapySession session) {
        return List.of(
            "Session completed successfully",
            "Progress score: " + session.progressScore(),
            "Duration: " + session.durationMinutes() + " minutes",
            "Intervention type: " + session.interventionType()
        );
    }
}