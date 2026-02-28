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

package org.yawlfoundation.yawl.integration.selfcare;

/**
 * Occupational Therapy performance areas per the AOTA Occupational Therapy Practice Framework.
 *
 * <p>OT practitioners organise meaningful activities (occupations) into three performance areas.
 * This enum maps those areas to self-care workflow categories searchable in Gregverse.</p>
 *
 * <ul>
 *   <li>{@link #SELF_CARE} — Activities of Daily Living (ADLs) and Instrumental ADLs:
 *       bathing, dressing, eating, grooming, hygiene, mobility, medication management</li>
 *   <li>{@link #PRODUCTIVITY} — IADLs related to work, education and caregiving:
 *       household management, meal preparation, financial management, community participation</li>
 *   <li>{@link #LEISURE} — Rest, play, social participation, recreation, creative expression</li>
 * </ul>
 *
 * @since YAWL 6.0
 */
public enum OTDomain {

    /**
     * Self-care: Activities of Daily Living (ADLs) and Instrumental ADLs.
     * Examples: bathing, dressing, eating, hygiene, mobility, medication management.
     */
    SELF_CARE("self-care", "Activities of Daily Living and Instrumental ADLs"),

    /**
     * Productivity: Work, education, caregiving, and household management.
     * Examples: meal preparation, financial management, community participation.
     */
    PRODUCTIVITY("productivity", "Work, education, caregiving, and household management"),

    /**
     * Leisure: Rest, play, social participation, and recreation.
     * Examples: creative activities, social engagement, physical recreation.
     */
    LEISURE("leisure", "Rest, play, social participation, and recreation");

    private final String sparqlValue;
    private final String description;

    OTDomain(String sparqlValue, String description) {
        this.sparqlValue = sparqlValue;
        this.description = description;
    }

    /**
     * Returns the lowercase string value used in Gregverse SPARQL queries.
     */
    public String sparqlValue() {
        return sparqlValue;
    }

    /**
     * Returns a human-readable description of this OT performance area.
     */
    public String description() {
        return description;
    }

    /**
     * Parses a domain string (case-insensitive) to an {@link OTDomain}.
     *
     * @param value the domain string (e.g. "self-care", "SELF_CARE", "selfcare")
     * @return matching OTDomain
     * @throws IllegalArgumentException if no match found
     */
    public static OTDomain fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("OTDomain value must not be null or blank");
        }
        String normalised = value.strip().toLowerCase().replace('_', '-').replace(' ', '-');
        for (OTDomain domain : values()) {
            if (domain.sparqlValue.equals(normalised) || domain.name().toLowerCase().replace('_', '-').equals(normalised)) {
                return domain;
            }
        }
        throw new IllegalArgumentException(
            "Unknown OTDomain: '" + value + "'. Valid values: self-care, productivity, leisure");
    }
}
