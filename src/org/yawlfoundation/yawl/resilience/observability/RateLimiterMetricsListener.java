package org.yawlfoundation.yawl.resilience.observability;

import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.event.RateLimiterEvent;
import org.yawlfoundation.yawl.observability.CustomMetricsRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Metrics listener for Resilience4j RateLimiter operations.
 *
 * Records:
 * - Permit acquisition time (histogram with 1ms, 5ms, 10ms, 50ms, 100ms buckets)
 * - Available permits (gauge)
 * - Permitted vs rejected requests (counters)
 *
 * Integrates with CustomMetricsRegistry to populate advanced Prometheus metrics.
 */
public final class RateLimiterMetricsListener implements
        RegistryEventConsumer<RateLimiter>,
        RateLimiter.EventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(RateLimiterMetricsListener.class);

    private final RateLimiter rateLimiter;
    private final AtomicLong lastAcquisitionTime = new AtomicLong(System.currentTimeMillis());

    /**
     * Creates a new listener for the given RateLimiter instance.
     */
    public RateLimiterMetricsListener(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;

        // Register this listener with the rate limiter's event publisher
        rateLimiter.getEventPublisher()
                .onSuccess(this::onSuccess)
                .onFailure(this::onFailure);

        // Record initial available permits
        updateAvailablePermits();

        LOGGER.info("RateLimiterMetricsListener registered for: {}", rateLimiter.getName());
    }

    /**
     * Called when a RateLimiter is added to the registry.
     */
    @Override
    public void onEntryAdded(EntryAddedEvent<RateLimiter> event) {
        LOGGER.debug("RateLimiter added to registry: {}", event.getAddedEntry().getName());
    }

    /**
     * Called when a RateLimiter is removed from the registry.
     */
    @Override
    public void onEntryRemoved(EntryRemovedEvent<RateLimiter> event) {
        LOGGER.debug("RateLimiter removed from registry: {}", event.getRemovedEntry().getName());
    }

    /**
     * Called when a RateLimiter is replaced in the registry.
     */
    @Override
    public void onEntryReplaced(EntryReplacedEvent<RateLimiter> event) {
        LOGGER.debug("RateLimiter replaced in registry: {}", event.getNewEntry().getName());
    }

    /**
     * Records successful permit acquisition.
     */
    @Override
    public void onSuccess(RateLimiter.SuccessEvent event) {
        long currentTime = System.currentTimeMillis();
        long acquisitionTime = currentTime - lastAcquisitionTime.getAndSet(currentTime);

        LOGGER.debug("RateLimiter '{}' permit acquired (duration: {}ms)",
                rateLimiter.getName(), acquisitionTime);

        try {
            CustomMetricsRegistry metrics = CustomMetricsRegistry.getInstance();
            metrics.recordRateLimiterAcquisition(acquisitionTime);
            metrics.incrementRateLimiterPermitAllowed();
            updateAvailablePermits();
        } catch (IllegalStateException e) {
            LOGGER.warn("CustomMetricsRegistry not initialized, skipping metrics recording", e);
        }
    }

    /**
     * Records rate limiter rejection.
     */
    @Override
    public void onFailure(RateLimiter.FailureEvent event) {
        long currentTime = System.currentTimeMillis();
        long acquisitionTime = currentTime - lastAcquisitionTime.getAndSet(currentTime);

        LOGGER.warn("RateLimiter '{}' request rejected (duration: {}ms)",
                rateLimiter.getName(), acquisitionTime);

        try {
            CustomMetricsRegistry metrics = CustomMetricsRegistry.getInstance();
            metrics.recordRateLimiterAcquisition(acquisitionTime);
            metrics.incrementRateLimiterPermitRejected();
            updateAvailablePermits();
        } catch (IllegalStateException e) {
            LOGGER.warn("CustomMetricsRegistry not initialized, skipping metrics recording", e);
        }
    }

    /**
     * Updates the available permits gauge in metrics registry.
     */
    private void updateAvailablePermits() {
        try {
            CustomMetricsRegistry metrics = CustomMetricsRegistry.getInstance();
            long availablePermits = rateLimiter.getMetrics().getAvailablePermissions();
            metrics.setRateLimiterAvailablePermits(availablePermits);
        } catch (IllegalStateException e) {
            LOGGER.debug("CustomMetricsRegistry not initialized, skipping permit update", e);
        }
    }
}
