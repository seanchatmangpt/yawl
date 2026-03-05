/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.worklet;

/**
 * Thrown when the {@link WorkletService} encounters an unrecoverable error
 * during RDR evaluation or worklet routing.
 *
 * <p>This is an unchecked exception. Callers that cannot handle worklet failures
 * can let it propagate; callers that need to degrade gracefully should catch it
 * and continue with normal task execution.
 */
public class WorkletServiceException extends RuntimeException {

    /**
     * Constructs a WorkletServiceException with the given message.
     *
     * @param message a description of the failure
     */
    public WorkletServiceException(String message) {
        super(message);
    }

    /**
     * Constructs a WorkletServiceException with a message and root cause.
     *
     * @param message a description of the failure
     * @param cause   the underlying exception that triggered this failure
     */
    public WorkletServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
