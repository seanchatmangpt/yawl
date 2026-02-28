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
 * Base implementation of GuardChecker using regex pattern matching.
 * Suitable for detecting simple text patterns like TODO comments and mock identifiers.
 * Scans line-by-line for efficiency.
 */
public class RegexGuardChecker implements GuardChecker {
    private final Pattern pattern;
    private final String patternName;
    private final Severity severity;

    /**
     * Create a new regex-based guard checker.
     *
     * @param patternName the guard pattern name (e.g., H_TODO)
     * @param regexPattern the regex pattern to match (compiled for efficiency)
     * @param severity the severity level (WARN or FAIL)
     */
    public RegexGuardChecker(String patternName, String regexPattern, Severity severity) {
        this.patternName = Objects.requireNonNull(patternName, "patternName must not be null");
        Objects.requireNonNull(regexPattern, "regexPattern must not be null");
        this.pattern = Pattern.compile(regexPattern);
        this.severity = Objects.requireNonNull(severity, "severity must not be null");
    }

    /**
     * Create a new regex-based guard checker with default FAIL severity.
     *
     * @param patternName the guard pattern name
     * @param regexPattern the regex pattern to match
     */
    public RegexGuardChecker(String patternName, String regexPattern) {
        this(patternName, regexPattern, Severity.FAIL);
    }

    @Override
    public List<GuardViolation> check(Path javaSource) throws IOException {
        List<GuardViolation> violations = new ArrayList<>();

        List<String> lines = Files.readAllLines(javaSource);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher matcher = pattern.matcher(line);

            if (matcher.find()) {
                violations.add(new GuardViolation(
                    patternName,
                    severity.name(),
                    i + 1,  // Line numbers are 1-indexed
                    line.trim()
                ));
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

    @Override
    public String toString() {
        return "RegexGuardChecker{" +
                "pattern=" + patternName +
                ", severity=" + severity +
                '}';
    }
}
