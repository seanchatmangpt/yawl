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

package org.yawlfoundation.yawl.integration.mcp.safe;

/**
 * Non-Functional Requirement (NFR) violation detected in source code.
 *
 * Records a single SAFe Responsible AI policy violation with location
 * and description for remediation.
 *
 * @param attribute NFR attribute violated (e.g., PRIVACY, FAIRNESS, SECURITY)
 * @param file path to source file containing violation
 * @param line line number where violation was detected
 * @param description detailed violation description for developer
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record NfrViolation(
    String attribute,
    String file,
    int line,
    String description
) {}
