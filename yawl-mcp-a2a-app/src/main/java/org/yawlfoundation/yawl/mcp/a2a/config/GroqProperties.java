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

package org.yawlfoundation.yawl.mcp.a2a.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Groq LLM configuration properties for A2A integration.
 *
 * <p>This component binds configuration from {@code application.yml} under
 * the {@code yawl.groq} prefix to provide Groq API settings for the
 * A2A agent executor.</p>
 *
 * <h2>Configuration Example</h2>
 * <pre>{@code
 * yawl:
 *   groq:
 *     enabled: true
 *     api-key: ${GROQ_API_KEY:}
 *     model: openai/gpt-oss-20b
 *     max-concurrency: 30
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
@Component
@ConfigurationProperties(prefix = "yawl.groq")
public final class GroqProperties {

    /**
     * Default Groq model for A2A intent detection.
     */
    public static final String DEFAULT_MODEL = "openai/gpt-oss-20b";

    /**
     * Default maximum concurrent requests (free tier: 30 RPM).
     */
    public static final int DEFAULT_MAX_CONCURRENCY = 30;

    /**
     * Whether Groq integration is enabled.
     */
    private boolean enabled = true;

    /**
     * Groq API key (from environment variable GROQ_API_KEY).
     */
    private String apiKey;

    /**
     * Groq model to use for chat completions.
     */
    private String model = DEFAULT_MODEL;

    /**
     * Maximum concurrent requests to Groq API.
     */
    private int maxConcurrency = DEFAULT_MAX_CONCURRENCY;

    /**
     * System prompt for A2A intent detection.
     */
    private String systemPrompt = """
        You are a YAWL workflow assistant. Analyze user requests and extract the intended action.
        Respond in JSON format with:
        - "intent": one of "list_specifications", "launch_case", "cancel_case", "case_status", "workitem_query", "unknown"
        - "specId": specification ID (if applicable)
        - "caseId": case ID (if applicable)
        - "parameters": any additional parameters extracted
        - "response": a natural language response to the user

        Available actions:
        - list_specifications: List available workflow specifications
        - launch_case: Launch a new workflow case (requires specId)
        - cancel_case: Cancel a running case (requires caseId)
        - case_status: Get status of a case (requires caseId)
        - workitem_query: Query work items (optional caseId)
        - unknown: Request not understood
        """;

    /**
     * Check if Groq is properly configured with an API key.
     *
     * @return true if enabled and API key is set
     */
    public boolean isConfigured() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }

    // Getters and Setters

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getMaxConcurrency() {
        return maxConcurrency;
    }

    public void setMaxConcurrency(int maxConcurrency) {
        this.maxConcurrency = maxConcurrency;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }
}
