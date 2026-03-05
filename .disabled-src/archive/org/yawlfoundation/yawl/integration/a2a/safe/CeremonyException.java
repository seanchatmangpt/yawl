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
 */

package org.yawlfoundation.yawl.integration.a2a.safe;

/**
 * Checked exception for SAFe ceremony execution errors.
 *
 * <p>Thrown when ceremony execution fails due to invalid inputs,
 * missing participants, or other ceremony-specific errors.
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public class CeremonyException extends Exception {

    /**
     * Create a ceremony exception with a message.
     *
     * @param message error message
     */
    public CeremonyException(String message) {
        super(message);
    }

    /**
     * Create a ceremony exception with a message and cause.
     *
     * @param message error message
     * @param cause   root cause exception
     */
    public CeremonyException(String message, Throwable cause) {
        super(message, cause);
    }
}
