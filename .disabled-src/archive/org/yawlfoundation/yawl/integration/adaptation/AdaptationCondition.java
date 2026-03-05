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

package org.yawlfoundation.yawl.integration.adaptation;

import java.util.Objects;

/**
 * Functional interface for matching process events against adaptation rule conditions.
 *
 * <p>AdaptationCondition is the core abstraction for defining event matching logic.
 * Implementations are composed using factory methods like {@link #and(AdaptationCondition, AdaptationCondition)}
 * and {@link #or(AdaptationCondition, AdaptationCondition)} to build complex conditions from
 * simple ones.</p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * AdaptationCondition condition = AdaptationCondition.and(
 *     AdaptationCondition.eventType("FRAUD_ALERT"),
 *     AdaptationCondition.payloadAbove("risk_score", 0.8)
 * );
 * boolean matches = condition.matches(event);
 * }</pre>
 *
 * @author YAWL Foundation
 * @since 6.0
 */
@FunctionalInterface
public interface AdaptationCondition {
    /**
     * Tests whether the given event matches this condition.
     *
     * @param event the event to test
     * @return true if the event matches, false otherwise
     * @throws NullPointerException if event is null
     */
    boolean matches(ProcessEvent event);

    /**
     * Creates a condition that matches events with a specific event type.
     *
     * @param type the event type to match (case-sensitive)
     * @return a condition that matches events with the given type
     * @throws NullPointerException if type is null
     */
    static AdaptationCondition eventType(String type) {
        Objects.requireNonNull(type, "type must not be null");
        return event -> type.equals(event.eventType());
    }

    /**
     * Creates a condition that matches events whose payload numeric value exceeds a threshold.
     *
     * <p>The payload value is retrieved via {@link ProcessEvent#numericPayload(String)}.
     * If the key is not found or the value is not numeric, this condition returns false
     * (does not throw).</p>
     *
     * @param key       the payload key to retrieve
     * @param threshold the numeric threshold (exclusive)
     * @return a condition matching events with payload[key] &gt; threshold
     * @throws NullPointerException if key is null
     */
    static AdaptationCondition payloadAbove(String key, double threshold) {
        Objects.requireNonNull(key, "key must not be null");
        return event -> {
            try {
                return event.numericPayload(key) > threshold;
            } catch (IllegalArgumentException e) {
                return false;
            }
        };
    }

    /**
     * Creates a condition that matches events whose payload numeric value is below a threshold.
     *
     * <p>The payload value is retrieved via {@link ProcessEvent#numericPayload(String)}.
     * If the key is not found or the value is not numeric, this condition returns false
     * (does not throw).</p>
     *
     * @param key       the payload key to retrieve
     * @param threshold the numeric threshold (exclusive)
     * @return a condition matching events with payload[key] &lt; threshold
     * @throws NullPointerException if key is null
     */
    static AdaptationCondition payloadBelow(String key, double threshold) {
        Objects.requireNonNull(key, "key must not be null");
        return event -> {
            try {
                return event.numericPayload(key) < threshold;
            } catch (IllegalArgumentException e) {
                return false;
            }
        };
    }

    /**
     * Creates a condition that matches events whose payload value equals a target.
     *
     * <p>Equality is determined via {@link Objects#equals(Object, Object)}.
     * If the key is not found, this condition returns false.</p>
     *
     * @param key    the payload key to retrieve
     * @param target the target value to match
     * @return a condition matching events with payload[key] == target
     * @throws NullPointerException if key is null
     */
    static AdaptationCondition payloadEquals(String key, Object target) {
        Objects.requireNonNull(key, "key must not be null");
        return event -> Objects.equals(event.payloadValue(key), target);
    }

    /**
     * Creates a condition that matches events with a minimum severity level.
     *
     * @param minSeverity the minimum severity required
     * @return a condition matching events with severity &gt;= minSeverity
     * @throws NullPointerException if minSeverity is null
     */
    static AdaptationCondition severityAtLeast(EventSeverity minSeverity) {
        Objects.requireNonNull(minSeverity, "minSeverity must not be null");
        return event -> event.severity().isAtLeast(minSeverity);
    }

    /**
     * Creates a condition that matches events when both conditions match (logical AND).
     *
     * <p>Evaluation is short-circuiting: if {@code a} returns false, {@code b} is not evaluated.</p>
     *
     * @param a first condition
     * @param b second condition
     * @return a condition matching when both a and b match
     * @throws NullPointerException if either condition is null
     */
    static AdaptationCondition and(AdaptationCondition a, AdaptationCondition b) {
        Objects.requireNonNull(a, "condition a must not be null");
        Objects.requireNonNull(b, "condition b must not be null");
        return event -> a.matches(event) && b.matches(event);
    }

    /**
     * Creates a condition that matches events when either condition matches (logical OR).
     *
     * <p>Evaluation is short-circuiting: if {@code a} returns true, {@code b} is not evaluated.</p>
     *
     * @param a first condition
     * @param b second condition
     * @return a condition matching when either a or b matches
     * @throws NullPointerException if either condition is null
     */
    static AdaptationCondition or(AdaptationCondition a, AdaptationCondition b) {
        Objects.requireNonNull(a, "condition a must not be null");
        Objects.requireNonNull(b, "condition b must not be null");
        return event -> a.matches(event) || b.matches(event);
    }
}
