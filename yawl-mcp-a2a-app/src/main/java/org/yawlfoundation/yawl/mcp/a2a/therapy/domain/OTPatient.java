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

package org.yawlfoundation.yawl.mcp.a2a.therapy.domain;

/**
 * Represents a patient in the occupational therapy lifestyle redesign programme.
 *
 * <p>The COPM (Canadian Occupational Performance Measure) framework identifies
 * three occupational performance areas: self-care, productivity, and leisure.</p>
 *
 * @param id unique patient identifier (UUID format)
 * @param name full legal name
 * @param age chronological age in years
 * @param condition primary diagnostic condition (e.g., "stroke", "depression", "chronic pain")
 * @param referralReason reason for occupational therapy referral
 * @param functionalGoal patient-stated primary goal in their own words
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record OTPatient(
    String id,
    String name,
    int age,
    String condition,
    String referralReason,
    String functionalGoal
) {
    /** Canonical constructor with validation. */
    public OTPatient {
        // validate all required fields
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Patient id required");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Patient name required");
        if (age < 0 || age > 150) throw new IllegalArgumentException("Invalid age: " + age);
        if (condition == null || condition.isBlank()) throw new IllegalArgumentException("Condition required");
        if (referralReason == null || referralReason.isBlank()) throw new IllegalArgumentException("Referral reason required");
        if (functionalGoal == null || functionalGoal.isBlank()) throw new IllegalArgumentException("Functional goal required");
    }

    /** Returns true if patient is in paediatric range (< 18). */
    public boolean isPaediatric() {
        return age < 18;
    }

    /** Returns true if patient is in geriatric range (>= 65). */
    public boolean isGeriatric() {
        return age >= 65;
    }
}
