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
 * Security configuration for YAWL.
 *
 * <p>This package provides security-related configurations and utilities
 * for protecting YAWL against common vulnerabilities.</p>
 *
 * <p>Key components:</p>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.security.ObjectInputStreamConfig} -
 *       Allowlist-based ObjectInputFilter configurations for safe Java deserialization</li>
 * </ul>
 *
 * <p>Security focus areas:</p>
 * <ul>
 *   <li>Safe deserialization (CWE-502 mitigation)</li>
 *   <li>Gadget chain attack prevention</li>
 *   <li>Remote Code Execution (RCE) vulnerability mitigation</li>
 * </ul>
 */
package org.yawlfoundation.yawl.security;
