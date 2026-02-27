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

package org.yawlfoundation.yawl.integration.autonomous;

/**
 * Checked exception for failures communicating with the yawl-native Rust service.
 *
 * <p>Wraps HTTP-level errors (non-2xx status), connection failures, and
 * serialisation errors. Callers that want to tolerate unavailability should
 * catch this and degrade gracefully.</p>
 *
 * @since YAWL 6.0
 */
public class YawlNativeException extends Exception {

    public YawlNativeException(String message) {
        super(message);
    }

    public YawlNativeException(String message, Throwable cause) {
        super(message, cause);
    }
}
