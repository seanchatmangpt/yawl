/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.performance.jmh.fixtures;

import java.util.List;
import java.util.Map;

/**
 * Test data fixtures for MCP performance benchmarks.
 *
 * Provides realistic test data that simulates real-world MCP tool usage patterns.
 * Includes various workflow specifications and conversion scenarios.
 *
 * @author YAWL Performance Team
 * @date 2026-02-26
 */
public class McpBenchmarkData {

    // Simple single-task workflow YAML
    public static final String SIMPLE_YAML = """
        name: SimpleTask
        uri: simple.xml
        first: TaskA
        tasks:
          - id: TaskA
            flows: [end]
        """;

    // Complex parallel workflow YAML
    public static final String PARALLEL_YAML = """
        name: OrderProcessing
        uri: order.xml
        first: Start
        tasks:
          - id: Start
            flows: [VerifyOrder, CheckInventory]
            split: and
          - id: VerifyOrder
            flows: [ProcessPayment]
          - id: CheckInventory
            flows: [ShipOrder, Backorder]
            condition: in_stock -> ShipOrder
            default: Backorder
            split: xor
          - id: ProcessPayment
            flows: [ShipOrder]
          - id: ShipOrder
            flows: [End]
            join: and
          - id: Backorder
            flows: [NotifyCustomer]
          - id: NotifyCustomer
            flows: [End]
          - id: End
            flows: []
        """;

    // Multi-step sequential workflow YAML
    public static final String SEQUENTIAL_YAML = """
        name: MultiStepProcess
        uri: sequential.xml
        first: Step1
        tasks:
          - id: Step1
            flows: [Step2]
          - id: Step2
            flows: [Step3]
          - id: Step3
            flows: [Step4]
          - id: Step4
            flows: [end]
        """;

    // Complex workflow with conditions
    public static final String CONDITIONAL_YAML = """
        name: LoanApproval
        uri: loan.xml
        first: Application
        tasks:
          - id: Application
            flows: [CreditCheck, IncomeVerification]
            split: and
          - id: CreditCheck
            flows: [RiskAssessment]
            condition: score > 700 -> RiskAssessment
            default: Reject
            split: xor
          - id: IncomeVerification
            flows: [RiskAssessment]
          - id: RiskAssessment
            flows: [Approve, Reject]
            split: xor
          - id: Approve
            flows: [Notify]
          - id: Reject
            flows: [Notify]
          - id: Notify
            flows: [end]
        """;

    // Corresponding YAWL XML specifications for validation
    public static final String SIMPLE_YAWL_XML = """
        <specificationSet xmlns="http://www.yawlfoundation.org/yawlschema">
          <specification uri="simple.xml" name="SimpleTask">
            <net>
              <places>
                <place id="start" name="Start"/>
                <place id="end" name="End"/>
              </places>
              <transitions>
                <transition id="TaskA" name="Task A"/>
              </transitions>
              <arcs>
                <arc id="arc1" source="start" target="TaskA"/>
                <arc id="arc2" source="TaskA" target="end"/>
              </arcs>
            </net>
          </specification>
        </specificationSet>
        """;

    public static final String PARALLEL_YAWL_XML = """
        <specificationSet xmlns="http://www.yawlfoundation.org/yawlschema">
          <specification uri="order.xml" name="OrderProcessing">
            <net>
              <places>
                <place id="start" name="Start"/>
                <place id="verify_order" name="VerifyOrder"/>
                <place id="check_inventory" name="CheckInventory"/>
                <place id="process_payment" name="ProcessPayment"/>
                <place id="ship_order" name="ShipOrder"/>
                <place id="backorder" name="Backorder"/>
                <place id="notify_customer" name="NotifyCustomer"/>
                <place id="end" name="End"/>
              </places>
              <transitions>
                <transition id="Start" name="Start"/>
                <transition id="VerifyOrder" name="Verify Order"/>
                <transition id="CheckInventory" name="Check Inventory"/>
                <transition id="ProcessPayment" name="Process Payment"/>
                <transition id="ShipOrder" name="Ship Order"/>
                <transition id="Backorder" name="Backorder"/>
                <transition id="NotifyCustomer" name="Notify Customer"/>
              </transitions>
              <arcs>
                <arc id="arc1" source="start" target="Start"/>
                <arc id="arc2" source="Start" target="VerifyOrder"/>
                <arc id="arc3" source="Start" target="CheckInventory"/>
                <arc id="arc4" source="VerifyOrder" target="ProcessPayment"/>
                <arc id="arc5" source="CheckInventory" target="ShipOrder"/>
                <arc id="arc6" source="CheckInventory" target="Backorder"/>
                <arc id="arc7" source="ProcessPayment" target="ShipOrder"/>
                <arc id="arc8" source="ShipOrder" target="end"/>
                <arc id="arc9" source="Backorder" target="NotifyCustomer"/>
                <arc id="arc10" source="NotifyCustomer" target="end"/>
              </arcs>
            </net>
          </specification>
        </specificationSet>
        """;

    // Performance test scenarios
    public static final List<Map<String, Object>> PERFORMANCE_SCENARIOS = List.of(
        Map.of(
            "name", "Simple Conversion",
            "yaml", SIMPLE_YAML,
            "expected_complexity", "LOW",
            "expected_processing_time_ms", 10
        ),
        Map.of(
            "name", "Parallel Processing",
            "yaml", PARALLEL_YAML,
            "expected_complexity", "MEDIUM",
            "expected_processing_time_ms", 25
        ),
        Map.of(
            "name", "Sequential Flow",
            "yaml", SEQUENTIAL_YAML,
            "expected_complexity", "LOW",
            "expected_processing_time_ms", 15
        ),
        Map.of(
            "name", "Conditional Logic",
            "yaml", CONDITIONAL_YAML,
            "expected_complexity", "HIGH",
            "expected_processing_time_ms", 40
        )
    );

    // Tool operation patterns
    public static final List<String> TOOL_OPERATIONS = List.of(
        "yaml-converter",
        "soundness-verifier",
        "yaml-converter",
        "soundness-verifier",
        "yaml-converter"
    );

    // Error patterns for testing robustness
    public static final List<String> ERROR_PATTERNS = List.of(
        // Invalid YAML formats
        "name: InvalidWorkflow\nflows: [invalid]",
        "name: MissingFirstTask\nuri: missing.xml\ntasks: []",
        // Invalid XML formats
        "<specification><invalid>content</invalid></specification>",
        "<specificationSet><specification><net></net></specification></specificationSet>"
    );

    public static String getRandomYaml() {
        var yamlList = List.of(SIMPLE_YAML, PARALLEL_YAML, SEQUENTIAL_YAML, CONDITIONAL_YAML);
        return yamlList.get((int) (Math.random() * yamlList.size()));
    }

    public static String getRandomYawlXml() {
        var xmlList = List.of(SIMPLE_YAWL_XML, PARALLEL_YAWL_XML);
        return xmlList.get((int) (Math.random() * xmlList.size()));
    }
}
