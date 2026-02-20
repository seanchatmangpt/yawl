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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Converts compact YAML format to YAWL XML using Jackson YAML parser.
 *
 * <p>This reduces token generation by ~60-70% compared to full XML.</p>
 *
 * <p>Generates schema-compliant YAWL XML 4.0 using namespace
 * http://www.yawlfoundation.org/yawlschema</p>
 *
 * <h2>Compact YAML Format</h2>
 * <pre>{@code
 * name: OrderFulfillment
 * uri: OrderFulfillment.xml
 * first: VerifyPayment
 * tasks:
 *   - id: VerifyPayment
 *     flows: [CheckInventory, CancelOrder]
 *     condition: payment_ok -> CheckInventory
 *     default: CancelOrder
 *     join: xor
 *     split: xor
 *
 *   - id: CheckInventory
 *     flows: [ShipOrder]
 *     condition: in_stock -> ShipOrder
 *     default: CancelOrder
 *     split: xor
 *
 *   - id: ShipOrder
 *     flows: [end]
 *
 *   - id: CancelOrder
 *     flows: [end]
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class YawlYamlConverter {

    /** YAWL Schema 4.0 namespace */
    protected static final String NAMESPACE = "http://www.yawlfoundation.org/yawlschema";

    /** XML Schema Instance namespace */
    protected static final String XSI_NAMESPACE = "http://www.w3.org/2001/XMLSchema-instance";

    private final YAMLMapper yamlMapper;

    public YawlYamlConverter() {
        this.yamlMapper = new YAMLMapper();
    }

    /**
     * Parses compact YAML and generates YAWL XML.
     *
     * @param yaml the compact YAML specification
     * @return valid YAWL XML
     * @throws IllegalArgumentException if YAML is invalid
     */
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
            return generateXml(spec);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse YAML: " + e.getMessage(), e);
        }
    }

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
     * Generate YAWL XML from the parsed specification map.
     */
    @SuppressWarnings("unchecked")
    private String generateXml(Map<String, Object> spec) {
        StringBuilder xml = new StringBuilder();

        String name = getString(spec, "name", "Workflow");
        String uri = getString(spec, "uri", name + ".xml");
        String firstTask = getString(spec, "first", null);
        List<Map<String, Object>> tasks = getTasks(spec);

        xml.append("<specificationSet xmlns=\"").append(NAMESPACE).append("\" ")
           .append("xmlns:xsi=\"").append(XSI_NAMESPACE).append("\" ")
           .append("version=\"4.0\">\n");
        xml.append("  <specification uri=\"").append(escapeXml(uri)).append("\">\n");
        xml.append("    <name>").append(escapeXml(name)).append("</name>\n");
        xml.append("    <metaData/>\n");
        xml.append("    <decomposition id=\"WorkflowNet\" isRootNet=\"true\" xsi:type=\"NetFactsType\">\n");
        xml.append("      <processControlElements>\n");

        // Input condition (start)
        xml.append("        <inputCondition id=\"i-top\">\n");
        xml.append("          <name>start</name>\n");
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

        // Tasks
        for (Map<String, Object> task : tasks) {
            String taskId = getString(task, "id", null);
            if (taskId == null) {
                continue;
            }

            xml.append("        <task id=\"").append(escapeXml(taskId)).append("\">\n");
            xml.append("          <name>").append(escapeXml(taskId)).append("</name>\n");

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

            // Join/Split
            String join = getString(task, "join", null);
            String split = getString(task, "split", null);

            if (join != null) {
                xml.append("          <join code=\"").append(escapeXml(join)).append("\"/>\n");
            }
            if (split != null) {
                xml.append("          <split code=\"").append(escapeXml(split)).append("\"/>\n");
            }

            // Decomposition
            xml.append("          <decomposesTo id=\"").append(escapeXml(taskId))
               .append("Decomposition\"/>\n");
            xml.append("        </task>\n");
        }

        // Output condition (end)
        xml.append("        <outputCondition id=\"o-top\">\n");
        xml.append("          <name>end</name>\n");
        xml.append("        </outputCondition>\n");

        xml.append("      </processControlElements>\n");
        xml.append("    </decomposition>\n");

        // Decomposition definitions
        for (Map<String, Object> task : tasks) {
            String taskId = getString(task, "id", null);
            if (taskId != null) {
                xml.append("    <decomposition id=\"")
                   .append(escapeXml(taskId)).append("Decomposition\" ")
                   .append("xsi:type=\"WebServiceGatewayFactsType\"/>\n");
            }
        }

        xml.append("  </specification>\n");
        xml.append("</specificationSet>");

        return xml.toString();
    }

    // --- Helper methods ---

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        return value.toString();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getTasks(Map<String, Object> spec) {
        Object tasksObj = spec.get("tasks");
        if (tasksObj instanceof List) {
            return (List<Map<String, Object>>) tasksObj;
        }
        return new ArrayList<>();
    }

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
     * Demo main method.
     */
    public static void main(String[] args) {
        String yaml = """
            name: OrderFulfillment
            uri: OrderFulfillment.xml
            first: VerifyPayment
            tasks:
              - id: VerifyPayment
                flows:
                  - CheckInventory
                  - CancelOrder
                condition: payment_ok -> CheckInventory
                default: CancelOrder
                join: xor
                split: xor

              - id: CheckInventory
                flows:
                  - ShipOrder
                  - CancelOrder
                condition: in_stock -> ShipOrder
                default: CancelOrder
                split: xor

              - id: ShipOrder
                flows:
                  - end

              - id: CancelOrder
                flows:
                  - end
            """;

        System.out.println("=== Input YAML (~600 chars) ===\n");
        System.out.println(yaml);

        YawlYamlConverter converter = new YawlYamlConverter();
        String xml = converter.convertToXml(yaml);

        System.out.println("\n=== Output XML (~1600 chars, 2.7x larger) ===\n");
        System.out.println(xml);

        System.out.println("\n=== Compression Stats ===");
        System.out.println("YAML: " + yaml.length() + " chars");
        System.out.println("XML:  " + xml.length() + " chars");
        System.out.println("Ratio: " + String.format("%.1fx", (double) xml.length() / yaml.length()));
    }
}
