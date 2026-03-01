package org.yawlfoundation.yawl.datamodelling;

import org.junit.jupiter.api.*;
import org.yawlfoundation.yawl.datamodelling.api.DataModellingService;
import org.yawlfoundation.yawl.datamodelling.generated.data_modelling_ffi_h;
import org.yawlfoundation.yawl.datamodelling.model.Sketch;
import org.yawlfoundation.yawl.datamodelling.model.SketchIndex;
import org.yawlfoundation.yawl.datamodelling.model.SketchType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.yawlfoundation.yawl.datamodelling.Capability.*;
import static org.yawlfoundation.yawl.datamodelling.test.DataModellingTestFixtures.*;

/**
 * Group J — Sketch Operations Capability Tests (8 capabilities).
 *
 * <p>Tests cover:
 * - PARSE_SKETCH_YAML: Parse YAML sketch definition into Sketch record
 * - PARSE_SKETCH_INDEX_YAML: Parse YAML sketch index into SketchIndex record
 * - EXPORT_SKETCH_TO_YAML: Export Sketch to YAML format
 * - EXPORT_SKETCH_INDEX_TO_YAML: Export SketchIndex to YAML format
 * - CREATE_SKETCH: Create new sketch with name, type, and description
 * - CREATE_SKETCH_INDEX: Create new empty sketch index with name
 * - ADD_SKETCH_TO_INDEX: Add sketch to index
 * - SEARCH_SKETCHES: Search sketches by query string in index
 *
 * <p>These tests use real sketch fixtures and assert structural properties
 * (non-null results, proper record fields, roundtrip preservation) without
 * mocking or stubbing the native bridge.
 *
 * <p>Skips if native library is not present (UnsupportedOperationException expected).
 */
@Tag("capability")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SketchCapabilityTest {

    DataModellingService service;

    @BeforeAll
    void setup() {
        assumeTrue(data_modelling_ffi_h.LIBRARY.isPresent(),
            "Native library not present — skipping Sketch tests");
        service = DataModellingModule.create();
    }

    @AfterAll
    void teardown() throws Exception {
        if (service != null) {
            service.close();
        }
    }

    @Nested
    @DisplayName("PARSE_SKETCH_YAML")
    class ParseSketchYaml {

        @Test
        @CapabilityTest(PARSE_SKETCH_YAML)
        @DisplayName("valid YAML returns non-null Sketch")
        void validYaml_returnsNonNullSketch() throws Exception {
            Sketch sketch = service.parseSketchYaml(SKETCH_YAML);
            assertNotNull(sketch, "Sketch parsing must return non-null Sketch");
        }

        @Test
        @CapabilityTest(PARSE_SKETCH_YAML)
        @DisplayName("parsed sketch has correct name")
        void parsedSketch_hasCorrectName() throws Exception {
            Sketch sketch = service.parseSketchYaml(SKETCH_YAML);
            assertNotNull(sketch.name(), "Parsed sketch must have non-null name");
            assertEquals("Order Flow Architecture", sketch.name(),
                "Parsed sketch name must match fixture content");
        }

        @Test
        @CapabilityTest(PARSE_SKETCH_YAML)
        @DisplayName("invalid YAML throws DataModellingException")
        void invalidYaml_throwsDataModellingException() {
            assertThrows(DataModellingException.class,
                () -> service.parseSketchYaml("not: valid: sketch: yaml:::"),
                "Invalid YAML must throw DataModellingException");
        }
    }

    @Nested
    @DisplayName("PARSE_SKETCH_INDEX_YAML")
    class ParseSketchIndexYaml {

        @Test
        @CapabilityTest(PARSE_SKETCH_INDEX_YAML)
        @DisplayName("valid YAML returns non-null SketchIndex")
        void validYaml_returnsNonNullIndex() throws Exception {
            SketchIndex index = service.parseSketchIndexYaml(SKETCH_INDEX_YAML);
            assertNotNull(index, "SketchIndex parsing must return non-null SketchIndex");
        }

        @Test
        @CapabilityTest(PARSE_SKETCH_INDEX_YAML)
        @DisplayName("parsed index has non-null name")
        void parsedIndex_hasNonNullName() throws Exception {
            SketchIndex index = service.parseSketchIndexYaml(SKETCH_INDEX_YAML);
            assertNotNull(index.name(), "Parsed SketchIndex must have non-null name");
        }

        @Test
        @CapabilityTest(PARSE_SKETCH_INDEX_YAML)
        @DisplayName("parsed index has non-null sketches list")
        void parsedIndex_hasNonNullSketches() throws Exception {
            SketchIndex index = service.parseSketchIndexYaml(SKETCH_INDEX_YAML);
            assertNotNull(index.sketches(), "Parsed SketchIndex must have non-null sketches list");
        }
    }

    @Nested
    @DisplayName("CREATE_SKETCH")
    class CreateSketch {

        @Test
        @CapabilityTest(CREATE_SKETCH)
        @DisplayName("creates sketch with name preserved")
        void createSketch_namePreserved() throws Exception {
            Sketch sketch = service.createSketch("Order Flow", "ENTITY_RELATIONSHIP", "An order flow diagram");
            assertNotNull(sketch, "createSketch must return non-null Sketch");
            assertEquals("Order Flow", sketch.name(), "Created sketch name must match input");
        }

        @Test
        @CapabilityTest(CREATE_SKETCH)
        @DisplayName("creates sketch with correct type enum")
        void createSketch_typePreservedAsEnum() throws Exception {
            Sketch sketch = service.createSketch("Test Sketch", "DOMAIN_MODEL", "A domain model");
            assertNotNull(sketch, "createSketch must return non-null Sketch");
            assertEquals(SketchType.DOMAIN_MODEL, sketch.type(),
                "Created sketch type must match input string as enum");
        }

        @Test
        @CapabilityTest(CREATE_SKETCH)
        @DisplayName("creates sketch with null description allowed")
        void createSketch_nullDescription_doesNotThrow() throws Exception {
            Sketch sketch = service.createSketch("Minimal Sketch", "SCHEMA", null);
            assertNotNull(sketch, "createSketch must return non-null Sketch");
            assertEquals("Minimal Sketch", sketch.name(), "Sketch name must be preserved with null description");
        }
    }

    @Nested
    @DisplayName("CREATE_SKETCH_INDEX")
    class CreateSketchIndex {

        @Test
        @CapabilityTest(CREATE_SKETCH_INDEX)
        @DisplayName("creates index with name preserved")
        void createSketchIndex_namePreserved() throws Exception {
            SketchIndex index = service.createSketchIndex("default-index");
            assertNotNull(index, "createSketchIndex must return non-null SketchIndex");
            assertEquals("default-index", index.name(), "Created index name must match input");
        }

        @Test
        @CapabilityTest(CREATE_SKETCH_INDEX)
        @DisplayName("creates empty index")
        void createSketchIndex_startsEmpty() throws Exception {
            SketchIndex index = service.createSketchIndex("my-index");
            assertNotNull(index, "createSketchIndex must return non-null SketchIndex");
            assertTrue(index.sketches().isEmpty(), "New sketch index must start with empty sketches list");
        }
    }

    @Nested
    @DisplayName("ADD_SKETCH_TO_INDEX")
    class AddSketchToIndex {

        @Test
        @CapabilityTest(ADD_SKETCH_TO_INDEX)
        @DisplayName("adds single sketch to index")
        void addSketchToIndex_singleSketch_countIsOne() throws Exception {
            SketchIndex index = service.createSketchIndex("test-idx");
            Sketch sketch = service.createSketch("S1", "ENTITY_RELATIONSHIP", "First sketch");
            SketchIndex updated = service.addSketchToIndex(index, sketch);
            assertNotNull(updated, "addSketchToIndex must return non-null SketchIndex");
            assertEquals(1, updated.sketches().size(), "Index must contain exactly one sketch after add");
        }

        @Test
        @CapabilityTest(ADD_SKETCH_TO_INDEX)
        @DisplayName("adds multiple sketches to index")
        void addSketchToIndex_multipleSketches_countMatches() throws Exception {
            SketchIndex index = service.createSketchIndex("test-idx");
            Sketch s1 = service.createSketch("S1", "ENTITY_RELATIONSHIP", "Sketch one");
            Sketch s2 = service.createSketch("S2", "DOMAIN_MODEL", "Sketch two");
            SketchIndex step1 = service.addSketchToIndex(index, s1);
            SketchIndex step2 = service.addSketchToIndex(step1, s2);
            assertEquals(2, step2.sketches().size(), "Index must contain exactly two sketches after two adds");
        }
    }

    @Nested
    @DisplayName("SEARCH_SKETCHES")
    class SearchSketches {

        @Test
        @CapabilityTest(SEARCH_SKETCHES)
        @DisplayName("search returns matching results")
        void searchSketches_matchingQuery_returnsResults() throws Exception {
            SketchIndex index = service.createSketchIndex("search-idx");
            Sketch s1 = service.createSketch("Order Architecture", "ENTITY_RELATIONSHIP", "Order flow");
            Sketch s2 = service.createSketch("Customer Journey", "DOMAIN_MODEL", "Customer domain");
            SketchIndex idx = service.addSketchToIndex(
                service.addSketchToIndex(index, s1), s2);
            List<Sketch> results = service.searchSketches(idx, "Order");
            assertNotNull(results, "searchSketches must return non-null List");
            assertFalse(results.isEmpty(), "Search for 'Order' must return non-empty results");
            assertTrue(results.stream().anyMatch(s -> s.name().contains("Order")),
                "Results must contain sketch with 'Order' in name");
        }

        @Test
        @CapabilityTest(SEARCH_SKETCHES)
        @DisplayName("search returns empty for non-matching query")
        void searchSketches_noMatch_returnsEmptyList() throws Exception {
            SketchIndex index = service.createSketchIndex("search-idx");
            Sketch sketch = service.createSketch("Order Flow", "ENTITY_RELATIONSHIP", "Order flow diagram");
            SketchIndex idx = service.addSketchToIndex(index, sketch);
            List<Sketch> results = service.searchSketches(idx, "zzz-no-match-xyz");
            assertNotNull(results, "searchSketches must return non-null List");
            assertTrue(results.isEmpty(), "Non-matching search must return empty list");
        }
    }

    @Nested
    @DisplayName("EXPORT_SKETCH_TO_YAML")
    class ExportSketchToYaml {

        @Test
        @CapabilityTest(EXPORT_SKETCH_TO_YAML)
        @DisplayName("roundtrip preserves sketch name")
        void exportSketchToYaml_roundtrip_namePreserved() throws Exception {
            Sketch original = service.createSketch("Roundtrip Test", "SCHEMA", "Test description");
            String yaml = service.exportSketchToYaml(original);
            assertNotNull(yaml, "exportSketchToYaml must return non-null String");
            assertFalse(yaml.isBlank(), "Exported YAML must not be blank");
            Sketch reimport = service.parseSketchYaml(yaml);
            assertEquals("Roundtrip Test", reimport.name(),
                "Roundtrip must preserve sketch name");
        }

        @Test
        @CapabilityTest(EXPORT_SKETCH_TO_YAML)
        @DisplayName("roundtrip preserves sketch type")
        void exportSketchToYaml_roundtrip_typePreserved() throws Exception {
            Sketch original = service.createSketch("Type Test", "DATA_FLOW", "Data flow diagram");
            String yaml = service.exportSketchToYaml(original);
            Sketch reimport = service.parseSketchYaml(yaml);
            assertEquals(SketchType.DATA_FLOW, reimport.type(),
                "Roundtrip must preserve sketch type");
        }
    }

    @Nested
    @DisplayName("EXPORT_SKETCH_INDEX_TO_YAML")
    class ExportSketchIndexToYaml {

        @Test
        @CapabilityTest(EXPORT_SKETCH_INDEX_TO_YAML)
        @DisplayName("roundtrip preserves sketch count in index")
        void exportSketchIndexToYaml_roundtrip_sketchCountPreserved() throws Exception {
            Sketch sketch = service.createSketch("Index Test", "ENTITY_RELATIONSHIP", "Test sketch");
            SketchIndex index = service.addSketchToIndex(
                service.createSketchIndex("rt-idx"), sketch);
            String yaml = service.exportSketchIndexToYaml(index);
            assertNotNull(yaml, "exportSketchIndexToYaml must return non-null String");
            assertFalse(yaml.isBlank(), "Exported index YAML must not be blank");
            SketchIndex reimport = service.parseSketchIndexYaml(yaml);
            assertEquals(1, reimport.sketches().size(),
                "Roundtrip must preserve sketch count in index");
        }

        @Test
        @CapabilityTest(EXPORT_SKETCH_INDEX_TO_YAML)
        @DisplayName("roundtrip preserves empty index")
        void exportSketchIndexToYaml_emptyIndex_roundtripSucceeds() throws Exception {
            SketchIndex empty = service.createSketchIndex("empty-idx");
            String yaml = service.exportSketchIndexToYaml(empty);
            SketchIndex reimport = service.parseSketchIndexYaml(yaml);
            assertTrue(reimport.sketches().isEmpty(),
                "Roundtrip must preserve empty sketches list");
        }
    }
}
