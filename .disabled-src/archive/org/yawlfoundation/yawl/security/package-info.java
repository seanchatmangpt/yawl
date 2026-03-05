/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
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

/**
 * Credential management infrastructure for YAWL.
 *
 * <p>This package defines the contract ({@link org.yawlfoundation.yawl.security.CredentialManager})
 * and supporting types for all credential access within the YAWL platform. No
 * hardcoded credentials, environment-variable fallbacks to known defaults, or
 * plaintext config files are permitted in production code - all credential access
 * must be routed through a {@code CredentialManager} implementation backed by a
 * secrets vault.
 *
 * <p>See {@code SECURITY.md} at the project root for:
 * <ul>
 *   <li>Known credential defaults to be removed</li>
 *   <li>Required vault integration steps</li>
 *   <li>Credential rotation procedures</li>
 * </ul>
 */
package org.yawlfoundation.yawl.security;
