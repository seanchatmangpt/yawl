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

package org.yawlfoundation.yawl.integration.claude;

import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * Test suite for Claude Code CLI integration.
 *
 * <p>Groups all tests for the Claude integration components:</p>
 * <ul>
 *   <li>ClaudeExecutionResult - Execution result record</li>
 *   <li>ClaudePromptSanitizer - Security filtering for prompts</li>
 * </ul>
 *
 * <p>Run with:</p>
 * <pre>
 * java -cp classes:lib/* junit.textui.TestRunner \
 *   org.yawlfoundation.yawl.integration.claude.ClaudeTestSuite
 * </pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class ClaudeTestSuite {

    /**
     * Creates the test suite.
     *
     * @return the test suite containing all Claude integration tests
     */
    public static Test suite() {
        TestSuite suite = new TestSuite("Claude Code CLI Integration Tests");

        // ClaudeExecutionResult tests - pure Java record, no preview features
        suite.addTestSuite(ClaudeExecutionResultTest.class);

        // ClaudePromptSanitizer tests - no preview features
        suite.addTestSuite(ClaudePromptSanitizerTest.class);

        // NOTE: ClaudeCodeExecutor tests are disabled because the source class
        // uses StructuredTaskScope.ShutdownOnFailure which is a preview feature
        // in Java 25. Enable when the API stabilizes or when running with
        // --enable-preview at runtime.
        // suite.addTestSuite(ClaudeCodeExecutorTest.class);

        return suite;
    }

    /**
     * Main entry point for running the suite.
     *
     * @param args command line arguments (ignored)
     */
    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }
}
