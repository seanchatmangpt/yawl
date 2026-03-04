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
 * DSPy-style signatures for type-safe LLM interactions.
 *
 * <p>Signatures define the input/output contract of an LLM call, separating
 * <em>what</em> you want from <em>how</em> to prompt for it. The DSPy runtime
 * generates optimized prompts from signatures.
 *
 * <h2>Core Types:</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.dspy.signature.Signature} - Input/output contract</li>
 *   <li>{@link org.yawlfoundation.yawl.dspy.signature.InputField} - Input parameter definition</li>
 *   <li>{@link org.yawlfoundation.yawl.dspy.signature.OutputField} - Output field definition</li>
 *   <li>{@link org.yawlfoundation.yawl.dspy.signature.SignatureResult} - Parsed LLM output</li>
 *   <li>{@link org.yawlfoundation.yawl.dspy.signature.Example} - Few-shot example</li>
 * </ul>
 *
 * <h2>Defining Signatures:</h2>
 *
 * <h3>Method 1: Builder API</h3>
 * {@snippet :
 * var sig = Signature.builder()
 *     .description("Predict case outcome")
 *     .input("caseEvents", "list of events", List.class)
 *     .input("caseDuration", "duration in ms", Long.class)
 *     .output("outcome", "predicted outcome", String.class)
 *     .output("confidence", "confidence 0-1", Double.class)
 *     .build();
 * }
 *
 * <h3>Method 2: Annotation-based Templates</h3>
 * {@snippet :
 * @SigDef(description = "Predict case outcome")
 * interface CaseOutcomePredictor extends SignatureTemplate {
 *     @In(desc = "list of events") List<CaseEvent> events();
 *     @In(desc = "duration in ms") long duration();
 *     @Out(desc = "predicted outcome") String outcome();
 *     @Out(desc = "confidence score") double confidence();
 * }
 *
 * Signature sig = Signature.fromTemplate(CaseOutcomePredictor.class);
 * }
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see org.yawlfoundation.yawl.dspy.module.Predict
 * @see org.yawlfoundation.yawl.dspy.module.ChainOfThought
 */
package org.yawlfoundation.yawl.dspy.signature;
