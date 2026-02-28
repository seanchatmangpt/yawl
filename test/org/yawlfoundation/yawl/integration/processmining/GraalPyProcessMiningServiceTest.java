/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * This file is part of YAWL. YAWL is free software: you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License.
 */
package org.yawlfoundation.yawl.integration.processmining;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link GraalPyProcessMiningService}.
 *
 * <p>These tests run on standard JDK (Temurin) where GraalPy is not available.
 * On standard JDK:</p>
 * <ul>
 *   <li>{@link GraalPyProcessMiningService#isHealthy()} returns {@code false}</li>
 *   <li>All process mining methods throw {@link IOException} wrapping
 *       {@link org.yawlfoundation.yawl.graalpy.PythonException}</li>
 * </ul>
 *
 * <p>Full pm4py functional tests (discoverDfg, tokenReplay, etc.) require
 * GraalVM JDK 24.1+ and pm4py installed in the GraalPy virtual environment.</p>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@DisplayName("GraalPyProcessMiningService")
class GraalPyProcessMiningServiceTest {

    private static final String MINIMAL_XES = """
            <?xml version="1.0" encoding="UTF-8"?>
            <log xes.version="1.0" xmlns="http://www.xes-standard.org/">
              <trace>
                <string key="concept:name" value="case-1"/>
                <event>
                  <string key="concept:name" value="Start"/>
                  <string key="lifecycle:transition" value="complete"/>
                </event>
                <event>
                  <string key="concept:name" value="End"/>
                  <string key="lifecycle:transition" value="complete"/>
                </event>
              </trace>
            </log>
            """;

    private static final String MINIMAL_PNML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <pnml>
              <net id="net1" type="http://www.pnml.org/version-2009/grammar/pnmlcoremodel">
                <place id="p1"><initialMarking><text>1</text></initialMarking></place>
                <place id="p2"/>
                <transition id="t1"><name><text>Start</text></name></transition>
                <arc id="a1" source="p1" target="t1"/>
                <arc id="a2" source="t1" target="p2"/>
              </net>
            </pnml>
            """;

    // ─── Null argument guards ─────────────────────────────────────────────────────

    @Test
    @DisplayName("tokenReplay throws NullPointerException for null pnmlXml")
    void tokenReplay_nullPnmlXml_throwsNpe() {
        try (var svc = new GraalPyProcessMiningService()) {
            assertThrows(NullPointerException.class,
                () -> svc.tokenReplay(null, MINIMAL_XES));
        }
    }

    @Test
    @DisplayName("tokenReplay throws NullPointerException for null xesXml")
    void tokenReplay_nullXesXml_throwsNpe() {
        try (var svc = new GraalPyProcessMiningService()) {
            assertThrows(NullPointerException.class,
                () -> svc.tokenReplay(MINIMAL_PNML, null));
        }
    }

    @Test
    @DisplayName("discoverDfg throws NullPointerException for null xesXml")
    void discoverDfg_nullXesXml_throwsNpe() {
        try (var svc = new GraalPyProcessMiningService()) {
            assertThrows(NullPointerException.class,
                () -> svc.discoverDfg(null));
        }
    }

    @Test
    @DisplayName("discoverAlphaPpp throws NullPointerException for null xesXml")
    void discoverAlphaPpp_nullXesXml_throwsNpe() {
        try (var svc = new GraalPyProcessMiningService()) {
            assertThrows(NullPointerException.class,
                () -> svc.discoverAlphaPpp(null));
        }
    }

    @Test
    @DisplayName("performanceAnalysis throws NullPointerException for null xesXml")
    void performanceAnalysis_nullXesXml_throwsNpe() {
        try (var svc = new GraalPyProcessMiningService()) {
            assertThrows(NullPointerException.class,
                () -> svc.performanceAnalysis(null));
        }
    }

    @Test
    @DisplayName("xesToOcel throws NullPointerException for null xesXml")
    void xesToOcel_nullXesXml_throwsNpe() {
        try (var svc = new GraalPyProcessMiningService()) {
            assertThrows(NullPointerException.class,
                () -> svc.xesToOcel(null));
        }
    }

    // ─── Standard JDK behaviour (GraalPy not available) ─────────────────────────

    @Test
    @DisplayName("isHealthy returns false when GraalPy runtime is unavailable")
    void isHealthy_graalPyNotAvailable_returnsFalse() {
        try (var svc = new GraalPyProcessMiningService()) {
            // On standard JDK (Temurin), GraalPy is not available; ping() returns false
            assertFalse(svc.isHealthy(),
                "isHealthy should return false when GraalPy runtime is not available");
        }
    }

    @Test
    @DisplayName("isHealthy does not throw when GraalPy runtime is unavailable")
    void isHealthy_graalPyNotAvailable_doesNotThrow() {
        try (var svc = new GraalPyProcessMiningService()) {
            assertDoesNotThrow(svc::isHealthy,
                "isHealthy must not throw; it returns false on unavailable runtime");
        }
    }

    @Test
    @DisplayName("tokenReplay wraps PythonException as IOException on unavailable runtime")
    void tokenReplay_graalPyNotAvailable_throwsIoException() {
        try (var svc = new GraalPyProcessMiningService()) {
            assertThrows(IOException.class,
                () -> svc.tokenReplay(MINIMAL_PNML, MINIMAL_XES),
                "tokenReplay should throw IOException when GraalPy runtime is not available");
        }
    }

    @Test
    @DisplayName("discoverDfg wraps PythonException as IOException on unavailable runtime")
    void discoverDfg_graalPyNotAvailable_throwsIoException() {
        try (var svc = new GraalPyProcessMiningService()) {
            assertThrows(IOException.class,
                () -> svc.discoverDfg(MINIMAL_XES),
                "discoverDfg should throw IOException when GraalPy runtime is not available");
        }
    }

    @Test
    @DisplayName("discoverAlphaPpp wraps PythonException as IOException on unavailable runtime")
    void discoverAlphaPpp_graalPyNotAvailable_throwsIoException() {
        try (var svc = new GraalPyProcessMiningService()) {
            assertThrows(IOException.class,
                () -> svc.discoverAlphaPpp(MINIMAL_XES),
                "discoverAlphaPpp should throw IOException when GraalPy runtime is not available");
        }
    }

    @Test
    @DisplayName("performanceAnalysis wraps PythonException as IOException on unavailable runtime")
    void performanceAnalysis_graalPyNotAvailable_throwsIoException() {
        try (var svc = new GraalPyProcessMiningService()) {
            assertThrows(IOException.class,
                () -> svc.performanceAnalysis(MINIMAL_XES),
                "performanceAnalysis should throw IOException when GraalPy runtime is not available");
        }
    }

    @Test
    @DisplayName("xesToOcel wraps PythonException as IOException on unavailable runtime")
    void xesToOcel_graalPyNotAvailable_throwsIoException() {
        try (var svc = new GraalPyProcessMiningService()) {
            assertThrows(IOException.class,
                () -> svc.xesToOcel(MINIMAL_XES),
                "xesToOcel should throw IOException when GraalPy runtime is not available");
        }
    }

    // ─── Interface conformance ────────────────────────────────────────────────────

    @Test
    @DisplayName("GraalPyProcessMiningService implements ProcessMiningService")
    void implementsProcessMiningService() {
        try (var svc = new GraalPyProcessMiningService()) {
            assertInstanceOf(ProcessMiningService.class, svc,
                "GraalPyProcessMiningService must implement ProcessMiningService");
        }
    }

    @Test
    @DisplayName("GraalPyProcessMiningService implements AutoCloseable")
    void implementsAutoCloseable() {
        try (var svc = new GraalPyProcessMiningService()) {
            assertInstanceOf(AutoCloseable.class, svc,
                "GraalPyProcessMiningService must implement AutoCloseable");
        }
    }

    @Test
    @DisplayName("close() is idempotent — may be called multiple times without error")
    void close_isIdempotent() {
        var svc = new GraalPyProcessMiningService();
        assertDoesNotThrow(() -> {
            svc.close();
            svc.close();   // second close must not throw
        }, "close() must be idempotent");
    }
}
