/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

/**
 * Production resilience patterns for autonomous YAWL agents.
 *
 * <p>This package provides battle-tested resilience components for production deployments:
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.resilience.RetryPolicy} -
 *       Exponential backoff retry logic for transient failures</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.resilience.CircuitBreaker} -
 *       Circuit breaker pattern to prevent cascading failures</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.resilience.FallbackHandler} -
 *       Graceful degradation with primary/fallback operations</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Retry with exponential backoff
 * RetryPolicy retry = new RetryPolicy(3, 2000);  // 3 attempts, 2s initial backoff
 * String result = retry.executeWithRetry(() -> {
 *     return interfaceBClient.checkConnection(sessionHandle);
 * });
 *
 * // Circuit breaker for external services
 * CircuitBreaker breaker = new CircuitBreaker("zai-service", 5, 30000);
 * String response = breaker.execute(() -> {
 *     return zaiService.chat(prompt);
 * });
 *
 * // Fallback handler
 * FallbackHandler fallback = new FallbackHandler();
 * String output = fallback.executeWithFallback(
 *     () -> zaiReasoner.produceOutput(workItem),
 *     () -> templateReasoner.produceOutput(workItem),
 *     "decision-reasoning"
 * );
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>All components in this package are thread-safe and suitable for concurrent use
 * in multi-threaded autonomous agent environments.
 *
 * @author YAWL Production Validator
 * @version 5.2
 * @since 5.2
 */
package org.yawlfoundation.yawl.integration.autonomous.resilience;
