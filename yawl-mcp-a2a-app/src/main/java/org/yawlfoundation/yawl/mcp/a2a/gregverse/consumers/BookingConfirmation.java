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

import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Represents confirmation of a service booking.
 *
 * @param sessionId the unique session ID
 * @param patientId the patient ID
 * @param providerId the provider ID
 * @param serviceId the service being booked
 * @param scheduledDate the scheduled date
 * @param durationMinutes session duration
 * @param taskResult the A2A task result
 * @param bookingTimestamp when the booking was confirmed
 */
public record BookingConfirmation(
    String sessionId,
    String patientId,
    String providerId,
    String serviceId,
    String scheduledDate,
    int durationMinutes,
    TaskResult taskResult,
    Instant bookingTimestamp
) {

    /**
     * Creates a booking confirmation.
     */
    public BookingConfirmation {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Session ID is required");
        }
        if (patientId == null || patientId.isBlank()) {
            throw new IllegalArgumentException("Patient ID is required");
        }
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("Provider ID is required");
        }
    }

    /**
     * Gets formatted booking date for display.
     */
    public String getFormattedDate() {
        try {
            var date = java.time.LocalDate.parse(scheduledDate);
            return date.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"));
        } catch (Exception e) {
            return scheduledDate;
        }
    }

    /**
     * Gets formatted booking time for display.
     */
    public String getFormattedTime() {
        // In a real implementation, this would extract and format time from scheduledDate
        return "Morning"; // Default for now
    }

    /**
     * Gets the status of the booking.
     */
    public String getStatus() {
        return taskResult != null ? taskResult.status() : "CONFIRMED";
    }

    /**
     * Gets booking reference number.
     */
    public String getBookingReference() {
        return "BOOK-" + sessionId.toUpperCase();
    }

    /**
     * Returns true if the booking is confirmed.
     */
    public boolean isConfirmed() {
        return "CONFIRMED".equals(getStatus()) || "SENT".equals(getStatus());
    }

    /**
     * Returns true if the booking is pending.
     */
    public boolean isPending() {
        return "PENDING".equals(getStatus());
    }

    /**
     * Returns true if the booking failed.
     */
    public boolean isFailed() {
        return "FAILED".equals(getStatus());
    }
}