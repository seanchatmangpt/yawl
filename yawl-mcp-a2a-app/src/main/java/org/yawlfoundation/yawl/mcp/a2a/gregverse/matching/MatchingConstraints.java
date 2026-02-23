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

package org.yawlfoundation.yawl.mcp.a2a.gregverse.matching;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Represents matching constraints for the N-dimensional OT marketplace.
 *
 * <p>Constraints define the boundaries and preferences for service provider matching,
 * ensuring that the routing algorithm finds optimal matches within defined parameters.</p>
 *
 * <p><b>WCP Integration:</b> These constraints are applied through:
 * <ul>
 *   <li>WCP-4 (Exclusive Choice) - Filters providers based on hard constraints</li>
 *   <li>WCP-6 (Multi-Choice) - Selects multiple qualified providers</li>
 *   <li>WCP-21 (Deferred Choice) - Processes flexible constraints based on availability</li>
 * </ul>
 * </p>
 *
 * @param maxDistance Maximum travel distance for in-person sessions (km)
 * @param budgetRange Maximum budget range (as percentage of standard rate)
 * @param ratingMinimum Minimum required rating (0.0-5.0)
 * @param availabilityRequired Whether availability is a hard constraint
 * @param requiredLanguages Required language compatibility
 * @param requiredInsurance Required insurance providers
 * @param specializationMustMatch Whether specialization must match exactly
 * @param deliveryModePreferences Weighted preferences for delivery modes
 * @param urgencyMustRespect Whether urgency must be matched with appropriate response time
 * @param timeSlotConstraints Specific time slot requirements
 * @param maxProviders Maximum number of providers to return
 * @param allowMultiBundle Whether to allow service bundle selections
 * @param excludeUnavailable Whether to filter out unavailable providers
 */
public record MatchingConstraints(
    double maxDistance,
    double budgetRange,
    double ratingMinimum,
    boolean availabilityRequired,
    List<String> requiredLanguages,
    List<String> requiredInsurance,
    boolean specializationMustMatch,
    Map<String, Double> deliveryModePreferences,
    boolean urgencyMustRespect,
    TimeSlotConstraints timeSlotConstraints,
    int maxProviders,
    boolean allowMultiBundle,
    boolean excludeUnavailable
) {

    /**
     * Represents specific time slot constraints for scheduling.
     *
     * @param preferredDays Preferred days of the week
     * @param preferredTimes Preferred time windows
     * @param timeZone Time zone for scheduling
     * @param minDuration Minimum session duration
     * @param maxDuration Maximum session duration
     */
    public record TimeSlotConstraints(
        List<DayOfWeek> preferredDays,
        List<TimeWindow> preferredTimes,
        String timeZone,
        int minDuration,
        int maxDuration
    ) {}

    /**
     * Represents a time window constraint.
     *
     * @param startTime Start time (HH:mm format)
     * @param endTime End time (HH:mm format)
     */
    public record TimeWindow(
        String startTime,
        String endTime
    ) {
        public TimeWindow {
            if (!startTime.matches("^([01]?[0-9]|2[0-3]):[0-5][0-9]$")) {
                throw new IllegalArgumentException("Invalid start time format: " + startTime);
            }
            if (!endTime.matches("^([01]?[0-9]|2[0-3]):[0-5][0-9]$")) {
                throw new IllegalArgumentException("Invalid end time format: " + endTime);
            }
        }

        /**
         * Checks if this time window contains the given time.
         *
         * @param time Time to check (HH:mm format)
         * @return true if time is within this window
         */
        public boolean contains(String time) {
            return !(time.compareTo(startTime) < 0 || time.compareTo(endTime) > 0);
        }
    }

    /**
     * Creates default matching constraints with reasonable defaults.
     *
     * @return default constraints
     */
    public static MatchingConstraints defaults() {
        return new MatchingConstraints(
            50.0, // 50km max travel distance
            1.5,  // 50% budget flexibility
            3.5,  // Minimum 3.5 star rating
            false, // Availability not required by default
            List.of(), // No specific language requirements
            List.of(), // No specific insurance requirements
            false, // Specialization doesn't need to match exactly
            Map.of(
                "telehealth", 0.8,
                "in-person", 1.0,
                "hybrid", 0.9
            ), // Default delivery mode preferences
            true, // Must respect urgency
            new TimeSlotConstraints(
                List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                       DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                List.of(new TimeWindow("09:00", "17:00")),
                "UTC",
                30, // 30 min minimum
                120 // 2 hours maximum
            ),
            5, // Return up to 5 providers
            true, // Allow multi-bundle selection
            true // Exclude unavailable providers
        );
    }

    /**
     * Creates constraints for emergency urgent cases.
     *
     * @return emergency constraints
     */
    public static MatchingConstraints emergency() {
        return new MatchingConstraints(
            20.0, // 20km max travel distance
            1.2,  // 20% budget flexibility
            4.0,  // Minimum 4.0 star rating
            true, // Availability is critical
            List.of(), // No specific language requirements
            List.of(), // No specific insurance requirements
            true, // Specialization must match exactly
            Map.of(
                "in-person", 1.0,
                "telehealth", 0.5,
                "hybrid", 0.6
            ), // Prefer in-person for emergencies
            true, // Must respect emergency urgency
            new TimeSlotConstraints(
                List.of(DayOfWeek.values()), // Any day
                List.of(new TimeWindow("00:00", "23:59")), // Any time
                "UTC",
                15, // 15 min minimum
                90  // 1.5 hours maximum
            ),
            3, // Return up to 3 providers
            false, // No multi-bundle for emergencies
            true // Exclude unavailable providers
        );
    }

    /**
     * Creates constraints for routine telehealth appointments.
     *
     * @return routine telehealth constraints
     */
    public static MatchingConstraints routineTelehealth() {
        return new MatchingConstraints(
            Double.POSITIVE_INFINITY, // No distance constraint
            1.3,  // 30% budget flexibility
            4.0,  // Minimum 4.0 star rating
            false, // Availability not critical for routine
            List.of("english"), // Require English proficiency
            List.of(), // No specific insurance requirements
            false, // Specialization doesn't need to match exactly
            Map.of(
                "telehealth", 1.0,
                "in-person", 0.3,
                "hybrid", 0.7
            ), // Strong preference for telehealth
            false, // Urgency not critical for routine
            new TimeSlotConstraints(
                List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                       DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                List.of(new TimeWindow("08:00", "18:00")),
                "UTC",
                45, // 45 min minimum
                60  // 1 hour maximum
            ),
            10, // Return up to 10 providers
            true, // Allow multi-bundle
            false // Include potentially available providers
        );
    }

    /**
     * Validates that these constraints are internally consistent.
     *
     * @return true if valid, false otherwise
     */
    public boolean isValid() {
        if (maxDistance < 0) return false;
        if (budgetRange <= 0 || budgetRange > 3.0) return false;
        if (ratingMinimum < 0.0 || ratingMinimum > 5.0) return false;
        if (maxProviders <= 0) return false;

        if (timeSlotConstraints != null) {
            if (timeSlotConstraints.minDuration() <= 0 || timeSlotConstraints.maxDuration() <= 0) {
                return false;
            }
            if (timeSlotConstraints.minDuration() > timeSlotConstraints.maxDuration()) {
                return false;
            }
        }

        return true;
    }

    /**
     * Merges these constraints with additional constraints.
     * New constraints override existing ones.
     *
     * @param additional additional constraints to merge
     * @return merged constraints
     */
    public MatchingConstraints merge(MatchingConstraints additional) {
        if (additional == null) return this;

        double mergedMaxDistance = additional.maxDistance > 0 ? additional.maxDistance : maxDistance;
        double mergedBudgetRange = additional.budgetRange > 0 ? additional.budgetRange : budgetRange;
        double mergedRatingMinimum = additional.ratingMinimum > 0 ? additional.ratingMinimum : ratingMinimum;
        int mergedMaxProviders = additional.maxProviders > 0 ? additional.maxProviders : maxProviders;

        TimeSlotConstraints mergedTimeConstraints = timeSlotConstraints;
        if (additional.timeSlotConstraints != null) {
            mergedTimeConstraints = additional.timeSlotConstraints;
        }

        return new MatchingConstraints(
            mergedMaxDistance,
            mergedBudgetRange,
            mergedRatingMinimum,
            additional.availabilityRequired || availabilityRequired,
            additional.requiredLanguages != null ? additional.requiredLanguages : requiredLanguages,
            additional.requiredInsurance != null ? additional.requiredInsurance : requiredInsurance,
            additional.specializationMustMatch || specializationMustMatch,
            additional.deliveryModePreferences != null ? additional.deliveryModePreferences : deliveryModePreferences,
            additional.urgencyMustRespect || urgencyMustRespect,
            mergedTimeConstraints,
            mergedMaxProviders,
            additional.allowMultiBundle || allowMultiBundle,
            additional.excludeUnavailable || excludeUnavailable
        );
    }
}