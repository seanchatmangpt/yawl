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

import org.yawlfoundation.yawl.graalwasm.WasmExecutionEngine;
import org.yawlfoundation.yawl.graalwasm.WasmModule;
import org.yawlfoundation.yawl.graalwasm.WasmSandboxConfig;

import java.util.Collection;
import java.util.List;
import java.util.OptionalDouble;

/**
 * COLLECT hit policy aggregation operators for DMN decision tables.
 *
 * <p>Implements the four DMN COLLECT aggregations defined in DMN 1.3 §8.8.2:</p>
 * <ul>
 *   <li>{@link #SUM} ({@code C+}) — sum all numeric output values</li>
 *   <li>{@link #MIN} ({@code C<}) — minimum numeric output value, WASM-accelerated</li>
 *   <li>{@link #MAX} ({@code C>}) — maximum numeric output value, WASM-accelerated</li>
 *   <li>{@link #COUNT} ({@code C#}) — count of matched output rows</li>
 * </ul>
 *
 * <h2>WASM acceleration</h2>
 * <p>{@link #MIN} and {@link #MAX} delegate numeric comparison to the bundled
 * {@code dmn_feel_engine.wasm} via {@link WasmExecutionEngine} when multiple
 * values are aggregated. The WASM {@code feel_min} / {@code feel_max} exports
 * perform pairwise f64 comparisons. For collections of one value, the WASM
 * round-trip is skipped.</p>
 *
 * <h2>Quick start</h2>
 * <pre>{@code
 * List<Double> scores = List.of(72.5, 88.0, 65.0, 91.3);
 *
 * double sum   = DmnCollectAggregation.SUM.aggregate(scores);   // 316.8
 * double min   = DmnCollectAggregation.MIN.aggregate(scores);   //  65.0
 * double max   = DmnCollectAggregation.MAX.aggregate(scores);   //  91.3
 * double count = DmnCollectAggregation.COUNT.aggregate(scores); //   4.0
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see org.yawlfoundation.yawl.graalwasm.dmn.DmnWasmBridge
 */
public enum DmnCollectAggregation {

    /**
     * Sum (C+): adds all numeric values in the collection.
     * Returns 0.0 for an empty collection.
     */
    SUM("C+") {
        @Override
        public double aggregate(Collection<? extends Number> values) {
            double sum = 0.0;
            for (Number v : values) {
                sum += v.doubleValue();
            }
            return sum;
        }
    },

    /**
     * Minimum (C&lt;): returns the smallest numeric value, delegating to WASM
     * for pairwise comparison.
     * Returns {@link Double#POSITIVE_INFINITY} for an empty collection.
     */
    MIN("C<") {
        @Override
        public double aggregate(Collection<? extends Number> values) {
            List<? extends Number> list = List.copyOf(values);
            if (list.isEmpty()) return Double.POSITIVE_INFINITY;
            if (list.size() == 1) return list.get(0).doubleValue();
            return wasmReduce("feel_min", list);
        }
    },

    /**
     * Maximum (C&gt;): returns the largest numeric value, delegating to WASM
     * for pairwise comparison.
     * Returns {@link Double#NEGATIVE_INFINITY} for an empty collection.
     */
    MAX("C>") {
        @Override
        public double aggregate(Collection<? extends Number> values) {
            List<? extends Number> list = List.copyOf(values);
            if (list.isEmpty()) return Double.NEGATIVE_INFINITY;
            if (list.size() == 1) return list.get(0).doubleValue();
            return wasmReduce("feel_max", list);
        }
    },

    /**
     * Count (C#): returns the number of values as a double.
     * Returns 0.0 for an empty collection.
     */
    COUNT("C#") {
        @Override
        public double aggregate(Collection<? extends Number> values) {
            return values.size();
        }
    };

    private final String dmnSymbol;

    DmnCollectAggregation(String dmnSymbol) {
        this.dmnSymbol = dmnSymbol;
    }

    /**
     * Aggregates the given numeric collection according to this operator.
     *
     * @param values  the collection to aggregate; must not be null
     * @return the aggregated result as a double
     */
    public abstract double aggregate(Collection<? extends Number> values);

    /**
     * Returns the DMN standard symbol for this aggregation (e.g., {@code "C+"}).
     *
     * @return the symbol string; never null
     */
    public String getDmnSymbol() {
        return dmnSymbol;
    }

    /**
     * Returns whether this aggregation operates on a single value and returns it
     * unchanged (identity-style; COUNT always returns collection size).
     *
     * @return {@code true} for {@link #SUM}, {@link #MIN}, {@link #MAX}
     */
    public boolean isNumericAggregation() {
        return this != COUNT;
    }

    /**
     * Resolves a {@code DmnCollectAggregation} from its DMN symbol or name.
     *
     * <p>Accepts {@code "C+"}, {@code "C<"}, {@code "C>"}, {@code "C#"} (DMN symbols)
     * and {@code "SUM"}, {@code "MIN"}, {@code "MAX"}, {@code "COUNT"} (names).</p>
     *
     * @param value  the symbol or name; must not be null
     * @return the matching aggregation
     * @throws IllegalArgumentException if unrecognised
     */
    public static DmnCollectAggregation fromValue(String value) {
        if (value == null) throw new IllegalArgumentException("aggregation value must not be null");
        return switch (value.trim().toUpperCase()) {
            case "C+", "SUM"   -> SUM;
            case "C<", "MIN"   -> MIN;
            case "C>", "MAX"   -> MAX;
            case "C#", "COUNT" -> COUNT;
            default -> throw new IllegalArgumentException(
                    "Unknown COLLECT aggregation: '" + value + "'. "
                    + "Expected one of: C+, C<, C>, C#, SUM, MIN, MAX, COUNT");
        };
    }

    /**
     * Aggregates an {@link OptionalDouble} stream of values, returning empty for
     * an empty collection.
     *
     * @param values  the list of doubles; must not be null
     * @return the aggregation result, or empty if values is empty and the operator
     *         would produce a sentinel (MIN/MAX)
     */
    public OptionalDouble aggregateDoubles(List<Double> values) {
        if (values.isEmpty()) return OptionalDouble.empty();
        return OptionalDouble.of(aggregate(values));
    }

    @Override
    public String toString() {
        return name() + "(" + dmnSymbol + ")";
    }

    // -------------------------------------------------------------------------
    // WASM-backed pairwise reduction for MIN and MAX
    // -------------------------------------------------------------------------

    /**
     * Reduces a list of numeric values using the named WASM f64 binary function.
     *
     * <p>Opens a short-lived {@link WasmExecutionEngine} backed by
     * {@code dmn_feel_engine.wasm}, then applies {@code fn(acc, next)} repeatedly
     * over the list, folding left. The WASM round-trip is constant per element.</p>
     *
     * @param wasmFn  the export name ({@code "feel_min"} or {@code "feel_max"})
     * @param values  list of at least two values
     * @return the reduced double value
     */
    private static double wasmReduce(String wasmFn, List<? extends Number> values) {
        try (WasmExecutionEngine engine = WasmExecutionEngine.builder()
                .sandboxConfig(WasmSandboxConfig.pureWasm())
                .build();
             WasmModule mod = engine.loadModuleFromClasspath(
                     "wasm/dmn_feel_engine.wasm", "dmn_feel_engine")) {

            double acc = values.get(0).doubleValue();
            for (int i = 1; i < values.size(); i++) {
                double next = values.get(i).doubleValue();
                org.graalvm.polyglot.Value result = mod.execute(wasmFn, acc, next);
                acc = result.asDouble();
            }
            return acc;
        }
    }
}
