package org.yawlfoundation.yawl.benchmark.sparql;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.QLeverSparqlEngine;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.OxigraphSparqlEngine;
import org.yawlfoundation.yawl.benchmark.sparql.RdfDataGenerator;

/**
 * Simple test to verify our SPARQL engine classes work
 */
class TestSparqlEngines {

    @Test
    void testQLeverHttpCreation() {
        // This should compile even if QLever is not running
        // We're just testing the class can be instantiated
        assertDoesNotThrow(() -> {
            QLeverSparqlEngine engine = new QLeverSparqlEngine("http://localhost:7001");
            assertFalse(engine.isAvailable());
        });
    }

    @Test
    void testOxigraphCreation() {
        assertDoesNotThrow(() -> {
            OxigraphSparqlEngine engine = new OxigraphSparqlEngine("http://localhost:8083");
            assertFalse(engine.isAvailable());
        });
    }

    @Test
    void testRdfDataGenerator() throws Exception {
        RdfDataGenerator generator = new RdfDataGenerator();
        assertDoesNotThrow(() -> {
            generator.generateDataset(1000, java.nio.file.Paths.get("test-output.ttl"));
        });
    }
}
