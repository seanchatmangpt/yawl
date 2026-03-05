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

import org.yawlfoundation.yawl.integration.mcp.spec.YawlMcpContext;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Stateless analyser that checks YAWL v6 MCP server objects for FMEA failure conditions.
 *
 * <p>Each {@code analyze*} method evaluates one MCP subject against the relevant
 * subset of {@link McpFailureModeType} failure modes and returns an
 * {@link McpFmeaReport}. A report with {@link McpFmeaReport#isClean()} {@code true}
 * means no violations were detected; {@code false} means at least one failure mode
 * fired and the caller should reject or log the request.
 *
 * <p>This class is intentionally stateless — instantiate once and reuse across
 * requests, or create per-request; both patterns are safe for concurrent use.
 *
 * <h2>Failure modes by method</h2>
 * <table>
 *   <tr><th>Method</th><th>Failure Modes Checked</th></tr>
 *   <tr><td>{@link #analyzeContext}</td>
 *       <td>FM_M2 (engine auth failure), FM_M7 (no providers registered)</td></tr>
 *   <tr><td>{@link #analyzeToolLookup}</td>
 *       <td>FM_M1 (tool not found)</td></tr>
 *   <tr><td>{@link #analyzeEnvironment}</td>
 *       <td>FM_M4 (missing environment variable)</td></tr>
 *   <tr><td>{@link #analyzeServiceHealth}</td>
 *       <td>FM_M3 (Z.AI unavailable), FM_M5 (circuit breaker open),
 *           FM_M6 (tool execution failure)</td></tr>
 * </table>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * McpFmeaAnalyzer analyzer = new McpFmeaAnalyzer();
 *
 * McpFmeaReport report = analyzer.analyzeContext(context, McpToolRegistry.providerCount());
 * if (!report.isClean()) {
 *     throw new IllegalStateException("MCP FMEA " + report.status()
 *         + " RPN=" + report.totalRpn());
 * }
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class McpFmeaAnalyzer {

    /**
     * Sentinel string embedded in YAWL engine session handles when authentication fails.
     * {@code InterfaceB_EnvironmentBasedClient.connect()} returns a handle containing
     * this literal when credentials are rejected.
     */
    private static final String ENGINE_FAILURE_MARKER = "<failure>";

    /**
     * Analyse a {@link YawlMcpContext} for engine authentication and tool provider
     * registration failures.
     *
     * <p>Checks performed:
     * <ul>
     *   <li><b>FM_M2</b> — fires when {@code context.sessionHandle()} contains the
     *       literal string {@code "<failure>"}, meaning the YAWL engine rejected the
     *       provided credentials during connection</li>
     *   <li><b>FM_M7</b> — fires when {@code providerCount <= 0}, meaning the
     *       {@code McpToolRegistry} has no registered providers and the server
     *       exposes zero tools</li>
     * </ul>
     *
     * <p>Note: Z.AI availability (FM_M3) is checked separately via
     * {@link #analyzeServiceHealth} to avoid requiring a live
     * {@code ZaiFunctionService} instance at the call site; pass
     * {@code context.isZaiAvailable()} as the {@code zaiAvailable} argument.
     *
     * @param context       the live MCP context to evaluate; must not be {@code null}
     * @param providerCount the number of registered {@code McpToolProvider} instances
     *                      (obtain via {@code McpToolRegistry.providerCount()})
     * @return an {@link McpFmeaReport} with zero or more violations; never {@code null}
     */
    public McpFmeaReport analyzeContext(YawlMcpContext context, int providerCount) {
        Objects.requireNonNull(context, "context must not be null");

        List<McpFmeaViolation> violations = new ArrayList<>();

        // FM_M2 — engine authentication failure
        if (context.sessionHandle().contains(ENGINE_FAILURE_MARKER)) {
            violations.add(new McpFmeaViolation(
                McpFailureModeType.FM_M2_ENGINE_AUTH_FAILURE,
                "sessionHandle='" + context.sessionHandle() + "'",
                "sessionHandle contains '" + ENGINE_FAILURE_MARKER
                    + "' — YAWL engine rejected the provided credentials"
            ));
        }

        // FM_M7 — no tool providers registered
        if (providerCount <= 0) {
            violations.add(new McpFmeaViolation(
                McpFailureModeType.FM_M7_NO_PROVIDERS_REGISTERED,
                "providerCount=" + providerCount,
                "McpToolRegistry has no registered providers — server exposes zero tools"
            ));
        }

        return new McpFmeaReport(Instant.now(), violations);
    }

    /**
     * Analyse a tool lookup request for registry miss failures.
     *
     * <p>Checks performed:
     * <ul>
     *   <li><b>FM_M1</b> — fires when {@code toolName} is not present in
     *       {@code registeredToolNames}; the MCP server cannot route the call</li>
     * </ul>
     *
     * @param toolName           the tool name being requested; must not be {@code null}
     * @param registeredToolNames the set of tool names currently registered on this server;
     *                            must not be {@code null}
     * @return an {@link McpFmeaReport} with zero or one violation; never {@code null}
     */
    public McpFmeaReport analyzeToolLookup(String toolName, Set<String> registeredToolNames) {
        Objects.requireNonNull(toolName,           "toolName must not be null");
        Objects.requireNonNull(registeredToolNames, "registeredToolNames must not be null");

        List<McpFmeaViolation> violations = new ArrayList<>();

        // FM_M1 — tool not found in registry
        if (!registeredToolNames.contains(toolName)) {
            violations.add(new McpFmeaViolation(
                McpFailureModeType.FM_M1_TOOL_NOT_FOUND,
                "toolName='" + toolName + "'",
                "tool '" + toolName + "' is not in the registered tool set "
                    + registeredToolNames
            ));
        }

        return new McpFmeaReport(Instant.now(), violations);
    }

    /**
     * Analyse the MCP server environment configuration for missing required variables.
     *
     * <p>Checks performed:
     * <ul>
     *   <li><b>FM_M4</b> — fires when any of {@code engineUrl}, {@code username}, or
     *       {@code password} is {@code null} or blank; the server cannot connect to the
     *       YAWL engine without all three values populated</li>
     * </ul>
     *
     * @param engineUrl the YAWL engine URL (e.g. from {@code YAWL_ENGINE_URL} env var);
     *                  may be {@code null} or blank to trigger the violation
     * @param username  the YAWL engine username (e.g. from {@code YAWL_ENGINE_USERNAME});
     *                  may be {@code null} or blank
     * @param password  the YAWL engine password (e.g. from {@code YAWL_ENGINE_PASSWORD});
     *                  may be {@code null} or blank
     * @return an {@link McpFmeaReport} with zero or one violation; never {@code null}
     */
    public McpFmeaReport analyzeEnvironment(String engineUrl, String username, String password) {
        List<McpFmeaViolation> violations = new ArrayList<>();

        // FM_M4 — missing environment variable
        List<String> missing = new ArrayList<>();
        if (engineUrl == null || engineUrl.isBlank())  missing.add("YAWL_ENGINE_URL");
        if (username  == null || username.isBlank())   missing.add("YAWL_ENGINE_USERNAME");
        if (password  == null || password.isBlank())   missing.add("YAWL_ENGINE_PASSWORD");

        if (!missing.isEmpty()) {
            violations.add(new McpFmeaViolation(
                McpFailureModeType.FM_M4_MISSING_ENV_VAR,
                "engineUrl='" + engineUrl
                    + "', username='" + username + "'",
                "required environment variable(s) missing or blank: " + missing
            ));
        }

        return new McpFmeaReport(Instant.now(), violations);
    }

    /**
     * Analyse the MCP service health for Z.AI availability, circuit breaker state,
     * and tool execution failures.
     *
     * <p>Checks performed:
     * <ul>
     *   <li><b>FM_M3</b> — fires when {@code zaiAvailable} is {@code false}, meaning
     *       the Z.AI function service is not initialised; pass
     *       {@code context.isZaiAvailable()} or {@code false} to trigger</li>
     *   <li><b>FM_M5</b> — fires when {@code circuitBreakerState} equals {@code "OPEN"},
     *       meaning all tool invocations routed through this circuit breaker are currently
     *       being rejected without reaching the YAWL engine</li>
     *   <li><b>FM_M6</b> — fires when {@code lastFailureMessage} is non-null and
     *       {@code attemptNumber > 0}, meaning a tool execution failed and all configured
     *       retry attempts have been exhausted</li>
     * </ul>
     *
     * @param serverName           the MCP server identifier (for evidence context);
     *                             must not be {@code null}
     * @param zaiAvailable         {@code true} if the Z.AI service is available and initialised
     *                             (typically {@code context.isZaiAvailable()})
     * @param circuitBreakerState  the current circuit breaker state name
     *                             ({@code "CLOSED"}, {@code "OPEN"}, or {@code "HALF_OPEN"});
     *                             must not be {@code null}
     * @param lastFailureMessage   the message from the last tool execution failure,
     *                             or {@code null} if no failure has occurred
     * @param attemptNumber        the retry attempt number at the time of the last failure
     *                             (0 = no retries attempted)
     * @return an {@link McpFmeaReport} with zero or more violations; never {@code null}
     */
    public McpFmeaReport analyzeServiceHealth(String serverName,
                                              boolean zaiAvailable,
                                              String circuitBreakerState,
                                              String lastFailureMessage,
                                              int attemptNumber) {
        Objects.requireNonNull(serverName,          "serverName must not be null");
        Objects.requireNonNull(circuitBreakerState, "circuitBreakerState must not be null");

        List<McpFmeaViolation> violations = new ArrayList<>();

        // FM_M3 — Z.AI service unavailable
        if (!zaiAvailable) {
            violations.add(new McpFmeaViolation(
                McpFailureModeType.FM_M3_ZAI_SERVICE_UNAVAILABLE,
                "server='" + serverName + "'",
                "zaiAvailable=false — Z.AI synthesis tools will degrade; "
                    + "check ZAI_API_KEY env var and network reachability"
            ));
        }

        // FM_M5 — circuit breaker is OPEN
        if ("OPEN".equals(circuitBreakerState)) {
            violations.add(new McpFmeaViolation(
                McpFailureModeType.FM_M5_CIRCUIT_BREAKER_OPEN,
                "server='" + serverName + "', state=" + circuitBreakerState,
                "circuit breaker is OPEN — all tool calls are being rejected "
                    + "without reaching the YAWL engine"
            ));
        }

        // FM_M6 — tool execution failed after retries
        if (lastFailureMessage != null && attemptNumber > 0) {
            violations.add(new McpFmeaViolation(
                McpFailureModeType.FM_M6_TOOL_EXECUTION_FAILURE,
                "server='" + serverName + "', attemptNumber=" + attemptNumber,
                "tool execution failed after " + attemptNumber
                    + " attempt(s): " + lastFailureMessage
            ));
        }

        return new McpFmeaReport(Instant.now(), violations);
    }
}
