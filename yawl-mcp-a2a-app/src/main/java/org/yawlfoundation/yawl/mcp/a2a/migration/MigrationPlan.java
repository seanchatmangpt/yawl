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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The complete Java 11 → 25 migration plan for a codebase.
 *
 * <p>Produced by the SPARQL CONSTRUCT phase of the migration pipeline.
 * Aggregates all {@link Java25Pattern} instances with summary statistics.
 * Corresponds to the top-level {@code rule:MigrationPlan} resource in the
 * migration graph produced by {@code construct-migration-plan.sparql}.</p>
 *
 * @param codebasePath  root path of the analyzed Java codebase
 * @param generatedAt   when the plan was generated
 * @param filesAnalyzed total number of .java files analyzed
 * @param migrations    all migration pattern instances (ordered by priority then file)
 */
public record MigrationPlan(
        String codebasePath,
        Instant generatedAt,
        int filesAnalyzed,
        List<Java25Pattern> migrations
) {

    /** @return all migrations that can be applied automatically (LOW/MEDIUM, no review). */
    public List<Java25Pattern> automaticMigrations() {
        return migrations.stream()
                .filter(Java25Pattern::isAutoApplicable)
                .toList();
    }

    /** @return migrations that require human review before merging. */
    public List<Java25Pattern> reviewMigrations() {
        return migrations.stream()
                .filter(Java25Pattern::requiresReview)
                .toList();
    }

    /** @return migrations grouped by complexity level. */
    public Map<Java11Pattern.MigrationComplexity, List<Java25Pattern>> byComplexity() {
        return migrations.stream()
                .collect(Collectors.groupingBy(m -> m.sourcePattern().complexity()));
    }

    /** @return migrations grouped by source file. */
    public Map<String, List<Java25Pattern>> byFile() {
        return migrations.stream()
                .collect(Collectors.groupingBy(Java25Pattern::targetFile));
    }

    /** @return count of migrations at LOW complexity. */
    public long lowCount() {
        return migrations.stream()
                .filter(m -> m.sourcePattern().complexity() == Java11Pattern.MigrationComplexity.LOW)
                .count();
    }

    /** @return count of migrations at MEDIUM complexity. */
    public long mediumCount() {
        return migrations.stream()
                .filter(m -> m.sourcePattern().complexity() == Java11Pattern.MigrationComplexity.MEDIUM)
                .count();
    }

    /** @return count of migrations at HIGH complexity (review required). */
    public long highCount() {
        return migrations.stream()
                .filter(m -> m.sourcePattern().complexity() == Java11Pattern.MigrationComplexity.HIGH)
                .count();
    }

    /** @return true if any LOW complexity migrations are present (safe to auto-commit). */
    public boolean hasLowComplexityMigrations() {
        return lowCount() > 0;
    }

    /** @return true if any MEDIUM or HIGH complexity migrations are present (review needed). */
    public boolean hasMediumOrHighComplexityMigrations() {
        return mediumCount() > 0 || highCount() > 0;
    }

    /** @return summary string suitable for logging and reporting. */
    public String summarize() {
        return """
                Migration Plan Summary
                ======================
                Codebase:  %s
                Generated: %s
                Files:     %d analyzed

                Migrations: %d total
                  LOW:    %d (auto-applicable)
                  MEDIUM: %d (review recommended)
                  HIGH:   %d (manual review required)

                Auto-applicable: %d
                Requires review: %d
                """.formatted(
                codebasePath,
                generatedAt,
                filesAnalyzed,
                migrations.size(),
                lowCount(),
                mediumCount(),
                highCount(),
                automaticMigrations().size(),
                reviewMigrations().size()
        );
    }
}
