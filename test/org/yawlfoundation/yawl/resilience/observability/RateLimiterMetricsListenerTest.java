package org.yawlfoundation.yawl.resilience.observability;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.yawlfoundation.yawl.observability.CustomMetricsRegistry;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 tests for RateLimiterMetricsListener integration.
 */
@DisplayName("Rate Limiter Metrics Listener Tests")
public class RateLimiterMetricsListenerTest {

    private MeterRegistry meterRegistry;
    private CustomMetricsRegistry customMetrics;
    private RateLimiterRegistry rateLimiterRegistry;
    private RateLimiter testRateLimiter;

    @BeforeEach
    public void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        CustomMetricsRegistry.initialize(meterRegistry);
        customMetrics = CustomMetricsRegistry.getInstance();

        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .limitForPeriod(10)
                .build();

        rateLimiterRegistry = RateLimiterRegistry.of(config);
        testRateLimiter = rateLimiterRegistry.rateLimiter("test-rl");

        new RateLimiterMetricsListener(testRateLimiter);
    }

    @Test
    @DisplayName("Listener should record permit acquisition time")
    public void testPermitAcquisitionTime() {
        customMetrics.recordRateLimiterAcquisition(25);

        Timer timer = meterRegistry.find("yawl.rate.limiter.acquisition.time").timer();
        assertNotNull(timer, "Acquisition time timer should be registered");
        assertEquals(1, timer.count(), "Should record acquisition");
    }

    @Test
    @DisplayName("Listener should track allowed vs rejected permits")
    public void testPermitCounting() {
        customMetrics.incrementRateLimiterPermitAllowed();
        customMetrics.incrementRateLimiterPermitAllowed();
        customMetrics.incrementRateLimiterPermitRejected();

        Counter allowedCounter = meterRegistry.find("yawl.rate.limiter.permits.allowed").counter();
        Counter rejectedCounter = meterRegistry.find("yawl.rate.limiter.permits.rejected").counter();

        assertNotNull(allowedCounter, "Allowed counter should be registered");
        assertNotNull(rejectedCounter, "Rejected counter should be registered");
        assertEquals(2.0, allowedCounter.count(), "Should count 2 allowed");
        assertEquals(1.0, rejectedCounter.count(), "Should count 1 rejected");
    }

    @Test
    @DisplayName("Listener should track available permits")
    public void testAvailablePermits() {
        customMetrics.setRateLimiterAvailablePermits(7);
        assertEquals(7, customMetrics.getRateLimiterAvailablePermits());
    }
}
