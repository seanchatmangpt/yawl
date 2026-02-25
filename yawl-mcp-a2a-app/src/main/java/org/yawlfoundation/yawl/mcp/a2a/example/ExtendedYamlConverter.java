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

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final Set<String> XQUERY_KEYWORDS = Set.of(
            "and", "or", "not", "true", "false", "eq", "ne", "lt", "gt", "le", "ge",
            "div", "mod", "if", "then", "else", "for", "let", "return", "some", "every"
    );

    public ExtendedYamlConverter() {
        super();
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
        String netName = name + "Net";
        String uri = getString(spec, "uri", name + ".xml");
        String firstTask = getString(spec, "first", null);
        List<Map<String, Object>> tasks = getTasks(spec);
        List<Map<String, Object>> variables = getVariables(spec);

        // Collect variable names for XQuery conversion
        Set<String> varNames = new LinkedHashSet<>();
        if (variables != null) {
            for (Map<String, Object> var : variables) {
                String vn = getString(var, "name", null);
                if (vn != null) varNames.add(vn);
            }
        }

        // Pass 1: Build condition lookup map (targetTaskId -> predicate expression)
        // Conditions on target tasks ("condition: expr -> Self") need to be applied
        // as predicates on the flow FROM the source task TO this target.
        Map<String, String> targetConditionMap = new LinkedHashMap<>();
        for (Map<String, Object> task : tasks) {
            String condition = getString(task, "condition", null);
            if (condition != null && condition.contains("->")) {
                String[] parts = condition.split("->");
                if (parts.length == 2) {
                    String target = parts[1].trim();
                    String expr = parts[0].trim();
                    targetConditionMap.put(target, expr);
                }
            }
        }

        xml.append("<specificationSet xmlns=\"").append(NAMESPACE).append("\" ")
           .append("xmlns:xsi=\"").append(XSI_NAMESPACE).append("\" ")
           .append("xsi:schemaLocation=\"").append(NAMESPACE).append(" ").append(NAMESPACE).append("/YAWL_Schema4.0.xsd\" ")
           .append("version=\"4.0\">\n");
        xml.append("  <specification uri=\"").append(escapeXml(uri)).append("\">\n");

        xml.append("    <name>").append(escapeXml(name)).append("</name>\n");

        String specDescription = getString(spec, "description", null);
        if (specDescription != null) {
            xml.append("    <documentation>").append(escapeXml(specDescription)).append("</documentation>\n");
        }

        xml.append("    <metaData/>\n");

        xml.append("    <decomposition id=\"").append(escapeXml(netName)).append("\" ")
           .append("isRootNet=\"true\" ")
           .append("xmlns:xsi=\"").append(XSI_NAMESPACE).append("\" ")
           .append("xsi:type=\"NetFactsType\">\n");
        xml.append("      <name>").append(escapeXml(netName)).append("</name>\n");

        // inputParam declarations
        if (variables != null && !variables.isEmpty()) {
            int index = 0;
            for (Map<String, Object> var : variables) {
                String varName = getString(var, "name", null);
                String varType = getString(var, "type", "xs:string");
                String defaultValue = getString(var, "default", null);
                boolean isInputParam = getBoolean(var, "input", true);
                if (varName == null) continue;
                String normalizedType = normalizeTypeName(varType);
                if (isInputParam) {
                    xml.append("      <inputParam>\n");
                    xml.append("        <index>").append(index).append("</index>\n");
                    xml.append("        <name>").append(escapeXml(varName)).append("</name>\n");
                    xml.append("        <type>").append(escapeXml(normalizedType)).append("</type>\n");
                    if (defaultValue != null) {
                        xml.append("        <initialValue>").append(escapeXml(String.valueOf(defaultValue))).append("</initialValue>\n");
                    }
                    xml.append("      </inputParam>\n");
                }
                index++;
            }

            // localVariable declarations
            index = 0;
            for (Map<String, Object> var : variables) {
                String varName = getString(var, "name", null);
                String varType = getString(var, "type", "xs:string");
                String defaultValue = getString(var, "default", null);
                boolean isInputParam = getBoolean(var, "input", true);
                if (varName == null) continue;
                String normalizedType = normalizeTypeName(varType);
                if (!isInputParam) {
                    xml.append("      <localVariable>\n");
                    xml.append("        <index>").append(index).append("</index>\n");
                    xml.append("        <name>").append(escapeXml(varName)).append("</name>\n");
                    xml.append("        <type>").append(escapeXml(normalizedType)).append("</type>\n");
                    if (defaultValue != null) {
                        xml.append("        <initialValue>").append(escapeXml(String.valueOf(defaultValue))).append("</initialValue>\n");
                    }
                    xml.append("      </localVariable>\n");
                }
                index++;
            }
        }

        xml.append("      <processControlElements>\n");

        // Input condition
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

        // Pass 2: Generate tasks with proper condition resolution
        for (Map<String, Object> task : tasks) {
            String taskId = getString(task, "id", null);
            if (taskId == null) continue;

            Map<String, Object> multiInstance = getMap(task, "multiInstance");
            boolean isMiTask = multiInstance != null;

            if (isMiTask) {
                xml.append("        <task id=\"").append(escapeXml(taskId))
                   .append("\" xsi:type=\"MultipleInstanceExternalTaskFactsType\">\n");
            } else {
                xml.append("        <task id=\"").append(escapeXml(taskId)).append("\">\n");
            }

            String taskName = getString(task, "name", taskId);
            xml.append("          <name>").append(escapeXml(taskName)).append("</name>\n");

            String description = getString(task, "description", null);
            if (description != null) {
                xml.append("          <documentation>").append(escapeXml(description)).append("</documentation>\n");
            }

            // flowsInto with condition resolution
            List<String> flows = getStringList(task, "flows");
            String condition = getString(task, "condition", null);
            String defaultFlow = getString(task, "default", null);
            String split = normalizeSplitJoin(getString(task, "split", "xor"));

            // For XOR/OR splits, ensure default flow is set
            if (("xor".equals(split) || "or".equals(split)) && defaultFlow == null && !flows.isEmpty()) {
                defaultFlow = flows.getLast();
            }

            // Parse this task's own condition (source-specified)
            String sourceCondExpr = null;
            String sourceCondTarget = null;
            if (condition != null && condition.contains("->")) {
                String[] parts = condition.split("->");
                if (parts.length == 2) {
                    sourceCondExpr = parts[0].trim();
                    sourceCondTarget = parts[1].trim();
                }
            }

            for (String flow : flows) {
                xml.append("          <flowsInto>\n");
                String targetId = "end".equals(flow) ? "o-top" : flow;
                xml.append("            <nextElementRef id=\"").append(escapeXml(targetId)).append("\"/>\n");

                // Resolve predicate: check source condition first, then target condition map
                String predicate = null;
                if (sourceCondExpr != null && flow.equals(sourceCondTarget)) {
                    predicate = sourceCondExpr;
                } else if (targetConditionMap.containsKey(flow)) {
                    predicate = targetConditionMap.get(flow);
                }

                if (predicate != null) {
                    String xquery = convertToXQuery(predicate, netName, varNames);
                    xml.append("            <predicate>")
                       .append(escapeXml(xquery))
                       .append("</predicate>\n");
                } else if (("xor".equals(split) || "or".equals(split))
                        && (defaultFlow == null || !flow.equals(defaultFlow))) {
                    // In XOR/OR splits, flows without predicates cause NPE in engine evaluation.
                    // Use false() so the flow is safely unreachable (handles lost YAML duplicate-key conditions).
                    xml.append("            <predicate>false()</predicate>\n");
                }

                if (defaultFlow != null && flow.equals(defaultFlow)) {
                    xml.append("            <isDefaultFlow/>\n");
                }

                xml.append("          </flowsInto>\n");
            }

            // join and split with normalization
            String join = normalizeSplitJoin(getString(task, "join", "xor"));
            xml.append("          <join code=\"").append(escapeXml(join)).append("\"/>\n");
            xml.append("          <split code=\"").append(escapeXml(split)).append("\"/>\n");

            // removesTokens: support both "cancels" and "cancelRegion" keys
            List<String> cancels = getStringList(task, "cancels");
            List<String> cancelRegion = getStringList(task, "cancelRegion");
            for (String cancel : cancels) {
                xml.append("          <removesTokens id=\"").append(escapeXml(cancel)).append("\"/>\n");
            }
            for (String cancel : cancelRegion) {
                xml.append("          <removesTokens id=\"").append(escapeXml(cancel)).append("\"/>\n");
            }

            // completedMappings: variable updates applied when the task completes
            Map<String, Object> outputs = getMap(task, "outputs");
            if (outputs != null && !outputs.isEmpty()) {
                xml.append("          <completedMappings>\n");
                for (Map.Entry<String, Object> entry : outputs.entrySet()) {
                    String varName = entry.getKey();
                    String expr = String.valueOf(entry.getValue());
                    String xquery = "<" + varName + ">{" + expr + "}</" + varName + ">";
                    xml.append("            <mapping>\n");
                    xml.append("              <expression query=\"")
                       .append(escapeXml(xquery)).append("\"/>\n");
                    xml.append("              <mapsTo>").append(escapeXml(varName))
                       .append("</mapsTo>\n");
                    xml.append("            </mapping>\n");
                }
                xml.append("          </completedMappings>\n");
            }

            // timer
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

            // decomposesTo
            xml.append("          <decomposesTo id=\"").append(escapeXml(taskId))
               .append("Decomposition\"/>\n");

            // Multi-instance attributes
            if (isMiTask) {
                String min = getString(multiInstance, "min", "1");
                String max = getString(multiInstance, "max", "1");
                String threshold = normalizeMiThreshold(
                        getString(multiInstance, "threshold", "1"), max);
                String mode = normalizeMiMode(getString(multiInstance, "mode", "static"));

                xml.append("          <minimum>").append(min).append("</minimum>\n");
                xml.append("          <maximum>").append(max).append("</maximum>\n");
                xml.append("          <threshold>").append(threshold).append("</threshold>\n");
                xml.append("          <creationMode code=\"").append(escapeXml(mode)).append("\"/>\n");

                // miDataInput is required for MI tasks - generate minimal valid structure.
                // Default expression selects net root (e.g. /MIStaticPattern) and splits
                // by all child elements (*), which creates one instance per net variable.
                String miVar = getString(multiInstance, "variable", "item");
                String miQuery = getString(multiInstance, "query", "/" + netName);
                String miSplitQuery = getString(multiInstance, "splitQuery", "*");
                xml.append("          <miDataInput>\n");
                xml.append("            <expression query=\"").append(escapeXml(miQuery)).append("\"/>\n");
                xml.append("            <splittingExpression query=\"").append(escapeXml(miSplitQuery)).append("\"/>\n");
                xml.append("            <formalInputParam>").append(escapeXml(miVar)).append("</formalInputParam>\n");
                xml.append("          </miDataInput>\n");
            }

            xml.append("        </task>\n");
        }

        // Output condition
        xml.append("        <outputCondition id=\"o-top\">\n");
        xml.append("          <name>end</name>\n");
        xml.append("        </outputCondition>\n");

        xml.append("      </processControlElements>\n");
        xml.append("    </decomposition>\n");

        // Task decomposition definitions
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

    /**
     * Convert a Java-style predicate expression to valid XQuery.
     * Handles: == to =, && to and, || to or, bare variable names to XPath paths,
     * and boolean/string literals.
     */
    String convertToXQuery(String predicate, String netName, Set<String> varNames) {
        if (predicate == null || predicate.isBlank()) return "true()";

        // Replace Java operators with XQuery equivalents
        String xq = predicate;
        // == must be replaced before single = (handle != first to avoid mangling)
        xq = xq.replace("!=", " ne ");
        xq = xq.replace("==", " = ");
        xq = xq.replace("&&", " and ");
        xq = xq.replace("||", " or ");
        // >= and <= are valid in XQuery, leave them

        // Tokenize and rebuild, replacing variable references with XPath paths
        StringBuilder result = new StringBuilder();
        // Match: quoted strings, numbers, operators, identifiers
        Pattern tokenPattern = Pattern.compile(
                "\"[^\"]*\"|'[^']*'|\\d+\\.?\\d*|>=|<=|!=|[=<>]|\\b[a-zA-Z_][a-zA-Z0-9_]*\\b|\\S");
        Matcher m = tokenPattern.matcher(xq);
        int lastEnd = 0;
        while (m.find()) {
            // Preserve whitespace between tokens
            result.append(xq, lastEnd, m.start());
            String token = m.group();
            if (token.startsWith("\"") || token.startsWith("'")) {
                // String literal — keep as-is
                result.append(token);
            } else if (token.matches("\\d+\\.?\\d*")) {
                // Numeric literal
                result.append(token);
            } else if (varNames.contains(token)) {
                // Variable reference — convert to XPath
                result.append("/").append(netName).append("/").append(token).append("/text()");
            } else if ("true".equals(token)) {
                result.append("'true'");
            } else if ("false".equals(token)) {
                result.append("'false'");
            } else {
                result.append(token);
            }
            lastEnd = m.end();
        }
        result.append(xq, lastEnd, xq.length());
        return result.toString();
    }

    /**
     * Normalize join/split type to engine-supported values (and, or, xor).
     * Maps unsupported types like 'discriminator' and 'partial' to nearest equivalents.
     */
    private String normalizeSplitJoin(String type) {
        if (type == null) return "xor";
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "and", "or", "xor" -> type.toLowerCase(Locale.ROOT);
            case "discriminator" -> "xor";
            case "partial" -> "or";
            default -> "xor";
        };
    }

    /**
     * Normalize MI threshold values. Converts symbolic values like "all", "first",
     * "dynamic" to numeric equivalents based on the max instance count.
     */
    private String normalizeMiThreshold(String threshold, String max) {
        if (threshold == null) return "1";
        return switch (threshold.toLowerCase(Locale.ROOT)) {
            case "all" -> max;
            case "first" -> "1";
            case "dynamic" -> max;
            default -> threshold;
        };
    }

    /**
     * Normalize MI creation mode to YAWL schema-valid values (static, dynamic).
     * Maps extended modes like 'sequential' and 'concurrent' to valid equivalents.
     */
    private String normalizeMiMode(String mode) {
        if (mode == null) return "static";
        return switch (mode.toLowerCase(Locale.ROOT)) {
            case "static", "dynamic" -> mode.toLowerCase(Locale.ROOT);
            case "sequential" -> "static";
            case "concurrent" -> "dynamic";
            default -> "static";
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