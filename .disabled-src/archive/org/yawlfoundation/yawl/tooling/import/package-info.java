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

/**
 * Import utilities for YAWL v6.0.0 specifications and data.
 *
 * <p>This package provides import functionality for YAWL specifications,
 * case data, and runtime state. Supports validation during import to
 * ensure data integrity and specification correctness.
 *
 * <h2>Import Capabilities</h2>
 * <ul>
 *   <li><b>Specification Import</b> - Load YAWL specs from XML, YAML, JSON</li>
 *   <li><b>Case Data Import</b> - Restore case data from archives</li>
 *   <li><b>State Import</b> - Restore engine state from backups</li>
 *   <li><b>Migration Import</b> - Import from older YAWL versions</li>
 * </ul>
 *
 * <h2>Validation</h2>
 * <p>All imports undergo validation against YAWL schema v4.0:
 * <ul>
 *   <li>Schema validation (XSD)</li>
 *   <li>Semantic validation (control flow, data flow)</li>
 *   <li>Reference integrity checks</li>
 *   <li>Warning reporting for deprecated features</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
package org.yawlfoundation.yawl.tooling.import;
