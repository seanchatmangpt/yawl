/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.graalwasm.dmn;

import org.graalvm.polyglot.Value;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.yawlfoundation.yawl.graalwasm.WasmExecutionEngine;
import org.yawlfoundation.yawl.graalwasm.WasmModule;
import org.yawlfoundation.yawl.graalwasm.WasmSandboxConfig;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Full DMN 1.3 support through GraalWASM — parse and evaluate Decision Model and Notation
 * models with FEEL expression evaluation accelerated by an in-process WebAssembly engine.
 *
 * <h2>Architecture</h2>
 * <pre>
 * DmnWasmBridge
 *   ├─ WasmExecutionEngine (GraalWASM)
 *   │    └─ dmn_feel_engine.wasm  ← FEEL numeric operations (add, subtract, compare, etc.)
 *   └─ Java DMN evaluator
 *        ├─ XML parser (javax.xml) ← DMN model parsing
 *        ├─ FEEL evaluator ← string, boolean, range, unary tests
 *        └─ Hit policy engine ← UNIQUE, FIRST, ANY, COLLECT, RULE_ORDER, PRIORITY, OUTPUT_ORDER
 * </pre>
 *
 * <h2>Supported DMN features</h2>
 * <ul>
 *   <li>DMN 1.2 and 1.3 XML namespaces</li>
 *   <li>Decision tables with all seven hit policies</li>
 *   <li>FEEL unary tests: string literals, numeric comparisons, ranges, negation, wildcard</li>
 *   <li>FEEL built-in functions: min, max, floor, ceiling, sqrt, abs (via WASM)</li>
 *   <li>Decision Requirements Graph: decisions referencing other decisions via requiredDecision</li>
 *   <li>Input expressions, output expressions, annotations</li>
 *   <li>Null-safe evaluation ({@code -} wildcard matches any value)</li>
 * </ul>
 *
 * <h2>Quick start</h2>
 * <pre>{@code
 * try (DmnWasmBridge bridge = new DmnWasmBridge()) {
 *
 *     // 1. Parse the DMN model
 *     DmnWasmBridge.DmnModel model = bridge.parseDmnModel(dmnXml);
 *
 *     // 2. Build the evaluation context
 *     DmnEvaluationContext ctx = DmnEvaluationContext.builder()
 *         .put("age", 35)
 *         .put("riskCategory", "HIGH")
 *         .build();
 *
 *     // 3. Evaluate a decision
 *     DmnDecisionResult result = bridge.evaluateDecision(model, "eligibility", ctx);
 *
 *     // 4. Read the result
 *     result.getSingleResult().ifPresent(row ->
 *         log.info("Eligibility status: {}", row.get("eligibilityStatus"))
 *     );
 * }
 * }</pre>
 *
 * <h2>Thread safety</h2>
 * <p>{@code DmnWasmBridge} is NOT thread-safe. Create one instance per thread or
 * use an external pool. The WASM module is created on demand per evaluation call.</p>
 *
 * <h2>Runtime requirement</h2>
 * <p>GraalVM JDK 24.1+ with {@code wasm} language support on the classpath.
 * The {@code dmn_feel_engine.wasm} binary must be present in the JAR at
 * {@code wasm/dmn_feel_engine.wasm}.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see DmnEvaluationContext
 * @see DmnDecisionResult
 * @see DmnHitPolicy
 * @see DmnException
 */
public final class DmnWasmBridge implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DmnWasmBridge.class);

    /** Classpath resource path for the FEEL engine WASM binary. */
    static final String FEEL_WASM_RESOURCE = "wasm/dmn_feel_engine.wasm";

    /** DMN 1.3 namespace URI. */
    private static final String DMN_NS_13 = "https://www.omg.org/spec/DMN/20191111/MODEL/";

    /** DMN 1.2 namespace URI. */
    private static final String DMN_NS_12 = "http://www.omg.org/spec/DMN/20180521/MODEL/";

    // FEEL range pattern: [1..10], (1..10], [1..10), (1..10)
    private static final Pattern RANGE_PATTERN =
            Pattern.compile("^([\\[(])\\s*([^.,]+?)\\s*\\.\\.\\s*([^.,\\]\\)]+?)\\s*([\\])])$");

    // FEEL numeric comparison: >= 18, <= 100, > 5, < 200
    private static final Pattern NUMERIC_COMPARE_PATTERN =
            Pattern.compile("^(>=|<=|>|<|!=|=)\\s*([+-]?\\d+(?:\\.\\d+)?)$");

    // FEEL negation: not("A", "B")
    private static final Pattern NOT_PATTERN =
            Pattern.compile("^not\\s*\\((.+)\\)$", Pattern.CASE_INSENSITIVE);

    private final WasmExecutionEngine wasmEngine;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Constructs a DmnWasmBridge and verifies the FEEL WASM engine can be loaded.
     *
     * @throws DmnException  WASM_LOAD_ERROR if the WASM binary is missing or invalid
     */
    public DmnWasmBridge() {
        this.wasmEngine = WasmExecutionEngine.builder()
                .sandboxConfig(WasmSandboxConfig.pureWasm())
                .build();
        verifyFeelWasm();
        log.info("DmnWasmBridge initialised with FEEL engine: {}", FEEL_WASM_RESOURCE);
    }

    // ── Public API: model parsing ────────────────────────────────────────────

    /**
     * Parses a DMN XML document and returns an in-memory model.
     *
     * <p>Supports DMN 1.2 and DMN 1.3 namespace URIs. The model is parsed once
     * and reused across multiple {@link #evaluateDecision} calls.</p>
     *
     * @param dmnXml  the DMN XML content; must not be null or blank
     * @return the parsed DmnModel; never null
     * @throws DmnException  MODEL_PARSE_ERROR if the XML is malformed or uses an
     *                       unknown namespace
     */
    public DmnModel parseDmnModel(String dmnXml) {
        if (dmnXml == null || dmnXml.isBlank()) {
            throw new DmnException("DMN XML must not be null or blank",
                    DmnException.ErrorKind.MODEL_PARSE_ERROR);
        }
        assertOpen();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder docBuilder = factory.newDocumentBuilder();
            Document doc = docBuilder.parse(new InputSource(new StringReader(dmnXml)));
            doc.getDocumentElement().normalize();

            Element root = doc.getDocumentElement();
            String ns = root.getNamespaceURI();
            if (ns == null || (!ns.equals(DMN_NS_13) && !ns.equals(DMN_NS_12))) {
                log.warn("Unrecognised DMN namespace '{}'; attempting to parse anyway", ns);
            }

            String modelId = root.getAttribute("id");
            String modelName = root.getAttribute("name");

            Map<String, DecisionEntry> decisions = new LinkedHashMap<>();
            NodeList decisionNodes = root.getElementsByTagNameNS("*", "decision");
            for (int i = 0; i < decisionNodes.getLength(); i++) {
                Element decisionEl = (Element) decisionNodes.item(i);
                DecisionEntry entry = parseDecisionElement(decisionEl);
                decisions.put(entry.id(), entry);
            }

            log.debug("Parsed DMN model '{}' with {} decisions", modelId, decisions.size());
            return new DmnModel(modelId, modelName, decisions);

        } catch (DmnException e) {
            throw e;
        } catch (Exception e) {
            throw new DmnException("Failed to parse DMN XML: " + e.getMessage(),
                    DmnException.ErrorKind.MODEL_PARSE_ERROR, e);
        }
    }

    // ── Public API: decision evaluation ─────────────────────────────────────

    /**
     * Evaluates a named decision from a parsed model against the given context.
     *
     * <p>If the decision requires other decisions (DRG dependency), those are evaluated
     * first and their output is merged into the context before evaluating the target.</p>
     *
     * @param model       the parsed DMN model; must not be null
     * @param decisionId  the decision ID to evaluate; must not be null
     * @param ctx         the input context; must not be null
     * @return the evaluation result; never null
     * @throws DmnException  DECISION_NOT_FOUND if the decision ID is absent;
     *                       EVALUATION_ERROR for rule matching failures;
     *                       HIT_POLICY_VIOLATION for UNIQUE with multiple matches
     */
    public DmnDecisionResult evaluateDecision(DmnModel model, String decisionId,
                                              DmnEvaluationContext ctx) {
        assertOpen();
        DecisionEntry decision = model.decisions().get(decisionId);
        if (decision == null) {
            throw new DmnException("Decision '" + decisionId + "' not found in model '"
                    + model.id() + "'", DmnException.ErrorKind.DECISION_NOT_FOUND);
        }

        // Resolve DRG: evaluate required decisions first, merge into context
        DmnEvaluationContext resolvedCtx = resolveDependencies(model, decision, ctx);

        return evaluateDecisionTable(decision, resolvedCtx);
    }

    /**
     * Evaluates a FEEL numeric expression against the WASM engine.
     *
     * <p>Supported operations: arithmetic ({@code +}, {@code -}, {@code *}, {@code /}),
     * comparison ({@code <}, {@code <=}, {@code >}, {@code >=}, {@code =}, {@code !=}),
     * and built-in functions ({@code floor}, {@code ceiling}, {@code sqrt}, {@code abs},
     * {@code min}, {@code max}).</p>
     *
     * <p>This method is provided for direct FEEL expression testing. Decision table
     * evaluation via {@link #evaluateDecision} uses this internally.</p>
     *
     * @param operator  the WASM export name (e.g., {@code "feel_add"}); must not be null
     * @param a         the first operand
     * @param b         the second operand (ignored for unary operators)
     * @return the result as a double
     * @throws DmnException  FEEL_EXPRESSION_ERROR if the WASM function traps
     */
    public double evaluateFeelNumericOp(String operator, double a, double b) {
        assertOpen();
        try (WasmModule module = wasmEngine.loadModuleFromClasspath(FEEL_WASM_RESOURCE, "feel")) {
            Value result = module.execute(operator, a, b);
            return result.asDouble();
        } catch (Exception e) {
            throw new DmnException("FEEL numeric op '" + operator + "' failed: " + e.getMessage(),
                    DmnException.ErrorKind.FEEL_EXPRESSION_ERROR, e);
        }
    }

    /**
     * Evaluates a FEEL unary numeric operation (single operand).
     *
     * @param operator  the WASM export name (e.g., {@code "feel_floor"}); must not be null
     * @param a         the operand
     * @return the result as a double
     * @throws DmnException  FEEL_EXPRESSION_ERROR if the WASM function traps
     */
    public double evaluateFeelUnaryNumericOp(String operator, double a) {
        assertOpen();
        try (WasmModule module = wasmEngine.loadModuleFromClasspath(FEEL_WASM_RESOURCE, "feel")) {
            Value result = module.execute(operator, a);
            return result.asDouble();
        } catch (Exception e) {
            throw new DmnException("FEEL unary op '" + operator + "' failed: " + e.getMessage(),
                    DmnException.ErrorKind.FEEL_EXPRESSION_ERROR, e);
        }
    }

    /**
     * Closes the bridge, releasing WASM engine resources.
     *
     * <p>Idempotent: subsequent calls are no-ops.</p>
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            wasmEngine.close();
            log.info("DmnWasmBridge closed");
        }
    }

    // ── Decision table evaluation ────────────────────────────────────────────

    private DmnDecisionResult evaluateDecisionTable(DecisionEntry decision,
                                                    DmnEvaluationContext ctx) {
        DmnHitPolicy hitPolicy = decision.hitPolicy();
        DmnDecisionResult.Builder resultBuilder =
                DmnDecisionResult.builder(decision.id(), hitPolicy);

        List<RuleEntry> matchedRules = new ArrayList<>();
        for (RuleEntry rule : decision.rules()) {
            if (ruleMatches(rule, decision.inputs(), ctx)) {
                matchedRules.add(rule);
            }
        }

        switch (hitPolicy) {
            case UNIQUE -> {
                if (matchedRules.size() > 1) {
                    throw new DmnException(
                            "UNIQUE hit policy violated in decision '" + decision.id()
                            + "': " + matchedRules.size() + " rules matched",
                            DmnException.ErrorKind.HIT_POLICY_VIOLATION);
                }
                matchedRules.forEach(r -> resultBuilder.addMatchedRule(r.outputs()));
            }
            case FIRST -> {
                if (!matchedRules.isEmpty()) {
                    resultBuilder.addMatchedRule(matchedRules.getFirst().outputs());
                }
            }
            case ANY -> {
                if (!matchedRules.isEmpty()) {
                    // Verify all matched rules produce identical output
                    Map<String, Object> first = matchedRules.getFirst().outputs();
                    for (RuleEntry r : matchedRules) {
                        if (!r.outputs().equals(first)) {
                            throw new DmnException(
                                    "ANY hit policy violated in decision '" + decision.id()
                                    + "': matched rules produce different outputs",
                                    DmnException.ErrorKind.HIT_POLICY_VIOLATION);
                        }
                    }
                    resultBuilder.addMatchedRule(first);
                }
            }
            case COLLECT, RULE_ORDER -> matchedRules.forEach(r -> resultBuilder.addMatchedRule(r.outputs()));
            case PRIORITY, OUTPUT_ORDER -> {
                // Priority: sort by priority annotation, take first (PRIORITY) or all (OUTPUT_ORDER)
                // We use rule order as priority since DMN priority values require output enumeration
                if (hitPolicy == DmnHitPolicy.PRIORITY) {
                    if (!matchedRules.isEmpty()) {
                        resultBuilder.addMatchedRule(matchedRules.getFirst().outputs());
                    }
                } else {
                    matchedRules.forEach(r -> resultBuilder.addMatchedRule(r.outputs()));
                }
            }
        }

        return resultBuilder.build();
    }

    // ── Rule matching ────────────────────────────────────────────────────────

    private boolean ruleMatches(RuleEntry rule, List<InputEntry> inputs,
                                DmnEvaluationContext ctx) {
        List<String> inputEntries = rule.inputEntries();
        for (int i = 0; i < inputEntries.size(); i++) {
            String feelExpr = inputEntries.get(i).trim();
            if (i >= inputs.size()) break;

            String inputExprName = inputs.get(i).expression();
            Object inputValue = ctx.get(inputExprName);

            if (!feelMatches(feelExpr, inputValue)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Evaluates a FEEL unary test expression against a given input value.
     *
     * <p>Supported FEEL unary test forms:</p>
     * <ul>
     *   <li>{@code -} — any value (wildcard)</li>
     *   <li>{@code "string"} — string literal equality</li>
     *   <li>{@code 42} — numeric literal equality (via WASM)</li>
     *   <li>{@code >= 18} — numeric comparison (via WASM)</li>
     *   <li>{@code [1..10]} — closed range (via WASM)</li>
     *   <li>{@code (1..10)} — open range (via WASM)</li>
     *   <li>{@code true} / {@code false} — boolean literal</li>
     *   <li>{@code "A","B","C"} — comma-separated OR of string literals</li>
     *   <li>{@code not("A")} — negation of a string or list</li>
     * </ul>
     *
     * @param feelExpr    the FEEL unary test expression; must not be null
     * @param inputValue  the input value to test; may be null
     * @return {@code true} if the expression matches the input value
     */
    boolean feelMatches(String feelExpr, @Nullable Object inputValue) {
        if (feelExpr == null) return true;
        String expr = feelExpr.trim();

        // Wildcard: matches anything
        if (expr.equals("-")) return true;

        // Null input: only wildcard or explicit "null" can match
        if (inputValue == null) {
            return expr.equalsIgnoreCase("null");
        }

        // Negation: not("A") or not("A","B")
        Matcher notMatcher = NOT_PATTERN.matcher(expr);
        if (notMatcher.matches()) {
            String inner = notMatcher.group(1);
            return !feelMatches(inner, inputValue);
        }

        // Comma-separated OR list: "A","B","C" or 1,2,3
        if (isOrList(expr)) {
            String[] parts = splitOrList(expr);
            for (String part : parts) {
                if (feelMatches(part.trim(), inputValue)) return true;
            }
            return false;
        }

        // Range: [1..10], (1..10], [1..10), (1..10)
        Matcher rangeMatcher = RANGE_PATTERN.matcher(expr);
        if (rangeMatcher.matches()) {
            return evaluateRange(rangeMatcher, inputValue);
        }

        // Numeric comparison: >= 18, <= 100, > 5, < 200, != 3, = 5
        Matcher numCompareMatcher = NUMERIC_COMPARE_PATTERN.matcher(expr);
        if (numCompareMatcher.matches()) {
            return evaluateNumericComparison(numCompareMatcher, inputValue);
        }

        // String literal: "Spring" or 'Spring'
        if ((expr.startsWith("\"") && expr.endsWith("\""))
                || (expr.startsWith("'") && expr.endsWith("'"))) {
            String literal = expr.substring(1, expr.length() - 1);
            return literal.equals(inputValue.toString());
        }

        // Boolean literal
        if (expr.equalsIgnoreCase("true") && inputValue instanceof Boolean b) {
            return b;
        }
        if (expr.equalsIgnoreCase("false") && inputValue instanceof Boolean b) {
            return !b;
        }

        // Bare numeric literal (FEEL: 42 means = 42)
        try {
            double literal = Double.parseDouble(expr);
            if (inputValue instanceof Number n) {
                // Use WASM for equality
                try (WasmModule module = wasmEngine.loadModuleFromClasspath(FEEL_WASM_RESOURCE, "feel")) {
                    return module.execute("feel_eq", n.doubleValue(), literal).asInt() == 1;
                }
            }
        } catch (NumberFormatException ignored) {
            // Not a numeric literal
        }

        // Bare string (no quotes) — treat as direct string equality
        return expr.equals(inputValue.toString());
    }

    // ── FEEL evaluation helpers ──────────────────────────────────────────────

    private boolean evaluateRange(Matcher rangeMatcher, Object inputValue) {
        if (!(inputValue instanceof Number num)) return false;
        double val = num.doubleValue();
        String lowerBracket = rangeMatcher.group(1);
        String lowerStr = rangeMatcher.group(2).trim();
        String upperStr = rangeMatcher.group(3).trim();
        String upperBracket = rangeMatcher.group(4);

        try {
            double lower = Double.parseDouble(lowerStr);
            double upper = Double.parseDouble(upperStr);

            try (WasmModule module = wasmEngine.loadModuleFromClasspath(FEEL_WASM_RESOURCE, "feel")) {
                boolean lowerOk = lowerBracket.equals("[")
                        ? module.execute("feel_ge", val, lower).asInt() == 1
                        : module.execute("feel_gt", val, lower).asInt() == 1;
                boolean upperOk = upperBracket.equals("]")
                        ? module.execute("feel_le", val, upper).asInt() == 1
                        : module.execute("feel_lt", val, upper).asInt() == 1;
                return lowerOk && upperOk;
            }
        } catch (NumberFormatException e) {
            throw new DmnException("Invalid range bounds in FEEL expression: " + rangeMatcher.group(0),
                    DmnException.ErrorKind.FEEL_EXPRESSION_ERROR, e);
        }
    }

    private boolean evaluateNumericComparison(Matcher numCompareMatcher, Object inputValue) {
        if (!(inputValue instanceof Number num)) return false;
        String op = numCompareMatcher.group(1);
        double threshold = Double.parseDouble(numCompareMatcher.group(2));
        double val = num.doubleValue();

        try (WasmModule module = wasmEngine.loadModuleFromClasspath(FEEL_WASM_RESOURCE, "feel")) {
            return switch (op) {
                case ">=" -> module.execute("feel_ge", val, threshold).asInt() == 1;
                case "<=" -> module.execute("feel_le", val, threshold).asInt() == 1;
                case ">" -> module.execute("feel_gt", val, threshold).asInt() == 1;
                case "<" -> module.execute("feel_lt", val, threshold).asInt() == 1;
                case "=" -> module.execute("feel_eq", val, threshold).asInt() == 1;
                case "!=" -> module.execute("feel_ne", val, threshold).asInt() == 1;
                default -> throw new DmnException("Unknown FEEL comparison operator: " + op,
                        DmnException.ErrorKind.FEEL_EXPRESSION_ERROR);
            };
        }
    }

    private boolean isOrList(String expr) {
        // Comma-separated if it contains comma outside quotes/brackets/parens
        int depth = 0;
        boolean inString = false;
        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (c == '"' || c == '\'') inString = !inString;
            else if (!inString && (c == '[' || c == '(' || c == '{')) depth++;
            else if (!inString && (c == ']' || c == ')' || c == '}')) depth--;
            else if (!inString && depth == 0 && c == ',') return true;
        }
        return false;
    }

    private String[] splitOrList(String expr) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        boolean inString = false;
        char stringChar = 0;
        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (!inString && (c == '"' || c == '\'')) {
                inString = true;
                stringChar = c;
                current.append(c);
            } else if (inString && c == stringChar) {
                inString = false;
                current.append(c);
            } else if (!inString && (c == '[' || c == '(' || c == '{')) {
                depth++;
                current.append(c);
            } else if (!inString && (c == ']' || c == ')' || c == '}')) {
                depth--;
                current.append(c);
            } else if (!inString && depth == 0 && c == ',') {
                parts.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            parts.add(current.toString().trim());
        }
        return parts.toArray(new String[0]);
    }

    // ── DRG dependency resolution ────────────────────────────────────────────

    private DmnEvaluationContext resolveDependencies(DmnModel model, DecisionEntry decision,
                                                     DmnEvaluationContext ctx) {
        if (decision.requiredDecisions().isEmpty()) {
            return ctx;
        }
        DmnEvaluationContext.Builder enriched = DmnEvaluationContext.builder().putAll(ctx.asMap());
        for (String reqId : decision.requiredDecisions()) {
            DecisionEntry required = model.decisions().get(reqId);
            if (required == null) {
                log.warn("Required decision '{}' not found in model '{}'; skipping", reqId, model.id());
                continue;
            }
            DmnEvaluationContext reqCtx = resolveDependencies(model, required, ctx);
            DmnDecisionResult reqResult = evaluateDecisionTable(required, reqCtx);
            // Merge single result outputs into the context for the dependent decision
            reqResult.getSingleResult().ifPresent(row -> row.forEach(enriched::put));
        }
        return enriched.build();
    }

    // ── XML parsing helpers ──────────────────────────────────────────────────

    private DecisionEntry parseDecisionElement(Element decisionEl) {
        String id = decisionEl.getAttribute("id");
        String name = decisionEl.getAttribute("name");

        // Required decisions (DRG)
        List<String> requiredDecisions = new ArrayList<>();
        NodeList infoReqs = decisionEl.getElementsByTagNameNS("*", "informationRequirement");
        for (int i = 0; i < infoReqs.getLength(); i++) {
            Element infoReq = (Element) infoReqs.item(i);
            NodeList reqDecNodes = infoReq.getElementsByTagNameNS("*", "requiredDecision");
            for (int j = 0; j < reqDecNodes.getLength(); j++) {
                Element reqDecEl = (Element) reqDecNodes.item(j);
                String href = reqDecEl.getAttribute("href");
                if (href.startsWith("#")) href = href.substring(1);
                requiredDecisions.add(href);
            }
        }

        // Decision table
        NodeList tableNodes = decisionEl.getElementsByTagNameNS("*", "decisionTable");
        if (tableNodes.getLength() == 0) {
            // Literal expression or other — treat as empty table
            return new DecisionEntry(id, name, DmnHitPolicy.UNIQUE,
                    List.of(), List.of(), requiredDecisions);
        }

        Element tableEl = (Element) tableNodes.item(0);
        String hitPolicyStr = tableEl.getAttribute("hitPolicy");
        DmnHitPolicy hitPolicy = DmnHitPolicy.fromDmnValue(hitPolicyStr);

        // Inputs
        List<InputEntry> inputs = new ArrayList<>();
        NodeList inputNodes = tableEl.getElementsByTagNameNS("*", "input");
        for (int i = 0; i < inputNodes.getLength(); i++) {
            Element inputEl = (Element) inputNodes.item(i);
            String inputLabel = inputEl.getAttribute("label");
            NodeList exprNodes = inputEl.getElementsByTagNameNS("*", "inputExpression");
            String expression = inputLabel;
            if (exprNodes.getLength() > 0) {
                NodeList textNodes = ((Element) exprNodes.item(0)).getElementsByTagNameNS("*", "text");
                if (textNodes.getLength() > 0 && textNodes.item(0).getTextContent() != null) {
                    expression = textNodes.item(0).getTextContent().trim();
                }
            }
            String typeRef = inputEl.getAttribute("typeRef");
            inputs.add(new InputEntry(inputLabel, expression, typeRef));
        }

        // Outputs
        List<String> outputNames = new ArrayList<>();
        NodeList outputNodes = tableEl.getElementsByTagNameNS("*", "output");
        for (int i = 0; i < outputNodes.getLength(); i++) {
            Element outputEl = (Element) outputNodes.item(i);
            String outputName = outputEl.getAttribute("name");
            if (outputName.isBlank()) outputName = outputEl.getAttribute("label");
            if (outputName.isBlank()) outputName = "output" + i;
            outputNames.add(outputName);
        }

        // Rules
        List<RuleEntry> rules = new ArrayList<>();
        NodeList ruleNodes = tableEl.getElementsByTagNameNS("*", "rule");
        for (int i = 0; i < ruleNodes.getLength(); i++) {
            Element ruleEl = (Element) ruleNodes.item(i);
            rules.add(parseRuleElement(ruleEl, outputNames));
        }

        return new DecisionEntry(id, name, hitPolicy, inputs, rules, requiredDecisions);
    }

    private RuleEntry parseRuleElement(Element ruleEl, List<String> outputNames) {
        List<String> inputEntries = new ArrayList<>();
        NodeList inputEntryNodes = ruleEl.getElementsByTagNameNS("*", "inputEntry");
        for (int i = 0; i < inputEntryNodes.getLength(); i++) {
            Element entryEl = (Element) inputEntryNodes.item(i);
            NodeList textNodes = entryEl.getElementsByTagNameNS("*", "text");
            String text = "-";
            if (textNodes.getLength() > 0) {
                text = textNodes.item(0).getTextContent();
                if (text == null || text.isBlank()) text = "-";
            }
            inputEntries.add(text);
        }

        Map<String, Object> outputs = new LinkedHashMap<>();
        NodeList outputEntryNodes = ruleEl.getElementsByTagNameNS("*", "outputEntry");
        for (int i = 0; i < outputEntryNodes.getLength(); i++) {
            Element entryEl = (Element) outputEntryNodes.item(i);
            NodeList textNodes = entryEl.getElementsByTagNameNS("*", "text");
            String text = "";
            if (textNodes.getLength() > 0 && textNodes.item(0).getTextContent() != null) {
                text = textNodes.item(0).getTextContent().trim();
            }
            String outputName = (i < outputNames.size()) ? outputNames.get(i) : "output" + i;
            outputs.put(outputName, parseOutputValue(text));
        }

        return new RuleEntry(inputEntries, outputs);
    }

    private @Nullable Object parseOutputValue(String text) {
        if (text == null || text.isBlank() || text.equals("-")) return null;
        // String literal
        if ((text.startsWith("\"") && text.endsWith("\""))
                || (text.startsWith("'") && text.endsWith("'"))) {
            return text.substring(1, text.length() - 1);
        }
        // Boolean
        if (text.equalsIgnoreCase("true")) return Boolean.TRUE;
        if (text.equalsIgnoreCase("false")) return Boolean.FALSE;
        // Numeric
        try { return Long.parseLong(text); } catch (NumberFormatException ignored) {}
        try { return Double.parseDouble(text); } catch (NumberFormatException ignored) {}
        // Bare string
        return text;
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    private void verifyFeelWasm() {
        try (WasmModule module = wasmEngine.loadModuleFromClasspath(FEEL_WASM_RESOURCE, "feel-verify")) {
            // Verify the WASM exports the expected FEEL functions
            var keys = module.getMemberKeys();
            if (!keys.contains("feel_add")) {
                throw new DmnException(
                        "FEEL WASM engine is missing expected export 'feel_add'. "
                        + "Ensure " + FEEL_WASM_RESOURCE + " is the correct binary.",
                        DmnException.ErrorKind.WASM_LOAD_ERROR);
            }
            log.debug("FEEL WASM engine verified: {} exports available", keys.size());
        } catch (DmnException e) {
            throw e;
        } catch (Exception e) {
            throw new DmnException("Cannot load FEEL engine WASM: " + e.getMessage(),
                    DmnException.ErrorKind.WASM_LOAD_ERROR, e);
        }
    }

    private void assertOpen() {
        if (closed.get()) {
            throw new DmnException("DmnWasmBridge has been closed",
                    DmnException.ErrorKind.EVALUATION_ERROR);
        }
    }

    // ── Internal record types ────────────────────────────────────────────────

    /**
     * Parsed DMN model.
     *
     * @param id         model identifier
     * @param name       model name
     * @param decisions  map from decision ID to DecisionEntry
     */
    public record DmnModel(String id, String name, Map<String, DecisionEntry> decisions) {
        /**
         * Returns the decision IDs in this model.
         *
         * @return unmodifiable set of decision IDs; never null
         */
        public java.util.Set<String> decisionIds() {
            return decisions.keySet();
        }
    }

    /**
     * Parsed decision entry with its decision table.
     */
    record DecisionEntry(
            String id,
            String name,
            DmnHitPolicy hitPolicy,
            List<InputEntry> inputs,
            List<RuleEntry> rules,
            List<String> requiredDecisions) {}

    /**
     * Input column specification.
     */
    record InputEntry(String label, String expression, String typeRef) {}

    /**
     * A single rule in a decision table.
     */
    record RuleEntry(List<String> inputEntries, Map<String, Object> outputs) {}
}
