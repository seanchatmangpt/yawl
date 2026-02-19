package org.yawlfoundation.yawl.mcp.a2a.service.metrics;

import java.time.Duration;
import java.util.Map;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * Aspect for automatic MCP tool metrics collection.
 *
 * <p>This aspect intercepts calls to {@link YawlMcpTool#execute(Map)} and
 * automatically records:</p>
 * <ul>
 *   <li>Tool call counts</li>
 *   <li>Execution duration (latency)</li>
 *   <li>Error counts by exception type</li>
 *   <li>Active invocations gauge</li>
 * </ul>
 *
 * <h2>Metrics Recorded</h2>
 * <ul>
 *   <li>{@code yawl_mcp_tool_calls_total} - Counter with tool_name label</li>
 *   <li>{@code yawl_mcp_tool_duration_seconds} - Timer with tool_name label</li>
 *   <li>{@code yawl_mcp_tool_errors_total} - Counter with tool_name, error_type labels</li>
 *   <li>{@code yawl_mcp_tool_active} - Gauge with tool_name label</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <p>Simply implement {@link YawlMcpTool} and the aspect will automatically
 * collect metrics. No manual instrumentation needed.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 * @see MetricsService
 */
@Aspect
@Component
public class McpMetricsAspect {

    private static final Logger logger = LoggerFactory.getLogger(McpMetricsAspect.class);

    @Nullable
    @Autowired
    private MetricsService metricsService;

    /**
     * Intercept YawlMcpTool.execute() calls to collect metrics.
     *
     * <p>Records:</p>
     * <ul>
     *   <li>Call count increment</li>
     *   <li>Active invocations gauge increment/decrement</li>
     *   <li>Duration timing</li>
     *   <li>Error count on exceptions</li>
     * </ul>
     *
     * @param joinPoint the AOP join point
     * @param params the tool parameters
     * @return the tool execution result
     * @throws Throwable if tool execution fails
     */
    @Around("execution(* org.yawlfoundation.yawl.integration.mcp.spring.YawlMcpTool.execute(..)) && args(params)")
    public Object aroundToolExecution(ProceedingJoinPoint joinPoint, Map<String, Object> params) throws Throwable {
        if (metricsService == null) {
            return joinPoint.proceed();
        }

        // Get tool name from the target object
        Object target = joinPoint.getTarget();
        String toolName = extractToolName(target);

        // Record call start
        metricsService.recordMcpToolCall(toolName);

        long startTime = System.nanoTime();
        try {
            // Execute the tool
            Object result = joinPoint.proceed();

            // Record success
            metricsService.recordMcpToolSuccess(toolName);

            // Check if result indicates an error
            if (result instanceof McpSchema.CallToolResult toolResult && toolResult.isError()) {
                metricsService.recordMcpToolError(toolName, "ToolError");
            }

            return result;

        } catch (Exception e) {
            // Record error
            String errorType = e.getClass().getSimpleName();
            metricsService.recordMcpToolError(toolName, errorType);
            throw e;

        } finally {
            // Record duration
            long elapsedNanos = System.nanoTime() - startTime;
            Duration duration = Duration.ofNanos(elapsedNanos);
            metricsService.recordMcpToolDuration(toolName, duration);
        }
    }

    /**
     * Extract the tool name from the target object.
     *
     * @param target the tool instance
     * @return the tool name
     */
    private String extractToolName(Object target) {
        if (target instanceof org.yawlfoundation.yawl.integration.mcp.spring.YawlMcpTool tool) {
            return tool.getName();
        }
        return target.getClass().getSimpleName();
    }
}
