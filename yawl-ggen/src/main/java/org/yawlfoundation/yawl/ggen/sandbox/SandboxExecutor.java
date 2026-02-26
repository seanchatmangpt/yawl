/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * This file is part of YAWL. YAWL is free software: you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License.
 */
package org.yawlfoundation.yawl.ggen.sandbox;

import java.io.IOException;

/**
 * Executes code in a sandboxed environment.
 * Implementations may use Docker, containers, or other isolation mechanisms
 * to safely execute untrusted code with resource constraints.
 */
public interface SandboxExecutor {
    /**
     * Executes code in a sandbox with the given configuration.
     *
     * @param code   the code to execute (e.g., a Node.js script, Python script, etc.)
     * @param config sandbox configuration including image, timeout, and workspace
     * @return the execution result containing stdout, stderr, exit code, and duration
     * @throws IOException if the sandbox cannot be created or the process fails at the OS level
     * @throws IllegalArgumentException if code or config is null or invalid
     */
    SandboxResult execute(String code, SandboxConfig config) throws IOException;
}
