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

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Extended YAML converter supporting all YAWL patterns with advanced features.
 *
 * <p>Enhances the base YawlYamlConverter with support for:
 * - Variables and data types
 * - Multi-instance patterns
 * - Timers and deadlines
 * - Cancellation patterns
 * - Agent tasks
 * - Compensations
 * - Business rules
 * - Complex conditions</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class ExtendedYamlConverter extends YawlYamlConverter {

    private final YAMLMapper yamlMapper;

    public ExtendedYamlConverter() {
        this.yamlMapper = new YAMLMapper();
    }

    /**
     * Convert extended YAML specification to YAWL XML
     */
    @Override
    @SuppressWarnings("unchecked")
    public String convertToXml(String yaml) {
        if (yaml == null || yaml.trim().isEmpty()) {
            throw new IllegalArgumentException("YAML input cannot be null or empty");
        }

        // Strip markdown code blocks if present
        String cleaned = stripMarkdownCodeBlock(yaml);

        try {
            // Parse YAML to Map using Jackson
            Map<String, Object> spec = yamlMapper.readValue(cleaned, Map.class);
            return generateExtendedXml(spec);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse YAML: " + e.getMessage(), e);
        }
    }

    /**
     * Generate XML from extended specification
     */
    @SuppressWarnings("unchecked")
    private String generateExtendedXml(Map<String, Object> spec) {
        StringBuilder xml = new StringBuilder();

        String name = getString(spec, "name", "Workflow");
        String uri = getString(spec, "uri", name + ".xml");
        String firstTask = getString(spec, "first", null);
        List<Map<String, Object>> tasks = getTasks(spec);
        List<Map<String, Object>> variables = getVariables(spec);
        List<Map<String, Object>> compensations = getCompensations(spec);

        xml.append("<specificationSet xmlns=\"http://www.citi.qut.edu.au/yawl\" ")
           .append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n");
        xml.append("  <specification uri=\"").append(escapeXml(uri)).append("\">\n");
        xml.append("    <rootNet id=\"top\">\n");
        xml.append("      <processControlElements>\n");

        // Input condition (start)
        xml.append("        <inputCondition id=\"i-top\">\n");
        if (firstTask != null) {
            xml.append("          <flowsInto><nextElementRef id=\"")
               .append(escapeXml(firstTask)).append("\"/></flowsInto>\n");
        } else if (!tasks.isEmpty()) {
            // Default to first task
            String firstId = getString(tasks.get(0), "id", null);
            if (firstId != null) {
                xml.append("          <flowsInto><nextElementRef id=\"")
                   .append(escapeXml(firstId)).append("\"/></flowsInto>\n");
            }
        }
        xml.append("        </inputCondition>\n");

        // Variable declarations
        if (variables != null && !variables.isEmpty()) {
            xml.append("        <variableDeclarations>\n");
            for (Map<String, Object> var : variables) {
                String varId = getString(var, "name", null);
                String varType = getString(var, "type", "xs:string");
                String defaultValue = getString(var, "default", null);

                xml.append("          <variable id=\"").append(escapeXml(varId)).append("\">\n");
                xml.append("            <typeDeclaration>").append(escapeXml(varType)).append("</typeDeclaration>\n");
                if (defaultValue != null) {
                    xml.append("            <defaultValue>").append(escapeXml(defaultValue)).append("</defaultValue>\n");
                }
                xml.append("          </variable>\n");
            }
            xml.append("        </variableDeclarations>\n");
        }

        // Tasks
        for (Map<String, Object> task : tasks) {
            String taskId = getString(task, "id", null);
            if (taskId == null) {
                continue;
            }

            xml.append("        <task id=\"").append(escapeXml(taskId)).append("\">\n");

            // Task description
            String description = getString(task, "description", null);
            if (description != null) {
                xml.append("          <description>").append(escapeXml(description)).append("</description>\n");
            }

            // Flow definitions
            List<String> flows = getStringList(task, "flows");
            String condition = getString(task, "condition", null);
            String defaultFlow = getString(task, "default", null);

            for (String flow : flows) {
                xml.append("          <flowsInto>\n");
                xml.append("            <nextElementRef id=\"").append(escapeXml(flow)).append("\"/>\n");

                // Check if this is the conditional flow
                if (condition != null && condition.contains("->")) {
                    String[] parts = condition.split("->");
                    if (parts.length == 2) {
                        String conditionTask = parts[1].trim();
                        if (flow.equals(conditionTask)) {
                            xml.append("            <predicate>")
                               .append(escapeXml(parts[0].trim()))
                               .append("</predicate>\n");
                        }
                    }
                }

                // Check if this is the default flow
                if (defaultFlow != null && flow.equals(defaultFlow)) {
                    xml.append("            <isDefaultFlow/>\n");
                }

                xml.append("          </flowsInto>\n");
            }

            // Multi-instance configuration
            Map<String, Object> multiInstance = getMap(task, "multiInstance");
            if (multiInstance != null) {
                xml.append("          <multiInstance>\n");
                String min = getString(multiInstance, "min", "1");
                String max = getString(multiInstance, "max", "1");
                String mode = getString(multiInstance, "mode", "static");
                String threshold = getString(multiInstance, "threshold", "all");

                xml.append("            <minInstances>").append(min).append("</minInstances>\n");
                xml.append("            <maxInstances>").append(max).append("</maxInstances>\n");
                xml.append("            <instanceSelection>").append(mode).append("</instanceSelection>\n");
                xml.append("            <completionThreshold>").append(threshold).append("</completionThreshold>\n");
                xml.append("          </multiInstance>\n");
            }

            // Timer configuration
            Map<String, Object> timer = getMap(task, "timer");
            if (timer != null) {
                xml.append("          <timer>\n");
                String trigger = getString(timer, "trigger", "onEnabled");
                String duration = getString(timer, "duration", "PT5M");

                xml.append("            <trigger>").append(trigger).append("</trigger>\n");
                xml.append("            <duration>").append(duration).append("</duration>\n");
                xml.append("          </timer>\n");
            }

            // Cancellation configuration
            List<String> cancels = getStringList(task, "cancels");
            String cancelCondition = getString(task, "cancelCondition", null);
            if (!cancels.isEmpty() || cancelCondition != null) {
                xml.append("          <cancellation>\n");
                for (String cancel : cancels) {
                    xml.append("            <cancelsElement id=\"").append(escapeXml(cancel)).append("\"/>\n");
                }
                if (cancelCondition != null) {
                    xml.append("            <condition>").append(escapeXml(cancelCondition)).append("</condition>\n");
                }
                xml.append("          </cancellation>\n");
            }

            // Agent configuration
            Map<String, Object> agent = getMap(task, "agent");
            if (agent != null) {
                xml.append("          <agent>\n");
                String agentType = getString(agent, "type", "human");
                String binding = getString(agent, "binding", "static");
                List<String> capabilities = getStringList(agent, "capabilities");

                xml.append("            <agentType>").append(agentType).append("</agentType>\n");
                xml.append("            <binding>").append(binding).append("</binding>\n");
                if (!capabilities.isEmpty()) {
                    xml.append("            <capabilities>\n");
                    for (String cap : capabilities) {
                        xml.append("              <capability>").append(cap).append("</capability>\n");
                    }
                    xml.append("            </capabilities>\n");
                }
                xml.append("          </agent>\n");
            }

            // Approval configuration
            if (getBoolean(task, "approval", false)) {
                xml.append("          <approval>true</approval>\n");
                String approver = getString(task, "approver", null);
                String amountThreshold = getString(task, "amountThreshold", null);
                String approvalType = getString(task, "approvalType", "sequential");

                if (approver != null) {
                    xml.append("          <approver>").append(approver).append("</approver>\n");
                }
                if (amountThreshold != null) {
                    xml.append("          <amountThreshold>").append(amountThreshold).append("</amountThreshold>\n");
                }
                xml.append("          <approvalType>").append(approvalType).append("</approvalType>\n");
            }

            // Join/Split
            String join = getString(task, "join", null);
            String split = getString(task, "split", null);

            if (join != null) {
                xml.append("          <join code=\"").append(escapeXml(join)).append("\"/>\n");
            }
            if (split != null) {
                xml.append("          <split code=\"").append(escapeXml(split)).append("\"/>\n");
            }

            // Business rules
            List<String> businessRules = getStringList(task, "businessRules");
            if (!businessRules.isEmpty()) {
                xml.append("          <businessRules>\n");
                for (String rule : businessRules) {
                    xml.append("            <rule>").append(escapeXml(rule)).append("</rule>\n");
                }
                xml.append("          </businessRules>\n");
            }

            // Decomposition
            xml.append("          <decomposesTo id=\"").append(escapeXml(taskId))
               .append("Decomposition\"/>\n");
            xml.append("        </task>\n");
        }

        // Output condition (end)
        xml.append("        <outputCondition id=\"o-top\"/>\n");

        xml.append("      </processControlElements>\n");
        xml.append("    </rootNet>\n");

        // Decomposition definitions
        for (Map<String, Object> task : tasks) {
            String taskId = getString(task, "id", null);
            if (taskId != null) {
                xml.append("    <decomposition id=\"")
                   .append(escapeXml(taskId)).append("Decomposition\" ")
                   .append("xsi:type=\"WebServiceGatewayFactsType\"/>\n");
            }
        }

        // Compensation definitions
        if (compensations != null && !compensations.isEmpty()) {
            xml.append("    <compensations>\n");
            for (Map<String, Object> comp : compensations) {
                String compId = getString(comp, "id", null);
                String description = getString(comp, "description", null);

                xml.append("      <compensation id=\"").append(escapeXml(compId)).append("\">\n");
                if (description != null) {
                    xml.append("        <description>").append(escapeXml(description)).append("</description>\n");
                }
                xml.append("      </compensation>\n");
            }
            xml.append("    </compensations>\n");
        }

        xml.append("  </specification>\n");
        xml.append("</specificationSet>");

        return xml.toString();
    }

    // --- Extended helper methods ---

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getVariables(Map<String, Object> spec) {
        Object varsObj = spec.get("variables");
        if (varsObj instanceof List) {
            return (List<Map<String, Object>>) varsObj;
        }
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getCompensations(Map<String, Object> spec) {
        Object compsObj = spec.get("compensations");
        if (compsObj instanceof List) {
            return (List<Map<String, Object>>) compsObj;
        }
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return null;
    }

    private boolean getBoolean(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }

    // --- Helper methods (private in parent, so reimplemented here) ---

    /**
     * Strip markdown code block wrappers (```yaml ... ``` or ``` ... ```).
     */
    private String stripMarkdownCodeBlock(String text) {
        String trimmed = text.trim();

        // Check for opening code fence
        if (trimmed.startsWith("```")) {
            int newlineIndex = trimmed.indexOf('\n');
            if (newlineIndex > 0) {
                // Skip the opening fence line (```yaml or ```)
                trimmed = trimmed.substring(newlineIndex + 1);
            }

            // Remove closing fence
            int closingFence = trimmed.lastIndexOf("```");
            if (closingFence > 0) {
                trimmed = trimmed.substring(0, closingFence);
            }
        }

        return trimmed.trim();
    }

    /**
     * Get a string value from a map with a default.
     */
    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        return value.toString();
    }

    /**
     * Get a list of strings from a map.
     */
    @SuppressWarnings("unchecked")
    private List<String> getStringList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    result.add(item.toString());
                }
            }
            return result;
        }
        return new ArrayList<>();
    }

    /**
     * Escape special XML characters.
     */
    private String escapeXml(String s) {
        if (s == null) {
            throw new IllegalArgumentException("String to escape cannot be null");
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /**
     * Get the list of tasks from a specification.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getTasks(Map<String, Object> spec) {
        Object tasksObj = spec.get("tasks");
        if (tasksObj instanceof List) {
            return (List<Map<String, Object>>) tasksObj;
        }
        return new ArrayList<>();
    }

    /**
     * Test method to demonstrate extended YAML conversion
     */
    public static void main(String[] args) {
        // Extended YAML example
        String extendedYaml = """
            name: ExtendedWorkflow
            uri: extended.xml
            first: StartProcess

            variables:
              - name: customerId
                type: xs:string
              - name: orderValue
                type: xs:decimal
                default: 0.0

            tasks:
              - id: StartProcess
                flows: [CheckOrder]
                split: xor
                join: and
                description: "Start order processing"

              - id: CheckOrder
                flows: [ProcessOrder, CancelOrder]
                condition: orderValue > 0 -> ProcessOrder
                default: CancelOrder
                split: xor
                join: xor
                timer:
                  trigger: onEnabled
                  duration: PT10M

              - id: ProcessOrder
                flows: [ShipItems]
                split: xor
                join: and
                agent:
                  type: human
                  binding: dynamic
                  capabilities: [order-processing]
                description: "Process customer order"

              - id: ShipItems
                flows: [end]
                multiInstance:
                  min: 1
                  max: count(/items/item)
                  mode: dynamic
                  threshold: all
                split: xor
                join: and
                description: "Ship order items"

              - id: CancelOrder
                flows: [end]
                split: xor
                join: xor
                description: "Cancel order"

            compensations:
              - id: compensateShip
                description: "Reverse shipment"
            """;

        System.out.println("=== Extended YAML Input ===\n");
        System.out.println(extendedYaml);

        ExtendedYamlConverter converter = new ExtendedYamlConverter();
        String xml = converter.convertToXml(extendedYaml);

        System.out.println("\n=== Extended XML Output ===\n");
        System.out.println(xml);

        System.out.println("\n=== Extended Features ===");
        System.out.println("• Variables with types and defaults");
        System.out.println("• Timers with duration");
        System.out.println("• Agent tasks with capabilities");
        System.out.println("• Multi-instance with dynamic mode");
        System.out.println("• Compensations");
        System.out.println("• Complex conditions");
        System.out.println("• Business rules support");
    }
}