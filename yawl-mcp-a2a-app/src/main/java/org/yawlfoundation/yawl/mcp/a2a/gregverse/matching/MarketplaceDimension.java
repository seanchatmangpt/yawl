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

/**
 * Enumeration defining the N-dimensional marketplace topology for autonomous OT service delivery.
 *
 * <p>The GregVerse marketplace operates in 6 primary dimensions that enable optimal
 * matching between OT providers and patients:</p>
 *
 * <ul>
 *   <li>Dimension 1: Service Type (assessment, intervention, scheduling)</li>
 *   <li>Dimension 2: Specialization (pediatric, geriatric, mental health, physical rehab)</li>
 *   <li>Dimension 3: Delivery Mode (telehealth, in-person, hybrid)</li>
 *   <li>Dimension 4: Urgency (routine, urgent, emergency)</li>
 *   <li>Dimension 5: Price Range (budget, standard, premium)</li>
 *   <li>Dimension 6: Rating Threshold (3+, 4+, 4.5+)</li>
 * </ul>
 *
 * <p>Each dimension is optimized through WCP workflow patterns for intelligent routing,
 * preference handling, and bundle selection.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public enum MarketplaceDimension {

    /**
     * Service type dimension representing the core occupational therapy services available.
     *
     * <p><b>Workflow Pattern Integration:</b> WCP-4 (Exclusive Choice) for routing based on service type.
     * The system determines which service type best matches the patient's needs through
     * comprehensive assessment.</p>
     *
     * <p><b>Valid Values:</b>
     *   <ul>
     *     <li>assessment - Initial evaluation and goal-setting phase</li>
     *     <li>intervention - Treatment implementation and skill-building</li>
     *     <li>scheduling - Appointment coordination and resource allocation</li>
     *   </ul>
     * </p>
     */
    SERVICE_TYPE("serviceType", "Service Type",
        new String[]{"assessment", "intervention", "scheduling"}),

    /**
     * Specialization dimension representing the clinical expertise areas of OT providers.
     *
     * <p><b>Workflow Pattern Integration:</b> WCP-4 (Exclusive Choice) for specialization routing.
     * Based on patient demographics and condition, routes to appropriate specialists.</p>
     *
     * <p><b>Valid Values:</b>
     *   <ul>
     *     <li>pediatric - Children and adolescents (0-17 years)</li>
     *     <li>geriatric - Older adults (65+ years)</li>
     *     <li>mental health - Psychological and psychiatric conditions</li>
     *     <li>physical rehab - Physical injuries and mobility impairments</li>
     *   </ul>
     * </p>
     */
    SPECIALIZATION("specialization", "Specialization",
        new String[]{"pediatric", "geriatric", "mental health", "physical rehab"}),

    /**
     * Delivery mode dimension representing how services are delivered to patients.
     *
     * <p><b>Workflow Pattern Integration:</b> WCP-21 (Deferred Choice) for patient preference handling.
     * Patient preferences are collected and processed, with the choice deferred until
     * optimal matching occurs.</p>
     *
     * <p><b>Valid Values:</b>
     *   <ul>
     *     <li>telehealth - Virtual delivery via video/audio platforms</li>
     *     <li>in-person - Face-to-face service delivery</li>
     *     <li>hybrid - Combination of virtual and in-person</li>
     *   </ul>
     * </p>
     */
    DELIVERY_MODE("deliveryMode", "Delivery Mode",
        new String[]{"telehealth", "in-person", "hybrid"}),

    /**
     * Urgency dimension representing the time sensitivity of service delivery.
     *
     * <p><b>Workflow Pattern Integration:</b> WCP-4 (Exclusive Choice) for urgent routing protocols.
     * Urgent and emergency cases are routed through prioritized channels with accelerated
     * processing paths.</p>
     *
     * <p><b>Valid Values:</b>
     *   <ul>
     *     <li>routine - Standard wait times (1-2 weeks)</li>
     *     <li>urgent - Priority processing (1-7 days)</li>
     *     <li>emergency - Immediate response (same day)</li>
     *   </ul>
     * </p>
     */
    URGENCY("urgency", "Urgency",
        new String[]{"routine", "urgent", "emergency"}),

    /**
     * Price range dimension representing the cost tiers of services.
     *
     * <p><b>Pricing Model Integration:</b> Dynamic pricing based on service complexity,
     * provider expertise, and market demand. Price ranges are relative to the patient's
     * financial capabilities and insurance coverage.</p>
     *
     * <p><b>Valid Values:</b>
     *   <ul>
     *     <li>budget - Basic services (25-50% below average market rate)</li>
     *     <li>standard - Professional services (average market rate)</li>
     *     <li>premium - Specialized services (25-100% above average market rate)</li>
     *   </ul>
     * </p>
     */
    PRICE_RANGE("priceRange", "Price Range",
        new String[]{"budget", "standard", "premium"}),

    /**
     * Rating threshold dimension representing the minimum acceptable quality level.
     *
     * <p><b>Quality Integration:</b> Rating thresholds filter providers based on
     * patient preferences and historical performance data. Combined with WCP-6 (Multi-Choice)
     * for bundle selection of multiple qualified providers.</p>
     *
     * <p><b>Valid Values:</b>
     *   <ul>
     *     <li>3+ - Minimum acceptable rating (3.0+ stars)</li>
     *     <li>4+ - Good quality providers (4.0+ stars)</li>
     *     <li>4.5+ - Excellent providers (4.5+ stars)</li>
     *   </ul>
     * </p>
     */
    RATING_THRESHOLD("ratingThreshold", "Rating Threshold",
        new String[]{"3+", "4+", "4.5+"});

    private final String key;
    private final String displayName;
    private final String[] validValues;

    /**
     * Constructor for MarketplaceDimension enum.
     *
     * @param key the machine-readable key for this dimension
     * @param displayName the human-readable name for this dimension
     * @param validValues the array of valid values for this dimension
     */
    MarketplaceDimension(String key, String displayName, String[] validValues) {
        this.key = key;
        this.displayName = displayName;
        this.validValues = validValues;
    }

    /**
     * Gets the machine-readable key for this dimension.
     *
     * @return the dimension key
     */
    public String getKey() {
        return key;
    }

    /**
     * Gets the human-readable name for this dimension.
     *
     * @return the display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets the valid values for this dimension.
     *
     * @return array of valid values
     */
    public String[] getValidValues() {
        return validValues;
    }

    /**
     * Validates if a value is valid for this dimension.
     *
     * @param value the value to validate
     * @return true if the value is valid, false otherwise
     */
    public boolean isValidValue(String value) {
        if (value == null) return false;
        for (String validValue : validValues) {
            if (validValue.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets the ordinal position of a value within this dimension.
     * Used for vector calculations in distance algorithms.
     *
     * @param value the value to get the ordinal for
     * @return the ordinal position, or -1 if value is invalid
     */
    public int getOrdinal(String value) {
        if (value == null) return -1;
        String lowerValue = value.toLowerCase();
        for (int i = 0; i < validValues.length; i++) {
            if (validValues[i].equalsIgnoreCase(lowerValue)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Creates a dimension-specific ordinal map for vector calculations.
     *
     * @return map of value names to ordinal positions
     */
    public java.util.Map<String, Integer> getOrdinalMap() {
        java.util.Map<String, Integer> ordinalMap = new java.util.HashMap<>();
        for (int i = 0; i < validValues.length; i++) {
            ordinalMap.put(validValues[i].toLowerCase(), i);
        }
        return ordinalMap;
    }
}