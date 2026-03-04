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

package org.yawlfoundation.yawl.dspy.signature;

/**
 * Marker interface for type-safe signature templates.
 *
 * <p>Extend this interface and use {@link SignatureCompiler.SigDef},
 * {@link SignatureCompiler.In}, and {@link SignatureCompiler.Out} annotations
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
 * @author YAWL Foundation
 * @version 6.0.0
 */
public interface SignatureTemplate {

    /**
     * Get the compiled Signature for this template.
     */
    default Signature getSignature() {
        @SuppressWarnings("unchecked")
        Class<? extends SignatureTemplate> clazz = (Class<? extends SignatureTemplate>) this.getClass();
        return SignatureCompiler.compile(clazz);
    }
}
