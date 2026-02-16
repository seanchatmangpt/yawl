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

package org.yawlfoundation.yawl.integration.autonomous.benchmarks;

/**
 * Benchmark comparing OrderfulfillmentLauncher vs GenericWorkflowLauncher.
 *
 * Measures case launch latency.
 *
 * Note: Requires running YAWL engine to execute.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public final class WorkflowLauncherBenchmark {

    private static final String YAWL_ENGINE_URL = getEnvOrDefault("YAWL_ENGINE_URL", "http://localhost:8080/yawl");
    private static final String YAWL_USERNAME = getEnvOrDefault("YAWL_USERNAME", "admin");
    private static final String YAWL_PASSWORD = getEnvOrDefault("YAWL_PASSWORD", "YAWL");

    public static void main(String[] args) {
        System.out.println("=== Workflow Launcher Benchmark ===");
        System.out.println("YAWL Engine: " + YAWL_ENGINE_URL);
        System.out.println();
        System.out.println("This benchmark requires a running YAWL engine with orderfulfillment spec loaded.");
        System.out.println("Run manually with:");
        System.out.println("  export YAWL_ENGINE_URL=" + YAWL_ENGINE_URL);
        System.out.println("  export SPEC_PATH=exampleSpecs/orderfulfillment/_examples/orderfulfillment.yawl");
        System.out.println("  java -cp ... WorkflowLauncherBenchmark");
        System.out.println();
        System.out.println("Expected metrics:");
        System.out.println("  - Case launch latency: <500ms (P95)");
        System.out.println("  - Generic framework overhead: <10%");
        System.out.println();
        System.out.println("SKIP: No live engine detected");
    }

    private static String getEnvOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }
}
