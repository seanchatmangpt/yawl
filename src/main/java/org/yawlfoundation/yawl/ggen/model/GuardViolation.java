package org.yawlfoundation.yawl.ggen.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Objects;
import java.util.Set;

/**
 * Represents a guard violation detected in generated code.
 *
 * Each violation indicates the presence of a forbidden pattern that violates
 * hyper-standards (H phase validation). Violations must be fixed before
 * proceeding to the invariants (Q) phase.
 *
 * @see org.yawlfoundation.yawl.ggen.validation.HyperStandardsValidator
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class GuardViolation {

    /**
     * Valid pattern names for guard violations.
     */
    public enum Pattern {
        H_TODO("H_TODO"),
        H_MOCK("H_MOCK"),
        H_STUB("H_STUB"),
        H_EMPTY("H_EMPTY"),
        H_FALLBACK("H_FALLBACK"),
        H_LIE("H_LIE"),
        H_SILENT("H_SILENT");

        private final String value;

        Pattern(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return value;
        }

        public static Pattern fromString(String text) {
            for (Pattern pattern : Pattern.values()) {
                if (pattern.value.equalsIgnoreCase(text)) {
                    return pattern;
                }
            }
            throw new IllegalArgumentException("No constant with text " + text + " found in Pattern enum");
        }
    }

    /**
     * Severity levels for guard violations.
     */
    public enum Severity {
        FAIL("FAIL"),
        WARN("WARN");

        private final String value;

        Severity(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return value;
        }
    }

    private final Pattern pattern;
    private final Severity severity;
    private final String file;
    private final int line;
    private final String content;
    private final String fixGuidance;

    /**
     * Constructor that auto-generates fix guidance based on pattern.
     * Validates input parameters and throws IllegalArgumentException for invalid values.
     *
     * @param pattern the violation pattern (H_TODO, H_MOCK, etc.)
     * @param severity the severity level (FAIL, WARN)
     * @param file the file path where violation was detected
     * @param line the line number of the violation (must be >= 1)
     * @param content the exact code content that violates the pattern
     */
    public GuardViolation(Pattern pattern, Severity severity, String file, int line, String content) {
        this.pattern = Objects.requireNonNull(pattern, "Pattern cannot be null");
        this.severity = Objects.requireNonNull(severity, "Severity cannot be null");
        this.file = Objects.requireNonNull(file, "File cannot be null");
        this.content = Objects.requireNonNull(content, "Content cannot be null");
        this.line = line;
        this.fixGuidance = generateFixGuidance(pattern);

        validate();
    }

    /**
     * Private constructor for use by the builder.
     */
    private GuardViolation(Builder builder) {
        this.pattern = Objects.requireNonNull(builder.pattern, "Pattern cannot be null");
        this.severity = Objects.requireNonNull(builder.severity, "Severity cannot be null");
        this.file = Objects.requireNonNull(builder.file, "File cannot be null");
        this.line = builder.line;
        this.content = Objects.requireNonNull(builder.content, "Content cannot be null");
        this.fixGuidance = generateFixGuidance(pattern);

        validate();
    }

    /**
     * Generates fix guidance based on the violation pattern.
     * This is called by both constructors to ensure consistency.
     *
     * @param pattern the violation pattern
     * @return fix guidance message
     */
    private static String generateFixGuidance(Pattern pattern) {
        return switch (pattern) {
            case H_TODO -> "Implement real logic or throw UnsupportedOperationException";
            case H_MOCK -> "Delete mock or implement real service";
            case H_STUB -> "Implement real method or throw exception";
            case H_EMPTY -> "Implement real logic or throw exception";
            case H_FALLBACK -> "Propagate exception instead of faking data";
            case H_LIE -> "Update code to match documentation";
            case H_SILENT -> "Throw exception instead of logging";
        };
    }

    /**
     * Validates the violation state.
     * Called by constructors to ensure invariants are maintained.
     *
     * @throws IllegalArgumentException if validation fails
     */
    private void validate() {
        if (line < 1) {
            throw new IllegalArgumentException("Line number must be >= 1, got: " + line);
        }
        if (content.isBlank()) {
            throw new IllegalArgumentException("Content cannot be blank");
        }
        if (fixGuidance == null || fixGuidance.isBlank()) {
            throw new IllegalStateException("Fix guidance cannot be null or blank");
        }
    }

    // Getters
    public Pattern getPattern() {
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
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for creating GuardViolation instances.
     * Provides fluent API for constructing violations step by step.
     */
    public static class Builder {
        private Pattern pattern;
        private Severity severity = Severity.FAIL;
        private String file;
        private int line;
        private String content;

        public Builder pattern(Pattern pattern) {
            this.pattern = pattern;
            return this;
        }

        public Builder pattern(String pattern) {
            this.pattern = Pattern.fromString(pattern);
            return this;
        }

        public Builder severity(Severity severity) {
            this.severity = severity;
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
            return new GuardViolation(this);
        }
    }

    // Utility methods
    public boolean isFail() {
        return severity == Severity.FAIL;
    }

    public boolean isWarn() {
        return severity == Severity.WARN;
    }

    public String getSummary() {
        return String.format("%s at %s:%d: %s", pattern, file, line, content);
    }

    // Equals and hashCode
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GuardViolation that = (GuardViolation) o;
        return line == that.line &&
               pattern == that.pattern &&
               severity == that.severity &&
               Objects.equals(file, that.file) &&
               Objects.equals(content, that.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pattern, severity, file, line, content);
    }

    @Override
    public String toString() {
        return String.format("GuardViolation{pattern=%s, severity=%s, file='%s', line=%d, content='%s', fixGuidance='%s'}",
                pattern, severity, file, line, content, fixGuidance);
    }
}