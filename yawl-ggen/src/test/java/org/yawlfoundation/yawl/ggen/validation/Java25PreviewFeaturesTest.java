/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yawlfoundation.yawl.ggen.validation.model.GuardSummary;
import org.yawlfoundation.yawl.ggen.validation.model.GuardViolation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies Java 25 preview features in the ggen validation module.
 *
 * <h2>Features under test</h2>
 * <ul>
 *   <li><b>JEP 455</b> — Primitive Types in Patterns ({@code case int n when n == 0})
 *       in {@link GuardSummary#getSeverityLabel()}, {@link GuardViolation#getLocationBand()},
 *       and the {@code severityBand} helper in {@code HyperStandardsValidator}.</li>
 *   <li><b>JEP 492</b> — Flexible Constructor Bodies (statement before {@code super()})
 *       in {@link BoundedRegexGuardChecker}.</li>
 * </ul>
 *
 * <p>All tests run in the same forked surefire JVM started with {@code --enable-preview}.
 * If the flag is absent, loading any preview-compiled class throws
 * {@link java.lang.ClassFormatError} before the first test method executes,
 * providing an immediate build-break signal.
 */
@DisplayName("Java 25 Preview Features — JEP 455 + JEP 492")
@SuppressWarnings("preview")
class Java25PreviewFeaturesTest {

    // Neutral content string used wherever GuardViolation needs a content arg.
    // Only the 'line' field drives getLocationBand(); content is irrelevant there.
    private static final String NEUTRAL_CONTENT = "guard-trigger-line";

    // Custom regex used in BoundedRegexGuardChecker tests.
    // Matches a project-specific marker token that has no overlap with any H_* pattern.
    private static final String BOUND_REGEX = "YAWL_GUARD_BOUNDARY";

    // -------------------------------------------------------------------------
    // JEP 455 — GuardSummary.getSeverityLabel()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("JEP 455 | getSeverityLabel: zero violations → GREEN")
    void jep455_getSeverityLabel_zero() {
        GuardSummary summary = new GuardSummary();
        assertEquals("GREEN", summary.getSeverityLabel());
    }

    @Test
    @DisplayName("JEP 455 | getSeverityLabel: 1 violation → YELLOW")
    void jep455_getSeverityLabel_one() {
        GuardSummary summary = new GuardSummary();
        summary.increment("H_TODO");
        assertEquals("YELLOW", summary.getSeverityLabel());
    }

    @Test
    @DisplayName("JEP 455 | getSeverityLabel: 5 violations → YELLOW (upper boundary)")
    void jep455_getSeverityLabel_five() {
        GuardSummary summary = new GuardSummary();
        for (int i = 0; i < 5; i++) summary.increment("H_TODO");
        assertEquals("YELLOW", summary.getSeverityLabel());
    }

    @Test
    @DisplayName("JEP 455 | getSeverityLabel: 6+ violations → RED")
    void jep455_getSeverityLabel_many() {
        GuardSummary summary = new GuardSummary();
        for (int i = 0; i < 6; i++) summary.increment("H_STUB");
        assertEquals("RED", summary.getSeverityLabel());
    }

    // -------------------------------------------------------------------------
    // JEP 455 — GuardViolation.getLocationBand()
    // Only the 'line' field is exercised; content uses the neutral constant above.
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("JEP 455 | getLocationBand: line 1 → imports")
    void jep455_getLocationBand_imports() {
        GuardViolation v = new GuardViolation("H_TODO", "FAIL", 1, NEUTRAL_CONTENT);
        assertEquals("imports", v.getLocationBand());
    }

    @Test
    @DisplayName("JEP 455 | getLocationBand: line 10 → imports (boundary)")
    void jep455_getLocationBand_imports_boundary() {
        GuardViolation v = new GuardViolation("H_TODO", "FAIL", 10, NEUTRAL_CONTENT);
        assertEquals("imports", v.getLocationBand());
    }

    @Test
    @DisplayName("JEP 455 | getLocationBand: line 11 → class-header")
    void jep455_getLocationBand_classHeader() {
        GuardViolation v = new GuardViolation("H_EMPTY", "FAIL", 11, NEUTRAL_CONTENT);
        assertEquals("class-header", v.getLocationBand());
    }

    @Test
    @DisplayName("JEP 455 | getLocationBand: line 100 → body")
    void jep455_getLocationBand_body() {
        GuardViolation v = new GuardViolation("H_EMPTY", "FAIL", 100, NEUTRAL_CONTENT);
        assertEquals("body", v.getLocationBand());
    }

    @Test
    @DisplayName("JEP 455 | getLocationBand: line 200 → body (boundary)")
    void jep455_getLocationBand_bodyBoundary() {
        GuardViolation v = new GuardViolation("H_FALLBACK", "FAIL", 200, NEUTRAL_CONTENT);
        assertEquals("body", v.getLocationBand());
    }

    @Test
    @DisplayName("JEP 455 | getLocationBand: line 201 → end")
    void jep455_getLocationBand_end() {
        GuardViolation v = new GuardViolation("H_LIE", "FAIL", 201, NEUTRAL_CONTENT);
        assertEquals("end", v.getLocationBand());
    }

    // -------------------------------------------------------------------------
    // JEP 492 — BoundedRegexGuardChecker (flexible constructor bodies)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("JEP 492 | BoundedRegexGuardChecker rejects maxViolationsPerFile ≤ 0")
    void jep492_boundedChecker_rejectsNonPositiveMax() {
        assertThrows(IllegalArgumentException.class,
            () -> new BoundedRegexGuardChecker("H_TODO", BOUND_REGEX, 0),
            "maxViolationsPerFile == 0 must throw IAE");

        assertThrows(IllegalArgumentException.class,
            () -> new BoundedRegexGuardChecker("H_TODO", BOUND_REGEX, -5),
            "maxViolationsPerFile < 0 must throw IAE");
    }

    @Test
    @DisplayName("JEP 492 | BoundedRegexGuardChecker caps violations at maxViolationsPerFile")
    void jep492_boundedChecker_capsViolations(@TempDir Path tmp) throws IOException {
        // Write a Java file with 4 lines containing the boundary marker.
        Path src = tmp.resolve("Many.java");
        String marker = BOUND_REGEX;
        Files.writeString(src,
            "public class Many {\n" +
            "    String a = \"" + marker + " one\";\n" +
            "    String b = \"" + marker + " two\";\n" +
            "    String c = \"" + marker + " three\";\n" +
            "    String d = \"" + marker + " four\";\n" +
            "}\n");

        BoundedRegexGuardChecker checker =
            new BoundedRegexGuardChecker("H_TODO", BOUND_REGEX, 2);

        List<GuardViolation> violations = checker.check(src);
        assertEquals(2, violations.size(),
            "Should cap at maxViolationsPerFile=2 even though 4 violations exist");
        assertEquals(2, checker.getMaxViolationsPerFile());
    }

    @Test
    @DisplayName("JEP 492 | BoundedRegexGuardChecker returns all when count < cap")
    void jep492_boundedChecker_returnsAllWhenUnderCap(@TempDir Path tmp) throws IOException {
        Path src = tmp.resolve("Few.java");
        String marker = BOUND_REGEX;
        Files.writeString(src,
            "public class Few {\n" +
            "    String a = \"" + marker + " one\";\n" +
            "}\n");

        BoundedRegexGuardChecker checker =
            new BoundedRegexGuardChecker("H_TODO", BOUND_REGEX, 10);

        List<GuardViolation> violations = checker.check(src);
        assertEquals(1, violations.size(),
            "Should return all 1 violation (well under cap of 10)");
    }
}
