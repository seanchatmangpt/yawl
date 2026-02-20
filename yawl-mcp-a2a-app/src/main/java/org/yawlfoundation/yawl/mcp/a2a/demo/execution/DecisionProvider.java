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

package org.yawlfoundation.yawl.mcp.a2a.demo.execution;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.integration.zai.ZaiService;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Interface for workflow routing decisions.
 *
 * <p>Decision providers determine which flow to take at XOR/OR splits
 * based on task ID and case data. Implementations can use various
 * strategies including XPath expressions, random selection, scripts,
 * or LLM-based reasoning.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Register decision providers
 * DecisionProvider xpath = new XPathDecisionProvider(rules);
 * DecisionProvider random = new RandomDecisionProvider();
 * DecisionProvider llm = new LlmDecisionProvider(zaiService);
 *
 * // Make a decision
 * String flow = provider.getDecisionForTask("ApproveOrder", caseData);
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public interface DecisionProvider {

    /**
     * Get the routing decision for a task.
     *
     * @param taskId the task identifier
     * @param caseData the current case data (may be null)
     * @return the chosen flow identifier or target task ID
     */
    String getDecisionForTask(String taskId, Map<String, Object> caseData);

    /**
     * Get the name of this decision provider.
     *
     * @return the provider name for logging/debugging
     */
    default String getName() {
        return getClass().getSimpleName();
    }

    /**
     * Check if this provider can handle the given task.
     *
     * @param taskId the task identifier
     * @return true if this provider can make decisions for the task
     */
    default boolean canHandle(String taskId) {
        return true;
    }

    // ==================== Implementations ====================

    /**
     * XPath-based decision provider.
     *
     * <p>Uses XPath expressions to evaluate case data and determine routing.
     * Rules are specified as a map of task IDs to XPath expressions that
     * return the target flow identifier.</p>
     *
     * <h3>Rule Format</h3>
     * <pre>{@code
     * Map<String, String> rules = Map.of(
     *     "ApproveOrder", "/case/order/amount > 1000 -> 'ManagerApproval' : 'AutoApprove'",
     *     "CheckCredit", "/case/customer/creditScore >= 700 -> 'Approved' : 'ManualReview'"
     * );
     * }</pre>
     */
    class XPathDecisionProvider implements DecisionProvider {

        private static final Logger LOGGER = LoggerFactory.getLogger(XPathDecisionProvider.class);

        private final Map<String, CompiledRule> compiledRules;
        private final XPath xpath;

        /**
         * Creates an XPath decision provider with the given rules.
         *
         * @param rules map of task IDs to XPath expressions
         */
        public XPathDecisionProvider(Map<String, String> rules) {
            this.xpath = XPathFactory.newInstance().newXPath();
            this.compiledRules = new ConcurrentHashMap<>();

            if (rules != null) {
                rules.forEach((taskId, expression) -> {
                    try {
                        compiledRules.put(taskId, compileRule(expression));
                    } catch (Exception e) {
                        LOGGER.warn("Failed to compile XPath rule for task {}: {}", taskId, e.getMessage());
                    }
                });
            }
        }

        /**
         * Creates an XPath decision provider with rules from a string.
         *
         * <p>Format: "taskId: expression; taskId2: expression2"</p>
         *
         * @param rulesString the rules in string format
         */
        public XPathDecisionProvider(String rulesString) {
            this(parseRulesFromString(rulesString));
        }

        private CompiledRule compileRule(String expression) {
            // Parse conditional expression: "condition -> 'trueValue' : 'falseValue'"
            Pattern pattern = Pattern.compile(
                "(.+?)\\s*->\\s*'([^']+)'\\s*:\\s*'([^']+)'"
            );
            Matcher matcher = pattern.matcher(expression.trim());

            if (matcher.matches()) {
                String condition = matcher.group(1).trim();
                String trueValue = matcher.group(2);
                String falseValue = matcher.group(3);

                try {
                    XPathExpression expr = xpath.compile(condition);
                    return new ConditionalRule(expr, trueValue, falseValue);
                } catch (XPathExpressionException e) {
                    LOGGER.warn("Failed to compile XPath condition: {}", condition);
                    return new ConstantRule(expression);
                }
            }

            // Treat as constant expression
            try {
                XPathExpression expr = xpath.compile(expression);
                return new SimpleRule(expr);
            } catch (XPathExpressionException e) {
                LOGGER.warn("Failed to compile XPath expression: {}", expression);
                return new ConstantRule(expression);
            }
        }

        private static Map<String, String> parseRulesFromString(String rulesString) {
            if (rulesString == null || rulesString.isBlank()) {
                return Collections.emptyMap();
            }

            Map<String, String> rules = new HashMap<>();
            String[] parts = rulesString.split(";");

            for (String part : parts) {
                String trimmed = part.trim();
                int colonIndex = trimmed.indexOf(':');
                if (colonIndex > 0) {
                    String taskId = trimmed.substring(0, colonIndex).trim();
                    String expression = trimmed.substring(colonIndex + 1).trim();
                    rules.put(taskId, expression);
                }
            }

            return rules;
        }

        @Override
        public String getDecisionForTask(String taskId, Map<String, Object> caseData) {
            CompiledRule rule = compiledRules.get(taskId);

            if (rule == null) {
                LOGGER.debug("No rule found for task {}, returning default", taskId);
                return "defaultFlow";
            }

            try {
                // Convert case data to XML for XPath evaluation
                String xmlData = convertToXml(caseData);
                return rule.evaluate(xmlData);
            } catch (Exception e) {
                LOGGER.error("Error evaluating XPath rule for task {}: {}", taskId, e.getMessage());
                return "defaultFlow";
            }
        }

        @Override
        public boolean canHandle(String taskId) {
            return compiledRules.containsKey(taskId);
        }

        private String convertToXml(Map<String, Object> data) {
            if (data == null || data.isEmpty()) {
                return "<case/>";
            }

            StringBuilder xml = new StringBuilder("<case>");
            data.forEach((key, value) -> {
                xml.append("<").append(key).append(">");
                if (value != null) {
                    xml.append(escapeXml(value.toString()));
                }
                xml.append("</").append(key).append(">");
            });
            xml.append("</case>");
            return xml.toString();
        }

        private String escapeXml(String value) {
            return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
        }

        // Inner interfaces and classes for rule handling
        private interface CompiledRule {
            String evaluate(String xmlData) throws XPathExpressionException;
        }

        private record ConditionalRule(XPathExpression condition, String trueValue, String falseValue)
                implements CompiledRule {
            @Override
            public String evaluate(String xmlData) throws XPathExpressionException {
                Boolean result = (Boolean) condition.evaluate(xmlData, XPathConstants.BOOLEAN);
                return result ? trueValue : falseValue;
            }
        }

        private record SimpleRule(XPathExpression expression) implements CompiledRule {
            @Override
            public String evaluate(String xmlData) throws XPathExpressionException {
                Object result = expression.evaluate(xmlData, XPathConstants.STRING);
                return result != null ? result.toString() : "defaultFlow";
            }
        }

        private record ConstantRule(String value) implements CompiledRule {
            @Override
            public String evaluate(String xmlData) {
                return value;
            }
        }
    }

    /**
     * Random decision provider for testing purposes.
     *
     * <p>Selects randomly from available flows. Useful for testing
     * workflow behavior under different routing scenarios.</p>
     */
    class RandomDecisionProvider implements DecisionProvider {

        private static final Logger LOGGER = LoggerFactory.getLogger(RandomDecisionProvider.class);

        private final Random random;
        private final List<String> availableFlows;
        private final Map<String, List<String>> taskFlows;

        /**
         * Creates a random decision provider with default flows.
         */
        public RandomDecisionProvider() {
            this(List.of("flow1", "flow2"));
        }

        /**
         * Creates a random decision provider with specific flows.
         *
         * @param flows the list of possible flow outcomes
         */
        public RandomDecisionProvider(List<String> flows) {
            this.random = new Random();
            this.availableFlows = new ArrayList<>(flows != null ? flows : List.of("flow1", "flow2"));
            this.taskFlows = new ConcurrentHashMap<>();
        }

        /**
         * Register specific flows for a task.
         *
         * @param taskId the task identifier
         * @param flows the available flows for this task
         */
        public void registerFlowsForTask(String taskId, List<String> flows) {
            if (taskId != null && flows != null && !flows.isEmpty()) {
                taskFlows.put(taskId, new ArrayList<>(flows));
            }
        }

        @Override
        public String getDecisionForTask(String taskId, Map<String, Object> caseData) {
            List<String> flows = taskFlows.getOrDefault(taskId, availableFlows);

            if (flows.isEmpty()) {
                return "defaultFlow";
            }

            String decision = flows.get(random.nextInt(flows.size()));
            LOGGER.debug("Random decision for task {}: {}", taskId, decision);
            return decision;
        }

        @Override
        public String getName() {
            return "RandomDecisionProvider";
        }
    }

    /**
     * Script-based decision provider using javax.script.
     *
     * <p>Evaluates scripts (JavaScript, Groovy) to make routing decisions.
     * The script has access to caseData as a variable and should return
     * the target flow identifier.</p>
     *
     * <h3>Script Format</h3>
     * <pre>{@code
     * // JavaScript example
     * "if (caseData.amount > 1000) 'ManagerApproval' else 'AutoApprove'"
     *
     * // Groovy example
     * "caseData?.creditScore >= 700 ? 'Approved' : 'ManualReview'"
     * }</pre>
     */
    class ScriptDecisionProvider implements DecisionProvider {

        private static final Logger LOGGER = LoggerFactory.getLogger(ScriptDecisionProvider.class);

        private final Map<String, ScriptRule> scriptRules;
        private final String defaultLanguage;

        /**
         * Creates a script decision provider with JavaScript as default.
         */
        public ScriptDecisionProvider() {
            this("JavaScript");
        }

        /**
         * Creates a script decision provider with a specific language.
         *
         * @param defaultLanguage the script language (e.g., "JavaScript", "groovy")
         */
        public ScriptDecisionProvider(String defaultLanguage) {
            this.defaultLanguage = Objects.requireNonNullElse(defaultLanguage, "JavaScript");
            this.scriptRules = new ConcurrentHashMap<>();
        }

        /**
         * Register a script rule for a task.
         *
         * @param taskId the task identifier
         * @param script the script expression
         */
        public void registerRule(String taskId, String script) {
            registerRule(taskId, script, defaultLanguage);
        }

        /**
         * Register a script rule for a task with a specific language.
         *
         * @param taskId the task identifier
         * @param script the script expression
         * @param language the script language
         */
        public void registerRule(String taskId, String script, String language) {
            if (taskId != null && script != null) {
                scriptRules.put(taskId, new ScriptRule(script, language));
            }
        }

        /**
         * Register multiple rules from a map.
         *
         * @param rules map of task IDs to script expressions
         */
        public void registerRules(Map<String, String> rules) {
            if (rules != null) {
                rules.forEach(this::registerRule);
            }
        }

        @Override
        public String getDecisionForTask(String taskId, Map<String, Object> caseData) {
            ScriptRule rule = scriptRules.get(taskId);

            if (rule == null) {
                LOGGER.debug("No script rule found for task {}, returning default", taskId);
                return "defaultFlow";
            }

            try {
                javax.script.ScriptEngineManager manager = new javax.script.ScriptEngineManager();
                javax.script.ScriptEngine engine = manager.getEngineByName(rule.language());

                if (engine == null) {
                    LOGGER.warn("Script engine not found for language: {}", rule.language());
                    return "defaultFlow";
                }

                // Bind case data
                javax.script.Bindings bindings = engine.createBindings();
                bindings.put("caseData", caseData != null ? caseData : Collections.emptyMap());
                bindings.put("taskId", taskId);

                Object result = engine.eval(rule.script(), bindings);

                if (result != null) {
                    return result.toString();
                }

                return "defaultFlow";

            } catch (javax.script.ScriptException e) {
                LOGGER.error("Script evaluation error for task {}: {}", taskId, e.getMessage());
                return "defaultFlow";
            }
        }

        @Override
        public boolean canHandle(String taskId) {
            return scriptRules.containsKey(taskId);
        }

        @Override
        public String getName() {
            return "ScriptDecisionProvider[" + defaultLanguage + "]";
        }

        private record ScriptRule(String script, String language) {}
    }

    /**
     * LLM-based decision provider using Z.AI for intelligent routing.
     *
     * <p>Uses an LLM to analyze case data and make context-aware routing
     * decisions. Requires ZAI_API_KEY environment variable.</p>
     *
     * <h3>Features</h3>
     * <ul>
     *   <li>Context-aware decision making</li>
     *   <li>Natural language reasoning</li>
     *   <li>Customizable prompts per task</li>
     *   <li>Fallback to default on LLM errors</li>
     * </ul>
     */
    class LlmDecisionProvider implements DecisionProvider {

        private static final Logger LOGGER = LoggerFactory.getLogger(LlmDecisionProvider.class);

        private final ZaiService zaiService;
        private final Map<String, TaskConfig> taskConfigs;
        private final String defaultFlow;
        private final boolean enabled;

        /**
         * Creates an LLM decision provider.
         *
         * @param zaiService the Z.AI service for LLM calls
         */
        public LlmDecisionProvider(ZaiService zaiService) {
            this(zaiService, "defaultFlow");
        }

        /**
         * Creates an LLM decision provider with a custom default flow.
         *
         * @param zaiService the Z.AI service
         * @param defaultFlow the default flow when LLM cannot decide
         */
        public LlmDecisionProvider(ZaiService zaiService, String defaultFlow) {
            this.zaiService = zaiService;
            this.defaultFlow = Objects.requireNonNullElse(defaultFlow, "defaultFlow");
            this.taskConfigs = new ConcurrentHashMap<>();
            this.enabled = zaiService != null;
        }

        /**
         * Configure a task with available options and optional custom prompt.
         *
         * @param taskId the task identifier
         * @param options the available flow options
         */
        public void configureTask(String taskId, List<String> options) {
            configureTask(taskId, options, null);
        }

        /**
         * Configure a task with options and custom prompt.
         *
         * @param taskId the task identifier
         * @param options the available flow options
         * @param customPrompt custom prompt template (use {data} and {options} placeholders)
         */
        public void configureTask(String taskId, List<String> options, String customPrompt) {
            if (taskId != null && options != null && !options.isEmpty()) {
                taskConfigs.put(taskId, new TaskConfig(options, customPrompt));
            }
        }

        @Override
        public String getDecisionForTask(String taskId, Map<String, Object> caseData) {
            if (!enabled) {
                LOGGER.warn("LLM decision provider not enabled (no ZaiService)");
                return defaultFlow;
            }

            TaskConfig config = taskConfigs.get(taskId);
            List<String> options = config != null ? config.options() : List.of("flow1", "flow2");

            try {
                String prompt = buildPrompt(taskId, caseData, options, config);
                String response = zaiService.chat(prompt);

                // Extract decision from response
                return extractDecision(response, options);

            } catch (Exception e) {
                LOGGER.error("LLM decision error for task {}: {}", taskId, e.getMessage());
                return defaultFlow;
            }
        }

        @Override
        public boolean canHandle(String taskId) {
            return enabled;
        }

        @Override
        public String getName() {
            return "LlmDecisionProvider";
        }

        private String buildPrompt(String taskId, Map<String, Object> caseData,
                                   List<String> options, TaskConfig config) {
            if (config != null && config.customPrompt() != null) {
                return config.customPrompt()
                    .replace("{taskId}", taskId)
                    .replace("{data}", formatData(caseData))
                    .replace("{options}", formatOptions(options));
            }

            return String.format("""
                You are a workflow routing decision engine.

                Task: %s
                Case Data: %s
                Available Options: %s

                Based on the case data, which option should be chosen?
                Respond with ONLY the option name, no explanation.

                Option:""", taskId, formatData(caseData), formatOptions(options));
        }

        private String formatData(Map<String, Object> data) {
            if (data == null || data.isEmpty()) {
                return "{}";
            }
            return data.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", ", "{", "}"));
        }

        private String formatOptions(List<String> options) {
            return String.join(", ", options);
        }

        private String extractDecision(String response, List<String> options) {
            if (response == null || response.isBlank()) {
                return defaultFlow;
            }

            String cleaned = response.trim().toLowerCase();

            // Find matching option (case-insensitive)
            for (String option : options) {
                if (cleaned.contains(option.toLowerCase())) {
                    return option;
                }
            }

            // If no match, return first option as safest default
            return options.get(0);
        }

        private record TaskConfig(List<String> options, String customPrompt) {}
    }
}
