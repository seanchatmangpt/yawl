package org.yawlfoundation.yawl.ggen.validation.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.yawlfoundation.yawl.ggen.validation.Severity;
import java.util.Set;
import java.util.Objects;

/**
 * Represents a guard violation detected during hyper-standards validation.
 *
 * <p>Guard violations are categorized by pattern type (H_TODO, H_MOCK, etc.)
 * and include severity level, location information, and automatic fix guidance.
 *
 * <p>This class is immutable and follows Java 25 modern patterns with validation.
 *
 * @since 1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class GuardViolation {

    /**
     * Valid guard pattern names. All patterns must be validated against this set.
     */
    private static final Set<String> VALID_PATTERNS = Set.of(
        "H_TODO", "H_MOCK", "H_STUB", "H_EMPTY",
        "H_FALLBACK", "H_LIE", "H_SILENT", "H_ERROR"
    );

    private final String pattern;
    private final Severity severity;
    private final String file;
    private final int line;
    private final String content;
    private final String fixGuidance;

    /**
     * Creates a new GuardViolation with the specified parameters.
     *
     * @param pattern the guard pattern type (must be one of H_TODO, H_MOCK, etc.)
     * @param severity the severity level (FAIL or WARN)
     * @param file the path to the file containing the violation
     * @param line the line number of the violation (must be >= 0)
     * @param content the exact code content that violates the guard
     * @throws IllegalArgumentException if pattern is invalid, severity is null,
     *                                  or line number is negative
     */
    public GuardViolation(String pattern, Severity severity, String file, int line, String content) {
        this.pattern = validatePattern(pattern);
        this.severity = Objects.requireNonNull(severity, "Severity cannot be null");
        this.file = Objects.requireNonNull(file, "File path cannot be null");
        validateLineNumber(line);
        this.line = line;
        this.content = Objects.requireNonNull(content, "Content cannot be null");
        this.fixGuidance = generateFixGuidance(pattern);
    }

    /**
     * Creates a new GuardViolation with string-based severity.
     *
     * @param pattern the guard pattern type (must be one of H_TODO, H_MOCK, etc.)
     * @param severity the severity level as string (FAIL or WARN)
     * @param file the path to the file containing the violation
     * @param line the line number of the violation (must be >= 0)
     * @param content the exact code content that violates the guard
     * @throws IllegalArgumentException if pattern is invalid, severity is not FAIL/WARN,
     *                                  or line number is negative
     */
    public GuardViolation(String pattern, String severity, String file, int line, String content) {
        this(pattern, Severity.fromString(severity), file, line, content);
    }

    /**
     * Validates that the pattern is supported.
     */
    private String validatePattern(String pattern) {
        Objects.requireNonNull(pattern, "Pattern cannot be null");

        if (!VALID_PATTERNS.contains(pattern)) {
            throw new IllegalArgumentException(
                "Invalid guard pattern: " + pattern + ". Must be one of: " + VALID_PATTERNS
            );
        }

        return pattern;
    }

    /**
     * Validates that the line number is non-negative.
     */
    private void validateLineNumber(int line) {
        if (line < 0) {
            throw new IllegalArgumentException("Line number must be >= 0, got: " + line);
        }
    }

    /**
     * Generates fix guidance text based on the guard pattern type.
     *
     * @param pattern the guard pattern type
     * @return descriptive fix guidance text
     */
    private String generateFixGuidance(String pattern) {
        return switch (pattern) {
            case "H_TODO" -> "Implement real logic or throw UnsupportedOperationException";
            case "H_MOCK" -> "Delete mock or implement real service";
            case "H_STUB" -> "Implement real method or throw exception";
            case "H_EMPTY" -> "Implement real logic or throw exception";
            case "H_FALLBACK" -> "Propagate exception instead of faking data";
            case "H_LIE" -> "Update code to match documentation";
            case "H_SILENT" -> "Throw exception instead of logging";
            case "H_ERROR" -> "Fix transient error and re-run validation";
            default -> throw new IllegalArgumentException("Unknown pattern: " + pattern);
        };
    }

    // Getters

    public String getPattern() {
        return pattern;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getFile() {
        return file;
    }

    public int getLine() {
        return line;
    }

    public String getContent() {
        return content;
    }

    public String getFixGuidance() {
        return fixGuidance;
    }

    // Builder pattern for fluent construction

    /**
     * Builder for creating GuardViolation instances with fluent API.
     */
    public static final class Builder {
        private String pattern;
        private Severity severity = Severity.FAIL; // Default severity
        private String file;
        private int line;
        private String content;

        public Builder pattern(String pattern) {
            this.pattern = pattern;
            return this;
        }

        public Builder severity(Severity severity) {
            this.severity = severity;
            return this;
        }

        public Builder severity(String severity) {
            this.severity = Severity.fromString(severity);
            return this;
        }

        public Builder file(String file) {
            this.file = file;
            return this;
        }

        public Builder line(int line) {
            this.line = line;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public GuardViolation build() {
            return new GuardViolation(pattern, severity, file, line, content);
        }
    }

    /**
     * Creates a new builder instance.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    // Standard object methods

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GuardViolation that = (GuardViolation) o;
        return line == that.line &&
               pattern.equals(that.pattern) &&
               severity.equals(that.severity) &&
               file.equals(that.file) &&
               content.equals(that.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pattern, severity, file, line, content);
    }

    @Override
    public String toString() {
        return "GuardViolation{" +
               "pattern='" + pattern + '\'' +
               ", severity=" + severity +
               ", file='" + file + '\'' +
               ", line=" + line +
               ", content='" + content + '\'' +
               '}';
    }

    /**
     * Creates a violation summary string suitable for logging.
     *
     * @return a formatted string describing the violation
     */
    public String toSummaryString() {
        return String.format("%s at %s:%d - %s", pattern, file, line, content);
    }
}