package org.yawlfoundation.yawl.mcp.a2a.gregverse.matching;

import java.util.Map;
import java.util.Objects;

/**
 * N-dimensional coordinate for service-provider matching in GregVerse OT marketplace.
 *
 * @param d1ServiceType Service type (assessment, intervention, scheduling)
 * @param d2Specialization Specialization (pediatric, geriatric, mental health, physical rehab)
 * @param d3DeliveryMode Delivery mode (telehealth, in-person, hybrid)
 * @param d4Urgency Urgency level (routine, urgent, emergency)
 * @param d5PriceRange Price range (budget, standard, premium)
 * @param d6RatingThreshold Rating threshold (3+, 4+, 4.5+)
 */
public record NDimensionalCoordinate(
    String d1ServiceType,
    String d2Specialization,
    String d3DeliveryMode,
    String d4Urgency,
    String d5PriceRange,
    String d6RatingThreshold
) {

    public static final Map<String, Integer> SERVICE_TYPE_ORDINAL = Map.of(
        "assessment", 0,
        "intervention", 1,
        "scheduling", 2
    );

    public static final Map<String, Integer> SPECIALIZATION_ORDINAL = Map.of(
        "pediatric", 0,
        "geriatric", 1,
        "mental health", 2,
        "physical rehab", 3
    );

    public static final Map<String, Integer> DELIVERY_MODE_ORDINAL = Map.of(
        "telehealth", 0,
        "in-person", 1,
        "hybrid", 2
    );

    public static final Map<String, Integer> URGENCY_ORDINAL = Map.of(
        "routine", 0,
        "urgent", 1,
        "emergency", 2
    );

    public static final Map<String, Integer> PRICE_RANGE_ORDINAL = Map.of(
        "budget", 0,
        "standard", 1,
        "premium", 2
    );

    public static final Map<String, Integer> RATING_THRESHOLD_ORDINAL = Map.of(
        "3+", 3,
        "4+", 4,
        "4.5+", 5
    );

    public double[] toVector() {
        return new double[] {
            SERVICE_TYPE_ORDINAL.getOrDefault(d1ServiceType.toLowerCase(), -1),
            SPECIALIZATION_ORDINAL.getOrDefault(d2Specialization.toLowerCase(), -1),
            DELIVERY_MODE_ORDINAL.getOrDefault(d3DeliveryMode.toLowerCase(), -1),
            URGENCY_ORDINAL.getOrDefault(d4Urgency.toLowerCase(), -1),
            PRICE_RANGE_ORDINAL.getOrDefault(d5PriceRange.toLowerCase(), -1),
            RATING_THRESHOLD_ORDINAL.getOrDefault(d6RatingThreshold.toLowerCase(), -1)
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NDimensionalCoordinate that = (NDimensionalCoordinate) o;
        return Objects.equals(d1ServiceType, that.d1ServiceType) &&
               Objects.equals(d2Specialization, that.d2Specialization) &&
               Objects.equals(d3DeliveryMode, that.d3DeliveryMode) &&
               Objects.equals(d4Urgency, that.d4Urgency) &&
               Objects.equals(d5PriceRange, that.d5PriceRange) &&
               Objects.equals(d6RatingThreshold, that.d6RatingThreshold);
    }

    @Override
    public int hashCode() {
        return Objects.hash(d1ServiceType, d2Specialization, d3DeliveryMode,
                           d4Urgency, d5PriceRange, d6RatingThreshold);
    }

    public double distanceTo(NDimensionalCoordinate other) {
        double[] v1 = this.toVector();
        double[] v2 = other.toVector();

        double sumSquared = 0.0;
        for (int i = 0; i < v1.length; i++) {
            double diff = v1[i] - v2[i];
            sumSquared += diff * diff;
        }
        return Math.sqrt(sumSquared);
    }
}