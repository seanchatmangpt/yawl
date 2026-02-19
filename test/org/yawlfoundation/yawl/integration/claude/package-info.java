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
 *   <li>{@link org.yawlfoundation.yawl.integration.claude.ClaudeCodeExecutor} -
 *       Executes Claude Code CLI commands with pipe-based communication</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.claude.ClaudeExecutionResult} -
 *       Immutable result record for CLI execution outcomes</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.claude.ClaudePromptSanitizer} -
 *       Security filtering for prompts (shell injection, credential leakage)</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.claude.ClaudeSessionManager} -
 *       Multi-turn conversation session management</li>
 * </ul>
 *
 * <h2>Test Classes</h2>
 * <ul>
 *   <li>{@link ClaudeCodeExecutorTest} - Executor functionality tests</li>
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
 *   <li>Java 25+ (records, structured concurrency)</li>
 * </ul>
 *
 * <h2>Conditional Tests</h2>
 * <p>Tests that require the actual Claude CLI executable are conditionally
 * skipped when the CLI is not available in the system PATH. The
 * {@code assumeClaudeAvailable()} method checks for CLI availability.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
package org.yawlfoundation.yawl.integration.claude;
