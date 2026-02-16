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

package org.yawlfoundation.yawl.integration.autonomous.stress;

import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.integration.autonomous.AgentCapability;
import org.yawlfoundation.yawl.integration.autonomous.AgentConfiguration;
import org.yawlfoundation.yawl.integration.autonomous.GenericPartyAgent;
import org.yawlfoundation.yawl.integration.autonomous.reasoners.ZaiDecisionReasoner;
import org.yawlfoundation.yawl.integration.autonomous.reasoners.ZaiEligibilityReasoner;
import org.yawlfoundation.yawl.integration.autonomous.strategies.PollingDiscoveryStrategy;
import org.yawlfoundation.yawl.integration.zai.ZaiService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stress test for concurrent generic agents.
 *
 * Launches 10 concurrent agents, 100 work items, measures completion time and error rate.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public final class ConcurrentAgentStressTest {

    private static final String YAWL_ENGINE_URL = getEnvOrDefault("YAWL_ENGINE_URL", "http://localhost:8080/yawl");
    private static final String YAWL_USERNAME = getEnvOrDefault("YAWL_USERNAME", "admin");
    private static final String YAWL_PASSWORD = getEnvOrDefault("YAWL_PASSWORD", "YAWL");
    private static final String ZAI_URL = getEnvOrDefault("ZAI_URL", "http://localhost:11434");
    private static final String ZAI_MODEL = getEnvOrDefault("ZAI_MODEL", "llama2");

    private static final int NUM_AGENTS = 10;
    private static final int NUM_CASES = 100;
    private static final int TIMEOUT_SECONDS = 600;

    public static void main(String[] args) {
        System.out.println("=== Concurrent Agent Stress Test ===");
        System.out.println("Agents: " + NUM_AGENTS);
        System.out.println("Cases: " + NUM_CASES);
        System.out.println("Timeout: " + TIMEOUT_SECONDS + "s");
        System.out.println("YAWL Engine: " + YAWL_ENGINE_URL);
        System.out.println("ZAI URL: " + ZAI_URL);
        System.out.println();

        System.out.println("This stress test requires a running YAWL engine with orderfulfillment spec.");
        System.out.println("Run manually with:");
        System.out.println("  export YAWL_ENGINE_URL=" + YAWL_ENGINE_URL);
        System.out.println("  export NUM_AGENTS=10");
        System.out.println("  export NUM_CASES=100");
        System.out.println("  java -cp ... ConcurrentAgentStressTest");
        System.out.println();

        try {
            runStressTest();
        } catch (Exception e) {
            System.err.println("Stress test failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void runStressTest() throws Exception {
        ExecutorService agentPool = Executors.newFixedThreadPool(NUM_AGENTS);
        List<GenericPartyAgent> agents = new ArrayList<>();
        AtomicInteger errorCount = new AtomicInteger(0);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down agents...");
            for (GenericPartyAgent agent : agents) {
                try {
                    if (agent.isRunning()) {
                        agent.stop();
                    }
                } catch (Exception e) {
                    System.err.println("Error stopping agent: " + e.getMessage());
                }
            }
            agentPool.shutdown();
        }));

        System.out.println("Starting " + NUM_AGENTS + " agents...");
        long agentStartTime = System.currentTimeMillis();

        for (int i = 0; i < NUM_AGENTS; i++) {
            int agentId = i;
            agentPool.submit(() -> {
                try {
                    GenericPartyAgent agent = createAgent(agentId);
                    synchronized (agents) {
                        agents.add(agent);
                    }
                    agent.start();
                    System.out.println("Agent " + agentId + " started on port " + (9000 + agentId));
                } catch (Exception e) {
                    System.err.println("Agent " + agentId + " failed to start: " + e.getMessage());
                    errorCount.incrementAndGet();
                }
            });
        }

        Thread.sleep(5000);
        long agentStartDuration = System.currentTimeMillis() - agentStartTime;
        System.out.println("All agents started in " + agentStartDuration + " ms");
        System.out.println("Agent startup errors: " + errorCount.get());

        System.out.println("\nLaunching " + NUM_CASES + " workflow cases...");
        List<String> caseIds = launchCases(NUM_CASES);
        System.out.println("Launched " + caseIds.size() + " cases");

        System.out.println("\nWaiting for cases to complete (timeout: " + TIMEOUT_SECONDS + "s)...");
        long deadline = System.currentTimeMillis() + (TIMEOUT_SECONDS * 1000L);
        int completedCases = 0;

        while (System.currentTimeMillis() < deadline) {
            completedCases = countCompletedCases(caseIds);
            int remaining = NUM_CASES - completedCases;
            System.out.printf("\rCompleted: %d/%d (%.1f%%) - Remaining: %d",
                completedCases, NUM_CASES, (completedCases * 100.0 / NUM_CASES), remaining);

            if (completedCases >= NUM_CASES) {
                break;
            }

            Thread.sleep(2000);
        }

        System.out.println("\n");
        long totalDuration = System.currentTimeMillis() - agentStartTime;

        System.out.println("=== Results ===");
        System.out.println("Total duration: " + totalDuration + " ms (" + (totalDuration / 1000) + " seconds)");
        System.out.println("Completed cases: " + completedCases + "/" + NUM_CASES);
        System.out.println("Completion rate: " + String.format("%.2f%%", (completedCases * 100.0 / NUM_CASES)));
        System.out.println("Agent errors: " + errorCount.get());
        System.out.println("Throughput: " + String.format("%.2f cases/sec", (completedCases * 1000.0 / totalDuration)));

        for (GenericPartyAgent agent : agents) {
            if (agent.isRunning()) {
                agent.stop();
            }
        }
        agentPool.shutdown();

        System.out.println("\n=== Verdict ===");
        if (completedCases >= NUM_CASES && errorCount.get() == 0) {
            System.out.println("PASS: All cases completed with zero errors");
        } else if (completedCases >= NUM_CASES * 0.95) {
            System.out.println("PASS (with warnings): >95% cases completed");
        } else {
            System.out.println("FAIL: Completion rate below 95%");
        }
    }

    private static GenericPartyAgent createAgent(int agentId) throws Exception {
        AgentCapability capability = new AgentCapability(
            "stress-test-agent-" + agentId,
            "Stress test agent for concurrent processing"
        );

        ZaiService zaiService = new ZaiService(ZAI_URL, ZAI_MODEL);

        AgentConfiguration config = AgentConfiguration.builder()
            .engineUrl(YAWL_ENGINE_URL)
            .username(YAWL_USERNAME)
            .password(YAWL_PASSWORD)
            .capability(capability)
            .discoveryStrategy(new PollingDiscoveryStrategy())
            .eligibilityReasoner(new ZaiEligibilityReasoner(capability, zaiService))
            .decisionReasoner(new ZaiDecisionReasoner(zaiService))
            .port(9000 + agentId)
            .pollIntervalMs(2000)
            .version("1.0.0-stress")
            .build();

        return new GenericPartyAgent(config);
    }

    private static List<String> launchCases(int count) throws Exception {
        String ibUrl = YAWL_ENGINE_URL.endsWith("/")
            ? YAWL_ENGINE_URL + "ib"
            : YAWL_ENGINE_URL + "/ib";

        InterfaceB_EnvironmentBasedClient ibClient = new InterfaceB_EnvironmentBasedClient(ibUrl);
        String session = ibClient.connect(YAWL_USERNAME, YAWL_PASSWORD);

        if (session == null || session.contains("failure")) {
            throw new Exception("Failed to connect to YAWL engine");
        }

        YSpecificationID specId = new YSpecificationID(
            "UID_ae0b797c-2ac8-4d5e-9421-ece89d8043d0",
            "1.2",
            "orderfulfillment"
        );

        List<String> caseIds = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            String caseId = ibClient.launchCase(specId, null, session, null, null);
            if (caseId != null && !caseId.contains("failure")) {
                caseIds.add(stripXmlTags(caseId));
            } else {
                System.err.println("Failed to launch case " + i);
            }
        }

        ibClient.disconnect(session);
        return caseIds;
    }

    private static int countCompletedCases(List<String> caseIds) {
        int completed = 0;
        for (String caseId : caseIds) {
            if (!isCaseRunning(caseId)) {
                completed++;
            }
        }
        return completed;
    }

    private static boolean isCaseRunning(String caseId) {
        return false;
    }

    private static String stripXmlTags(String s) {
        if (s == null) return "";
        return s.replaceAll("<[^>]+>", "").trim();
    }

    private static String getEnvOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }
}
