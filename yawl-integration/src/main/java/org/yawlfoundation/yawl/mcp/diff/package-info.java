/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

/**
 * Workflow Behavioral Diff Engine for YAWL.
 *
 * <p>This package provides semantic workflow diff capabilities that go beyond
 * simple XML comparison. It analyzes two workflow specifications and identifies:
 *
 * <ul>
 *   <li><strong>Structural changes:</strong> Tasks added, removed, or modified</li>
 *   <li><strong>Complexity metrics:</strong> Task counts, split/join counts, decision points</li>
 *   <li><strong>Behavioral fingerprints:</strong> Hash-based signatures for comparing versions</li>
 *   <li><strong>Regression risk:</strong> Assessment of potential execution impacts</li>
 *   <li><strong>Change magnitude:</strong> Classification from NONE to MAJOR</li>
 * </ul>
 *
 * <p>The primary entry point is {@link WorkflowBehavioralDiffer}, which provides:
 * <ul>
 *   <li>{@code diff()} — performs semantic comparison of two specifications</li>
 *   <li>{@code generateReport()} — creates human-readable ASCII diff reports</li>
 * </ul>
 *
 * <p>This capability is exposed via the MCP (Model Context Protocol) tool
 * {@code yawl_diff_workflows}, allowing autonomous agents and AI assistants
 * to analyze workflow changes as part of version management and change
 * impact analysis workflows.
 *
 * <p><strong>Example usage:</strong>
 * <pre>
 * SpecificationData v1 = client.getSpecification("OrderProcessing", "1.0");
 * SpecificationData v2 = client.getSpecification("OrderProcessing", "2.0");
 *
 * WorkflowBehavioralDiffer.DiffResult diff = WorkflowBehavioralDiffer.diff(v1, v2);
 * String report = WorkflowBehavioralDiffer.generateReport(diff);
 * System.out.println(report);
 * </pre>
 *
 * @since 6.0.0
 * @author YAWL Foundation
 */
package org.yawlfoundation.yawl.integration.mcp.diff;
