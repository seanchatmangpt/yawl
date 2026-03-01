package org.yawlfoundation.yawl.datamodelling.bridge;

import org.junit.jupiter.api.*;
import org.yawlfoundation.yawl.datamodelling.Capability;
import org.yawlfoundation.yawl.datamodelling.CapabilityTest;
import org.yawlfoundation.yawl.datamodelling.generated.data_modelling_ffi_h;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.yawlfoundation.yawl.datamodelling.Capability.*;

/**
 * Integration tests for {@link DataModellingBridge}.
 * All tests skip if the native library is not loaded.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DataModellingBridgeTest {

    @BeforeEach
    void skipIfNoLib() {
        assumeTrue(data_modelling_ffi_h.LIBRARY.isPresent(),
            "Skipped: native library not loaded. Set -D" + data_modelling_ffi_h.LIB_PATH_PROP
                + "=/path/to/libdata_modelling_ffi.so");
    }

    @Test @CapabilityTest(PARSE_ODCS_YAML)
    void parseOdcsYaml_minimalYaml_returnsJson() {
        try (DataModellingBridge b = new DataModellingBridge()) {
            String result = b.parseOdcsYaml("apiVersion: v3.1.0\nkind: DataContract\nname: test");
            assertNotNull(result);
            assertFalse(result.isBlank());
        }
    }

    @Test @CapabilityTest(EXPORT_TO_ODCS_YAML)
    void exportToOdcsYaml_validJson_returnsYaml() {
        try (DataModellingBridge b = new DataModellingBridge()) {
            String result = b.exportToOdcsYaml("{\"name\":\"test\"}");
            assertNotNull(result);
        }
    }

    @Test @CapabilityTest(CONVERT_TO_ODCS)
    void convertToOdcs_validArgs_returnsJson() {
        try (DataModellingBridge b = new DataModellingBridge()) {
            String result = b.convertToOdcs("{}", "avro");
            assertNotNull(result);
        }
    }

    @Test @CapabilityTest(IMPORT_FROM_SQL)
    void importFromSql_validArgs_returnsJson() {
        try (DataModellingBridge b = new DataModellingBridge()) {
            String result = b.importFromSql("CREATE TABLE t (id INT)", "ANSI");
            assertNotNull(result);
        }
    }

    @Test @CapabilityTest(EXPORT_TO_SQL)
    void exportToSql_validArgs_returnsSql() {
        try (DataModellingBridge b = new DataModellingBridge()) {
            String result = b.exportToSql("{}", "ANSI");
            assertNotNull(result);
        }
    }

    @Test @CapabilityTest(IMPORT_FROM_AVRO)
    void importFromAvro_validSchema_returnsJson() {
        try (DataModellingBridge b = new DataModellingBridge()) {
            String result = b.importFromAvro("{\"type\":\"record\",\"name\":\"Test\",\"fields\":[]}");
            assertNotNull(result);
        }
    }

    @Test @CapabilityTest(IMPORT_FROM_JSON_SCHEMA)
    void importFromJsonSchema_validSchema_returnsJson() {
        try (DataModellingBridge b = new DataModellingBridge()) {
            String result = b.importFromJsonSchema("{\"$schema\":\"http://json-schema.org/draft-07/schema\"}");
            assertNotNull(result);
        }
    }

    @Test @CapabilityTest(IMPORT_FROM_PROTOBUF)
    void importFromProtobuf_validProto_returnsJson() {
        try (DataModellingBridge b = new DataModellingBridge()) {
            String result = b.importFromProtobuf("syntax = \"proto3\"; message Test {}");
            assertNotNull(result);
        }
    }

    @Test @CapabilityTest(IMPORT_FROM_CADS)
    void importFromCads_validJson_returnsJson() {
        try (DataModellingBridge b = new DataModellingBridge()) {
            String result = b.importFromCads("{}");
            assertNotNull(result);
        }
    }

    @Test @CapabilityTest(IMPORT_FROM_ODPS)
    void importFromOdps_validYaml_returnsJson() {
        try (DataModellingBridge b = new DataModellingBridge()) {
            String result = b.importFromOdps("version: 1.0");
            assertNotNull(result);
        }
    }

    @Test @CapabilityTest(EXPORT_TO_AVRO)
    void exportToAvro_validJson_returnsSchema() {
        try (DataModellingBridge b = new DataModellingBridge()) {
            String result = b.exportToAvro("{}");
            assertNotNull(result);
        }
    }

    @Test @CapabilityTest(EXPORT_TO_JSON_SCHEMA)
    void exportToJsonSchema_validJson_returnsSchema() {
        try (DataModellingBridge b = new DataModellingBridge()) {
            String result = b.exportToJsonSchema("{}");
            assertNotNull(result);
        }
    }

    @Test @CapabilityTest(EXPORT_TO_PROTOBUF)
    void exportToProtobuf_validJson_returnsProto() {
        try (DataModellingBridge b = new DataModellingBridge()) {
            String result = b.exportToProtobuf("{}");
            assertNotNull(result);
        }
    }

    @Test @CapabilityTest(EXPORT_TO_CADS)
    void exportToCads_validJson_returnsCads() {
        try (DataModellingBridge b = new DataModellingBridge()) {
            String result = b.exportToCads("{}");
            assertNotNull(result);
        }
    }

    @Test @CapabilityTest(EXPORT_TO_ODPS)
    void exportToOdps_validJson_returnsYaml() {
        try (DataModellingBridge b = new DataModellingBridge()) {
            String result = b.exportToOdps("{}");
            assertNotNull(result);
        }
    }

    @Test @CapabilityTest(VALIDATE_ODPS)
    void validateOdps_validYaml_doesNotThrow() {
        try (DataModellingBridge b = new DataModellingBridge()) {
            assertDoesNotThrow(() -> b.validateOdps("version: 1.0\nname: test"));
        }
    }

    @Test @CapabilityTest(IMPORT_BPMN_MODEL)
    void importBpmnModel_validXml_returnsJson() {
        try (DataModellingBridge b = new DataModellingBridge()) {
            String result = b.importBpmnModel("<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"/>");
            assertNotNull(result);
        }
    }

    @Test @CapabilityTest(EXPORT_BPMN_MODEL)
    void exportBpmnModel_validJson_returnsXml() {
        try (DataModellingBridge b = new DataModellingBridge()) {
            String result = b.exportBpmnModel("{}");
            assertNotNull(result);
        }
    }

    @Test @CapabilityTest(IMPORT_DMN_MODEL)
    void importDmnModel_validXml_returnsJson() {
        try (DataModellingBridge b = new DataModellingBridge()) {
            String result = b.importDmnModel("<definitions xmlns=\"https://www.omg.org/spec/DMN/20191111/MODEL/\"/>");
            assertNotNull(result);
        }
    }

    @Test @CapabilityTest(EXPORT_DMN_MODEL)
    void exportDmnModel_validJson_returnsXml() {
        try (DataModellingBridge b = new DataModellingBridge()) {
            String result = b.exportDmnModel("{}");
            assertNotNull(result);
        }
    }

    @Test @CapabilityTest(IMPORT_OPENAPI_SPEC)
    void importOpenapiSpec_validSpec_returnsJson() {
        try (DataModellingBridge b = new DataModellingBridge()) {
            String result = b.importOpenapiSpec("openapi: 3.0.0\ninfo:\n  title: Test\n  version: 1.0.0\npaths: {}");
            assertNotNull(result);
        }
    }

    @Test @CapabilityTest(EXPORT_OPENAPI_SPEC)
    void exportOpenapiSpec_validJson_returnsSpec() {
        try (DataModellingBridge b = new DataModellingBridge()) {
            String result = b.exportOpenapiSpec("{}");
            assertNotNull(result);
        }
    }

    @Test @CapabilityTest(CONVERT_OPENAPI_TO_ODCS)
    void convertOpenapiToOdcs_validSpec_returnsOdcs() {
        try (DataModellingBridge b = new DataModellingBridge()) {
            String result = b.convertOpenapiToOdcs("openapi: 3.0.0\ninfo:\n  title: T\n  version: 1.0\npaths: {}");
            assertNotNull(result);
        }
    }

    @Test @CapabilityTest(ANALYZE_OPENAPI_CONVERSION)
    void analyzeOpenapiConversion_validSpec_returnsAnalysis() {
        try (DataModellingBridge b = new DataModellingBridge()) {
            String result = b.analyzeOpenapiConversion("openapi: 3.0.0\ninfo:\n  title: T\n  version: 1.0\npaths: {}");
            assertNotNull(result);
        }
    }

    @Test @CapabilityTest(MIGRATE_DATAFLOW_TO_DOMAIN)
    void migrateDataflowToDomain_validJson_returnsDomain() {
        try (DataModellingBridge b = new DataModellingBridge()) {
            String result = b.migrateDataflowToDomain("{}");
            assertNotNull(result);
        }
    }

    @Test @CapabilityTest(PARSE_SKETCH_YAML)
    void parseSketchYaml_validYaml_returnsJson() {
        try (DataModellingBridge b = new DataModellingBridge()) {
            String result = b.parseSketchYaml("name: test\ntype: ENTITY_RELATIONSHIP");
            assertNotNull(result);
        }
    }

    @Test @CapabilityTest(PARSE_SKETCH_INDEX_YAML)
    void parseSketchIndexYaml_validYaml_returnsJson() {
        try (DataModellingBridge b = new DataModellingBridge()) {
            String result = b.parseSketchIndexYaml("name: index\nsketches: []");
            assertNotNull(result);
        }
    }

    @Test @CapabilityTest(EXPORT_SKETCH_TO_YAML)
    void exportSketchToYaml_validJson_returnsYaml() {
        try (DataModellingBridge b = new DataModellingBridge()) {
            String result = b.exportSketchToYaml("{\"name\":\"test\"}");
            assertNotNull(result);
        }
    }

    @Test @CapabilityTest(EXPORT_SKETCH_INDEX_TO_YAML)
    void exportSketchIndexToYaml_validJson_returnsYaml() {
        try (DataModellingBridge b = new DataModellingBridge()) {
            String result = b.exportSketchIndexToYaml("{\"name\":\"idx\"}");
            assertNotNull(result);
        }
    }

    @Test @CapabilityTest(CREATE_SKETCH)
    void createSketch_validArgs_returnsJson() {
        try (DataModellingBridge b = new DataModellingBridge()) {
            String result = b.createSketch("mySketch", "ENTITY_RELATIONSHIP", "A test sketch");
            assertNotNull(result);
        }
    }

    @Test @CapabilityTest(CREATE_SKETCH_INDEX)
    void createSketchIndex_validName_returnsJson() {
        try (DataModellingBridge b = new DataModellingBridge()) {
            String result = b.createSketchIndex("myIndex");
            assertNotNull(result);
        }
    }

    @Test @CapabilityTest(ADD_SKETCH_TO_INDEX)
    void addSketchToIndex_validArgs_returnsJson() {
        try (DataModellingBridge b = new DataModellingBridge()) {
            String result = b.addSketchToIndex("{\"name\":\"idx\",\"sketches\":[]}", "{\"name\":\"sk\"}");
            assertNotNull(result);
        }
    }

    @Test @CapabilityTest(SEARCH_SKETCHES)
    void searchSketches_validArgs_returnsJson() {
        try (DataModellingBridge b = new DataModellingBridge()) {
            String result = b.searchSketches("{\"name\":\"idx\",\"sketches\":[]}", "test");
            assertNotNull(result);
        }
    }

    @Test @CapabilityTest(CREATE_DOMAIN)
    void createDomain_validArgs_returnsJson() {
        try (DataModellingBridge b = new DataModellingBridge()) {
            String result = b.createDomain("myDomain", "A test domain");
            assertNotNull(result);
        }
    }

    @Test @CapabilityTest(ADD_SYSTEM_TO_DOMAIN)
    void addSystemToDomain_validArgs_returnsJson() {
        try (DataModellingBridge b = new DataModellingBridge()) {
            String result = b.addSystemToDomain("{\"name\":\"d\"}", "{\"name\":\"sys\"}");
            assertNotNull(result);
        }
    }

    @Test @CapabilityTest(ADD_CADS_NODE_TO_DOMAIN)
    void addCadsNodeToDomain_validArgs_returnsJson() {
        try (DataModellingBridge b = new DataModellingBridge()) {
            String result = b.addCadsNodeToDomain("{\"name\":\"d\"}", "{\"id\":\"n1\"}");
            assertNotNull(result);
        }
    }

    @Test @CapabilityTest(ADD_ODCS_NODE_TO_DOMAIN)
    void addOdcsNodeToDomain_validArgs_returnsJson() {
        try (DataModellingBridge b = new DataModellingBridge()) {
            String result = b.addOdcsNodeToDomain("{\"name\":\"d\"}", "{\"id\":\"n2\"}");
            assertNotNull(result);
        }
    }

    @Test @CapabilityTest(FILTER_NODES_BY_OWNER)
    void filterNodesByOwner_validArgs_returnsJson() {
        try (DataModellingBridge b = new DataModellingBridge()) {
            String result = b.filterNodesByOwner("[]", "alice");
            assertNotNull(result);
        }
    }

    @Test @CapabilityTest(FILTER_RELATIONSHIPS_BY_OWNER)
    void filterRelationshipsByOwner_validArgs_returnsJson() {
        try (DataModellingBridge b = new DataModellingBridge()) {
            String result = b.filterRelationshipsByOwner("[]", "alice");
            assertNotNull(result);
        }
    }

    @Test @CapabilityTest(FILTER_NODES_BY_INFRASTRUCTURE_TYPE)
    void filterNodesByInfrastructureType_validArgs_returnsJson() {
        try (DataModellingBridge b = new DataModellingBridge()) {
            String result = b.filterNodesByInfrastructureType("[]", "cloud");
            assertNotNull(result);
        }
    }

    @Test @CapabilityTest(FILTER_RELATIONSHIPS_BY_INFRASTRUCTURE_TYPE)
    void filterRelationshipsByInfrastructureType_validArgs_returnsJson() {
        try (DataModellingBridge b = new DataModellingBridge()) {
            String result = b.filterRelationshipsByInfrastructureType("[]", "cloud");
            assertNotNull(result);
        }
    }

    @Test @CapabilityTest(FILTER_BY_TAGS)
    void filterByTags_validArgs_returnsJson() {
        try (DataModellingBridge b = new DataModellingBridge()) {
            String result = b.filterByTags("[]", "[\"prod\"]");
            assertNotNull(result);
        }
    }
}
