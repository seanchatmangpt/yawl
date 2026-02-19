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
 * Chicago TDD tests for Claude Code CLI integration.
 *
 * <p>This package contains tests for the Claude Code CLI integration components:</p>
 *
 * <h2>Components Tested</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.claude.ClaudeExecutionResult} -
 *       Immutable result record for CLI execution outcomes</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.claude.ClaudePromptSanitizer} -
 *       Security filtering for prompts (shell injection, credential leakage)</li>
 * </ul>
 *
 * <h2>Test Classes</h2>
 * <ul>
 *   <li>{@link ClaudeExecutionResultTest} - Result record tests</li>
 *   <li>{@link ClaudePromptSanitizerTest} - Security sanitization tests</li>
 * </ul>
 *
 * <h2>Running Tests</h2>
 * <pre>
 * # Run all Claude integration tests
 * java -cp classes:lib/* junit.textui.TestRunner \
 *   org.yawlfoundation.yawl.integration.claude.ClaudeTestSuite
 *
 * # Run specific test class
 * java -cp classes:lib/* junit.textui.TestRunner \
 *   org.yawlfoundation.yawl.integration.claude.ClaudePromptSanitizerTest
 *
 * # Run with Maven
 * mvn test -Dtest=org.yawlfoundation.yawl.integration.claude.*
 * </pre>
 *
 * <h2>Test Dependencies</h2>
 * <ul>
 *   <li>JUnit 4 (junit.framework.TestCase)</li>
 *   <li>Java 25+ (records)</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
package org.yawlfoundation.yawl.integration.claude;
