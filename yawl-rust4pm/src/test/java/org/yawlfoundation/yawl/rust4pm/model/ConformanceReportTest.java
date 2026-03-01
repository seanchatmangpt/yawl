package org.yawlfoundation.yawl.rust4pm.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConformanceReportTest {

    @Test
    void f1Score_perfect_fitness_and_precision() {
        ConformanceReport r = new ConformanceReport(1.0, 1.0, 100, null);
        assertEquals(1.0, r.f1Score(), 0.0001);
        assertTrue(r.isPerfectFit());
    }

    @Test
    void f1Score_zero_when_both_zero() {
        ConformanceReport r = new ConformanceReport(0.0, 0.0, 0, null);
        assertEquals(0.0, r.f1Score());
        assertFalse(r.isPerfectFit());
    }

    @Test
    void f1Score_harmonic_mean() {
        ConformanceReport r = new ConformanceReport(0.8, 0.6, 50, null);
        double expected = 2.0 * (0.8 * 0.6) / (0.8 + 0.6);
        assertEquals(expected, r.f1Score(), 0.0001);
    }

    @Test
    void isPerfectFit_false_when_precision_below_one() {
        ConformanceReport r = new ConformanceReport(1.0, 0.9, 10, null);
        assertFalse(r.isPerfectFit());
    }

    @Test
    void record_fields_with_model() {
        ProcessModel.PetriNet model = new ProcessModel.PetriNet(List.of(), List.of(), "");
        ConformanceReport r = new ConformanceReport(0.95, 0.87, 200, model);
        assertEquals(0.95, r.fitness(), 0.001);
        assertEquals(0.87, r.precision(), 0.001);
        assertEquals(200, r.eventCount());
        assertInstanceOf(ProcessModel.PetriNet.class, r.model());
    }
}
