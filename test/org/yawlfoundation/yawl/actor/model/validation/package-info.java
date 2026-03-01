/*
 * YAWL - Yet Another Workflow Language
 * Copyright (C) 2003-2006, 2008-2011, 2014-2019 National University of Ireland, Galway
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

/**
 * YAWL Actor Model Validation Suite - Phase 2
 *
 * This package contains comprehensive validation tests for YAWL's actor model
 * scalability claims, specifically targeting 10M agent deployments.
 *
 * Package Structure:
 * - ActorModelScaleTest: Scale testing at 100K-10M agents
 * - ActorModelPerformanceTest: Performance threshold validation
 * - ActorModelStressTest: Stress and stability testing
 * - ActorModelValidationSuite: Comprehensive orchestration
 * - MetricsCollector: Real-time metrics collection
 *
 * Test Categories:
 * 1. Scale Testing - Heap, GC, thread utilization at critical scales
 * 2. Performance Validation - Latency, throughput, message delivery
 * 3. Stress Testing - Stability, flood, bursts, recovery
 *
 * Key Claims Validated:
 * - 10M agent scale support
 * - ≤150 bytes heap per agent
 * - p99 latency <100ms
 * - >10K msg/sec/agent
 * - Zero message loss
 * - 24-hour stability
 *
 * @since 6.0.0
 */
package org.yawlfoundation.yawl.actor.model.validation;