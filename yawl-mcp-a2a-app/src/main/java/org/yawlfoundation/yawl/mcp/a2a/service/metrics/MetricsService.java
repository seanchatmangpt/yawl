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
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;

/**
 * Central metrics collection service for YAWL MCP-A2A application.
 *
 * <p>Provides comprehensive metrics for monitoring and observability:</p>
 * <ul>
 *   <li><strong>MCP Tool Metrics</strong>: Tool call counts, latencies, success/error rates</li>
 *   <li><strong>A2A Message Metrics</strong>: Message counts, processing times, agent communication</li>
 *   <li><strong>Connection Pool Metrics</strong>: Active connections, pool size, wait times</li>
 *   <li><strong>Error Metrics</strong>: Error rates by component, exception types</li>
 *   <li><strong>Workflow Metrics</strong>: Active cases, work items, throughput</li>
 *   <li><strong>Health Metrics</strong>: Component health status, readiness indicators</li>
 * </ul>
 *
 * <h2>Metric Types</h2>
 * <ul>
 *   <li><strong>Counter</strong>: Monotonically increasing values (requests, errors)</li>
 *   <li><strong>Gauge</strong>: Point-in-time values (active connections, queue size)</li>
 *   <li><strong>Timer</strong>: Duration measurements (latency, processing time)</li>
 *   <li><strong>DistributionSummary</strong>: Distribution of values (request sizes)</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * @Autowired
 * private MetricsService metricsService;
 *
 * public void handleMcpToolCall(String toolName, Map<String, Object> params) {
 *     long startTime = System.nanoTime();
 *     try {
 *         // Execute tool
 *         Object result = executeTool(toolName, params);
 *         metricsService.recordMcpToolSuccess(toolName);
 *         return result;
 *     } catch (Exception e) {
 *         metricsService.recordMcpToolError(toolName, e.getClass().getSimpleName());
 *         throw e;
 *     } finally {
 *         Duration duration = Duration.ofNanos(System.nanoTime() - startTime);
 *         metricsService.recordMcpToolDuration(toolName, duration);
 *     }
 * }
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>All metric recording methods are thread-safe. The underlying Micrometer
 * meters use atomic operations for counter increments and gauge updates.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
@Service
public class MetricsService {

    private static final Logger logger = LoggerFactory.getLogger(MetricsService.class);

    /** Metric name prefix for all YAWL metrics */
    private static final String PREFIX = "yawl_";

    // =========================================================================
    // Metric Name Constants - MCP Tools
    // =========================================================================

    /** Total count of MCP tool invocations */
    private static final String MCP_TOOL_CALLS = PREFIX + "mcp_tool_calls_total";

    /** Duration of MCP tool invocations */
    private static final String MCP_TOOL_DURATION = PREFIX + "mcp_tool_duration_seconds";

    /** Count of MCP tool errors */
    private static final String MCP_TOOL_ERRORS = PREFIX + "mcp_tool_errors_total";

    /** Currently active MCP tool invocations */
    private static final String MCP_TOOL_ACTIVE = PREFIX + "mcp_tool_active";

    // =========================================================================
    // Metric Name Constants - A2A Messages
    // =========================================================================

    /** Total count of A2A messages received */
    private static final String A2A_MESSAGES_RECEIVED = PREFIX + "a2a_messages_received_total";

    /** Total count of A2A messages sent */
    private static final String A2A_MESSAGES_SENT = PREFIX + "a2a_messages_sent_total";

    /** Duration of A2A message processing */
    private static final String A2A_MESSAGE_DURATION = PREFIX + "a2a_message_duration_seconds";

    /** Count of A2A message processing errors */
    private static final String A2A_MESSAGE_ERRORS = PREFIX + "a2a_message_errors_total";

    /** Currently active A2A connections */
    private static final String A2A_CONNECTIONS_ACTIVE = PREFIX + "a2a_connections_active";

    // =========================================================================
    // Metric Name Constants - Connection Pool
    // =========================================================================

    /** Active connections in the pool */
    private static final String POOL_ACTIVE_CONNECTIONS = PREFIX + "pool_connections_active";

    /** Idle connections in the pool */
    private static final String POOL_IDLE_CONNECTIONS = PREFIX + "pool_connections_idle";

    /** Total connections in the pool */
    private static final String POOL_TOTAL_CONNECTIONS = PREFIX + "pool_connections_total";

    /** Time waiting for connection from pool */
    private static final String POOL_WAIT_DURATION = PREFIX + "pool_wait_duration_seconds";

    /** Connection creation count */
    private static final String POOL_CONNECTIONS_CREATED = PREFIX + "pool_connections_created_total";

    /** Connection eviction count */
    private static final String POOL_CONNECTIONS_EVICTED = PREFIX + "pool_connections_evicted_total";

    // =========================================================================
    // Metric Name Constants - Errors
    // =========================================================================

    /** Error count by component */
    private static final String ERRORS_TOTAL = PREFIX + "errors_total";

    /** Current circuit breaker state (0=closed, 1=open, 2=half-open) */
    private static final String CIRCUIT_BREAKER_STATE = PREFIX + "circuit_breaker_state";

    /** Retry attempts count */
    private static final String RETRY_ATTEMPTS = PREFIX + "retry_attempts_total";

    // =========================================================================
    // Metric Name Constants - Workflow
    // =========================================================================

    /** Active workflow cases */
    private static final String WORKFLOW_CASES_ACTIVE = PREFIX + "workflow_cases_active";

    /** Active work items */
    private static final String WORKFLOW_WORKITEMS_ACTIVE = PREFIX + "workflow_workitems_active";

    /** Workflow case throughput */
    private static final String WORKFLOW_CASES_COMPLETED = PREFIX + "workflow_cases_completed_total";

    /** Workflow case launch count */
    private static final String WORKFLOW_CASES_LAUNCHED = PREFIX + "workflow_cases_launched_total";

    /** Work item processing duration */
    private static final String WORKITEM_DURATION = PREFIX + "workitem_duration_seconds";

    // =========================================================================
    // Metric Name Constants - Health
    // =========================================================================

    /** Component health status (0=unhealthy, 1=healthy) */
    private static final String HEALTH_STATUS = PREFIX + "health_status";

    /** Time since last successful health check */
    private static final String HEALTH_LAST_SUCCESS = PREFIX + "health_last_success_seconds";

    // =========================================================================
    // Instance Fields
    // =========================================================================

    private final MeterRegistry registry;

    /** Cache of active tool counters for real-time tracking */
    private final ConcurrentHashMap<String, AtomicLong> activeToolCounts = new ConcurrentHashMap<>();

    /** Cache of active A2A connection gauges */
    private final AtomicLong activeA2aConnections = new AtomicLong(0);

    /** Cache of active cases gauge supplier */
    private volatile Supplier<Number> activeCasesSupplier = () -> 0;

    /** Cache of active work items gauge supplier */
    private volatile Supplier<Number> activeWorkItemsSupplier = () -> 0;

    // =========================================================================
    // Constructor
    // =========================================================================

    /**
     * Create the metrics service with the given meter registry.
     *
     * <p>Initializes JVM metrics and system metrics for comprehensive monitoring.</p>
     *
     * @param registry the Micrometer meter registry (typically PrometheusMeterRegistry)
     */
    public MetricsService(MeterRegistry registry) {
        if (registry == null) {
            throw new IllegalArgumentException("MeterRegistry is required");
        }
        this.registry = registry;

        // Initialize JVM metrics
        initializeJvmMetrics();

        // Initialize base gauges
        initializeBaseGauges();

        logger.info("MetricsService initialized with registry type: {}",
            registry.getClass().getSimpleName());
    }

    // =========================================================================
    // MCP Tool Metrics
    // =========================================================================

    /**
     * Record an MCP tool invocation.
     *
     * <p>Increments the tool call counter and active gauge. Should be called
     * at the start of tool execution.</p>
     *
     * @param toolName the name of the MCP tool being invoked
     */
    public void recordMcpToolCall(String toolName) {
        validateToolName(toolName);

        Counter.builder(MCP_TOOL_CALLS)
            .tag("tool_name", toolName)
            .description("Total count of MCP tool invocations")
            .register(registry)
            .increment();

        // Increment active count
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

    /**
     * Record successful completion of an MCP tool.
     *
     * <p>Decrements the active gauge. Should be called after successful
     * tool execution.</p>
     *
     * @param toolName the name of the MCP tool that completed
     */
    public void recordMcpToolComplete(String toolName) {
        validateToolName(toolName);

        AtomicLong activeCount = activeToolCounts.get(toolName);
        if (activeCount != null) {
            activeCount.decrementAndGet();
        }

        logger.debug("Recorded MCP tool completion: {}", toolName);
    }

    /**
     * Record successful MCP tool execution.
     *
     * @param toolName the name of the MCP tool
     */
    public void recordMcpToolSuccess(String toolName) {
        recordMcpToolComplete(toolName);
    }

    /**
     * Record an MCP tool error.
     *
     * <p>Increments error counter and decrements active gauge. Should be called
     * when tool execution fails.</p>
     *
     * @param toolName the name of the MCP tool that failed
     * @param errorType the type/short name of the exception
     */
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

    /**
     * Record the duration of an MCP tool invocation.
     *
     * @param toolName the name of the MCP tool
     * @param duration the execution duration
     */
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

    /**
     * Create a timer sample for measuring MCP tool duration.
     *
     * <p>Usage:</p>
     * <pre>{@code
     * Timer.Sample sample = metricsService.startMcpToolTimer();
     * try {
     *     // execute tool
     * } finally {
     *     sample.stop(metricsService.getMcpToolTimer("my_tool"));
     * }
     * }</pre>
     *
     * @return a Timer.Sample for recording duration
     */
    public Timer.Sample startMcpToolTimer() {
        return Timer.start(registry);
    }

    /**
     * Get the timer for an MCP tool.
     *
     * @param toolName the name of the MCP tool
     * @return the Timer instance
     */
    public Timer getMcpToolTimer(String toolName) {
        return Timer.builder(MCP_TOOL_DURATION)
            .tag("tool_name", toolName)
            .description("Duration of MCP tool invocations")
            .register(registry);
    }

    // =========================================================================
    // A2A Message Metrics
    // =========================================================================

    /**
     * Record an A2A message received.
     *
     * @param messageType the type of A2A message
     * @param sourceAgent the agent that sent the message
     */
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

    /**
     * Record an A2A message sent.
     *
     * @param messageType the type of A2A message
     * @param targetAgent the agent receiving the message
     */
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

    /**
     * Record the duration of A2A message processing.
     *
     * @param messageType the type of A2A message
     * @param duration the processing duration
     */
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

    /**
     * Record an A2A message processing error.
     *
     * @param messageType the type of A2A message
     * @param errorType the type/short name of the exception
     */
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

    /**
     * Increment active A2A connections count.
     */
    public void incrementA2aConnections() {
        activeA2aConnections.incrementAndGet();
        logger.debug("A2A connections incremented to: {}", activeA2aConnections.get());
    }

    /**
     * Decrement active A2A connections count.
     */
    public void decrementA2aConnections() {
        activeA2aConnections.decrementAndGet();
        logger.debug("A2A connections decremented to: {}", activeA2aConnections.get());
    }

    // =========================================================================
    // Connection Pool Metrics
    // =========================================================================

    /**
     * Register connection pool gauges using supplier functions.
     *
     * <p>This method registers gauges that read values from supplier functions,
     * allowing the connection pool implementation to provide real-time values.</p>
     *
     * @param poolName the name of the connection pool
     * @param activeSupplier supplier for active connection count
     * @param idleSupplier supplier for idle connection count
     * @param totalSupplier supplier for total connection count
     */
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

    /**
     * Record time waiting for a connection from the pool.
     *
     * @param poolName the name of the connection pool
     * @param duration the wait duration
     */
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

    /**
     * Record a connection being created.
     *
     * @param poolName the name of the connection pool
     */
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

    /**
     * Record a connection being evicted.
     *
     * @param poolName the name of the connection pool
     * @param reason the reason for eviction
     */
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

    // =========================================================================
    // Error Metrics
    // =========================================================================

    /**
     * Record an error by component.
     *
     * @param component the component where the error occurred
     * @param errorType the type/short name of the exception
     */
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

    /**
     * Record a retry attempt.
     *
     * @param component the component performing the retry
     * @param attemptNumber the retry attempt number
     */
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

    /**
     * Update circuit breaker state.
     *
     * @param circuitBreakerName the name of the circuit breaker
     * @param state the state (0=closed, 1=open, 2=half-open)
     */
    public void updateCircuitBreakerState(String circuitBreakerName, int state) {
        if (circuitBreakerName == null || circuitBreakerName.isBlank()) {
            circuitBreakerName = "default";
        }

        // Use a gauge with a fixed value supplier for state
        String stateName = switch (state) {
            case 0 -> "closed";
            case 1 -> "open";
            case 2 -> "half_open";
            default -> "unknown";
        };

        Gauge.builder(CIRCUIT_BREAKER_STATE, () -> state)
            .tag("name", circuitBreakerName)
            .tag("state", stateName)
            .description("Circuit breaker state (0=closed, 1=open, 2=half-open)")
            .register(registry);

        logger.debug("Updated circuit breaker state: {} = {}", circuitBreakerName, stateName);
    }

    // =========================================================================
    // Workflow Metrics
    // =========================================================================

    /**
     * Set the supplier for active workflow cases count.
     *
     * <p>Registers a gauge that reads from the supplier function.</p>
     *
     * @param supplier supplier that returns the current active case count
     */
    public void setActiveCasesSupplier(Supplier<Number> supplier) {
        this.activeCasesSupplier = supplier;
        logger.info("Registered active cases supplier");
    }

    /**
     * Set the supplier for active work items count.
     *
     * <p>Registers a gauge that reads from the supplier function.</p>
     *
     * @param supplier supplier that returns the current active work item count
     */
    public void setActiveWorkItemsSupplier(Supplier<Number> supplier) {
        this.activeWorkItemsSupplier = supplier;
        logger.info("Registered active work items supplier");
    }

    /**
     * Record a workflow case launch.
     *
     * @param specId the specification identifier
     */
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

    /**
     * Record a workflow case completion.
     *
     * @param specId the specification identifier
     * @param status the completion status (completed, cancelled, failed)
     */
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

    /**
     * Record work item processing duration.
     *
     * @param taskId the task identifier
     * @param duration the processing duration
     */
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

    // =========================================================================
    // Health Metrics
    // =========================================================================

    /**
     * Update health status for a component.
     *
     * @param component the component name
     * @param healthy true if healthy, false otherwise
     */
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

    /**
     * Record time since last successful health check.
     *
     * @param component the component name
     * @param seconds seconds since last successful check
     */
    public void recordHealthLastSuccess(String component, double seconds) {
        if (component == null || component.isBlank()) {
            component = "unknown";
        }

        Gauge.builder(HEALTH_LAST_SUCCESS, () -> seconds)
            .tag("component", component)
            .description("Seconds since last successful health check")
            .register(registry);
    }

    // =========================================================================
    // Private Helper Methods
    // =========================================================================

    /**
     * Initialize JVM and system metrics.
     */
    private void initializeJvmMetrics() {
        // JVM memory metrics
        new JvmMemoryMetrics().bindTo(registry);

        // JVM GC metrics
        new JvmGcMetrics().bindTo(registry);

        // JVM thread metrics
        new JvmThreadMetrics().bindTo(registry);

        // CPU metrics
        new ProcessorMetrics().bindTo(registry);

        logger.debug("JVM metrics initialized");
    }

    /**
     * Initialize base gauges that are always registered.
     */
    private void initializeBaseGauges() {
        // Active A2A connections gauge
        Gauge.builder(A2A_CONNECTIONS_ACTIVE, activeA2aConnections, AtomicLong::get)
            .description("Currently active A2A connections")
            .register(registry);

        // Active workflow cases gauge
        Gauge.builder(WORKFLOW_CASES_ACTIVE, activeCasesSupplier, s -> s.get().doubleValue())
            .description("Active workflow cases")
            .register(registry);

        // Active work items gauge
        Gauge.builder(WORKFLOW_WORKITEMS_ACTIVE, activeWorkItemsSupplier, s -> s.get().doubleValue())
            .description("Active work items")
            .register(registry);

        logger.debug("Base gauges initialized");
    }

    /**
     * Validate tool name parameter.
     */
    private void validateToolName(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("Tool name is required");
        }
    }

    /**
     * Validate message type parameter.
     */
    private void validateMessageType(String messageType) {
        if (messageType == null || messageType.isBlank()) {
            throw new IllegalArgumentException("Message type is required");
        }
    }

    /**
     * Get the underlying meter registry.
     *
     * <p>Useful for custom metrics or advanced use cases.</p>
     *
     * @return the meter registry
     */
    public MeterRegistry getRegistry() {
        return registry;
    }
}
