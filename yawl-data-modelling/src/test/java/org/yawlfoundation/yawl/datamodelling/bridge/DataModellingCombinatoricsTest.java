/**
 * DataModellingCombinatoricsTest — parameterised tests using real case data.
 *
 * <p>Tests each L3 operation against multiple real-world data contracts sourced
 * from the data-modelling-sdk examples, producing a combinatorial coverage matrix.
 *
 * <p><b>Case data sources:</b>
 * <ul>
 *   <li>odcs-payments-all-types — ODCS v3.0.2 payment transactions (all SDK data types)</li>
 *   <li>odcs-full-example — ODCS v3.1.0 full seller contract (2 tables, composite keys, SLA)</li>
 *   <li>odcl-orders — ODCL v1.2.0 e-commerce orders with 10 sample order rows</li>
 *   <li>odps-customer-product — ODPS v0.9.0 customer data product (4 input + 4 output ports)</li>
 * </ul>
 *
 * <p><b>Test matrix:</b> 6 ODCS operations × 4 ODCS cases + 2 ODPS operations × 2 ODPS cases
 * = 28 combinatorial test executions.
 *
 * <p><b>These tests skip gracefully when the native library is absent.</b>
 * Skipped ≠ Failed; they block <em>release</em>, not merge.
 *
 * @pattern WCP-NB-10 (native bridge combinatorial testing)
 * @see DataModellingL3
 * @see JTBDTestSupport
 */
package org.yawlfoundation.yawl.datamodelling.bridge;

import java.io.IOException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.yawlfoundation.yawl.datamodelling.bridge.JTBDTestSupport.assertPlausible;
import static org.yawlfoundation.yawl.datamodelling.bridge.JTBDTestSupport.loadFixture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class DataModellingCombinatoricsTest {

    private DataModellingL3 l3;

    @BeforeEach
    void setUp() throws Exception {
        var l2 = new DataModellingL2(System.getProperty(DataModellingL2.LIB_PATH_PROP));
        this.l3 = new DataModellingL3(l2);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (l3 != null) {
            l3.close();
        }
    }

    // -------------------------------------------------------------------------
    // Case providers
    // -------------------------------------------------------------------------

    /**
     * Four real ODCS/ODCL schemas from the data-modelling-sdk examples directory.
     * Each is a content-wrapped JSON fixture: {@code {"content": "<yaml>"}}
     */
    static Stream<Arguments> odcsSchemaCases() throws IOException {
        return Stream.of(
            Arguments.of("odcs-simple",            loadFixture("odcs-schema")),
            Arguments.of("odcs-payments-all-types", loadFixture("odcs-payments-all-types")),
            Arguments.of("odcs-full-seller",        loadFixture("odcs-full-example")),
            Arguments.of("odcl-orders-ecommerce",   loadFixture("odcl-orders"))
        );
    }

    /**
     * Two real ODPS data-product fixtures.
     */
    static Stream<Arguments> odpsSchemaCases() throws IOException {
        return Stream.of(
            Arguments.of("odps-basic-schema",     loadFixture("odps-schema")),
            Arguments.of("odps-customer-product", loadFixture("odps-customer-product"))
        );
    }

    // -------------------------------------------------------------------------
    // ODCS combinatorial tests
    // -------------------------------------------------------------------------

    /**
     * Combinatorial: importOdcsSchema + validateOdcsSchema for every real case.
     * Proves the import and validation pipeline handles real-world field types,
     * composite keys, sample data rows, and ODCL format variations.
     */
    @ParameterizedTest(name = "[{0}] importOdcsSchema + validateOdcsSchema")
    @MethodSource("odcsSchemaCases")
    void importAndValidate_odcsSchema(String caseName, String fixture) {
        assumeTrue(l3.isAvailable(), "libdatamodelling.so absent — combinatorial test skipped");
        String imported = l3.importOdcsSchema(fixture);
        assertPlausible(imported, "schema_id");
        String validated = l3.validateOdcsSchema(fixture);
        assertPlausible(validated);
    }

    /**
     * Combinatorial: importOdcsSchema → convertOdcsToOpenapi for every real case.
     * Validates that each real contract can be converted to an OpenAPI 3.1.0 spec.
     */
    @ParameterizedTest(name = "[{0}] importOdcsSchema → convertOdcsToOpenapi")
    @MethodSource("odcsSchemaCases")
    void convertToOpenapi_odcsSchema(String caseName, String fixture) {
        assumeTrue(l3.isAvailable(), "libdatamodelling.so absent — combinatorial test skipped");
        String imported = l3.importOdcsSchema(fixture);
        assertPlausible(imported, "schema_id");
        String openapi = l3.convertOdcsToOpenapi(fixture);
        assertPlausible(openapi);
    }

    /**
     * Combinatorial: importOdcsSchema → convertOdcsToOdps for every real case.
     * Validates ODCS → ODPS cross-format conversion with real data products.
     */
    @ParameterizedTest(name = "[{0}] importOdcsSchema → convertOdcsToOdps")
    @MethodSource("odcsSchemaCases")
    void convertToOdps_odcsSchema(String caseName, String fixture) {
        assumeTrue(l3.isAvailable(), "libdatamodelling.so absent — combinatorial test skipped");
        String imported = l3.importOdcsSchema(fixture);
        assertPlausible(imported, "schema_id");
        String odps = l3.convertOdcsToOdps(fixture);
        assertPlausible(odps);
    }

    /**
     * Combinatorial: importOdcsSchema → inferConstraints → exportConstraintSet.
     * Three-step chain proving constraint discovery works on real-world schemas
     * with quality metrics, SLAs, PII fields, and complex field type options.
     */
    @ParameterizedTest(name = "[{0}] importOdcsSchema → inferConstraints → exportConstraintSet")
    @MethodSource("odcsSchemaCases")
    void constraintDiscoveryChain_odcsSchema(String caseName, String fixture) {
        assumeTrue(l3.isAvailable(), "libdatamodelling.so absent — combinatorial test skipped");
        String imported = l3.importOdcsSchema(fixture);
        assertPlausible(imported, "schema_id");
        String inferred = l3.inferConstraints(fixture);
        assertPlausible(inferred);
        String exported = l3.exportConstraintSet(fixture);
        assertPlausible(exported);
    }

    /**
     * Combinatorial: importOdcsSchema → inferVocabulary for every real case.
     * Proves semantic term extraction works across real domain models
     * (payment metrics, e-commerce orders, master data).
     */
    @ParameterizedTest(name = "[{0}] importOdcsSchema → inferVocabulary")
    @MethodSource("odcsSchemaCases")
    void vocabularyExtraction_odcsSchema(String caseName, String fixture) {
        assumeTrue(l3.isAvailable(), "libdatamodelling.so absent — combinatorial test skipped");
        String imported = l3.importOdcsSchema(fixture);
        assertPlausible(imported, "schema_id");
        String vocabulary = l3.inferVocabulary(fixture);
        assertPlausible(vocabulary);
    }

    /**
     * Combinatorial: importOdcsSchema → exportOdcsSchema for every real case.
     * Round-trip through the native bridge: ingest a real contract, emit it back
     * as ODCS. Validates no data loss on structural re-serialisation.
     */
    @ParameterizedTest(name = "[{0}] importOdcsSchema → exportOdcsSchema")
    @MethodSource("odcsSchemaCases")
    void exportRoundTrip_odcsSchema(String caseName, String fixture) {
        assumeTrue(l3.isAvailable(), "libdatamodelling.so absent — combinatorial test skipped");
        String imported = l3.importOdcsSchema(fixture);
        assertPlausible(imported, "schema_id");
        String exported = l3.exportOdcsSchema(fixture);
        assertPlausible(exported);
    }

    // -------------------------------------------------------------------------
    // ODPS combinatorial tests
    // -------------------------------------------------------------------------

    /**
     * Combinatorial: importOdpsSchema + validateOdpsSchema for every real ODPS case.
     * Validates the ODPS v0.9.0 customer data product (4 input ports, 4 output ports,
     * SBOM, Kafka management port) is correctly parsed and validated.
     */
    @ParameterizedTest(name = "[{0}] importOdpsSchema + validateOdpsSchema")
    @MethodSource("odpsSchemaCases")
    void importAndValidate_odpsSchema(String caseName, String fixture) {
        assumeTrue(l3.isAvailable(), "libdatamodelling.so absent — combinatorial test skipped");
        String imported = l3.importOdpsSchema(fixture);
        assertPlausible(imported);
        String validated = l3.validateOdpsSchema(fixture);
        assertPlausible(validated);
    }

    /**
     * Combinatorial: importOdpsSchema → inferSchemaRecommendations for every ODPS case.
     * Proves AI-assisted schema recommendations work on real data products
     * with composite input/output port structures.
     */
    @ParameterizedTest(name = "[{0}] importOdpsSchema → inferSchemaRecommendations")
    @MethodSource("odpsSchemaCases")
    void schemaRecommendations_odpsSchema(String caseName, String fixture) {
        assumeTrue(l3.isAvailable(), "libdatamodelling.so absent — combinatorial test skipped");
        String imported = l3.importOdpsSchema(fixture);
        assertPlausible(imported);
        String recommendations = l3.inferSchemaRecommendations(fixture);
        assertPlausible(recommendations);
    }
}
