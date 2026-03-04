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
 * PNML to YAWL specification synthesis and conformance analysis.
 *
 * Core components:
 * - {@link org.yawlfoundation.yawl.integration.processmining.synthesis.YawlSpecSynthesizer}
 *   synthesizes YAWL XML from parsed PNML processes
 * - {@link org.yawlfoundation.yawl.integration.processmining.synthesis.ConformanceScore}
 *   measures quality metrics (fitness, precision, generalization)
 * - {@link org.yawlfoundation.yawl.integration.processmining.synthesis.SynthesisResult}
 *   packages synthesis output with metrics and timing
 *
 * Synthesis pipeline:
 * 1. Parse PNML XML → PnmlProcess
 * 2. Validate process structure
 * 3. Synthesize YAWL specification from Petri net
 * 4. Compute conformance metrics
 * 5. Return SynthesisResult with XML and scores
 *
 * Usage:
 * <pre>
 * YawlSpecSynthesizer synth = new YawlSpecSynthesizer("http://example.com/spec", "MyWorkflow");
 * SynthesisResult result = synth.synthesizeWithConformance(process);
 * System.out.println(result.summary());
 * System.out.println(result.getYawlXml());
 * </pre>
 */
package org.yawlfoundation.yawl.integration.processmining.synthesis;
