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

package org.yawlfoundation.yawl.integration.fmea;

import java.util.Objects;

/**
 * Immutable record representing a single detected FMEA violation for an A2A agent.
 *
 * <p>A violation is produced by {@link A2AFmeaAnalyzer} when an A2A object
 * (agent principal, handoff token, skill request, or auth configuration) satisfies
 * the conditions for one of the {@link A2AFailureModeType} failure modes.
 *
 * <p>Example:
 * <pre>{@code
 * // FM_A3 violation from an expired handoff token
 * new A2AFmeaViolation(
 *     A2AFailureModeType.FM_A3_HANDOFF_TOKEN_EXPIRY,
 *     "workItemId=WI-42, fromAgent=agent-A, toAgent=agent-B",
 *     "token expired at 2026-02-25T14:32:15Z"
 * )
 * }</pre>
 *
 * @param mode     the failure mode that was triggered; never {@code null}
 * @param context  human-readable identification of the subject being analysed
 *                 (e.g. {@code "agent=agent-A, scheme=Bearer"}); never {@code null}
 * @param evidence human-readable explanation of why the violation was raised
 *                 (e.g. {@code "token expired at 2026-02-25T14:32:15Z"}); never {@code null}
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public record A2AFmeaViolation(
        A2AFailureModeType mode,
        String context,
        String evidence) {

    /**
     * Compact canonical constructor — validates that no field is {@code null}.
     */
    public A2AFmeaViolation {
        Objects.requireNonNull(mode,     "mode must not be null");
        Objects.requireNonNull(context,  "context must not be null");
        Objects.requireNonNull(evidence, "evidence must not be null");
    }

    /**
     * Convenience accessor for the RPN of the underlying failure mode.
     *
     * @return {@code mode().rpn()}
     */
    public int rpn() {
        return mode.rpn();
    }

    /**
     * Returns a single-line summary suitable for log messages.
     * Format: {@code [FM_A3_HANDOFF_TOKEN_EXPIRY|RPN=105] workItemId=WI-42 — token expired…}
     *
     * @return formatted summary string
     */
    @Override
    public String toString() {
        return "[" + mode.name() + "|RPN=" + rpn() + "] "
             + context + " — " + evidence;
    }
}
