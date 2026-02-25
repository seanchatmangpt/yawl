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
 * Immutable record representing a single detected FMEA violation for a user.
 *
 * <p>A violation is produced by {@link UserFmeaAnalyzer} when a user object
 * (principal, OIDC context, tenant context, or resource) satisfies the
 * conditions for one of the {@link UserFailureModeType} failure modes.
 *
 * <p>Example:
 * <pre>{@code
 * // FM_U1 violation from an expired principal
 * new UserFmeaViolation(
 *     UserFailureModeType.FM_U1_CREDENTIAL_EXPIRY,
 *     "principal=alice, scheme=Bearer",
 *     "token expired at 2026-02-25T00:00:00Z, now is 2026-02-25T01:00:00Z"
 * )
 * }</pre>
 *
 * @param mode     the failure mode that was triggered; never {@code null}
 * @param context  human-readable identification of the subject being analysed
 *                 (e.g. {@code "principal=alice, scheme=Bearer"}); never {@code null}
 * @param evidence human-readable explanation of why the violation was raised
 *                 (e.g. {@code "token expired at 2026-02-25T00:00:00Z"}); never {@code null}
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public record UserFmeaViolation(
        UserFailureModeType mode,
        String context,
        String evidence) {

    /**
     * Compact canonical constructor — validates that no field is {@code null}.
     */
    public UserFmeaViolation {
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
     * Format: {@code [FM_U1_CREDENTIAL_EXPIRY|RPN=96] principal=alice, scheme=Bearer — token expired…}
     *
     * @return formatted summary string
     */
    @Override
    public String toString() {
        return "[" + mode.name() + "|RPN=" + rpn() + "] "
             + context + " — " + evidence;
    }
}
