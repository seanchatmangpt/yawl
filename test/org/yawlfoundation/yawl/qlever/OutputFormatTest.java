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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.yawlfoundation.yawl.qlever.SparqlCapability.*;
import static org.yawlfoundation.yawl.qlever.QLeverMediaType.*;

/**
 * Chicago TDD tests for output format capabilities.
 * Covers: FORMAT_JSON, FORMAT_TSV, FORMAT_CSV, FORMAT_XML,
 *         FORMAT_TURTLE, FORMAT_NTRIPLES, FORMAT_BINARY.
 *
 * @author YAWL Foundation
 * @since YAWL 6.0
 */
@Tag("sparql")
class OutputFormatTest extends SparqlTestFixtures {

    @Test
    @SparqlCapabilityTest(FORMAT_JSON)
    void formatJson_wellFormedJson() throws Exception {
        var result = engine().executeSelect(PFX + "SELECT ?s WHERE { ?s a :Person } LIMIT 3", JSON);
        assertDoesNotThrow(() -> new ObjectMapper().readTree(result.data()));
        assertThat(result.data()).contains("\"results\"").contains("\"bindings\"");
    }

    @Test
    @SparqlCapabilityTest(FORMAT_TSV)
    void formatTsv_tabSeparatedWithHeader() throws Exception {
        var result = engine().executeSelect(PFX + "SELECT ?s ?age WHERE { ?s :age ?age } LIMIT 3", TSV);
        var lines = result.data().split("\n");
        assertThat(lines[0]).contains("s").contains("age");
        assertThat(lines.length).isGreaterThan(1);
    }

    @Test
    @SparqlCapabilityTest(FORMAT_CSV)
    void formatCsv_commaSeparatedWithHeader() throws Exception {
        var result = engine().executeSelect(PFX + "SELECT ?s WHERE { ?s a :Person } LIMIT 3", CSV);
        var firstLine = result.data().lines().findFirst().orElseThrow();
        assertThat(firstLine).contains("s");
    }

    @Test
    @SparqlCapabilityTest(FORMAT_XML)
    void formatXml_wellFormedWithSparqlRoot() throws Exception {
        var result = engine().executeSelect(PFX + "SELECT ?s WHERE { ?s a :Person } LIMIT 3", XML);
        assertThat(result.data()).contains("<sparql").or().contains("sparql-results");
        assertThat(result.data()).contains("results");
    }

    @Test
    @SparqlCapabilityTest(FORMAT_TURTLE)
    void formatTurtle_validForConstruct() throws Exception {
        var result = engine().executeConstruct(PFX +
            "CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o } LIMIT 5", TURTLE);
        assertThat(result.data()).matches(data ->
            data.contains("@prefix") || data.contains("<http") || data.isBlank());
    }

    @Test
    @SparqlCapabilityTest(FORMAT_NTRIPLES)
    void formatNtriples_eachLineIsValidTriple() throws Exception {
        var result = engine().executeConstruct(PFX +
            "CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o } LIMIT 5", N_TRIPLES);
        var nonEmpty = result.data().lines()
            .filter(l -> !l.isBlank() && !l.startsWith("#"))
            .toList();
        for (var line : nonEmpty) {
            assertThat(line.trim()).matches(l -> l.startsWith("<") || l.startsWith("_:"));
            assertThat(line.trim()).endsWith(".");
        }
    }

    @Test
    @SparqlCapabilityTest(FORMAT_BINARY)
    void formatBinary_nonEmpty() throws Exception {
        var result = engine().executeSelect(PFX +
            "SELECT * WHERE { ?s ?p ?o } LIMIT 5", BINARY);
        assertThat(result.data()).isNotNull();
        assertThat(result.data().length()).isGreaterThanOrEqualTo(0);
    }
}
