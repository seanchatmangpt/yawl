/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.mining.generators;

/**
 * Thrown when a {@link PetriNet} cannot be represented as a valid YAWL specification.
 *
 * <p>Common causes:
 * <ul>
 *   <li>No input condition (no place with no incoming arcs and positive initial marking)</li>
 *   <li>No output condition (no place with no outgoing arcs)</li>
 *   <li>Net contains isolated elements (transitions or places unreachable from start)</li>
 * </ul>
 */
public class YawlExportException extends RuntimeException {

    /**
     * Constructs an exception with a description of the structural problem.
     *
     * @param message description of why export failed
     */
    public YawlExportException(String message) {
        super(message);
    }

    /**
     * Constructs an exception with a description and underlying cause.
     *
     * @param message description of why export failed
     * @param cause   the underlying exception
     */
    public YawlExportException(String message, Throwable cause) {
        super(message, cause);
    }
}
