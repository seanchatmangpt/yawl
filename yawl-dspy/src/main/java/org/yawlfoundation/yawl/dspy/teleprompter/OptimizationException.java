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

package org.yawlfoundation.yawl.dspy.teleprompter;

/**
 * Exception thrown when teleprompter optimization fails.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class OptimizationException extends RuntimeException {

    private final String teleprompterName;
    private final ErrorKind kind;

    public OptimizationException(String teleprompterName, ErrorKind kind, String message) {
        super("[%s] %s: %s".formatted(teleprompterName, kind, message));
        this.teleprompterName = teleprompterName;
        this.kind = kind;
    }

    public OptimizationException(String teleprompterName, ErrorKind kind, String message, Throwable cause) {
        super("[%s] %s: %s".formatted(teleprompterName, kind, message), cause);
        this.teleprompterName = teleprompterName;
        this.kind = kind;
    }

    public String teleprompterName() { return teleprompterName; }
    public ErrorKind kind() { return kind; }

    public enum ErrorKind {
        NO_VALID_EXAMPLES,
        ALL_BOOTSTRAPS_FAILED,
        EVALUATION_ERROR,
        CONFIGURATION_ERROR,
        TIMEOUT
    }
}
