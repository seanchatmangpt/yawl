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
 * Fluent API for building OTP-supervised process mining pipelines.
 *
 * <h2>Architecture</h2>
 *
 * <p>This package bridges two paradigms:
 * <ul>
 *   <li><strong>Erlang/OTP supervision</strong> — restart strategies
 *       ({@link org.yawlfoundation.yawl.erlang.fluent.RestartStrategy}),
 *       health checks, and circuit breaker protection</li>
 *   <li><strong>Process mining pipelines</strong> — composable stages for
 *       parsing event logs, discovering process models, and checking conformance</li>
 * </ul>
 *
 * <h2>Design Principles</h2>
 *
 * <ul>
 *   <li><strong>Immutable configuration</strong> — all specs are records;
 *       pipelines are immutable after construction</li>
 *   <li><strong>Type-safe context passing</strong> — {@link PipelineContext}
 *       provides typed access to prior stage outputs</li>
 *   <li><strong>Backend-agnostic</strong> — stages are {@code Function<PipelineContext, Object>},
 *       allowing either Erlang bridge or Rust4PM bridge as the backend</li>
 *   <li><strong>Exhaustive events</strong> — sealed {@link PipelineEvent} hierarchy
 *       enables compile-time checked event handling</li>
 * </ul>
 *
 * <h2>Quick Start</h2>
 *
 * <pre>{@code
 * PipelineResult result = ProcessMiningPipeline.builder()
 *     .supervision(sup -> sup
 *         .strategy(RestartStrategy.ONE_FOR_ONE)
 *         .maxRestarts(3))
 *     .circuitBreaker(cb -> cb
 *         .failureThreshold(5)
 *         .resetTimeout(Duration.ofSeconds(60)))
 *     .onEvent(System.out::println)
 *     .stage("parse", ctx -> engine.parseOcel2Json(json))
 *     .stage("discover", ctx -> engine.discoverDfg(ctx.get("parse", LogHandle.class)))
 *     .build()
 *     .execute();
 * }</pre>
 *
 * @see org.yawlfoundation.yawl.erlang.fluent.ProcessMiningPipeline
 * @see org.yawlfoundation.yawl.erlang.fluent.SupervisionSpec
 * @see org.yawlfoundation.yawl.erlang.fluent.CircuitBreakerSpec
 * @see org.yawlfoundation.yawl.erlang.fluent.PipelineEvent
 */
package org.yawlfoundation.yawl.erlang.fluent;
