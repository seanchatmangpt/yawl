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
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.webhook;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.integration.messagequeue.WorkflowEvent;
import org.yawlfoundation.yawl.integration.messagequeue.WorkflowEventSerializer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Reliable outbound webhook delivery service with exponential-backoff retry.
 *
 * <p>Delivers YAWL workflow events to registered HTTP endpoints with:
 * <ul>
 *   <li>HMAC-SHA256 request signing via {@link WebhookSigner}</li>
 *   <li>Exponential backoff retry schedule: 0s, 5s, 30s, 5m, 30m, 2h, 8h (7 attempts)</li>
 *   <li>Delivery audit logging for every attempt</li>
 *   <li>Automatic subscription suspension after retry exhaustion</li>
 *   <li>Correlation ID header for distributed tracing</li>
 * </ul>
 *
 * <h2>Retry Schedule</h2>
 * <pre>
 * Attempt 1: immediate
 * Attempt 2: +5 seconds
 * Attempt 3: +30 seconds
 * Attempt 4: +5 minutes
 * Attempt 5: +30 minutes
 * Attempt 6: +2 hours
 * Attempt 7: +8 hours (final; failure routes to dead-letter log)
 * </pre>
 *
 * <h2>Request Headers</h2>
 * <pre>
 * Content-Type: application/json; charset=UTF-8
 * X-YAWL-Event-ID: {eventId}
 * X-YAWL-Event-Type: {eventType}
 * X-YAWL-Delivery-ID: {unique-delivery-attempt-uuid}
 * X-YAWL-Signature-256: sha256={hmac-hex}
 * X-Request-ID: {correlationId}
 * User-Agent: YAWL-Webhook/6.0.0
 * </pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class WebhookDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryService.class);

    private static final String USER_AGENT = "YAWL-Webhook/6.0.0";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Retry delay schedule in seconds.
     * Index 0 = first retry delay (after immediate attempt 1 fails), etc.
     */
    private static final long[] RETRY_DELAYS_SECONDS = {5L, 30L, 300L, 1800L, 7200L, 28800L};

    private final WebhookSubscriptionRepository subscriptionRepo;
    private final WebhookDeliveryLog            deliveryLog;
    private final WorkflowEventSerializer       serializer;
    private final HttpClient                    httpClient;
    private final ScheduledExecutorService      retryScheduler;
    private final ExecutorService               deliveryExecutor;

    /**
     * Construct the delivery service.
     *
     * @param subscriptionRepo repository for loading active webhook subscriptions
     * @param deliveryLog      audit log for recording delivery attempts
     */
    public WebhookDeliveryService(WebhookSubscriptionRepository subscriptionRepo,
                                  WebhookDeliveryLog deliveryLog) {
        this.subscriptionRepo = Objects.requireNonNull(subscriptionRepo, "subscriptionRepo");
        this.deliveryLog      = Objects.requireNonNull(deliveryLog, "deliveryLog");
        this.serializer       = new WorkflowEventSerializer();
        this.httpClient       = HttpClient.newBuilder()
                                          .connectTimeout(REQUEST_TIMEOUT)
                                          .build();
        this.deliveryExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.retryScheduler   = Executors.newScheduledThreadPool(
                2,
                r -> {
                    Thread t = new Thread(r, "yawl-webhook-retry");
                    t.setDaemon(true);
                    return t;
                }
        );
    }

    /**
     * Dispatch a workflow event to all matching active webhook subscriptions.
     *
     * <p>Delivery is asynchronous; this method returns immediately after submitting
     * delivery tasks. Each subscription receives the event on a virtual thread.
     * Retry scheduling for failed attempts is handled internally.
     *
     * @param event the workflow event to dispatch (must not be null)
     */
    public void dispatch(WorkflowEvent event) {
        Objects.requireNonNull(event, "event must not be null");

        List<WebhookSubscription> subscriptions = subscriptionRepo.findActive(event.getEventType());
        if (subscriptions.isEmpty()) {
            log.debug("No active webhook subscriptions for event type {}", event.getEventType());
            return;
        }

        log.debug("Dispatching event {} (type={}) to {} subscription(s)",
                  event.getEventId(), event.getEventType(), subscriptions.size());

        byte[] bodyBytes = serializeEvent(event);
        if (bodyBytes == null) {
            return; // serialization failure logged inside serializeEvent
        }

        for (WebhookSubscription sub : subscriptions) {
            if (sub.shouldDeliver(event)) {
                final byte[] body = bodyBytes;
                deliveryExecutor.submit(() -> attemptDelivery(event, sub, body, 1));
            }
        }
    }

    // -------------------------------------------------------------------------
    // Delivery execution
    // -------------------------------------------------------------------------

    private void attemptDelivery(WorkflowEvent event, WebhookSubscription subscription,
                                 byte[] bodyBytes, int attemptNumber) {
        String deliveryId = UUID.randomUUID().toString();
        Instant attemptAt = Instant.now();

        String signatureHeader;
        try {
            signatureHeader = WebhookSigner.buildSignatureHeader(
                    subscription.getSecretKey(), bodyBytes);
        } catch (WebhookSigner.SignatureException e) {
            log.error("Failed to compute signature for subscription {} event {}: {}",
                      subscription.getSubscriptionId(), event.getEventId(), e.getMessage());
            deliveryLog.recordFailure(event.getEventId(), subscription.getSubscriptionId(),
                                      deliveryId, attemptNumber, attemptAt, 0,
                                      "Signature computation failed: " + e.getMessage(),
                                      null);
            return;
        }

        HttpRequest request = buildRequest(
                subscription.getTargetUrl(), bodyBytes, signatureHeader,
                event.getEventId(), event.getEventType().name(), deliveryId);

        long startNs  = System.nanoTime();
        int  statusCode = 0;
        String responseBody = null;

        try {
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            statusCode   = response.statusCode();
            responseBody = truncate(response.body(), 512);
            long latencyMs = (System.nanoTime() - startNs) / 1_000_000;

            if (statusCode >= 200 && statusCode < 300) {
                // Success
                log.info("Webhook delivery succeeded: sub={}, event={}, attempt={}, status={}, latency={}ms",
                         subscription.getSubscriptionId(), event.getEventId(),
                         attemptNumber, statusCode, latencyMs);
                deliveryLog.recordSuccess(event.getEventId(), subscription.getSubscriptionId(),
                                          deliveryId, attemptNumber, attemptAt,
                                          statusCode, latencyMs, responseBody);
            } else {
                // Non-success HTTP status
                log.warn("Webhook delivery failed: sub={}, event={}, attempt={}, status={}",
                         subscription.getSubscriptionId(), event.getEventId(),
                         attemptNumber, statusCode);
                deliveryLog.recordFailure(event.getEventId(), subscription.getSubscriptionId(),
                                          deliveryId, attemptNumber, attemptAt,
                                          statusCode, "HTTP " + statusCode, responseBody);
                scheduleRetry(event, subscription, bodyBytes, attemptNumber);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Webhook delivery interrupted: sub={}, event={}",
                     subscription.getSubscriptionId(), event.getEventId());
        } catch (Exception e) {
            long latencyMs = (System.nanoTime() - startNs) / 1_000_000;
            log.warn("Webhook delivery error: sub={}, event={}, attempt={}: {}",
                     subscription.getSubscriptionId(), event.getEventId(),
                     attemptNumber, e.getMessage());
            deliveryLog.recordFailure(event.getEventId(), subscription.getSubscriptionId(),
                                      deliveryId, attemptNumber, attemptAt,
                                      statusCode, e.getMessage(), null);
            scheduleRetry(event, subscription, bodyBytes, attemptNumber);
        }
    }

    private void scheduleRetry(WorkflowEvent event, WebhookSubscription subscription,
                                byte[] bodyBytes, int lastAttempt) {
        int nextAttempt = lastAttempt + 1;
        int retryIndex  = lastAttempt - 1; // 0-based index into RETRY_DELAYS_SECONDS

        if (retryIndex >= RETRY_DELAYS_SECONDS.length) {
            // All retries exhausted
            log.error("Webhook delivery permanently failed after {} attempts: sub={}, event={}. "
                    + "Routing to dead-letter log.",
                    lastAttempt, subscription.getSubscriptionId(), event.getEventId());
            deliveryLog.recordDeadLetter(event.getEventId(), subscription.getSubscriptionId(),
                                         lastAttempt, Instant.now());
            // Pause the subscription to prevent further delivery storms
            subscriptionRepo.pauseSubscription(
                    subscription.getSubscriptionId(),
                    Instant.now().plusSeconds(3600)); // 1-hour auto-pause
            return;
        }

        long delaySeconds = RETRY_DELAYS_SECONDS[retryIndex];
        log.info("Scheduling webhook retry: sub={}, event={}, attempt={} in {}s",
                 subscription.getSubscriptionId(), event.getEventId(), nextAttempt, delaySeconds);

        retryScheduler.schedule(
                () -> attemptDelivery(event, subscription, bodyBytes, nextAttempt),
                delaySeconds,
                TimeUnit.SECONDS);
    }

    // -------------------------------------------------------------------------
    // HTTP request building
    // -------------------------------------------------------------------------

    private HttpRequest buildRequest(String targetUrl, byte[] bodyBytes,
                                     String signatureHeader, String eventId,
                                     String eventType, String deliveryId) {
        return HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes))
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("User-Agent", USER_AGENT)
                .header("X-YAWL-Event-ID",     eventId)
                .header("X-YAWL-Event-Type",   eventType)
                .header("X-YAWL-Delivery-ID",  deliveryId)
                .header(WebhookSigner.SIGNATURE_HEADER, signatureHeader)
                .header("X-Request-ID",         deliveryId)
                .build();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private byte[] serializeEvent(WorkflowEvent event) {
        try {
            return serializer.serialize(event);
        } catch (WorkflowEventSerializer.SerializationException e) {
            log.error("Failed to serialize event {} for webhook dispatch: {}",
                      event.getEventId(), e.getMessage());
            return null;
        }
    }

    private static String truncate(String s, int maxLength) {
        if (s == null) return null;
        return s.length() <= maxLength ? s : s.substring(0, maxLength) + "...[truncated]";
    }

    /**
     * Shutdown the delivery executor and retry scheduler, waiting for in-flight
     * deliveries to complete (up to 60 seconds).
     */
    public void shutdown() {
        log.info("Shutting down WebhookDeliveryService...");
        deliveryExecutor.shutdown();
        retryScheduler.shutdown();
        try {
            if (!deliveryExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                deliveryExecutor.shutdownNow();
            }
            if (!retryScheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                retryScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            deliveryExecutor.shutdownNow();
            retryScheduler.shutdownNow();
        }
        log.info("WebhookDeliveryService shutdown complete");
    }
}
