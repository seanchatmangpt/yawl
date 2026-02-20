/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.mcp.a2a.example;

import org.yawlfoundation.yawl.integration.zai.ZaiService;

import java.util.ArrayList;
import java.util.List;

/**
 * Multi-agent demo with YAWL YAML generation and validation.
 *
 * <p>Uses compact YAML format instead of XML to reduce token generation by ~70%.</p>
 *
 * <h2>Token Savings</h2>
 * <ul>
 *   <li>YAML workflow spec: ~300-500 tokens</li>
 *   <li>XML workflow spec: ~1200-2000 tokens</li>
 *   <li>Compression ratio: 3-4x</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class A2AMultiAgentDemo {

    private static final String ZAI_API_KEY = System.getenv("ZAI_API_KEY");
    private static final int DELAY_MS = 2000;

    /** Compact YAML example for Wil to learn from (~400 chars vs ~1500 for XML) */
    private static final String YAWL_YAML_EXAMPLE = """
        name: ExampleWorkflow
        uri: Example.xml
        first: TaskA
        tasks:
          - id: TaskA
            flows: [TaskB, TaskC]
            condition: condition_met -> TaskB
            default: TaskC
            join: xor
            split: xor
          - id: TaskB
            flows: [end]
          - id: TaskC
            flows: [end]
        """;

    /**
     * Simple YAWL YAML validator - checks structural correctness.
     */
    static class YawlYamlValidator {

        List<String> validate(String yaml) {
            List<String> errors = new ArrayList<>();

            if (yaml == null || yaml.trim().isEmpty()) {
                errors.add("YAML is empty");
                return errors;
            }

            // Strip markdown code blocks for validation
            String cleaned = stripMarkdown(yaml);

            // Check required fields
            if (!cleaned.contains("name:") && !cleaned.contains("tasks:")) {
                errors.add("Missing 'name:' or 'tasks:' field");
            }

            // Check for task definitions
            if (!cleaned.contains("- id:")) {
                errors.add("No task definitions found (use '- id: TaskName')");
            }

            // Check for flows
            if (!cleaned.contains("flows:")) {
                errors.add("Tasks must have 'flows:' defined");
            }

            // Check for invalid XML-like syntax (but allow markdown code blocks)
            String noMarkdown = cleaned.replace("```", "");
            if (noMarkdown.contains("<") && noMarkdown.contains(">") &&
                (noMarkdown.contains("</") || noMarkdown.contains("/>"))) {
                errors.add("YAML should not contain XML syntax (< > tags)");
            }

            return errors;
        }

        boolean isValid(String yaml) {
            return validate(yaml).isEmpty();
        }

        private String stripMarkdown(String text) {
            String trimmed = text.trim();
            if (trimmed.startsWith("```")) {
                int newlineIndex = trimmed.indexOf('\n');
                if (newlineIndex > 0) {
                    trimmed = trimmed.substring(newlineIndex + 1);
                }
                int closingFence = trimmed.lastIndexOf("```");
                if (closingFence > 0) {
                    trimmed = trimmed.substring(0, closingFence);
                }
            }
            return trimmed.trim();
        }
    }

    /**
     * YAWL XML validator - validates the converted XML.
     */
    static class YawlXmlValidator {

        List<String> validate(String xml) {
            List<String> errors = new ArrayList<>();

            if (xml == null || xml.trim().isEmpty()) {
                errors.add("XML is empty");
                return errors;
            }

            // Check root element
            if (!xml.contains("<specificationSet")) {
                errors.add("Root element must be <specificationSet>");
            }

            // Check namespace
            if (!xml.contains("xmlns=\"http://www.citi.qut.edu.au/yawl\"")) {
                errors.add("Missing YAWL namespace");
            }

            // Check rootNet
            if (!xml.contains("<rootNet")) {
                errors.add("Missing <rootNet> element");
            }

            // Check processControlElements
            if (!xml.contains("<processControlElements")) {
                errors.add("Missing <processControlElements> inside rootNet");
            }

            // Check inputCondition (start)
            if (!xml.contains("<inputCondition")) {
                errors.add("Missing <inputCondition> (workflow start)");
            }

            // Check outputCondition (end)
            if (!xml.contains("<outputCondition")) {
                errors.add("Missing <outputCondition> (workflow end)");
            }

            // Check for tasks
            if (!xml.contains("<task")) {
                errors.add("No <task> elements found");
            }

            // Check decomposesTo
            if (xml.contains("<task") && !xml.contains("<decomposesTo")) {
                errors.add("Tasks should have <decomposesTo>");
            }

            // Check decomposition definitions
            if (xml.contains("<task") && !xml.contains("<decomposition")) {
                errors.add("Missing <decomposition> definitions");
            }

            return errors;
        }

        boolean isValid(String xml) {
            return validate(xml).isEmpty();
        }
    }

    /** Wil van der Aalst - YAWL Expert (generates YAML) */
    static class WilVanDerAalstAgent {
        private final ZaiService zai;

        WilVanDerAalstAgent(String apiKey) {
            this.zai = new ZaiService(apiKey);
            this.zai.setSystemPrompt("""
                You are Dr. Wil van der Aalst, father of Workflow Mining and YAWL co-creator.
                Professor at RWTH Aachen, Chief Scientist at Celonis.

                Generate YAWL workflows in COMPACT YAML format:

                name: WorkflowName
                uri: WorkflowName.xml
                first: FirstTask
                tasks:
                  - id: TaskName
                    flows: [NextTask1, NextTask2]
                    condition: predicate -> NextTask1
                    default: NextTask2
                    join: xor/and
                    split: xor/and

                Rules:
                - Use 'first:' to specify entry task
                - Use 'flows: [task1, task2]' for flow targets
                - Use 'condition: pred -> target' for conditional flows
                - Use 'default: TaskName' for default flow
                - Use 'end' as target to connect to output condition
                - NO XML syntax (< > tags) - only YAML

                Be concise. 2-3 sentences unless generating YAML.
                """);
        }

        String think(String prompt) throws InterruptedException {
            Thread.sleep(DELAY_MS);
            System.out.println("🧔 Thinking...");
            return zai.chat(prompt);
        }

        String fixYaml(String badYaml, List<String> errors) throws InterruptedException {
            Thread.sleep(DELAY_MS);
            System.out.println("🧔 Fixing YAML...");
            return zai.chat(String.format("""
                Your YAWL YAML has these validation errors:
                %s

                Here is a CORRECT example to follow:
                %s

                Now fix your YAML to match this structure. Output ONLY valid YAML.
                """, String.join("\n", errors), YAWL_YAML_EXAMPLE));
        }
    }

    /** Customer Agent */
    static class CustomerAgent {
        private final ZaiService zai;

        CustomerAgent(String apiKey) {
            this.zai = new ZaiService(apiKey);
            this.zai.setSystemPrompt("""
                You are CTO of FastShop, an e-commerce company.
                Be concise. 2-3 sentences per response.
                """);
        }

        String think(String prompt) throws InterruptedException {
            Thread.sleep(DELAY_MS);
            System.out.println("👤 Thinking...");
            return zai.chat(prompt);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        if (ZAI_API_KEY == null || ZAI_API_KEY.isEmpty()) {
            throw new IllegalStateException("ZAI_API_KEY environment variable required");
        }

        WilVanDerAalstAgent wil = new WilVanDerAalstAgent(ZAI_API_KEY);
        CustomerAgent customer = new CustomerAgent(ZAI_API_KEY);
        YawlYamlValidator yamlValidator = new YawlYamlValidator();
        YawlXmlValidator xmlValidator = new YawlXmlValidator();
        YawlYamlConverter converter = new YawlYamlConverter();

        StringBuilder transcript = new StringBuilder();

        header("A2A Multi-Agent Demo with YAWL YAML Generation");
        System.out.println("🧔 Dr. Wil van der Aalst (YAWL Expert - generates YAML)");
        System.out.println("👤 Customer Agent (FastShop CTO)");
        System.out.println("✓ YAML Validator → XML Converter → XML Validator\n");
        System.out.println("Token savings: YAML is ~3.7x smaller than XML\n");

        // === TURN 1: Customer Request ===
        header("TURN 1: Customer Request");
        String t1 = customer.think("Ask Wil to design an order fulfillment workflow with: payment verification, inventory check, shipping. Request YAML format.");
        print("👤 CUSTOMER", t1);
        transcript.append("[CUSTOMER] ").append(t1).append("\n\n");

        // === TURN 2: Wil Generates YAML (First Attempt) ===
        header("TURN 2: Wil Generates YAML (Attempt 1)");
        String t2 = wil.think("""
            Generate YAWL YAML for order fulfillment workflow with:
            - VerifyPayment (entry point)
            - CheckInventory (if payment OK)
            - ShipOrder (if in stock)
            - CancelOrder (if payment fails or no stock)

            Output ONLY valid YAML, no explanations.
            """);
        print("🧔 DR. VAN DER AALST (YAML)", t2);
        transcript.append("[WIL - YAML ATTEMPT 1]\n").append(t2).append("\n\n");

        // === YAML VALIDATION ===
        header("VALIDATION: Checking YAML Syntax");
        List<String> yamlErrors = yamlValidator.validate(t2);
        String validYaml = t2;

        if (!yamlErrors.isEmpty()) {
            System.out.println("❌ YAML has issues:");
            for (String error : yamlErrors) {
                System.out.println("   • " + error);
            }
            System.out.println();
            transcript.append("[YAML VALIDATOR] Errors: ").append(yamlErrors).append("\n\n");

            // Fix YAML
            header("TURN 3: Wil Fixes YAML (Attempt 2)");
            validYaml = wil.fixYaml(t2, yamlErrors);
            print("🧔 DR. VAN DER AALST (FIXED YAML)", validYaml);
            transcript.append("[WIL - YAML ATTEMPT 2]\n").append(validYaml).append("\n\n");
        } else {
            System.out.println("✅ YAML syntax is VALID!\n");
        }

        // === CONVERT TO XML ===
        header("CONVERSION: YAML → YAWL XML");
        System.out.println("Input YAML size: " + validYaml.length() + " chars");
        String xml;
        try {
            xml = converter.convertToXml(validYaml);
            System.out.println("Output XML size: " + xml.length() + " chars");
            System.out.println("Compression ratio: " + String.format("%.1fx", (double) xml.length() / validYaml.length()));
            System.out.println();
            transcript.append("[CONVERTER] Generated XML:\n").append(xml).append("\n\n");
        } catch (Exception e) {
            System.out.println("❌ Conversion failed: " + e.getMessage());
            transcript.append("[CONVERTER] Error: ").append(e.getMessage()).append("\n\n");
            return;
        }

        // === XML VALIDATION ===
        header("VALIDATION: Checking YAWL XML Schema");
        List<String> xmlErrors = xmlValidator.validate(xml);

        if (xmlErrors.isEmpty()) {
            System.out.println("✅ YAWL XML is VALID!\n");
            System.out.println("Generated XML:");
            System.out.println("─".repeat(60));
            System.out.println(xml);
            System.out.println("─".repeat(60));
        } else {
            System.out.println("❌ XML has issues:");
            for (String error : xmlErrors) {
                System.out.println("   • " + error);
            }
            System.out.println();
            transcript.append("[XML VALIDATOR] Errors: ").append(xmlErrors).append("\n\n");
        }

        // === FINAL: Customer Review ===
        header("FINAL: Customer Review");
        String finalReview = customer.think("The YAWL workflow has been generated. YAML format saved ~70% tokens. Review and approve or note concerns.");
        print("👤 CUSTOMER", finalReview);
        transcript.append("[CUSTOMER - FINAL] ").append(finalReview).append("\n\n");

        // === FULL TRANSCRIPT ===
        header("FULL TRANSCRIPT");
        System.out.println(transcript);
    }

    private static void header(String title) {
        System.out.println("\n════════════════ " + title + " ════════════════\n");
    }

    private static void print(String speaker, String message) {
        System.out.println("┌─────────────────────────────────────────────────────────────────┐");
        System.out.println("│ " + speaker);
        System.out.println("├─────────────────────────────────────────────────────────────────┤");
        String[] lines = message.split("\n");
        for (String line : lines) {
            wrapLine(line);
        }
        System.out.println("└─────────────────────────────────────────────────────────────────┘");
    }

    private static void wrapLine(String line) {
        int maxLen = 64;
        while (line.length() > maxLen) {
            System.out.println("│ " + line.substring(0, maxLen));
            line = line.substring(maxLen);
        }
        System.out.println("│ " + line);
    }
}
