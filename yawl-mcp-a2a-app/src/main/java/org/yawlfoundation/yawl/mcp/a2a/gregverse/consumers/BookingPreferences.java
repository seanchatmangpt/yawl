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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Represents patient preferences for service booking.
 *
 * @param patientId the patient ID
 * @param scheduledDate preferred date (yyyy-MM-dd format)
 * @param preferredTimeOfDay preferred time of day
 * @param durationMinutes preferred session duration
 * @param maxPrice maximum willing to pay
 * @param minRating minimum acceptable provider rating
 * @param travelDistance maximum travel distance (km)
 * @param locationPreferences location preferences
 * @param accessibilityRequirements accessibility needs
 * @param notes additional booking notes
 */
public record BookingPreferences(
    String patientId,
    String scheduledDate,
    String preferredTimeOfDay,
    Integer durationMinutes,
    BigDecimal maxPrice,
    BigDecimal minRating,
    Double travelDistance,
    String locationPreferences,
    String accessibilityRequirements,
    String notes
) {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Creates booking preferences with required fields.
     */
    public BookingPreferences {
        Objects.requireNonNull(patientId, "Patient ID is required");
        scheduledDate = scheduledDate != null ? scheduledDate : LocalDate.now().format(DATE_FORMATTER);
        durationMinutes = durationMinutes != null ? durationMinutes : 60;
    }

    /**
     * Creates booking preferences for a specific date.
     */
    public static BookingPreferences forDate(String patientId, String scheduledDate) {
        return new BookingPreferences(
            patientId,
            scheduledDate,
            null, // default time
            60,   // default duration
            null, // no price limit
            null, // no rating minimum
            null, // no distance limit
            null, // no location preference
            null, // no accessibility needs
            null  // no notes
        );
    }

    /**
     * Creates booking preferences with price constraints.
     */
    public static BookingPreferences withPriceLimit(
            String patientId, BigDecimal maxPrice, Integer durationMinutes) {
        return new BookingPreferences(
            patientId,
            null, // flexible date
            null,
            durationMinutes,
            maxPrice,
            null,
            null,
            null,
            null,
            null
        );
    }

    /**
     * Returns true if preferences include a specific date.
     */
    public boolean hasSpecificDate() {
        return scheduledDate != null && !scheduledDate.isEmpty();
    }

    /**
     * Returns true if preferences include a price limit.
     */
    public boolean hasPriceLimit() {
        return maxPrice != null;
    }

    /**
     * Returns true if preferences include rating requirements.
     */
    public boolean hasRatingRequirement() {
        return minRating != null;
    }

    /**
     * Validates that scheduled date is in the future.
     */
    public boolean isValidDate() {
        if (scheduledDate == null) return true;
        try {
            var prefDate = LocalDate.parse(scheduledDate, DATE_FORMATTER);
            var today = LocalDate.now();
            return !prefDate.isBefore(today);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Gets formatted date for display.
     */
    public String getFormattedDate() {
        if (scheduledDate == null) return "Flexible";
        try {
            var date = LocalDate.parse(scheduledDate, DATE_FORMATTER);
            return date.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"));
        } catch (Exception e) {
            return scheduledDate;
        }
    }
}