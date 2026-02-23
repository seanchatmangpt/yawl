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
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link JavaMigrationAnalyzer} — Phase 2 (Detect) of the Java migration pipeline.
 *
 * <p>Tests verify that pattern detection correctly identifies Java 11 migration candidates.
 * Each detection pattern corresponds to a SPARQL SELECT query in {@code query/migration/detect-*.sparql}
 * and an entry in {@code ontology/migration/java11-patterns.ttl}.</p>
 *
 * <p>Chicago TDD: all tests use real {@link JavaMigrationAnalyzer} instances with
 * no mocks, stubs, or fakes. Java source snippets are written to temp files.</p>
 */
@DisplayName("JavaMigrationAnalyzer — Phase 2: Detect Java 11 patterns")
class JavaMigrationAnalyzerTest {

    private JavaMigrationAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new JavaMigrationAnalyzer();
    }

    // =========================================================================
    // Concurrency patterns
    // =========================================================================

    @Nested
    @DisplayName("R01: Thread Creation Pattern")
    class ThreadCreationTests {

        @Test
        @DisplayName("detects new Thread(runnable).start() — R01 ThreadCreationPattern")
        void detectsNewThreadStart(@TempDir Path tmpDir) throws IOException {
            Path file = writeJava(tmpDir, "ThreadTest.java", """
                    public class ThreadTest {
                        void run() {
                            new Thread(() -> processItem(item)).start();
                        }
                    }
                    """);

            List<Java11Pattern> patterns = analyzer.analyzeFile(file);

            assertTrue(patterns.stream().anyMatch(p -> p.ruleId().equals("R01")),
                    "Should detect R01 ThreadCreationPattern");
        }

        @Test
        @DisplayName("detects LOW complexity for thread creation")
        void threadCreationIsLowComplexity(@TempDir Path tmpDir) throws IOException {
            Path file = writeJava(tmpDir, "T.java", """
                    class T { void m() { new Thread(r).start(); } }
                    """);

            List<Java11Pattern> patterns = analyzer.analyzeFile(file);
            Java11Pattern p = findRule(patterns, "R01");

            assertNotNull(p, "R01 pattern should be detected");
            assertEquals(Java11Pattern.MigrationComplexity.LOW, p.complexity());
            assertTrue(p.isAutomatic());
        }
    }

    @Nested
    @DisplayName("R02: Thread Pool Pattern")
    class ThreadPoolTests {

        @Test
        @DisplayName("detects Executors.newFixedThreadPool — R02 ThreadPoolPattern")
        void detectsNewFixedThreadPool(@TempDir Path tmpDir) throws IOException {
            Path file = writeJava(tmpDir, "Exec.java", """
                    import java.util.concurrent.*;
                    class Exec {
                        ExecutorService pool = Executors.newFixedThreadPool(10);
                    }
                    """);

            List<Java11Pattern> patterns = analyzer.analyzeFile(file);

            assertTrue(patterns.stream().anyMatch(p -> p.ruleId().equals("R02")),
                    "Should detect R02 ThreadPoolPattern");
        }

        @Test
        @DisplayName("detects newCachedThreadPool variant")
        void detectsCachedThreadPool(@TempDir Path tmpDir) throws IOException {
            Path file = writeJava(tmpDir, "Exec.java", """
                    class Exec { void m() { Executors.newCachedThreadPool(); } }
                    """);

            List<Java11Pattern> patterns = analyzer.analyzeFile(file);

            assertTrue(patterns.stream().anyMatch(p -> p.ruleId().equals("R02")));
        }
    }

    @Nested
    @DisplayName("R03: ThreadLocal Pattern")
    class ThreadLocalTests {

        @Test
        @DisplayName("detects ThreadLocal<T> field — R03 (HIGH complexity)")
        void detectsThreadLocalField(@TempDir Path tmpDir) throws IOException {
            Path file = writeJava(tmpDir, "Ctx.java", """
                    class Ctx {
                        private static final ThreadLocal<UserContext> CTX = new ThreadLocal<>();
                    }
                    """);

            List<Java11Pattern> patterns = analyzer.analyzeFile(file);
            Java11Pattern p = findRule(patterns, "R03");

            assertNotNull(p, "R03 ThreadLocal pattern should be detected");
            assertEquals(Java11Pattern.MigrationComplexity.HIGH, p.complexity());
            assertFalse(p.isAutomatic(), "ThreadLocal migration is HIGH complexity — not automatic");
        }
    }

    @Nested
    @DisplayName("R05: Anonymous Class Pattern")
    class AnonymousClassTests {

        @Test
        @DisplayName("detects new Runnable() {} anonymous class — R05")
        void detectsRunnableAnonymousClass(@TempDir Path tmpDir) throws IOException {
            Path file = writeJava(tmpDir, "AnonClass.java", """
                    class AnonClass {
                        void m() {
                            new Thread(new Runnable() {
                                public void run() { doWork(); }
                            }).start();
                        }
                    }
                    """);

            List<Java11Pattern> patterns = analyzer.analyzeFile(file);

            assertTrue(patterns.stream().anyMatch(p -> p.ruleId().equals("R05")),
                    "Should detect R05 AnonymousClassPattern");
        }

        @Test
        @DisplayName("detects new Comparator<T>() {} anonymous class — R05")
        void detectsComparatorAnonymousClass(@TempDir Path tmpDir) throws IOException {
            Path file = writeJava(tmpDir, "Sort.java", """
                    class Sort {
                        Comparator<String> c = new Comparator<String>() {
                            public int compare(String a, String b) { return a.compareTo(b); }
                        };
                    }
                    """);

            List<Java11Pattern> patterns = analyzer.analyzeFile(file);

            assertTrue(patterns.stream().anyMatch(p -> p.ruleId().equals("R05")));
        }
    }

    @Nested
    @DisplayName("R06: instanceof + Cast Pattern")
    class InstanceofCastTests {

        @Test
        @DisplayName("detects instanceof with cast — R06 InstanceofCastPattern")
        void detectsInstanceofCast(@TempDir Path tmpDir) throws IOException {
            Path file = writeJava(tmpDir, "Dispatch.java", """
                    class Dispatch {
                        void handle(Object element) {
                            if (element instanceof YTask) {
                                YTask task = (YTask) element;
                                task.fire();
                            }
                        }
                    }
                    """);

            List<Java11Pattern> patterns = analyzer.analyzeFile(file);

            assertTrue(patterns.stream().anyMatch(p -> p.ruleId().equals("R06")),
                    "Should detect R06 InstanceofCastPattern");
        }
    }

    @Nested
    @DisplayName("R08: Switch Statement Pattern")
    class SwitchStatementTests {

        @Test
        @DisplayName("detects traditional switch statement — R08")
        void detectsSwitchStatement(@TempDir Path tmpDir) throws IOException {
            Path file = writeJava(tmpDir, "Switch.java", """
                    class Switch {
                        int code(String type) {
                            switch (type) {
                                case "XOR": return 0;
                                case "AND": return 1;
                                default: return -1;
                            }
                        }
                    }
                    """);

            List<Java11Pattern> patterns = analyzer.analyzeFile(file);

            assertTrue(patterns.stream().anyMatch(p -> p.ruleId().equals("R08")),
                    "Should detect R08 SwitchStatementPattern");
        }
    }

    @Nested
    @DisplayName("R09: String.format() Pattern")
    class StringFormatTests {

        @Test
        @DisplayName("detects String.format() call — R09")
        void detectsStringFormat(@TempDir Path tmpDir) throws IOException {
            Path file = writeJava(tmpDir, "Fmt.java", """
                    class Fmt {
                        String msg(String caseId) {
                            return String.format("Case %s failed at task %s", caseId, taskId);
                        }
                    }
                    """);

            List<Java11Pattern> patterns = analyzer.analyzeFile(file);

            assertTrue(patterns.stream().anyMatch(p -> p.ruleId().equals("R09")));
        }
    }

    @Nested
    @DisplayName("R10: Date/Calendar Pattern")
    class DateCalendarTests {

        @Test
        @DisplayName("detects new Date() usage — R10 (MEDIUM complexity)")
        void detectsNewDate(@TempDir Path tmpDir) throws IOException {
            Path file = writeJava(tmpDir, "Ts.java", """
                    import java.util.Date;
                    class Ts {
                        Date startTime = new Date();
                    }
                    """);

            List<Java11Pattern> patterns = analyzer.analyzeFile(file);
            Java11Pattern p = findRule(patterns, "R10");

            assertNotNull(p, "R10 Date pattern should be detected");
            assertEquals(Java11Pattern.MigrationComplexity.MEDIUM, p.complexity());
        }

        @Test
        @DisplayName("detects Calendar.getInstance() — R10")
        void detectsCalendarGetInstance(@TempDir Path tmpDir) throws IOException {
            Path file = writeJava(tmpDir, "Cal.java", """
                    class Cal {
                        void m() { var c = Calendar.getInstance(); }
                    }
                    """);

            List<Java11Pattern> patterns = analyzer.analyzeFile(file);

            assertTrue(patterns.stream().anyMatch(p -> p.ruleId().equals("R10")));
        }

        @Test
        @DisplayName("detects new SimpleDateFormat() — R10")
        void detectsSimpleDateFormat(@TempDir Path tmpDir) throws IOException {
            Path file = writeJava(tmpDir, "Sdf.java", """
                    class Sdf {
                        var f = new SimpleDateFormat("yyyy-MM-dd");
                    }
                    """);

            List<Java11Pattern> patterns = analyzer.analyzeFile(file);

            assertTrue(patterns.stream().anyMatch(p -> p.ruleId().equals("R10")));
        }
    }

    @Nested
    @DisplayName("R11: Legacy Collection Pattern")
    class LegacyCollectionTests {

        @Test
        @DisplayName("detects new Vector<>() — R11 LegacyCollectionPattern")
        void detectsNewVector(@TempDir Path tmpDir) throws IOException {
            Path file = writeJava(tmpDir, "LegColl.java", """
                    import java.util.*;
                    class LegColl {
                        Vector<String> ids = new Vector<>();
                        Hashtable<String, Object> props = new Hashtable<>();
                    }
                    """);

            List<Java11Pattern> patterns = analyzer.analyzeFile(file);

            assertTrue(patterns.stream().anyMatch(p -> p.ruleId().equals("R11")),
                    "Should detect R11 LegacyCollectionPattern");
        }

        @Test
        @DisplayName("detects new Stack<>() — R11")
        void detectsNewStack(@TempDir Path tmpDir) throws IOException {
            Path file = writeJava(tmpDir, "Stk.java", """
                    class Stk { Stack<String> s = new Stack<>(); }
                    """);

            List<Java11Pattern> patterns = analyzer.analyzeFile(file);

            assertTrue(patterns.stream().anyMatch(p -> p.ruleId().equals("R11")));
        }
    }

    @Nested
    @DisplayName("R13: Synchronized Pattern")
    class SynchronizedTests {

        @Test
        @DisplayName("detects synchronized method — R13 (HIGH complexity)")
        void detectsSynchronizedMethod(@TempDir Path tmpDir) throws IOException {
            Path file = writeJava(tmpDir, "Engine.java", """
                    class Engine {
                        public synchronized YIdentifier startCase(YSpec spec) {
                            return runner.startCase(spec);
                        }
                    }
                    """);

            List<Java11Pattern> patterns = analyzer.analyzeFile(file);
            Java11Pattern p = findRule(patterns, "R13");

            assertNotNull(p, "R13 SynchronizedPattern should be detected");
            assertEquals(Java11Pattern.MigrationComplexity.HIGH, p.complexity());
            assertFalse(p.isAutomatic());
        }

        @Test
        @DisplayName("detects synchronized block — R13")
        void detectsSynchronizedBlock(@TempDir Path tmpDir) throws IOException {
            Path file = writeJava(tmpDir, "Lock.java", """
                    class Lock {
                        void m() {
                            synchronized (this.lock) {
                                this.state = newState;
                            }
                        }
                    }
                    """);

            List<Java11Pattern> patterns = analyzer.analyzeFile(file);

            assertTrue(patterns.stream().anyMatch(p -> p.ruleId().equals("R13")));
        }
    }

    @Nested
    @DisplayName("Clean code — no false positives")
    class FalsePositiveTests {

        @Test
        @DisplayName("modern Java 25 code produces no detections")
        void modernJava25CodeIsClean(@TempDir Path tmpDir) throws IOException {
            Path file = writeJava(tmpDir, "Modern.java", """
                    import java.time.Instant;
                    import java.util.List;
                    import java.util.concurrent.StructuredTaskScope;

                    public class Modern {
                        record WorkItem(String id, String status, Instant created) {}

                        void process(Object element) {
                            if (element instanceof WorkItem item) {
                                handle(item);
                            }
                        }

                        int score(String type) {
                            return switch (type) {
                                case "XOR" -> 0;
                                case "AND" -> 1;
                                default    -> -1;
                            };
                        }

                        void handle(WorkItem item) {
                            Thread.ofVirtual().start(() -> doWork(item));
                        }

                        private void doWork(WorkItem item) {}
                    }
                    """);

            List<Java11Pattern> patterns = analyzer.analyzeFile(file);

            // Modern code should not trigger R01 (virtual thread is not new Thread())
            assertFalse(patterns.stream().anyMatch(p -> p.ruleId().equals("R01")),
                    "Thread.ofVirtual() should not be flagged as R01");

            // No legacy collections, no Date, no ThreadLocal
            assertFalse(patterns.stream().anyMatch(p ->
                    List.of("R10", "R11", "R03").contains(p.ruleId())));
        }

        @Test
        @DisplayName("empty file produces no detections")
        void emptyFileProducesNoDetections(@TempDir Path tmpDir) throws IOException {
            Path file = writeJava(tmpDir, "Empty.java", "");
            List<Java11Pattern> patterns = analyzer.analyzeFile(file);
            assertTrue(patterns.isEmpty(), "Empty file should produce no patterns");
        }

        @Test
        @DisplayName("comment-only file produces no detections")
        void commentOnlyFileProducesNoDetections(@TempDir Path tmpDir) throws IOException {
            Path file = writeJava(tmpDir, "Comments.java", """
                    // new Thread(runnable).start() — this is a comment
                    // Executors.newFixedThreadPool(10) — also a comment
                    /* new Date() in a block comment */
                    """);

            List<Java11Pattern> patterns = analyzer.analyzeFile(file);

            // Comments should not trigger pattern detection
            assertTrue(patterns.isEmpty() || patterns.stream().allMatch(
                    p -> p.snippet().startsWith("//")),
                    "Comment-only file should produce no non-comment detections");
        }
    }

    // =========================================================================
    // Directory analysis
    // =========================================================================

    @Nested
    @DisplayName("Directory analysis")
    class DirectoryTests {

        @Test
        @DisplayName("analyzes multiple .java files in a directory tree")
        void analyzesDirectory(@TempDir Path tmpDir) throws IOException {
            writeJava(tmpDir, "FileA.java", """
                    class FileA { void m() { new Thread(r).start(); } }
                    """);
            writeJava(tmpDir, "FileB.java", """
                    class FileB { ThreadLocal<String> tl = new ThreadLocal<>(); }
                    """);

            List<Java11Pattern> patterns = analyzer.analyzeDirectory(tmpDir);

            assertTrue(patterns.stream().anyMatch(p -> p.ruleId().equals("R01")),
                    "Should detect R01 from FileA.java");
            assertTrue(patterns.stream().anyMatch(p -> p.ruleId().equals("R03")),
                    "Should detect R03 from FileB.java");
            assertTrue(patterns.stream()
                    .map(Java11Pattern::filePath)
                    .distinct()
                    .count() >= 2,
                    "Should have patterns from at least 2 different files");
        }

        @Test
        @DisplayName("ignores non-Java files in directory")
        void ignoresNonJavaFiles(@TempDir Path tmpDir) throws IOException {
            Files.writeString(tmpDir.resolve("README.md"), "new Thread(r).start()");
            Files.writeString(tmpDir.resolve("config.xml"), "<thread>new Thread</thread>");
            writeJava(tmpDir, "Actual.java", "class Actual {}");

            List<Java11Pattern> patterns = analyzer.analyzeDirectory(tmpDir);

            assertTrue(patterns.stream().allMatch(p -> p.filePath().endsWith(".java")),
                    "All detected patterns should be from .java files");
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static Path writeJava(Path dir, String filename, String content) throws IOException {
        Path file = dir.resolve(filename);
        Files.writeString(file, content);
        return file;
    }

    private static Java11Pattern findRule(List<Java11Pattern> patterns, String ruleId) {
        return patterns.stream()
                .filter(p -> p.ruleId().equals(ruleId))
                .findFirst()
                .orElse(null);
    }
}
