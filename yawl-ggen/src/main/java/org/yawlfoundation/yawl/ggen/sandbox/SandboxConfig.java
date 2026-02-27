/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * This file is part of YAWL. YAWL is free software: you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License.
 */
package org.yawlfoundation.yawl.ggen.sandbox;

import java.nio.file.Path;

/**
 * Configuration for a Docker sandbox execution environment.
 * Immutable record containing image name, network proxy configuration,
 * timeout settings, and workspace directory.
 */
public record SandboxConfig(
    String imageName,
    String networkProxy,
    int timeoutSeconds,
    Path workspaceDir
) {
    /**
     * Compact constructor that validates all configuration parameters.
     *
     * @throws IllegalArgumentException if imageName or networkProxy are blank, or timeoutSeconds ≤ 0
     */
    public SandboxConfig {
        if (imageName == null || imageName.isBlank())
            throw new IllegalArgumentException("imageName must not be blank");
        if (networkProxy == null || networkProxy.isBlank())
            throw new IllegalArgumentException("networkProxy must not be blank");
        if (timeoutSeconds <= 0)
            throw new IllegalArgumentException("timeoutSeconds must be positive");
        // workspaceDir may be null (a temp dir will be created)
    }

    /**
     * Returns default configuration using Node.js 22 slim image
     * with standard network proxy settings.
     *
     * @return a SandboxConfig with default values
     */
    public static SandboxConfig defaults() {
        return new SandboxConfig("node:22-slim", "host.docker.internal:3128", 30, null);
    }
}
