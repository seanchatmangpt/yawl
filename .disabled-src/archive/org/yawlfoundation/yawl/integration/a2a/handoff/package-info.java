/**
 * Handoff protocol implementation for secure agent-to-agent coordination.
 *
 * <p>This package provides a standardized protocol for securely transferring
 * workflow cases between YAWL agents using JWT-based authentication. The protocol
 * supports graceful handoffs with session state preservation and message
 * integrity verification.
 *
 * <p>Usage:
 * <pre>{@code
 * // Create a handoff protocol instance
 * HandoffProtocol protocol = new HandoffProtocol(jwtProvider);
 *
 * // Generate a handoff token
 * HandoffToken token = protocol.generateHandoffToken(
 *     "source-agent",
 *     "target-agent",
 *     "case-123",
 *     Duration.ofMinutes(5)
 * );
 *
 * // Verify a handoff message
 * HandoffMessage message = protocol.verifyHandoffMessage(token, signature);
 * }</pre>
 *
 * @since YAWL 5.2
 * @see org.yawlfoundation.yawl.integration.a2a.auth.JwtAuthenticationProvider
 */
package org.yawlfoundation.yawl.integration.a2a.handoff;