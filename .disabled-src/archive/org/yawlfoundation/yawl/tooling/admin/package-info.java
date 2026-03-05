/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL. YAWL is free software: you can
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
 * Administrative tools and utilities for YAWL v6.0.0.
 *
 * <p>This package provides administrative functionality for YAWL engine
 * management including user administration, logging configuration,
 * system maintenance, and operational tasks.
 *
 * <h2>Administrative Capabilities</h2>
 * <ul>
 *   <li><b>User Management</b> - Create, update, delete users and roles</li>
 *   <li><b>Logging Configuration</b> - Dynamic log level adjustment</li>
 *   <li><b>System Maintenance</b> - Cleanup, archival, optimization</li>
 *   <li><b>Configuration Management</b> - Export/import engine settings</li>
 *   <li><b>Audit Operations</b> - Query and export audit logs</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 * <pre>{@code
 * // Adjust logging level at runtime
 * AdminService.setLogLevel("org.yawlfoundation.yawl.engine", Level.DEBUG);
 *
 * // Run maintenance tasks
 * MaintenanceResult result = AdminService.runMaintenance(
 *     MaintenanceOptions.builder()
 *         .archiveCompletedCases(true)
 *         .cleanupOrphanedData(true)
 *         .optimizeDatabase(true)
 *         .build()
 * );
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
package org.yawlfoundation.yawl.tooling.admin;
