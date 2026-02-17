package org.yawlfoundation.yawl.integration.a2a.auth;

import java.io.Serial;

/**
 * Thrown by an {@link A2AAuthenticationProvider} when a request cannot be
 * authenticated.
 *
 * <p>The server translates this exception directly into an HTTP 401 response.
 * The {@link #getMessage()} value is included in the JSON response body under
 * the {@code "error"} key and in the {@code WWW-Authenticate} challenge
 * header. It MUST NOT disclose internal secrets, key material, or stack
 * traces. Include only information that a legitimate client needs to correct
 * its credentials.
 *
 * <p>The {@link #getSupportedSchemes()} value is used to populate the
 * {@code WWW-Authenticate} header returned to the caller so that it knows
 * which schemes the server accepts.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class A2AAuthenticationException extends Exception {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String supportedSchemes;

    /**
     * Construct an authentication exception with an end-user-safe reason and
     * the supported authentication schemes.
     *
     * @param reason          safe description of why authentication failed
     *                        (no secrets)
     * @param supportedSchemes comma-separated list of accepted scheme names,
     *                        used in the {@code WWW-Authenticate} response
     *                        header (e.g. {@code "Bearer, ApiKey"})
     */
    public A2AAuthenticationException(String reason, String supportedSchemes) {
        super(reason);
        this.supportedSchemes = supportedSchemes != null ? supportedSchemes : "Bearer";
    }

    /**
     * Construct an authentication exception wrapping an underlying cause.
     * The cause is NOT propagated to the HTTP response.
     *
     * @param reason          safe description of why authentication failed
     * @param supportedSchemes accepted scheme names
     * @param cause           underlying technical cause (logged server-side
     *                        only, never sent to client)
     */
    public A2AAuthenticationException(String reason,
                                      String supportedSchemes,
                                      Throwable cause) {
        super(reason, cause);
        this.supportedSchemes = supportedSchemes != null ? supportedSchemes : "Bearer";
    }

    /**
     * Comma-separated list of authentication schemes this server accepts.
     * Included in the {@code WWW-Authenticate} response header.
     *
     * @return scheme names string; never null
     */
    public String getSupportedSchemes() {
        return supportedSchemes;
    }
}
