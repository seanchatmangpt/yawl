/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.mcp.a2a.gregverse.verification;

/**
 * Sealed class representing the verification status of an OT credential.
 *
 * <p>This sealed class defines the possible states of credential verification
 * and allows exhaustive pattern matching for better type safety and code clarity.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public sealed interface VerificationStatus
    permits VerificationStatus.Pending, VerificationStatus.Verified,
           VerificationStatus.Rejected, VerificationStatus.Expired {

    /**
     * Indicates that credential verification is in progress.
     */
    record Pending() implements VerificationStatus {
        public static final Pending INSTANCE = new Pending();
    }

    /**
     * Indicates that credential verification was successful.
     */
    record Verified() implements VerificationStatus {
        public static final Verified INSTANCE = new Verified();
    }

    /**
     * Indicates that credential verification failed.
     */
    record Rejected(String reason) implements VerificationStatus {
        public Rejected {
            if (reason == null || reason.trim().isEmpty()) {
                throw new IllegalArgumentException("Rejection reason cannot be null or empty");
            }
        }
    }

    /**
     * Indicates that credential verification was successful but has expired.
     */
    record Expired() implements VerificationStatus {
        public static final Expired INSTANCE = new Expired();
    }

    /**
     * Converts the verification status to a display-friendly string.
     *
     * @return the display string representation
     */
    default String toDisplayString() {
        return switch (this) {
            case Pending p -> "Pending Verification";
            case Verified v -> "Verified";
            case Rejected r -> "Rejected: " + r.reason();
            case Expired e -> "Expired";
        };
    }

    /**
     * Returns whether this status indicates successful verification (either verified or not expired).
     *
     * @return true if verification is successful, false otherwise
     */
    default boolean isSuccessful() {
        return this instanceof Verified || this instanceof Pending;
    }

    /**
     * Returns whether this status indicates a failure.
     *
     * @return true if verification failed, false otherwise
     */
    default boolean isFailed() {
        return this instanceof Rejected || this instanceof Expired;
    }
}