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
 * You should have received a copy of the GNU Lesser General
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.mcp.a2a.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.yawlfoundation.yawl.integration.groq.GroqService;

/**
 * Spring configuration for Groq LLM integration in the A2A module.
 *
 * <p>This configuration creates and manages the {@link GroqService} bean
 * when Groq is enabled via configuration properties. The service is
 * conditionally created only when {@code yawl.groq.enabled=true} and
 * a valid API key is provided.</p>
 *
 * <h2>Configuration Properties</h2>
 * <pre>{@code
 * yawl:
 *   groq:
 *     enabled: true
 *     api-key: ${GROQ_API_KEY:}
 *     model: openai/gpt-oss-20b
 *     max-concurrency: 30
 * }</pre>
 *
 * <h2>Environment Variables</h2>
 * <ul>
 *   <li>{@code GROQ_API_KEY} - Required Groq API key</li>
 *   <li>{@code GROQ_MODEL} - Optional model override (default: openai/gpt-oss-20b)</li>
 *   <li>{@code GROQ_MAX_CONCURRENCY} - Optional concurrency limit (default: 30)</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 * @see GroqService
 * @see GroqProperties
 */
@Configuration
@EnableConfigurationProperties(GroqProperties.class)
@ConditionalOnProperty(prefix = "yawl.groq", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GroqConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(GroqConfig.class);

    /**
     * Creates the GroqService bean for A2A integration.
     *
     * <p>The service is created only when a valid API key is available.
     * If the API key is missing, a warning is logged and null is returned,
     * causing the A2A executor to fall back to deterministic routing.</p>
     *
     * @param properties the Groq configuration properties
     * @return the configured GroqService, or null if not properly configured
     */
    @Bean
    public GroqService groqService(GroqProperties properties) {
        LOGGER.info("Initializing Groq service for A2A integration");

        // Check if API key is available from properties or environment
        String apiKey = resolveApiKey(properties);

        if (apiKey == null || apiKey.isBlank()) {
            LOGGER.warn("Groq API key not configured. A2A will use deterministic routing. " +
                "Set GROQ_API_KEY environment variable or yawl.groq.api-key property.");
            return null;
        }

        GroqService service = new GroqService(apiKey);

        // Set system prompt if configured
        if (properties.getSystemPrompt() != null && !properties.getSystemPrompt().isBlank()) {
            service.setSystemPrompt(properties.getSystemPrompt());
        }

        LOGGER.info("Groq service initialized successfully with model: {}",
            properties.getModel());

        return service;
    }

    /**
     * Resolves the API key from properties or environment variable.
     *
     * <p>Priority: Environment variable > Properties file</p>
     *
     * @param properties the Groq configuration properties
     * @return the API key, or null if not found
     */
    private String resolveApiKey(GroqProperties properties) {
        // First check environment variable (highest priority)
        String envKey = System.getenv("GROQ_API_KEY");
        if (envKey != null && !envKey.isBlank()) {
            return envKey;
        }

        // Fall back to properties
        return properties.getApiKey();
    }
}
