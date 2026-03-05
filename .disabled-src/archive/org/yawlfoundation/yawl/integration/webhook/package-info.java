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

/**
 * Webhook delivery system for YAWL workflow events (v6.0.0).
 *
 * <p>This package provides a production-grade outbound webhook system that pushes
 * YAWL workflow lifecycle events to registered HTTP endpoints. Key capabilities:
 *
 * <ul>
 *   <li><b>Reliable Delivery</b> - exponential-backoff retry with configurable max
 *       attempts (default: 7 attempts over ~2 hours). Failed deliveries after
 *       exhausted retries are routed to a dead-letter queue for manual inspection.</li>
 *   <li><b>Signature Verification</b> - every outbound request carries an
 *       {@code X-YAWL-Signature-256} header containing {@code sha256=<hmac>} where
 *       the HMAC-SHA256 is computed over the raw request body using the webhook's
 *       secret key. Receivers must verify this signature before processing events.</li>
 *   <li><b>Subscription Management</b> - REST API for CRUD operations on webhook
 *       subscriptions, including per-subscription event type filtering and secret
 *       rotation without downtime.</li>
 *   <li><b>Delivery Audit</b> - every delivery attempt is logged with HTTP status,
 *       latency, and response body (truncated to 512 bytes) for debugging.</li>
 * </ul>
 *
 * <h2>Retry Schedule</h2>
 * <pre>
 * Attempt 1: immediate
 * Attempt 2: +5s
 * Attempt 3: +30s
 * Attempt 4: +5m
 * Attempt 5: +30m
 * Attempt 6: +2h
 * Attempt 7: +8h  (final; failure routed to dead-letter)
 * </pre>
 *
 * <h2>Signature Verification (receiver side)</h2>
 * <pre>
 * String receivedSig = request.getHeader("X-YAWL-Signature-256"); // "sha256=<hex>"
 * byte[] body = request.getInputStream().readAllBytes();
 * String computedHmac = HmacSha256.hex(webhookSecret, body);
 * if (!MessageDigest.isEqual(
 *         computedHmac.getBytes(UTF_8),
 *         receivedSig.substring(7).getBytes(UTF_8))) {
 *     throw new SecurityException("Webhook signature mismatch");
 * }
 * </pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
package org.yawlfoundation.yawl.integration.webhook;
