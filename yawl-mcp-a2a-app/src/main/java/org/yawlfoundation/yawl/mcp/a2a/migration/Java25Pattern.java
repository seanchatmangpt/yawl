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

import java.util.List;

/**
 * Java 25 migration target pattern derived from a {@link Java11Pattern}.
 *
 * <p>Corresponds to pattern classes in {@code ontology/migration/java25-patterns.ttl}.
 * Instances are produced by SPARQL CONSTRUCT queries (construct-*.sparql) and
 * rendered via Tera templates (templates/java25-migration/*.tera).</p>
 *
 * @param sourcePattern  the Java 11 pattern this was derived from
 * @param patternType    ontology class URI fragment (e.g., "VirtualThreadPattern")
 * @param templateFile   Tera template path for rendering Java 25 code
 * @param renderedCode   the generated Java 25 replacement code
 * @param requiredImports fully-qualified class names that must be added as imports
 * @param requiresReview  true if a human must review before merging
 * @param migrationNote  additional guidance for MEDIUM/HIGH complexity migrations
 */
public record Java25Pattern(
        Java11Pattern sourcePattern,
        String patternType,
        String templateFile,
        String renderedCode,
        List<String> requiredImports,
        boolean requiresReview,
        String migrationNote
) {

    /** @return the target file path (same as source, different content). */
    public String targetFile() {
        return sourcePattern.filePath();
    }

    /** @return target line number for the replacement. */
    public int targetLine() {
        return sourcePattern.lineNumber();
    }

    /** @return true if this migration can be applied without human review. */
    public boolean isAutoApplicable() {
        return !requiresReview && sourcePattern.isAutomatic();
    }

    /** @return human-readable migration description. */
    public String describe() {
        return "%s → %s at %s:%d%s".formatted(
                sourcePattern.patternType(),
                patternType,
                targetFile(),
                targetLine(),
                requiresReview ? " [REVIEW REQUIRED]" : "");
    }
}
