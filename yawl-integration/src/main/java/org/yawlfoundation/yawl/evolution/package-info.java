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
 * Workflow evolution engine with self-optimization capability.
 *
 * <p>This package provides self-evolving workflow capabilities that monitor
 * bottlenecks in real-time, generate optimized sub-workflows via Z.AI,
 * and hot-swap them using Ripple-Down Rules (RDR) for dynamic adaptation.</p>
 *
 * <p><b>Core Components</b>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.evolution.WorkflowEvolutionEngine}
 *       - Main orchestrator for bottleneck detection and evolution</li>
 * </ul>
 *
 * <p><b>Workflow Evolution Process</b>
 * <ol>
 *   <li>Monitor workflow execution for bottlenecks via BottleneckDetector</li>
 *   <li>Evaluate evolution ROI (only proceed if contribution > 20%)</li>
 *   <li>Generate optimized sub-workflow via Z.AI SpecificationGenerator</li>
 *   <li>Validate generated specification (no TODOs, mocks, stubs)</li>
 *   <li>Install in Ripple-Down Rules tree for task substitution</li>
 *   <li>Register with PredictiveRouter for dynamic task routing</li>
 *   <li>Track evolution history and speedup factors</li>
 * </ol>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
package org.yawlfoundation.yawl.integration.evolution;
