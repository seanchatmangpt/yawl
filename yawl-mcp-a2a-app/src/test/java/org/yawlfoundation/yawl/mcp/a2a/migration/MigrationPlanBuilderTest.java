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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MigrationPlanBuilder} — Phase 3 (Plan) of the Java migration pipeline.
 *
 * <p>Tests verify that CONSTRUCT rules correctly map Java 11 patterns to Java 25 targets,
 * and that the resulting {@link MigrationPlan} has correct aggregation and ordering.
 * Corresponds to {@code query/migration/construct-*.sparql} behavior and
 * {@code ontology/migration/migration-rules.ttl} rule definitions.</p>
 */
@DisplayName("MigrationPlanBuilder — Phase 3: Construct Java 25 migration plan")
class MigrationPlanBuilderTest {

    private MigrationPlanBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new MigrationPlanBuilder();
    }

    // =========================================================================
    // CONSTRUCT rules: Java 11 → Java 25
    // =========================================================================

    @Nested
    @DisplayName("R01: Thread → Virtual Thread")
    class R01Tests {

        @Test
        @DisplayName("constructs VirtualThreadPattern from ThreadCreationPattern")
        void constructsVirtualThread() {
            Java11Pattern src = threadCreationPattern("R01");
            MigrationPlan plan = builder.build("src/", 1, List.of(src));

            Java25Pattern target = findByRule(plan, "R01");
            assertNotNull(target, "R01 should produce a migration target");
            assertEquals("VirtualThreadPattern", target.patternType());
            assertFalse(target.requiresReview());
            assertTrue(target.isAutoApplicable());
            assertTrue(target.renderedCode().contains("ofVirtual"),
                    "Rendered code should reference Thread.ofVirtual()");
        }

        @Test
        @DisplayName("R01 migration requires java.lang.Thread import")
        void r01RequiresThreadImport() {
            Java11Pattern src = threadCreationPattern("R01");
            MigrationPlan plan = builder.build("src/", 1, List.of(src));

            Java25Pattern target = findByRule(plan, "R01");
            assertNotNull(target);
            assertTrue(target.requiredImports().contains("java.lang.Thread"));
        }
    }

    @Nested
    @DisplayName("R03: ThreadLocal → ScopedValue (HIGH complexity)")
    class R03Tests {

        @Test
        @DisplayName("constructs ScopedValuePattern — flags requiresReview")
        void constructsScopedValue() {
            Java11Pattern src = pattern("R03", "ThreadLocalPattern",
                    Java11Pattern.MigrationComplexity.HIGH);
            MigrationPlan plan = builder.build("src/", 1, List.of(src));

            Java25Pattern target = findByRule(plan, "R03");
            assertNotNull(target, "R03 should produce a migration target");
            assertEquals("ScopedValuePattern", target.patternType());
            assertTrue(target.requiresReview(), "R03 is HIGH — review required");
            assertFalse(target.isAutoApplicable(), "R03 should not auto-apply");
            assertNotNull(target.migrationNote(), "R03 should have a migration note");
            assertTrue(target.migrationNote().contains("ThreadLocal"),
                    "Migration note should explain ThreadLocal → ScopedValue change");
        }
    }

    @Nested
    @DisplayName("R04: Getter/Setter POJO → Record")
    class R04Tests {

        @Test
        @DisplayName("constructs RecordPattern from GetterSetterPojoPattern")
        void constructsRecord() {
            Java11Pattern src = pattern("R04", "GetterSetterPojoPattern",
                    Java11Pattern.MigrationComplexity.LOW);
            MigrationPlan plan = builder.build("src/", 1, List.of(src));

            Java25Pattern target = findByRule(plan, "R04");
            assertNotNull(target);
            assertEquals("RecordPattern", target.patternType());
            assertEquals("templates/java25-migration/record.tera", target.templateFile());
            assertFalse(target.requiresReview());
            assertTrue(target.isAutoApplicable());
        }
    }

    @Nested
    @DisplayName("R06: instanceof + Cast → Pattern Matching")
    class R06Tests {

        @Test
        @DisplayName("constructs PatternMatchingInstanceofPattern")
        void constructsPatternMatching() {
            Java11Pattern src = pattern("R06", "InstanceofCastPattern",
                    Java11Pattern.MigrationComplexity.LOW);
            MigrationPlan plan = builder.build("src/", 1, List.of(src));

            Java25Pattern target = findByRule(plan, "R06");
            assertNotNull(target);
            assertEquals("PatternMatchingInstanceofPattern", target.patternType());
            assertTrue(target.renderedCode().contains("instanceof"),
                    "Rendered code should show pattern matching instanceof");
        }
    }

    @Nested
    @DisplayName("R08: Switch Statement → Switch Expression")
    class R08Tests {

        @Test
        @DisplayName("constructs SwitchExpressionPattern")
        void constructsSwitchExpression() {
            Java11Pattern src = pattern("R08", "SwitchStatementPattern",
                    Java11Pattern.MigrationComplexity.LOW);
            MigrationPlan plan = builder.build("src/", 1, List.of(src));

            Java25Pattern target = findByRule(plan, "R08");
            assertNotNull(target);
            assertEquals("SwitchExpressionPattern", target.patternType());
            assertTrue(target.renderedCode().contains("->"),
                    "Rendered code should use arrow case syntax");
        }
    }

    @Nested
    @DisplayName("R10: Date/Calendar → java.time")
    class R10Tests {

        @Test
        @DisplayName("constructs InstantPattern from Date usage")
        void constructsInstantFromDate() {
            Java11Pattern src = new Java11Pattern(
                    "R10", "DateCalendarPattern", "src/Engine.java", 42,
                    Java11Pattern.MigrationComplexity.MEDIUM, "new Date()");
            MigrationPlan plan = builder.build("src/", 1, List.of(src));

            Java25Pattern target = findByRule(plan, "R10");
            assertNotNull(target);
            assertEquals("InstantPattern", target.patternType());
            assertTrue(target.renderedCode().contains("Instant.now()"),
                    "Date → Instant.now()");
            assertTrue(target.requiredImports().contains("java.time.Instant"));
        }

        @Test
        @DisplayName("constructs LocalDateTimePattern from Calendar usage")
        void constructsLocalDateTimeFromCalendar() {
            Java11Pattern src = new Java11Pattern(
                    "R10", "DateCalendarPattern", "src/Engine.java", 42,
                    Java11Pattern.MigrationComplexity.MEDIUM, "Calendar.getInstance()");
            MigrationPlan plan = builder.build("src/", 1, List.of(src));

            Java25Pattern target = findByRule(plan, "R10");
            assertNotNull(target);
            assertTrue(target.renderedCode().contains("LocalDateTime.now()"),
                    "Calendar → LocalDateTime.now()");
            assertTrue(target.requiredImports().contains("java.time.LocalDateTime"));
        }

        @Test
        @DisplayName("constructs DateTimeFormatterPattern from SimpleDateFormat usage")
        void constructsDateTimeFormatterFromSdf() {
            Java11Pattern src = new Java11Pattern(
                    "R10", "DateCalendarPattern", "src/Fmt.java", 10,
                    Java11Pattern.MigrationComplexity.MEDIUM, "new SimpleDateFormat(\"yyyy-MM-dd\")");
            MigrationPlan plan = builder.build("src/", 1, List.of(src));

            Java25Pattern target = findByRule(plan, "R10");
            assertNotNull(target);
            assertTrue(target.renderedCode().contains("DateTimeFormatter"),
                    "SimpleDateFormat → DateTimeFormatter");
            assertTrue(target.requiredImports().contains("java.time.format.DateTimeFormatter"));
        }
    }

    @Nested
    @DisplayName("R13: Synchronized → StructuredTaskScope (HIGH complexity)")
    class R13Tests {

        @Test
        @DisplayName("constructs StructuredTaskScopePattern — flags requiresReview")
        void constructsStructuredConcurrency() {
            Java11Pattern src = pattern("R13", "SynchronizedPattern",
                    Java11Pattern.MigrationComplexity.HIGH);
            MigrationPlan plan = builder.build("src/", 1, List.of(src));

            Java25Pattern target = findByRule(plan, "R13");
            assertNotNull(target, "R13 should produce a migration target");
            assertEquals("StructuredTaskScopePattern", target.patternType());
            assertTrue(target.requiresReview(), "R13 is HIGH — review required");
            assertFalse(target.isAutoApplicable());
            assertTrue(target.migrationNote().contains("soundness"),
                    "Migration note should mention YAWL soundness requirements");
        }

        @Test
        @DisplayName("R13 requires StructuredTaskScope import")
        void r13RequiresImport() {
            Java11Pattern src = pattern("R13", "SynchronizedPattern",
                    Java11Pattern.MigrationComplexity.HIGH);
            MigrationPlan plan = builder.build("src/", 1, List.of(src));

            Java25Pattern target = findByRule(plan, "R13");
            assertNotNull(target);
            assertTrue(target.requiredImports()
                    .contains("java.util.concurrent.StructuredTaskScope"));
        }
    }

    // =========================================================================
    // MigrationPlan aggregation
    // =========================================================================

    @Nested
    @DisplayName("MigrationPlan aggregation and statistics")
    class PlanAggregationTests {

        @Test
        @DisplayName("empty pattern list produces empty migration plan")
        void emptyPatternsProducesEmptyPlan() {
            MigrationPlan plan = builder.build("src/", 10, List.of());

            assertEquals("src/", plan.codebasePath());
            assertEquals(10, plan.filesAnalyzed());
            assertTrue(plan.migrations().isEmpty());
            assertEquals(0, plan.lowCount());
            assertEquals(0, plan.mediumCount());
            assertEquals(0, plan.highCount());
        }

        @Test
        @DisplayName("counts migrations by complexity correctly")
        void countsComplexityCorrectly() {
            List<Java11Pattern> patterns = List.of(
                    pattern("R01", "ThreadCreationPattern", Java11Pattern.MigrationComplexity.LOW),
                    pattern("R04", "GetterSetterPojoPattern", Java11Pattern.MigrationComplexity.LOW),
                    pattern("R10", "DateCalendarPattern", Java11Pattern.MigrationComplexity.MEDIUM),
                    pattern("R03", "ThreadLocalPattern", Java11Pattern.MigrationComplexity.HIGH),
                    pattern("R13", "SynchronizedPattern", Java11Pattern.MigrationComplexity.HIGH)
            );

            MigrationPlan plan = builder.build("src/", 5, patterns);

            assertEquals(2, plan.lowCount());
            assertEquals(1, plan.mediumCount());
            assertEquals(2, plan.highCount());
            assertEquals(5, plan.migrations().size());
        }

        @Test
        @DisplayName("automaticMigrations() returns only non-review migrations")
        void automaticMigrationsExcludesHighComplexity() {
            List<Java11Pattern> patterns = List.of(
                    pattern("R01", "ThreadCreationPattern", Java11Pattern.MigrationComplexity.LOW),
                    pattern("R03", "ThreadLocalPattern", Java11Pattern.MigrationComplexity.HIGH),
                    pattern("R13", "SynchronizedPattern", Java11Pattern.MigrationComplexity.HIGH)
            );

            MigrationPlan plan = builder.build("src/", 3, patterns);

            List<Java25Pattern> automatic = plan.automaticMigrations();
            List<Java25Pattern> review = plan.reviewMigrations();

            assertEquals(1, automatic.size(), "Only R01 (LOW) should be automatic");
            assertEquals(2, review.size(), "R03 and R13 (HIGH) require review");
            assertTrue(automatic.stream().allMatch(Java25Pattern::isAutoApplicable));
            assertTrue(review.stream().allMatch(Java25Pattern::requiresReview));
        }

        @Test
        @DisplayName("hasLowComplexityMigrations() is true when LOW migrations exist")
        void hasLowComplexityMigrationsFlag() {
            MigrationPlan plan = builder.build("src/", 1, List.of(
                    pattern("R01", "ThreadCreationPattern", Java11Pattern.MigrationComplexity.LOW)
            ));

            assertTrue(plan.hasLowComplexityMigrations());
            assertFalse(plan.hasMediumOrHighComplexityMigrations());
        }

        @Test
        @DisplayName("byComplexity() groups migrations correctly")
        void byComplexityGroupsCorrectly() {
            List<Java11Pattern> patterns = List.of(
                    pattern("R01", "ThreadCreationPattern", Java11Pattern.MigrationComplexity.LOW),
                    pattern("R10", "DateCalendarPattern", Java11Pattern.MigrationComplexity.MEDIUM),
                    pattern("R13", "SynchronizedPattern", Java11Pattern.MigrationComplexity.HIGH)
            );

            MigrationPlan plan = builder.build("src/", 3, patterns);
            var groups = plan.byComplexity();

            assertEquals(1, groups.get(Java11Pattern.MigrationComplexity.LOW).size());
            assertEquals(1, groups.get(Java11Pattern.MigrationComplexity.MEDIUM).size());
            assertEquals(1, groups.get(Java11Pattern.MigrationComplexity.HIGH).size());
        }

        @Test
        @DisplayName("migrations are sorted LOW → MEDIUM → HIGH by complexity")
        void migrationsAreSortedByComplexity() {
            List<Java11Pattern> patterns = List.of(
                    pattern("R13", "SynchronizedPattern", Java11Pattern.MigrationComplexity.HIGH),
                    pattern("R10", "DateCalendarPattern", Java11Pattern.MigrationComplexity.MEDIUM),
                    pattern("R01", "ThreadCreationPattern", Java11Pattern.MigrationComplexity.LOW)
            );

            MigrationPlan plan = builder.build("src/", 3, patterns);
            List<Java25Pattern> sorted = plan.migrations();

            assertEquals(Java11Pattern.MigrationComplexity.LOW,
                    sorted.get(0).sourcePattern().complexity(),
                    "First migration should be LOW complexity");
            assertEquals(Java11Pattern.MigrationComplexity.MEDIUM,
                    sorted.get(1).sourcePattern().complexity());
            assertEquals(Java11Pattern.MigrationComplexity.HIGH,
                    sorted.get(2).sourcePattern().complexity(),
                    "Last migration should be HIGH complexity");
        }

        @Test
        @DisplayName("summarize() produces non-null human-readable summary")
        void summarizeProducesReadableSummary() {
            List<Java11Pattern> patterns = List.of(
                    pattern("R01", "ThreadCreationPattern", Java11Pattern.MigrationComplexity.LOW),
                    pattern("R13", "SynchronizedPattern", Java11Pattern.MigrationComplexity.HIGH)
            );

            MigrationPlan plan = builder.build("src/main", 42, patterns);
            String summary = plan.summarize();

            assertNotNull(summary);
            assertTrue(summary.contains("src/main"));
            assertTrue(summary.contains("42"));
            assertTrue(summary.contains("2 total") || summary.contains("Migrations: 2"));
        }

        @Test
        @DisplayName("targetFile() and targetLine() return correct source location")
        void targetFileAndLineAreCorrect() {
            Java11Pattern src = new Java11Pattern(
                    "R01", "ThreadCreationPattern",
                    "src/org/yawl/engine/YNetRunner.java", 427,
                    Java11Pattern.MigrationComplexity.LOW,
                    "new Thread(() -> advance()).start();"
            );

            MigrationPlan plan = builder.build("src/", 1, List.of(src));
            Java25Pattern target = findByRule(plan, "R01");

            assertNotNull(target);
            assertEquals("src/org/yawl/engine/YNetRunner.java", target.targetFile());
            assertEquals(427, target.targetLine());
        }
    }

    // =========================================================================
    // Java25Pattern record properties
    // =========================================================================

    @Nested
    @DisplayName("Java25Pattern record")
    class Java25PatternTests {

        @Test
        @DisplayName("describe() produces human-readable string")
        void describeProducesReadableString() {
            Java11Pattern src = new Java11Pattern(
                    "R04", "GetterSetterPojoPattern",
                    "src/WorkItemData.java", 12,
                    Java11Pattern.MigrationComplexity.LOW, "class WorkItemData { ... }");
            MigrationPlan plan = builder.build("src/", 1, List.of(src));
            Java25Pattern target = findByRule(plan, "R04");

            assertNotNull(target);
            String desc = target.describe();
            assertTrue(desc.contains("GetterSetterPojoPattern"));
            assertTrue(desc.contains("RecordPattern"));
            assertTrue(desc.contains("WorkItemData.java"));
        }

        @Test
        @DisplayName("Java11Pattern.describe() produces human-readable string")
        void java11PatternDescribeWorks() {
            Java11Pattern p = new Java11Pattern(
                    "R06", "InstanceofCastPattern",
                    "src/Dispatch.java", 55,
                    Java11Pattern.MigrationComplexity.LOW,
                    "if (element instanceof YTask) {");

            String desc = p.describe();
            assertTrue(desc.contains("R06"));
            assertTrue(desc.contains("InstanceofCastPattern"));
            assertTrue(desc.contains("src/Dispatch.java"));
            assertTrue(desc.contains("55"));
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static Java11Pattern pattern(String ruleId, String type,
                                          Java11Pattern.MigrationComplexity complexity) {
        return new Java11Pattern(ruleId, type, "src/Test.java", 1, complexity, "/* snippet */");
    }

    private static Java11Pattern threadCreationPattern(String ruleId) {
        return new Java11Pattern(ruleId, "ThreadCreationPattern",
                "src/Engine.java", 100,
                Java11Pattern.MigrationComplexity.LOW,
                "new Thread(() -> processWorkItem(item)).start();");
    }

    private static Java25Pattern findByRule(MigrationPlan plan, String ruleId) {
        return plan.migrations().stream()
                .filter(m -> m.sourcePattern().ruleId().equals(ruleId))
                .findFirst()
                .orElse(null);
    }
}
