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

import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.integration.autonomous.AgentCapability;
import org.yawlfoundation.yawl.integration.autonomous.reasoners.ZaiEligibilityReasoner;
import org.yawlfoundation.yawl.integration.orderfulfillment.EligibilityWorkflow;
import org.yawlfoundation.yawl.integration.zai.ZaiService;

/**
 * Benchmark comparing concrete EligibilityWorkflow vs generic ZaiEligibilityReasoner.
 *
 * Measures latency and throughput to quantify overhead of generic framework.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public final class EligibilityReasoningBenchmark {

    private static final String ZAI_URL = getEnvOrDefault("ZAI_URL", "http://localhost:11434");
    private static final String ZAI_MODEL = getEnvOrDefault("ZAI_MODEL", "llama2");

    public static void main(String[] args) throws Exception {
        System.out.println("=== Eligibility Reasoning Benchmark ===");
        System.out.println("ZAI URL: " + ZAI_URL);
        System.out.println("ZAI Model: " + ZAI_MODEL);

        WorkItemRecord mockWorkItem = createMockWorkItem();
        AgentCapability capability = new AgentCapability(
            "order-fulfillment",
            "Purchase order approval and inventory management"
        );

        ZaiService zaiService = new ZaiService(ZAI_URL, ZAI_MODEL);

        System.out.println("\n=== Concrete EligibilityWorkflow (Baseline) ===");
        BenchmarkHarness concreteHarness = new BenchmarkHarness(
            "Concrete-EligibilityWorkflow",
            10,
            100
        );

        BenchmarkHarness.BenchmarkResult concreteResult = concreteHarness.run(
            new BenchmarkHarness.BenchmarkOperation() {
                private EligibilityWorkflow workflow;

                @Override
                public void setup() {
                    workflow = new EligibilityWorkflow(
                        new org.yawlfoundation.yawl.integration.orderfulfillment.AgentCapability(
                            "order-fulfillment",
                            "Purchase order approval and inventory management"
                        ),
                        zaiService
                    );
                }

                @Override
                public void run() {
                    workflow.isEligible(mockWorkItem);
                }
            }
        );

        System.out.println("\n=== Generic ZaiEligibilityReasoner ===");
        BenchmarkHarness genericHarness = new BenchmarkHarness(
            "Generic-ZaiEligibilityReasoner",
            10,
            100
        );

        BenchmarkHarness.BenchmarkResult genericResult = genericHarness.run(
            new BenchmarkHarness.BenchmarkOperation() {
                private ZaiEligibilityReasoner reasoner;

                @Override
                public void setup() {
                    reasoner = new ZaiEligibilityReasoner(capability, zaiService);
                }

                @Override
                public void run() {
                    reasoner.isEligible(mockWorkItem);
                }
            }
        );

        System.out.println("\n=== Performance Comparison ===");
        double overhead = genericResult.overheadPercentage(concreteResult);
        System.out.printf("Concrete P50: %.3f ms%n", concreteResult.getP50Ms());
        System.out.printf("Generic P50:  %.3f ms%n", genericResult.getP50Ms());
        System.out.printf("Overhead:     %.2f%%%n", overhead);

        System.out.printf("Concrete P95: %.3f ms%n", concreteResult.getP95Ms());
        System.out.printf("Generic P95:  %.3f ms%n", genericResult.getP95Ms());

        System.out.printf("Concrete P99: %.3f ms%n", concreteResult.getP99Ms());
        System.out.printf("Generic P99:  %.3f ms%n", genericResult.getP99Ms());

        System.out.println("\n=== Throughput Comparison ===");
        double concreteThroughput = concreteHarness.measureThroughput(
            new BenchmarkHarness.BenchmarkOperation() {
                private EligibilityWorkflow workflow;

                @Override
                public void setup() {
                    workflow = new EligibilityWorkflow(
                        new org.yawlfoundation.yawl.integration.orderfulfillment.AgentCapability(
                            "order-fulfillment",
                            "Purchase order approval and inventory management"
                        ),
                        zaiService
                    );
                }

                @Override
                public void run() {
                    workflow.isEligible(mockWorkItem);
                }
            },
            10
        );

        double genericThroughput = genericHarness.measureThroughput(
            new BenchmarkHarness.BenchmarkOperation() {
                private ZaiEligibilityReasoner reasoner;

                @Override
                public void setup() {
                    reasoner = new ZaiEligibilityReasoner(capability, zaiService);
                }

                @Override
                public void run() {
                    reasoner.isEligible(mockWorkItem);
                }
            },
            10
        );

        System.out.printf("Concrete throughput: %.2f ops/sec%n", concreteThroughput);
        System.out.printf("Generic throughput:  %.2f ops/sec%n", genericThroughput);
        double throughputDegradation = ((concreteThroughput - genericThroughput) / concreteThroughput) * 100.0;
        System.out.printf("Throughput degradation: %.2f%%%n", throughputDegradation);

        System.out.println("\n=== Verdict ===");
        if (Math.abs(overhead) < 10.0 && Math.abs(throughputDegradation) < 5.0) {
            System.out.println("PASS: Generic framework overhead is acceptable (<10% latency, <5% throughput)");
        } else {
            System.out.println("FAIL: Generic framework overhead exceeds targets");
            System.out.println("  Latency overhead: " + String.format("%.2f%%", overhead) + " (target: <10%)");
            System.out.println("  Throughput degradation: " + String.format("%.2f%%", throughputDegradation) + " (target: <5%)");
        }
    }

    private static WorkItemRecord createMockWorkItem() {
        WorkItemRecord wir = new WorkItemRecord();
        wir.setID("test-workitem-1");
        wir.setCaseID("test-case-1");
        wir.setTaskID("Approve_Purchase_Order");
        wir.setTaskName("Approve Purchase Order");
        wir.setStatus(WorkItemRecord.statusEnabled);
        wir.setDataString("<Approve_Purchase_Order><orderId>PO-12345</orderId><amount>5000.00</amount></Approve_Purchase_Order>");
        return wir;
    }

    private static String getEnvOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }
}
