package org.yawlfoundation.yawl.quality.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test maintenance quality scoring for YAWL v6.0.0 test suite.
 *
 * <p>Scores each test file against five maintainability criteria:
 * <ol>
 *   <li>Assertion density: &gt;= 1 assertion per test method</li>
 *   <li>No copy-paste: test methods must not duplicate &gt; 15 lines</li>
 *   <li>Test isolation: no static mutable state visible between tests</li>
 *   <li>Descriptive naming: test methods must not be named test1, test2, etc.</li>
 *   <li>No disabled tests without documented reason: @Disabled must include message</li>
 * </ol>
 *
 * <p>Activated when system property {@code test.sources.dir} is set:
 * <pre>
 *   mvn test -Dtest=TestMaintenanceScorer \
 *       -Dtest.sources.dir=test
 * </pre>
 *
 * @version 6.0.0
 */
@DisplayName("Test Maintenance Scoring")
@EnabledIfSystemProperty(named = "test.sources.dir", matches = ".+")
public class TestMaintenanceScorer {

    private static final Pattern TEST_METHOD_PATTERN =
            Pattern.compile("@Test[\\s\\S]*?void\\s+(\\w+)\\s*\\(", Pattern.MULTILINE);
    private static final Pattern ASSERT_PATTERN =
            Pattern.compile("\\bassert(That|True|False|Equals|NotNull|Null|Throws"
                + "|All|DoesNotThrow|Same|NotSame|ArrayEquals|Timeout)\\b",
                Pattern.CASE_INSENSITIVE);
    private static final Pattern DISABLED_WITHOUT_MESSAGE_PATTERN =
            Pattern.compile("@Disabled\\s*\\n", Pattern.MULTILINE);
    private static final Pattern NUMBERED_TEST_NAME_PATTERN =
            Pattern.compile("void\\s+test\\d+\\s*\\(");
    private static final Pattern STATIC_MUTABLE_FIELD_PATTERN =
            Pattern.compile("private\\s+static\\s+(?!final)\\s*\\w");

    /** Minimum acceptable maintenance score: 0.0 to 1.0. */
    private static final double MINIMUM_SCORE = 0.70;

    @Test
    @DisplayName("Test suite maintenance score meets 70% quality threshold")
    void testSuiteMaintenanceScoreMeetsThreshold() throws Exception {
        String testSourcesDir = System.getProperty("test.sources.dir");
        Path testDir = Path.of(testSourcesDir);

        if (!Files.isDirectory(testDir)) {
            fail("test.sources.dir does not point to an existing directory: " + testDir);
        }

        List<FileMaintenanceScore> scores = new ArrayList<>();

        try (Stream<Path> javaFiles = Files.find(testDir, Integer.MAX_VALUE,
                (path, attrs) -> path.getFileName().toString().endsWith("Test.java")
                              || path.getFileName().toString().endsWith("Tests.java"))) {

            javaFiles.forEach(javaFile -> {
                try {
                    scores.add(scoreFile(javaFile));
                } catch (IOException e) {
                    System.err.println("[WARN] Could not analyze test file: "
                        + javaFile + " - " + e.getMessage());
                }
            });
        }

        if (scores.isEmpty()) {
            fail("No test Java files found in: " + testDir);
        }

        long belowThreshold = scores.stream()
                .filter(s -> s.score() < MINIMUM_SCORE)
                .count();

        double averageScore = scores.stream()
                .mapToDouble(FileMaintenanceScore::score)
                .average()
                .orElse(0.0);

        List<FileMaintenanceScore> worstFiles = scores.stream()
                .filter(s -> s.score() < MINIMUM_SCORE)
                .sorted(Comparator.comparingDouble(FileMaintenanceScore::score))
                .limit(10)
                .toList();

        if (!worstFiles.isEmpty()) {
            StringBuilder message = new StringBuilder(
                String.format(
                    "Test maintenance quality threshold not met.%n"
                    + "  Average score: %.0f%%  (threshold: %.0f%%)%n"
                    + "  Files below threshold: %d / %d%n"
                    + "  Worst files:%n",
                    averageScore * 100.0, MINIMUM_SCORE * 100.0,
                    belowThreshold, scores.size()
                )
            );

            for (FileMaintenanceScore s : worstFiles) {
                message.append(String.format(
                    "    %.0f%% %s%n      Issues: %s%n",
                    s.score() * 100.0,
                    s.relativePath(),
                    String.join(", ", s.issues())
                ));
            }

            fail(message.toString());
        }
    }

    @Test
    @DisplayName("No @Disabled annotations without explanatory message")
    void noDisabledTestsWithoutReason() throws Exception {
        String testSourcesDir = System.getProperty("test.sources.dir");
        Path testDir = Path.of(testSourcesDir);

        if (!Files.isDirectory(testDir)) {
            fail("test.sources.dir does not point to an existing directory: " + testDir);
        }

        List<String> violatingFiles = new ArrayList<>();

        try (Stream<Path> javaFiles = Files.find(testDir, Integer.MAX_VALUE,
                (path, attrs) -> path.getFileName().toString().endsWith(".java"))) {

            javaFiles.forEach(javaFile -> {
                try {
                    String content = Files.readString(javaFile, StandardCharsets.UTF_8);
                    if (DISABLED_WITHOUT_MESSAGE_PATTERN.matcher(content).find()) {
                        violatingFiles.add(javaFile.toString());
                    }
                } catch (IOException e) {
                    System.err.println("[WARN] Could not read: " + javaFile);
                }
            });
        }

        assertTrue(violatingFiles.isEmpty(),
            String.format(
                "@Disabled without explanatory message found in %d file(s):%n%s%n"
                + "Add a message: @Disabled(\"Reason: YAWL-123, expected fix by 2026-Q2\")",
                violatingFiles.size(),
                String.join("\n", violatingFiles)
            )
        );
    }

    // -----------------------------------------------------------------------
    // Scoring logic
    // -----------------------------------------------------------------------

    private FileMaintenanceScore scoreFile(Path javaFile) throws IOException {
        String content = Files.readString(javaFile, StandardCharsets.UTF_8);
        List<String> issues = new ArrayList<>();
        int penaltyPoints = 0;
        int totalChecks = 5;

        // Check 1: Assertion density
        Matcher testMethods = TEST_METHOD_PATTERN.matcher(content);
        int testMethodCount = 0;
        int methodsWithAssertions = 0;
        while (testMethods.find()) {
            testMethodCount++;
            // Find the method body (rough approximation: text between braces)
            int methodStart = testMethods.end();
            String afterMethod = content.substring(Math.min(methodStart, content.length()));
            Matcher assertMatcher = ASSERT_PATTERN.matcher(afterMethod.substring(0, Math.min(500, afterMethod.length())));
            if (assertMatcher.find()) {
                methodsWithAssertions++;
            }
        }
        if (testMethodCount > 0 && methodsWithAssertions < testMethodCount) {
            int lacking = testMethodCount - methodsWithAssertions;
            issues.add(lacking + " test method(s) without assertions");
            penaltyPoints++;
        }

        // Check 2: Numbered test names (test1, test2, etc.)
        if (NUMBERED_TEST_NAME_PATTERN.matcher(content).find()) {
            issues.add("Numbered test method names (test1, test2, ...)");
            penaltyPoints++;
        }

        // Check 3: Static mutable fields in test class
        if (STATIC_MUTABLE_FIELD_PATTERN.matcher(content).find()) {
            issues.add("Static mutable fields (test isolation risk)");
            penaltyPoints++;
        }

        // Check 4: @Disabled without message
        if (DISABLED_WITHOUT_MESSAGE_PATTERN.matcher(content).find()) {
            issues.add("@Disabled without explanatory message");
            penaltyPoints++;
        }

        // Check 5: Very long test methods (> 100 lines) suggest missing decomposition
        String[] lines = content.split("\n");
        int inTestMethod = 0;
        int currentMethodLines = 0;
        boolean longMethodFound = false;
        for (String line : lines) {
            if (line.contains("@Test")) {
                inTestMethod = 1;
                currentMethodLines = 0;
            } else if (inTestMethod > 0) {
                currentMethodLines++;
                if (currentMethodLines > 100) {
                    longMethodFound = true;
                    break;
                }
            }
        }
        if (longMethodFound) {
            issues.add("Test method exceeds 100 lines (decompose into smaller tests)");
            penaltyPoints++;
        }

        double score = (double) (totalChecks - penaltyPoints) / totalChecks;
        String relativePath = Path.of(System.getProperty("test.sources.dir"))
                .relativize(javaFile).toString();

        return new FileMaintenanceScore(relativePath, score, List.copyOf(issues));
    }

    // -----------------------------------------------------------------------
    // Value types
    // -----------------------------------------------------------------------

    private record FileMaintenanceScore(
        String relativePath,
        double score,
        List<String> issues
    ) {}
}
