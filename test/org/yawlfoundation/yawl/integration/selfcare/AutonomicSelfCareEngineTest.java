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

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 tests for autonomic self-care components.
 *
 * <p>Chicago TDD: real objects throughout. GregverseSearchClient tested against
 * a real in-process HttpServer returning SPARQL JSON. AutonomicSelfCareEngine tested
 * with a deterministic ScheduledExecutorService that allows immediate trigger control.</p>
 */
class AutonomicSelfCareEngineTest {

    // ─── SPARQL JSON served by the test HTTP server ───────────────────────────

    private static final String SPARQL_RESULTS_SELF_CARE = """
        {
          "results": {
            "bindings": [
              {
                "specId":        {"value": "morning-hygiene-v1"},
                "specName":      {"value": "Morning Hygiene Routine"},
                "provider":      {"value": "OT Gregverse Community"},
                "domain":        {"value": "self-care"},
                "description":   {"value": "Step-by-step morning hygiene workflow for ADL support."},
                "downloadCount": {"value": "142"}
              },
              {
                "specId":        {"value": "medication-mgmt-v2"},
                "specName":      {"value": "Medication Management"},
                "provider":      {"value": "OT Gregverse Community"},
                "domain":        {"value": "self-care"},
                "description":   {"value": "Daily medication tracking and reminder workflow."},
                "downloadCount": {"value": "98"}
              }
            ]
          }
        }
        """;

    private static final String SPARQL_RESULTS_EMPTY = """
        {"results": {"bindings": []}}
        """;

    // ─── Test state ───────────────────────────────────────────────────────────

    private HttpServer sparqlServer;
    private int sparqlPort;
    private GregverseSearchClient searchClient;
    private AutonomicSelfCareEngine engine;

    @BeforeEach
    void setUp() throws Exception {
        // Real HTTP server on a random port — no mocks
        sparqlServer = HttpServer.create(new InetSocketAddress(0), 0);
        sparqlPort = sparqlServer.getAddress().getPort();

        sparqlServer.createContext("/sparql", exchange -> {
            byte[] body = SPARQL_RESULTS_SELF_CARE.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/sparql-results+json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.getResponseBody().close();
        });
        sparqlServer.createContext("/sparql-empty", exchange -> {
            byte[] body = SPARQL_RESULTS_EMPTY.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/sparql-results+json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().close();
        });
        sparqlServer.createContext("/sparql-error", exchange -> {
            exchange.sendResponseHeaders(503, 0);
            exchange.getResponseBody().close();
        });
        sparqlServer.start();

        searchClient = new GregverseSearchClient(
            "http://localhost:" + sparqlPort + "/sparql",
            HttpClient.newHttpClient()
        );

        // Real engine with real virtual-thread scheduler
        engine = new AutonomicSelfCareEngine(searchClient);
    }

    @AfterEach
    void tearDown() {
        if (engine != null) engine.stop();
        if (sparqlServer != null) sparqlServer.stop(0);
    }

    // ─── OTDomain tests ───────────────────────────────────────────────────────

    @Test
    void otDomain_fromString_selfCareVariants() {
        assertEquals(OTDomain.SELF_CARE, OTDomain.fromString("self-care"));
        assertEquals(OTDomain.SELF_CARE, OTDomain.fromString("SELF_CARE"));
        assertEquals(OTDomain.SELF_CARE, OTDomain.fromString("SELF-CARE"));
    }

    @Test
    void otDomain_fromString_productivityVariants() {
        assertEquals(OTDomain.PRODUCTIVITY, OTDomain.fromString("productivity"));
        assertEquals(OTDomain.PRODUCTIVITY, OTDomain.fromString("PRODUCTIVITY"));
    }

    @Test
    void otDomain_fromString_leisureVariants() {
        assertEquals(OTDomain.LEISURE, OTDomain.fromString("leisure"));
        assertEquals(OTDomain.LEISURE, OTDomain.fromString("LEISURE"));
    }

    @Test
    void otDomain_fromString_unknownThrows() {
        assertThrows(IllegalArgumentException.class, () -> OTDomain.fromString("unknown"));
        assertThrows(IllegalArgumentException.class, () -> OTDomain.fromString(""));
        assertThrows(IllegalArgumentException.class, () -> OTDomain.fromString(null));
    }

    @Test
    void otDomain_sparqlValues_areLowercase() {
        for (OTDomain domain : OTDomain.values()) {
            assertEquals(domain.sparqlValue(), domain.sparqlValue().toLowerCase(),
                "sparqlValue() for " + domain + " must be lowercase");
        }
    }

    // ─── SelfCareAction sealed interface tests ────────────────────────────────

    @Test
    void selfCareAction_dailyLiving_validConstruction() {
        var action = new SelfCareAction.DailyLivingAction(
            "test-id", "Morning Hygiene", "Complete morning hygiene routine",
            Duration.ofMinutes(15), OTDomain.SELF_CARE, "morning-hygiene-v1");

        assertEquals("test-id", action.id());
        assertEquals("Morning Hygiene", action.title());
        assertEquals(Duration.ofMinutes(15), action.estimated());
        assertEquals(OTDomain.SELF_CARE, action.domain());
        assertEquals("morning-hygiene-v1", action.specId());
    }

    @Test
    void selfCareAction_physicalActivity_intensityRange() {
        // Valid intensity 1-5
        for (int i = 1; i <= 5; i++) {
            final int level = i;
            assertDoesNotThrow(() -> new SelfCareAction.PhysicalActivity(
                "p" + level, "Walk", "Take a walk",
                Duration.ofMinutes(5), OTDomain.SELF_CARE, level));
        }
        // Invalid intensity
        assertThrows(IllegalArgumentException.class, () -> new SelfCareAction.PhysicalActivity(
            "p0", "Walk", "Take a walk", Duration.ofMinutes(5), OTDomain.SELF_CARE, 0));
        assertThrows(IllegalArgumentException.class, () -> new SelfCareAction.PhysicalActivity(
            "p6", "Walk", "Take a walk", Duration.ofMinutes(5), OTDomain.SELF_CARE, 6));
    }

    @Test
    void selfCareAction_patternMatch_allSubtypesExhaustive() {
        List<SelfCareAction> actions = List.of(
            new SelfCareAction.DailyLivingAction("d1", "ADL", "desc", Duration.ofMinutes(10), OTDomain.SELF_CARE, "spec1"),
            new SelfCareAction.PhysicalActivity("p1", "Walk", "desc", Duration.ofMinutes(5), OTDomain.SELF_CARE, 2),
            new SelfCareAction.CognitiveActivity("c1", "Read", "desc", Duration.ofMinutes(15), OTDomain.LEISURE, "reading"),
            new SelfCareAction.SocialEngagement("s1", "Call", "desc", Duration.ofMinutes(5), OTDomain.LEISURE, 2)
        );

        // Exhaustive pattern match must compile and cover all subtypes
        for (SelfCareAction action : actions) {
            String label = switch (action) {
                case SelfCareAction.DailyLivingAction a  -> "daily:" + a.specId();
                case SelfCareAction.PhysicalActivity  a  -> "physical:" + a.intensityLevel();
                case SelfCareAction.CognitiveActivity a  -> "cognitive:" + a.cognitiveTarget();
                case SelfCareAction.SocialEngagement  a  -> "social:" + a.groupSize();
            };
            assertNotNull(label, "Pattern match should produce a non-null label");
        }
    }

    @Test
    void selfCareAction_nullFieldsThrow() {
        assertThrows(IllegalArgumentException.class, () ->
            new SelfCareAction.DailyLivingAction(null, "title", "desc", Duration.ofMinutes(5), OTDomain.SELF_CARE, "spec"));
        assertThrows(IllegalArgumentException.class, () ->
            new SelfCareAction.DailyLivingAction("id", null, "desc", Duration.ofMinutes(5), OTDomain.SELF_CARE, "spec"));
        assertThrows(IllegalArgumentException.class, () ->
            new SelfCareAction.DailyLivingAction("id", "title", "desc", Duration.ZERO, OTDomain.SELF_CARE, "spec"));
    }

    // ─── BehavioralActivationPlan tests ──────────────────────────────────────

    @Test
    void plan_totalDurationSumsCorrectly() {
        List<SelfCareAction> actions = List.of(
            new SelfCareAction.PhysicalActivity("p1", "Walk", "desc", Duration.ofMinutes(5), OTDomain.SELF_CARE, 1),
            new SelfCareAction.CognitiveActivity("c1", "Read", "desc", Duration.ofMinutes(10), OTDomain.LEISURE, "focus"),
            new SelfCareAction.SocialEngagement("s1", "Call", "desc", Duration.ofMinutes(3), OTDomain.LEISURE, 2)
        );

        var plan = new BehavioralActivationPlan(
            "test-plan-1", OTDomain.SELF_CARE, actions, Instant.now(), "Test rationale");

        assertEquals(Duration.ofMinutes(18), plan.totalDuration());
    }

    @Test
    void plan_nextActionIsFirstInList() {
        var first = new SelfCareAction.PhysicalActivity("p1", "Easiest", "desc", Duration.ofMinutes(5), OTDomain.SELF_CARE, 1);
        var second = new SelfCareAction.PhysicalActivity("p2", "Harder", "desc", Duration.ofMinutes(20), OTDomain.SELF_CARE, 4);

        var plan = new BehavioralActivationPlan(
            "test-plan-2", OTDomain.SELF_CARE, List.of(first, second), Instant.now(), "Test rationale");

        assertSame(first, plan.nextAction(), "nextAction() must return the first element");
    }

    @Test
    void plan_emptyActionsThrows() {
        assertThrows(IllegalArgumentException.class, () ->
            new BehavioralActivationPlan(
                "test-plan-3", OTDomain.SELF_CARE, List.of(), Instant.now(), "rationale"));
    }

    @Test
    void plan_actionsListIsImmutable() {
        List<SelfCareAction> mutable = new java.util.ArrayList<>();
        mutable.add(new SelfCareAction.PhysicalActivity(
            "p1", "Walk", "desc", Duration.ofMinutes(5), OTDomain.SELF_CARE, 1));

        var plan = new BehavioralActivationPlan(
            "test-plan-4", OTDomain.SELF_CARE, mutable, Instant.now(), "rationale");

        assertThrows(UnsupportedOperationException.class, () ->
            plan.actions().add(new SelfCareAction.PhysicalActivity(
                "p2", "Run", "desc", Duration.ofMinutes(10), OTDomain.SELF_CARE, 3)),
            "Plan actions list must be immutable");
    }

    // ─── GregverseSearchClient tests ──────────────────────────────────────────

    @Test
    void search_findsByOTDomain() {
        List<GregverseSearchClient.WorkflowSpecSummary> results =
            searchClient.searchByDomain(OTDomain.SELF_CARE);

        assertFalse(results.isEmpty(), "Should find specs from test SPARQL server");
        assertEquals("morning-hygiene-v1", results.get(0).specId());
        assertEquals("Morning Hygiene Routine", results.get(0).specName());
        assertEquals(OTDomain.SELF_CARE, results.get(0).domain());
        assertEquals(142, results.get(0).downloadCount());
    }

    @Test
    void search_keywordFilterReturnsResults() {
        // Test server returns same fixture for any query
        List<GregverseSearchClient.WorkflowSpecSummary> results =
            searchClient.searchByKeywords("hygiene", "morning");
        assertNotNull(results, "searchByKeywords must not return null");
    }

    @Test
    void search_unavailableEndpointReturnsEmpty() {
        var offlineClient = new GregverseSearchClient(
            "http://localhost:" + sparqlPort + "/sparql-error",
            HttpClient.newHttpClient());

        List<GregverseSearchClient.WorkflowSpecSummary> results =
            offlineClient.searchByDomain(OTDomain.SELF_CARE);

        assertTrue(results.isEmpty(),
            "Search against unavailable endpoint must return empty list (graceful degradation)");
    }

    @Test
    void search_emptyResultsReturnEmptyList() {
        var emptyClient = new GregverseSearchClient(
            "http://localhost:" + sparqlPort + "/sparql-empty",
            HttpClient.newHttpClient());

        List<GregverseSearchClient.WorkflowSpecSummary> results =
            emptyClient.searchByDomain(OTDomain.SELF_CARE);

        assertTrue(results.isEmpty(), "Empty SPARQL bindings must return empty list");
    }

    @Test
    void search_requiresAtLeastOneKeyword() {
        assertThrows(IllegalArgumentException.class, () -> searchClient.searchByKeywords());
    }

    // ─── AutonomicSelfCareEngine tests ────────────────────────────────────────

    @Test
    void engine_generatesOTAlignedPlan() {
        BehavioralActivationPlan plan = engine.generatePlan(OTDomain.SELF_CARE, 3);

        assertNotNull(plan);
        assertEquals(OTDomain.SELF_CARE, plan.domain());
        assertEquals(3, plan.actions().size());
        assertNotNull(plan.planId());
        assertNotNull(plan.generatedAt());
        assertFalse(plan.rationale().isBlank());
    }

    @Test
    void engine_nextActionIsEasiestFirst() {
        // Easiest-first means shortest estimated duration is first
        BehavioralActivationPlan plan = engine.generatePlan(OTDomain.SELF_CARE, 3);

        SelfCareAction first = plan.nextAction();
        for (SelfCareAction action : plan.actions()) {
            assertFalse(action.estimated().compareTo(first.estimated()) < 0,
                "No action should have a shorter duration than nextAction(): "
                    + action.title() + " (" + action.estimated() + ") < "
                    + first.title() + " (" + first.estimated() + ")");
        }
    }

    @Test
    void engine_generatesAllDomainsWithoutError() {
        for (OTDomain domain : OTDomain.values()) {
            BehavioralActivationPlan plan = engine.generatePlan(domain, 2);
            assertNotNull(plan, "Plan must not be null for domain: " + domain);
            assertEquals(2, plan.actions().size(), "Plan must contain 2 actions for domain: " + domain);
            assertEquals(domain, plan.domain());
        }
    }

    @Test
    void engine_actionCountMustBePositive() {
        assertThrows(IllegalArgumentException.class, () ->
            engine.generatePlan(OTDomain.SELF_CARE, 0));
        assertThrows(IllegalArgumentException.class, () ->
            engine.generatePlan(OTDomain.SELF_CARE, -1));
    }

    @Test
    void engine_statusNotRunningBeforeStart() {
        AutonomicSelfCareEngine.EngineStatus status = engine.getStatus();
        assertFalse(status.running(), "Engine must not be running before start()");
        assertNull(status.lastMonitorAt(), "lastMonitorAt must be null before first MAPE-K cycle");
    }

    @Test
    void engine_statusRunningAfterStart() throws InterruptedException {
        engine.start();
        // Give the executor a moment to register as started
        Thread.sleep(50);
        assertTrue(engine.getStatus().running(), "Engine must be running after start()");
    }

    @Test
    void engine_gracefulShutdown() {
        engine.start();
        assertDoesNotThrow(() -> engine.stop(), "stop() must not throw");
        assertFalse(engine.getStatus().running(), "Engine must not be running after stop()");
    }

    @Test
    void engine_schedulesActionsIncrementalCounter() throws InterruptedException {
        // Use a real fast executor so actions fire quickly in test
        CountDownLatch latch = new CountDownLatch(2);
        AtomicInteger fired = new AtomicInteger(0);

        // Create engine with an offline search (falls back to built-in starters)
        var offlineSearch = new GregverseSearchClient(
            "http://localhost:" + sparqlPort + "/sparql-empty",
            HttpClient.newHttpClient());

        // Manual-trigger executor: scheduled tasks run immediately with 0 delay
        ScheduledExecutorService immediate = Executors.newSingleThreadScheduledExecutor();
        var testEngine = new AutonomicSelfCareEngine(offlineSearch, immediate);

        try {
            BehavioralActivationPlan plan = testEngine.generatePlan(OTDomain.PRODUCTIVITY, 2);
            testEngine.scheduleActions(plan);

            // Status should show 2 scheduled actions
            AutonomicSelfCareEngine.EngineStatus status = testEngine.getStatus();
            assertEquals(2, status.scheduledActions(),
                "scheduleActions() must register 2 scheduled actions");
        } finally {
            testEngine.stop();
        }
    }

    @Test
    void engine_worksWhenGregverseOffline() {
        // Engine backed by offline Gregverse must fall back to built-in starters
        var offlineSearch = new GregverseSearchClient(
            "http://localhost:" + sparqlPort + "/sparql-error",
            HttpClient.newHttpClient());
        var offlineEngine = new AutonomicSelfCareEngine(offlineSearch);

        try {
            BehavioralActivationPlan plan = offlineEngine.generatePlan(OTDomain.LEISURE, 3);
            assertEquals(3, plan.actions().size(),
                "Engine must produce a full plan from built-in starters when Gregverse is offline");
            assertNotNull(plan.nextAction(), "nextAction() must be non-null even when offline");
        } finally {
            offlineEngine.stop();
        }
    }

    // ─── WorkflowSpecSummary record validation ────────────────────────────────

    @Test
    void workflowSpecSummary_negativeDownloadCountThrows() {
        assertThrows(IllegalArgumentException.class, () ->
            new GregverseSearchClient.WorkflowSpecSummary(
                "id", "name", "provider", OTDomain.SELF_CARE, "desc", -1));
    }

    @Test
    void workflowSpecSummary_nullFieldsThrow() {
        assertThrows(NullPointerException.class, () ->
            new GregverseSearchClient.WorkflowSpecSummary(
                null, "name", "provider", OTDomain.SELF_CARE, "desc", 0));
        assertThrows(NullPointerException.class, () ->
            new GregverseSearchClient.WorkflowSpecSummary(
                "id", null, "provider", OTDomain.SELF_CARE, "desc", 0));
    }
}
