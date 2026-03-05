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
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.synthesis;

import org.yawlfoundation.yawl.integration.verification.VerificationReport;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Result of workflow synthesis from business intent.
 *
 * <p>Contains the generated specification XML (or net description), soundness verification
 * proof, and metadata about the synthesis process (elapsed time, WCP patterns applied).</p>
 *
 * @param specXml the generated YAWL specification XML (or net description)
 * @param soundnessReport verification report from soundness checking
 *                       (null if synthesis failed before verification)
 * @param wcpPatternsUsed list of WCP pattern IDs that were applied
 * @param elapsed total synthesis time including verification
 * @param successful true if synthesis completed successfully (soundness verification passed)
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record SynthesisResult(
    String specXml,
    VerificationReport soundnessReport,
    List<String> wcpPatternsUsed,
    Duration elapsed,
    boolean successful
) {
    /**
     * Constructs a SynthesisResult with validation.
     *
     * @throws NullPointerException if elapsed is null
     */
    public SynthesisResult {
        Objects.requireNonNull(elapsed, "elapsed must not be null");
        if (wcpPatternsUsed == null) {
            wcpPatternsUsed = List.of();
        }
    }

    /**
     * Returns true if this synthesis produced a sound workflow.
     *
     * @return true iff successful and soundnessReport indicates isSound
     */
    public boolean isSoundWorkflow() {
        return successful && soundnessReport != null && soundnessReport.isSound();
    }
}
