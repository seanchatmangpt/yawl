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

/**
 * Named Java 11 migration pattern detected in source code.
 *
 * <p>Corresponds to pattern classes in {@code ontology/migration/java11-patterns.ttl}.
 * Instances are produced by SPARQL SELECT queries (detect-*.sparql) against the
 * codebase RDF graph, then consumed by SPARQL CONSTRUCT queries (construct-*.sparql)
 * to produce {@link Java25Pattern} migration targets.</p>
 *
 * @param ruleId      migration rule identifier (R01–R13)
 * @param patternType ontology class URI fragment (e.g., "ThreadCreationPattern")
 * @param filePath    relative path to the source file containing the pattern
 * @param lineNumber  source line number
 * @param complexity  LOW | MEDIUM | HIGH
 * @param snippet     verbatim Java 11 source snippet exhibiting the pattern
 */
public record Java11Pattern(
        String ruleId,
        String patternType,
        String filePath,
        int lineNumber,
        MigrationComplexity complexity,
        String snippet
) {

    /** Migration complexity levels — matches migration-rules.ttl rule:complexity values. */
    public enum MigrationComplexity {
        LOW, MEDIUM, HIGH
    }

    /** @return true if this migration can be applied automatically without human review. */
    public boolean isAutomatic() {
        return complexity != MigrationComplexity.HIGH;
    }

    /** @return human-readable description of this pattern instance. */
    public String describe() {
        return "%s [%s] at %s:%d (%s complexity)".formatted(
                patternType, ruleId, filePath, lineNumber, complexity);
    }
}
