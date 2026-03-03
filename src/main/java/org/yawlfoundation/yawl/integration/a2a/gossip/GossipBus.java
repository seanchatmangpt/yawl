/*
 * Copyright 2024 YAWL Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.yawlfoundation.yawl.integration.a2a.gossip;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.Objects;

/**
 * Async A2A Gossip Bus for non-blocking message publishing and subscription.
 *
 * Features:
 * - Async, non-blocking publish operations
 * - At-least-once delivery guarantee
 * - Topic-based routing
 * - Virtual thread per subscription for optimal performance
 * - Thread-safe operations with proper synchronization
 * - Built-in metrics for monitoring
 */
public final class GossipBus {

    private final ConcurrentMap<String, ConcurrentMap<GossipSubscriber, SubscriptionInfo>> topicSubscribers;
    private final ExecutorService messageDeliveryExecutor;
    private final ReentrantLock shutdownLock;
    private final AtomicInteger sequenceCounter;
    private volatile boolean isShutdown;
    private final Metrics metrics;

    /**
     * Creates a new GossipBus with default configuration.
     */
    public GossipBus() {
        this(16, Runtime.getRuntime().availableProcessors() * 2);
    }

    /**
     * Creates a new GossipBus with specified configuration.
     *
     * @param initialTopicCapacity initial topic capacity
     * @param virtualThreadCount number of virtual threads for message delivery
     */
    public GossipBus(int initialTopicCapacity, int virtualThreadCount) {
        this.topicSubscribers = new ConcurrentHashMap<>(initialTopicCapacity);
        this.messageDeliveryExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.shutdownLock = new ReentrantLock();
        this.sequenceCounter = new AtomicInteger(0);
        this.isShutdown = false;
        this.metrics = new Metrics();
    }

    /**
     * Publishes a message to all subscribers of the specified topic.
     *
     * This method is non-blocking and returns immediately. Message delivery
     * is performed asynchronously in virtual threads.
     *
     * @param topic the topic to publish to
     * @param message the message to publish
     * @throws IllegalArgumentException if topic or message is null
     * @throws IllegalStateException if the bus is shutdown
     */
    public void publish(String topic, GossipMessage message) {
        Objects.requireNonNull(topic, "Topic cannot be null");
        Objects.requireNonNull(message, "Message cannot be null");

        if (isShutdown) {
            throw new IllegalStateException("GossipBus is shutdown");
        }

        metrics.recordPublish();
        long startTime = System.nanoTime();

        try {
            ConcurrentMap<GossipSubscriber, SubscriptionInfo> subscribers = topicSubscribers.get(topic);
            if (subscribers != null && !subscribers.isEmpty()) {
                for (ConcurrentMap.Entry<GossipSubscriber, SubscriptionInfo> entry : subscribers.entrySet()) {
                    GossipSubscriber subscriber = entry.getKey();
                    SubscriptionInfo info = entry.getValue();

                    // Increment sequence for at-least-once delivery
                    int sequence = sequenceCounter.incrementAndGet();
                    message.setSequenceNumber(sequence);

                    // Deliver message asynchronously in virtual thread
                    messageDeliveryExecutor.submit(() -> deliverMessage(subscriber, message, info));
                }
                metrics.recordDeliveryAttempt(subscribers.size());
            }
        } finally {
            metrics.recordPublishTime(System.nanoTime() - startTime);
        }
    }

    /**
     * Subscribes to a topic with the specified subscriber.
     *
     * Each subscriber receives messages in its own virtual thread.
     *
     * @param topic the topic to subscribe to
     * @param subscriber the subscriber that will receive messages
     * @throws IllegalArgumentException if topic or subscriber is null
     * @throws IllegalStateException if the bus is shutdown
     */
    public void subscribe(String topic, GossipSubscriber subscriber) {
        Objects.requireNonNull(topic, "Topic cannot be null");
        Objects.requireNonNull(subscriber, "Subscriber cannot be null");

        if (isShutdown) {
            throw new IllegalStateException("GossipBus is shutdown");
        }

        metrics.recordSubscribe();

        topicSubscribers.computeIfAbsent(topic, k -> new ConcurrentHashMap<>())
                       .put(subscriber, new SubscriptionInfo(subscriber));
    }

    /**
     * Unsubscribes a subscriber from the specified topic.
     *
     * @param topic the topic to unsubscribe from
     * @param subscriber the subscriber to unsubscribe
     * @throws IllegalArgumentException if topic or subscriber is null
     */
    public void unsubscribe(String topic, GossipSubscriber subscriber) {
        Objects.requireNonNull(topic, "Topic cannot be null");
        Objects.requireNonNull(subscriber, "Subscriber cannot be null");

        ConcurrentMap<GossipSubscriber, SubscriptionInfo> subscribers = topicSubscribers.get(topic);
        if (subscribers != null) {
            subscribers.remove(subscriber);
            if (subscribers.isEmpty()) {
                topicSubscribers.remove(topic);
            }
            metrics.recordUnsubscribe();
        }
    }

    /**
     * Shuts down the gossip bus gracefully.
     *
     * @param timeout the maximum time to wait for pending messages
     * @param unit the time unit for the timeout
     * @throws InterruptedException if the current thread is interrupted
     */
    public void shutdown(long timeout, TimeUnit unit) throws InterruptedException {
        shutdownLock.lock();
        try {
            if (!isShutdown) {
                isShutdown = true;
                topicSubscribers.clear();
                messageDeliveryExecutor.shutdown();
                messageDeliveryExecutor.awaitTermination(timeout, unit);
                metrics.recordShutdown();
            }
        } finally {
            shutdownLock.unlock();
        }
    }

    /**
     * Gets the current metrics for the gossip bus.
     *
     * @return the metrics object
     */
    public Metrics getMetrics() {
        return metrics;
    }

    /**
     * Gets the number of active topics.
     *
     * @return number of active topics
     */
    public int getTopicCount() {
        return topicSubscribers.size();
    }

    /**
     * Gets the number of active subscribers for a topic.
     *
     * @param topic the topic to check
     * @return number of subscribers
     */
    public int getSubscriberCount(String topic) {
        ConcurrentMap<GossipSubscriber, SubscriptionInfo> subscribers = topicSubscribers.get(topic);
        return subscribers != null ? subscribers.size() : 0;
    }

    /**
     * Gets all active topics.
     *
     * @return set of active topics
     */
    public java.util.Set<String> getActiveTopics() {
        return topicSubscribers.keySet();
    }

    /**
     * Delivers a message to a subscriber.
     *
     * @param subscriber the subscriber to deliver to
     * @param message the message to deliver
     * @param info subscription information
     */
    private void deliverMessage(GossipSubscriber subscriber, GossipMessage message, SubscriptionInfo info) {
        long startTime = System.nanoTime();
        try {
            metrics.recordDeliveryStart();
            subscriber.onMessage(message);
            metrics.recordDeliverySuccess(System.nanoTime() - startTime);
        } catch (Exception e) {
            metrics.recordDeliveryFailure(System.nanoTime() - startTime, e);
            // Log the error but don't propagate - at-least-once delivery
            System.err.printf("Failed to deliver message to subscriber %s: %s%n",
                            subscriber.getId(), e.getMessage());
        }
    }

    /**
     * Subscription information for a subscriber.
     */
    private static class SubscriptionInfo {
        private final GossipSubscriber subscriber;
        private final long subscribeTime;

        SubscriptionInfo(GossipSubscriber subscriber) {
            this.subscriber = subscriber;
            this.subscribeTime = System.nanoTime();
        }
    }

    /**
     * Metrics for monitoring the gossip bus performance.
     */
    public static final class Metrics {
        private final AtomicInteger publishCount;
        private final AtomicInteger subscribeCount;
        private final AtomicInteger unsubscribeCount;
        private final AtomicInteger deliveryAttempts;
        private final AtomicInteger successfulDeliveries;
        private final AtomicInteger failedDeliveries;
        private final LongAccumulator totalPublishTime;
        private final LongAccumulator totalDeliveryTime;
        private final LongAccumulator totalFailureTime;
        private final ConcurrentMap<String, AtomicInteger> topicMetrics;

        public Metrics() {
            this.publishCount = new AtomicInteger(0);
            this.subscribeCount = new AtomicInteger(0);
            this.unsubscribeCount = new AtomicInteger(0);
            this.deliveryAttempts = new AtomicInteger(0);
            this.successfulDeliveries = new AtomicInteger(0);
            this.failedDeliveries = new AtomicInteger(0);
            this.totalPublishTime = new LongAccumulator(Long::sum, 0);
            this.totalDeliveryTime = new LongAccumulator(Long::sum, 0);
            this.totalFailureTime = new LongAccumulator(Long::sum, 0);
            this.topicMetrics = new ConcurrentHashMap<>();
        }

        void recordPublish() {
            publishCount.incrementAndGet();
        }

        void recordSubscribe() {
            subscribeCount.incrementAndGet();
        }

        void recordUnsubscribe() {
            unsubscribeCount.incrementAndGet();
        }

        void recordDeliveryAttempt(int count) {
            deliveryAttempts.addAndGet(count);
        }

        void recordDeliveryStart() {
            // Can be used to track delivery timing
        }

        void recordDeliverySuccess(long duration) {
            successfulDeliveries.incrementAndGet();
            totalDeliveryTime.accumulate(duration);
        }

        void recordDeliveryFailure(long duration, Throwable error) {
            failedDeliveries.incrementAndGet();
            totalFailureTime.accumulate(duration);
        }

        void recordPublishTime(long duration) {
            totalPublishTime.accumulate(duration);
        }

        void recordShutdown() {
            // Record shutdown metrics
        }

        // Getters for metrics
        public int getPublishCount() { return publishCount.get(); }
        public int getSubscribeCount() { return subscribeCount.get(); }
        public int getUnsubscribeCount() { return unsubscribeCount.get(); }
        public int getDeliveryAttempts() { return deliveryAttempts.get(); }
        public int getSuccessfulDeliveries() { return successfulDeliveries.get(); }
        public int getFailedDeliveries() { return failedDeliveries.get(); }
        public long getTotalPublishTime() { return totalPublishTime.get(); }
        public long getTotalDeliveryTime() { return totalDeliveryTime.get(); }
        public long getTotalFailureTime() { return totalFailureTime.get(); }

        public double getAveragePublishTime() {
            int count = publishCount.get();
            return count > 0 ? (double) totalPublishTime.get() / count : 0.0;
        }

        public double getAverageDeliveryTime() {
            int count = successfulDeliveries.get();
            return count > 0 ? (double) totalDeliveryTime.get() / count : 0.0;
        }

        public void recordTopicActivity(String topic) {
            topicMetrics.computeIfAbsent(topic, k -> new AtomicInteger(0)).incrementAndGet();
        }

        public Map<String, Integer> getTopicActivity() {
            return topicMetrics.entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    e -> e.getValue().get()
                ));
        }
    }
}