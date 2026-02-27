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

package org.yawlfoundation.yawl.pi.automl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests Tpot2Config construction, factory methods, and validation guards.
 *
 * <p>Chicago TDD: tests cover real record construction and validation behaviour
 * with no mock objects. No TPOT2 installation required.
 */
public class Tpot2ConfigTest {

    // ── defaults() factory ────────────────────────────────────────────────────

    @Test
    public void testDefaultsReturnsAllExpectedValues() {
        Tpot2Config cfg = Tpot2Config.defaults(Tpot2TaskType.CASE_OUTCOME);

        assertEquals(Tpot2TaskType.CASE_OUTCOME, cfg.taskType());
        assertEquals(5, cfg.generations());
        assertEquals(50, cfg.populationSize());
        assertEquals(60, cfg.maxTimeMins());
        assertEquals(5, cfg.cvFolds());
        assertNull(cfg.scoringMetric());
        assertEquals(-1, cfg.nJobs());
        assertEquals("python3", cfg.pythonExecutable());
    }

    @Test
    public void testDefaultsForAllTaskTypesAreValid() {
        for (Tpot2TaskType type : Tpot2TaskType.values()) {
            assertDoesNotThrow(
                () -> Tpot2Config.defaults(type),
                "defaults() should not throw for task type: " + type);
        }
    }

    @Test
    public void testDefaultsNullTaskTypeThrows() {
        assertThrows(NullPointerException.class, () -> Tpot2Config.defaults(null));
    }

    // ── task-specific factory methods ─────────────────────────────────────────

    @Test
    public void testForCaseOutcomeSetsRocAucMetric() {
        Tpot2Config cfg = Tpot2Config.forCaseOutcome();
        assertEquals(Tpot2TaskType.CASE_OUTCOME, cfg.taskType());
        assertEquals("roc_auc", cfg.scoringMetric());
    }

    @Test
    public void testForRemainingTimeSetsRegressionMetric() {
        Tpot2Config cfg = Tpot2Config.forRemainingTime();
        assertEquals(Tpot2TaskType.REMAINING_TIME, cfg.taskType());
        assertEquals("neg_mean_absolute_error", cfg.scoringMetric());
    }

    @Test
    public void testForNextActivitySetsFMacroMetric() {
        Tpot2Config cfg = Tpot2Config.forNextActivity();
        assertEquals(Tpot2TaskType.NEXT_ACTIVITY, cfg.taskType());
        assertEquals("f1_macro", cfg.scoringMetric());
    }

    @Test
    public void testForAnomalyDetectionSetsRocAucMetric() {
        Tpot2Config cfg = Tpot2Config.forAnomalyDetection();
        assertEquals(Tpot2TaskType.ANOMALY_DETECTION, cfg.taskType());
        assertEquals("roc_auc", cfg.scoringMetric());
    }

    // ── null guards ───────────────────────────────────────────────────────────

    @Test
    public void testNullTaskTypeRejected() {
        NullPointerException ex = assertThrows(NullPointerException.class,
            () -> new Tpot2Config(null, 5, 50, 60, 5, null, -1, "python3"));
        assertTrue(ex.getMessage().contains("taskType"));
    }

    @Test
    public void testNullPythonExecutableRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new Tpot2Config(Tpot2TaskType.CASE_OUTCOME, 5, 50, 60, 5, null, -1, null));
    }

    // ── generations validation ────────────────────────────────────────────────

    @Test
    public void testGenerationsZeroRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> new Tpot2Config(Tpot2TaskType.CASE_OUTCOME, 0, 50, 60, 5, null, -1, "python3"));
        assertTrue(ex.getMessage().contains("generations"));
    }

    @Test
    public void testGenerationsTooHighRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new Tpot2Config(Tpot2TaskType.CASE_OUTCOME, 101, 50, 60, 5, null, -1, "python3"));
    }

    @Test
    public void testGenerationsBoundaryValuesAccepted() {
        assertDoesNotThrow(() ->
            new Tpot2Config(Tpot2TaskType.CASE_OUTCOME, 1, 50, 60, 5, null, -1, "python3"));
        assertDoesNotThrow(() ->
            new Tpot2Config(Tpot2TaskType.CASE_OUTCOME, 100, 50, 60, 5, null, -1, "python3"));
    }

    // ── populationSize validation ─────────────────────────────────────────────

    @Test
    public void testPopulationSizeTooLowRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new Tpot2Config(Tpot2TaskType.CASE_OUTCOME, 5, 1, 60, 5, null, -1, "python3"));
    }

    @Test
    public void testPopulationSizeTooHighRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new Tpot2Config(Tpot2TaskType.CASE_OUTCOME, 5, 501, 60, 5, null, -1, "python3"));
    }

    @Test
    public void testPopulationSizeBoundaryValuesAccepted() {
        assertDoesNotThrow(() ->
            new Tpot2Config(Tpot2TaskType.CASE_OUTCOME, 5, 2, 60, 5, null, -1, "python3"));
        assertDoesNotThrow(() ->
            new Tpot2Config(Tpot2TaskType.CASE_OUTCOME, 5, 500, 60, 5, null, -1, "python3"));
    }

    // ── maxTimeMins validation ────────────────────────────────────────────────

    @Test
    public void testMaxTimeMinsZeroRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new Tpot2Config(Tpot2TaskType.CASE_OUTCOME, 5, 50, 0, 5, null, -1, "python3"));
    }

    @Test
    public void testMaxTimeMinsTooHighRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new Tpot2Config(Tpot2TaskType.CASE_OUTCOME, 5, 50, 1441, 5, null, -1, "python3"));
    }

    // ── cvFolds validation ────────────────────────────────────────────────────

    @Test
    public void testCvFoldsTooLowRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new Tpot2Config(Tpot2TaskType.CASE_OUTCOME, 5, 50, 60, 1, null, -1, "python3"));
    }

    @Test
    public void testCvFoldsTooHighRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new Tpot2Config(Tpot2TaskType.CASE_OUTCOME, 5, 50, 60, 11, null, -1, "python3"));
    }

    // ── pythonExecutable validation ───────────────────────────────────────────

    @Test
    public void testBlankPythonExecutableRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new Tpot2Config(Tpot2TaskType.CASE_OUTCOME, 5, 50, 60, 5, null, -1, "  "));
        assertThrows(IllegalArgumentException.class,
            () -> new Tpot2Config(Tpot2TaskType.CASE_OUTCOME, 5, 50, 60, 5, null, -1, ""));
    }

    // ── optional fields ───────────────────────────────────────────────────────

    @Test
    public void testScoringMetricIsOptional() {
        // null scoringMetric is accepted — TPOT2 uses its own default
        assertDoesNotThrow(() ->
            new Tpot2Config(Tpot2TaskType.REMAINING_TIME, 5, 50, 60, 5, null, -1, "python3"));
        assertDoesNotThrow(() ->
            new Tpot2Config(Tpot2TaskType.REMAINING_TIME, 5, 50, 60, 5, "r2", -1, "python3"));
    }

    // ── Tpot2TaskType.isClassification() ─────────────────────────────────────

    @Test
    public void testIsClassificationHelper() {
        assertTrue(Tpot2TaskType.CASE_OUTCOME.isClassification());
        assertTrue(Tpot2TaskType.NEXT_ACTIVITY.isClassification());
        assertTrue(Tpot2TaskType.ANOMALY_DETECTION.isClassification());
        assertFalse(Tpot2TaskType.REMAINING_TIME.isClassification());
    }

    // ── Tpot2Result validation ────────────────────────────────────────────────

    @Test
    public void testResultRejectsNullOnnxBytes() {
        assertThrows(NullPointerException.class,
            () -> new Tpot2Result(Tpot2TaskType.CASE_OUTCOME, 0.9, "pipeline", null, 1000));
    }

    @Test
    public void testResultRejectsEmptyOnnxBytes() {
        assertThrows(IllegalArgumentException.class,
            () -> new Tpot2Result(Tpot2TaskType.CASE_OUTCOME, 0.9, "pipeline", new byte[0], 1000));
    }

    @Test
    public void testResultRejectsNegativeTrainingTime() {
        assertThrows(IllegalArgumentException.class,
            () -> new Tpot2Result(Tpot2TaskType.CASE_OUTCOME, 0.9, "pipeline",
                new byte[]{1, 2, 3}, -1));
    }

    @Test
    public void testResultAcceptsValidValues() {
        Tpot2Result result = new Tpot2Result(
            Tpot2TaskType.CASE_OUTCOME, 0.92, "GradientBoosting()",
            new byte[]{0x4F, 0x4E, 0x4E, 0x58}, 14325L);

        assertEquals(Tpot2TaskType.CASE_OUTCOME, result.taskType());
        assertEquals(0.92, result.bestScore(), 0.001);
        assertEquals("GradientBoosting()", result.pipelineDescription());
        assertEquals(4, result.onnxModelBytes().length);
        assertEquals(14325L, result.trainingTimeMs());
    }
}
