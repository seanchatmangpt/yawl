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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Phase 2 (Detect) of the Java 11 → 25 migration pipeline.
 *
 * <p>Analyzes Java source files for Java 11 migration patterns using regex-based
 * detection. In the full pipeline, this phase runs SPARQL SELECT queries against
 * the RDF codebase graph produced by Phase 1 (Analyze). This implementation provides
 * a self-contained Java equivalent of those detection queries, suitable for use
 * when Oxigraph/QLever is not available.</p>
 *
 * <p>Pattern detection rules correspond to {@code query/migration/detect-*.sparql}
 * and {@code ontology/migration/java11-patterns.ttl}.</p>
 *
 * <p>All detection is read-only — no source files are modified.</p>
 */
public class JavaMigrationAnalyzer {

    // -------------------------------------------------------------------------
    // Detection patterns (correspond to SPARQL SELECT detect-*.sparql queries)
    // -------------------------------------------------------------------------

    /** R01: new Thread(runnable).start() */
    private static final Pattern THREAD_CREATION =
            Pattern.compile("new\\s+Thread\\s*\\(");

    /** R02: Executors.newFixedThreadPool() and similar */
    private static final Pattern THREAD_POOL =
            Pattern.compile("Executors\\s*\\.\\s*new(Fixed|Cached|SingleThread|Scheduled|WorkStealing)\\w*\\s*\\(");

    /** R03: ThreadLocal<T> field declarations */
    private static final Pattern THREAD_LOCAL =
            Pattern.compile("ThreadLocal\\s*<");

    /** R04: Getter/setter pattern — detected by presence of getX()/setX() pairs */
    private static final Pattern GETTER =
            Pattern.compile("public\\s+\\w+\\s+get[A-Z]\\w*\\s*\\(\\s*\\)");
    private static final Pattern SETTER =
            Pattern.compile("public\\s+void\\s+set[A-Z]\\w*\\s*\\(");

    /** R05: Anonymous class implementations of functional interfaces */
    private static final Pattern ANONYMOUS_CLASS =
            Pattern.compile("new\\s+(Runnable|Callable|Comparator|Supplier|Consumer|Function|Predicate)(?:<[^>]+>)?\\s*\\(\\s*\\)\\s*\\{");

    /** R06: instanceof + cast pattern */
    private static final Pattern INSTANCEOF_CAST =
            Pattern.compile("instanceof\\s+(\\w+)\\s*\\)");

    /** R08: traditional switch statement */
    private static final Pattern SWITCH_STATEMENT =
            Pattern.compile("\\bswitch\\s*\\(");

    /** R09: String.format() with multi-line potential */
    private static final Pattern STRING_FORMAT =
            Pattern.compile("String\\.format\\s*\\(");

    /** R10: java.util.Date and Calendar usage */
    private static final Pattern DATE_CALENDAR =
            Pattern.compile("new\\s+(Date|GregorianCalendar|SimpleDateFormat)\\s*\\(|Calendar\\.getInstance\\(");

    /** R11: legacy collections */
    private static final Pattern LEGACY_COLLECTION =
            Pattern.compile("new\\s+(Vector|Hashtable|Stack)\\s*[<(]");

    /** R13: synchronized methods/blocks */
    private static final Pattern SYNCHRONIZED =
            Pattern.compile("\\bsynchronized\\b");

    // -------------------------------------------------------------------------
    // Analysis
    // -------------------------------------------------------------------------

    /**
     * Analyzes a single Java source file for Java 11 migration patterns.
     *
     * @param filePath path to the .java file to analyze
     * @return list of detected Java 11 patterns, ordered by line number
     * @throws IOException if the file cannot be read
     */
    public List<Java11Pattern> analyzeFile(Path filePath) throws IOException {
        List<String> lines = Files.readAllLines(filePath);
        List<Java11Pattern> patterns = new ArrayList<>();
        String relativePath = filePath.toString();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int lineNum = i + 1;

            detectAll(relativePath, lineNum, line, patterns);
        }

        return patterns;
    }

    /**
     * Analyzes all .java files under a directory tree.
     *
     * @param sourceRoot root directory to scan
     * @return all detected Java 11 patterns across the codebase
     * @throws IOException if directory traversal fails
     */
    public List<Java11Pattern> analyzeDirectory(Path sourceRoot) throws IOException {
        List<Java11Pattern> all = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(p -> p.toString().endsWith(".java"))
                 .forEach(file -> {
                     try {
                         all.addAll(analyzeFile(file));
                     } catch (IOException e) {
                         throw new RuntimeException("Failed to analyze " + file, e);
                     }
                 });
        }

        return all;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void detectAll(String file, int line, String src, List<Java11Pattern> out) {
        String trimmed = src.trim();

        // Skip comments (line comments, block comment lines, and block comment starts)
        if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) return;

        if (THREAD_CREATION.matcher(src).find()) {
            out.add(pattern("R01", "ThreadCreationPattern", file, line,
                    Java11Pattern.MigrationComplexity.LOW, trimmed));
        }

        if (THREAD_POOL.matcher(src).find()) {
            out.add(pattern("R02", "ThreadPoolPattern", file, line,
                    Java11Pattern.MigrationComplexity.LOW, trimmed));
        }

        if (THREAD_LOCAL.matcher(src).find()) {
            out.add(pattern("R03", "ThreadLocalPattern", file, line,
                    Java11Pattern.MigrationComplexity.HIGH, trimmed));
        }

        if (GETTER.matcher(src).find() && !src.contains("@Override")) {
            out.add(pattern("R04", "GetterSetterPojoPattern", file, line,
                    Java11Pattern.MigrationComplexity.LOW, trimmed));
        }

        if (ANONYMOUS_CLASS.matcher(src).find()) {
            out.add(pattern("R05", "AnonymousClassPattern", file, line,
                    Java11Pattern.MigrationComplexity.LOW, trimmed));
        }

        if (INSTANCEOF_CAST.matcher(src).find()) {
            out.add(pattern("R06", "InstanceofCastPattern", file, line,
                    Java11Pattern.MigrationComplexity.LOW, trimmed));
        }

        if (SWITCH_STATEMENT.matcher(src).find()) {
            out.add(pattern("R08", "SwitchStatementPattern", file, line,
                    Java11Pattern.MigrationComplexity.LOW, trimmed));
        }

        if (STRING_FORMAT.matcher(src).find()) {
            out.add(pattern("R09", "StringFormatPattern", file, line,
                    Java11Pattern.MigrationComplexity.LOW, trimmed));
        }

        if (DATE_CALENDAR.matcher(src).find()) {
            out.add(pattern("R10", "DateCalendarPattern", file, line,
                    Java11Pattern.MigrationComplexity.MEDIUM, trimmed));
        }

        if (LEGACY_COLLECTION.matcher(src).find()) {
            out.add(pattern("R11", "LegacyCollectionPattern", file, line,
                    Java11Pattern.MigrationComplexity.LOW, trimmed));
        }

        if (SYNCHRONIZED.matcher(src).find() && !trimmed.startsWith("//")) {
            out.add(pattern("R13", "SynchronizedPattern", file, line,
                    Java11Pattern.MigrationComplexity.HIGH, trimmed));
        }
    }

    private static Java11Pattern pattern(String ruleId, String type, String file,
                                          int line, Java11Pattern.MigrationComplexity complexity,
                                          String snippet) {
        return new Java11Pattern(ruleId, type, file, line, complexity, snippet);
    }
}
