package org.yawlfoundation.yawl.datamodelling.bridge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * DataModellingBridgeTest — graceful-degradation tests for all 55 capabilities.
 *
 * <p>These tests verify that when {@code libdatamodelling.so} is not loaded (system property
 * {@value DataModellingL2#LIB_PATH_PROP} absent), each L3 operation throws
 * {@link UnsupportedOperationException} with a diagnostic message. This is the correct
 * production behavior: the JVM starts without the native library and requests fail fast
 * rather than silently degrading.</p>
 *
 * <p>All tests use {@link DataModellingL3#fromSystemProperty()}, which constructs an L2
 * with a null library path when the system property is absent — the standard deployment
 * pattern for environments that may or may not have the native SDK installed.</p>
 */
class DataModellingBridgeTest {

    /** JSON sentinel used as a minimal well-formed request in all tests. */
    private static final String REQ = "{\"test\":true}";

    // =========================================================================
    // CONVERT (5 capabilities)
    // =========================================================================

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.CONVERT_BPMN_TO_DMN)
    void convertBpmnToDmn_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.convertBpmnToDmn(REQ));
        }
    }

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.CONVERT_ODCS_TO_ODPS)
    void convertOdcsToOdps_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.convertOdcsToOdps(REQ));
        }
    }

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.CONVERT_ODCS_TO_OPENAPI)
    void convertOdcsToOpenapi_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.convertOdcsToOpenapi(REQ));
        }
    }

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.CONVERT_SCHEMA_TO_TEMPLATE)
    void convertSchemaToTemplate_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.convertSchemaToTemplate(REQ));
        }
    }

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.CONVERT_VOCABULARY_TO_ODCS)
    void convertVocabularyToOdcs_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.convertVocabularyToOdcs(REQ));
        }
    }

    // =========================================================================
    // EXPORT (14 capabilities)
    // =========================================================================

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.EXPORT_BPMN_PROCESS)
    void exportBpmnProcess_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.exportBpmnProcess(REQ));
        }
    }

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.EXPORT_CADS_SCHEMA)
    void exportCadsSchema_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.exportCadsSchema(REQ));
        }
    }

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.EXPORT_CONFIGURATION)
    void exportConfiguration_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.exportConfiguration(REQ));
        }
    }

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.EXPORT_CONSTRAINT_SET)
    void exportConstraintSet_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.exportConstraintSet(REQ));
        }
    }

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.EXPORT_DECISION_RECORD)
    void exportDecisionRecord_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.exportDecisionRecord(REQ));
        }
    }

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.EXPORT_DMN_DECISION)
    void exportDmnDecision_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.exportDmnDecision(REQ));
        }
    }

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.EXPORT_DOMAIN_ORG)
    void exportDomainOrg_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.exportDomainOrg(REQ));
        }
    }

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.EXPORT_KNOWLEDGE_BASE)
    void exportKnowledgeBase_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.exportKnowledgeBase(REQ));
        }
    }

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.EXPORT_ODCS_SCHEMA)
    void exportOdcsSchema_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.exportOdcsSchema(REQ));
        }
    }

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.EXPORT_ODPS_SCHEMA)
    void exportOdpsSchema_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.exportOdpsSchema(REQ));
        }
    }

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.EXPORT_OPENAPI_SCHEMA)
    void exportOpenapiSchema_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.exportOpenapiSchema(REQ));
        }
    }

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.EXPORT_SKETCH)
    void exportSketch_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.exportSketch(REQ));
        }
    }

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.EXPORT_TEMPLATE)
    void exportTemplate_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.exportTemplate(REQ));
        }
    }

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.EXPORT_VOCABULARY)
    void exportVocabulary_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.exportVocabulary(REQ));
        }
    }

    // =========================================================================
    // IMPORT (14 capabilities)
    // =========================================================================

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.IMPORT_BPMN_PROCESS)
    void importBpmnProcess_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.importBpmnProcess(REQ));
        }
    }

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.IMPORT_CADS_SCHEMA)
    void importCadsSchema_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.importCadsSchema(REQ));
        }
    }

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.IMPORT_CONFIGURATION)
    void importConfiguration_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.importConfiguration(REQ));
        }
    }

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.IMPORT_CONSTRAINT_SET)
    void importConstraintSet_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.importConstraintSet(REQ));
        }
    }

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.IMPORT_DECISION_RECORD)
    void importDecisionRecord_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.importDecisionRecord(REQ));
        }
    }

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.IMPORT_DMN_DECISION)
    void importDmnDecision_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.importDmnDecision(REQ));
        }
    }

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.IMPORT_DOMAIN_ORG)
    void importDomainOrg_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.importDomainOrg(REQ));
        }
    }

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.IMPORT_KNOWLEDGE_BASE)
    void importKnowledgeBase_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.importKnowledgeBase(REQ));
        }
    }

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.IMPORT_ODCS_SCHEMA)
    void importOdcsSchema_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.importOdcsSchema(REQ));
        }
    }

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.IMPORT_ODPS_SCHEMA)
    void importOdpsSchema_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.importOdpsSchema(REQ));
        }
    }

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.IMPORT_OPENAPI_SCHEMA)
    void importOpenapiSchema_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.importOpenapiSchema(REQ));
        }
    }

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.IMPORT_SKETCH)
    void importSketch_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.importSketch(REQ));
        }
    }

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.IMPORT_TEMPLATE)
    void importTemplate_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.importTemplate(REQ));
        }
    }

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.IMPORT_VOCABULARY)
    void importVocabulary_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.importVocabulary(REQ));
        }
    }

    // =========================================================================
    // INFER (6 capabilities)
    // =========================================================================

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.INFER_CONSTRAINTS)
    void inferConstraints_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.inferConstraints(REQ));
        }
    }

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.INFER_DECISION_RULES)
    void inferDecisionRules_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.inferDecisionRules(REQ));
        }
    }

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.INFER_DOMAIN_ORG)
    void inferDomainOrg_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.inferDomainOrg(REQ));
        }
    }

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.INFER_KNOWLEDGE_GAPS)
    void inferKnowledgeGaps_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.inferKnowledgeGaps(REQ));
        }
    }

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.INFER_SCHEMA_RECOMMENDATIONS)
    void inferSchemaRecommendations_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.inferSchemaRecommendations(REQ));
        }
    }

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.INFER_VOCABULARY)
    void inferVocabulary_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.inferVocabulary(REQ));
        }
    }

    // =========================================================================
    // MAP (4 capabilities)
    // =========================================================================

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.MAP_BPMN_TO_SCHEMA)
    void mapBpmnToSchema_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.mapBpmnToSchema(REQ));
        }
    }

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.MAP_DOMAIN_TO_SCHEMA)
    void mapDomainToSchema_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.mapDomainToSchema(REQ));
        }
    }

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.MAP_SCHEMAS)
    void mapSchemas_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.mapSchemas(REQ));
        }
    }

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.MAP_VOCABULARIES)
    void mapVocabularies_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.mapVocabularies(REQ));
        }
    }

    // =========================================================================
    // VALIDATE (12 capabilities)
    // =========================================================================

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.VALIDATE_BPMN_PROCESS)
    void validateBpmnProcess_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.validateBpmnProcess(REQ));
        }
    }

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.VALIDATE_CADS_SCHEMA)
    void validateCadsSchema_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.validateCadsSchema(REQ));
        }
    }

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.VALIDATE_CONFIGURATION)
    void validateConfiguration_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.validateConfiguration(REQ));
        }
    }

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.VALIDATE_CONSTRAINT_SET)
    void validateConstraintSet_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.validateConstraintSet(REQ));
        }
    }

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.VALIDATE_DECISION_RECORD)
    void validateDecisionRecord_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.validateDecisionRecord(REQ));
        }
    }

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.VALIDATE_DMN_DECISION)
    void validateDmnDecision_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.validateDmnDecision(REQ));
        }
    }

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.VALIDATE_DOMAIN_ORG)
    void validateDomainOrg_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.validateDomainOrg(REQ));
        }
    }

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.VALIDATE_KNOWLEDGE_BASE)
    void validateKnowledgeBase_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.validateKnowledgeBase(REQ));
        }
    }

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.VALIDATE_ODCS_SCHEMA)
    void validateOdcsSchema_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.validateOdcsSchema(REQ));
        }
    }

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.VALIDATE_ODPS_SCHEMA)
    void validateOdpsSchema_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.validateOdpsSchema(REQ));
        }
    }

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.VALIDATE_OPENAPI_SCHEMA)
    void validateOpenapiSchema_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.validateOpenapiSchema(REQ));
        }
    }

    @Test
    @DataModellingCapabilityTest(DataModellingCapability.VALIDATE_SKETCH)
    void validateSketch_throwsWhenLibraryAbsent() {
        try (DataModellingL3 l3 = DataModellingL3.fromSystemProperty()) {
            assertThrows(UnsupportedOperationException.class,
                () -> l3.validateSketch(REQ));
        }
    }

    // =========================================================================
    // REGISTRY VALIDATION — proves the full structural invariant holds
    // =========================================================================

    @Test
    void registryValidates_allCapabilitiesCovered() {
        DataModellingCapabilityRegistry registry = new DataModellingCapabilityRegistry(
            List.of(DataModellingL2.class),
            List.of(DataModellingL3.class),
            List.of(DataModellingBridgeTest.class));
        assertDoesNotThrow(registry::validate,
            "All 55 capabilities must have L2, L3, and test coverage");
    }
}
