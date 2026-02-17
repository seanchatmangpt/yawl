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

package org.yawlfoundation.yawl.security;

/**
 * Enumeration of all credential identifiers managed by {@link CredentialManager}.
 *
 * <p>Every credential used anywhere in the YAWL platform must have a corresponding
 * constant here. Adding a new credential requires:
 * <ol>
 *   <li>Adding a constant to this enum with documentation.</li>
 *   <li>Adding the vault path mapping in the deployment runbook (see {@code SECURITY.md}).</li>
 *   <li>Registering a rotation schedule in the credential rotation procedures.</li>
 * </ol>
 *
 * @since YAWL 5.3
 */
public enum CredentialKey {

    /**
     * Password for the built-in YAWL admin account.
     * Vault path: {@code secret/yawl/admin/password}
     * Rotation: 90 days
     */
    YAWL_ADMIN_PASSWORD,

    /**
     * Password used by YAWL services to authenticate with the engine via Interface B.
     * Vault path: {@code secret/yawl/engine/service-password}
     * Rotation: 90 days
     */
    YAWL_ENGINE_SERVICE_PASSWORD,

    /**
     * API key for the Z.AI (ZAI) integration.
     * Vault path: {@code secret/yawl/integration/zai-api-key}
     * Rotation: on demand (external provider)
     */
    ZAI_API_KEY,

    /**
     * API key for the Zhipu AI integration.
     * Vault path: {@code secret/yawl/integration/zhipu-api-key}
     * Rotation: on demand (external provider)
     */
    ZHIPU_API_KEY,

    /**
     * Password for the proclet service editor (MainScreen) to authenticate with YAWL.
     * Vault path: {@code secret/yawl/procletservice/password}
     * Rotation: 90 days
     */
    PROCLET_SERVICE_PASSWORD

}
