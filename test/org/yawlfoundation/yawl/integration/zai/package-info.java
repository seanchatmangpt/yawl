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
 * You should have received a copy of the GNU Lesser General
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * Unit and integration tests for Z.AI integration components.
 *
 * <p>This package contains comprehensive tests for:</p>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.zai.ZaiServiceTest} -
 *       Tests for the main Z.AI service including initialization, chat operations,
 *       data transformation, and workflow decision making</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.zai.ZaiDecisionReasonerTest} -
 *       Tests for the AI-powered decision reasoner including routing decisions,
 *       data quality assessment, and bottleneck analysis</li>
 * </ul>
 *
 * <p>Tests are organized into two categories:</p>
 * <ul>
 *   <li><strong>Unit Tests</strong> - Run without API key, test internal logic and validation</li>
 *   <li><strong>Integration Tests</strong> - Require ZAI_API_KEY environment variable,
 *       test actual API interactions</li>
 * </ul>
 *
 * <p>To run all tests:</p>
 * <pre>{@code mvn test -Dtest=org.yawlfoundation.yawl.integration.zai.*}</pre>
 *
 * <p>To run only unit tests (no API key required):</p>
 * <pre>{@code mvn test -Dtest=org.yawlfoundation.yawl.integration.zai.* -Dgroups="unit"}</pre>
 *
 * @author YAWL Foundation - ZAI Integration Team
 * @version 6.0
 */
package org.yawlfoundation.yawl.integration.zai;
