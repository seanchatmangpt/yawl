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
 * Immutable record representing a single detected FMEA violation in a marketplace
 * end-to-end round-trip.
 *
 * <p>A violation is produced by {@link MarketplaceFmeaAnalyzer} when a marketplace
 * event envelope, payment event, vendor status, or engine session satisfies the
 * conditions for one of the {@link MarketplaceFailureModeType} failure modes.
 *
 * <p>Example:
 * <pre>{@code
 * // FM_E2 violation from a duplicate event
 * new MarketplaceFmeaViolation(
 *     MarketplaceFailureModeType.FM_E2_DUPLICATE_EVENT,
 *     "eventId=EVT-42, source=vendor-agent-1, type=OrderCreatedEvent",
 *     "idempotencyKey='order-42-create' already present in processed-keys cache"
 * )
 * }</pre>
 *
 * @param mode     the failure mode that was triggered; never {@code null}
 * @param context  human-readable identification of the subject being analysed; never {@code null}
 * @param evidence human-readable explanation of why the violation was raised; never {@code null}
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public record MarketplaceFmeaViolation(
        MarketplaceFailureModeType mode,
        String context,
        String evidence) {

    /**
     * Compact canonical constructor — validates that no field is {@code null}.
     */
    public MarketplaceFmeaViolation {
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
     * Format: {@code [FM_E2_DUPLICATE_EVENT|RPN=60] eventId=EVT-42 — idempotency key already seen}
     *
     * @return formatted summary string
     */
    @Override
    public String toString() {
        return "[" + mode.name() + "|RPN=" + rpn() + "] "
             + context + " — " + evidence;
    }
}
