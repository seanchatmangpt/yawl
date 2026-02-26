/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * This file is part of YAWL. YAWL is free software: you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License.
 */
package org.yawlfoundation.yawl.ggen.sandbox;

/**
 * Immutable result from a Docker sandbox execution.
 * Contains stdout, stderr, exit code, and duration of execution.
 */
public record SandboxResult(
    String stdout,
    String stderr,
    int exitCode,
    long durationMs
) {
    /**
     * Compact constructor that normalizes null strings to empty strings.
     */
    public SandboxResult {
        if (stdout == null) stdout = "";
        if (stderr == null) stderr = "";
    }

    /**
     * Returns true if the sandbox process exited with code 0.
     *
     * @return true if exit code is 0, false otherwise
     */
    public boolean isSuccess() {
        return exitCode == 0;
    }
}
