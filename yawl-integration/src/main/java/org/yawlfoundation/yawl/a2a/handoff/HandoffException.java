package org.yawlfoundation.yawl.integration.a2a.handoff;

import java.io.Serial;

/**
 * Thrown when a handoff protocol operation fails.
 *
 * <p>This exception indicates errors during handoff token generation,
 * verification, or message processing. The exception includes details about
 * the specific failure reason to aid in debugging handoff failures.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class HandoffException extends Exception {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a handoff exception with a descriptive message.
     *
     * @param reason description of what went wrong
     */
    public HandoffException(String reason) {
        super(reason);
    }

    /**
     * Constructs a handoff exception with a cause.
     *
     * @param reason description of what went wrong
     * @param cause underlying technical cause (for logging)
     */
    public HandoffException(String reason, Throwable cause) {
        super(reason, cause);
    }
}