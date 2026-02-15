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

package org.yawlfoundation.yawl.engine.actuator.metrics;

import io.micrometer.core.instrument.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Micrometer metrics for A2A and MCP agent performance.
 *
 * Provides metrics for:
 * - Agent invocation counts
 * - Agent response times
 * - Agent success/failure rates
 * - Agent availability
 *
 * @author YAWL Foundation
 * @version 5.2
 */
@Component
public class YAgentPerformanceMetrics {

    private static final Logger _logger = LogManager.getLogger(YAgentPerformanceMetrics.class);

    private final MeterRegistry registry;

    private final Map<String, AtomicInteger> agentInvocations;
    private final Map<String, AtomicInteger> agentSuccesses;
    private final Map<String, AtomicInteger> agentFailures;
    private final Map<String, AtomicLong> agentTotalResponseTime;

    private Counter totalAgentInvocationsCounter;
    private Counter totalAgentSuccessCounter;
    private Counter totalAgentFailureCounter;
    private Timer agentResponseTimer;

    public YAgentPerformanceMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.agentInvocations = new ConcurrentHashMap<>();
        this.agentSuccesses = new ConcurrentHashMap<>();
        this.agentFailures = new ConcurrentHashMap<>();
        this.agentTotalResponseTime = new ConcurrentHashMap<>();
    }

    @PostConstruct
    public void initializeMetrics() {
        _logger.info("Initializing YAWL agent performance metrics");

        totalAgentInvocationsCounter = Counter.builder("yawl.agent.invocations.total")
            .description("Total number of agent invocations")
            .tag("type", "all")
            .register(registry);

        totalAgentSuccessCounter = Counter.builder("yawl.agent.success.total")
            .description("Total number of successful agent invocations")
            .tag("type", "all")
            .register(registry);

        totalAgentFailureCounter = Counter.builder("yawl.agent.failure.total")
            .description("Total number of failed agent invocations")
            .tag("type", "all")
            .register(registry);

        agentResponseTimer = Timer.builder("yawl.agent.response.time")
            .description("Agent response time")
            .tag("type", "all")
            .register(registry);

        _logger.info("YAWL agent performance metrics initialized successfully");
    }

    public void recordAgentInvocation(String agentName, String agentType) {
        agentInvocations.computeIfAbsent(agentName, k -> new AtomicInteger(0)).incrementAndGet();
        totalAgentInvocationsCounter.increment();

        Counter.builder("yawl.agent.invocations")
            .description("Agent invocations by name")
            .tag("agent", agentName)
            .tag("type", agentType)
            .register(registry)
            .increment();
    }

    public void recordAgentSuccess(String agentName, String agentType, long responseTimeMs) {
        agentSuccesses.computeIfAbsent(agentName, k -> new AtomicInteger(0)).incrementAndGet();
        agentTotalResponseTime.computeIfAbsent(agentName, k -> new AtomicLong(0)).addAndGet(responseTimeMs);

        totalAgentSuccessCounter.increment();
        agentResponseTimer.record(responseTimeMs, java.util.concurrent.TimeUnit.MILLISECONDS);

        Counter.builder("yawl.agent.success")
            .description("Agent successful invocations")
            .tag("agent", agentName)
            .tag("type", agentType)
            .register(registry)
            .increment();

        Timer.builder("yawl.agent.response.time.by_agent")
            .description("Agent response time by agent name")
            .tag("agent", agentName)
            .tag("type", agentType)
            .register(registry)
            .record(responseTimeMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public void recordAgentFailure(String agentName, String agentType, String errorType) {
        agentFailures.computeIfAbsent(agentName, k -> new AtomicInteger(0)).incrementAndGet();
        totalAgentFailureCounter.increment();

        Counter.builder("yawl.agent.failure")
            .description("Agent failed invocations")
            .tag("agent", agentName)
            .tag("type", agentType)
            .tag("error", errorType)
            .register(registry)
            .increment();
    }

    public void recordA2AAgentInvocation(String agentName) {
        recordAgentInvocation(agentName, "a2a");
    }

    public void recordA2AAgentSuccess(String agentName, long responseTimeMs) {
        recordAgentSuccess(agentName, "a2a", responseTimeMs);
    }

    public void recordA2AAgentFailure(String agentName, String errorType) {
        recordAgentFailure(agentName, "a2a", errorType);
    }

    public void recordMCPAgentInvocation(String agentName) {
        recordAgentInvocation(agentName, "mcp");
    }

    public void recordMCPAgentSuccess(String agentName, long responseTimeMs) {
        recordAgentSuccess(agentName, "mcp", responseTimeMs);
    }

    public void recordMCPAgentFailure(String agentName, String errorType) {
        recordAgentFailure(agentName, "mcp", errorType);
    }

    public double getAgentSuccessRate(String agentName) {
        int invocations = agentInvocations.getOrDefault(agentName, new AtomicInteger(0)).get();
        if (invocations == 0) {
            return 0.0;
        }
        int successes = agentSuccesses.getOrDefault(agentName, new AtomicInteger(0)).get();
        return (double) successes / invocations;
    }

    public double getAgentAverageResponseTime(String agentName) {
        int successes = agentSuccesses.getOrDefault(agentName, new AtomicInteger(0)).get();
        if (successes == 0) {
            return 0.0;
        }
        long totalTime = agentTotalResponseTime.getOrDefault(agentName, new AtomicLong(0)).get();
        return (double) totalTime / successes;
    }

    public Map<String, Integer> getAgentInvocationCounts() {
        Map<String, Integer> result = new ConcurrentHashMap<>();
        agentInvocations.forEach((name, count) -> result.put(name, count.get()));
        return result;
    }

    public Map<String, Double> getAgentSuccessRates() {
        Map<String, Double> result = new ConcurrentHashMap<>();
        agentInvocations.keySet().forEach(name ->
            result.put(name, getAgentSuccessRate(name))
        );
        return result;
    }
}
