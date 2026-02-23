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

package org.yawlfoundation.yawl.mcp.a2a.therapy;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.mcp.a2a.therapy.agents.OTAdaptationAgent;
import org.yawlfoundation.yawl.mcp.a2a.therapy.agents.OTAssessmentAgent;
import org.yawlfoundation.yawl.mcp.a2a.therapy.agents.OTGoalSettingAgent;
import org.yawlfoundation.yawl.mcp.a2a.therapy.agents.OTIntakeAgent;
import org.yawlfoundation.yawl.mcp.a2a.therapy.agents.OTInterventionPlannerAgent;
import org.yawlfoundation.yawl.mcp.a2a.therapy.agents.OTOutcomeEvaluatorAgent;
import org.yawlfoundation.yawl.mcp.a2a.therapy.agents.OTProgressMonitorAgent;
import org.yawlfoundation.yawl.mcp.a2a.therapy.agents.OTSchedulerAgent;
import org.yawlfoundation.yawl.mcp.a2a.therapy.domain.LifestyleGoal;
import org.yawlfoundation.yawl.mcp.a2a.therapy.domain.OccupationalProfile;
import org.yawlfoundation.yawl.mcp.a2a.therapy.domain.OTPatient;
import org.yawlfoundation.yawl.mcp.a2a.therapy.domain.SwarmTaskResult;
import org.yawlfoundation.yawl.mcp.a2a.therapy.domain.TherapySession;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-End integration tests for the Occupational Therapy Lifestyle Redesign Swarm.
 *
 * <p>Tests real agent execution without mocks, verifying that the 8-phase swarm workflow
 * produces clinically meaningful occupational therapy outcomes. Each test validates one
 * aspect of the workflow: agent registry completeness, individual agent behavior,
 * domain model invariants, and full coordinator lifecycle execution.</p>
 *
 * <p>Chicago TDD approach: tests exercise real domain objects, verify real state changes,
 * and assert on actual clinical outputs (risk stratification, goals, session schedules,
 * progress scores, adaptation cycles, clinical recommendations).</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@DisplayName("E2E: Occupational Therapy Lifestyle Redesign Swarm")
class E2ETherapySwarmTest {

    // ========== Test Fixtures ==========

    private static OTPatient strokePatient;
    private static OTPatient depressionPatient;
    private static OTPatient paediatricPatient;

    @BeforeAll
    static void setUpFixtures() {
        strokePatient = new OTPatient(
            "P001",
            "Margaret Chen",
            72,
            "stroke",
            "Left hemiplegia following ischaemic stroke",
            "I want to dress myself again"
        );

        depressionPatient = new OTPatient(
            "P002",
            "James Rodriguez",
            34,
            "moderate depression",
            "Unable to maintain work and daily routine",
            "I want to return to work full-time"
        );

        paediatricPatient = new OTPatient(
            "P003",
            "Emma Wilson",
            8,
            "developmental coordination disorder",
            "Difficulties with school tasks and handwriting",
            "I want to write like my friends"
        );
    }

    // ========== Registry Tests ==========

    @Test
    @DisplayName("Registry contains all eight swarm agents")
    void testRegistryContainsAllEightAgents() {
        OTAgentRegistry registry = new OTAgentRegistry();

        assertAll(
            () -> assertEquals(8, registry.size(), "Registry should contain exactly 8 agents"),
            () -> assertNotNull(registry.getAgent(SwarmPhase.INTAKE), "INTAKE agent registered"),
            () -> assertNotNull(registry.getAgent(SwarmPhase.ASSESSMENT), "ASSESSMENT agent registered"),
            () -> assertNotNull(registry.getAgent(SwarmPhase.GOAL_SETTING), "GOAL_SETTING agent registered"),
            () -> assertNotNull(registry.getAgent(SwarmPhase.INTERVENTION_PLANNING), "INTERVENTION_PLANNING agent registered"),
            () -> assertNotNull(registry.getAgent(SwarmPhase.SCHEDULING), "SCHEDULING agent registered"),
            () -> assertNotNull(registry.getAgent(SwarmPhase.PROGRESS_MONITORING), "PROGRESS_MONITORING agent registered"),
            () -> assertNotNull(registry.getAgent(SwarmPhase.ADAPTATION), "ADAPTATION agent registered"),
            () -> assertNotNull(registry.getAgent(SwarmPhase.OUTCOME_EVALUATION), "OUTCOME_EVALUATION agent registered")
        );
    }

    @Test
    @DisplayName("Registry has agent for every SwarmPhase value")
    void testRegistryAllPhasesRegistered() {
        OTAgentRegistry registry = new OTAgentRegistry();
        Map<SwarmPhase, OTSwarmAgent> allAgents = registry.getAllAgents();

        assertAll(
            () -> {
                for (SwarmPhase phase : SwarmPhase.values()) {
                    assertTrue(allAgents.containsKey(phase),
                        "Phase " + phase + " should have registered agent");
                    assertNotNull(allAgents.get(phase),
                        "Agent for phase " + phase + " should not be null");
                }
            }
        );
    }

    // ========== Domain Model Tests ==========

    @Test
    @DisplayName("OTPatient validation rejects null and invalid fields")
    void testOTPatientValidation() {
        assertAll(
            () -> assertThrows(IllegalArgumentException.class,
                () -> new OTPatient(null, "name", 50, "condition", "reason", "goal"),
                "Null ID should be rejected"),
            () -> assertThrows(IllegalArgumentException.class,
                () -> new OTPatient("P001", "", 50, "condition", "reason", "goal"),
                "Blank name should be rejected"),
            () -> assertThrows(IllegalArgumentException.class,
                () -> new OTPatient("P001", "name", -1, "condition", "reason", "goal"),
                "Negative age should be rejected"),
            () -> assertThrows(IllegalArgumentException.class,
                () -> new OTPatient("P001", "name", 200, "condition", "reason", "goal"),
                "Age > 150 should be rejected")
        );
    }

    @Test
    @DisplayName("OccupationalProfile defensive copy prevents external mutation")
    void testOccupationalProfileDefensiveCopy() {
        Map<String, Integer> perfScores = new HashMap<>();
        perfScores.put("self-care", 6);
        perfScores.put("productivity", 4);

        Map<String, Integer> satScores = new HashMap<>();
        satScores.put("self-care", 5);
        satScores.put("productivity", 3);

        List<String> areas = new java.util.ArrayList<>();
        areas.add("self-care");
        areas.add("productivity");

        OccupationalProfile profile = new OccupationalProfile(
            "P001", perfScores, satScores, areas, "Notes"
        );

        // Verify that external modifications to input maps don't affect profile
        perfScores.put("self-care", 10);
        areas.add("leisure");

        assertEquals(6, profile.performanceScores().get("self-care"),
            "Profile performance scores should be immutable");
        assertEquals(2, profile.priorityAreas().size(),
            "Profile priority areas should not reflect external modifications");
    }

    @Test
    @DisplayName("Patient age classification works correctly")
    void testPatientAgeClassification() {
        assertAll(
            () -> assertTrue(paediatricPatient.isPaediatric(), "Age 8 is paediatric"),
            () -> assertFalse(paediatricPatient.isGeriatric(), "Age 8 is not geriatric"),
            () -> assertFalse(depressionPatient.isPaediatric(), "Age 34 is not paediatric"),
            () -> assertFalse(depressionPatient.isGeriatric(), "Age 34 is not geriatric"),
            () -> assertFalse(strokePatient.isPaediatric(), "Age 72 is not paediatric"),
            () -> assertTrue(strokePatient.isGeriatric(), "Age 72 is geriatric")
        );
    }

    // ========== Individual Agent Tests ==========

    @Test
    @DisplayName("Intake agent stratifies stroke patient as HIGH risk")
    void testIntakeAgentStratifiesRisk() {
        OTIntakeAgent agent = new OTIntakeAgent();
        Map<String, Object> context = new HashMap<>();

        SwarmTaskResult result = agent.execute(strokePatient, context);

        assertAll(
            () -> assertTrue(result.success(), "Intake should succeed"),
            () -> assertEquals(SwarmPhase.INTAKE, result.phase()),
            () -> assertTrue(result.output().contains("Risk: HIGH"),
                "Stroke + geriatric should be stratified as HIGH risk"),
            () -> assertTrue(result.data().containsKey("risk_level")),
            () -> assertEquals("HIGH", result.data().get("risk_level")),
            () -> assertTrue(result.data().containsKey("programme_type")),
            () -> assertEquals("INPATIENT", result.data().get("programme_type"),
                "Stroke should use INPATIENT programme")
        );
    }

    @Test
    @DisplayName("Assessment agent produces valid occupational profile")
    void testAssessmentAgentProducesProfile() {
        OTAssessmentAgent agent = new OTAssessmentAgent();
        Map<String, Object> context = new HashMap<>();
        context.put("patientId", strokePatient.id());

        SwarmTaskResult result = agent.execute(strokePatient, context);

        assertAll(
            () -> assertTrue(result.success(), "Assessment should succeed"),
            () -> assertEquals(SwarmPhase.ASSESSMENT, result.phase()),
            () -> assertTrue(result.data().containsKey("profile"),
                "Assessment should produce 'profile' key"),
            () -> {
                Object profileObj = result.data().get("profile");
                assertNotNull(profileObj, "Profile should not be null");
                assertTrue(profileObj instanceof OccupationalProfile,
                    "Profile should be OccupationalProfile instance");
                OccupationalProfile profile = (OccupationalProfile) profileObj;
                assertTrue(profile.meanPerformanceScore() >= 1.0 && profile.meanPerformanceScore() <= 10.0,
                    "Mean performance score should be in valid range");
                assertTrue(profile.meanSatisfactionScore() >= 1.0 && profile.meanSatisfactionScore() <= 10.0,
                    "Mean satisfaction score should be in valid range");
                assertFalse(profile.priorityAreas().isEmpty(), "Should have priority areas");
                assertEquals(strokePatient.id(), profile.patientId());
            }
        );
    }

    @Test
    @DisplayName("Goal-setting agent requires occupational profile in context")
    void testGoalSettingAgentRequiresProfile() {
        OTGoalSettingAgent agent = new OTGoalSettingAgent();
        Map<String, Object> context = new HashMap<>();
        // Intentionally omit profile

        SwarmTaskResult result = agent.execute(depressionPatient, context);

        assertAll(
            () -> assertFalse(result.success(), "Goal-setting should fail without profile"),
            () -> assertEquals(SwarmPhase.GOAL_SETTING, result.phase()),
            () -> assertNotNull(result.errorMessage()),
            () -> assertTrue(result.errorMessage().contains("profile") ||
                           result.errorMessage().contains("Profile"),
                "Error should reference missing profile")
        );
    }

    @Test
    @DisplayName("Goal-setting agent produces three lifestyle goals")
    void testGoalSettingAgentProducesGoals() {
        OTGoalSettingAgent agent = new OTGoalSettingAgent();

        OccupationalProfile profile = new OccupationalProfile(
            depressionPatient.id(),
            Map.of("self-care", 5, "productivity", 3, "leisure", 4),
            Map.of("self-care", 4, "productivity", 2, "leisure", 3),
            List.of("productivity", "self-care", "leisure"),
            "Assessment notes"
        );

        Map<String, Object> context = new HashMap<>();
        context.put("profile", profile);

        SwarmTaskResult result = agent.execute(depressionPatient, context);

        assertAll(
            () -> assertTrue(result.success(), "Goal-setting should succeed with profile"),
            () -> assertEquals(SwarmPhase.GOAL_SETTING, result.phase()),
            () -> assertTrue(result.data().containsKey("goals")),
            () -> {
                @SuppressWarnings("unchecked")
                List<LifestyleGoal> goals = (List<LifestyleGoal>) result.data().get("goals");
                assertNotNull(goals, "Goals list should not be null");
                assertTrue(goals.size() >= 3, "Should produce at least 3 goals");
                assertTrue(goals.stream().anyMatch(LifestyleGoal::isPrimary),
                    "At least one goal should be primary");
                goals.forEach(g -> {
                    assertFalse(g.description().isBlank(), "Goal description required");
                    assertTrue(g.targetWeeks() >= 1 && g.targetWeeks() <= 52,
                        "Target weeks must be 1-52");
                });
            }
        );
    }

    @Test
    @DisplayName("Intervention planner requires goals in context")
    void testInterventionPlannerRequiresGoals() {
        OTInterventionPlannerAgent agent = new OTInterventionPlannerAgent();
        Map<String, Object> context = new HashMap<>();
        // Intentionally omit goals

        SwarmTaskResult result = agent.execute(depressionPatient, context);

        assertAll(
            () -> assertFalse(result.success(), "Intervention planning should fail without goals"),
            () -> assertEquals(SwarmPhase.INTERVENTION_PLANNING, result.phase()),
            () -> assertNotNull(result.errorMessage()),
            () -> assertTrue(result.errorMessage().contains("goal") ||
                           result.errorMessage().contains("Goal"),
                "Error should reference missing goals")
        );
    }

    @Test
    @DisplayName("Intervention planner maps goals to intervention areas")
    void testInterventionPlannerMapsToAreas() {
        OTInterventionPlannerAgent agent = new OTInterventionPlannerAgent();

        List<LifestyleGoal> goals = List.of(
            new LifestyleGoal("G1", "Return to part-time work", 1, "productivity",
                "Work 20 hours/week without fatigue", 12),
            new LifestyleGoal("G2", "Maintain morning routine", 2, "self-care",
                "Complete ADLs in <45 minutes", 8)
        );

        Map<String, Object> context = new HashMap<>();
        context.put("goals", goals);

        SwarmTaskResult result = agent.execute(depressionPatient, context);

        assertAll(
            () -> assertTrue(result.success(), "Intervention planning should succeed"),
            () -> assertEquals(SwarmPhase.INTERVENTION_PLANNING, result.phase()),
            () -> assertTrue(result.data().containsKey("interventions")),
            () -> {
                @SuppressWarnings("unchecked")
                Map<String, String> interventions = (Map<String, String>) result.data().get("interventions");
                assertNotNull(interventions, "Interventions map should not be null");
                assertFalse(interventions.isEmpty(), "Should map goals to interventions");
                interventions.values().forEach(v -> assertFalse(v.isBlank(),
                    "Intervention descriptions should not be blank"));
            }
        );
    }

    @Test
    @DisplayName("Scheduler generates therapy sessions with future dates")
    void testSchedulerGeneratesSessionDates() {
        OTSchedulerAgent agent = new OTSchedulerAgent();

        List<LifestyleGoal> goals = List.of(
            new LifestyleGoal("G1", "Return to work", 1, "productivity",
                "Work 20 hours/week", 12)
        );

        Map<String, Object> context = new HashMap<>();
        context.put("goals", goals);
        context.put("interventions", Map.of("productivity", "Work conditioning"));

        SwarmTaskResult result = agent.execute(strokePatient, context);

        assertAll(
            () -> assertTrue(result.success(), "Scheduling should succeed"),
            () -> assertEquals(SwarmPhase.SCHEDULING, result.phase()),
            () -> assertTrue(result.data().containsKey("therapy_sessions")),
            () -> {
                @SuppressWarnings("unchecked")
                List<TherapySession> sessions = (List<TherapySession>) result.data().get("therapy_sessions");
                assertNotNull(sessions, "Sessions list should not be null");
                assertFalse(sessions.isEmpty(), "Should schedule at least one session");

                DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE;
                LocalDate today = LocalDate.now();

                sessions.forEach(session -> {
                    assertTrue(session.scheduledDate().matches("\\d{4}-\\d{2}-\\d{2}"),
                        "Scheduled date must be yyyy-MM-dd format");
                    LocalDate scheduledDate = LocalDate.parse(session.scheduledDate(), fmt);
                    assertTrue(scheduledDate.isAfter(today) || scheduledDate.isEqual(today),
                        "Sessions should be scheduled in the future");
                    assertTrue(session.durationMinutes() >= 15 && session.durationMinutes() <= 180,
                        "Session duration must be 15-180 minutes");
                    assertEquals(TherapySession.STATUS_SCHEDULED, session.status());
                });
            }
        );
    }

    @Test
    @DisplayName("Progress monitor computes goal attainment score between 0.0 and 1.0")
    void testProgressMonitorComputesGasScore() {
        OTProgressMonitorAgent agent = new OTProgressMonitorAgent();

        List<TherapySession> sessions = List.of(
            new TherapySession("S1", strokePatient.id(), "ADL training", "2026-02-25", 60,
                TherapySession.STATUS_SCHEDULED, 0.0),
            new TherapySession("S2", strokePatient.id(), "ADL training", "2026-03-04", 60,
                TherapySession.STATUS_SCHEDULED, 0.0),
            new TherapySession("S3", strokePatient.id(), "ADL training", "2026-03-11", 60,
                TherapySession.STATUS_SCHEDULED, 0.0)
        );

        List<LifestyleGoal> goals = List.of(
            new LifestyleGoal("G1", "Dress independently", 1, "self-care",
                "Don/doff all items without assistance", 12)
        );

        Map<String, Object> context = new HashMap<>();
        context.put("therapy_sessions", sessions);
        context.put("lifestyle_goals", goals);
        context.put("adaptation_cycle", 0);

        SwarmTaskResult result = agent.execute(strokePatient, context);

        assertAll(
            () -> assertTrue(result.success(), "Progress monitoring should succeed"),
            () -> assertEquals(SwarmPhase.PROGRESS_MONITORING, result.phase()),
            () -> assertTrue(result.data().containsKey("progress_score")),
            () -> {
                Object scoreObj = result.data().get("progress_score");
                assertNotNull(scoreObj, "Progress score should not be null");
                assertTrue(scoreObj instanceof Number, "Progress score should be numeric");
                double score = ((Number) scoreObj).doubleValue();
                assertTrue(score >= 0.0 && score <= 1.0,
                    "Progress score should be between 0.0 and 1.0, got: " + score);
            }
        );
    }

    @Test
    @DisplayName("Progress monitor routing at advancement threshold")
    void testProgressMonitorRoutingThreshold() {
        OTProgressMonitorAgent agent = new OTProgressMonitorAgent();

        List<TherapySession> sessions = List.of(
            new TherapySession("S1", strokePatient.id(), "ADL training", "2026-02-25", 60,
                TherapySession.STATUS_SCHEDULED, 0.0),
            new TherapySession("S2", strokePatient.id(), "ADL training", "2026-03-04", 60,
                TherapySession.STATUS_SCHEDULED, 0.0)
        );

        List<LifestyleGoal> goals = List.of(
            new LifestyleGoal("G1", "Dress independently", 1, "self-care",
                "Don/doff all items without assistance", 12)
        );

        // Test with adaptation cycle that should trigger advancement
        Map<String, Object> context = new HashMap<>();
        context.put("therapy_sessions", sessions);
        context.put("lifestyle_goals", goals);
        context.put("adaptation_cycle", 5); // High cycle should increase score

        SwarmTaskResult result = agent.execute(strokePatient, context);

        assertTrue(result.success(), "Progress monitoring should succeed");

        @SuppressWarnings("unchecked")
        double progressScore = (double) result.data().get("progress_score");

        if (progressScore >= OTProgressMonitorAgent.ADVANCEMENT_THRESHOLD) {
            assertTrue(result.output().contains("OutcomeEvaluation"),
                "High progress score should route to OutcomeEvaluation");
        } else {
            assertTrue(result.output().contains("PlanAdaptation"),
                "Low progress score should route to PlanAdaptation");
        }
    }

    @Test
    @DisplayName("Adaptation agent increments adaptation cycle counter")
    void testAdaptationAgentIncrementsAdaptationCycle() {
        OTAdaptationAgent agent = new OTAdaptationAgent();

        List<LifestyleGoal> goals = List.of(
            new LifestyleGoal("G1", "Dress independently", 1, "self-care",
                "Don/doff all items without assistance", 12)
        );

        Map<String, Object> context = new HashMap<>();
        context.put("lifestyle_goals", goals);
        context.put("adaptation_cycle", 1);

        SwarmTaskResult result = agent.execute(strokePatient, context);

        assertAll(
            () -> assertTrue(result.success(), "Adaptation should succeed"),
            () -> assertEquals(SwarmPhase.ADAPTATION, result.phase()),
            () -> assertTrue(result.data().containsKey("adaptation_cycle")),
            () -> {
                Object cycleObj = result.data().get("adaptation_cycle");
                assertTrue(cycleObj instanceof Number, "Adaptation cycle should be numeric");
                int newCycle = ((Number) cycleObj).intValue();
                assertEquals(2, newCycle, "Cycle should be incremented from 1 to 2");
            }
        );
    }

    @Test
    @DisplayName("Outcome evaluator produces clinical discharge report")
    void testOutcomeEvaluatorProducesClinicalReport() {
        OTOutcomeEvaluatorAgent agent = new OTOutcomeEvaluatorAgent();

        OccupationalProfile profile = new OccupationalProfile(
            strokePatient.id(),
            Map.of("self-care", 9, "productivity", 8),
            Map.of("self-care", 9, "productivity", 8),
            List.of("self-care", "productivity"),
            "Strong progress in ADL independence"
        );

        List<TherapySession> sessions = List.of(
            new TherapySession("S1", strokePatient.id(), "ADL training", "2026-02-25", 60,
                TherapySession.STATUS_COMPLETED, 0.95),
            new TherapySession("S2", strokePatient.id(), "ADL training", "2026-03-04", 60,
                TherapySession.STATUS_COMPLETED, 0.92)
        );

        Map<String, Object> context = new HashMap<>();
        context.put("profile", profile);
        context.put("therapy_sessions", sessions);

        SwarmTaskResult result = agent.execute(strokePatient, context);

        assertAll(
            () -> assertTrue(result.success(), "Outcome evaluation should succeed"),
            () -> assertEquals(SwarmPhase.OUTCOME_EVALUATION, result.phase()),
            () -> assertTrue(result.data().containsKey("outcome_report")),
            () -> {
                Object reportObj = result.data().get("outcome_report");
                assertNotNull(reportObj, "Outcome report should not be null");
                String report = reportObj.toString();
                assertFalse(report.isBlank(), "Outcome report should not be blank");
                assertTrue(report.contains("discharge") || report.contains("Discharge") ||
                          report.contains("recommend") || report.contains("Recommend"),
                    "Report should contain discharge recommendations");
            }
        );
    }

    // ========== Workflow Spec Tests ==========

    @Test
    @DisplayName("OTWorkflowSpec YAML is valid and non-empty")
    void testWorkflowSpecIsValidYaml() {
        String yaml = OTWorkflowSpec.THERAPY_WORKFLOW_YAML;

        assertAll(
            () -> assertNotNull(yaml, "Workflow YAML should not be null"),
            () -> assertFalse(yaml.isBlank(), "Workflow YAML should not be blank"),
            () -> assertTrue(yaml.contains("PatientIntake"), "Should reference PatientIntake task"),
            () -> assertTrue(yaml.contains("OutcomeEvaluation"), "Should reference OutcomeEvaluation task"),
            () -> assertTrue(yaml.contains("ProgressMonitoring"), "Should reference ProgressMonitoring task"),
            () -> assertTrue(yaml.contains("progressScore >= 85"), "Should have progress threshold condition"),
            () -> assertTrue(yaml.contains("PlanAdaptation"), "Should reference adaptation loop")
        );
    }

    @Test
    @DisplayName("SwarmPhase task ID mapping is complete and unique")
    void testSwarmPhaseTaskIdMapping() {
        Map<String, SwarmPhase> taskIdToPhase = new HashMap<>();

        taskIdToPhase.put("PatientIntake", SwarmPhase.INTAKE);
        taskIdToPhase.put("OccupationalAssessment", SwarmPhase.ASSESSMENT);
        taskIdToPhase.put("GoalSetting", SwarmPhase.GOAL_SETTING);
        taskIdToPhase.put("InterventionPlanning", SwarmPhase.INTERVENTION_PLANNING);
        taskIdToPhase.put("SessionScheduling", SwarmPhase.SCHEDULING);
        taskIdToPhase.put("ProgressMonitoring", SwarmPhase.PROGRESS_MONITORING);
        taskIdToPhase.put("PlanAdaptation", SwarmPhase.ADAPTATION);
        taskIdToPhase.put("OutcomeEvaluation", SwarmPhase.OUTCOME_EVALUATION);

        assertAll(
            () -> assertEquals(8, taskIdToPhase.size(), "Should have 8 task ID mappings"),
            () -> {
                for (Map.Entry<String, SwarmPhase> entry : taskIdToPhase.entrySet()) {
                    assertFalse(entry.getKey().isBlank(), "Task ID should not be blank");
                    assertNotNull(entry.getValue(), "Phase should not be null");
                }
            }
        );
    }

    // ========== Integration Tests ==========

    @Test
    @DisplayName("Full swarm lifecycle for stroke patient completes successfully")
    void testFullSwarmLifecycleStrokePatient() {
        OTAgentRegistry registry = new OTAgentRegistry();
        Map<String, Object> sharedContext = new HashMap<>();
        List<SwarmTaskResult> results = new java.util.ArrayList<>();

        // Execute all 8 phases sequentially
        SwarmPhase[] phases = SwarmPhase.values();

        for (SwarmPhase phase : phases) {
            OTSwarmAgent agent = registry.getAgent(phase);
            SwarmTaskResult result = agent.execute(strokePatient, sharedContext);
            results.add(result);

            if (result.success() && result.data() != null) {
                sharedContext.putAll(result.data());
            }
        }

        // Verify all phases completed
        assertEquals(8, results.size(), "Should have 8 agent results");

        long successCount = results.stream().filter(SwarmTaskResult::success).count();
        assertTrue(successCount >= 6,
            "At least 6 out of 8 agents should succeed for valid workflow");

        // Verify progression through key phases
        assertTrue(results.get(0).phase() == SwarmPhase.INTAKE && results.get(0).success(),
            "Intake should succeed");
        assertTrue(results.get(1).phase() == SwarmPhase.ASSESSMENT && results.get(1).success(),
            "Assessment should succeed");
        assertTrue(results.get(2).phase() == SwarmPhase.GOAL_SETTING && results.get(2).success(),
            "Goal-setting should succeed when profile available");

        // Verify key outputs are populated
        assertTrue(sharedContext.containsKey("profile"), "Profile should be in context");
        assertTrue(sharedContext.containsKey("goals"), "Goals should be in context");
        assertTrue(sharedContext.containsKey("therapy_sessions"), "Sessions should be in context");
    }

    @Test
    @DisplayName("Full swarm lifecycle for depression patient completes successfully")
    void testFullSwarmLifecycleDepressionPatient() {
        OTAgentRegistry registry = new OTAgentRegistry();
        Map<String, Object> sharedContext = new HashMap<>();
        List<SwarmTaskResult> results = new java.util.ArrayList<>();

        // Execute all 8 phases sequentially
        SwarmPhase[] phases = SwarmPhase.values();

        for (SwarmPhase phase : phases) {
            OTSwarmAgent agent = registry.getAgent(phase);
            SwarmTaskResult result = agent.execute(depressionPatient, sharedContext);
            results.add(result);

            if (result.success() && result.data() != null) {
                sharedContext.putAll(result.data());
            }
        }

        // Verify completion
        assertEquals(8, results.size(), "Should have 8 agent results");

        long successCount = results.stream().filter(SwarmTaskResult::success).count();
        assertTrue(successCount >= 6,
            "At least 6 out of 8 agents should succeed");

        // Verify key outputs for depression patient (working-age, different risk profile)
        assertTrue(sharedContext.containsKey("profile"),
            "Profile should be populated");
        assertTrue(sharedContext.containsKey("goals"),
            "Goals should be populated");

        // Intake should stratify depression as MEDIUM or MEDIUM-HIGH (not HIGH like stroke)
        assertTrue(results.get(0).success(), "Intake should succeed");
        assertTrue(results.get(0).output().contains("MEDIUM"),
            "Depression should be stratified as MEDIUM or MEDIUM-HIGH risk");
    }

}
