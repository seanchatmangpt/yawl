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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Shared SPARQL test dataset and registry assertion for all QLever capability tests.
 * All capability test classes extend this class.
 *
 * @author YAWL Foundation
 * @since YAWL 6.0
 */
public abstract class SparqlTestFixtures {

    protected static final String PFX =
        "PREFIX :    <http://yawl.test/>\n" +
        "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n" +
        "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n" +
        "PREFIX ql:  <http://qlever.cs.uni-freiburg.de/builtin-functions/>\n";

    protected static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Loads the shared dataset and verifies registry coverage.
     * Called automatically before tests run.
     *
     * @throws Exception if dataset loading fails
     */
    @BeforeAll
    static void loadDatasetAndVerifyRegistry() throws Exception {
        assumeTrue(QLeverTestNode.isAvailable(), "QLever native library or test index not available");
        QLeverTestNode.engine().executeUpdate(PFX + buildDataset());
        SparqlCapabilityRegistry.assertAllTested(
            SelectCapabilityTest.class,
            GraphPatternCapabilityTest.class,
            SolutionModifierTest.class,
            AggregateTest.class,
            PropertyPathTest.class,
            QueryFormTest.class,
            SparqlUpdateTest.class,
            OutputFormatTest.class,
            FilterFunctionTest.class,
            QLeverExtensionTest.class,
            ErrorHandlingTest.class
        );
    }

    /**
     * Returns the shared QLever test engine instance.
     */
    protected static QLeverEmbeddedSparqlEngine engine() {
        return QLeverTestNode.engine();
    }

    /**
     * Builds the test dataset (200+ triples via INSERT DATA).
     * Includes organizations, people, and relationships.
     *
     * @return SPARQL INSERT DATA command
     */
    private static String buildDataset() {
        StringBuilder sb = new StringBuilder();
        sb.append("INSERT DATA {\n");

        sb.append("  :acme a :Organization ; :name \"ACME Corp\"@en ; :partOf :megacorp .\n");
        sb.append("  :megacorp a :Organization ; :name \"MegaCorp\"@en .\n");
        sb.append("  :startup a :Organization ; :name \"Startup Inc\"@en ; :partOf :megacorp .\n");

        String[] orgs = {":acme", ":megacorp", ":startup"};
        String[] textTemplates = {
            "works on climate change research",
            "studies renewable energy solutions",
            "leads the climate policy team",
            "develops sustainable technology",
            "researches carbon capture methods",
            "analyzes environmental data"
        };
        String[] firstNames = {
            "Alice", "Bob", "Carol", "Dave", "Eve", "Frank", "Grace", "Henry",
            "Iris", "Jack", "Kate", "Leo", "Mia", "Noah", "Olivia", "Paul",
            "Quinn", "Rose", "Sam", "Tina", "Uma", "Victor", "Wendy", "Xander",
            "Yara", "Zoe", "Aaron", "Bella", "Carlos", "Diana", "Ethan", "Fiona",
            "George", "Hannah", "Ivan", "Julia", "Kevin", "Laura", "Mark", "Nina",
            "Oscar", "Petra", "Rafael", "Sandra", "Thomas", "Ursula", "Vincent", "Wanda",
            "Xavier", "Yasmin", "Zack", "Amy", "Ben", "Clara", "Derek", "Elsa",
            "Felix", "Gloria", "Hugo", "Isabella"
        };

        sb.append("  :alice a :Person ;\n")
          .append("    :name \"Alice\"@en, \"Alicia\"@es ;\n")
          .append("    :age 30 ;\n")
          .append("    :worksFor :acme ;\n")
          .append("    :text \"Alice works on climate change research\" .\n");
        sb.append("  :bob a :Person ;\n")
          .append("    :name \"Bob\"@en, \"Roberto\"@de ;\n")
          .append("    :age 25 ;\n")
          .append("    :worksFor :acme ;\n")
          .append("    :text \"Bob studies renewable energy solutions\" .\n");
        sb.append("  :carol a :Person ;\n")
          .append("    :name \"Carol\"@en ;\n")
          .append("    :age 35 ;\n")
          .append("    :worksFor :megacorp ;\n")
          .append("    :text \"Carol leads the climate policy team\" .\n");

        sb.append("  GRAPH :graph1 { :alice :knows :bob }\n");
        sb.append("  GRAPH :graph2 { :bob :knows :carol }\n");

        for (int i = 3; i < firstNames.length; i++) {
            String localName = ":" + firstNames[i].toLowerCase();
            String org = orgs[i % orgs.length];
            int age = 20 + (i % 31);
            String text = textTemplates[i % textTemplates.length];
            String name = firstNames[i];
            sb.append("  ").append(localName).append(" a :Person ;\n")
              .append("    :name \"").append(name).append("\"@en ;\n")
              .append("    :age ").append(age).append(" ;\n")
              .append("    :worksFor ").append(org).append(" ;\n")
              .append("    :text \"").append(name).append(" ").append(text).append("\" .\n");
        }

        sb.append("  :alice :category :science .\n");
        sb.append("  :bob   :category :science .\n");
        sb.append("  :carol :category :policy .\n");

        sb.append("}");
        return sb.toString();
    }

    /**
     * Parses JSON results from a string.
     *
     * @param data JSON string
     * @return parsed JsonNode
     * @throws Exception if parsing fails
     */
    protected JsonNode parseJson(String data) throws Exception {
        return MAPPER.readTree(data);
    }

    /**
     * Extracts result rows from a QLeverResult.
     *
     * @param result the QLeverResult
     * @return list of binding rows
     * @throws Exception if parsing fails
     */
    protected List<JsonNode> rows(QLeverResult result) throws Exception {
        JsonNode root = parseJson(result.data());
        List<JsonNode> rows = new ArrayList<>();
        root.at("/results/bindings").forEach(rows::add);
        return rows;
    }

    /**
     * Gets all variable names from a JSON result.
     *
     * @param json the JSON result object
     * @return list of variable names
     */
    protected List<String> allVarNames(JsonNode json) {
        List<String> names = new ArrayList<>();
        json.at("/head/vars").forEach(v -> names.add(v.asText()));
        return names;
    }

    /**
     * Gets a single integer value from the first row.
     *
     * @param result the QLeverResult
     * @param varName the variable name
     * @return the integer value
     * @throws Exception if extraction fails
     */
    protected int singleIntValue(QLeverResult result, String varName) throws Exception {
        return rows(result).get(0).at("/" + varName + "/value").asInt();
    }

    /**
     * Gets a single string value from the first row.
     *
     * @param result the QLeverResult
     * @param varName the variable name
     * @return the string value
     * @throws Exception if extraction fails
     */
    protected String singleValue(QLeverResult result, String varName) throws Exception {
        return rows(result).get(0).at("/" + varName + "/value").asText();
    }

    /**
     * Gets a value from the first row.
     *
     * @param result the QLeverResult
     * @param varName the variable name
     * @return the string value
     * @throws Exception if extraction fails
     */
    protected String firstValue(QLeverResult result, String varName) throws Exception {
        return rows(result).get(0).at("/" + varName + "/value").asText();
    }

    /**
     * Gets a value from the second row.
     *
     * @param result the QLeverResult
     * @param varName the variable name
     * @return the string value
     * @throws Exception if extraction fails
     */
    protected String secondValue(QLeverResult result, String varName) throws Exception {
        return rows(result).get(1).at("/" + varName + "/value").asText();
    }

    /**
     * Gets all values for a given variable.
     *
     * @param result the QLeverResult
     * @param varName the variable name
     * @return list of string values
     * @throws Exception if extraction fails
     */
    protected List<String> values(QLeverResult result, String varName) throws Exception {
        return rows(result).stream()
            .map(row -> row.at("/" + varName + "/value").asText())
            .collect(Collectors.toList());
    }

    /**
     * Gets all integer values for a given variable.
     *
     * @param result the QLeverResult
     * @param varName the variable name
     * @return list of integer values
     * @throws Exception if extraction fails
     */
    protected List<Integer> intColumn(QLeverResult result, String varName) throws Exception {
        return rows(result).stream()
            .map(row -> row.at("/" + varName + "/value").asInt())
            .collect(Collectors.toList());
    }

    /**
     * Gets all double values for a given variable.
     *
     * @param result the QLeverResult
     * @param varName the variable name
     * @return list of double values
     * @throws Exception if extraction fails
     */
    protected List<Double> doubleColumn(QLeverResult result, String varName) throws Exception {
        return rows(result).stream()
            .map(row -> row.at("/" + varName + "/value").asDouble())
            .collect(Collectors.toList());
    }

    /**
     * Gets all subject URIs from a result.
     *
     * @param result the QLeverResult
     * @return set of subject URIs
     * @throws Exception if extraction fails
     */
    protected Set<String> subjects(QLeverResult result) throws Exception {
        return new HashSet<>(values(result, "s"));
    }

    /**
     * Gets all organization URIs from a result.
     *
     * @param result the QLeverResult
     * @return set of organization URIs
     * @throws Exception if extraction fails
     */
    protected Set<String> orgSet(QLeverResult result) throws Exception {
        return new HashSet<>(values(result, "org"));
    }

    /**
     * Gets an integer value from a JSON row.
     *
     * @param row the JSON row object
     * @param varName the variable name
     * @return the integer value
     */
    protected int intValue(JsonNode row, String varName) {
        return row.at("/" + varName + "/value").asInt();
    }

    /**
     * Gets a string value from a JSON row.
     *
     * @param row the JSON row object
     * @param varName the variable name
     * @return the string value
     */
    protected String stringValue(JsonNode row, String varName) {
        return row.at("/" + varName + "/value").asText();
    }

    /**
     * Gets the language tag from a JSON row value.
     *
     * @param row the JSON row object
     * @param varName the variable name
     * @return the language tag (e.g., "en", "de")
     */
    protected String langTag(JsonNode row, String varName) {
        return row.at("/" + varName + "/xml:lang").asText();
    }
}
