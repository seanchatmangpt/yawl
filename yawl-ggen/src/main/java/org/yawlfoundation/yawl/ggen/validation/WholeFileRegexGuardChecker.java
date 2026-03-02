/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.validation;

import org.yawlfoundation.yawl.ggen.validation.model.GuardViolation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Guard checker that matches a regex against the whole file content (not line-by-line).
 *
 * <p>Used for patterns that span multiple lines, such as empty catch blocks:
 * <pre>{@code
 * catch (Exception e) {
 * }
 * }</pre>
 *
 * <p>The pattern is compiled with {@link Pattern#DOTALL} so {@code .} matches newlines,
 * enabling patterns like {@code catch\s*\([^)]+\)\s*\{\s*\}} to span lines.
 *
 * <p>Line numbers are computed by counting preceding newlines in the file.
 */
public class WholeFileRegexGuardChecker implements GuardChecker {

    private final Pattern pattern;
    private final String patternName;
    private final Severity severity;

    /**
     * Create a whole-file regex guard checker.
     *
     * @param patternName  the guard pattern name (e.g., H_SWALLOWED)
     * @param regexPattern the regex to match against whole file content (DOTALL mode)
     * @param severity     the severity level
     */
    public WholeFileRegexGuardChecker(String patternName, String regexPattern, Severity severity) {
        this.patternName = Objects.requireNonNull(patternName, "patternName must not be null");
        Objects.requireNonNull(regexPattern, "regexPattern must not be null");
        this.pattern = Pattern.compile(regexPattern, Pattern.DOTALL);
        this.severity = Objects.requireNonNull(severity, "severity must not be null");
    }

    @Override
    public List<GuardViolation> check(Path javaSource) throws IOException {
        String content = Files.readString(javaSource);
        List<GuardViolation> violations = new ArrayList<>();

        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            int lineNumber = lineOf(content, matcher.start());
            String matchedText = matcher.group().strip();
            // Condense multi-line matches for readability in the receipt
            String displayText = matchedText.replace('\n', ' ').replaceAll("\\s{2,}", " ");
            violations.add(new GuardViolation(patternName, severity.name(), lineNumber, displayText));
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
     * Compute 1-based line number for a character offset within a string.
     * Counts {@code \n} occurrences before {@code charOffset}.
     */
    private static int lineOf(String content, int charOffset) {
        int line = 1;
        for (int i = 0; i < charOffset; i++) {
            if (content.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    @Override
    public String toString() {
        return "WholeFileRegexGuardChecker{pattern=" + patternName + ", severity=" + severity + '}';
    }
}
