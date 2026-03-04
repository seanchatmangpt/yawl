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

package org.yawlfoundation.yawl.signature.model;

/**
 * Marker interface for type-safe signature templates.
 *
 * <p>Extend this interface and use {@link annotations.SigDef},
 * {@link annotations.In}, and {@link annotations.Out} annotations
 * to define signatures with compile-time type safety:
 *
 * {@snippet :
 * @SigDef(description = "Predict case outcome")
 * interface CaseOutcomePredictor extends SignatureTemplate {
 *     @In(desc = "workflow specification ID") String specId();
 *     @In(desc = "case duration in milliseconds") long durationMs();
 *     @In(desc = "number of completed tasks") int taskCount();
 *
 *     @Out(desc = "predicted outcome: 'completed' or 'failed'") String outcome();
 *     @Out(desc = "confidence score between 0 and 1") double confidence();
 * }
 * }
 *
 * <p>The runtime will:
 * <ol>
 *   <li>Extract signature metadata via reflection</li>
 *   <li>Generate optimized prompts from the annotations</li>
 *   <li>Parse LLM output into typed results</li>
 * </ol>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public interface SignatureTemplate {

    /**
     * Convert this template to a runtime Signature.
     *
     * <p>Default implementation uses reflection to extract fields
     * from annotations. Override for custom behavior.
     *
     * @return Signature for this template
     */
    default Signature toSignature() {
        return SignatureCompiler.compile(this.getClass());
    }

    /**
     * Get the description from the {@link annotations.SigDef} annotation.
     *
     * @return description or empty string if not annotated
     */
    default String getDescription() {
        annotations.SigDef def = this.getClass().getAnnotation(annotations.SigDef.class);
        return def != null ? def.description() : "";
    }
}
