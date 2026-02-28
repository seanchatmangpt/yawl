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
 * Validation components for YAWL DSPy workflows.
 *
 * <h2>Package Overview</h2>
 * This package provides comprehensive validation capabilities for workflow generation,
 * focusing on perfect generation criteria and behavioral footprint analysis.
 *
 * <h2>Key Components</h2>
 * <ul>
 *   <li>{@link PerfectWorkflowValidator} - Main validation class for perfect generation criteria</li>
 *   <li>{@link ValidationResult} - Record representing validation results</li>
 *   <li>{@link ValidationMetric} - Record representing individual validation metrics</li>
 *   <li>{@link PerfectGenerationException} - Exception for validation failures</li>
 * </ul>
 *
 * <h2>Validation Strategy</h2>
 * <p>The validator implements a multi-faceted approach:</p>
 * <ul>
 *   <li><strong>Behavioral Footprint Analysis</strong> - Uses FootprintScorer to measure conformance</li>
 *   <li><strong>Performance Validation</strong> - Measures execution time and resource utilization</li>
 *   <li><strong>Semantic Accuracy</strong> - Optional LLM-based validation of workflow semantics</li>
 *   <li><strong>Perfect Generation Check</strong> - Ensures footprint equals 1.0</li>
 * </ul>
 *
 * <h2>Usage Pattern</h2>
 * <pre>{@code
 * PerfectWorkflowValidator validator = new PerfectWorkflowValidator(true);
 * ValidationResult result = validator.validatePerfectWorkflow(
 *     generatedWorkflow,
 *     referenceWorkflow,
 *     GepaOptimizationTarget.BEHAVIORAL
 * );
 *
 * if (result.perfectGeneration()) {
 *     System.out.println("Perfect workflow generated!");
 * }
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
package org.yawlfoundation.yawl.dspy.validation;