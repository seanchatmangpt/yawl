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
package org.yawlfoundation.yawl.erlang.processmining;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ConformanceResult} record.
 * Tests record contract, validation, and immutability.
 */
@Tag("unit")
@DisplayName("ConformanceResult tests")
class ConformanceResultTest {

    @Test
    @DisplayName("Record stores fitness correctly")
    void record_stores_fitness_correctly() {
        double fitness = 0.95;
        ConformanceResult result = new ConformanceResult(fitness, 0.85, 100, 95, "test");

        assertEquals(fitness, result.fitness());
    }

    @Test
    @DisplayName("Record stores precision correctly")
    void record_stores_precision_correctly() {
        double precision = 0.89;
        ConformanceResult result = new ConformanceResult(0.95, precision, 100, 95, "test");

        assertEquals(precision, result.precision());
    }

    @Test
    @DisplayName("Record stores totalEvents correctly")
    void record_stores_totalEvents_correctly() {
        int totalEvents = 1000;
        ConformanceResult result = new ConformanceResult(0.95, 0.89, totalEvents, 950, "test");

        assertEquals(totalEvents, result.totalEvents());
    }

    @Test
    @DisplayName("Record stores conformingEvents correctly")
    void record_stores_conformingEvents_correctly() {
        int conformingEvents = 950;
        ConformanceResult result = new ConformanceResult(0.95, 0.89, 1000, conformingEvents, "test");

        assertEquals(conformingEvents, result.conformingEvents());
    }

    @Test
    @DisplayName("Record stores diagnosis correctly")
    void record_stores_diagnosis_correctly() {
        String diagnosis = "fitness=0.95, precision=0.89";
        ConformanceResult result = new ConformanceResult(0.95, 0.89, 100, 95, diagnosis);

        assertEquals(diagnosis, result.diagnosis());
    }

    @Test
    @DisplayName("Equals is value-based")
    void equals_value_based() {
        ConformanceResult result1 = new ConformanceResult(0.95, 0.89, 100, 95, "test");
        ConformanceResult result2 = new ConformanceResult(0.95, 0.89, 100, 95, "test");

        assertEquals(result1, result2);
    }

    @Test
    @DisplayName("Equals is false for different fitness")
    void equals_false_different_fitness() {
        ConformanceResult result1 = new ConformanceResult(0.95, 0.89, 100, 95, "test");
        ConformanceResult result2 = new ConformanceResult(0.90, 0.89, 100, 95, "test");

        assertNotEquals(result1, result2);
    }

    @Test
    @DisplayName("Equals is false for different precision")
    void equals_false_different_precision() {
        ConformanceResult result1 = new ConformanceResult(0.95, 0.89, 100, 95, "test");
        ConformanceResult result2 = new ConformanceResult(0.95, 0.85, 100, 95, "test");

        assertNotEquals(result1, result2);
    }

    @Test
    @DisplayName("hashCode is value-based")
    void hashCode_value_based() {
        ConformanceResult result1 = new ConformanceResult(0.95, 0.89, 100, 95, "test");
        ConformanceResult result2 = new ConformanceResult(0.95, 0.89, 100, 95, "test");

        assertEquals(result1.hashCode(), result2.hashCode());
    }

    @Test
    @DisplayName("hashCode differs for different values")
    void hashCode_differs_for_different_values() {
        ConformanceResult result1 = new ConformanceResult(0.95, 0.89, 100, 95, "test");
        ConformanceResult result2 = new ConformanceResult(0.90, 0.89, 100, 95, "test");

        assertNotEquals(result1.hashCode(), result2.hashCode());
    }

    @Test
    @DisplayName("toString() contains fitness value")
    void toString_contains_fitness() {
        ConformanceResult result = new ConformanceResult(0.95, 0.89, 100, 95, "test");
        String str = result.toString();

        assertTrue(str.contains("0.95") || str.contains("fitness"));
    }

    @Test
    @DisplayName("toString() contains precision value")
    void toString_contains_precision() {
        ConformanceResult result = new ConformanceResult(0.95, 0.89, 100, 95, "test");
        String str = result.toString();

        assertTrue(str.contains("0.89") || str.contains("precision"));
    }

    @Test
    @DisplayName("Fitness validation: rejects negative fitness")
    void record_validation_negative_fitness() {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> new ConformanceResult(-0.1, 0.89, 100, 95, "test")
        );
        assertTrue(ex.getMessage().contains("fitness"));
    }

    @Test
    @DisplayName("Fitness validation: rejects fitness > 1.0")
    void record_validation_fitness_above_one() {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> new ConformanceResult(1.5, 0.89, 100, 95, "test")
        );
        assertTrue(ex.getMessage().contains("fitness"));
    }

    @Test
    @DisplayName("Precision validation: rejects negative precision")
    void record_validation_negative_precision() {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> new ConformanceResult(0.95, -0.1, 100, 95, "test")
        );
        assertTrue(ex.getMessage().contains("precision"));
    }

    @Test
    @DisplayName("Precision validation: rejects precision > 1.0")
    void record_validation_precision_above_one() {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> new ConformanceResult(0.95, 1.5, 100, 95, "test")
        );
        assertTrue(ex.getMessage().contains("precision"));
    }

    @Test
    @DisplayName("totalEvents validation: rejects negative")
    void record_validation_negative_totalEvents() {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> new ConformanceResult(0.95, 0.89, -1, 0, "test")
        );
        assertTrue(ex.getMessage().contains("totalEvents"));
    }

    @Test
    @DisplayName("conformingEvents validation: rejects negative")
    void record_validation_negative_conformingEvents() {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> new ConformanceResult(0.95, 0.89, 100, -1, "test")
        );
        assertTrue(ex.getMessage().contains("conformingEvents"));
    }

    @Test
    @DisplayName("conformingEvents validation: rejects exceeding totalEvents")
    void record_validation_conformingEvents_exceeds_total() {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> new ConformanceResult(0.95, 0.89, 100, 150, "test")
        );
        assertTrue(ex.getMessage().contains("conformingEvents"));
    }

    @Test
    @DisplayName("diagnosis validation: rejects null")
    void record_validation_null_diagnosis() {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> new ConformanceResult(0.95, 0.89, 100, 95, null)
        );
        assertTrue(ex.getMessage().contains("diagnosis"));
    }

    @Test
    @DisplayName("Accepts boundary fitness 0.0")
    void accepts_fitness_zero() {
        ConformanceResult result = new ConformanceResult(0.0, 0.89, 100, 95, "test");
        assertEquals(0.0, result.fitness());
    }

    @Test
    @DisplayName("Accepts boundary fitness 1.0")
    void accepts_fitness_one() {
        ConformanceResult result = new ConformanceResult(1.0, 0.89, 100, 95, "test");
        assertEquals(1.0, result.fitness());
    }

    @Test
    @DisplayName("Accepts boundary precision 0.0")
    void accepts_precision_zero() {
        ConformanceResult result = new ConformanceResult(0.95, 0.0, 100, 95, "test");
        assertEquals(0.0, result.precision());
    }

    @Test
    @DisplayName("Accepts boundary precision 1.0")
    void accepts_precision_one() {
        ConformanceResult result = new ConformanceResult(0.95, 1.0, 100, 95, "test");
        assertEquals(1.0, result.precision());
    }

    @Test
    @DisplayName("Accepts conformingEvents == totalEvents")
    void accepts_conforming_equals_total() {
        ConformanceResult result = new ConformanceResult(0.95, 0.89, 100, 100, "test");
        assertEquals(100, result.conformingEvents());
        assertEquals(100, result.totalEvents());
    }

    @Test
    @DisplayName("Accepts conformingEvents == 0 when totalEvents > 0")
    void accepts_zero_conforming_events() {
        ConformanceResult result = new ConformanceResult(0.95, 0.89, 100, 0, "test");
        assertEquals(0, result.conformingEvents());
    }

    @Test
    @DisplayName("Accepts totalEvents == 0 and conformingEvents == 0")
    void accepts_zero_total_and_conforming() {
        ConformanceResult result = new ConformanceResult(0.95, 0.89, 0, 0, "test");
        assertEquals(0, result.totalEvents());
        assertEquals(0, result.conformingEvents());
    }
}
