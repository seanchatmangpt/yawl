package org.yawlfoundation.yawl.mcp.a2a.config;

import java.util.logging.Logger;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;

import org.yawlfoundation.yawl.mcp.a2a.service.CircuitBreakerProperties;
import org.yawlfoundation.yawl.mcp.a2a.service.McpCircuitBreakerRegistry;
import org.yawlfoundation.yawl.mcp.a2a.service.ResilientMcpClientWrapper;

/**
 * Spring configuration for MCP client resilience patterns.
 *
 * <p>Auto-configures circuit breaker, retry, and fallback beans when
 * {@code yawl.mcp.resilience.enabled=true} (default).</p>
 *
 * <h2>Configuration Properties</h2>
 * <ul>
 *   <li>{@code yawl.mcp.resilience.enabled} - Enable resilience patterns (default: true)</li>
 *   <li>{@code yawl.mcp.resilience.circuit-breaker.*} - Circuit breaker settings</li>
 *   <li>{@code yawl.mcp.resilience.retry.*} - Retry with jitter settings</li>
 *   <li>{@code yawl.mcp.resilience.fallback.*} - Fallback strategy settings</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
@Configuration
@EnableConfigurationProperties(CircuitBreakerProperties.class)
@ConditionalOnProperty(prefix = "yawl.mcp.resilience", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ResilienceConfiguration {

    private static final Logger LOGGER = Logger.getLogger(ResilienceConfiguration.class.getName());

    /**
     * Creates the MCP circuit breaker registry bean.
     *
     * @param properties the circuit breaker configuration properties
     * @return the circuit breaker registry
     */
    @Bean
    @ConditionalOnMissingBean
    public McpCircuitBreakerRegistry mcpCircuitBreakerRegistry(CircuitBreakerProperties properties) {
        LOGGER.info("Creating MCP circuit breaker registry");
        return new McpCircuitBreakerRegistry(properties);
    }

    /**
     * Creates the resilient MCP client wrapper bean.
     *
     * @param properties the circuit breaker configuration properties
     * @return the resilient MCP client wrapper
     */
    @Bean
    @ConditionalOnMissingBean
    public ResilientMcpClientWrapper resilientMcpClientWrapper(CircuitBreakerProperties properties) {
        LOGGER.info("Creating resilient MCP client wrapper");
        return new ResilientMcpClientWrapper(properties);
    }

    /**
     * Registers circuit breaker metrics with Micrometer.
     *
     * @param mcpCircuitBreakerRegistry the MCP circuit breaker registry
     * @param meterRegistry the Micrometer meter registry
     * @return the tagged circuit breaker metrics binder
     */
    @Bean
    @ConditionalOnMissingBean
    public TaggedCircuitBreakerMetrics taggedCircuitBreakerMetrics(
            McpCircuitBreakerRegistry mcpCircuitBreakerRegistry,
            MeterRegistry meterRegistry) {

        LOGGER.info("Registering circuit breaker metrics with Micrometer");

        CircuitBreakerRegistry resilience4jRegistry = mcpCircuitBreakerRegistry.getResilience4jRegistry();
        TaggedCircuitBreakerMetrics metrics = TaggedCircuitBreakerMetrics
            .ofCircuitBreakerRegistry(resilience4jRegistry);

        metrics.bindTo(meterRegistry);

        return metrics;
    }
}
