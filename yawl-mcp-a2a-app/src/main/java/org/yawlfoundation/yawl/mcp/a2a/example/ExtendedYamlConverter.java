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

/**
 * Extended YAML converter producing YAWL Schema 4.0 compliant XML.
 *
 * <p>Enhances the base YawlYamlConverter with support for:
 * - Variables as inputParam/localVariable (DecompositionFactsType)
 * - Multi-instance tasks (MultipleInstanceExternalTaskFactsType)
 * - Timers with OnEnabled/OnExecuting triggers (TimerType)
 * - Cancellation via removesTokens (ExternalTaskFactsType)
 * - Complex conditions with predicates and default flows
 * - WebServiceGateway task decompositions</p>
 *
 * <p>All output validates against YAWL_Schema4.0.xsd with namespace
 * http://www.yawlfoundation.org/yawlschema</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class ExtendedYamlConverter extends YawlYamlConverter {

    /** YAWL Schema 4.0 namespace */
    private static final String NAMESPACE = "http://www.yawlfoundation.org/yawlschema";

    /** XML Schema Instance namespace */
    private static final String XSI_NAMESPACE = "http://www.w3.org/2001/XMLSchema-instance";

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
     * Generate schema-compliant YAWL XML 4.0 from extended specification.
     *
     * <p>Element order per XSD types:
     * <ul>
     *   <li>specificationSet: specification+, layout?</li>
     *   <li>specification: name?, documentation?, metaData, xs:any?, decomposition+, importedNet*</li>
     *   <li>decomposition (NetFactsType): name?, documentation?, inputParam*, outputParam*,
     *       logPredicate?, localVariable*, processControlElements, externalDataGateway?</li>
     *   <li>task (ExternalTaskFactsType): name?, documentation?, flowsInto+, join, split,
     *       removesTokens*, removesTokensFromFlow*, startingMappings?, completedMappings?,
     *       enablementMappings?, timer?, resourcing?, customForm?, decomposesTo?</li>
     * </ul></p>
     */
    @SuppressWarnings("unchecked")
    private String generateExtendedXml(Map<String, Object> spec) {
        StringBuilder xml = new StringBuilder();

        String name = getString(spec, "name", "Workflow");
        String uri = getString(spec, "uri", name + ".xml");
        String firstTask = getString(spec, "first", null);
        List<Map<String, Object>> tasks = getTasks(spec);
        List<Map<String, Object>> variables = getVariables(spec);

        xml.append("<specificationSet xmlns=\"").append(NAMESPACE).append("\" ")
           .append("xmlns:xsi=\"").append(XSI_NAMESPACE).append("\" ")
           .append("xsi:schemaLocation=\"").append(NAMESPACE).append(" ").append(NAMESPACE).append("/YAWL_Schema4.0.xsd\" ")
           .append("version=\"4.0\">\n");
        xml.append("  <specification uri=\"").append(escapeXml(uri)).append("\">\n");

        // Add name element (required by schema)
        xml.append("    <name>").append(escapeXml(name)).append("</name>\n");

        // Add documentation if available
        String specDescription = getString(spec, "description", null);
        if (specDescription != null) {
            xml.append("    <documentation>").append(escapeXml(specDescription)).append("</documentation>\n");
        }

        // Add metaData (required by schema)
        xml.append("    <metaData/>\n");

        // Root net as decomposition with isRootNet="true"
        xml.append("    <decomposition id=\"").append(escapeXml(name)).append("Net\" ")
           .append("isRootNet=\"true\" ")
           .append("xmlns:xsi=\"").append(XSI_NAMESPACE).append("\" ")
           .append("xsi:type=\"NetFactsType\">\n");

        // decomposition/name element (optional per DecompositionFactsType)
        xml.append("      <name>").append(escapeXml(name)).append("Net</name>\n");

        // inputParam declarations (per DecompositionFactsType, before localVariable)
        if (variables != null && !variables.isEmpty()) {
            int index = 0;
            for (Map<String, Object> var : variables) {
                String varName = getString(var, "name", null);
                String varType = getString(var, "type", "xs:string");
                String defaultValue = getString(var, "default", null);
                boolean isInputParam = getBoolean(var, "input", true);

                if (varName == null) {
                    continue;
                }

                String normalizedType = normalizeTypeName(varType);

                if (isInputParam) {
                    xml.append("      <inputParam>\n");
                    xml.append("        <index>").append(index).append("</index>\n");
                    xml.append("        <name>").append(escapeXml(varName)).append("</name>\n");
                    xml.append("        <type>").append(escapeXml(normalizedType)).append("</type>\n");
                    if (defaultValue != null) {
                        xml.append("        <initialValue>").append(escapeXml(defaultValue)).append("</initialValue>\n");
                    }
                    xml.append("      </inputParam>\n");
                }
                index++;
            }

            // localVariable declarations (per NetFactsType, after inputParam/outputParam)
            index = 0;
            for (Map<String, Object> var : variables) {
                String varName = getString(var, "name", null);
                String varType = getString(var, "type", "xs:string");
                String defaultValue = getString(var, "default", null);
                boolean isInputParam = getBoolean(var, "input", true);

                if (varName == null) {
                    continue;
                }

                String normalizedType = normalizeTypeName(varType);

                if (!isInputParam) {
                    xml.append("      <localVariable>\n");
                    xml.append("        <index>").append(index).append("</index>\n");
                    xml.append("        <name>").append(escapeXml(varName)).append("</name>\n");
                    xml.append("        <type>").append(escapeXml(normalizedType)).append("</type>\n");
                    if (defaultValue != null) {
                        xml.append("        <initialValue>").append(escapeXml(defaultValue)).append("</initialValue>\n");
                    }
                    xml.append("      </localVariable>\n");
                }
                index++;
            }
        }

        xml.append("      <processControlElements>\n");

        // Input condition (ExternalConditionFactsType: name?, documentation?, flowsInto+)
        xml.append("        <inputCondition id=\"i-top\">\n");
        xml.append("          <name>start</name>\n");
        if (firstTask != null) {
            xml.append("          <flowsInto><nextElementRef id=\"")
               .append(escapeXml(firstTask)).append("\"/></flowsInto>\n");
        } else if (!tasks.isEmpty()) {
            String firstId = getString(tasks.get(0), "id", null);
            if (firstId != null) {
                xml.append("          <flowsInto><nextElementRef id=\"")
                   .append(escapeXml(firstId)).append("\"/></flowsInto>\n");
            }
        }
        xml.append("        </inputCondition>\n");

        // Tasks - element order per ExternalTaskFactsType:
        //   name?, documentation?, flowsInto+, join, split, defaultConfiguration?,
        //   configuration?, removesTokens*, removesTokensFromFlow*,
        //   startingMappings?, completedMappings?, enablementMappings?,
        //   timer?, resourcing?, customForm?, decomposesTo?
        // Multi-instance tasks extend with: minimum, maximum, threshold,
        //   creationMode, miDataInput, miDataOutput?
        for (Map<String, Object> task : tasks) {
            String taskId = getString(task, "id", null);
            if (taskId == null) {
                continue;
            }

            Map<String, Object> multiInstance = getMap(task, "multiInstance");
            boolean isMiTask = multiInstance != null;

            // Multi-instance tasks use xsi:type to declare the extended type
            if (isMiTask) {
                xml.append("        <task id=\"").append(escapeXml(taskId))
                   .append("\" xsi:type=\"MultipleInstanceExternalTaskFactsType\">\n");
            } else {
                xml.append("        <task id=\"").append(escapeXml(taskId)).append("\">\n");
            }

            // 1. name (optional per schema, but always emit for clarity)
            String taskName = getString(task, "name", taskId);
            xml.append("          <name>").append(escapeXml(taskName)).append("</name>\n");

            // 2. documentation (optional)
            String description = getString(task, "description", null);
            if (description != null) {
                xml.append("          <documentation>").append(escapeXml(description)).append("</documentation>\n");
            }

            // 3. flowsInto+ (required, at least one)
            List<String> flows = getStringList(task, "flows");
            String condition = getString(task, "condition", null);
            String defaultFlow = getString(task, "default", null);

            for (String flow : flows) {
                xml.append("          <flowsInto>\n");
                String targetId = "end".equals(flow) ? "o-top" : flow;
                xml.append("            <nextElementRef id=\"").append(escapeXml(targetId)).append("\"/>\n");

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

                if (defaultFlow != null && flow.equals(defaultFlow)) {
                    xml.append("            <isDefaultFlow/>\n");
                }

                xml.append("          </flowsInto>\n");
            }

            // 4. join (REQUIRED by ExternalTaskFactsType, default to xor)
            String join = getString(task, "join", "xor");
            xml.append("          <join code=\"").append(escapeXml(join)).append("\"/>\n");

            // 5. split (REQUIRED by ExternalTaskFactsType, default to xor)
            String split = getString(task, "split", "xor");
            xml.append("          <split code=\"").append(escapeXml(split)).append("\"/>\n");

            // 6. removesTokens (schema element for cancellation patterns)
            List<String> cancels = getStringList(task, "cancels");
            for (String cancel : cancels) {
                xml.append("          <removesTokens id=\"").append(escapeXml(cancel)).append("\"/>\n");
            }

            // 7. timer (optional, per TimerType: trigger + duration/expiry)
            Map<String, Object> timer = getMap(task, "timer");
            if (timer != null) {
                xml.append("          <timer>\n");
                String trigger = normalizeTimerTrigger(getString(timer, "trigger", "OnEnabled"));
                String duration = getString(timer, "duration", null);
                String expiry = getString(timer, "expiry", null);

                xml.append("            <trigger>").append(trigger).append("</trigger>\n");
                if (duration != null) {
                    xml.append("            <duration>").append(duration).append("</duration>\n");
                } else if (expiry != null) {
                    xml.append("            <expiry>").append(expiry).append("</expiry>\n");
                } else {
                    xml.append("            <duration>PT5M</duration>\n");
                }
                xml.append("          </timer>\n");
            }

            // 8. decomposesTo (optional)
            xml.append("          <decomposesTo id=\"").append(escapeXml(taskId))
               .append("Decomposition\"/>\n");

            // For multi-instance tasks: minimum, maximum, threshold, creationMode, miDataInput
            if (isMiTask) {
                String min = getString(multiInstance, "min", "1");
                String max = getString(multiInstance, "max", "1");
                String threshold = getString(multiInstance, "threshold", "1");
                String mode = getString(multiInstance, "mode", "static");

                xml.append("          <minimum>").append(min).append("</minimum>\n");
                xml.append("          <maximum>").append(max).append("</maximum>\n");
                xml.append("          <threshold>").append(threshold).append("</threshold>\n");
                xml.append("          <creationMode code=\"").append(escapeXml(mode)).append("\"/>\n");

                // miDataInput is required for MI tasks - generate minimal valid structure
                String miVar = getString(multiInstance, "variable", "item");
                String miQuery = getString(multiInstance, "query", "/net/data");
                String miSplitQuery = getString(multiInstance, "splitQuery", "/data/" + miVar);
                xml.append("          <miDataInput>\n");
                xml.append("            <expression query=\"").append(escapeXml(miQuery)).append("\"/>\n");
                xml.append("            <splittingExpression query=\"").append(escapeXml(miSplitQuery)).append("\"/>\n");
                xml.append("            <formalInputParam>").append(escapeXml(miVar)).append("</formalInputParam>\n");
                xml.append("          </miDataInput>\n");
            }

            xml.append("        </task>\n");
        }

        // Output condition (OutputConditionFactsType: name?, documentation?)
        xml.append("        <outputCondition id=\"o-top\">\n");
        xml.append("          <name>end</name>\n");
        xml.append("        </outputCondition>\n");

        xml.append("      </processControlElements>\n");
        xml.append("    </decomposition>\n");

        // Task decomposition definitions (WebServiceGatewayFactsType)
        for (Map<String, Object> task : tasks) {
            String taskId = getString(task, "id", null);
            if (taskId != null) {
                String taskDescription = getString(task, "description", taskId);
                xml.append("    <decomposition id=\"")
                   .append(escapeXml(taskId)).append("Decomposition\" ")
                   .append("xmlns:xsi=\"").append(XSI_NAMESPACE).append("\" ")
                   .append("xsi:type=\"WebServiceGatewayFactsType\">\n");
                xml.append("      <name>").append(escapeXml(taskDescription)).append("</name>\n");
                xml.append("    </decomposition>\n");
            }
        }

        xml.append("  </specification>\n");
        xml.append("</specificationSet>");

        return xml.toString();
    }

    /**
     * Normalize timer trigger value to match XSD TimerTriggerType enumeration.
     * XSD requires PascalCase: "OnEnabled" or "OnExecuting".
     *
     * @param trigger the trigger value from YAML (may be lowercase)
     * @return normalized trigger value matching XSD enumeration
     */
    private String normalizeTimerTrigger(String trigger) {
        if (trigger == null) {
            return "OnEnabled";
        }
        return switch (trigger.toLowerCase(Locale.ROOT)) {
            case "onenabled", "on_enabled", "enabled" -> "OnEnabled";
            case "onexecuting", "on_executing", "executing" -> "OnExecuting";
            default -> trigger;
        };
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

    /**
     * Normalize a type name by stripping namespace prefix.
     * YAWL schema 4.0 requires NCName for type element (e.g., "string" not "xs:string").
     *
     * @param typeName the type name, possibly with namespace prefix
     * @return the normalized type name without namespace prefix
     */
    private String normalizeTypeName(String typeName) {
        if (typeName == null) {
            return "string"; // default type
        }
        // Strip namespace prefix (xs:string -> string, xsd:int -> int, etc.)
        int colonIndex = typeName.lastIndexOf(':');
        if (colonIndex >= 0 && colonIndex < typeName.length() - 1) {
            return typeName.substring(colonIndex + 1);
        }
        return typeName;
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
     * Test method to demonstrate extended YAML conversion.
     * Accepts an optional file path argument to convert a YAML file.
     * If no argument is provided, uses the built-in example.
     *
     * @param args optional first argument is the path to a YAML file to convert
     */
    public static void main(String[] args) {
        String extendedYaml;

        if (args.length > 0) {
            // Read YAML from file
            String filePath = args[0];
            try {
                extendedYaml = java.nio.file.Files.readString(java.nio.file.Path.of(filePath));
                System.out.println("=== YAML Input from: " + filePath + " ===\n");
            } catch (java.io.IOException e) {
                System.err.println("Error reading file: " + filePath + " - " + e.getMessage());
                System.exit(1);
                return;
            }
        } else {
            // Extended YAML example
            extendedYaml = """
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
                      trigger: OnEnabled
                      duration: PT10M

                  - id: ProcessOrder
                    flows: [ShipItems]
                    split: xor
                    join: and
                    description: "Process customer order"

                  - id: ShipItems
                    flows: [end]
                    multiInstance:
                      min: 1
                      max: 10
                      mode: dynamic
                      threshold: 10
                    split: xor
                    join: and
                    description: "Ship order items"

                  - id: CancelOrder
                    flows: [end]
                    split: xor
                    join: xor
                    description: "Cancel order"
                """;
            System.out.println("=== Extended YAML Input ===\n");
        }

        System.out.println(extendedYaml);

        ExtendedYamlConverter converter = new ExtendedYamlConverter();
        String xml = converter.convertToXml(extendedYaml);

        System.out.println("\n=== Extended XML Output ===\n");
        System.out.println(xml);

        System.out.println("\n=== Extended Features (Schema 4.0 Compliant) ===");
        System.out.println("• Variables with types and defaults (inputParam/localVariable)");
        System.out.println("• Timers with duration (OnEnabled/OnExecuting triggers)");
        System.out.println("• Multi-instance tasks (MultipleInstanceExternalTaskFactsType)");
        System.out.println("• Cancellation via removesTokens");
        System.out.println("• Complex conditions with predicates");
        System.out.println("• WebServiceGateway decompositions");
    }
}