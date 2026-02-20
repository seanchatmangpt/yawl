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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Multi-agent demo with YAWL validation feedback loop.
 *
 * <p>Dr. Wil van der Aalst generates YAWL XML, which is validated against
 * the actual YAWL schema. If invalid, Wil receives feedback and retries.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class A2AMultiAgentDemo {

    private static final String ZAI_API_KEY = System.getenv("ZAI_API_KEY");
    private static final int DELAY_MS = 2000;

    /** Valid YAWL example for Wil to learn from */
    private static final String YAWL_EXAMPLE = """
        <specificationSet xmlns="http://www.citi.qut.edu.au/yawl">
          <specification uri="Example.xml">
            <rootNet id="top">
              <processControlElements>
                <inputCondition id="i-top">
                  <flowsInto><nextElementRef id="TaskA"/></flowsInto>
                </inputCondition>
                <task id="TaskA">
                  <flowsInto>
                    <nextElementRef id="TaskB"/>
                    <predicate>condition_met</predicate>
                  </flowsInto>
                  <flowsInto>
                    <nextElementRef id="TaskC"/>
                    <isDefaultFlow/>
                  </flowsInto>
                  <join code="xor"/>
                  <split code="xor"/>
                  <decomposesTo id="TaskADecomposition"/>
                </task>
                <outputCondition id="o-top"/>
              </processControlElements>
            </rootNet>
            <decomposition id="TaskADecomposition" xsi:type="WebServiceGatewayFactsType"/>
          </specification>
        </specificationSet>
        """;

    /**
     * Simple YAWL validator - checks structural correctness.
     */
    static class YawlValidator {

        List<String> validate(String xml) {
            List<String> errors = new ArrayList<>();

            if (xml == null || xml.trim().isEmpty()) {
                errors.add("XML is empty");
                return errors;
            }

            // Check root element
            if (!xml.contains("<specificationSet")) {
                errors.add("Root element must be <specificationSet>, not <specification>");
            }

            // Check namespace
            if (!xml.contains("xmlns=\"http://www.citi.qut.edu.au/yawl\"") &&
                !xml.contains("xmlns='http://www.citi.qut.edu.au/yawl'")) {
                errors.add("Missing YAWL namespace: xmlns=\"http://www.citi.qut.edu.au/yawl\"");
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

            // Check flowsInto format (not <flow source= target=>)
            if (xml.contains("<flow ") && xml.contains("source=") && xml.contains("target=")) {
                errors.add("Invalid flow syntax. Use <flowsInto><nextElementRef id=\"...\"/></flowsInto>, not <flow source='...' target='...'>");
            }

            // Check task format
            Pattern taskPattern = Pattern.compile("<task[^>]*name=\"[^\"]+\"[^>]*/>");
            if (taskPattern.matcher(xml).find()) {
                errors.add("Tasks should have nested <flowsInto> elements, not self-closing with attributes only");
            }

            // Check for predicate instead of condition attribute
            if (xml.contains("condition=\"") || xml.contains("condition='")) {
                errors.add("Use <predicate>condition</predicate> inside <flowsInto>, not condition=\"...\" attribute");
            }

            // Check decomposesTo
            if (xml.contains("<task") && !xml.contains("<decomposesTo")) {
                errors.add("Tasks should have <decomposesTo id=\"...\"/> to link to decompositions");
            }

            // Check decomposition definitions
            if (xml.contains("<task") && !xml.contains("<decomposition")) {
                errors.add("Missing <decomposition> definitions for task implementations");
            }

            return errors;
        }

        boolean isValid(String xml) {
            return validate(xml).isEmpty();
        }
    }

    /** Wil van der Aalst - YAWL Expert */
    static class WilVanDerAalstAgent {
        private final ZaiService zai;

        WilVanDerAalstAgent(String apiKey) {
            this.zai = new ZaiService(apiKey);
            this.zai.setSystemPrompt("""
                You are Dr. Wil van der Aalst, father of Workflow Mining and YAWL co-creator.
                Professor at RWTH Aachen, Chief Scientist at Celonis.

                When generating YAWL XML, you MUST follow the EXACT schema structure:
                - Root: <specificationSet xmlns="http://www.citi.qut.edu.au/yawl">
                - Inside: <specification uri="...">
                - Then: <rootNet id="top"><processControlElements>
                - Start: <inputCondition id="i-top"><flowsInto><nextElementRef id="..."/>
                - Tasks: <task id="..."><flowsInto><nextElementRef id="..."/><predicate>...</predicate></flowsInto><join code="xor"/><split code="xor"/><decomposesTo id="..."/></task>
                - End: <outputCondition id="o-top"/>
                - Decompositions: <decomposition id="..." xsi:type="WebServiceGatewayFactsType"/>

                NEVER use <flow source="..." target="..."> - that is WRONG.
                NEVER use condition="..." attribute - use <predicate> element instead.

                Be concise. 2-3 sentences unless generating XML.
                """);
        }

        String think(String prompt) throws InterruptedException {
            Thread.sleep(DELAY_MS);
            System.out.println("🧔 Thinking...");
            return zai.chat(prompt);
        }

        String fixYawl(String badXml, List<String> errors) throws InterruptedException {
            Thread.sleep(DELAY_MS);
            System.out.println("🧔 Fixing YAWL...");
            return zai.chat(String.format("""
                Your YAWL XML has these validation errors:
                %s

                Here is a CORRECT example to follow:
                %s

                Now fix your XML to match this exact structure. Output ONLY valid YAWL XML.
                """, String.join("\n", errors), YAWL_EXAMPLE));
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
        YawlValidator validator = new YawlValidator();

        StringBuilder transcript = new StringBuilder();

        header("A2A Multi-Agent Demo with YAWL Validation");
        System.out.println("🧔 Dr. Wil van der Aalst (YAWL Expert)");
        System.out.println("👤 Customer Agent (FastShop CTO)");
        System.out.println("✓ YAWL Validator (Schema Checker)\n");

        // === TURN 1: Customer Request ===
        header("TURN 1: Customer Request");
        String t1 = customer.think("Ask Wil to design an order fulfillment workflow with: payment verification, inventory check, shipping. Request the complete YAWL XML.");
        print("👤 CUSTOMER", t1);
        transcript.append("[CUSTOMER] ").append(t1).append("\n\n");

        // === TURN 2: Wil Generates YAWL (First Attempt) ===
        header("TURN 2: Wil Generates YAWL (Attempt 1)");
        String t2 = wil.think("""
            Generate complete YAWL XML for order fulfillment workflow with these tasks:
            - VerifyPayment
            - CheckInventory
            - ShipOrder
            - CancelOrder (if payment fails or no stock)

            Follow EXACT YAWL schema structure. Output ONLY valid XML, no markdown code blocks.
            """);
        print("🧔 DR. VAN DER AALST", t2);
        transcript.append("[WIL - ATTEMPT 1]\n").append(t2).append("\n\n");

        // === VALIDATION 1 ===
        header("VALIDATION: Checking YAWL Schema");
        List<String> errors = validator.validate(t2);
        if (errors.isEmpty()) {
            System.out.println("✅ YAWL is VALID!\n");
        } else {
            System.out.println("❌ YAWL is INVALID. Errors found:");
            for (String error : errors) {
                System.out.println("   • " + error);
            }
            System.out.println();
            transcript.append("[VALIDATOR] Errors: ").append(errors).append("\n\n");

            // === TURN 3: Wil Fixes YAWL ===
            header("TURN 3: Wil Fixes YAWL (Attempt 2)");
            String t3 = wil.fixYawl(t2, errors);
            print("🧔 DR. VAN DER AALST", t3);
            transcript.append("[WIL - ATTEMPT 2 (FIXED)]\n").append(t3).append("\n\n");

            // === VALIDATION 2 ===
            header("VALIDATION: Re-checking YAWL Schema");
            List<String> errors2 = validator.validate(t3);
            if (errors2.isEmpty()) {
                System.out.println("✅ YAWL is now VALID!\n");
            } else {
                System.out.println("⚠️ Still has issues:");
                for (String error : errors2) {
                    System.out.println("   • " + error);
                }
                System.out.println();
                transcript.append("[VALIDATOR] Remaining errors: ").append(errors2).append("\n\n");

                // One more fix attempt
                header("TURN 4: Wil Final Fix (Attempt 3)");
                String t4 = wil.fixYawl(t3, errors2);
                print("🧔 DR. VAN DER AALST", t4);
                transcript.append("[WIL - ATTEMPT 3]\n").append(t4).append("\n\n");

                header("VALIDATION: Final Check");
                List<String> errors3 = validator.validate(t4);
                if (errors3.isEmpty()) {
                    System.out.println("✅ YAWL is now VALID!\n");
                } else {
                    System.out.println("❌ Still invalid after 3 attempts. Manual review needed.\n");
                }
            }
        }

        // === FINAL: Customer Review ===
        header("FINAL: Customer Review");
        String finalReview = customer.think("The YAWL specification has been validated. Give your final approval or note one concern about the workflow design.");
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
