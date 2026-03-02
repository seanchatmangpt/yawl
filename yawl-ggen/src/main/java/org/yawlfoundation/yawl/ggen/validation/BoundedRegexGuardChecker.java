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
import java.nio.file.Path;
import java.util.List;

/**
 * A regex-based guard checker that caps the number of reported violations per file.
 *
 * <h2>JEP 492 — Flexible Constructor Bodies (Java 25 preview)</h2>
 *
 * <p>Prior to JEP 492 (Flexible Constructor Bodies), the Java language required that any
 * call to {@code super()} or {@code this()} appear as the <em>first statement</em> in a
 * constructor body. Validation of constructor arguments had to be done via a static helper
 * method passed into the delegating call:
 *
 * <pre>{@code
 * // Pre-JEP 492: validation buried in a static helper
 * public BoundedRegexGuardChecker(String name, String regex, int max) {
 *     super(name, regex, validateMax(max));  // can't throw meaningfully before super()
 *     this.max = max;
 * }
 * private static Severity validateMax(int max) {
 *     if (max <= 0) throw new IllegalArgumentException("max <= 0");
 *     return Severity.FAIL;
 * }
 * }</pre>
 *
 * <p>With JEP 492, statements that do <em>not</em> reference {@code this} may appear
 * before {@code super()} or {@code this()}, making constructor validation natural and
 * readable:
 *
 * <pre>{@code
 * public BoundedRegexGuardChecker(String name, String regex, int max) {
 *     if (max <= 0) throw new IllegalArgumentException(...);  // ← JEP 492
 *     super(name, regex, Severity.FAIL);
 *     this.maxViolationsPerFile = max;
 * }
 * }</pre>
 *
 * <p>The {@code @SuppressWarnings("preview")} annotation silences the per-compilation-unit
 * preview-feature note emitted by javac when {@code --enable-preview} is active.
 *
 * @see RegexGuardChecker
 */
@SuppressWarnings("preview")
public class BoundedRegexGuardChecker extends RegexGuardChecker {

    private final int maxViolationsPerFile;

    /**
     * Create a bounded regex guard checker.
     *
     * <p><b>JEP 492</b>: the {@code if} guard before {@code super()} is valid because it
     * does not reference {@code this} — it only reads a local parameter.
     *
     * @param patternName        guard pattern identifier (e.g., "H_TODO")
     * @param regexPattern       regex pattern to match per line
     * @param maxViolationsPerFile maximum violations to report per file (must be ≥ 1)
     * @throws IllegalArgumentException if {@code maxViolationsPerFile} is ≤ 0
     */
    public BoundedRegexGuardChecker(String patternName, String regexPattern,
                                    int maxViolationsPerFile) {
        // JEP 492: statement before super() — validates the argument without boxing or
        // a static helper method.  Prior to Java 25 preview this was a compile error:
        //   "call to super must be first statement in constructor".
        if (maxViolationsPerFile <= 0)
            throw new IllegalArgumentException(
                "maxViolationsPerFile must be >= 1, got: " + maxViolationsPerFile);
        super(patternName, regexPattern, Severity.FAIL);
        this.maxViolationsPerFile = maxViolationsPerFile;
    }

    /**
     * Check the file and return at most {@code maxViolationsPerFile} violations.
     * If more are found they are silently truncated — the caller is expected to act on
     * the first {@code N} occurrences and re-run after fixing them.
     *
     * @param javaSource the Java source file to scan
     * @return violations found, capped at {@code maxViolationsPerFile}
     * @throws IOException if the file cannot be read
     */
    @Override
    public List<GuardViolation> check(Path javaSource) throws IOException {
        List<GuardViolation> all = super.check(javaSource);
        return all.size() > maxViolationsPerFile
            ? all.subList(0, maxViolationsPerFile)
            : all;
    }

    /**
     * @return the per-file violation cap configured at construction time
     */
    public int getMaxViolationsPerFile() {
        return maxViolationsPerFile;
    }
}
