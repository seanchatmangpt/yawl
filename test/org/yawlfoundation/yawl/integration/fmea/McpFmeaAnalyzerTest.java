/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.fmea;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.engine.interfce.interfaceA.InterfaceA_EnvironmentBasedClient;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.integration.mcp.spec.YawlMcpContext;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD tests for {@link McpFmeaAnalyzer}.
 *
 * <p>Real objects only — no mocks. Each nested class covers one analyzer method.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@DisplayName("McpFmeaAnalyzer")
class McpFmeaAnalyzerTest {

    private final McpFmeaAnalyzer analyzer = new McpFmeaAnalyzer();

    // -----------------------------------------------------------------------
    // Test helpers
    // -----------------------------------------------------------------------

    /** Returns a valid YawlMcpContext with the given session handle and no Z.AI. */
    private static YawlMcpContext context(String sessionHandle) {
        return new YawlMcpContext(
            new InterfaceB_EnvironmentBasedClient("http://localhost:8080/yawl/ib"),
            new InterfaceA_EnvironmentBasedClient("http://localhost:8080/yawl/ia"),
            sessionHandle,
            null   // Z.AI optional; isZaiAvailable() = false
        );
    }

    // -----------------------------------------------------------------------
    // analyzeContext — FM_M2, FM_M7
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("analyzeContext")
    class AnalyzeContext {

        @Test
        @DisplayName("engine auth failure → FM_M2 violation")
        void engineAuthFailure_fm_m2() {
            YawlMcpContext ctx = context("<failure>authFailed</failure>");
            McpFmeaReport report = analyzer.analyzeContext(ctx, 1);

            assertAll(
                () -> assertFalse(report.isClean()),
                () -> assertEquals("RED", report.status()),
                () -> assertEquals(1, report.violations().size()),
                () -> assertEquals(McpFailureModeType.FM_M2_ENGINE_AUTH_FAILURE,
                                   report.violations().get(0).mode())
            );
        }

        @Test
        @DisplayName("valid session handle → no FM_M2")
        void validSession_noFm_m2() {
            YawlMcpContext ctx = context("valid-session-handle-abc123");
            McpFmeaReport report = analyzer.analyzeContext(ctx, 1);

            assertTrue(report.violations().stream()
                .noneMatch(v -> v.mode() == McpFailureModeType.FM_M2_ENGINE_AUTH_FAILURE));
        }

        @Test
        @DisplayName("providerCount = 0 → FM_M7 violation")
        void noProviders_fm_m7() {
            YawlMcpContext ctx = context("valid-session");
            McpFmeaReport report = analyzer.analyzeContext(ctx, 0);

            assertAll(
                () -> assertFalse(report.isClean()),
                () -> assertEquals(1, report.violations().size()),
                () -> assertEquals(McpFailureModeType.FM_M7_NO_PROVIDERS_REGISTERED,
                                   report.violations().get(0).mode())
            );
        }

        @Test
        @DisplayName("providerCount negative → FM_M7 violation")
        void negativeProviderCount_fm_m7() {
            YawlMcpContext ctx = context("valid-session");
            McpFmeaReport report = analyzer.analyzeContext(ctx, -1);

            assertTrue(report.violations().stream()
                .anyMatch(v -> v.mode() == McpFailureModeType.FM_M7_NO_PROVIDERS_REGISTERED));
        }

        @Test
        @DisplayName("providerCount = 1 → no FM_M7")
        void oneProvider_noFm_m7() {
            YawlMcpContext ctx = context("valid-session");
            McpFmeaReport report = analyzer.analyzeContext(ctx, 1);

            assertTrue(report.violations().stream()
                .noneMatch(v -> v.mode() == McpFailureModeType.FM_M7_NO_PROVIDERS_REGISTERED));
        }

        @Test
        @DisplayName("auth failure + no providers → FM_M2 and FM_M7")
        void authFailure_noProviders_twoViolations() {
            YawlMcpContext ctx = context("<failure>badCreds</failure>");
            McpFmeaReport report = analyzer.analyzeContext(ctx, 0);

            assertAll(
                () -> assertEquals(2, report.violations().size()),
                () -> assertTrue(report.violations().stream()
                    .anyMatch(v -> v.mode() == McpFailureModeType.FM_M2_ENGINE_AUTH_FAILURE)),
                () -> assertTrue(report.violations().stream()
                    .anyMatch(v -> v.mode() == McpFailureModeType.FM_M7_NO_PROVIDERS_REGISTERED))
            );
        }

        @Test
        @DisplayName("valid session + providerCount = 2 → GREEN")
        void clean_green() {
            YawlMcpContext ctx = context("session-xyz");
            McpFmeaReport report = analyzer.analyzeContext(ctx, 2);

            assertAll(
                () -> assertTrue(report.isClean()),
                () -> assertEquals("GREEN", report.status()),
                () -> assertEquals(0, report.totalRpn())
            );
        }

        @Test
        @DisplayName("null context → NullPointerException")
        void nullContext_throws() {
            assertThrows(NullPointerException.class,
                () -> analyzer.analyzeContext(null, 1));
        }
    }

    // -----------------------------------------------------------------------
    // analyzeToolLookup — FM_M1
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("analyzeToolLookup")
    class AnalyzeToolLookup {

        @Test
        @DisplayName("tool absent from registry → FM_M1 violation")
        void toolNotFound_fm_m1() {
            McpFmeaReport report = analyzer.analyzeToolLookup(
                "launch_case",
                Set.of("list_cases", "get_workitems"));

            assertAll(
                () -> assertFalse(report.isClean()),
                () -> assertEquals("RED", report.status()),
                () -> assertEquals(1, report.violations().size()),
                () -> assertEquals(McpFailureModeType.FM_M1_TOOL_NOT_FOUND,
                                   report.violations().get(0).mode())
            );
        }

        @Test
        @DisplayName("tool present in registry → no FM_M1")
        void toolFound_noFm_m1() {
            McpFmeaReport report = analyzer.analyzeToolLookup(
                "launch_case",
                Set.of("launch_case", "list_cases"));

            assertTrue(report.isClean());
        }

        @Test
        @DisplayName("tool found in singleton registry → GREEN")
        void singletonRegistry_toolPresent_green() {
            McpFmeaReport report = analyzer.analyzeToolLookup(
                "get_workitems",
                Set.of("get_workitems"));

            assertAll(
                () -> assertTrue(report.isClean()),
                () -> assertEquals("GREEN", report.status())
            );
        }

        @Test
        @DisplayName("empty registry → FM_M1 violation")
        void emptyRegistry_fm_m1() {
            McpFmeaReport report = analyzer.analyzeToolLookup("any_tool", Set.of());

            assertFalse(report.isClean());
            assertEquals(McpFailureModeType.FM_M1_TOOL_NOT_FOUND,
                         report.violations().get(0).mode());
        }

        @Test
        @DisplayName("null toolName → NullPointerException")
        void nullToolName_throws() {
            assertThrows(NullPointerException.class,
                () -> analyzer.analyzeToolLookup(null, Set.of("tool")));
        }

        @Test
        @DisplayName("null registeredToolNames → NullPointerException")
        void nullRegistry_throws() {
            assertThrows(NullPointerException.class,
                () -> analyzer.analyzeToolLookup("launch_case", null));
        }
    }

    // -----------------------------------------------------------------------
    // analyzeEnvironment — FM_M4
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("analyzeEnvironment")
    class AnalyzeEnvironment {

        @Test
        @DisplayName("null engineUrl → FM_M4 violation")
        void nullUrl_fm_m4() {
            McpFmeaReport report = analyzer.analyzeEnvironment(null, "admin", "pass");

            assertFalse(report.isClean());
            assertEquals(McpFailureModeType.FM_M4_MISSING_ENV_VAR,
                         report.violations().get(0).mode());
        }

        @Test
        @DisplayName("blank engineUrl → FM_M4 violation")
        void blankUrl_fm_m4() {
            McpFmeaReport report = analyzer.analyzeEnvironment("  ", "admin", "pass");

            assertFalse(report.isClean());
        }

        @Test
        @DisplayName("null username → FM_M4 violation")
        void nullUsername_fm_m4() {
            McpFmeaReport report = analyzer.analyzeEnvironment(
                "http://localhost:8080", null, "pass");

            assertFalse(report.isClean());
        }

        @Test
        @DisplayName("blank password → FM_M4 violation")
        void blankPassword_fm_m4() {
            McpFmeaReport report = analyzer.analyzeEnvironment(
                "http://localhost:8080", "admin", "");

            assertFalse(report.isClean());
        }

        @Test
        @DisplayName("all three missing → single FM_M4 violation listing all missing")
        void allMissing_singleViolation() {
            McpFmeaReport report = analyzer.analyzeEnvironment(null, null, null);

            assertAll(
                () -> assertEquals(1, report.violations().size()),
                () -> assertEquals(McpFailureModeType.FM_M4_MISSING_ENV_VAR,
                                   report.violations().get(0).mode()),
                () -> assertTrue(report.violations().get(0).evidence()
                    .contains("YAWL_ENGINE_URL"))
            );
        }

        @Test
        @DisplayName("all three populated → GREEN")
        void allPresent_green() {
            McpFmeaReport report = analyzer.analyzeEnvironment(
                "http://localhost:8080", "admin", "password");

            assertAll(
                () -> assertTrue(report.isClean()),
                () -> assertEquals("GREEN", report.status()),
                () -> assertEquals(0, report.totalRpn())
            );
        }
    }

    // -----------------------------------------------------------------------
    // analyzeServiceHealth — FM_M3, FM_M5, FM_M6
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("analyzeServiceHealth")
    class AnalyzeServiceHealth {

        @Test
        @DisplayName("zaiAvailable=false → FM_M3 violation")
        void zaiUnavailable_fm_m3() {
            McpFmeaReport report = analyzer.analyzeServiceHealth(
                "yawl-mcp", false, "CLOSED", null, 0);

            assertAll(
                () -> assertFalse(report.isClean()),
                () -> assertEquals(1, report.violations().size()),
                () -> assertEquals(McpFailureModeType.FM_M3_ZAI_SERVICE_UNAVAILABLE,
                                   report.violations().get(0).mode())
            );
        }

        @Test
        @DisplayName("zaiAvailable=true → no FM_M3")
        void zaiAvailable_noFm_m3() {
            McpFmeaReport report = analyzer.analyzeServiceHealth(
                "yawl-mcp", true, "CLOSED", null, 0);

            assertTrue(report.violations().stream()
                .noneMatch(v -> v.mode() == McpFailureModeType.FM_M3_ZAI_SERVICE_UNAVAILABLE));
        }

        @Test
        @DisplayName("circuit breaker OPEN → FM_M5 violation")
        void circuitOpen_fm_m5() {
            McpFmeaReport report = analyzer.analyzeServiceHealth(
                "yawl-mcp", true, "OPEN", null, 0);

            assertAll(
                () -> assertFalse(report.isClean()),
                () -> assertEquals(1, report.violations().size()),
                () -> assertEquals(McpFailureModeType.FM_M5_CIRCUIT_BREAKER_OPEN,
                                   report.violations().get(0).mode())
            );
        }

        @Test
        @DisplayName("circuit breaker CLOSED → no FM_M5")
        void circuitClosed_noFm_m5() {
            McpFmeaReport report = analyzer.analyzeServiceHealth(
                "yawl-mcp", true, "CLOSED", null, 0);

            assertTrue(report.violations().stream()
                .noneMatch(v -> v.mode() == McpFailureModeType.FM_M5_CIRCUIT_BREAKER_OPEN));
        }

        @Test
        @DisplayName("circuit breaker HALF_OPEN → no FM_M5")
        void circuitHalfOpen_noFm_m5() {
            McpFmeaReport report = analyzer.analyzeServiceHealth(
                "yawl-mcp", true, "HALF_OPEN", null, 0);

            assertTrue(report.violations().stream()
                .noneMatch(v -> v.mode() == McpFailureModeType.FM_M5_CIRCUIT_BREAKER_OPEN));
        }

        @Test
        @DisplayName("tool execution failed after retries → FM_M6 violation")
        void toolExecutionFailed_fm_m6() {
            McpFmeaReport report = analyzer.analyzeServiceHealth(
                "yawl-mcp", true, "CLOSED", "Connection refused to engine", 3);

            assertAll(
                () -> assertFalse(report.isClean()),
                () -> assertEquals(1, report.violations().size()),
                () -> assertEquals(McpFailureModeType.FM_M6_TOOL_EXECUTION_FAILURE,
                                   report.violations().get(0).mode())
            );
        }

        @Test
        @DisplayName("lastFailureMessage null → no FM_M6")
        void noFailureMessage_noFm_m6() {
            McpFmeaReport report = analyzer.analyzeServiceHealth(
                "yawl-mcp", true, "CLOSED", null, 3);

            assertTrue(report.violations().stream()
                .noneMatch(v -> v.mode() == McpFailureModeType.FM_M6_TOOL_EXECUTION_FAILURE));
        }

        @Test
        @DisplayName("attemptNumber = 0 → no FM_M6 even with failure message")
        void attemptZero_noFm_m6() {
            McpFmeaReport report = analyzer.analyzeServiceHealth(
                "yawl-mcp", true, "CLOSED", "Some error", 0);

            assertTrue(report.violations().stream()
                .noneMatch(v -> v.mode() == McpFailureModeType.FM_M6_TOOL_EXECUTION_FAILURE));
        }

        @Test
        @DisplayName("zai unavailable + circuit OPEN + tool failure → 3 violations")
        void allThree_threeViolations() {
            McpFmeaReport report = analyzer.analyzeServiceHealth(
                "yawl-mcp", false, "OPEN", "Timeout after 5000ms", 2);

            assertAll(
                () -> assertEquals(3, report.violations().size()),
                () -> assertTrue(report.violations().stream()
                    .anyMatch(v -> v.mode() == McpFailureModeType.FM_M3_ZAI_SERVICE_UNAVAILABLE)),
                () -> assertTrue(report.violations().stream()
                    .anyMatch(v -> v.mode() == McpFailureModeType.FM_M5_CIRCUIT_BREAKER_OPEN)),
                () -> assertTrue(report.violations().stream()
                    .anyMatch(v -> v.mode() == McpFailureModeType.FM_M6_TOOL_EXECUTION_FAILURE))
            );
        }

        @Test
        @DisplayName("all healthy → GREEN")
        void allHealthy_green() {
            McpFmeaReport report = analyzer.analyzeServiceHealth(
                "yawl-mcp", true, "CLOSED", null, 0);

            assertAll(
                () -> assertTrue(report.isClean()),
                () -> assertEquals("GREEN", report.status()),
                () -> assertEquals(0, report.totalRpn())
            );
        }

        @Test
        @DisplayName("null serverName → NullPointerException")
        void nullServerName_throws() {
            assertThrows(NullPointerException.class,
                () -> analyzer.analyzeServiceHealth(null, true, "CLOSED", null, 0));
        }

        @Test
        @DisplayName("null circuitBreakerState → NullPointerException")
        void nullCircuitState_throws() {
            assertThrows(NullPointerException.class,
                () -> analyzer.analyzeServiceHealth("yawl-mcp", true, null, null, 0));
        }
    }

    // -----------------------------------------------------------------------
    // FailureModeTypeRpn — FM_M1 through FM_M7
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("FailureModeTypeRpn")
    class FailureModeTypeRpn {

        @Test
        @DisplayName("all RPN values are positive")
        void allRpnsPositive() {
            for (McpFailureModeType mode : McpFailureModeType.values()) {
                assertTrue(mode.rpn() > 0,
                    "Expected positive RPN for " + mode.name() + " but got " + mode.rpn());
            }
        }

        @Test
        @DisplayName("RPN = Severity × Occurrence × Detection")
        void rpnFormula() {
            for (McpFailureModeType mode : McpFailureModeType.values()) {
                int expected = mode.getSeverity() * mode.getOccurrence() * mode.getDetection();
                assertEquals(expected, mode.rpn(),
                    "RPN mismatch for " + mode.name());
            }
        }

        @Test
        @DisplayName("all descriptions and mitigations are non-blank")
        void descriptionsAndMitigationsNonBlank() {
            for (McpFailureModeType mode : McpFailureModeType.values()) {
                assertAll(
                    () -> assertFalse(mode.getDescription().isBlank(),
                        "description blank for " + mode.name()),
                    () -> assertFalse(mode.getMitigation().isBlank(),
                        "mitigation blank for " + mode.name())
                );
            }
        }

        @Test
        @DisplayName("FM_M6 has highest RPN (tool execution failure is highest risk)")
        void fm_m6_highestRpn() {
            int fm_m6_rpn = McpFailureModeType.FM_M6_TOOL_EXECUTION_FAILURE.rpn();
            assertEquals(140, fm_m6_rpn);
        }

        @Test
        @DisplayName("FM_M5 circuit breaker RPN = 120")
        void fm_m5_rpn() {
            assertEquals(120, McpFailureModeType.FM_M5_CIRCUIT_BREAKER_OPEN.rpn());
        }
    }

    // -----------------------------------------------------------------------
    // ReportBehaviour
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("ReportBehaviour")
    class ReportBehaviour {

        @Test
        @DisplayName("violations list is immutable")
        void violationsList_isImmutable() {
            McpFmeaReport report = analyzer.analyzeToolLookup("missing", Set.of("tool_a"));
            assertThrows(UnsupportedOperationException.class,
                () -> report.violations().clear());
        }

        @Test
        @DisplayName("status GREEN when no violations")
        void statusGreen_noViolations() {
            McpFmeaReport report = analyzer.analyzeToolLookup("tool_a", Set.of("tool_a"));
            assertEquals("GREEN", report.status());
        }

        @Test
        @DisplayName("status RED when any violation")
        void statusRed_anyViolation() {
            McpFmeaReport report = analyzer.analyzeToolLookup("missing", Set.of("tool_a"));
            assertEquals("RED", report.status());
        }

        @Test
        @DisplayName("totalRpn sums violation RPNs")
        void totalRpn_sumOfViolationRpns() {
            McpFmeaReport report = analyzer.analyzeServiceHealth(
                "srv", false, "OPEN", null, 0);
            int expected = McpFailureModeType.FM_M3_ZAI_SERVICE_UNAVAILABLE.rpn()
                         + McpFailureModeType.FM_M5_CIRCUIT_BREAKER_OPEN.rpn();
            assertEquals(expected, report.totalRpn());
        }

        @Test
        @DisplayName("totalRpn = 0 when clean")
        void totalRpn_zeroWhenClean() {
            McpFmeaReport report = analyzer.analyzeServiceHealth(
                "srv", true, "CLOSED", null, 0);
            assertEquals(0, report.totalRpn());
        }
    }
}
