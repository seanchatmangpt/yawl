package org.yawlfoundation.yawl.datamodelling;

import org.junit.jupiter.api.*;
import org.yawlfoundation.yawl.datamodelling.generated.data_modelling_ffi_h;
import org.yawlfoundation.yawl.datamodelling.model.SqlDialect;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.yawlfoundation.yawl.datamodelling.test.DataModellingTestFixtures.*;

/**
 * End-to-end integration tests for {@link DataModellingModule}.
 * Exercises multi-capability pipelines and concurrent usage patterns.
 */
@Tag("integration")
class DataModellingIntegrationTest {

    private static void skip() {
        assumeTrue(data_modelling_ffi_h.LIBRARY.isPresent(),
            "Native library not present — skipping");
    }

    // ── Pipeline 1: SQL → ODCS YAML → Avro roundtrip ────────────────────────

    @Test
    void fullPipeline_sqlToOdcsYamlToAvroRoundtrip() throws Exception {
        skip();
        try (var svc = DataModellingModule.create()) {
            var ws1 = svc.importFromSql(SQL_POSTGRES, SqlDialect.POSTGRESQL);
            assertFalse(ws1.tables().isEmpty());

            var yaml = svc.exportToOdcsYaml(ws1);
            assertFalse(yaml.isBlank());

            var ws2 = svc.parseOdcsYaml(yaml);
            assertEquals(ws1.tables().size(), ws2.tables().size());

            var avro = svc.exportToAvro(ws1);
            assertFalse(avro.isBlank());
        }
    }

    // ── Pipeline 2: OpenAPI → ODCS → SQL ────────────────────────────────────

    @Test
    void fullPipeline_openapiToOdcsToSql() throws Exception {
        skip();
        try (var svc = DataModellingModule.create()) {
            var ws = svc.importOpenapiSpec(OPENAPI_YAML);
            assertFalse(ws.tables().isEmpty());

            var sql = svc.exportToSql(ws, SqlDialect.POSTGRESQL);
            assertTrue(sql.toUpperCase().contains("CREATE TABLE"));
        }
    }

    // ── Pipeline 3: Sketch lifecycle ─────────────────────────────────────────

    @Test
    void sketchLifecycle_createIndexSearchRoundtrip() throws Exception {
        skip();
        try (var svc = DataModellingModule.create()) {
            var s1 = svc.createSketch("Order Architecture",  "ENTITY_RELATIONSHIP", "Entity diagram");
            var s2 = svc.createSketch("Customer Journey",    "DOMAIN_MODEL",        "Customer flow");
            var s3 = svc.createSketch("Inventory Flow",      "DATA_FLOW",           "Inventory flow");

            var idx = svc.createSketchIndex("lifecycle-idx");
            idx = svc.addSketchToIndex(idx, s1);
            idx = svc.addSketchToIndex(idx, s2);
            idx = svc.addSketchToIndex(idx, s3);
            assertEquals(3, idx.sketches().size());

            var results = svc.searchSketches(idx, "Order");
            assertFalse(results.isEmpty());

            // Export/reimport roundtrip preserves sketch count
            var reimported = svc.parseSketchIndexYaml(svc.exportSketchIndexToYaml(idx));
            assertEquals(3, reimported.sketches().size());
        }
    }

    // ── Pipeline 4: Concurrent virtual-thread safety ─────────────────────────

    @Test
    void concurrentCalls_100VirtualThreads_noCorruption() throws Exception {
        skip();
        final int threadCount = 100;
        var latch = new CountDownLatch(threadCount);
        var errors = new AtomicInteger(0);
        var tableCountMismatches = new AtomicInteger(0);
        List<Thread> threads = new ArrayList<>(threadCount);

        try (var svc = DataModellingModule.create()) {
            IntStream.range(0, threadCount).forEach(i -> {
                var t = Thread.ofVirtual().start(() -> {
                    try {
                        var ws = svc.parseOdcsYaml(ODCS_YAML);
                        if (ws.tables().size() != 2) {
                            tableCountMismatches.incrementAndGet();
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
                threads.add(t);
            });

            latch.await();
            assertEquals(0, errors.get(), "Unexpected errors in virtual threads");
            assertEquals(0, tableCountMismatches.get(), "Table count mismatch detected across threads");
        }
    }
}
