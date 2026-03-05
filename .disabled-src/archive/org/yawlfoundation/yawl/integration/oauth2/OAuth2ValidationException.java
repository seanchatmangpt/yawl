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

package org.yawlfoundation.yawl.integration.oauth2;

/**
 * Thrown when OAuth2/OIDC token validation fails.
 *
 * <p>Each failure carries a {@link Code} enum value so callers can map
 * validation failures to appropriate HTTP response codes without string parsing.
 *
 * <h2>HTTP mapping</h2>
 * <pre>
 * MISSING_TOKEN, MALFORMED_TOKEN, INVALID_SIGNATURE  -> 401 Unauthorized
 * TOKEN_EXPIRED, TOKEN_NOT_YET_VALID                 -> 401 Unauthorized
 * ISSUER_MISMATCH, AUDIENCE_MISMATCH                 -> 403 Forbidden
 * MISSING_CLAIM, UNSUPPORTED_ALGORITHM               -> 400 Bad Request
 * KEY_NOT_FOUND                                      -> 503 Service Unavailable (JWKS down)
 * </pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class OAuth2ValidationException extends Exception {

    /**
     * Specific validation failure codes for programmatic error handling.
     */
    public enum Code {
        MISSING_TOKEN,
        MALFORMED_TOKEN,
        INVALID_SIGNATURE,
        TOKEN_EXPIRED,
        TOKEN_NOT_YET_VALID,
        ISSUER_MISMATCH,
        AUDIENCE_MISMATCH,
        MISSING_CLAIM,
        UNSUPPORTED_ALGORITHM,
        KEY_NOT_FOUND
    }

    private final Code code;

    /**
     * Construct with message and failure code.
     *
     * @param message human-readable description suitable for server logs (not client responses)
     * @param code    machine-readable failure code
     */
    public OAuth2ValidationException(String message, Code code) {
        super(message);
        if (code == null) {
            throw new IllegalArgumentException("code must not be null");
        }
        this.code = code;
    }

    /**
     * Returns the specific validation failure code.
     *
     * @return failure code
     */
    public Code getCode() {
        return code;
    }

    /**
     * Whether this failure should be reported as a 401 Unauthorized response.
     *
     * @return true if 401, false if another status code applies
     */
    public boolean isUnauthorized() {
        return code == Code.MISSING_TOKEN
            || code == Code.MALFORMED_TOKEN
            || code == Code.INVALID_SIGNATURE
            || code == Code.TOKEN_EXPIRED
            || code == Code.TOKEN_NOT_YET_VALID;
    }

    /**
     * Whether this failure should be reported as a 403 Forbidden response.
     *
     * @return true if 403
     */
    public boolean isForbidden() {
        return code == Code.ISSUER_MISMATCH || code == Code.AUDIENCE_MISMATCH;
    }

    /**
     * Returns the recommended HTTP status code for this failure.
     *
     * @return HTTP status code integer
     */
    public int httpStatus() {
        return switch (code) {
            case MISSING_TOKEN, MALFORMED_TOKEN, INVALID_SIGNATURE,
                 TOKEN_EXPIRED, TOKEN_NOT_YET_VALID              -> 401;
            case ISSUER_MISMATCH, AUDIENCE_MISMATCH              -> 403;
            case MISSING_CLAIM, UNSUPPORTED_ALGORITHM            -> 400;
            case KEY_NOT_FOUND                                   -> 503;
        };
    }
}
