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
 * Report generation and delivery for YAWL.
 *
 * <p>This package provides servlet-based report generation and delivery
 * functionality for YAWL workflow systems.</p>
 *
 * <p>Key components:</p>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.reporter.Reporter} - HTTP servlet for report requests</li>
 *   <li>{@link org.yawlfoundation.yawl.reporter.Report} - Report data model</li>
 *   <li>{@link org.yawlfoundation.yawl.reporter.Sender} - Report delivery via configured channels</li>
 * </ul>
 */
package org.yawlfoundation.yawl.reporter;
