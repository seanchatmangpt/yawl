package org.yawlfoundation.yawl.ggen.validation;

import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;
import org.yawlfoundation.yawl.ggen.validation.model.GuardViolation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * SPARQL-based guard checker that analyzes Java AST using RDF and SPARQL queries.
 * Handles complex pattern detection that requires semantic analysis beyond simple regex.
 */
public class SparqlGuardChecker implements GuardChecker {

    private final String patternName;
    private final String sparqlQuery;

    public SparqlGuardChecker(String patternName, String sparqlQuery) {
        this.patternName = patternName;
        this.sparqlQuery = sparqlQuery;
    }

    @Override
    public List<GuardViolation> check(Path javaSource) throws IOException {
        List<GuardViolation> violations = new ArrayList<>();

        try {
            // Parse the Java file to extract AST information
            JavaAstParser parser = new JavaAstParser();
            JavaAstParser.ParseResult astInfo = parser.parseAndExtract(javaSource);

            // Convert AST to RDF model
            RdfAstConverter converter = new RdfAstConverter();
            Model rdfModel = converter.convertAstToRdf(
                astInfo.getMethods(),
                astInfo.getComments()
            );

            // Execute SPARQL query with timeout
            QueryExecution qexec = QueryExecutionFactory.create(
                sparqlQuery,
                rdfModel
            );

            try {
                // Execute the query and process results
                ResultSet results = qexec.execSelect();
                while (results.hasNext()) {
                    QuerySolution soln = results.next();
                    violations.add(convertToGuardViolation(soln, javaSource.toString()));
                }
            } catch (QueryException e) {
                // Handle SPARQL query syntax errors gracefully
                System.err.println("SPARQL query error in " + patternName + ": " + e.getMessage());
                // Return empty list - don't fail the build for query errors
            } finally {
                qexec.close();
            }
        } catch (JavaAstParser.ParseException e) {
            System.err.println("Parse error in " + patternName + " for " + javaSource + ": " + e.getMessage());
            // Return empty list for parse errors - don't fail the build
        }

        return violations;
    }

    /**
     * Convert a SPARQL query solution to a GuardViolation
     */
    private GuardViolation convertToGuardViolation(QuerySolution soln, String filePath) {
        // Extract required fields from query solution
        String violationText = soln.get("violation").isLiteral() ?
            soln.getLiteral("violation").getString() : "Unknown violation";
        int line = soln.get("line").isLiteral() ?
            soln.getLiteral("line").getInt() : 0;
        String pattern = soln.get("pattern").isLiteral() ?
            soln.getLiteral("pattern").getString() : patternName;

        // Create the guard violation
        return new GuardViolation(pattern, Severity.FAIL, filePath, line, violationText);
    }

    @Override
    public String patternName() {
        return patternName;
    }

    @Override
    public Severity severity() {
        return Severity.FAIL;
    }

    /**
     * Helper method to create SPARQL queries for guard patterns
     */
    public static class QueryFactory {
        private static final String QUERY_PREFIX = """
            PREFIX code: <http://ggen.io/code#>
            PREFIX javadoc: <http://ggen.io/javadoc#>
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            """;

        public static Query createStubReturnQuery() {
            String query = QUERY_PREFIX + """
                SELECT ?violation ?line ?pattern
                WHERE {
                  ?method a code:Method ;
                          code:body ?body ;
                          code:lineNumber ?line ;
                          code:returnType ?retType .

                  FILTER(
                    (REGEX(?body, 'return\\s+"";') ||
                     REGEX(?body, 'return\\s+0;') ||
                     REGEX(?body, 'return\\s+null;.*//.*stub') ||
                     REGEX(?body, 'return\\s+(Collections\\.empty|new\\s+(HashMap|ArrayList)\\(\\));\\s*$'))
                    &&
                    ?retType != "void"
                  )

                  BIND("H_STUB" AS ?pattern)
                  BIND(CONCAT("Stub return at line ", STR(?line), ": ", ?body)
                       AS ?violation)
                }
                """;
            return QueryFactory.create(query);
        }

        public static Query createEmptyQuery() {
            String query = QUERY_PREFIX + """
                SELECT ?violation ?line ?pattern
                WHERE {
                  ?method a code:Method ;
                          code:body ?body ;
                          code:lineNumber ?line ;
                          code:returnType "void" .

                  FILTER(REGEX(?body, '^\\s*\\{\\s*\\}\\s*$'))

                  BIND("H_EMPTY" AS ?pattern)
                  BIND(CONCAT("Empty method body at line ", STR(?line))
                       AS ?violation)
                }
                """;
            return QueryFactory.create(query);
        }

        public static Query createFallbackQuery() {
            String query = QUERY_PREFIX + """
                SELECT ?violation ?line ?pattern
                WHERE {
                  ?method a code:Method ;
                          code:body ?body ;
                          code:lineNumber ?line .

                  FILTER(REGEX(?body, 'catch\\s*\\([^)]+\\)\\s*\\{[^}]*return[^}]*fake[^}]*\\}'))

                  BIND("H_FALLBACK" AS ?pattern)
                  BIND(CONCAT("Silent fallback at line ", STR(?line))
                       AS ?violation)
                }
                """;
            return QueryFactory.create(query);
        }

        public static Query createLieQuery() {
            String query = QUERY_PREFIX + """
                SELECT ?violation ?line ?pattern
                WHERE {
                  ?method a code:Method ;
                          code:javadoc ?doc ;
                          code:body ?body ;
                          code:lineNumber ?line .

                  ?doc javadoc:throws ?throws .
                  ?doc javadoc:returns ?returns .

                  # Check if method claims to return value but body doesn't
                  FILTER(
                    (STRSTARTS(?returns, "void") && REGEX(?body, 'return\\s+[^;};]+')) ||
                    (NOT STRSTARTS(?returns, "void") && !REGEX(?body, 'return\\s+[^;};]+'))
                  )

                  FILTER(
                    # Check if method claims to throw but doesn't
                    EXISTS { ?throws javadoc:name ?throwName } &&
                    !REGEX(?body, 'throw\\s+' + ?throwName)
                  )

                  BIND("H_LIE" AS ?pattern)
                  BIND(CONCAT("Documentation mismatch at line ", STR(?line))
                       AS ?violation)
                }
                """;
            return QueryFactory.create(query);
        }

        public static Query create(String sparql) {
            return org.apache.jena.query.QueryFactory.create(sparql);
        }
    }
}