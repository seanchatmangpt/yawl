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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.yawlfoundation.yawl.qlever.SparqlCapability.*;
import static org.yawlfoundation.yawl.qlever.QLeverMediaType.*;

/**
 * Chicago TDD tests for SPARQL FILTER function capabilities.
 * Covers: FN_LANG, FN_LANGMATCHES, FN_DATATYPE, FN_BOUND, FN_ISIRI, FN_ISLITERAL,
 *         FN_ISBLANK, FN_STR, FN_STRSTARTS, FN_REGEX, FN_NUMERIC_OPS, FN_BOOLEAN_OPS.
 *
 * @author YAWL Foundation
 * @since YAWL 6.0
 */
@Tag("sparql")
class FilterFunctionTest extends SparqlTestFixtures {

    @Test
    @SparqlCapabilityTest(FN_LANG)
    void filterLang_returnsOnlyEnglishLabels() throws Exception {
        var result = engine().executeSelect(PFX + """
            SELECT ?name WHERE { ?p :name ?name FILTER(LANG(?name) = 'en') }""", JSON);
        assertThat(result.rowCount()).isGreaterThan(0);
        for (var row : rows(result)) {
            String lang = row.at("/name/xml:lang").asText();
            assertThat(lang).isEqualTo("en");
        }
    }

    @Test
    @SparqlCapabilityTest(FN_LANGMATCHES)
    void filterLangmatches_matchesEnglish() throws Exception {
        var result = engine().executeSelect(PFX + """
            SELECT ?name WHERE { ?p :name ?name FILTER(LANGMATCHES(LANG(?name), 'en')) }""", JSON);
        assertThat(result.rowCount()).isGreaterThan(0);
    }

    @Test
    @SparqlCapabilityTest(FN_DATATYPE)
    void filterDatatype_integerLiterals() throws Exception {
        var result = engine().executeSelect(PFX + """
            SELECT ?age WHERE {
              ?p :age ?age .
              FILTER(DATATYPE(?age) = <http://www.w3.org/2001/XMLSchema#integer>)
            }""", JSON);
        assertThat(result.rowCount()).isGreaterThan(0);
    }

    @Test
    @SparqlCapabilityTest(FN_BOUND)
    void filterBound_keepsOnlyBoundRows() throws Exception {
        var withOptional = engine().executeSelect(PFX + """
            SELECT ?p ?cat WHERE {
              ?p a :Person .
              OPTIONAL { ?p :category ?cat }
            }""", JSON);
        var boundOnly = engine().executeSelect(PFX + """
            SELECT ?p ?cat WHERE {
              ?p a :Person .
              OPTIONAL { ?p :category ?cat }
              FILTER(BOUND(?cat))
            }""", JSON);
        assertThat(boundOnly.rowCount()).isLessThanOrEqualTo(withOptional.rowCount());
        assertThat(boundOnly.rowCount()).isGreaterThan(0);
    }

    @Test
    @SparqlCapabilityTest(FN_ISIRI)
    void filterIsIri_keepsIriObjects() throws Exception {
        var result = engine().executeSelect(PFX + """
            SELECT ?o WHERE { :alice ?p ?o . FILTER(isIRI(?o)) }""", JSON);
        assertThat(result.rowCount()).isGreaterThan(0);
    }

    @Test
    @SparqlCapabilityTest(FN_ISLITERAL)
    void filterIsLiteral_keepsLiteralObjects() throws Exception {
        var result = engine().executeSelect(PFX + """
            SELECT ?o WHERE { :alice ?p ?o . FILTER(isLiteral(?o)) }""", JSON);
        assertThat(result.rowCount()).isGreaterThan(0);
    }

    @Test
    @SparqlCapabilityTest(FN_ISBLANK)
    void filterIsBlank_noBlankNodes() throws Exception {
        var result = engine().executeSelect(PFX + """
            SELECT ?s WHERE { ?s ?p ?o . FILTER(isBlank(?s)) }""", JSON);
        assertThat(result.rowCount()).isEqualTo(0);
    }

    @Test
    @SparqlCapabilityTest(FN_STR)
    void filterStr_convertsToString() throws Exception {
        var result = engine().executeSelect(PFX + """
            SELECT ?s WHERE { ?s :age ?age . FILTER(STR(?age) = '30') }""", JSON);
        assertThat(result.rowCount()).isGreaterThan(0);
        assertThat(values(result, "s")).contains("http://yawl.test/alice");
    }

    @Test
    @SparqlCapabilityTest(FN_STRSTARTS)
    void filterStrStarts_matchesPrefix() throws Exception {
        var result = engine().executeSelect(PFX + """
            SELECT ?name WHERE { ?p :name ?name . FILTER(STRSTARTS(STR(?name), 'Ali')) }""", JSON);
        assertThat(result.rowCount()).isGreaterThan(0);
        for (var row : rows(result)) {
            assertThat(row.at("/name/value").asText()).startsWith("Ali");
        }
    }

    @Test
    @SparqlCapabilityTest(FN_REGEX)
    void filterRegex_matchesPattern() throws Exception {
        var result = engine().executeSelect(PFX + """
            SELECT ?name WHERE { ?p :name ?name . FILTER(REGEX(STR(?name), '^Ali')) }""", JSON);
        assertThat(result.rowCount()).isGreaterThan(0);
        for (var row : rows(result)) {
            assertThat(row.at("/name/value").asText()).startsWith("Ali");
        }
    }

    @Test
    @SparqlCapabilityTest(FN_NUMERIC_OPS)
    void filterNumericOps_greaterThanAndLessOrEqual() throws Exception {
        var gt = engine().executeSelect(PFX + "SELECT ?p WHERE { ?p :age ?a FILTER(?a > 28) }", JSON);
        var le = engine().executeSelect(PFX + "SELECT ?p WHERE { ?p :age ?a FILTER(?a <= 28) }", JSON);
        var all = engine().executeSelect(PFX + "SELECT ?p WHERE { ?p :age ?a }", JSON);
        assertThat(gt.rowCount() + le.rowCount()).isEqualTo(all.rowCount());
    }

    @Test
    @SparqlCapabilityTest(FN_BOOLEAN_OPS)
    void filterBooleanOps_andOrNot() throws Exception {
        var and = engine().executeSelect(PFX +
            "SELECT ?p WHERE { ?p :age ?a FILTER(?a > 20 && ?a < 35) }", JSON);
        var or = engine().executeSelect(PFX +
            "SELECT ?p WHERE { ?p :age ?a FILTER(?a < 25 || ?a > 33) }", JSON);
        var not = engine().executeSelect(PFX +
            "SELECT ?p WHERE { ?p :age ?a FILTER(!(?a = 30)) }", JSON);
        assertThat(and.rowCount()).isGreaterThan(0);
        assertThat(or.rowCount()).isGreaterThan(0);
        assertThat(not.rowCount()).isGreaterThan(0);
    }
}
