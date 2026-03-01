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

package org.yawlfoundation.yawl.qlever;

/**
 * Enumeration of SPARQL capabilities supported by the QLever engine.
 * Contains 80 distinct capabilities covering all major SPARQL features.
 *
 * @author YAWL Foundation
 * @since YAWL 6.0
 */
public enum SparqlCapability {
    // SELECT_CORE (5)
    SELECT_STAR,
    SELECT_VARIABLES,
    SELECT_EXPRESSIONS,
    SELECT_DISTINCT,
    SELECT_REDUCED,

    // GRAPH_PATTERNS (11)
    BGP,
    OPTIONAL,
    UNION,
    MINUS,
    FILTER,
    FILTER_EXISTS,
    FILTER_NOT_EXISTS,
    BIND,
    VALUES_INLINE,
    VALUES_MULTIVAR,
    SUBQUERY,

    // SOLUTION_MODIFIERS (8)
    ORDER_BY_ASC,
    ORDER_BY_DESC,
    ORDER_BY_EXPR,
    LIMIT,
    OFFSET,
    LIMIT_OFFSET,
    GROUP_BY,
    HAVING,

    // AGGREGATES (9)
    AGG_COUNT,
    AGG_COUNT_DISTINCT,
    AGG_COUNT_STAR,
    AGG_SUM,
    AGG_AVG,
    AGG_MIN,
    AGG_MAX,
    AGG_SAMPLE,
    AGG_GROUP_CONCAT,

    // PROPERTY_PATHS (8)
    PATH_SEQUENCE,
    PATH_ALTERNATIVE,
    PATH_INVERSE,
    PATH_ZERO_OR_MORE,
    PATH_ONE_OR_MORE,
    PATH_ZERO_OR_ONE,
    PATH_NEGATED,
    PATH_NEGATED_INVERSE,

    // QUERY_FORMS (5)
    CONSTRUCT,
    ASK,
    DESCRIBE,
    NAMED_GRAPHS_FROM,
    NAMED_GRAPHS_GRAPH,

    // SPARQL_UPDATE (7)
    UPDATE_INSERT_DATA,
    UPDATE_DELETE_DATA,
    UPDATE_DELETE_WHERE,
    UPDATE_INSERT_WHERE,
    UPDATE_DELETE_INSERT,
    UPDATE_CLEAR,
    UPDATE_DROP,

    // OUTPUT_FORMATS (7)
    FORMAT_JSON,
    FORMAT_TSV,
    FORMAT_CSV,
    FORMAT_XML,
    FORMAT_TURTLE,
    FORMAT_NTRIPLES,
    FORMAT_BINARY,

    // FILTER_FUNCTIONS (12)
    FN_LANG,
    FN_LANGMATCHES,
    FN_DATATYPE,
    FN_BOUND,
    FN_ISIRI,
    FN_ISLITERAL,
    FN_ISBLANK,
    FN_STR,
    FN_STRSTARTS,
    FN_REGEX,
    FN_NUMERIC_OPS,
    FN_BOOLEAN_OPS,

    // QLEVER_EXTENSIONS (3)
    EXT_CONTAINS_WORD,
    EXT_CONTAINS_ENTITY,
    EXT_SCORE,

    // ERROR_HANDLING (5)
    ERR_MALFORMED_SPARQL,
    ERR_TIMEOUT,
    ERR_UNKNOWN_PREDICATE,
    ERR_UPDATE_ON_READONLY,
    ERR_EMPTY_RESULT;

    /**
     * Total number of capabilities. Used for validation that all capabilities
     * are properly mapped and tested.
     */
    public static final int TOTAL = 80;
}
