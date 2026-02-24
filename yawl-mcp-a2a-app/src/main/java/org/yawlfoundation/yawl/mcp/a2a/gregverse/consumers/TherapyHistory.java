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

import org.yawlfoundation.yawl.mcp.a2a.therapy.domain.OTPatient;
import org.yawlfoundation.yawl.mcp.a2a.therapy.domain.TherapySession;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents complete therapy history for a patient.
 *
 * @param patientId the patient ID
 * @param patientProfile the patient profile information
 * @param sessions list of therapy sessions
 * @param ratings list of provider ratings given by this patient
 * @param searchHistory list of service searches performed
 * @param historyTimestamp when this history was generated
 */
public record TherapyHistory(
    String patientId,
    OTPatient patientProfile,
    List<TherapySession> sessions,
    List<ServiceProviderRating> ratings,
    List<ServiceProviderSearchResult> searchHistory,
    Instant historyTimestamp
) {

    /**
     * Creates a therapy history.
     */
    public TherapyHistory {
        sessions = List.copyOf(sessions);
        ratings = List.copyOf(ratings);
        searchHistory = List.copyOf(searchHistory);
    }

    /**
     * Gets the total number of therapy sessions.
     */
    public int getTotalSessions() {
        return sessions.size();
    }

    /**
     * Gets the total number of ratings given.
     */
    public int getTotalRatings() {
        return ratings.size();
    }

    /**
     * Gets the total number of service searches performed.
     */
    public int getTotalSearches() {
        return searchHistory.size();
    }

    /**
     * Gets completed sessions only.
     */
    public List<TherapySession> getCompletedSessions() {
        return sessions.stream()
            .filter(TherapySession::isCompleted)
            .collect(Collectors.toList());
    }

    /**
     * Gets sessions in progress only.
     */
    public List<TherapySession> getInProgressSessions() {
        return sessions.stream()
            .filter(s -> !s.isCompleted())
            .collect(Collectors.toList());
    }

    /**
     * Gets average rating given by this patient.
     */
    public double getAverageRatingGiven() {
        if (ratings.isEmpty()) return 0.0;

        return ratings.stream()
            .mapToDouble(r -> r.averageRating().doubleValue())
            .average()
            .orElse(0.0);
    }

    /**
     * Gets highest rating given by this patient.
     */
    public double getHighestRatingGiven() {
        if (ratings.isEmpty()) return 0.0;

        return ratings.stream()
            .mapToDouble(r -> r.averageRating().doubleValue())
            .max()
            .orElse(0.0);
    }

    /**
     * Gets lowest rating given by this patient.
     */
    public double getLowestRatingGiven() {
        if (ratings.isEmpty()) return 0.0;

        return ratings.stream()
            .mapToDouble(r -> r.averageRating().doubleValue())
            .min()
            .orElse(0.0);
    }

    /**
     * Gets most common service type in therapy history.
     */
    public String getMostCommonServiceType() {
        if (sessions.isEmpty()) return "No services yet";

        return sessions.stream()
            .collect(Collectors.groupingBy(
                TherapySession::interventionType,
                Collectors.counting()
            ))
            .entrySet()
            .stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("No services");
    }

    /**
     * Gets total time spent in therapy sessions.
     */
    public int getTotalTherapyTimeMinutes() {
        return sessions.stream()
            .mapToInt(TherapySession::durationMinutes)
            .sum();
    }

    /**
     * Gets total spending on therapy services.
     */
    public int getTotalSpending() {
        // In a real implementation, this would track actual payments
        return getCompletedSessions().size() * 200; // Assuming $200 per session
    }

    /**
     * Gets patient engagement level.
     */
    public String getEngagementLevel() {
        if (getTotalSessions() == 0) return "New patient";
        if (getTotalSessions() < 5) return "Casual user";
        if (getTotalSessions() < 15) return "Regular patient";
        return "Active participant";
    }

    /**
     * Gets patient loyalty score (0-100).
     */
    public int getLoyaltyScore() {
        var score = 0;

        // Session count (max 30 points)
        score += Math.min(30, getTotalSessions() * 2);

        // Rating consistency (max 20 points)
        var avgRating = getAverageRatingGiven();
        score += Math.min(20, avgRating * 4);

        // Session completion rate (max 25 points)
        var completionRate = getTotalSessions() > 0 ?
            (double) getCompletedSessions().size() / getTotalSessions() : 0;
        score += (int) (completionRate * 25);

        // Activity recency (max 25 points)
        var daysSinceLastActivity = ChronoUnit.DAYS.between(
            getLastActivityDate(),
            Instant.now()
        );
        score += Math.max(0, 25 - (int) (daysSinceLastActivity / 7));

        return Math.min(100, score);
    }

    /**
     * Gets date of last activity.
     */
    public Instant getLastActivityDate() {
        Instant latest = historyTimestamp;

        // Check latest session
        var latestSession = sessions.stream()
            .max(Comparator.comparing(TherapySession::scheduledDate))
            .map(s -> Instant.parse(s.scheduledDate() + "T00:00:00Z"));

        if (latestSession.isPresent() && latestSession.get().isAfter(latest)) {
            latest = latestSession.get();
        }

        // Check latest rating
        var latestRating = ratings.stream()
            .map(r -> r.individualRatings().stream()
                .max(Comparator.comparing(ServiceProviderRating.IndividualRating::timestamp)))
            .flatMap(Optional::stream)
            .map(ServiceProviderRating.IndividualRating::timestamp)
            .max(Comparator.naturalOrder());

        if (latestRating.isPresent() && latestRating.get().isAfter(latest)) {
            latest = latestRating.get();
        }

        return latest;
    }

    /**
     * Gets improvement recommendations based on history.
     */
    public List<String> getImprovementRecommendations() {
        var recommendations = new ArrayList<String>();

        if (getTotalSessions() < 3) {
            recommendations.add("Consider more frequent therapy sessions for better progress");
        }

        if (getAverageRatingGiven() < 3.5) {
            recommendations.add("Explore different providers for better service quality");
        }

        var incompleteSessions = getInProgressSessions().size();
        if (incompleteSessions > 0) {
            recommendations.add("Complete " + incompleteSessions + " pending sessions");
        }

        if (getTotalSearches() > 10 && getTotalSessions() < 5) {
            recommendations.add("Consider committing to fewer providers for better continuity of care");
        }

        return recommendations;
    }
}