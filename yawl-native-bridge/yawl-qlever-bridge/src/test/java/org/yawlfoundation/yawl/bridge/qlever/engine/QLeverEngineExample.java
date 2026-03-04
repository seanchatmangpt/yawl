package org.yawlfoundation.yawl.bridge.qlever.engine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Example usage of the QLeverEngine API
 *
 * This demonstrates how to use the pure Java API for executing SPARQL queries
 * using the QLever engine through the native bridge.
 */
public class QLeverEngineExample {

    public static void main(String[] args) {
        String indexPath = "/path/to/qlever/index";

        // Example 1: Basic usage with try-with-resources
        try (QLeverEngine engine = QLeverEngine.create(indexPath)) {
            System.out.println("QLever Engine created successfully");
            System.out.println("Version: " + engine.getVersion());

            // Example 2: Execute an ASK query
            askQueryExample(engine);

            // Example 3: Execute a SELECT query
            selectQueryExample(engine);

            // Example 4: Execute a CONSTRUCT query
            constructQueryExample(engine);

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private static void askQueryExample(QLeverEngine engine) {
        System.out.println("\n=== ASK Query Example ===");

        String query = "ASK WHERE { ?s ?p ?o }";

        try {
            AskResult result = engine.ask(query);

            System.out.println("Query: " + query);
            System.out.println("Result: " + result.value());
            System.out.println("Is True: " + result.isTrue());
            System.out.println("Is False: " + result.isFalse());

        } catch (Exception e) {
            System.err.println("ASK query failed: " + e.getMessage());
        }
    }

    private static void selectQueryExample(QLeverEngine engine) {
        System.out.println("\n=== SELECT Query Example ===");

        String query = "SELECT ?subject ?predicate ?object WHERE { ?subject ?predicate ?object } LIMIT 10";

        try {
            SelectResult result = engine.select(query);

            System.out.println("Query: " + query);
            System.out.println("Columns: " + result.getVariables());
            System.out.println("Row count: " + result.getRowCount());

            // Print results
            for (int i = 0; i < result.getRowCount(); i++) {
                System.out.println("Row " + i + ":");
                for (int j = 0; j < result.getColumnCount(); j++) {
                    String value = result.getValue(i, j);
                    String varName = result.getVariables().get(j);
                    System.out.println("  " + varName + ": " + value);
                }
            }

            // Access by variable name
            if (result.getRowCount() > 0) {
                String subject = result.getValue(0, "subject");
                System.out.println("First subject: " + subject);
            }

        } catch (Exception e) {
            System.err.println("SELECT query failed: " + e.getMessage());
        }
    }

    private static void constructQueryExample(QLeverEngine engine) {
        System.out.println("\n=== CONSTRUCT Query Example ===");

        String query = "CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o } LIMIT 5";

        try {
            ConstructResult result = engine.construct(query);

            System.out.println("Query: " + query);
            System.out.println("Triple count: " + result.getTripleCount());

            // Print triples
            for (Triple triple : result.getTriples()) {
                System.out.println("Triple: " + triple.subject() + " " + triple.predicate() + " " + triple.object());
            }

            // Get Turtle representation
            if (result.hasContent()) {
                System.out.println("\nTurtle representation:");
                System.out.println(result.getTurtleResult());
            }

        } catch (Exception e) {
            System.err.println("CONSTRUCT query failed: " + e.getMessage());
        }
    }

    /**
     * Example of creating a simple index directory
     */
    private static void createExampleIndex() throws IOException {
        Path indexPath = Paths.get("/tmp/yawl-qlever-index");

        // Create index directory if it doesn't exist
        if (!Files.exists(indexPath)) {
            Files.createDirectories(indexPath);

            // Create minimal index files (this is just an example)
            // In a real scenario, you would have proper QLever index files
            Files.writeString(indexPath.resolve("index.properties"),
                "index.name=yawl-test\n" +
                "index.type=plain\n" +
                "index.path=/tmp/yawl-data");
        }
    }

    /**
     * Example of error handling
     */
    private static void errorHandlingExample() {
        System.out.println("\n=== Error Handling Example ===");

        try (QLeverEngine engine = QLeverEngine.create("/tmp/nonexistent-index")) {
            // This will likely fail due to nonexistent index
            AskResult result = engine.ask("ASK { ?s ?p ?o }");
            System.out.println("Result: " + result.value());

        } catch (Exception e) {
            System.err.println("Expected error caught: " + e.getClass().getSimpleName());
            System.err.println("Error message: " + e.getMessage());
        }
    }

    /**
     * Example of validation
     */
    private static void validationExample(QLeverEngine engine) {
        System.out.println("\n=== Query Validation Example ===");

        // Valid query
        String validQuery = "SELECT ?s WHERE { ?s ?p ?o }";
        boolean isValid = engine.validateQuery(validQuery);
        System.out.println("Valid query: " + validQuery);
        System.out.println("Is valid: " + isValid);

        // Invalid query
        String invalidQuery = "INVALID SPARQL QUERY";
        isValid = engine.validateQuery(invalidQuery);
        System.out.println("Invalid query: " + invalidQuery);
        System.out.println("Is valid: " + isValid);
    }
}