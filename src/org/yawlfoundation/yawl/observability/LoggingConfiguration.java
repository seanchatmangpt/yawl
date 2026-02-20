package org.yawlfoundation.yawl.observability;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerEvent;
import io.github.resilience4j.core.EventConsumer;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.event.RateLimiterEvent;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.event.RetryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;

/**
 * Spring Boot Configuration for structured JSON logging with Logstash integration.
 *
 * Provides:
 * - MDC enrichment with application context (workflow ID, task ID, client IP)
 * - Sleuth trace ID and span ID propagation
 * - Resilience4j event listener integration for circuit breaker, rate limiter, and retry events
 * - HTTP request tracing with correlation IDs
 * - Structured logging marker support
 *
 * Configuration properties (application.yml/properties):
 * ```
 * yawl:
 *   logging:
 *     mdc-enrichment:
 *       enabled: true
 *       include-user-agent: true
 *       include-request-headers: false
 * ```
 */
@Configuration
@EnableConfigurationProperties(LoggingConfiguration.LoggingProperties.class)
public class LoggingConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(LoggingConfiguration.class);

    /**
     * Configuration properties for structured logging.
     */
    @ConfigurationProperties(prefix = "yawl.logging")
    public static class LoggingProperties {
        private MDCEnrichmentProperties mdcEnrichment = new MDCEnrichmentProperties();

        public static class MDCEnrichmentProperties {
            private boolean enabled = true;
            private boolean includeUserAgent = true;
            private boolean includeRequestHeaders = false;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public boolean isIncludeUserAgent() {
                return includeUserAgent;
            }

            public void setIncludeUserAgent(boolean includeUserAgent) {
                this.includeUserAgent = includeUserAgent;
            }

            public boolean isIncludeRequestHeaders() {
                return includeRequestHeaders;
            }

            public void setIncludeRequestHeaders(boolean includeRequestHeaders) {
                this.includeRequestHeaders = includeRequestHeaders;
            }
        }

        public MDCEnrichmentProperties getMdcEnrichment() {
            return mdcEnrichment;
        }

        public void setMdcEnrichment(MDCEnrichmentProperties mdcEnrichment) {
            this.mdcEnrichment = mdcEnrichment;
        }
    }

    /**
     * HTTP request interceptor to enrich MDC with request context.
     * Adds correlation ID, trace ID, and client information to all HTTP requests.
     */
    @Bean
    public WebMvcConfigurer webMvcConfigurer(LoggingProperties props) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                if (props.getMdcEnrichment().isEnabled()) {
                    registry.addInterceptor(new HandlerInterceptor() {
                        @Override
                        public boolean preHandle(HttpServletRequest request,
                                                HttpServletResponse response,
                                                Object handler) {
                            // Generate or extract correlation ID
                            String correlationId = request.getHeader("X-Correlation-ID");
                            if (correlationId == null || correlationId.isEmpty()) {
                                correlationId = UUID.randomUUID().toString();
                            }
                            MDC.put("correlation_id", correlationId);
                            response.setHeader("X-Correlation-ID", correlationId);

                            // Extract Sleuth trace ID if present
                            String traceId = request.getHeader("X-Trace-ID");
                            if (traceId != null && !traceId.isEmpty()) {
                                MDC.put("trace_id", traceId);
                            }

                            String spanId = request.getHeader("X-Span-ID");
                            if (spanId != null && !spanId.isEmpty()) {
                                MDC.put("span_id", spanId);
                            }

                            // Enrich with client IP
                            String clientIp = extractClientIp(request);
                            if (clientIp != null) {
                                MDC.put("client_ip", clientIp);
                            }

                            // Enrich with user agent if enabled
                            if (props.getMdcEnrichment().isIncludeUserAgent()) {
                                String userAgent = request.getHeader("User-Agent");
                                if (userAgent != null) {
                                    MDC.put("user_agent", userAgent);
                                }
                            }

                            // Add request method and path
                            MDC.put("http_method", request.getMethod());
                            MDC.put("http_path", request.getRequestURI());

                            return true;
                        }

                        @Override
                        public void afterCompletion(HttpServletRequest request,
                                                   HttpServletResponse response,
                                                   Object handler,
                                                   Exception ex) {
                            // Clear MDC after request completion
                            StructuredLogger.clearContext();
                        }

                        /**
                         * Extract client IP from request, considering X-Forwarded-For header.
                         */
                        private String extractClientIp(HttpServletRequest request) {
                            String forwardedFor = request.getHeader("X-Forwarded-For");
                            if (forwardedFor != null && !forwardedFor.isEmpty()) {
                                return forwardedFor.split(",")[0].trim();
                            }
                            String xRealIp = request.getHeader("X-Real-IP");
                            if (xRealIp != null && !xRealIp.isEmpty()) {
                                return xRealIp;
                            }
                            return request.getRemoteAddr();
                        }
                    });
                }
            }
        };
    }

    /**
     * Creates an event consumer for resilience4j CircuitBreaker events.
     * Logs circuit breaker state changes as structured JSON entries.
     */
    @Bean
    @ConditionalOnClass(CircuitBreaker.class)
    public EventConsumer<CircuitBreakerEvent> circuitBreakerEventConsumer() {
        return event -> {
            MDC.put("resilience4j_pattern", "circuit_breaker");
            MDC.put("circuit_breaker_name", event.getCircuitBreakerName());

            switch (event.getEventType()) {
                case STATE_TRANSITION -> {
                    CircuitBreakerEvent.StateTransition stateEvent = (CircuitBreakerEvent.StateTransition) event;
                    MDC.put("circuit_breaker_state_from", stateEvent.getStateTransition().getFromState().toString());
                    MDC.put("circuit_breaker_state_to", stateEvent.getStateTransition().getToState().toString());
                    logger.info("CircuitBreaker state changed: {} -> {}",
                            stateEvent.getStateTransition().getFromState(),
                            stateEvent.getStateTransition().getToState(),
                            LogMarkers.circuitBreakerStateChange(event.getCircuitBreakerName()));
                }
                case ERROR -> {
                    CircuitBreakerEvent.Error errorEvent = (CircuitBreakerEvent.Error) event;
                    MDC.put("circuit_breaker_failure_rate", String.valueOf(errorEvent.getCircuitBreakerMetrics().getFailureRate()));
                    logger.warn("CircuitBreaker error occurred (failure_rate={}%)",
                            errorEvent.getCircuitBreakerMetrics().getFailureRate(),
                            LogMarkers.circuitBreakerError(event.getCircuitBreakerName()));
                }
                case SUCCESS -> {
                    CircuitBreakerEvent.Success successEvent = (CircuitBreakerEvent.Success) event;
                    MDC.put("circuit_breaker_success_rate", String.valueOf(successEvent.getCircuitBreakerMetrics().getSuccessRate()));
                    logger.debug("CircuitBreaker success recorded",
                            LogMarkers.circuitBreakerSuccess(event.getCircuitBreakerName()));
                }
                case SLOW_CALL -> {
                    logger.warn("CircuitBreaker slow call detected",
                            LogMarkers.circuitBreakerSlowCall(event.getCircuitBreakerName()));
                }
                case CALL_NOT_PERMITTED -> {
                    logger.warn("CircuitBreaker call not permitted (circuit is open)",
                            LogMarkers.circuitBreakerOpen(event.getCircuitBreakerName()));
                }
                case IGNORED_ERROR -> {
                    logger.debug("CircuitBreaker ignored error",
                            LogMarkers.circuitBreakerIgnoredError(event.getCircuitBreakerName()));
                }
            }

            MDC.remove("resilience4j_pattern");
            MDC.remove("circuit_breaker_name");
            MDC.remove("circuit_breaker_state_from");
            MDC.remove("circuit_breaker_state_to");
            MDC.remove("circuit_breaker_failure_rate");
            MDC.remove("circuit_breaker_success_rate");
        };
    }

    /**
     * Creates an event consumer for resilience4j RateLimiter events.
     * Logs rate limit state changes as structured JSON entries.
     */
    @Bean
    @ConditionalOnClass(RateLimiter.class)
    public EventConsumer<RateLimiterEvent> rateLimiterEventConsumer() {
        return event -> {
            MDC.put("resilience4j_pattern", "rate_limiter");
            MDC.put("rate_limiter_name", event.getRateLimiterName());

            switch (event.getEventType()) {
                case SUCCESSFUL -> {
                    logger.debug("RateLimiter allowed request", LogMarkers.rateLimiterAllowed(event.getRateLimiterName()));
                }
                case FAILED -> {
                    logger.warn("RateLimiter rejected request (rate limit exceeded)", LogMarkers.rateLimiterExceeded(event.getRateLimiterName()));
                }
            }

            MDC.remove("resilience4j_pattern");
            MDC.remove("rate_limiter_name");
        };
    }

    /**
     * Creates an event consumer for resilience4j Retry events.
     * Logs retry attempts and final outcomes as structured JSON entries.
     */
    @Bean
    @ConditionalOnClass(Retry.class)
    public EventConsumer<RetryEvent> retryEventConsumer() {
        return event -> {
            MDC.put("resilience4j_pattern", "retry");
            MDC.put("retry_name", event.getRetryName());

            switch (event.getEventType()) {
                case RETRY -> {
                    RetryEvent.Retry retryEvent = (RetryEvent.Retry) event;
                    MDC.put("retry_attempt", String.valueOf(retryEvent.getNumberOfRetryAttempts()));
                    logger.warn("Retry attempt #{}: {}",
                            retryEvent.getNumberOfRetryAttempts(),
                            retryEvent.getLastThrowable().getMessage(),
                            LogMarkers.retryAttempt(event.getRetryName()));
                }
                case SUCCESS -> {
                    RetryEvent.Success successEvent = (RetryEvent.Success) event;
                    MDC.put("retry_attempts", String.valueOf(successEvent.getNumberOfRetryAttempts()));
                    logger.info("Retry succeeded after {} attempts", successEvent.getNumberOfRetryAttempts(),
                            LogMarkers.retrySuccess(event.getRetryName()));
                }
                case IGNORED_ERROR -> {
                    logger.debug("Retry ignored error", LogMarkers.retryIgnoredError(event.getRetryName()));
                }
                case ERROR -> {
                    RetryEvent.Error errorEvent = (RetryEvent.Error) event;
                    MDC.put("retry_error_cause", errorEvent.getLastThrowable().getClass().getSimpleName());
                    logger.error("Retry exhausted: {}",
                            errorEvent.getLastThrowable().getMessage(),
                            LogMarkers.retryExhausted(event.getRetryName()));
                }
            }

            MDC.remove("resilience4j_pattern");
            MDC.remove("retry_name");
            MDC.remove("retry_attempt");
            MDC.remove("retry_attempts");
            MDC.remove("retry_error_cause");
        };
    }

}
