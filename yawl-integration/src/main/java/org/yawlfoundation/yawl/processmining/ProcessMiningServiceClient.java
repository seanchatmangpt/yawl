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

package org.yawlfoundation.yawl.integration.processmining;

import org.yawlfoundation.yawl.integration.autonomous.YawlNativeClient;
import org.yawlfoundation.yawl.integration.autonomous.YawlNativeException;

import java.io.IOException;
import java.time.Duration;

/**
 * HTTP client implementation of {@link ProcessMiningService} backed by the
 * {@code yawl-native} Rust service (default port 8083).
 *
 * <p>All HTTP transport is delegated to {@link YawlNativeClient}, which is the
 * single entry point for all Java-to-Rust calls. This class is responsible only
 * for serialising XML payloads into the JSON bodies expected by the Rust
 * handlers and surfacing {@link YawlNativeException} as {@link IOException} to
 * honour the {@link ProcessMiningService} contract.</p>
 *
 * <p>Request payloads are large (MB-scale XES/PNML files). JSON string escaping
 * is applied to avoid injection vulnerabilities when embedding XML in JSON.</p>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public final class ProcessMiningServiceClient implements ProcessMiningService {

    private final YawlNativeClient client;

    /**
     * Create a client targeting the default {@code yawl-native} URL
     * ({@value YawlNativeClient#DEFAULT_BASE_URL}).
     */
    public ProcessMiningServiceClient() {
        this(YawlNativeClient.DEFAULT_BASE_URL);
    }

    /**
     * Create a client targeting the given base URL.
     *
     * @param baseUrl base URL of the {@code yawl-native} service,
     *                e.g. {@code "http://localhost:8083"}
     */
    public ProcessMiningServiceClient(String baseUrl) {
        this.client = new YawlNativeClient(
                baseUrl != null ? baseUrl : YawlNativeClient.DEFAULT_BASE_URL);
    }

    /**
     * Create a client with a custom request timeout.
     *
     * @param baseUrl base URL of the {@code yawl-native} service
     * @param timeout per-request timeout
     */
    public ProcessMiningServiceClient(String baseUrl, Duration timeout) {
        this.client = new YawlNativeClient(
                baseUrl != null ? baseUrl : YawlNativeClient.DEFAULT_BASE_URL,
                timeout);
    }

    @Override
    public String tokenReplay(String pnmlXml, String xesXml) throws IOException {
        String json = buildTwoFieldJson("pnml", pnmlXml, "xes", xesXml);
        try {
            return client.tokenReplay(json);
        } catch (YawlNativeException e) {
            throw new IOException("Token replay failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String discoverDfg(String xesXml) throws IOException {
        String json = buildOneFieldJson("xes", xesXml);
        try {
            return client.discoverDfg(json);
        } catch (YawlNativeException e) {
            throw new IOException("DFG discovery failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String discoverAlphaPpp(String xesXml) throws IOException {
        String json = buildOneFieldJson("xes", xesXml);
        try {
            return client.discoverAlphaPpp(json);
        } catch (YawlNativeException e) {
            throw new IOException("Alpha+++ discovery failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String performanceAnalysis(String xesXml) throws IOException {
        String json = buildOneFieldJson("xes", xesXml);
        try {
            return client.performanceAnalysis(json);
        } catch (YawlNativeException e) {
            throw new IOException("Performance analysis failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String xesToOcel(String xesXml) throws IOException {
        String json = buildOneFieldJson("xes", xesXml);
        try {
            return client.xesToOcel(json);
        } catch (YawlNativeException e) {
            throw new IOException("XES to OCEL conversion failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isHealthy() {
        return client.isAvailable();
    }

    // -----------------------------------------------------------------------
    // JSON serialisation helpers
    // -----------------------------------------------------------------------

    /**
     * Build a one-field JSON object: {@code {"field": "value"}}.
     * The value is JSON-string-escaped to handle MB-scale XML payloads safely.
     *
     * @param field field name
     * @param value string value (must not be null)
     * @return compact JSON string
     */
    private static String buildOneFieldJson(String field, String value) {
        if (value == null) {
            throw new IllegalArgumentException("value for field '" + field + "' must not be null");
        }
        return "{\"" + field + "\":\"" + escapeJson(value) + "\"}";
    }

    /**
     * Build a two-field JSON object:
     * {@code {"field1": "value1", "field2": "value2"}}.
     * Both values are JSON-string-escaped.
     *
     * @param field1 first field name
     * @param value1 first value (must not be null)
     * @param field2 second field name
     * @param value2 second value (must not be null)
     * @return compact JSON string
     */
    private static String buildTwoFieldJson(String field1, String value1,
                                            String field2, String value2) {
        if (value1 == null) {
            throw new IllegalArgumentException("value for field '" + field1 + "' must not be null");
        }
        if (value2 == null) {
            throw new IllegalArgumentException("value for field '" + field2 + "' must not be null");
        }
        return "{\"" + field1 + "\":\"" + escapeJson(value1)
                + "\",\"" + field2 + "\":\"" + escapeJson(value2) + "\"}";
    }

    /**
     * Escape a string for safe inclusion in a JSON string literal.
     *
     * Handles backslashes, quotes, newlines, carriage returns, tabs, and all
     * other control characters. Prevents JSON injection when embedding large
     * XML payloads.
     *
     * @param s the string to escape (must not be null)
     * @return escaped string safe for use inside a JSON string literal
     */
    static String escapeJson(String s) {
        if (s == null) {
            throw new IllegalArgumentException("String to escape must not be null");
        }
        StringBuilder sb = new StringBuilder(s.length() + 64);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"':  sb.append("\\\""); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                case '\b': sb.append("\\b");  break;
                case '\f': sb.append("\\f");  break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
