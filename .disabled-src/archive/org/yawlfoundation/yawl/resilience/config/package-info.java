/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
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
 * Configuration for YAWL resilience patterns.
 *
 * <p>This package provides centralized configuration for Resilience4j patterns
 * including circuit breakers, retries, rate limiters, bulkheads, and time limiters.</p>
 *
 * <p>Key components:</p>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.resilience.config.ResilienceConfig} - Platform-level Resilience4j configuration</li>
 *   <li>{@link org.yawlfoundation.yawl.resilience.config.YawlResilienceProperties} - Properties-based configuration</li>
 * </ul>
 *
 * <p>Supported patterns:</p>
 * <ul>
 *   <li>Circuit Breakers for external service calls</li>
 *   <li>Retry with exponential backoff and jitter</li>
 *   <li>Rate limiting for multi-agent fan-out</li>
 *   <li>Bulkheads for concurrent workflow isolation</li>
 *   <li>Time limiters for operation timeouts</li>
 * </ul>
 */
package org.yawlfoundation.yawl.resilience.config;
