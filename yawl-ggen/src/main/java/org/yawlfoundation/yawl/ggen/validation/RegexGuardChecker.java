package org.yawlfoundation.yawl.ggen.validation;

import org.yawlfoundation.yawl.ggen.validation.model.GuardViolation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementation of GuardChecker that uses regular expressions to detect
 * guard violations in Java source code.
 *
 * <p>This checker scans Java files line by line, applying regex patterns to
 * detect specific violations. It supports context extraction to show
 * surrounding lines when violations are found.</p>
 *
 * <p><b>Supported Patterns:</b></p>
 * <ul>
 *   <li><b>H_TODO:</b> Deferred work markers (TODO, FIXME, etc.)</li>
 *   <li><b>H_MOCK:</b> Mock implementations (class names or method names)</li>
 *   <li><b>H_SILENT:</b> Silent logging instead of throwing exceptions</li>
 * </ul>
 */
public class RegexGuardChecker implements GuardChecker {

    private final Pattern pattern;
    private final String patternName;
    private final Severity severity;
    private final int contextLines;

    /**
     * Constructs a new RegexGuardChecker.
     *
     * @param patternName the name of the guard pattern (e.g., "H_TODO")
     * @param regex the regular expression pattern to match
     */
    public RegexGuardChecker(String patternName, String regex) {
        this(patternName, regex, Severity.FAIL);
    }

    /**
     * Constructs a new RegexGuardChecker with specified severity.
     *
     * @param patternName the name of the guard pattern (e.g., "H_TODO")
     * @param regex the regular expression pattern to match
     * @param severity the severity level for violations
     */
    public RegexGuardChecker(String patternName, String regex, Severity severity) {
        this.patternName = patternName;
        this.pattern = Pattern.compile(regex);
        this.severity = severity;
        this.contextLines = 2; // Show 2 lines before/after violation
    }

    @Override
    public List<GuardViolation> check(Path javaSource) throws IOException {
        List<GuardViolation> violations = new ArrayList<>();
        List<String> allLines = Files.readAllLines(javaSource);

        for (int i = 0; i < allLines.size(); i++) {
            String line = allLines.get(i);
            Matcher matcher = pattern.matcher(line);

            if (matcher.find()) {
                String content = extractContext(allLines, i);
                GuardViolation violation = new GuardViolation(
                    patternName,
                    severity,
                    javaSource.toString(),
                    i + 1, // Line numbers start at 1
                    content
                );
                violations.add(violation);
            }
        }

        return violations;
    }

    @Override
    public String patternName() {
        return patternName;
    }

    @Override
    public Severity severity() {
        return severity;
    }

    /**
     * Extracts context lines around the violation.
     *
     * @param allLines all lines from the file
     * @param violationLine the line number of the violation (0-based)
     * @return context string with surrounding lines
     */
    private String extractContext(List<String> allLines, int violationLine) {
        StringBuilder context = new StringBuilder();
        int startLine = Math.max(0, violationLine - contextLines);
        int endLine = Math.min(allLines.size() - 1, violationLine + contextLines);

        for (int i = startLine; i <= endLine; i++) {
            if (i > startLine) {
                context.append("\n");
            }

            // Mark the violation line with >>>
            if (i == violationLine) {
                context.append(">>> ");
            } else {
                context.append("    ");
            }

            context.append(String.format("%3d: ", i + 1))
                   .append(allLines.get(i));
        }

        return context.toString();
    }

    /**
     * Factory method for creating predefined regex checkers.
     */
    public static class Factory {

        /**
         * Creates a checker for H_TODO pattern.
         */
        public static RegexGuardChecker createTodoChecker() {
            String regex = "//\\s*(TODO|FIXME|XXX|HACK|LATER|FUTURE|@incomplete|@stub|placeholder)";
            return new RegexGuardChecker("H_TODO", regex);
        }

        /**
         * Creates a checker for H_MOCK pattern.
         * This detects both mock class names and mock method calls.
         */
        public static RegexGuardChecker createPatternChecker() {
            String regex = "(?i)(mock|stub|fake|demo)[a-z]*\\s*[=(]|(?:class|interface|enum)\\s+(Mock|Stub|Fake|Demo)";
            return new RegexGuardChecker("H_MOCK", regex);
        }

        /**
         * Creates a checker for H_SILENT pattern.
         */
        public static RegexGuardChecker createSilentChecker() {
            String regex = "log\\.(warn|error)\\([^)]*\"[^\"]*not\\s+implemented";
            return new RegexGuardChecker("H_SILENT", regex);
        }
    }
}