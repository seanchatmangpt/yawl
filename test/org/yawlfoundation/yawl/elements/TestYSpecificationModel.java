package org.yawlfoundation.yawl.elements;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.exceptions.YSyntaxException;
import org.yawlfoundation.yawl.schema.YSchemaVersion;
import org.yawlfoundation.yawl.unmarshal.YMetaData;
import org.yawlfoundation.yawl.util.YVerificationHandler;
import org.yawlfoundation.yawl.elements.YSpecVersion;

/**
 * Chicago TDD tests for YSpecification model.
 * Tests specification creation, URI handling, root net assignment,
 * decomposition management, version handling, and verification.
 *
 * No mocks - real YSpecification objects with real dependencies.
 */
@DisplayName("YSpecification Model Tests")
@Tag("unit")
class TestYSpecificationModel {

    private YSpecification spec;

    @BeforeEach
    void setUp() {
        spec = new YSpecification("http://example.com/test-spec");
    }

    @Nested
    @DisplayName("Specification Creation and URI")
    class SpecificationCreationTests {

        @Test
        @DisplayName("Specification stores URI correctly via constructor")
        void specificationStoresUriViaConstructor() {
            String expectedUri = "http://example.com/workflow/order-process";
            YSpecification localSpec = new YSpecification(expectedUri);
            assertEquals(expectedUri, localSpec.getURI());
        }

        @Test
        @DisplayName("Default constructor creates specification with null URI")
        void defaultConstructorCreatesNullUri() {
            YSpecification emptySpec = new YSpecification();
            assertNull(emptySpec.getURI());
        }

        @Test
        @DisplayName("Set URI updates specification URI")
        void setUriUpdatesSpecificationUri() {
            spec.setURI("http://updated.com/new-uri");
            assertEquals("http://updated.com/new-uri", spec.getURI());
        }

        @Test
        @DisplayName("Specification URI can be null")
        void specificationUriCanBeSetToNull() {
            spec.setURI(null);
            assertNull(spec.getURI());
        }
    }

    @Nested
    @DisplayName("Root Net Assignment")
    class RootNetTests {

        @Test
        @DisplayName("Root net is null by default")
        void rootNetIsNullByDefault() {
            assertNull(spec.getRootNet());
        }

        @Test
        @DisplayName("Set root net stores the net correctly")
        void setRootNetStoresNetCorrectly() {
            YNet rootNet = createMinimalNet("rootNet", spec);
            spec.setRootNet(rootNet);

            assertEquals(rootNet, spec.getRootNet());
            assertEquals("rootNet", spec.getRootNet().getID());
        }

        @Test
        @DisplayName("Setting root net adds it to decompositions")
        void setRootNetAddsToDecompositions() {
            YNet rootNet = createMinimalNet("mainNet", spec);
            spec.setRootNet(rootNet);

            YDecomposition retrieved = spec.getDecomposition("mainNet");
            assertNotNull(retrieved);
            assertSame(rootNet, retrieved);
        }

        @Test
        @DisplayName("Can replace root net with different net")
        void canReplaceRootNet() {
            YNet firstNet = createMinimalNet("firstNet", spec);
            YNet secondNet = createMinimalNet("secondNet", spec);

            spec.setRootNet(firstNet);
            assertSame(firstNet, spec.getRootNet());

            spec.setRootNet(secondNet);
            assertSame(secondNet, spec.getRootNet());
        }
    }

    @Nested
    @DisplayName("Decomposition Management")
    class DecompositionTests {

        @Test
        @DisplayName("Get decompositions returns empty set when none added")
        void getDecompositionsReturnsEmptySet() {
            Set<YDecomposition> decomps = spec.getDecompositions();
            assertNotNull(decomps);
            assertTrue(decomps.isEmpty());
        }

        @Test
        @DisplayName("Add decomposition stores it by ID")
        void addDecompositionStoresById() {
            YAWLServiceGateway gateway = new YAWLServiceGateway("serviceGateway", spec);
            spec.addDecomposition(gateway);

            YDecomposition retrieved = spec.getDecomposition("serviceGateway");
            assertNotNull(retrieved);
            assertSame(gateway, retrieved);
        }

        @Test
        @DisplayName("Add multiple decompositions retrieves all")
        void addMultipleDecompositionsRetrievesAll() {
            YAWLServiceGateway gateway1 = new YAWLServiceGateway("gateway1", spec);
            YAWLServiceGateway gateway2 = new YAWLServiceGateway("gateway2", spec);
            YNet net1 = createMinimalNet("subnet1", spec);

            spec.addDecomposition(gateway1);
            spec.addDecomposition(gateway2);
            spec.addDecomposition(net1);

            Set<YDecomposition> decomps = spec.getDecompositions();
            assertEquals(3, decomps.size());
            assertTrue(decomps.contains(gateway1));
            assertTrue(decomps.contains(gateway2));
            assertTrue(decomps.contains(net1));
        }

        @Test
        @DisplayName("Remove decomposition by ID removes correctly")
        void removeDecompositionRemovesCorrectly() {
            YAWLServiceGateway gateway = new YAWLServiceGateway("toRemove", spec);
            spec.addDecomposition(gateway);

            assertNotNull(spec.getDecomposition("toRemove"));

            YDecomposition removed = spec.removeDecomposition("toRemove");
            assertSame(gateway, removed);
            assertNull(spec.getDecomposition("toRemove"));
        }

        @Test
        @DisplayName("Remove non-existent decomposition returns null")
        void removeNonExistentDecompositionReturnsNull() {
            YDecomposition removed = spec.removeDecomposition("nonExistent");
            assertNull(removed);
        }

        @Test
        @DisplayName("Get decomposition returns null for unknown ID")
        void getDecompositionReturnsNullForUnknown() {
            assertNull(spec.getDecomposition("unknown-id"));
        }
    }

    @Nested
    @DisplayName("Version Handling")
    class VersionTests {

        @Test
        @DisplayName("Default schema version is FourPointZero")
        void defaultSchemaVersionIsFourPointZero() {
            assertEquals(YSchemaVersion.FourPointZero, spec.getSchemaVersion());
        }

        @Test
        @DisplayName("Set version string updates schema version")
        void setVersionStringUpdatesSchemaVersion() {
            spec.setVersion("2.1");
            assertEquals(YSchemaVersion.TwoPointOne, spec.getSchemaVersion());
        }

        @Test
        @DisplayName("Set version with YSchemaVersion enum")
        void setVersionWithEnum() {
            spec.setVersion(YSchemaVersion.ThreePointZero);
            assertEquals(YSchemaVersion.ThreePointZero, spec.getSchemaVersion());
        }

        @Test
        @DisplayName("Set version throws on invalid version string")
        void setVersionThrowsOnInvalidString() {
            assertThrows(IllegalArgumentException.class, () -> {
                spec.setVersion("invalid-version");
            });
        }

        @Test
        @DisplayName("Set beta3 version string converts to Beta 3")
        void setBeta3VersionConvertsToBeta3() {
            spec.setVersion("beta3");
            assertEquals(YSchemaVersion.Beta3, spec.getSchemaVersion());
        }

        @Test
        @DisplayName("Deprecated getBetaVersion returns schema version string")
        @SuppressWarnings("deprecation")
        void deprecatedGetBetaVersionReturnsString() {
            spec.setVersion("2.2");
            assertEquals("2.2", spec.getBetaVersion());
        }

        @Test
        @DisplayName("Spec version defaults to 0.1 without metadata")
        void specVersionDefaultsToZeroPointOne() {
            assertEquals("0.1", spec.getSpecVersion());
        }

        @Test
        @DisplayName("Spec version from metadata returns correct version")
        void specVersionFromMetadataReturnsCorrectVersion() {
            YMetaData metaData = new YMetaData();
            metaData.setVersion(new YSpecVersion("1.5"));
            spec.setMetaData(metaData);

            assertEquals("1.5", spec.getSpecVersion());
        }
    }

    @Nested
    @DisplayName("Name and Documentation")
    class NameDocumentationTests {

        @Test
        @DisplayName("Name is null by default")
        void nameIsNullByDefault() {
            assertNull(spec.getName());
        }

        @Test
        @DisplayName("Set name stores correctly")
        void setNameStoresCorrectly() {
            spec.setName("Order Processing Workflow");
            assertEquals("Order Processing Workflow", spec.getName());
        }

        @Test
        @DisplayName("Documentation is null by default")
        void documentationIsNullByDefault() {
            assertNull(spec.getDocumentation());
        }

        @Test
        @DisplayName("Set documentation stores correctly")
        void setDocumentationStoresCorrectly() {
            String doc = "This specification handles order processing from receipt to fulfillment.";
            spec.setDocumentation(doc);
            assertEquals(doc, spec.getDocumentation());
        }
    }

    @Nested
    @DisplayName("Schema and Data Validation")
    class SchemaTests {

        @Test
        @DisplayName("Set schema creates data validator")
        void setSchemaCreatesDataValidator() throws YSyntaxException {
            String schema = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"/>";
            spec.setSchema(schema);

            assertNotNull(spec.getDataValidator());
            assertNotNull(spec.getDataSchema());
        }

        @Test
        @DisplayName("Data schema returns null without validator")
        void dataSchemaReturnsNullWithoutValidator() {
            assertNull(spec.getDataSchema());
        }

        @Test
        @DisplayName("Data validator returns null by default")
        void dataValidatorReturnsNullByDefault() {
            assertNull(spec.getDataValidator());
        }
    }

    @Nested
    @DisplayName("Metadata Handling")
    class MetadataTests {

        @Test
        @DisplayName("Metadata is null by default")
        void metadataIsNullByDefault() {
            assertNull(spec.getMetaData());
        }

        @Test
        @DisplayName("Set metadata stores correctly")
        void setMetadataStoresCorrectly() {
            YMetaData metaData = new YMetaData();
            metaData.setTitle("Test Specification");
            spec.setMetaData(metaData);

            assertNotNull(spec.getMetaData());
            assertEquals("Test Specification", spec.getMetaData().getTitle());
        }

        @Test
        @DisplayName("Get ID returns null without metadata")
        void getIdReturnsNullWithoutMetadata() {
            assertNull(spec.getID());
        }

        @Test
        @DisplayName("Get ID returns unique ID from metadata")
        void getIdReturnsUniqueIdFromMetadata() {
            YMetaData metaData = new YMetaData();
            metaData.setUniqueID("spec-unique-123");
            spec.setMetaData(metaData);

            assertEquals("spec-unique-123", spec.getID());
        }
    }

    @Nested
    @DisplayName("Specification ID")
    class SpecificationIdTests {

        @Test
        @DisplayName("Get specification ID creates valid YSpecificationID")
        void getSpecificationIdCreatesValidId() {
            YMetaData metaData = new YMetaData();
            metaData.setUniqueID("spec-123");
            metaData.setVersion(new YSpecVersion("2.0"));
            spec.setMetaData(metaData);
            spec.setURI("http://test.com/spec");

            YSpecificationID specId = spec.getSpecificationID();
            assertNotNull(specId);
            assertEquals("spec-123", specId.identifier());
            assertEquals("2.0", specId.version().toString());
            assertEquals("http://test.com/spec", specId.uri());
        }

        @Test
        @DisplayName("Specification ID with null metadata uses defaults")
        void specificationIdWithNullMetadataUsesDefaults() {
            spec.setURI("http://default.com/spec");

            YSpecificationID specId = spec.getSpecificationID();
            assertNotNull(specId);
            assertNull(specId.identifier());
            assertEquals("0.1", specId.version().toString());
            assertEquals("http://default.com/spec", specId.uri());
        }
    }

    @Nested
    @DisplayName("Equality and HashCode")
    class EqualityTests {

        @Test
        @DisplayName("Equal specifications have same specification ID")
        void equalSpecificationsHaveSameId() {
            YSpecification spec1 = new YSpecification("http://test.com/spec");
            YSpecification spec2 = new YSpecification("http://test.com/spec");

            YMetaData metaData = new YMetaData();
            metaData.setUniqueID("same-id");
            metaData.setVersion(new YSpecVersion("1.0"));
            spec1.setMetaData(metaData);
            spec2.setMetaData(metaData);

            assertEquals(spec1, spec2);
        }

        @Test
        @DisplayName("Different specifications are not equal")
        void differentSpecificationsAreNotEqual() {
            YSpecification spec1 = new YSpecification("http://test.com/spec1");
            YSpecification spec2 = new YSpecification("http://test.com/spec2");

            assertNotEquals(spec1, spec2);
        }

        @Test
        @DisplayName("HashCode based on specification ID")
        void hashCodeBasedOnSpecificationId() {
            YSpecification spec1 = new YSpecification("http://test.com/spec");
            YSpecification spec2 = new YSpecification("http://test.com/spec");

            YMetaData metaData = new YMetaData();
            metaData.setUniqueID("same-id");
            spec1.setMetaData(metaData);
            spec2.setMetaData(metaData);

            assertEquals(spec1.hashCode(), spec2.hashCode());
        }

        @Test
        @DisplayName("Not equal to null")
        void notEqualToNull() {
            assertNotEquals(null, spec);
        }

        @Test
        @DisplayName("Not equal to different class")
        void notEqualToDifferentClass() {
            assertNotEquals("not a spec", spec);
        }
    }

    @Nested
    @DisplayName("Verification")
    class VerificationTests {

        @Test
        @DisplayName("Verify fails without root net")
        void verifyFailsWithoutRootNet() throws YSyntaxException {
            spec.setSchema("<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"/>");
            YVerificationHandler handler = new YVerificationHandler();
            spec.verify(handler);

            assertTrue(handler.hasErrors());
            assertTrue(handler.getErrors().stream()
                    .anyMatch(msg -> msg.getMessage().contains("root net")));
        }

        @Test
        @DisplayName("Verify with valid root net passes")
        void verifyWithValidRootNetPasses() throws YSyntaxException {
            spec.setSchema("<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"/>");
            YNet rootNet = createValidRootNet("rootNet", spec);
            spec.setRootNet(rootNet);

            YVerificationHandler handler = new YVerificationHandler();
            spec.verify(handler);

            assertFalse(handler.hasErrors(), "Should have no errors: " + handler.getMessages());
        }
    }

    @Nested
    @DisplayName("Row Key for Persistence")
    class PersistenceTests {

        @Test
        @DisplayName("Row key defaults to zero")
        void rowKeyDefaultsToZero() {
            assertEquals(0L, spec.getRowKey());
        }

        @Test
        @DisplayName("Set row key stores correctly")
        void setRowKeyStoresCorrectly() {
            spec.setRowKey(12345L);
            assertEquals(12345L, spec.getRowKey());
        }
    }

    @Nested
    @DisplayName("ToXML Generation")
    class ToXmlTests {

        @Test
        @DisplayName("ToXML throws without root net")
        void toXmlThrowsWithoutRootNet() {
            assertThrows(NullPointerException.class, () -> spec.toXML());
        }

        @Test
        @DisplayName("ToXML generates valid XML with root net")
        void toXmlGeneratesValidXml() throws YSyntaxException {
            spec.setSchema("<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"/>");
            YMetaData metaData = new YMetaData();
            metaData.setTitle("Test Spec");
            spec.setMetaData(metaData);

            YNet rootNet = createValidRootNet("mainNet", spec);
            spec.setRootNet(rootNet);

            String xml = spec.toXML();

            assertNotNull(xml);
            assertTrue(xml.contains("<specification"));
            assertTrue(xml.contains("uri=\"http://example.com/test-spec\""));
            assertTrue(xml.contains("mainNet"));
        }
    }

    // ========== Helper Methods ==========

    /**
     * Creates a minimal YNet with input and output conditions.
     */
    private YNet createMinimalNet(String id, YSpecification specification) {
        YNet net = new YNet(id, specification);
        YInputCondition input = new YInputCondition("i_" + id, net);
        YOutputCondition output = new YOutputCondition("o_" + id, net);
        net.setInputCondition(input);
        net.setOutputCondition(output);
        return net;
    }

    /**
     * Creates a valid root net with a simple task between input and output.
     */
    private YNet createValidRootNet(String id, YSpecification specification) {
        YNet net = new YNet(id, specification);

        YInputCondition input = new YInputCondition("i", net);
        YOutputCondition output = new YOutputCondition("o", net);
        YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);

        // Create flows: i -> task1 -> o
        // Using addPostset adds the flow to both source and target elements
        input.addPostset(new YFlow(input, task));
        task.addPostset(new YFlow(task, output));

        net.setInputCondition(input);
        net.setOutputCondition(output);

        return net;
    }
}
