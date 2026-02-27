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

/**
 * Enumeration of MCP (Model Context Protocol) server FMEA failure modes for YAWL v6.
 *
 * <p>Each constant encodes Severity (S), Occurrence (O), and Detection (D) scores
 * on a 1–10 scale (10 = worst), following the same RPN formula used by the
 * Observatory build-risk FMEA (FM1–FM7).
 *
 * <pre>
 *   RPN = Severity × Occurrence × Detection
 * </pre>
 *
 * <table>
 *   <tr><th>ID</th><th>Name</th><th>S</th><th>O</th><th>D</th><th>RPN</th></tr>
 *   <tr><td>FM_M1</td><td>Tool Not Found</td>
 *       <td>7</td><td>4</td><td>3</td><td>84</td></tr>
 *   <tr><td>FM_M2</td><td>Engine Auth Failure</td>
 *       <td>9</td><td>4</td><td>2</td><td>72</td></tr>
 *   <tr><td>FM_M3</td><td>Z.AI Service Unavailable</td>
 *       <td>5</td><td>5</td><td>3</td><td>75</td></tr>
 *   <tr><td>FM_M4</td><td>Missing Environment Variable</td>
 *       <td>10</td><td>2</td><td>2</td><td>40</td></tr>
 *   <tr><td>FM_M5</td><td>Circuit Breaker Open</td>
 *       <td>8</td><td>3</td><td>5</td><td>120</td></tr>
 *   <tr><td>FM_M6</td><td>Tool Execution Failure</td>
 *       <td>7</td><td>5</td><td>4</td><td>140</td></tr>
 *   <tr><td>FM_M7</td><td>No Providers Registered</td>
 *       <td>10</td><td>2</td><td>3</td><td>60</td></tr>
 * </table>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public enum McpFailureModeType {

    /**
     * FM_M1 — Requested tool name is absent from the MCP tool registry.
     * The tool was not registered via {@code McpToolRegistry} before the call;
     * the request will fail with an unknown-tool error.
     * S=7 (request fails hard), O=4 (monthly: API version mismatch or typo),
     * D=3 (registry lookup is straightforward).
     */
    FM_M1_TOOL_NOT_FOUND(7, 4, 3,
        "Requested MCP tool name absent from the tool registry",
        "Discover available tools via McpToolRegistry.createAll() before invoking; "
            + "validate tool names against the registry at agent startup"),

    /**
     * FM_M2 — YAWL engine returned a {@code <failure>} session handle.
     * The session handle obtained during {@code YawlMcpServer.connectToEngine()}
     * contains the literal string {@code <failure>}, meaning authentication failed.
     * S=9 (all engine operations blocked), O=4 (monthly: credential rotation or expiry),
     * D=2 (detectable by checking sessionHandle for &lt;failure&gt;).
     */
    FM_M2_ENGINE_AUTH_FAILURE(9, 4, 2,
        "YAWL engine returned a <failure> session handle — authentication failed",
        "Verify YAWL_ENGINE_USERNAME and YAWL_ENGINE_PASSWORD env vars match the engine's "
            + "credential store; rotate credentials and restart the MCP server"),

    /**
     * FM_M3 — Z.AI function service is unavailable or uninitialised.
     * {@code YawlMcpContext.isZaiAvailable()} returns {@code false}; any tool that
     * delegates to the Z.AI API will receive a degraded or empty response.
     * S=5 (synthesis features unavailable; core YAWL continues), O=5 (weekly: external service),
     * D=3 (isZaiAvailable() check detects it).
     */
    FM_M3_ZAI_SERVICE_UNAVAILABLE(5, 5, 3,
        "Z.AI function service unavailable — synthesis tools will degrade",
        "Set ZAI_API_KEY env var and ensure network reachability to the Z.AI endpoint; "
            + "check ZaiFunctionService.isInitialized() at startup"),

    /**
     * FM_M4 — A required environment variable (engine URL, username, or password) is missing.
     * The MCP server cannot build a valid connection to the YAWL engine without all three
     * variables populated. Server startup will fail or produce a null client.
     * S=10 (server fails to start), O=2 (rare: deployment misconfiguration),
     * D=2 (immediately detectable at startup by checking System.getenv).
     */
    FM_M4_MISSING_ENV_VAR(10, 2, 2,
        "Required environment variable (YAWL_ENGINE_URL / USERNAME / PASSWORD) is missing or blank",
        "Ensure YAWL_ENGINE_URL, YAWL_ENGINE_USERNAME, YAWL_ENGINE_PASSWORD are set "
            + "before starting the MCP server; use a .env file or container secrets"),

    /**
     * FM_M5 — The MCP server's circuit breaker is in the OPEN state.
     * All tool invocations routed through the circuit breaker will be rejected
     * immediately without reaching the YAWL engine.
     * S=8 (all tool calls rejected), O=3 (monthly: engine spike),
     * D=5 (requires active monitoring of circuit state).
     */
    FM_M5_CIRCUIT_BREAKER_OPEN(8, 3, 5,
        "MCP server circuit breaker is OPEN — all tool calls are being rejected",
        "Wait for the wait-duration to elapse (circuit transitions to HALF_OPEN), "
            + "then allow test calls to recover; expose circuit state via /actuator/health"),

    /**
     * FM_M6 — A tool invocation failed and retry attempts have been exhausted.
     * The tool handler threw an exception on the last attempt; the workflow that
     * triggered the tool call cannot proceed until the failure is resolved.
     * S=7 (workflow stalls on tool boundary), O=5 (weekly: transient engine errors),
     * D=4 (retry logic masks the root failure until attemptNumber is inspected).
     */
    FM_M6_TOOL_EXECUTION_FAILURE(7, 5, 4,
        "Tool execution failed after exhausting all retry attempts",
        "Inspect lastFailureMessage for root cause; increase retry budget or "
            + "reduce failure threshold; add fallback tool handler for critical tools"),

    /**
     * FM_M7 — The MCP tool registry has no providers registered.
     * {@code McpToolRegistry.providerCount()} returns 0 or the server was started
     * after an accidental {@code McpToolRegistry.reset()} call in production.
     * S=10 (zero tools available — server is useless), O=2 (coding error, rare),
     * D=3 (tool list is observably empty).
     */
    FM_M7_NO_PROVIDERS_REGISTERED(10, 2, 3,
        "MCP tool registry has no providers — server exposes zero tools",
        "Ensure McpToolRegistry.reset() is never called outside tests; "
            + "verify at least one McpToolProvider is registered before server.start()");

    // -----------------------------------------------------------------------

    private final int severity;
    private final int occurrence;
    private final int detection;
    private final String description;
    private final String mitigation;

    McpFailureModeType(int severity, int occurrence, int detection,
                       String description, String mitigation) {
        this.severity    = severity;
        this.occurrence  = occurrence;
        this.detection   = detection;
        this.description = description;
        this.mitigation  = mitigation;
    }

    /**
     * Risk Priority Number: {@code Severity × Occurrence × Detection}.
     * Higher RPN = higher risk. Maximum possible RPN = 1000 (10 × 10 × 10).
     *
     * @return computed RPN value
     */
    public int rpn() {
        return severity * occurrence * detection;
    }

    /** Severity score (1–10, 10 = catastrophic impact). */
    public int getSeverity()   { return severity; }

    /** Occurrence score (1–10, 10 = occurs on every change). */
    public int getOccurrence() { return occurrence; }

    /** Detection score (1–10, 10 = no detection possible). */
    public int getDetection()  { return detection; }

    /** Human-readable description of the failure mode. */
    public String getDescription() { return description; }

    /** Recommended mitigation action. */
    public String getMitigation()  { return mitigation; }
}
