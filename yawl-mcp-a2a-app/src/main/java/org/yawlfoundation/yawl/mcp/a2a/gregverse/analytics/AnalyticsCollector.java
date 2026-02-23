package org.yawlfoundation.yawl.mcp.a2a.gregverse.analytics;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Event stream processor for real-time analytics collection
 */
public class AnalyticsCollector {

    private final MarketplaceMetrics metrics;
    private final ExecutorService executor;
    private final BlockingQueue<MarketplaceEvent> eventQueue;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicReference<Consumer<MarketplaceEvent>> eventProcessor;
    private final int batchSize;
    private final long processingIntervalMs;

    public AnalyticsCollector(int batchSize, long processingIntervalMs) {
        this.metrics = new MarketplaceMetrics();
        this.batchSize = batchSize;
        this.processingIntervalMs = processingIntervalMs;
        this.eventQueue = new LinkedBlockingQueue<>(1000);
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.eventProcessor = new AtomicReference<>(this::defaultEventProcessor);
    }

    public void start() {
        if (isRunning.compareAndSet(false, true)) {
            // Start the batch processing thread
            executor.submit(this::processBatchLoop);

            // Start the pruning scheduler
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            ScheduledFuture<?> pruningFuture = scheduler.scheduleAtFixedRate(
                this::pruneInactiveActivities,
                1, 1, TimeUnit.HOURS
            );

            // Register shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                stop();
                pruningFuture.cancel(true);
            }));
        }
    }

    public void stop() {
        if (isRunning.compareAndSet(true, false)) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    public void setEventProcessor(Consumer<MarketplaceEvent> processor) {
        eventProcessor.set(processor);
    }

    public CompletableFuture<Void> submitEvent(MarketplaceEvent event) {
        if (!isRunning.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Collector not running"));
        }

        return CompletableFuture.runAsync(() -> {
            try {
                if (!eventQueue.offer(event, 100, TimeUnit.MILLISECONDS)) {
                    // Queue full - log warning but don't block
                    System.err.println("Event queue full, dropping event: " + event);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Interrupted while submitting event: " + event);
            }
        }, executor);
    }

    private void processBatchLoop() {
        List<MarketplaceEvent> batch = new ArrayList<>(batchSize);

        while (isRunning.get()) {
            try {
                // Drain available events
                eventQueue.drainTo(batch, batchSize);

                if (!batch.isEmpty()) {
                    processBatch(batch);
                    batch.clear();
                }

                // Wait for next interval
                TimeUnit.MILLISECONDS.sleep(processingIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("Error in batch processing: " + e.getMessage());
            }
        }
    }

    private void processBatch(List<MarketplaceEvent> batch) {
        // Process events in parallel using virtual threads
        List<CompletableFuture<Void>> futures = batch.stream()
            .map(event -> CompletableFuture.runAsync(() -> {
                Consumer<MarketplaceEvent> processor = eventProcessor.get();
                if (processor != null) {
                    processor.accept(event);
                }
            }, executor))
            .toList();

        // Wait for all events to be processed
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .join();
    }

    private void defaultEventProcessor(MarketplaceEvent event) {
        switch (event.getType()) {
            case TRANSACTION:
                metrics.recordTransaction(
                    event.getProviderId(),
                    event.getConsumerId(),
                    event.getValue(),
                    event.getCategory(),
                    event.getResponseTimeMs(),
                    event.getRating(),
                    event.getLocation()
                );
                break;
            case PROVIDER_ACTIVITY:
                metrics.registerProviderActivity(event.getProviderId(), event.getTimestamp());
                break;
            case CONSUMER_ACTIVITY:
                metrics.registerConsumerActivity(event.getConsumerId(), event.getTimestamp());
                break;
        }
    }

    private void pruneInactiveActivities() {
        // Prune providers inactive for more than 24 hours
        metrics.pruneInactiveProviders(Instant.now().minus(24, ChronoUnit.HOURS));

        // Prune consumers inactive for more than 24 hours
        metrics.pruneInactiveConsumers(Instant.now().minus(24, ChronoUnit.HOURS));
    }

    public MarketplaceMetrics getMetrics() {
        return metrics;
    }

    public boolean isRunning() {
        return isRunning.get();
    }

    public int getQueueSize() {
        return eventQueue.size();
    }

    public enum EventType {
        TRANSACTION,
        PROVIDER_ACTIVITY,
        CONSUMER_ACTIVITY
    }

    public static class MarketplaceEvent {
        private final EventType type;
        private final String providerId;
        private final String consumerId;
        private final double value;
        private final String category;
        private final long responseTimeMs;
        private final int rating;
        private final String location;
        private final Instant timestamp;

        public MarketplaceEvent(EventType type, String providerId, String consumerId,
                               double value, String category, long responseTimeMs,
                               int rating, String location) {
            this.type = type;
            this.providerId = providerId;
            this.consumerId = consumerId;
            this.value = value;
            this.category = category;
            this.responseTimeMs = responseTimeMs;
            this.rating = rating;
            this.location = location;
            this.timestamp = Instant.now();
        }

        // Getters
        public EventType getType() { return type; }
        public String getProviderId() { return providerId; }
        public String getConsumerId() { return consumerId; }
        public double getValue() { return value; }
        public String getCategory() { return category; }
        public long getResponseTimeMs() { return responseTimeMs; }
        public int getRating() { return rating; }
        public String getLocation() { return location; }
        public Instant getTimestamp() { return timestamp; }
    }
}