/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it terms of the GNU Lesser
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
package org.yawlfoundation.yawl.nativebridge.qlever;

/**
 * Exception hierarchy for QLever engine errors.
 * All exceptions carry the original QLeverStatus for detailed error information.
 */
public sealed interface QLeverException extends Exception
    permits QLeverParseException, QLeverSemanticException, QLeverRuntimeException {

    /**
     * Gets the original QLeverStatus that caused this exception.
     *
     * @return the status that triggered the exception
     */
    QLeverStatus getQLeverStatus();
}

/**
 * Exception thrown when a SPARQL query cannot be parsed.
 */
public final class QLeverParseException extends RuntimeException implements QLeverException {
    private final QLeverStatus status;

    public QLeverParseException(String message, QLeverStatus status) {
        super(message);
        this.status = status;
    }

    public QLeverParseException(String message, QLeverStatus status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    @Override
    public QLeverStatus getQLeverStatus() {
        return status;
    }
}

/**
 * Exception thrown when a SPARQL query is syntactically valid but semantically incorrect.
 */
public final class QLeverSemanticException extends RuntimeException implements QLeverException {
    private final QLeverStatus status;

    public QLeverSemanticException(String message, QLeverStatus status) {
        super(message);
        this.status = status;
    }

    public QLeverSemanticException(String message, QLeverStatus status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    @Override
    public QLeverStatus getQLeverStatus() {
        return status;
    }
}

/**
 * Exception thrown when a SPARQL query execution fails at runtime.
 */
public final class QLeverRuntimeException extends RuntimeException implements QLeverException {
    private final QLeverStatus status;

    public QLeverRuntimeException(String message, QLeverStatus status) {
        super(message);
        this.status = status;
    }

    public QLeverRuntimeException(String message, QLeverStatus status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    @Override
    public QLeverStatus getQLeverStatus() {
        return status;
    }
}