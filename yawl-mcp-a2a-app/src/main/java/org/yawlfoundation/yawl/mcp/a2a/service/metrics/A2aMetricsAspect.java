package org.yawlfoundation.yawl.mcp.a2a.service.metrics;

import java.time.Duration;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Aspect for automatic A2A message metrics collection.
 *
 * <p>This aspect intercepts A2A message handling methods and automatically
 * records:</p>
 * <ul>
 *   <li>Message counts (received/sent)</li>
 *   <li>Processing duration</li>
 *   <li>Error counts by message type</li>
 *   <li>Active connections</li>
 * </ul>
 *
 * <h2>Pointcuts</h2>
 * <ul>
 *   <li>{@code handleMessage} - Incoming A2A messages</li>
 *   <li>{@code sendMessage} - Outgoing A2A messages</li>
 *   <li>{@code processTask} - A2A task processing</li>
 * </ul>
 *
 * <h2>Metrics Recorded</h2>
 * <ul>
 *   <li>{@code yawl_a2a_messages_received_total} - Counter with message_type, source_agent labels</li>
 *   <li>{@code yawl_a2a_messages_sent_total} - Counter with message_type, target_agent labels</li>
 *   <li>{@code yawl_a2a_message_duration_seconds} - Timer with message_type label</li>
 *   <li>{@code yawl_a2a_message_errors_total} - Counter with message_type, error_type labels</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 * @see MetricsService
 */
@Aspect
@Component
public class A2aMetricsAspect {

    private static final Logger logger = LoggerFactory.getLogger(A2aMetricsAspect.class);

    @Nullable
    @Autowired
    private MetricsService metricsService;

    /**
     * Intercept A2A message handling for incoming messages.
     *
     * <p>Records message received count and processing duration.</p>
     *
     * @param joinPoint the AOP join point
     * @return the message handling result
     * @throws Throwable if message handling fails
     */
    @Around("execution(* io.anthropic.a2a..*.handle*(..)) || " +
            "execution(* org.yawlfoundation.yawl.integration.a2a..*.handle*(..))")
    public Object aroundMessageHandling(ProceedingJoinPoint joinPoint) throws Throwable {
        if (metricsService == null) {
            return joinPoint.proceed();
        }

        String messageType = extractMessageType(joinPoint);
        String sourceAgent = extractSourceAgent(joinPoint);

        // Record message received
        metricsService.recordA2aMessageReceived(messageType, sourceAgent);

        long startTime = System.nanoTime();
        try {
            Object result = joinPoint.proceed();

            // Record successful processing duration
            long elapsedNanos = System.nanoTime() - startTime;
            metricsService.recordA2aMessageDuration(messageType, Duration.ofNanos(elapsedNanos));

            return result;

        } catch (Exception e) {
            // Record error
            String errorType = e.getClass().getSimpleName();
            metricsService.recordA2aMessageError(messageType, errorType);
            throw e;
        }
    }

    /**
     * Intercept A2A message sending for outgoing messages.
     *
     * <p>Records message sent count.</p>
     *
     * @param joinPoint the AOP join point
     * @return the send result
     * @throws Throwable if sending fails
     */
    @Around("execution(* io.anthropic.a2a..*.send*(..)) || " +
            "execution(* org.yawlfoundation.yawl.integration.a2a..*.send*(..))")
    public Object aroundMessageSending(ProceedingJoinPoint joinPoint) throws Throwable {
        if (metricsService == null) {
            return joinPoint.proceed();
        }

        String messageType = extractMessageType(joinPoint);
        String targetAgent = extractTargetAgent(joinPoint);

        try {
            Object result = joinPoint.proceed();

            // Record message sent
            metricsService.recordA2aMessageSent(messageType, targetAgent);

            return result;

        } catch (Exception e) {
            // Record error
            String errorType = e.getClass().getSimpleName();
            metricsService.recordA2aMessageError(messageType, errorType);
            throw e;
        }
    }

    /**
     * Intercept A2A task processing.
     *
     * <p>Records task processing duration and errors.</p>
     *
     * @param joinPoint the AOP join point
     * @return the task result
     * @throws Throwable if task processing fails
     */
    @Around("execution(* io.anthropic.a2a..*.process*(..)) || " +
            "execution(* org.yawlfoundation.yawl.integration.a2a..*.process*(..))")
    public Object aroundTaskProcessing(ProceedingJoinPoint joinPoint) throws Throwable {
        if (metricsService == null) {
            return joinPoint.proceed();
        }

        String taskType = extractTaskType(joinPoint);

        long startTime = System.nanoTime();
        try {
            Object result = joinPoint.proceed();

            // Record processing duration
            long elapsedNanos = System.nanoTime() - startTime;
            metricsService.recordA2aMessageDuration(taskType, Duration.ofNanos(elapsedNanos));

            return result;

        } catch (Exception e) {
            // Record error
            String errorType = e.getClass().getSimpleName();
            metricsService.recordA2aMessageError(taskType, errorType);
            throw e;
        }
    }

    /**
     * Extract message type from method arguments or class name.
     */
    private String extractMessageType(ProceedingJoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().getName();
        // Extract type from method name (e.g., handleMessageTask -> task)
        if (methodName.startsWith("handle")) {
            return methodName.substring(6).toLowerCase();
        }
        return joinPoint.getTarget().getClass().getSimpleName();
    }

    /**
     * Extract source agent from method arguments.
     */
    private String extractSourceAgent(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args.length > 0) {
            // Try to extract agent name from first argument if it's a message object
            Object firstArg = args[0];
            if (firstArg != null) {
                return firstArg.getClass().getSimpleName();
            }
        }
        return "unknown";
    }

    /**
     * Extract target agent from method arguments.
     */
    private String extractTargetAgent(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args.length > 0 && args[0] != null) {
            return args[0].getClass().getSimpleName();
        }
        return "unknown";
    }

    /**
     * Extract task type from method signature.
     */
    private String extractTaskType(ProceedingJoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().getName();
        if (methodName.startsWith("process")) {
            return methodName.substring(7).toLowerCase();
        }
        return "unknown";
    }
}
