/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
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
 * Tests for A2A Skills in the YAWL self-upgrading codebase.
 *
 * <p>This package contains Chicago School TDD tests for all A2A skills
 * that enable autonomous code improvement through the A2A protocol.
 *
 * <p><b>Test Classes:</b>
 * <ul>
 *   <li>{@link IntrospectCodebaseSkillTest} - Observatory queries and context compression</li>
 *   <li>{@link GenerateCodeSkillTest} - Z.AI code generation with YAWL patterns</li>
 *   <li>{@link ExecuteBuildSkillTest} - Maven build execution (incremental/full)</li>
 *   <li>{@link RunTestsSkillTest} - JUnit test execution with coverage verification</li>
 *   <li>{@link CommitChangesSkillTest} - Git operations with safety guards</li>
 *   <li>{@link SelfUpgradeSkillTest} - Full upgrade cycle orchestration</li>
 * </ul>
 *
 * <p><b>Testing Principles:</b>
 * <ul>
 *   <li>Test behavior, not implementation</li>
 *   <li>Use real file system operations where feasible</li>
 *   <li>Use real processes for build/test execution</li>
 *   <li>Meaningful test names describing behavior</li>
 *   <li>80%+ coverage target</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 5.2
 * @see org.yawlfoundation.yawl.integration.a2a.skills.A2ASkill
 * @see org.yawlfoundation.yawl.integration.a2a.skills.SkillRequest
 * @see org.yawlfoundation.yawl.integration.a2a.skills.SkillResult
 */
package org.yawlfoundation.yawl.integration.a2a.skills;
