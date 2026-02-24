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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 3 (Plan) of the Java 11 → 25 migration pipeline.
 *
 * <p>Builds a {@link MigrationPlan} by applying migration rules to detected
 * {@link Java11Pattern} instances, producing {@link Java25Pattern} migration targets.
 * In the full pipeline, this phase runs SPARQL CONSTRUCT queries that enrich the
 * RDF graph with j25: pattern triples. This implementation provides the Java equivalent
 * of those CONSTRUCT queries.</p>
 *
 * <p>Each rule maps one Java11Pattern class → one Java25Pattern class, with a
 * rendered code snippet and template file reference. Corresponds to
 * {@code ontology/migration/migration-rules.ttl} rule instances R01–R13.</p>
 */
public class MigrationPlanBuilder {

    /**
     * Builds a migration plan from detected Java 11 patterns.
     *
     * @param codebasePath root path of the analyzed codebase
     * @param filesAnalyzed number of files that were analyzed
     * @param detectedPatterns all Java 11 patterns detected by {@link JavaMigrationAnalyzer}
     * @return the complete migration plan
     */
    public MigrationPlan build(String codebasePath, int filesAnalyzed,
                                List<Java11Pattern> detectedPatterns) {
        List<Java25Pattern> migrations = new ArrayList<>();

        for (Java11Pattern src : detectedPatterns) {
            Java25Pattern target = constructTarget(src);
            if (target != null) {
                migrations.add(target);
            }
        }

        // Sort by: complexity (LOW first), then file, then line
        List<Java25Pattern> sorted = migrations.stream()
                .sorted(this::byComplexityThenFileThenLine)
                .toList();

        return new MigrationPlan(codebasePath, Instant.now(), filesAnalyzed, sorted);
    }

    // -------------------------------------------------------------------------
    // CONSTRUCT: map each Java 11 pattern → Java 25 target
    // Corresponds to construct-*.sparql queries
    // -------------------------------------------------------------------------

    private Java25Pattern constructTarget(Java11Pattern src) {
        return switch (src.ruleId()) {
            case "R01" -> constructVirtualThread(src);
            case "R02" -> constructVirtualExecutor(src);
            case "R03" -> constructScopedValue(src);
            case "R04" -> constructRecord(src);
            case "R05" -> constructLambda(src);
            case "R06" -> constructPatternMatching(src);
            case "R08" -> constructSwitchExpression(src);
            case "R09" -> constructTextBlock(src);
            case "R10" -> constructJavaTime(src);
            case "R11" -> constructModernCollection(src);
            case "R13" -> constructStructuredConcurrency(src);
            default    -> null;
        };
    }

    private Java25Pattern constructVirtualThread(Java11Pattern src) {
        return new Java25Pattern(
                src,
                "VirtualThreadPattern",
                "templates/java25-migration/virtual-thread.tera",
                "Thread.ofVirtual().start(runnable);",
                List.of("java.lang.Thread"),
                false,
                "Replace new Thread(runnable).start() with Thread.ofVirtual().start(runnable). " +
                "Virtual threads are JVM-managed: cheap to create, block without consuming OS threads."
        );
    }

    private Java25Pattern constructVirtualExecutor(Java11Pattern src) {
        return new Java25Pattern(
                src,
                "VirtualThreadExecutorPattern",
                "templates/java25-migration/virtual-thread.tera",
                "Executors.newVirtualThreadPerTaskExecutor()",
                List.of("java.util.concurrent.Executors"),
                false,
                "Replace fixed/cached thread pool with virtual thread executor. " +
                "No pool sizing needed for I/O-bound workloads."
        );
    }

    private Java25Pattern constructScopedValue(Java11Pattern src) {
        return new Java25Pattern(
                src,
                "ScopedValuePattern",
                "templates/java25-migration/scoped-value.tera",
                "// HIGH COMPLEXITY: ScopedValue.newInstance() — review set() call sites",
                List.of("java.lang.ScopedValue"),
                true,
                "HIGH COMPLEXITY: ThreadLocal → ScopedValue requires analysis of set() call sites. " +
                "ScopedValue creates an immutable binding via ScopedValue.runWhere(). " +
                "Manual review required to ensure correct scope boundaries."
        );
    }

    private Java25Pattern constructRecord(Java11Pattern src) {
        return new Java25Pattern(
                src,
                "RecordPattern",
                "templates/java25-migration/record.tera",
                "// Candidate for record — verify no mutable fields or inheritance",
                List.of(),
                false,
                "POJO with only getters → record. Records provide canonical constructor, " +
                "equals, hashCode, toString, and accessor methods automatically."
        );
    }

    private Java25Pattern constructLambda(Java11Pattern src) {
        return new Java25Pattern(
                src,
                "LambdaPattern",
                "templates/java25-migration/lambda.tera",
                "() -> { /* lambda body */ }",
                List.of(),
                false,
                "Replace single-method anonymous class with lambda expression."
        );
    }

    private Java25Pattern constructPatternMatching(Java11Pattern src) {
        return new Java25Pattern(
                src,
                "PatternMatchingInstanceofPattern",
                "templates/java25-migration/pattern-matching.tera",
                "if (element instanceof Type t) { /* use t directly */ }",
                List.of(),
                false,
                "Java 16+ pattern matching instanceof eliminates redundant cast. " +
                "Binding variable t is in scope in the true branch."
        );
    }

    private Java25Pattern constructSwitchExpression(Java11Pattern src) {
        return new Java25Pattern(
                src,
                "SwitchExpressionPattern",
                "templates/java25-migration/switch-expression.tera",
                "result = switch (expr) {\n    case A -> valueA;\n    case B -> valueB;\n};",
                List.of(),
                false,
                "Replace traditional switch statement with switch expression using arrow cases. " +
                "Eliminates break statements. Compiler verifies exhaustiveness."
        );
    }

    private Java25Pattern constructTextBlock(Java11Pattern src) {
        return new Java25Pattern(
                src,
                "FormattedTextBlockPattern",
                "templates/java25-migration/text-block.tera",
                "\"\"\"\n    %s\n    \"\"\".formatted(/* args */)",
                List.of(),
                false,
                "Replace String.format() multi-line with text block + .formatted(). " +
                "More readable for XML, JSON, SQL, and multi-line string templates."
        );
    }

    private Java25Pattern constructJavaTime(Java11Pattern src) {
        String replacement = src.snippet().contains("SimpleDateFormat")
                ? "DateTimeFormatter.ofPattern(/* pattern */)"
                : src.snippet().contains("Calendar")
                  ? "LocalDateTime.now()"
                  : "Instant.now()";

        List<String> imports = src.snippet().contains("SimpleDateFormat")
                ? List.of("java.time.format.DateTimeFormatter")
                : src.snippet().contains("Calendar")
                  ? List.of("java.time.LocalDateTime")
                  : List.of("java.time.Instant");

        return new Java25Pattern(
                src,
                "InstantPattern",
                "templates/java25-migration/java-time.tera",
                replacement,
                imports,
                false,
                "Replace java.util.Date/Calendar with java.time.*. " +
                "Instant for UTC timestamps, LocalDateTime for local display, " +
                "DateTimeFormatter (thread-safe) for formatting."
        );
    }

    private Java25Pattern constructModernCollection(Java11Pattern src) {
        String modernType = src.snippet().contains("Hashtable") ? "HashMap / ConcurrentHashMap"
                : src.snippet().contains("Stack") ? "ArrayDeque (as Deque)"
                : "ArrayList";
        return new Java25Pattern(
                src,
                "ImmutableCollectionPattern",
                "templates/java25-migration/collections.tera",
                "// Replace with: " + modernType,
                List.of(),
                false,
                "Replace legacy collection (" + modernType + "). " +
                "For initialization-only use: List.of(), Map.of(), Set.of()."
        );
    }

    private Java25Pattern constructStructuredConcurrency(Java11Pattern src) {
        return new Java25Pattern(
                src,
                "StructuredTaskScopePattern",
                "templates/java25-migration/structured-concurrency.tera",
                "// HIGH COMPLEXITY: StructuredTaskScope.ShutdownOnFailure — review YAWL soundness",
                List.of("java.util.concurrent.StructuredTaskScope"),
                true,
                "HIGH COMPLEXITY: synchronized → StructuredTaskScope requires deep analysis. " +
                "YAWL soundness properties (completeness, no deadlock, proper completion) " +
                "must be preserved. Human review mandatory before merging."
        );
    }

    // -------------------------------------------------------------------------
    // Sort comparator
    // -------------------------------------------------------------------------

    private int byComplexityThenFileThenLine(Java25Pattern a, Java25Pattern b) {
        int cmp = a.sourcePattern().complexity().compareTo(b.sourcePattern().complexity());
        if (cmp != 0) return cmp;
        cmp = a.targetFile().compareTo(b.targetFile());
        if (cmp != 0) return cmp;
        return Integer.compare(a.targetLine(), b.targetLine());
    }
}
