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

package org.yawlfoundation.yawl.dspy.module;

import org.yawlfoundation.yawl.dspy.signature.Signature;

/**
 * Exception thrown when a DSPy module fails.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class ModuleException extends RuntimeException {

    private final String moduleName;
    private final Signature signature;
    private final ErrorKind kind;

    public ModuleException(String moduleName, Signature signature, ErrorKind kind, String message) {
        super("[%s] %s: %s".formatted(moduleName, kind, message));
        this.moduleName = moduleName;
        this.signature = signature;
        this.kind = kind;
    }

    public ModuleException(String moduleName, Signature signature, ErrorKind kind, String message, Throwable cause) {
        super("[%s] %s: %s".formatted(moduleName, kind, message), cause);
        this.moduleName = moduleName;
        this.signature = signature;
        this.kind = kind;
    }

    public String moduleName() { return moduleName; }
    public Signature signature() { return signature; }
    public ErrorKind kind() { return kind; }

    public enum ErrorKind {
        INVALID_INPUT,
        LLM_ERROR,
        PARSE_ERROR,
        MISSING_OUTPUT,
        VALIDATION_ERROR,
        TIMEOUT
    }
}
