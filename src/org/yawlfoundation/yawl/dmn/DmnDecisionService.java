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

package org.yawlfoundation.yawl.dmn;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.integration.util.GraalVMUtils;
import org.yawlfoundation.yawl.integration.util.ParameterValidator;
import org.yawlfoundation.yawl.integration.util.YawlConstants;
import org.yawlfoundation.yawl.integration.util.SkillLogger;
import org.yawlfoundation.yawl.graalwasm.dmn.DmnDecisionResult;
import org.yawlfoundation.yawl.graalwasm.dmn.DmnEvaluationContext;
import org.yawlfoundation.yawl.graalwasm.dmn.DmnException;
import org.yawlfoundation.yawl.graalwasm.dmn.DmnWasmBridge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * High-level DMN decision service combining {@link DataModel} schema validation with
 * {@link DmnWasmBridge} decision evaluation.
 *
 * <p>This facade is the primary entry point for DMN execution in YAWL workflows.
 * It wraps {@link DmnWasmBridge} with:</p>
 * <ol>
 *   <li>Schema-aware input validation against a {@link DataModel} before evaluation</li>
 *   <li>Referential integrity checks ({@link DataModel#validateIntegrity()})</li>
 *   <li>Post-evaluation COLLECT aggregation via {@link DmnCollectAggregation}</li>
 *   <li>A {@link AutoCloseable} lifecycle tied to the underlying WASM engine</li>
 * </ol>
 *
 * <h2>Quick start — single decision</h2>
 * <pre>{@code
 * DataModel schema = DataModel.builder("LoanSchema")
 *     .table(DmnTable.builder("Applicant")
 *         .column(DmnColumn.of("age", "integer").build())
 *         .column(DmnColumn.of("income", "double").build())
 *         .build())
 *     .build();
 *
 * try (DmnDecisionService svc = new DmnDecisionService(schema)) {
 *     DmnWasmBridge.DmnModel model = svc.parseDmnModel(dmnXml);
 *
 *     DmnEvaluationContext ctx = DmnEvaluationContext.builder()
 *         .put("age", 35)
 *         .put("income", 55000.0)
 *         .build();
 *
 *     DmnDecisionResult result = svc.evaluate(model, "EligibilityDecision", ctx);
 *     result.getSingleResult().ifPresent(row ->
 *         System.out.println("Eligible: " + row.get("eligible")));
 * }
 * }</pre>
 *
 * <h2>Quick start — COLLECT aggregation</h2>
 * <pre>{@code
 * DmnDecisionResult result = svc.evaluate(model, "RiskScoreCollect", ctx);
 * OptionalDouble total = svc.collectAggregate(result, "score", DmnCollectAggregation.SUM);
 * OptionalDouble best  = svc.collectAggregate(result, "score", DmnCollectAggregation.MAX);
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see DmnWasmBridge
 * @see DataModel
 * @see DmnCollectAggregation
 */
public final class DmnDecisionService implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DmnDecisionService.class);
    private static final SkillLogger skillLogger = SkillLogger.forSkill("dmn-service", "DMN_DecisionService");

    private final @Nullable DataModel schema;
    private final DmnWasmBridge bridge;

    /**
     * Creates a service with schema validation enabled.
     *
     * <p>Input contexts will be validated against the tables in {@code schema}
     * before each evaluation. The model's referential integrity is checked immediately.</p>
     *
     * @param schema  the data model; must not be null
     * @throws IllegalArgumentException if the schema has integrity violations
     */
    public DmnDecisionService(DataModel schema) {
        ParameterValidator.validateNotNull(schema, "schema");

        List<String> errors = schema.validateIntegrity();
        if (!errors.isEmpty()) {
            String errorMsg = "DataModel '" + schema.getName() + "' has integrity violations: " + errors;
            skillLogger.error(errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }

        this.schema = schema;
        this.bridge = new DmnWasmBridge();
        skillLogger.info("DmnDecisionService created with schema '{}': {} tables, {} relationships",
                schema.getName(), schema.tableCount(), schema.relationshipCount());
    }

    /**
     * Creates a schema-less service (no input validation).
     *
     * <p>Use this constructor when no {@link DataModel} is available or validation
     * should be deferred to the DMN model's own FEEL type checks.</p>
     */
    public DmnDecisionService() {
        this.schema = null;
        this.bridge = new DmnWasmBridge();
        skillLogger.info("DmnDecisionService created without schema");
    }

    /**
     * Returns the data model this service validates inputs against, or null if
     * schema validation is disabled.
     *
     * @return the schema, or null
     */
    public @Nullable DataModel getSchema() {
        return schema;
    }

    /**
     * Parses a DMN XML string into an executable model.
     *
     * @param dmnXml  the DMN 1.2 or 1.3 XML; must not be null or blank
     * @return the parsed model; never null
     * @throws DmnException if the XML is malformed or unsupported
     */
    public DmnWasmBridge.DmnModel parseDmnModel(String dmnXml) {
        String sanitizedXml = ParameterValidator.validateRequired(Map.of("dmnXml", dmnXml), "dmnXml",
                "DMN XML must not be null or blank");
        skillLogger.debug("Parsing DMN model with " + sanitizedXml.length() + " characters");
        return bridge.parseDmnModel(sanitizedXml);
    }

    /**
     * Evaluates a named decision against the given context.
     *
     * <p>If a {@link DataModel} was provided at construction, the context variables
     * are validated against all tables in the schema before evaluation. Missing required
     * columns produce warnings (not errors) to allow partial context evaluation.</p>
     *
     * @param model       the parsed DMN model; must not be null
     * @param decisionId  the decision identifier; must not be null or blank
     * @param ctx         the evaluation context; must not be null
     * @return the decision result; never null
     * @throws DmnException if evaluation fails or the decision is not found
     */
    public DmnDecisionResult evaluate(DmnWasmBridge.DmnModel model,
                                      String decisionId,
                                      DmnEvaluationContext ctx) {
        ParameterValidator.validateNotNull(model, "model");
        ParameterValidator.validateNotNull(decisionId, "decisionId");
        ParameterValidator.validateNotNull(ctx, "ctx");

        String sanitizedDecisionId = ParameterValidator.validateRequired(Map.of("decisionId", decisionId),
                "decisionId", "Decision ID must not be null or blank");

        skillLogger.debug("Evaluating decision '{}' with schema validation: {}",
                sanitizedDecisionId, schema != null);

        if (schema != null) {
            validateContextAgainstSchema(ctx, sanitizedDecisionId);
        }

        return bridge.evaluateDecision(model, sanitizedDecisionId, ctx);
    }

    /**
     * Evaluates a decision and applies a COLLECT aggregation to a named output column.
     *
     * <p>Extracts all numeric values of {@code outputColumn} from the matched rules,
     * then aggregates them using the given {@link DmnCollectAggregation} operator.
     * Returns empty if no rules matched or the column has no numeric values.</p>
     *
     * @param model        the parsed DMN model; must not be null
     * @param decisionId   the decision identifier; must not be null
     * @param ctx          the evaluation context; must not be null
     * @param outputColumn the output column name to aggregate; must not be null
     * @param aggregation  the aggregation operator; must not be null
     * @return the aggregated value, or empty if no numeric values were found
     * @throws DmnException if evaluation fails
     */
    public OptionalDouble evaluateAndAggregate(DmnWasmBridge.DmnModel model,
                                               String decisionId,
                                               DmnEvaluationContext ctx,
                                               String outputColumn,
                                               DmnCollectAggregation aggregation) {
        DmnDecisionResult result = evaluate(model, decisionId, ctx);
        return collectAggregate(result, outputColumn, aggregation);
    }

    /**
     * Applies a COLLECT aggregation to a named output column of an already-evaluated
     * decision result.
     *
     * <p>The method extracts all non-null {@link Number} values of {@code outputColumn}
     * from the matched rules (both single-result and multi-hit). For {@link DmnCollectAggregation#COUNT}
     * the full matched-rule count is used, regardless of column content.</p>
     *
     * @param result       a previously evaluated decision result; must not be null
     * @param outputColumn the output column name; must not be null
     * @param aggregation  the aggregation operator; must not be null
     * @return the aggregated value, or empty if no values were found
     */
    public OptionalDouble collectAggregate(DmnDecisionResult result,
                                           String outputColumn,
                                           DmnCollectAggregation aggregation) {
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(outputColumn, "outputColumn must not be null");
        Objects.requireNonNull(aggregation, "aggregation must not be null");

        if (aggregation == DmnCollectAggregation.COUNT) {
            // COUNT aggregates total matched rows, not column values
            int count = result.getMatchedRules().isEmpty()
                    ? (result.getSingleResult().isPresent() ? 1 : 0)
                    : result.getMatchedRules().size();
            return count == 0 ? OptionalDouble.empty() : OptionalDouble.of(count);
        }

        List<Double> numericValues = extractNumericValues(result, outputColumn);
        return aggregation.aggregateDoubles(numericValues);
    }

    @Override
    public void close() {
        bridge.close();
        log.debug("DmnDecisionService closed");
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void validateContextAgainstSchema(DmnEvaluationContext ctx, String decisionId) {
        assert schema != null;
        List<String> warnings = new ArrayList<>();

        for (DmnTable table : schema.getTables()) {
            List<String> rowErrors = table.validateRow(ctx.asMap());
            if (!rowErrors.isEmpty()) {
                for (String err : rowErrors) {
                    warnings.add("[table=" + table.getName() + "] " + err);
                }
            }
        }

        if (!warnings.isEmpty()) {
            skillLogger.warn("Decision '{}': schema validation warnings for context {}: {}",
                    decisionId, ctx.keySet(), warnings);
        }
    }

    private List<Double> extractNumericValues(DmnDecisionResult result, String outputColumn) {
        List<Double> values = new ArrayList<>();

        // Single-result path
        result.getSingleResult().ifPresent(row -> {
            Object val = row.get(outputColumn);
            if (val instanceof Number n) {
                values.add(n.doubleValue());
            }
        });

        // Multi-hit path
        if (values.isEmpty()) {
            for (Map<String, Object> row : result.getMatchedRules()) {
                Object val = row.get(outputColumn);
                if (val instanceof Number n) {
                    values.add(n.doubleValue());
                }
            }
        }

        return values;
    }
}
