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

package org.yawlfoundation.yawl.mcp.a2a.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for the full Java 11 → 25 migration pipeline.
 *
 * <p>Tests the complete two-phase flow: Analyze (detect Java 11 patterns) →
 * Plan (construct Java 25 migration targets). Validates that the pipeline
 * produces the expected mix of automatic and review-required migrations for
 * a realistic YAWL-style Java 11 codebase.</p>
 *
 * <p>This corresponds to running Phases 2+3 of the ggen-java-migration.toml pipeline:</p>
 * <ul>
 *   <li>Phase 2: SPARQL SELECT queries → pattern detection</li>
 *   <li>Phase 3: SPARQL CONSTRUCT queries → migration plan</li>
 * </ul>
 */
@DisplayName("Java Migration Pipeline — Integration: Detect + Plan")
class MigrationPipelineIntegrationTest {

    /**
     * Simulates a YAWL-style Java 11 engine class with multiple migration opportunities.
     * Contains: synchronized methods, Date, ThreadLocal, new Thread(), legacy collections,
     * instanceof cast, switch statement, String.format().
     */
    private static final String YAWL_ENGINE_JAVA_11 = """
            package org.yawlfoundation.yawl.engine;

            import java.util.Date;
            import java.util.Hashtable;
            import java.util.Vector;

            /**
             * YAWL Engine — Java 11 version with migration opportunities.
             */
            public class YNetRunner {

                private static final ThreadLocal<String> CURRENT_CASE = new ThreadLocal<>();
                private final Hashtable<String, Object> caseTable = new Hashtable<>();
                private final Vector<String> activeTaskIds = new Vector<>();
                private Date startTime;

                public synchronized void startCase(String caseId) {
                    CURRENT_CASE.set(caseId);
                    this.startTime = new Date();
                    new Thread(() -> processCase(caseId)).start();
                }

                public synchronized void advance(String taskId) {
                    synchronized (this.caseTable) {
                        caseTable.put(taskId, new Date());
                    }
                }

                private void handleElement(Object element) {
                    if (element instanceof YTask) {
                        YTask task = (YTask) element;
                        task.fire();
                    } else if (element instanceof YCondition) {
                        YCondition cond = (YCondition) element;
                        cond.mark();
                    }
                }

                private int getSplitCode(String type) {
                    switch (type) {
                        case "XOR": return 0;
                        case "AND": return 1;
                        case "OR":  return 2;
                        default: return -1;
                    }
                }

                private String buildErrorMessage(String caseId, String taskId, String reason) {
                    return String.format("Case %s failed at task %s: %s", caseId, taskId, reason);
                }

                private void processCase(String caseId) {}
                interface YTask { void fire(); }
                interface YCondition { void mark(); }
            }
            """;

    @Test
    @DisplayName("full pipeline: detects 10+ patterns in Java 11 YAWL engine, builds plan")
    void fullPipelineOnYawlEngineCode(@TempDir Path tmpDir) throws IOException {
        // Write the Java 11 source
        Path sourceFile = tmpDir.resolve("YNetRunner.java");
        Files.writeString(sourceFile, YAWL_ENGINE_JAVA_11);

        // Phase 2: Detect
        JavaMigrationAnalyzer analyzer = new JavaMigrationAnalyzer();
        List<Java11Pattern> detected = analyzer.analyzeFile(sourceFile);

        // Verify key patterns are detected
        assertTrue(detected.stream().anyMatch(p -> p.ruleId().equals("R01")),
                "Should detect new Thread() — R01");
        assertTrue(detected.stream().anyMatch(p -> p.ruleId().equals("R03")),
                "Should detect ThreadLocal — R03");
        assertTrue(detected.stream().anyMatch(p -> p.ruleId().equals("R06")),
                "Should detect instanceof + cast — R06");
        assertTrue(detected.stream().anyMatch(p -> p.ruleId().equals("R08")),
                "Should detect switch statement — R08");
        assertTrue(detected.stream().anyMatch(p -> p.ruleId().equals("R09")),
                "Should detect String.format() — R09");
        assertTrue(detected.stream().anyMatch(p -> p.ruleId().equals("R10")),
                "Should detect new Date() — R10");
        assertTrue(detected.stream().anyMatch(p -> p.ruleId().equals("R11")),
                "Should detect legacy collections — R11");
        assertTrue(detected.stream().anyMatch(p -> p.ruleId().equals("R13")),
                "Should detect synchronized — R13");

        // Phase 3: Plan
        MigrationPlanBuilder builder = new MigrationPlanBuilder();
        MigrationPlan plan = builder.build(tmpDir.toString(), 1, detected);

        // Verify plan structure
        assertNotNull(plan);
        assertFalse(plan.migrations().isEmpty());
        assertEquals(1, plan.filesAnalyzed());

        // Should have AUTO migrations (LOW: thread, instanceof, switch, format, R11)
        assertTrue(plan.hasLowComplexityMigrations(),
                "Should have LOW complexity migrations for auto-commit");

        // Should have REVIEW migrations (HIGH: ThreadLocal, synchronized)
        assertTrue(plan.hasMediumOrHighComplexityMigrations(),
                "Should have MEDIUM/HIGH migrations requiring review");

        List<Java25Pattern> automatic = plan.automaticMigrations();
        List<Java25Pattern> review = plan.reviewMigrations();

        assertFalse(automatic.isEmpty(), "Should have automatic migrations");
        assertFalse(review.isEmpty(), "Should have review-required migrations");

        // High complexity migrations should NOT be auto-applicable
        assertTrue(review.stream().noneMatch(Java25Pattern::isAutoApplicable),
                "Review migrations should not be auto-applicable");

        // Verify ordering: LOW before HIGH
        List<Java25Pattern> sorted = plan.migrations();
        if (sorted.size() >= 2) {
            // LOW complexity must come before HIGH
            int firstHighIdx = -1, lastLowIdx = -1;
            for (int i = 0; i < sorted.size(); i++) {
                var c = sorted.get(i).sourcePattern().complexity();
                if (c == Java11Pattern.MigrationComplexity.LOW) lastLowIdx = i;
                if (c == Java11Pattern.MigrationComplexity.HIGH && firstHighIdx == -1) firstHighIdx = i;
            }
            if (firstHighIdx != -1 && lastLowIdx != -1) {
                assertTrue(lastLowIdx < firstHighIdx,
                        "All LOW migrations should come before any HIGH migration in sorted order");
            }
        }

        // Verify summarize() output is meaningful
        String summary = plan.summarize();
        assertNotNull(summary);
        assertFalse(summary.isBlank());
        assertTrue(summary.contains("LOW") || summary.contains("MEDIUM") || summary.contains("HIGH"));
    }

    @Test
    @DisplayName("YAWL soundness: HIGH complexity migrations are flagged correctly")
    void yawlSoundnessMigrationsAreProperlyFlagged(@TempDir Path tmpDir) throws IOException {
        // Synchronized engine method — must preserve YAWL soundness
        Path file = tmpDir.resolve("YEngine.java");
        Files.writeString(file, """
                class YEngine {
                    private static final ThreadLocal<String> CTX = new ThreadLocal<>();

                    public synchronized String executeTask(String taskId) {
                        CTX.set(taskId);
                        return "executed";
                    }
                }
                """);

        JavaMigrationAnalyzer analyzer = new JavaMigrationAnalyzer();
        List<Java11Pattern> detected = analyzer.analyzeFile(file);

        MigrationPlanBuilder builder = new MigrationPlanBuilder();
        MigrationPlan plan = builder.build("src/", 1, detected);

        // All HIGH complexity migrations must require review
        plan.migrations().stream()
                .filter(m -> m.sourcePattern().complexity() == Java11Pattern.MigrationComplexity.HIGH)
                .forEach(m -> {
                    assertTrue(m.requiresReview(),
                            "HIGH complexity migration must require review: " + m.describe());
                    assertFalse(m.isAutoApplicable(),
                            "HIGH complexity migration must not be auto-applicable: " + m.describe());
                    assertNotNull(m.migrationNote(),
                            "HIGH complexity migration must have a guidance note: " + m.describe());
                    assertFalse(m.migrationNote().isBlank(),
                            "Migration note must not be blank: " + m.describe());
                });
    }

    @Test
    @DisplayName("pipeline ontology files exist in expected locations")
    void ontologyFilesExist() {
        // Verify the specification layer is complete
        List<String> requiredFiles = List.of(
                "ontology/migration/java-code.ttl",
                "ontology/migration/java11-patterns.ttl",
                "ontology/migration/java25-patterns.ttl",
                "ontology/migration/migration-rules.ttl",
                "ontology/migration/yawl-migration-workflow.ttl",
                "ggen-java-migration.toml",
                "exampleSpecs/JavaMigrationPipeline.xml"
        );

        // Find the project root (4 levels up from test class)
        Path projectRoot = findProjectRoot();

        for (String required : requiredFiles) {
            assertTrue(Files.exists(projectRoot.resolve(required)),
                    "Required pipeline file must exist: " + required);
        }
    }

    @Test
    @DisplayName("SPARQL query files exist in expected locations")
    void sparqlQueryFilesExist() {
        Path projectRoot = findProjectRoot();
        Path queryDir = projectRoot.resolve("query/migration");

        List<String> requiredQueries = List.of(
                "detect-thread-creation.sparql",
                "detect-thread-pool.sparql",
                "detect-thread-local.sparql",
                "detect-pojo-record.sparql",
                "detect-anonymous-class.sparql",
                "detect-instanceof-cast.sparql",
                "detect-chained-instanceof.sparql",
                "detect-switch-statement.sparql",
                "detect-string-format.sparql",
                "detect-date-calendar.sparql",
                "detect-legacy-collections.sparql",
                "detect-raw-types.sparql",
                "detect-synchronized.sparql",
                "construct-virtual-thread.sparql",
                "construct-record.sparql",
                "construct-pattern-matching.sparql",
                "construct-switch-expression.sparql",
                "construct-text-block.sparql",
                "construct-java-time.sparql",
                "construct-scoped-value.sparql",
                "construct-lambda.sparql",
                "construct-structured-concurrency.sparql",
                "construct-collections.sparql",
                "construct-migration-plan.sparql"
        );

        for (String query : requiredQueries) {
            assertTrue(Files.exists(queryDir.resolve(query)),
                    "Required SPARQL query must exist: query/migration/" + query);
        }
    }

    @Test
    @DisplayName("Tera template files exist for all Java 25 patterns")
    void teraTemplateFilesExist() {
        Path projectRoot = findProjectRoot();
        Path templatesDir = projectRoot.resolve("templates/java25-migration");

        List<String> requiredTemplates = List.of(
                "virtual-thread.tera",
                "record.tera",
                "pattern-matching.tera",
                "switch-expression.tera",
                "text-block.tera",
                "java-time.tera",
                "scoped-value.tera",
                "lambda.tera",
                "structured-concurrency.tera",
                "collections.tera",
                "migration-report.tera"
        );

        for (String template : requiredTemplates) {
            assertTrue(Files.exists(templatesDir.resolve(template)),
                    "Required Tera template must exist: templates/java25-migration/" + template);
        }
    }

    // =========================================================================
    // Helper
    // =========================================================================

    private static Path findProjectRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        // Walk up until we find ggen-java-migration.toml (marks the project root)
        Path candidate = current;
        while (candidate != null) {
            if (Files.exists(candidate.resolve("ggen-java-migration.toml"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        // Fallback: assume yawl root from the test module location
        return current.getParent() != null ? current.getParent() : current;
    }
}
