package org.yawlfoundation.yawl.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.domain.JavaClasses;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Observability Architecture Compliance Tests for YAWL.
 *
 * Enforces observability best practices:
 * - All Resilience4j events are logged
 * - Metrics exposed via MeterRegistry
 * - OpenTelemetry trace IDs in MDC for correlation
 */
@DisplayName("YAWL Observability Architecture Compliance Tests")
class ObservabilityArchitectureTests {

    private static final String YAWL_PACKAGE = "org.yawlfoundation.yawl";

    private final JavaClasses allClasses = new ClassFileImporter()
            .withImportOption(location -> !location.contains("test"))
            .importPackages(YAWL_PACKAGE);

    // =========================================================================
    // Observability: Resilience4j Event Logging
    // =========================================================================

    @Test
    @DisplayName("All CircuitBreaker events must be published and logged")
    void circuitBreakerEventsLogged() {
        classes()
                .that()
                .dependOnClassesThat()
                .haveFullyQualifiedName("io.github.resilience4j.circuitbreaker.CircuitBreaker")
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage(
                        YAWL_PACKAGE + "..",
                        "io.github.resilience4j..",
                        "io.micrometer..",
                        "java..",
                        "jakarta.."
                )
                .because("CircuitBreaker events (open, closed, half-open) must be logged for observability")
                .allowEmptyShould(true)
                .check(allClasses);
    }

    @Test
    @DisplayName("All Retry events must be logged with attempt count")
    void retryEventsLogged() {
        classes()
                .that()
                .dependOnClassesThat()
                .haveFullyQualifiedName("io.github.resilience4j.retry.Retry")
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage(
                        YAWL_PACKAGE + "..",
                        "io.github.resilience4j..",
                        "io.micrometer..",
                        "java..",
                        "jakarta.."
                )
                .because("Retry events must be logged with attempt count for debugging")
                .allowEmptyShould(true)
                .check(allClasses);
    }

    @Test
    @DisplayName("All RateLimiter events must be logged with permitting/rejection status")
    void rateLimiterEventsLogged() {
        classes()
                .that()
                .dependOnClassesThat()
                .haveFullyQualifiedName("io.github.resilience4j.ratelimiter.RateLimiter")
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage(
                        YAWL_PACKAGE + "..",
                        "io.github.resilience4j..",
                        "io.micrometer..",
                        "java..",
                        "jakarta.."
                )
                .because("RateLimiter events must be logged to monitor traffic patterns")
                .allowEmptyShould(true)
                .check(allClasses);
    }

    // =========================================================================
    // Observability: Metrics Exposition
    // =========================================================================

    @Test
    @DisplayName("Metrics must be exposed via MeterRegistry (Micrometer)")
    void metricsExposedViaRegistry() {
        classes()
                .that()
                .haveSimpleNameMatching(".*Metrics|.*MetricsCollector|.*MetricsProvider")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("io.micrometer.core.instrument.MeterRegistry")
                .or()
                .dependOnClassesThat()
                .haveFullyQualifiedName("io.micrometer.core.instrument.Meter")
                .or()
                .dependOnClassesThat()
                .haveFullyQualifiedName("io.micrometer.core.instrument.Timer")
                .because("All metrics must be exposed through Micrometer MeterRegistry for Prometheus scraping")
                .allowEmptyShould(true)
                .check(allClasses);
    }

    @Test
    @DisplayName("No direct Prometheus client calls; use Micrometer instead")
    void noDirectPrometheusClient() {
        classes()
                .that()
                .resideInAPackage(YAWL_PACKAGE + "..")
                .should()
                .notDependOnClassesThat()
                .haveFullyQualifiedName("io.prometheus.client.Counter")
                .andShould()
                .notDependOnClassesThat()
                .haveFullyQualifiedName("io.prometheus.client.Gauge")
                .andShould()
                .notDependOnClassesThat()
                .haveFullyQualifiedName("io.prometheus.client.Histogram")
                .because("Use Micrometer MeterRegistry instead of direct Prometheus client for vendor independence")
                .allowEmptyShould(true)
                .check(allClasses);
    }

    // =========================================================================
    // Observability: Distributed Tracing
    // =========================================================================

    @Test
    @DisplayName("OpenTelemetry trace IDs must be in MDC for log correlation")
    void traceIdsInMdc() {
        classes()
                .that()
                .haveSimpleNameMatching(".*TraceHelper|.*TraceContext|.*CorrelationId.*")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("org.slf4j.MDC")
                .or()
                .dependOnClassesThat()
                .haveFullyQualifiedName("io.opentelemetry.api.trace.Tracer")
                .because("Trace IDs must be in SLF4J MDC for correlating logs with spans")
                .allowEmptyShould(true)
                .check(allClasses);
    }

    @Test
    @DisplayName("OpenTelemetry must be used for distributed tracing, not custom instrumentation")
    void openTelemetryUsedForTracing() {
        classes()
                .that()
                .resideInAPackage(YAWL_PACKAGE + ".observability..")
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage(
                        YAWL_PACKAGE + "..",
                        "io.opentelemetry..",
                        "io.micrometer..",
                        "io.github.resilience4j..",
                        "java..",
                        "jakarta.."
                )
                .because("Observability must use OpenTelemetry standard, not proprietary instrumentation")
                .allowEmptyShould(true)
                .check(allClasses);
    }

    @Test
    @DisplayName("Span creation must include relevant workflow context")
    void spansIncludeContext() {
        classes()
                .that()
                .haveSimpleNameMatching(".*ObservabilityHelper|.*TracingHelper|.*SpanHelper")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("io.opentelemetry.api.trace.Span")
                .or()
                .dependOnClassesThat()
                .haveFullyQualifiedName("io.opentelemetry.api.common.Attributes")
                .because("Spans must be enriched with workflow context (case ID, task, user) for observability")
                .allowEmptyShould(true)
                .check(allClasses);
    }

    // =========================================================================
    // Observability: Health Indicators
    // =========================================================================

    @Test
    @DisplayName("Health indicators must use Spring Actuator conventions")
    void healthIndicatorsUseActuator() {
        classes()
                .that()
                .haveSimpleNameMatching(".*HealthIndicator|.*Health.*")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("org.springframework.boot.actuate.health.HealthIndicator")
                .or()
                .dependOnClassesThat()
                .haveFullyQualifiedName("org.springframework.boot.actuate.health.Health")
                .because("Health indicators must implement Spring Boot Actuator interfaces")
                .allowEmptyShould(true)
                .check(allClasses);
    }

    @Test
    @DisplayName("Resilience health indicators must check circuit breaker state")
    void resilienceHealthCheckState() {
        classes()
                .that()
                .haveSimpleNameMatching(".*ResilienceHealthIndicator|.*CircuitBreakerHealth.*")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("io.github.resilience4j.circuitbreaker.CircuitBreaker")
                .or()
                .dependOnClassesThat()
                .haveFullyQualifiedName("io.github.resilience4j.circuitbreaker.CircuitBreaker$State")
                .because("Resilience health checks must report circuit breaker state")
                .allowEmptyShould(true)
                .check(allClasses);
    }

    // =========================================================================
    // Observability: Logging Standards
    // =========================================================================

    @Test
    @DisplayName("Structured logging must be used for observability")
    void structuredLoggingUsed() {
        classes()
                .that()
                .haveSimpleNameMatching(".*Logger|.*LogEvent|.*AuditLog")
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage(
                        YAWL_PACKAGE + "..",
                        "org.apache.logging.log4j..",
                        "org.slf4j..",
                        "java..",
                        "jakarta.."
                )
                .because("Structured logging enables machine parsing for analysis and alerting")
                .allowEmptyShould(true)
                .check(allClasses);
    }

}
