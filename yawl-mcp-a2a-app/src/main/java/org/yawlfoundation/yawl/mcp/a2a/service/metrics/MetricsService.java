package org.yawlfoundation.yawl.mcp.a2a.service.metrics;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;

/**
 * Central metrics collection service for YAWL MCP-A2A application.
 *
 * <p>Provides comprehensive application-level metrics complementary to Resilience4j's
 * native Micrometer integration. All metrics use Micrometer for unified export via
 * standard backends (Prometheus, etc.).</p>
 *
 * <p>Metric Categories:</p>
 * <ul>
 *   <li><strong>MCP Tool Metrics:</strong> Tool call counts, latencies, success/error rates</li>
 *   <li><strong>A2A Message Metrics:</strong> Message counts, processing times, agent communication</li>
 *   <li><strong>Connection Pool Metrics:</strong> Active connections, pool size, wait times</li>
 *   <li><strong>Error Metrics:</strong> Error rates by component, exception types</li>
 *   <li><strong>Workflow Metrics:</strong> Active cases, work items, throughput</li>
 *   <li><strong>Health Metrics:</strong> Component health status, readiness indicators</li>
 * </ul>
 *
 * <p><strong>Metric Types:</strong></p>
 * <ul>
 *   <li>Counter: Monotonically increasing values (requests, errors)</li>
 *   <li>Gauge: Point-in-time values (active connections, queue size)</li>
 *   <li>Timer: Duration measurements (latency, processing time)</li>
 * </ul>
 *
 * <p><strong>Note:</strong> Resilience4j metrics (circuit breaker, retry, rate limiter,
 * bulkhead) are exported automatically by resilience4j-micrometer. This service
 * provides application-specific metrics.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
@Service
public class MetricsService {

    private static final Logger logger = LoggerFactory.getLogger(MetricsService.class);

    private static final String PREFIX = "yawl_";

    // MCP Tools
    private static final String MCP_TOOL_CALLS = PREFIX + "mcp_tool_calls_total";
    private static final String MCP_TOOL_DURATION = PREFIX + "mcp_tool_duration_seconds";
    private static final String MCP_TOOL_ERRORS = PREFIX + "mcp_tool_errors_total";
    private static final String MCP_TOOL_ACTIVE = PREFIX + "mcp_tool_active";

    // A2A Messages
    private static final String A2A_MESSAGES_RECEIVED = PREFIX + "a2a_messages_received_total";
    private static final String A2A_MESSAGES_SENT = PREFIX + "a2a_messages_sent_total";
    private static final String A2A_MESSAGE_DURATION = PREFIX + "a2a_message_duration_seconds";
    private static final String A2A_MESSAGE_ERRORS = PREFIX + "a2a_message_errors_total";
    private static final String A2A_CONNECTIONS_ACTIVE = PREFIX + "a2a_connections_active";

    // Connection Pool
    private static final String POOL_ACTIVE_CONNECTIONS = PREFIX + "pool_connections_active";
    private static final String POOL_IDLE_CONNECTIONS = PREFIX + "pool_connections_idle";
    private static final String POOL_TOTAL_CONNECTIONS = PREFIX + "pool_connections_total";
    private static final String POOL_WAIT_DURATION = PREFIX + "pool_wait_duration_seconds";
    private static final String POOL_CONNECTIONS_CREATED = PREFIX + "pool_connections_created_total";
    private static final String POOL_CONNECTIONS_EVICTED = PREFIX + "pool_connections_evicted_total";

    // Errors
    private static final String ERRORS_TOTAL = PREFIX + "errors_total";
    private static final String RETRY_ATTEMPTS = PREFIX + "retry_attempts_total";

    // Workflow
    private static final String WORKFLOW_CASES_ACTIVE = PREFIX + "workflow_cases_active";
    private static final String WORKFLOW_WORKITEMS_ACTIVE = PREFIX + "workflow_workitems_active";
    private static final String WORKFLOW_CASES_COMPLETED = PREFIX + "workflow_cases_completed_total";
    private static final String WORKFLOW_CASES_LAUNCHED = PREFIX + "workflow_cases_launched_total";
    private static final String WORKITEM_DURATION = PREFIX + "workitem_duration_seconds";

    // Health
    private static final String HEALTH_STATUS = PREFIX + "health_status";
    private static final String HEALTH_LAST_SUCCESS = PREFIX + "health_last_success_seconds";

    private final MeterRegistry registry;
    private final ConcurrentHashMap<String, AtomicLong> activeToolCounts = new ConcurrentHashMap<>();
    private final AtomicLong activeA2aConnections = new AtomicLong(0);
    private volatile Supplier<Number> activeCasesSupplier = () -> 0;
    private volatile Supplier<Number> activeWorkItemsSupplier = () -> 0;

    /**
     * Create metrics service with the given meter registry.
     *
     * @param registry Micrometer meter registry (e.g., PrometheusMeterRegistry)
     * @throws IllegalArgumentException if registry is null
     */
    public MetricsService(MeterRegistry registry) {
        if (registry == null) {
            throw new IllegalArgumentException("MeterRegistry is required");
        }
        this.registry = registry;
        initializeJvmMetrics();
        initializeBaseGauges();
        logger.info("MetricsService initialized with registry: {}", registry.getClass().getSimpleName());
    }

    // ========================= MCP Tool Metrics =========================

    public void recordMcpToolCall(String toolName) {
        validateToolName(toolName);
        Counter.builder(MCP_TOOL_CALLS)
            .tag("tool_name", toolName)
            .description("Total count of MCP tool invocations")
            .register(registry)
            .increment();
        activeToolCounts.computeIfAbsent(toolName, k -> {
            AtomicLong counter = new AtomicLong(0);
            Gauge.builder(MCP_TOOL_ACTIVE, counter, AtomicLong::get)
                .tag("tool_name", k)
                .description("Currently active MCP tool invocations")
                .register(registry);
            return counter;
        }).incrementAndGet();
        logger.debug("Recorded MCP tool call: {}", toolName);
    }

    public void recordMcpToolComplete(String toolName) {
        validateToolName(toolName);
        AtomicLong activeCount = activeToolCounts.get(toolName);
        if (activeCount != null) {
            activeCount.decrementAndGet();
        }
        logger.debug("Recorded MCP tool completion: {}", toolName);
    }

    public void recordMcpToolSuccess(String toolName) {
        recordMcpToolComplete(toolName);
    }

    public void recordMcpToolError(String toolName, String errorType) {
        validateToolName(toolName);
        if (errorType == null || errorType.isBlank()) {
            errorType = "Unknown";
        }
        Counter.builder(MCP_TOOL_ERRORS)
            .tag("tool_name", toolName)
            .tag("error_type", errorType)
            .description("Total count of MCP tool errors")
            .register(registry)
            .increment();
        recordMcpToolComplete(toolName);
        logger.debug("Recorded MCP tool error: {} - {}", toolName, errorType);
    }

    public void recordMcpToolDuration(String toolName, Duration duration) {
        validateToolName(toolName);
        if (duration == null) {
            duration = Duration.ZERO;
        }
        Timer.builder(MCP_TOOL_DURATION)
            .tag("tool_name", toolName)
            .description("Duration of MCP tool invocations")
            .register(registry)
            .record(duration);
        logger.debug("Recorded MCP tool duration: {} - {}ms", toolName, duration.toMillis());
    }

    public Timer.Sample startMcpToolTimer() {
        return Timer.start(registry);
    }

    public Timer getMcpToolTimer(String toolName) {
        return Timer.builder(MCP_TOOL_DURATION)
            .tag("tool_name", toolName)
            .description("Duration of MCP tool invocations")
            .register(registry);
    }

    // ========================= A2A Message Metrics =========================

    public void recordA2aMessageReceived(String messageType, @Nullable String sourceAgent) {
        validateMessageType(messageType);
        Counter.builder(A2A_MESSAGES_RECEIVED)
            .tag("message_type", messageType)
            .tag("source_agent", sourceAgent != null ? sourceAgent : "unknown")
            .description("Total count of A2A messages received")
            .register(registry)
            .increment();
        logger.debug("Recorded A2A message received: {} from {}", messageType, sourceAgent);
    }

    public void recordA2aMessageSent(String messageType, @Nullable String targetAgent) {
        validateMessageType(messageType);
        Counter.builder(A2A_MESSAGES_SENT)
            .tag("message_type", messageType)
            .tag("target_agent", targetAgent != null ? targetAgent : "unknown")
            .description("Total count of A2A messages sent")
            .register(registry)
            .increment();
        logger.debug("Recorded A2A message sent: {} to {}", messageType, targetAgent);
    }

    public void recordA2aMessageDuration(String messageType, Duration duration) {
        validateMessageType(messageType);
        if (duration == null) {
            duration = Duration.ZERO;
        }
        Timer.builder(A2A_MESSAGE_DURATION)
            .tag("message_type", messageType)
            .description("Duration of A2A message processing")
            .register(registry)
            .record(duration);
        logger.debug("Recorded A2A message duration: {} - {}ms", messageType, duration.toMillis());
    }

    public void recordA2aMessageError(String messageType, String errorType) {
        validateMessageType(messageType);
        if (errorType == null || errorType.isBlank()) {
            errorType = "Unknown";
        }
        Counter.builder(A2A_MESSAGE_ERRORS)
            .tag("message_type", messageType)
            .tag("error_type", errorType)
            .description("Total count of A2A message processing errors")
            .register(registry)
            .increment();
        logger.debug("Recorded A2A message error: {} - {}", messageType, errorType);
    }

    public void incrementA2aConnections() {
        activeA2aConnections.incrementAndGet();
        logger.debug("A2A connections incremented to: {}", activeA2aConnections.get());
    }

    public void decrementA2aConnections() {
        activeA2aConnections.decrementAndGet();
        logger.debug("A2A connections decremented to: {}", activeA2aConnections.get());
    }

    // ========================= Connection Pool Metrics =========================

    public void registerConnectionPoolMetrics(
            String poolName,
            Supplier<Number> activeSupplier,
            Supplier<Number> idleSupplier,
            Supplier<Number> totalSupplier) {
        if (poolName == null || poolName.isBlank()) {
            poolName = "default";
        }
        Gauge.builder(POOL_ACTIVE_CONNECTIONS, activeSupplier)
            .tag("pool", poolName)
            .description("Active connections in the pool")
            .register(registry);
        Gauge.builder(POOL_IDLE_CONNECTIONS, idleSupplier)
            .tag("pool", poolName)
            .description("Idle connections in the pool")
            .register(registry);
        Gauge.builder(POOL_TOTAL_CONNECTIONS, totalSupplier)
            .tag("pool", poolName)
            .description("Total connections in the pool")
            .register(registry);
        logger.info("Registered connection pool metrics for pool: {}", poolName);
    }

    public void recordPoolWaitDuration(String poolName, Duration duration) {
        if (poolName == null || poolName.isBlank()) {
            poolName = "default";
        }
        if (duration == null) {
            duration = Duration.ZERO;
        }
        Timer.builder(POOL_WAIT_DURATION)
            .tag("pool", poolName)
            .description("Time waiting for connection from pool")
            .register(registry)
            .record(duration);
    }

    public void recordPoolConnectionCreated(String poolName) {
        if (poolName == null || poolName.isBlank()) {
            poolName = "default";
        }
        Counter.builder(POOL_CONNECTIONS_CREATED)
            .tag("pool", poolName)
            .description("Total connections created")
            .register(registry)
            .increment();
    }

    public void recordPoolConnectionEvicted(String poolName, String reason) {
        if (poolName == null || poolName.isBlank()) {
            poolName = "default";
        }
        if (reason == null || reason.isBlank()) {
            reason = "unknown";
        }
        Counter.builder(POOL_CONNECTIONS_EVICTED)
            .tag("pool", poolName)
            .tag("reason", reason)
            .description("Total connections evicted")
            .register(registry)
            .increment();
    }

    // ========================= Error Metrics =========================

    public void recordError(String component, String errorType) {
        if (component == null || component.isBlank()) {
            component = "unknown";
        }
        if (errorType == null || errorType.isBlank()) {
            errorType = "Unknown";
        }
        Counter.builder(ERRORS_TOTAL)
            .tag("component", component)
            .tag("error_type", errorType)
            .description("Total errors by component")
            .register(registry)
            .increment();
        logger.debug("Recorded error: {} - {}", component, errorType);
    }

    public void recordRetryAttempt(String component, int attemptNumber) {
        if (component == null || component.isBlank()) {
            component = "unknown";
        }
        Counter.builder(RETRY_ATTEMPTS)
            .tag("component", component)
            .tag("attempt", String.valueOf(attemptNumber))
            .description("Total retry attempts")
            .register(registry)
            .increment();
    }

    // ========================= Workflow Metrics =========================

    public void setActiveCasesSupplier(Supplier<Number> supplier) {
        this.activeCasesSupplier = supplier;
        logger.info("Registered active cases supplier");
    }

    public void setActiveWorkItemsSupplier(Supplier<Number> supplier) {
        this.activeWorkItemsSupplier = supplier;
        logger.info("Registered active work items supplier");
    }

    public void recordCaseLaunched(String specId) {
        if (specId == null || specId.isBlank()) {
            specId = "unknown";
        }
        Counter.builder(WORKFLOW_CASES_LAUNCHED)
            .tag("spec_id", specId)
            .description("Total workflow cases launched")
            .register(registry)
            .increment();
        logger.debug("Recorded case launched: {}", specId);
    }

    public void recordCaseCompleted(String specId, String status) {
        if (specId == null || specId.isBlank()) {
            specId = "unknown";
        }
        if (status == null || status.isBlank()) {
            status = "completed";
        }
        Counter.builder(WORKFLOW_CASES_COMPLETED)
            .tag("spec_id", specId)
            .tag("status", status)
            .description("Total workflow cases completed")
            .register(registry)
            .increment();
        logger.debug("Recorded case completed: {} - {}", specId, status);
    }

    public void recordWorkItemDuration(String taskId, Duration duration) {
        if (taskId == null || taskId.isBlank()) {
            taskId = "unknown";
        }
        if (duration == null) {
            duration = Duration.ZERO;
        }
        Timer.builder(WORKITEM_DURATION)
            .tag("task_id", taskId)
            .description("Work item processing duration")
            .register(registry)
            .record(duration);
    }

    // ========================= Health Metrics =========================

    public void updateHealthStatus(String component, boolean healthy) {
        if (component == null || component.isBlank()) {
            component = "unknown";
        }
        int status = healthy ? 1 : 0;
        Gauge.builder(HEALTH_STATUS, () -> status)
            .tag("component", component)
            .description("Component health status (0=unhealthy, 1=healthy)")
            .register(registry);
        logger.debug("Updated health status: {} = {}", component, healthy ? "healthy" : "unhealthy");
    }

    public void recordHealthLastSuccess(String component, double seconds) {
        if (component == null || component.isBlank()) {
            component = "unknown";
        }
        Gauge.builder(HEALTH_LAST_SUCCESS, () -> seconds)
            .tag("component", component)
            .description("Seconds since last successful health check")
            .register(registry);
    }

    // ========================= Private Helpers =========================

    private void initializeJvmMetrics() {
        new JvmMemoryMetrics().bindTo(registry);
        new JvmGcMetrics().bindTo(registry);
        new JvmThreadMetrics().bindTo(registry);
        new ProcessorMetrics().bindTo(registry);
        logger.debug("JVM metrics initialized");
    }

    private void initializeBaseGauges() {
        Gauge.builder(A2A_CONNECTIONS_ACTIVE, activeA2aConnections, AtomicLong::get)
            .description("Currently active A2A connections")
            .register(registry);
        Gauge.builder(WORKFLOW_CASES_ACTIVE, activeCasesSupplier, s -> s.get().doubleValue())
            .description("Active workflow cases")
            .register(registry);
        Gauge.builder(WORKFLOW_WORKITEMS_ACTIVE, activeWorkItemsSupplier, s -> s.get().doubleValue())
            .description("Active work items")
            .register(registry);
        logger.debug("Base gauges initialized");
    }

    private void validateToolName(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("Tool name is required");
        }
    }

    private void validateMessageType(String messageType) {
        if (messageType == null || messageType.isBlank()) {
            throw new IllegalArgumentException("Message type is required");
        }
    }

    public MeterRegistry getRegistry() {
        return registry;
    }
}
