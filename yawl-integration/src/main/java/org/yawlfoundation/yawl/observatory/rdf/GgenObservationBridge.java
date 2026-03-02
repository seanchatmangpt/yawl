/**
 * GgenObservationBridge — Integrates observatory facts with ggen code generation
 *
 * Provides ggen with RDF representation of YAWL codebase structure for context-aware
 * code generation.
 *
 * Pipeline:
 *   1. observatory.sh → facts/*.json (codebase analysis)
 *   2. FactsToRDFConverter → facts.ttl (RDF/Turtle)
 *   3. DriftDetector → checks for changes
 *   4. GgenObservationBridge → queries RDF facts
 *   5. ggen templates → use SPARQL queries for code generation context
 *
 * ggen template example:
 *   {% set modules = sparql("
 *     SELECT ?moduleName ?testCount
 *     WHERE {
 *       ?m ex:moduleName ?moduleName ;
 *           ex:testCount ?testCount .
 *       FILTER (?testCount > 100)
 *     }
 *   ") %}
 *
 *   // High-test-count modules:
 *   {% for mod in modules %}
 *     // {{ mod.moduleName }}: {{ mod.testCount }} tests
 *   {% endfor %}
 *
 * Features:
 *   - Load and query facts.ttl via Jena SPARQL endpoint
 *   - Expose SPARQL query execution to Tera templates
 *   - Cache RDF model for performance (reuse across template runs)
 *   - Validate facts against ggen-observation.ttl ontology
 *   - Report query errors with context for debugging
 */
package org.yawlfoundation.yawl.observatory.rdf;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.riot.RDFDataMgr;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bridge between observatory facts and ggen code generation.
 *
 * Loads facts.ttl and provides SPARQL query interface for Tera templates.
 * Caches RDF model for reuse across multiple template calls.
 *
 * Usage:
 *   GgenObservationBridge bridge = new GgenObservationBridge();
 *   bridge.loadFacts(Paths.get("docs/v6/latest/facts.ttl"));
 *
 *   List<Map<String, String>> results = bridge.query(
 *     "SELECT ?moduleName WHERE { ?m ex:moduleName ?moduleName }"
 *   );
 *
 *   for (Map<String, String> row : results) {
 *       System.out.println("Module: " + row.get("moduleName"));
 *   }
 */
public class GgenObservationBridge {

    private Model factsModel;
    private Model ontologyModel;
    private boolean isInitialized = false;

    /**
     * Load facts.ttl (RDF model of codebase).
     *
     * @param factsTtlPath Path to facts.ttl file
     * @throws IOException if file cannot be read
     */
    public void loadFacts(Path factsTtlPath) throws IOException {
        if (!Files.exists(factsTtlPath)) {
            throw new IOException("Facts file not found: " + factsTtlPath);
        }

        factsModel = RDFDataMgr.loadModel(factsTtlPath.toString());
        isInitialized = true;

        System.out.println("Facts model loaded: " + factsTtlPath);
        System.out.println("  Statements: " + factsModel.size());
    }

    /**
     * Load ontology schema (ggen-observation.ttl) for validation.
     *
     * Optional but recommended for RDFS reasoning and constraint checking.
     *
     * @param ontologyPath Path to ggen-observation.ttl
     * @throws IOException if file cannot be read
     */
    public void loadOntology(Path ontologyPath) throws IOException {
        if (!Files.exists(ontologyPath)) {
            throw new IOException("Ontology file not found: " + ontologyPath);
        }

        ontologyModel = RDFDataMgr.loadModel(ontologyPath.toString());

        System.out.println("Ontology model loaded: " + ontologyPath);
        System.out.println("  Statements: " + ontologyModel.size());
    }

    /**
     * Execute SPARQL query against facts model.
     *
     * Returns results as list of maps (one map per result row).
     * Each map contains variable bindings (e.g., {"moduleName": "yawl-engine"}).
     *
     * @param sparqlQuery SPARQL SELECT query
     * @return List of result rows (each row is a map of variable bindings)
     * @throws IllegalStateException if facts model not loaded
     * @throws RuntimeException if query fails (parse error, execution error)
     */
    public List<Map<String, String>> query(String sparqlQuery) {
        if (!isInitialized) {
            throw new IllegalStateException("Facts model not loaded. Call loadFacts() first.");
        }

        List<Map<String, String>> results = new ArrayList<>();

        try {
            var queryObj = QueryFactory.create(sparqlQuery);

            try (QueryExecution qexec = QueryExecutionFactory.create(queryObj, factsModel)) {
                ResultSet resultSet = qexec.execSelect();

                while (resultSet.hasNext()) {
                    var solution = resultSet.nextSolution();
                    Map<String, String> row = new HashMap<>();

                    // Extract variable bindings
                    resultSet.getResultVars().forEach(varName -> {
                        RDFNode node = solution.get(varName);
                        if (node != null) {
                            if (node.isLiteral()) {
                                row.put(varName, node.asLiteral().getString());
                            } else if (node.isResource()) {
                                row.put(varName, node.asResource().getLocalName());
                            } else {
                                row.put(varName, node.toString());
                            }
                        }
                    });

                    results.add(row);
                }
            }

            return results;

        } catch (Exception e) {
            throw new RuntimeException("SPARQL query failed: " + e.getMessage() + "\n" +
                "Query: " + sparqlQuery, e);
        }
    }

    /**
     * Query for modules with specific property.
     *
     * Convenience method for common query pattern.
     *
     * @return List of module names
     */
    public List<String> getModules() {
        String query = """
            PREFIX ex: <http://yawlfoundation.org/facts#>
            SELECT ?moduleName
            WHERE {
                ?m ex:moduleName ?moduleName .
            }
            ORDER BY ?moduleName
            """;

        return query(query).stream()
            .map(row -> row.get("moduleName"))
            .toList();
    }

    /**
     * Query for module dependencies.
     *
     * Convenience method for common query pattern.
     *
     * @return List of dependency pairs (from, to)
     */
    public List<Map<String, String>> getDependencies() {
        String query = """
            PREFIX ex: <http://yawlfoundation.org/facts#>
            SELECT ?from ?to
            WHERE {
                ?fromModule ex:moduleName ?from ;
                            ex:dependsOn ?toModule .
                ?toModule ex:moduleName ?to .
            }
            ORDER BY ?from ?to
            """;

        return query(query);
    }

    /**
     * Query for circular dependencies (if any).
     *
     * @return List of circular dependency cycles
     */
    public List<Map<String, String>> findCircularDependencies() {
        String query = """
            PREFIX ex: <http://yawlfoundation.org/facts#>
            SELECT ?module1 ?module2 ?module3
            WHERE {
                ?m1 ex:moduleName ?module1 ;
                    ex:dependsOn ?m2 .
                ?m2 ex:moduleName ?module2 ;
                    ex:dependsOn ?m3 .
                ?m3 ex:moduleName ?module3 ;
                    ex:dependsOn ?m1 .
            }
            LIMIT 10
            """;

        return query(query);
    }

    /**
     * Query for modules below coverage target.
     *
     * @return List of modules with low coverage
     */
    public List<Map<String, String>> getLowCoverageModules() {
        String query = """
            PREFIX ex: <http://yawlfoundation.org/facts#>
            SELECT ?moduleName ?lineCoverage
            WHERE {
                ?module ex:moduleName ?moduleName ;
                        ex:lineCoverage ?lineCoverage .
                FILTER (?lineCoverage < 65)
            }
            ORDER BY ?lineCoverage
            """;

        return query(query);
    }

    /**
     * Query for integration points (MCP, A2A, ZAI).
     *
     * @return List of integration points with counts
     */
    public List<Map<String, String>> getIntegrationPoints() {
        String query = """
            PREFIX ex: <http://yawlfoundation.org/facts#>
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            SELECT ?integrationType ?server ?resourceCount
            WHERE {
                ?integration rdf:type ex:IntegrationPoint ;
                             ex:integrationType ?integrationType ;
                             ex:server ?server ;
                             (ex:toolCount | ex:skillCount) ?resourceCount .
            }
            """;

        return query(query);
    }

    /**
     * Validate facts model against ontology.
     *
     * Checks for schema violations (missing properties, type errors, etc.)
     * if ontology is loaded. Requires SHACL constraint checking.
     *
     * @return true if facts conform to ontology, false otherwise
     * @throws UnsupportedOperationException if SHACL library not available
     */
    public boolean validateAgainstOntology() {
        if (ontologyModel == null) {
            System.out.println("WARNING: Ontology not loaded. Skipping validation.");
            return true;
        }

        throw new UnsupportedOperationException(
            "Ontology validation requires SHACL library (org.apache.jena:jena-shacl). " +
            "Add dependency to pom.xml and implement SHACLValidation.validateModel(factsModel, ontologyModel). " +
            "Until then, use SPARQL queries (query method) to inspect facts directly."
        );
    }

    /**
     * Get model statistics for reporting.
     *
     * @return Map of statistics (statement count, class count, property count, etc.)
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("statementCount", factsModel.size());
        stats.put("resourceCount", (int) factsModel.listResourcesWithProperty(
            factsModel.getProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")).toList().size());

        // Count modules
        List<String> modules = getModules();
        stats.put("moduleCount", modules.size());

        // Count dependencies
        List<Map<String, String>> deps = getDependencies();
        stats.put("dependencyCount", deps.size());

        return stats;
    }

    /**
     * Check if facts model is loaded and ready.
     */
    public boolean isInitialized() {
        return isInitialized;
    }

    /**
     * Get underlying Jena model (for advanced usage).
     */
    public Model getModel() {
        return factsModel;
    }
}
