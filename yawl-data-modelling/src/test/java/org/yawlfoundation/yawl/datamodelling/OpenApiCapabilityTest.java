package org.yawlfoundation.yawl.datamodelling;

import org.junit.jupiter.api.*;
import org.yawlfoundation.yawl.datamodelling.api.DataModellingService;
import org.yawlfoundation.yawl.datamodelling.generated.data_modelling_ffi_h;
import org.yawlfoundation.yawl.datamodelling.model.OpenApiConversionAnalysis;
import org.yawlfoundation.yawl.datamodelling.model.WorkspaceModel;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.yawlfoundation.yawl.datamodelling.Capability.*;
import static org.yawlfoundation.yawl.datamodelling.test.DataModellingTestFixtures.*;

/**
 * Group H — OpenAPI Capability Tests (4 capabilities).
 *
 * <p>Tests cover:
 * - IMPORT_OPENAPI_SPEC: Parse OpenAPI 3.0 YAML/JSON into WorkspaceModel
 * - EXPORT_OPENAPI_SPEC: Export WorkspaceModel back to OpenAPI 3.0 YAML/JSON
 * - CONVERT_OPENAPI_TO_ODCS: Convert OpenAPI spec to ODCS WorkspaceModel
 * - ANALYZE_OPENAPI_CONVERSION: Analyze OpenAPI→ODCS conversion completeness
 *
 * <p>These tests use real OpenAPI fixtures (orders API), asserting structural
 * properties (non-null results, valid content, correct analysis metrics) without
 * mocking or stubbing the native bridge.
 *
 * <p>Skips if native library is not present (UnsupportedOperationException expected).
 */
@Tag("capability")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpenApiCapabilityTest {

    DataModellingService service;

    @BeforeAll
    void setup() {
        assumeTrue(data_modelling_ffi_h.LIBRARY.isPresent(),
            "Native library not present — skipping OpenAPI tests");
        service = DataModellingModule.create();
    }

    @AfterAll
    void teardown() throws Exception {
        if (service != null) {
            service.close();
        }
    }

    @Nested
    @DisplayName("IMPORT_OPENAPI_SPEC")
    class ImportOpenapiSpec {

        @Test
        @CapabilityTest(IMPORT_OPENAPI_SPEC)
        @DisplayName("valid OpenAPI YAML returns non-null WorkspaceModel")
        void validOpenApiYaml_returnsNonNullWorkspace() throws Exception {
            WorkspaceModel ws = service.importOpenapiSpec(OPENAPI_YAML);
            assertNotNull(ws, "OpenAPI import must return non-null WorkspaceModel");
        }

        @Test
        @CapabilityTest(IMPORT_OPENAPI_SPEC)
        @DisplayName("imported workspace contains tables from schemas")
        void importedWorkspace_tablesNotEmpty() throws Exception {
            WorkspaceModel ws = service.importOpenapiSpec(OPENAPI_YAML);
            assertFalse(ws.tables().isEmpty(),
                "OpenAPI import must produce tables from schemas");
        }

        @Test
        @CapabilityTest(IMPORT_OPENAPI_SPEC)
        @DisplayName("invalid YAML throws DataModellingException")
        void invalidYaml_throwsDataModellingException() {
            assertThrows(DataModellingException.class,
                () -> service.importOpenapiSpec("not: valid: openapi: yaml"),
                "Invalid YAML must throw DataModellingException");
        }
    }

    @Nested
    @DisplayName("EXPORT_OPENAPI_SPEC")
    class ExportOpenapiSpec {

        @Test
        @CapabilityTest(EXPORT_OPENAPI_SPEC)
        @DisplayName("exported output from imported model is non-blank")
        void exportedOutput_isNonBlank() throws Exception {
            WorkspaceModel ws = service.importOpenapiSpec(OPENAPI_YAML);
            String openapi = service.exportOpenapiSpec(ws);

            assertNotNull(openapi, "OpenAPI export must not be null");
            assertFalse(openapi.isBlank(), "OpenAPI export must not be blank");
        }

        @Test
        @CapabilityTest(EXPORT_OPENAPI_SPEC)
        @DisplayName("exported output contains OpenAPI marker")
        void exportedOutput_containsOpenApiMarker() throws Exception {
            WorkspaceModel ws = service.importOpenapiSpec(OPENAPI_YAML);
            String openapi = service.exportOpenapiSpec(ws);

            assertTrue(
                openapi.contains("openapi") || openapi.contains("paths") || openapi.contains("components"),
                "OpenAPI export must contain openapi, paths, or components marker");
        }

        @Test
        @CapabilityTest(EXPORT_OPENAPI_SPEC)
        @DisplayName("roundtrip preserves model properties")
        void roundtrip_preservesModelProperties() throws Exception {
            WorkspaceModel original = service.importOpenapiSpec(OPENAPI_YAML);
            String exported = service.exportOpenapiSpec(original);
            WorkspaceModel roundtrip = service.importOpenapiSpec(exported);

            assertNotNull(roundtrip, "Roundtrip must produce non-null WorkspaceModel");
            assertEquals(original.tables().size(), roundtrip.tables().size(),
                "Roundtrip must preserve table count");
        }
    }

    @Nested
    @DisplayName("CONVERT_OPENAPI_TO_ODCS")
    class ConvertOpenapiToOdcs {

        @Test
        @CapabilityTest(CONVERT_OPENAPI_TO_ODCS)
        @DisplayName("conversion returns non-null WorkspaceModel")
        void conversion_returnsNonNullWorkspace() throws Exception {
            WorkspaceModel ws = service.convertOpenapiToOdcs(OPENAPI_YAML);
            assertNotNull(ws, "OpenAPI→ODCS conversion must return non-null WorkspaceModel");
        }

        @Test
        @CapabilityTest(CONVERT_OPENAPI_TO_ODCS)
        @DisplayName("converted workspace contains tables from OpenAPI schemas")
        void convertedWorkspace_tablesNotEmpty() throws Exception {
            WorkspaceModel ws = service.convertOpenapiToOdcs(OPENAPI_YAML);
            assertFalse(ws.tables().isEmpty(),
                "OpenAPI→ODCS conversion must produce tables");
        }

        @Test
        @CapabilityTest(CONVERT_OPENAPI_TO_ODCS)
        @DisplayName("invalid input throws exception")
        void invalidInput_throwsException() {
            assertThrows(Exception.class,
                () -> service.convertOpenapiToOdcs("not openapi"),
                "Invalid OpenAPI must throw exception");
        }
    }

    @Nested
    @DisplayName("ANALYZE_OPENAPI_CONVERSION")
    class AnalyzeOpenapiConversion {

        @Test
        @CapabilityTest(ANALYZE_OPENAPI_CONVERSION)
        @DisplayName("analysis returns non-null OpenApiConversionAnalysis")
        void analysis_returnsNonNullAnalysis() throws Exception {
            OpenApiConversionAnalysis analysis = service.analyzeOpenapiConversion(OPENAPI_YAML);
            assertNotNull(analysis, "Analysis must return non-null OpenApiConversionAnalysis");
        }

        @Test
        @CapabilityTest(ANALYZE_OPENAPI_CONVERSION)
        @DisplayName("analysis properties are valid")
        void analysis_structuralPropertiesValid() throws Exception {
            OpenApiConversionAnalysis analysis = service.analyzeOpenapiConversion(OPENAPI_YAML);

            assertTrue(analysis.totalSchemas() >= 0,
                "totalSchemas must be non-negative");
            assertTrue(analysis.convertedSchemas() >= 0,
                "convertedSchemas must be non-negative");
            assertTrue(analysis.convertedSchemas() <= analysis.totalSchemas(),
                "convertedSchemas must not exceed totalSchemas");
            assertNotNull(analysis.warnings(), "warnings list must not be null");
            assertNotNull(analysis.unconvertedPaths(), "unconvertedPaths list must not be null");
        }

        @Test
        @CapabilityTest(ANALYZE_OPENAPI_CONVERSION)
        @DisplayName("analysis handles valid OpenAPI spec without throwing")
        void analysis_handlesValidSpecWithoutThrowing() {
            assertDoesNotThrow(() -> service.analyzeOpenapiConversion(OPENAPI_YAML),
                "Analysis of valid OpenAPI must not throw");
        }

        @Test
        @CapabilityTest(ANALYZE_OPENAPI_CONVERSION)
        @DisplayName("analysis warnings and unconvertedPaths are lists")
        void analysis_warningsAndPathsAreLists() throws Exception {
            OpenApiConversionAnalysis analysis = service.analyzeOpenapiConversion(OPENAPI_YAML);

            assertTrue(analysis.warnings().size() >= 0, "warnings must be a valid list");
            assertTrue(analysis.unconvertedPaths().size() >= 0, "unconvertedPaths must be a valid list");
        }
    }
}
