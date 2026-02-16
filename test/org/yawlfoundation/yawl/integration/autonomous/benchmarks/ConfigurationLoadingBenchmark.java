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

import org.yawlfoundation.yawl.integration.autonomous.AgentCapability;
import org.yawlfoundation.yawl.integration.autonomous.AgentConfiguration;
import org.yawlfoundation.yawl.integration.autonomous.reasoners.ZaiDecisionReasoner;
import org.yawlfoundation.yawl.integration.autonomous.reasoners.ZaiEligibilityReasoner;
import org.yawlfoundation.yawl.integration.autonomous.strategies.PollingDiscoveryStrategy;
import org.yawlfoundation.yawl.integration.zai.ZaiService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Benchmark comparing hardcoded vs YAML config-driven agent instantiation.
 *
 * Measures configuration loading time and startup overhead.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public final class ConfigurationLoadingBenchmark {

    private static final String ZAI_URL = "http://localhost:11434";
    private static final String ZAI_MODEL = "llama2";
    private static final String ENGINE_URL = "http://localhost:8080/yawl";

    public static void main(String[] args) throws Exception {
        System.out.println("=== Configuration Loading Benchmark ===");

        String yamlConfig = createSampleYamlConfig();
        Path tempConfig = Files.createTempFile("agent-config-", ".yaml");
        Files.writeString(tempConfig, yamlConfig);

        System.out.println("\n=== Hardcoded Agent Instantiation (Baseline) ===");
        BenchmarkHarness hardcodedHarness = new BenchmarkHarness(
            "Hardcoded-Agent-Instantiation",
            10,
            100
        );

        BenchmarkHarness.BenchmarkResult hardcodedResult = hardcodedHarness.run(
            new BenchmarkHarness.BenchmarkOperation() {
                @Override
                public void run() {
                    AgentCapability capability = new AgentCapability(
                        "order-fulfillment",
                        "Purchase order approval and inventory management"
                    );

                    ZaiService zaiService = new ZaiService(ZAI_URL, ZAI_MODEL);

                    AgentConfiguration config = AgentConfiguration.builder()
                        .engineUrl(ENGINE_URL)
                        .username("admin")
                        .password("YAWL")
                        .capability(capability)
                        .discoveryStrategy(new PollingDiscoveryStrategy())
                        .eligibilityReasoner(new ZaiEligibilityReasoner(capability, zaiService))
                        .decisionReasoner(new ZaiDecisionReasoner(zaiService))
                        .port(9001)
                        .pollIntervalMs(5000)
                        .version("1.0.0")
                        .build();
                }
            }
        );

        System.out.println("\n=== YAML Config-Driven Instantiation ===");
        BenchmarkHarness yamlHarness = new BenchmarkHarness(
            "YAML-Config-Driven-Instantiation",
            10,
            100
        );

        BenchmarkHarness.BenchmarkResult yamlResult = yamlHarness.run(
            new BenchmarkHarness.BenchmarkOperation() {
                @Override
                public void run() throws IOException {
                    String yaml = Files.readString(tempConfig);
                    parseYamlAndCreateAgent(yaml);
                }
            }
        );

        System.out.println("\n=== Performance Comparison ===");
        double overhead = yamlResult.overheadPercentage(hardcodedResult);
        System.out.printf("Hardcoded P50: %.3f ms%n", hardcodedResult.getP50Ms());
        System.out.printf("YAML P50:      %.3f ms%n", yamlResult.getP50Ms());
        System.out.printf("Overhead:      %.2f%%%n", overhead);

        System.out.printf("Hardcoded P95: %.3f ms%n", hardcodedResult.getP95Ms());
        System.out.printf("YAML P95:      %.3f ms%n", yamlResult.getP95Ms());

        Files.delete(tempConfig);

        System.out.println("\n=== Verdict ===");
        if (Math.abs(overhead) < 50.0) {
            System.out.println("PASS: YAML configuration overhead is acceptable (<50%)");
            System.out.println("Note: Configuration loading is one-time startup cost");
        } else {
            System.out.println("WARNING: YAML configuration overhead is high");
        }
    }

    private static String createSampleYamlConfig() {
        return """
            agent:
              domain: order-fulfillment
              description: Purchase order approval and inventory management
              version: 1.0.0
              port: 9001
            
            yawl:
              engineUrl: http://localhost:8080/yawl
              username: admin
              password: YAWL
            
            zai:
              url: http://localhost:11434
              model: llama2
            
            discovery:
              strategy: polling
              pollIntervalMs: 5000
            
            reasoning:
              eligibility:
                type: zai
              decision:
                type: zai
            """;
    }

    private static void parseYamlAndCreateAgent(String yaml) {
        String[] lines = yaml.split("\n");
        String domain = null;
        String description = null;
        String version = null;
        int port = 9001;

        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("domain:")) {
                domain = line.substring("domain:".length()).trim();
            } else if (line.startsWith("description:")) {
                description = line.substring("description:".length()).trim();
            } else if (line.startsWith("version:")) {
                version = line.substring("version:".length()).trim();
            } else if (line.startsWith("port:")) {
                port = Integer.parseInt(line.substring("port:".length()).trim());
            }
        }

        AgentCapability capability = new AgentCapability(domain, description);
        ZaiService zaiService = new ZaiService(ZAI_URL, ZAI_MODEL);

        AgentConfiguration config = AgentConfiguration.builder()
            .engineUrl(ENGINE_URL)
            .username("admin")
            .password("YAWL")
            .capability(capability)
            .discoveryStrategy(new PollingDiscoveryStrategy())
            .eligibilityReasoner(new ZaiEligibilityReasoner(capability, zaiService))
            .decisionReasoner(new ZaiDecisionReasoner(zaiService))
            .port(port)
            .pollIntervalMs(5000)
            .version(version != null ? version : "1.0.0")
            .build();
    }
}
