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

package org.yawlfoundation.yawl.mcp.a2a.gregverse.verification;

/**
 * Enumeration of supported certification types for occupational therapists.
 *
 * <p>This enum defines the types of professional certifications that OTs must
 * obtain and maintain to offer services in the GregVerse marketplace.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public enum CertificationType {
    /**
     * National Board for Certification in Occupational Therapy (NBCOT) certification.
     * Required for all OTs practicing in the United States.
     */
    NBCOT("NBCOT", "National Board Certification in Occupational Therapy", true),

    /**
     * State occupational therapy license. Required to practice in specific states.
     */
    STATE_LICENSE("STATE_LICENSE", "State Occupational Therapy License", true),

    /**
     * Specialty certification for advanced practice areas.
     * Examples: Hand Therapy, Pediatrics, Mental Health, etc.
     */
    SPECIALTY_CERT("SPECIALTY_CERT", "Specialty Certification", false),

    /**
     * Advanced practice certification requiring additional training and experience.
     */
    ADVANCED_CERT("ADVANCED_CERT", "Advanced Practice Certification", false);

    private final String code;
    private final String description;
    private final boolean required;

    CertificationType(String code, String description, boolean required) {
        this.code = code;
        this.description = description;
        this.required = required;
    }

    /**
     * Returns the code for this certification type.
     *
     * @return the certification code
     */
    public String getCode() {
        return code;
    }

    /**
     * Returns the description of this certification type.
     *
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns whether this certification type is required.
     *
     * @return true if required, false if optional
     */
    public boolean isRequired() {
        return required;
    }

    /**
     * Returns the certification type from its code.
     *
     * @param code the certification code
     * @return the matching certification type
     * @throws IllegalArgumentException if no matching certification type is found
     */
    public static CertificationType fromCode(String code) {
        for (CertificationType type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("No certification type found for code: " + code);
    }
}