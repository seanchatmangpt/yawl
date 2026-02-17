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
 * Output generation strategies for autonomous agents.
 *
 * <p>This package provides implementations of the OutputGenerator interface
 * for producing work item completion data. Generators transform input data
 * and context into valid XML output for YAWL task completion.</p>
 *
 * <p>Key components:</p>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.generators.TemplateOutputGenerator} -
 *       Template-based output with variable substitution</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.generators.XmlOutputGenerator} -
 *       XML-based output generation</li>
 * </ul>
 *
 * <p>Template syntax supports: ${variableName}, ${taskName}, ${caseId}, ${input.elementName}</p>
 */
package org.yawlfoundation.yawl.integration.autonomous.generators;
