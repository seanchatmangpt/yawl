/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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
 * 3-stage pipeline for LLM-based YAWL process model generation.
 *
 * <p>This package implements the pipeline described in the van der Aalst
 * "Process Modeling With Large Language Models" (2024) paper, with improvements:
 * <ul>
 *   <li>YAWL output format (not BPMN/Petri nets)</li>
 *   <li>3-stage intermediate representations</li>
 *   <li>Multi-layer validation (XSD + rust4pm + virtual exec)</li>
 *   <li>GEPA evolutionary prompt optimization</li>
 * </ul>
 *
 * <h2>Architecture:</h2>
 * <pre>
 * ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
 * │   STAGE 1    │───▶│   STAGE 2    │───▶│   STAGE 3    │
 * │ NL → Spec    │    │ Spec → Graph │    │ Graph → YAWL │
 * │   (DSPy)     │    │   (DSPy)     │    │   (DSPy)     │
 * └──────────────┘    └──────────────┘    └──────────────┘
 * </pre>
 *
 * <h2>Quick Start:</h2>
 * <pre>{@code
 * // Configure DSPy
 * Dspy.configureGroq();
 *
 * // Create generator
 * YawlGenerator gen = YawlGenerator.create();
 *
 * // Generate YAWL from natural language
 * YawlSpec spec = gen.generate("""
 *     Patient admission process:
 *     1. Triage (OR-join from emergency)
 *     2. Registration || Insurance verification
 *     3. Bed assignment (cancellation if ICU)
 *     """);
 *
 * // Load into YEngine
 * YEngine.getInstance().loadSpecification(spec.yawlXml());
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see org.yawlfoundation.yawl.ggen.model.ProcessSpec
 * @see org.yawlfoundation.yawl.ggen.model.ProcessGraph
 * @see org.yawlfoundation.yawl.ggen.model.YawlSpec
 */
package org.yawlfoundation.yawl.ggen.pipeline;
