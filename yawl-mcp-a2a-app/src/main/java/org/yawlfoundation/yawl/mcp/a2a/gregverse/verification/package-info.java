/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * Package for occupational therapist credential verification in the GregVerse marketplace.
 *
 * <p>This package provides a comprehensive system for verifying OT credentials,
 * ensuring that all occupational therapists offering services in the GregVerse
 * marketplace have valid, up-to-date credentials from trusted authorities.</p>
 *
 * <h2>Components</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.mcp.a2a.gregverse.verification.OTCertificationVerifier} -
 *       Main service for credential verification</li>
 *   <li>{@link org.yawlfoundation.yawl.mcp.a2a.gregverse.verification.CertificationType} -
 *       Enum defining supported certification types</li>
 *   <li>{@link org.yawlfoundation.yawl.mcp.a2a.gregverse.verification.VerificationStatus} -
 *       Sealed class for verification status states</li>
 *   <li>{@link org.yawlfoundation.yawl.mcp.a2a.gregverse.verification.CredentialRecord} -
 *       Immutable record representing credentials and their status</li>
 * </ul>
 *
 * <h2>Verification Workflow</h2>
 * <ol>
 *   <li>Credential submission by OT</li>
 *   <li>Expiration date validation</li>
 *   <li>Issuer trust verification</li>
 *   <li>Document format validation</li>
 *   <li>Type-specific validation</li>
 *   <li>Status update and notification</li>
 * </ol>
 *
 * <h2>Integration Points</h2>
 * <ul>
 *   <li>Called during OT registration process</li>
 *   <li>Periodic re-verification for active credentials</li>
 *   <li>Public API for consumer verification checks</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
package org.yawlfoundation.yawl.mcp.a2a.gregverse.verification;