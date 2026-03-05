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
 * Export utilities for YAWL v6.0.0 specifications and data.
 *
 * <p>This package provides export functionality for YAWL specifications,
 * case data, and runtime state. Supports multiple output formats for
 * interoperability with external systems and backup operations.
 *
 * <h2>Export Capabilities</h2>
 * <ul>
 *   <li><b>Specification Export</b> - Export YAWL specs to XML, YAML, JSON</li>
 *   <li><b>Case Data Export</b> - Export completed case data for archival</li>
 *   <li><b>State Export</b> - Capture engine state for migration/backup</li>
 *   <li><b>Report Export</b> - Generate compliance and audit reports</li>
 * </ul>
 *
 * <h2>Supported Formats</h2>
 * <ul>
 *   <li><b>YAWL XML</b> - Native YAWL specification format</li>
 *   <li><b>BPMN 2.0</b> - Industry-standard BPMN export (partial support)</li>
 *   <li><b>JSON/YAML</b> - Modern interchange formats</li>
 *   <li><b>CSV</b> - Tabular data export for analysis</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
package org.yawlfoundation.yawl.tooling.export;
