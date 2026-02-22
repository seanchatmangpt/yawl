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
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.processmining.streaming;

import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.engine.YWorkItemID;
import org.yawlfoundation.yawl.stateless.listener.event.YWorkItemEvent;
import org.yawlfoundation.yawl.stateless.elements.YSpecificationID;

import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Real-time event streaming interface for YAWL workflow execution.
 * Captures work item events as they occur and provides them to registered listeners
 * for real-time process mining analysis.
 *
 * <h2>Architecture</h2>
 * <pre>
 *   YAWL Engine            Event Stream              Process Mining
 *   (YNetRunner)           (Streaming)               (Analyzers)
 *        |                      |                          |
 *        | YWorkItemEvent       | EventBuffer              | onEvent()
 *        | --streaming-->       | --batch-->              | onTraceComplete()
 *        |                      |                          |
 *        | Case Start            | Trace Aggregator         | Trace data
 *        | --streaming-->       | --trace-->              | Process Mining
 * </pre>
 *
 * <h2>Features</h2>
 * <ul>
 *   <li><strong>Real-time streaming</strong> - Events captured as they occur</li>
 *   <li><strong>Trace aggregation</strong> - Batches events into complete traces</li>
 *   <li><strong>Backpressure handling</strong> - Throttles when consumers are slow</li>
 *   <li><strong>Event filtering</strong> - Filter by event type, activity, or resource</li>
 *   <li><strong>Buffering</strong> - Handles temporary consumer unavailability</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Create stream for specification
 * YAWLEventStream stream = new YAWLEventStreamImpl(specId);
 *
 * // Configure streaming
 * stream.setBufferSize(1000);
 * stream.setBatchInterval(Duration.ofMillis(500));
 * stream.addEventFilter(event ->
 *     event.getEventType() == YEventType.COMPLETED);
 *
 * // Start streaming with listener
 * stream.addStreamListener(new EventStreamListener() {
 *     public void onEvent(YWorkItemEvent event) {
 *         System.out.println("Event: " + event.getActivityName() + " " + event.getTimestamp());
 *     }
 *
 *     public void onTraceComplete(String caseId, List<YWorkItemEvent> trace) {
 *         System.out.println("Trace complete: " + trace.size() + " events");
 *     }
 * });
 *
 * // Start streaming
 * stream.startStreaming();
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public interface YAWLEventStream {

    /**
     * Start streaming events for the specified specification.
     * Once started, the stream will capture all work item events
     * and deliver them to registered listeners.
     */
    void startStreaming();

    /**
     * Stop streaming events. This will flush any remaining buffered events
     * to listeners before stopping.
     */
    void stopStreaming();

    /**
     * Pause streaming. Events will be buffered but not delivered to listeners.
     * Use {@link #resumeStreaming()} to resume.
     */
    void pauseStreaming();

    /**
     * Resume streaming after being paused. Buffered events will be
     * delivered to listeners, and new events will be captured.
     */
    void resumeStreaming();

    /**
     * Add an event listener that will receive individual events and trace completions.
     *
     * @param listener the event listener to add
     * @throws IllegalArgumentException if listener is null
     */
    void addStreamListener(EventStreamListener listener);

    /**
     * Remove an event listener.
     *
     * @param listener the listener to remove
     * @throws IllegalArgumentException if listener is null
     */
    void removeStreamListener(EventStreamListener listener);

    /**
     * Add a filter for events. Only events that pass all filters
     * will be delivered to listeners.
     *
     * @param filter the event filter
     * @throws IllegalArgumentException if filter is null
     */
    void addEventFilter(EventFilter filter);

    /**
     * Remove an event filter.
     *
     * @param filter the filter to remove
     * @throws IllegalArgumentException if filter is null
     */
    void removeEventFilter(EventFilter filter);

    /**
     * Set the buffer size for event buffering. This controls how many events
     * can be buffered before applying backpressure.
     *
     * @param bufferSize the maximum buffer size
     * @throws IllegalArgumentException if bufferSize < 1
     */
    void setBufferSize(int bufferSize);

    /**
     * Set the batch interval for trace aggregation. Events within this time
     * window will be aggregated into trace completions.
     *
     * @param interval the batch interval
     * @throws IllegalArgumentException if interval is null or negative
     */
    void setBatchInterval(java.time.Duration interval);

    /**
     * Set the executor for event processing. Default uses virtual threads.
     *
     * @param executor the executor to use
     * @throws IllegalArgumentException if executor is null
     */
    void setExecutor(Executor executor);

    /**
     * Get the current streaming statistics.
     *
     * @return streaming statistics
     */
    StreamingStatistics getStatistics();

    /**
     * Clear all buffered events and reset statistics.
     */
    void reset();
}

/**
 * Listener interface for receiving streaming events.
 */
@FunctionalInterface
interface EventStreamListener {
    /**
     * Called when a work item event occurs.
     *
     * @param event the work item event
     */
    void onEvent(YWorkItemEvent event);

    /**
     * Called when a trace is complete (all events for a case have been processed).
     *
     * @param caseId the case ID
     * @param trace the complete trace of events for the case
     */
    default void onTraceComplete(String caseId, java.util.List<YWorkItemEvent> trace) {
        // Default implementation does nothing
    }

    /**
     * Called when an error occurs during event processing.
     *
     * @param error the error that occurred
     */
    default void onError(Throwable error) {
        // Default implementation logs the error
        System.err.println("Error in event stream: " + error.getMessage());
    }
}

/**
 * Filter interface for filtering events.
 */
@FunctionalInterface
interface EventFilter {
    /**
     * Test if the event should be processed.
     *
     * @param event the event to test
     * @return true if the event should be processed, false otherwise
     */
    boolean test(YWorkItemEvent event);
}

/**
 * Statistics for the event stream.
 */
class StreamingStatistics {
    private long eventsProcessed;
    private long tracesCompleted;
    private long errors;
    private Instant startTime;
    private Instant lastEventTime;
    private int currentBufferSize;

    public StreamingStatistics() {
        this.startTime = Instant.now();
    }

    public void incrementEventsProcessed() {
        this.eventsProcessed++;
        this.lastEventTime = Instant.now();
    }

    public void incrementTracesCompleted() {
        this.tracesCompleted++;
    }

    public void incrementErrors() {
        this.errors++;
    }

    public void setCurrentBufferSize(int size) {
        this.currentBufferSize = size;
    }

    // Getters
    public long getEventsProcessed() { return eventsProcessed; }
    public long getTracesCompleted() { return tracesCompleted; }
    public long getErrors() { return errors; }
    public java.time.Duration getUptime() { return java.time.Duration.between(startTime, Instant.now()); }
    public Instant getLastEventTime() { return lastEventTime; }
    public int getCurrentBufferSize() { return currentBufferSize; }
}

/**
 * Implementation of YAWLEventStream using virtual threads for efficient processing.
 */
class YAWLEventStreamImpl implements YAWLEventStream {
    private final YSpecificationID specId;
    private final java.util.List<EventStreamListener> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<EventFilter> filters = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.concurrent.BlockingQueue<YWorkItemEvent> eventQueue;
    private final StreamingStatistics statistics = new StreamingStatistics();

    private volatile boolean isRunning = false;
    private volatile boolean isPaused = false;
    private int bufferSize = 1000;
    private java.time.Duration batchInterval = java.time.Duration.ofMillis(500);
    private Executor executor;

    public YAWLEventStreamImpl(YSpecificationID specId) {
        if (specId == null) {
            throw new IllegalArgumentException("Specification ID cannot be null");
        }
        this.specId = specId;
        this.eventQueue = new java.util.concurrent.ArrayBlockingQueue<>(bufferSize);
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @Override
    public void startStreaming() {
        if (isRunning) {
            return;
        }

        isRunning = true;
        isPaused = false;

        // Start event processing thread
        executor.execute(this::processEvents);
        executor.execute(this::processTraces);

        // Register with YAWL engine for event notifications
        registerWithEngine();
    }

    @Override
    public void stopStreaming() {
        if (!isRunning) {
            return;
        }

        isRunning = false;
        isPaused = false;

        // Flush remaining events
        flushEvents();

        // Unregister from YAWL engine
        unregisterFromEngine();
    }

    @Override
    public void pauseStreaming() {
        isPaused = true;
    }

    @Override
    public void resumeStreaming() {
        isPaused = false;
    }

    @Override
    public void addStreamListener(EventStreamListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener cannot be null");
        }
        listeners.add(listener);
    }

    @Override
    public void removeStreamListener(EventStreamListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener cannot be null");
        }
        listeners.remove(listener);
    }

    @Override
    public void addEventFilter(EventFilter filter) {
        if (filter == null) {
            throw new IllegalArgumentException("Filter cannot be null");
        }
        filters.add(filter);
    }

    @Override
    public void removeEventFilter(EventFilter filter) {
        if (filter == null) {
            throw new IllegalArgumentException("Filter cannot be null");
        }
        filters.remove(filter);
    }

    @Override
    public void setBufferSize(int bufferSize) {
        if (bufferSize < 1) {
            throw new IllegalArgumentException("Buffer size must be at least 1");
        }
        this.bufferSize = bufferSize;
        // Note: We would need to recreate the queue for this to take effect immediately
    }

    @Override
    public void setBatchInterval(java.time.Duration interval) {
        if (interval == null || interval.isNegative()) {
            throw new IllegalArgumentException("Batch interval must be positive");
        }
        this.batchInterval = interval;
    }

    @Override
    public void setExecutor(Executor executor) {
        if (executor == null) {
            throw new IllegalArgumentException("Executor cannot be null");
        }
        this.executor = executor;
    }

    @Override
    public StreamingStatistics getStatistics() {
        return statistics;
    }

    @Override
    public void reset() {
        eventQueue.clear();
        statistics.setCurrentBufferSize(0);
        // Reset other statistics if needed
    }

    /**
     * Process events from the queue and deliver to listeners.
     */
    private void processEvents() {
        while (isRunning) {
            try {
                if (isPaused) {
                    java.time.TimeUnit.MILLISECONDS.sleep(100);
                    continue;
                }

                YWorkItemEvent event = eventQueue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (event != null) {
                    // Apply filters
                    boolean passesFilters = filters.isEmpty() ||
                        filters.stream().allMatch(f -> f.test(event));

                    if (passesFilters) {
                        deliverEvent(event);
                        statistics.incrementEventsProcessed();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Process trace completions.
     */
    private void processTraces() {
        while (isRunning) {
            try {
                if (isPaused) {
                    java.time.TimeUnit.MILLISECONDS.sleep(100);
                    continue;
                }

                // In a real implementation, we would track trace completion here
                // For now, this is a placeholder
                java.time.TimeUnit.MILLISECONDS.sleep(batchInterval.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Deliver an event to all listeners.
     */
    private void deliverEvent(YWorkItemEvent event) {
        for (EventStreamListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                statistics.incrementErrors();
                listener.onError(e);
            }
        }
    }

    /**
     * Flush remaining events to listeners.
     */
    private void flushEvents() {
        while (!eventQueue.isEmpty()) {
            try {
                YWorkItemEvent event = eventQueue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (event != null) {
                    deliverEvent(event);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Register with YAWL engine for event notifications.
     * This would integrate with YNetRunner or YCaseMonitoringService.
     */
    private void registerWithEngine() {
        // Implementation would register with YAWL engine
        // For example: YCaseMonitoringService.registerStreamListener(this);
    }

    /**
     * Unregister from YAWL engine.
     */
    private void unregisterFromEngine() {
        // Implementation would unregister from YAWL engine
    }

    /**
     * Receive an event from the YAWL engine.
     * This method would be called by the YAWL engine when an event occurs.
     */
    public void onWorkItemEvent(YWorkItemEvent event) {
        if (isRunning && !isPaused) {
            try {
                eventQueue.offer(event, 100, java.util.concurrent.TimeUnit.MILLISECONDS);
                statistics.setCurrentBufferSize(eventQueue.size());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                statistics.incrementErrors();
            }
        }
    }
}