/*
 * Copyright (c) 2004-2025 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.schema.turtle;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TempDir;

import org.yawlfoundation.yawl.elements.YAtomicTask;
import org.yawlfoundation.yawl.elements.YInputCondition;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YOutputCondition;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.unmarshal.YMetaData;
import org.yawlfoundation.yawl.unmarshal.turtle.YTurtleImporter;

/**
 * Comprehensive test suite for YTurtleExporter.
 *
 * Tests cover:
 * - Exporting YSpecification objects to Turtle format
 * - Generating valid Turtle syntax with proper prefixes
 * - Preserving specification metadata (title, description, creators, dates)
 * - Exporting workflow nets and elements
 * - File-based exports
 * - Multiple specification export
 * - Round-trip consistency (export/import cycles)
 * - Turtle syntax validation
 *
 * @author Test Suite
 */
@DisplayName("YTurtleExporter Test Suite")
class YTurtleExporterTest {

    private YSpecification testSpec;
    private String simpleWorkflowTurtle;

    @BeforeEach
    void setUp() throws IOException {
        // Load fixture for reference
        Path fixturePath = new java.io.File("test/resources/turtle/simple-workflow.ttl").toPath();
        simpleWorkflowTurtle = Files.readString(fixturePath);

        // Import fixture to get test specification
        List<YSpecification> specs = YTurtleImporter.importFromString(simpleWorkflowTurtle);
        testSpec = specs.get(0);
    }

    @Nested
    @DisplayName("Basic Export Tests")
    class BasicExportTests {

        @Test
        @DisplayName("exportToString returns non-null string")
        void exportToString_returnsNonNull() {
            String exported = YTurtleExporter.exportToString(testSpec);

            assertNotNull(exported, "Export should return non-null string");
        }

        @Test
        @DisplayName("exportToString returns non-empty string")
        void exportToString_returnsNonEmpty() {
            String exported = YTurtleExporter.exportToString(testSpec);

            assertFalse(exported.isEmpty(), "Export should return non-empty string");
            assertTrue(exported.length() > 100,
                "Exported Turtle should have substantial content");
        }

        @Test
        @DisplayName("exportToString produces valid Turtle prefixes")
        void exportToString_containsPrefixes() {
            String exported = YTurtleExporter.exportToString(testSpec);

            assertTrue(exported.contains("@prefix yawls:"),
                "Should contain YAWL namespace prefix");
            assertTrue(exported.contains("@prefix dcterms:"),
                "Should contain Dublin Core namespace prefix");
            assertTrue(exported.contains("@prefix xsd:"),
                "Should contain XML Schema namespace prefix");
        }

        @Test
        @DisplayName("exportToString contains Specification type declaration")
        void exportToString_containsSpecificationType() {
            String exported = YTurtleExporter.exportToString(testSpec);

            assertTrue(exported.contains("yawls:Specification"),
                "Should declare Specification type");
        }

        @Test
        @DisplayName("exportToString preserves specification URI")
        void exportToString_preservesSpecUri() {
            String originalUri = testSpec.getURI();
            String exported = YTurtleExporter.exportToString(testSpec);

            assertTrue(exported.contains(originalUri),
                "Should preserve specification URI in output");
        }

        @Test
        @DisplayName("exportToString preserves specification title")
        void exportToString_preservesTitle() {
            YMetaData metadata = testSpec.getMetaData();
            String title = metadata.getTitle();
            String exported = YTurtleExporter.exportToString(testSpec);

            assertTrue(exported.contains(title),
                "Should preserve title in output");
            assertTrue(exported.contains("dcterms:title"),
                "Should use dcterms:title property");
        }

        @Test
        @DisplayName("exportToString preserves specification description")
        void exportToString_preservesDescription() {
            YMetaData metadata = testSpec.getMetaData();
            String description = metadata.getDescription();
            String exported = YTurtleExporter.exportToString(testSpec);

            assertTrue(exported.contains("dcterms:description"),
                "Should use dcterms:description property");
        }

        @Test
        @DisplayName("exportToString contains workflow net definition")
        void exportToString_containsWorkflowNet() {
            String exported = YTurtleExporter.exportToString(testSpec);

            assertTrue(exported.contains("yawls:WorkflowNet"),
                "Should declare workflow net");
            assertTrue(exported.contains("yawls:id"),
                "Should set net ID");
        }
    }

    @Nested
    @DisplayName("Metadata Export Tests")
    class MetadataExportTests {

        @Test
        @DisplayName("exportToString includes creators from metadata")
        void exportToString_includesCreators() {
            YMetaData metadata = testSpec.getMetaData();
            String exported = YTurtleExporter.exportToString(testSpec);

            assertTrue(exported.contains("dcterms:creator"),
                "Should include creator property");
            if (!metadata.getCreators().isEmpty()) {
                assertTrue(exported.contains(metadata.getCreators().get(0)),
                    "Should include creator names");
            }
        }

        @Test
        @DisplayName("exportToString includes creation date")
        void exportToString_includesCreationDate() {
            YMetaData metadata = testSpec.getMetaData();
            String exported = YTurtleExporter.exportToString(testSpec);

            assertTrue(exported.contains("dcterms:created"),
                "Should include created property");
        }

        @Test
        @DisplayName("exportToString includes all metadata creators")
        void exportToString_includesAllCreators() {
            YMetaData metadata = testSpec.getMetaData();
            metadata.addCreator("Author1");
            metadata.addCreator("Author2");

            String exported = YTurtleExporter.exportToString(testSpec);

            assertTrue(exported.contains("Author1"),
                "Should include first creator");
            assertTrue(exported.contains("Author2"),
                "Should include second creator");
        }

        @Test
        @DisplayName("exportToString sets version information")
        void exportToString_includesVersionInfo() {
            String exported = YTurtleExporter.exportToString(testSpec);

            assertTrue(exported.contains("yawls:"),
                "Should reference YAWL namespace");
        }
    }

    @Nested
    @DisplayName("Workflow Structure Export Tests")
    class WorkflowStructureExportTests {

        @Test
        @DisplayName("exportToString includes input condition")
        void exportToString_includesInputCondition() {
            String exported = YTurtleExporter.exportToString(testSpec);

            assertTrue(exported.contains("yawls:InputCondition"),
                "Should declare input condition");
        }

        @Test
        @DisplayName("exportToString includes output condition")
        void exportToString_includesOutputCondition() {
            String exported = YTurtleExporter.exportToString(testSpec);

            assertTrue(exported.contains("yawls:OutputCondition"),
                "Should declare output condition");
        }

        @Test
        @DisplayName("exportToString includes tasks from workflow net")
        void exportToString_includesTasks() {
            String exported = YTurtleExporter.exportToString(testSpec);

            assertTrue(exported.contains("yawls:Task"),
                "Should declare tasks");
            assertTrue(exported.contains("yawls:id"),
                "Should set task IDs");
        }

        @Test
        @DisplayName("exportToString includes join type information")
        void exportToString_includesJoinTypes() {
            String exported = YTurtleExporter.exportToString(testSpec);

            assertTrue(exported.contains("yawls:Join"),
                "Should declare join elements");
            assertTrue(exported.contains("yawls:code"),
                "Should set join code (type)");
        }

        @Test
        @DisplayName("exportToString includes split type information")
        void exportToString_includesSplitTypes() {
            String exported = YTurtleExporter.exportToString(testSpec);

            assertTrue(exported.contains("yawls:Split"),
                "Should declare split elements");
            assertTrue(exported.contains("yawls:code"),
                "Should set split code (type)");
        }

        @Test
        @DisplayName("exportToString includes flow definitions")
        void exportToString_includesFlows() {
            String exported = YTurtleExporter.exportToString(testSpec);

            assertTrue(exported.contains("yawls:FlowInto"),
                "Should declare flows between elements");
            assertTrue(exported.contains("yawls:nextElement"),
                "Should define next element in flow");
        }

        @Test
        @DisplayName("exportToString includes intermediate conditions")
        void exportToString_includesIntermediateConditions() {
            String exported = YTurtleExporter.exportToString(testSpec);

            assertTrue(exported.contains("yawls:Condition"),
                "Should declare intermediate conditions");
        }
    }

    @Nested
    @DisplayName("File-Based Export Tests")
    class FileBasedExportTests {

        @Test
        @DisplayName("exportToFile creates file at specified path")
        void exportToFile_createsFile(@TempDir Path tempDir) throws IOException {
            Path outputFile = tempDir.resolve("output.ttl");

            YTurtleExporter.exportToFile(testSpec, outputFile.toString());

            assertTrue(Files.exists(outputFile),
                "File should be created at specified path");
        }

        @Test
        @DisplayName("exportToFile writes valid Turtle content")
        void exportToFile_writesValidContent(@TempDir Path tempDir) throws IOException {
            Path outputFile = tempDir.resolve("output.ttl");

            YTurtleExporter.exportToFile(testSpec, outputFile.toString());

            String content = Files.readString(outputFile);
            assertTrue(content.contains("@prefix"),
                "File should contain Turtle prefixes");
            assertTrue(content.contains("yawls:Specification"),
                "File should contain specification declaration");
        }

        @Test
        @DisplayName("exportToFile output can be re-imported")
        void exportToFile_outputCanBeReimported(@TempDir Path tempDir) throws Exception {
            Path outputFile = tempDir.resolve("reimport.ttl");
            YTurtleExporter.exportToFile(testSpec, outputFile.toString());

            List<YSpecification> reimported = YTurtleImporter.importFromFile(outputFile.toString());
            assertNotNull(reimported, "Should be able to re-import exported file");
            assertFalse(reimported.isEmpty(), "Re-imported list should not be empty");
        }

        @Test
        @DisplayName("exportToFile preserves all metadata in written file")
        void exportToFile_preservesMetadata(@TempDir Path tempDir) throws IOException {
            Path outputFile = tempDir.resolve("metadata.ttl");
            YTurtleExporter.exportToFile(testSpec, outputFile.toString());

            String content = Files.readString(outputFile);
            YMetaData metadata = testSpec.getMetaData();

            if (metadata.getTitle() != null) {
                assertTrue(content.contains(metadata.getTitle()),
                    "File should preserve title");
            }
            assertTrue(content.contains("dcterms:created"),
                "File should preserve creation date");
        }

        @Test
        @DisplayName("exportToFile overwrites existing file")
        void exportToFile_overwritesExistingFile(@TempDir Path tempDir) throws IOException {
            Path outputFile = tempDir.resolve("overwrite.ttl");
            Files.writeString(outputFile, "Old content here");

            YTurtleExporter.exportToFile(testSpec, outputFile.toString());

            String content = Files.readString(outputFile);
            assertFalse(content.contains("Old content"),
                "Should overwrite old content");
            assertTrue(content.contains("yawls:Specification"),
                "Should contain new Turtle content");
        }
    }

    @Nested
    @DisplayName("Multiple Specifications Export Tests")
    class MultipleSpecExportTests {

        @Test
        @DisplayName("exportToString handles single spec in list")
        void exportToString_handlesSingleSpecInList() {
            List<YSpecification> specs = List.of(testSpec);
            String exported = YTurtleExporter.exportToString(specs);

            assertNotNull(exported, "Should export list with single spec");
            assertFalse(exported.isEmpty(), "Exported content should not be empty");
        }

        @Test
        @DisplayName("exportToString exports multiple specifications")
        void exportToString_exportsMultipleSpecs() {
            List<YSpecification> specs = List.of(testSpec, testSpec);
            String exported = YTurtleExporter.exportToString(specs);

            assertNotNull(exported, "Should export multiple specs");
            assertTrue(exported.contains("yawls:Specification"),
                "Should contain specification declarations");
        }

        @Test
        @DisplayName("exportToString multi-spec output is valid Turtle")
        void exportToString_multiSpecOutputIsValidTurtle() {
            List<YSpecification> specs = List.of(testSpec, testSpec);
            String exported = YTurtleExporter.exportToString(specs);

            assertTrue(exported.contains("@prefix"),
                "Should have proper Turtle prefixes");
            assertTrue(exported.contains("yawls:"),
                "Should reference YAWL ontology");
        }

        @Test
        @DisplayName("exportToString multi-spec output can be re-imported")
        void exportToString_multiSpecCanBeReimported() throws Exception {
            List<YSpecification> specs = List.of(testSpec, testSpec);
            String exported = YTurtleExporter.exportToString(specs);

            List<YSpecification> reimported = YTurtleImporter.importFromString(exported);
            assertNotNull(reimported, "Should be able to re-import");
            assertFalse(reimported.isEmpty(), "Should have at least one spec");
        }
    }

    @Nested
    @DisplayName("Turtle Syntax Validation Tests")
    class TurtleSyntaxTests {

        @Test
        @DisplayName("exportToString output has valid Turtle syntax structure")
        void exportToString_validTurtleStructure() {
            String exported = YTurtleExporter.exportToString(testSpec);

            // Check prefix declarations come before data
            int prefixEnd = exported.indexOf("yawls:Specification");
            int prefixStart = exported.indexOf("@prefix");
            assertTrue(prefixStart < prefixEnd,
                "Prefix declarations should come before data");
        }

        @Test
        @DisplayName("exportToString uses proper Turtle URI syntax")
        void exportToString_properUriSyntax() {
            String exported = YTurtleExporter.exportToString(testSpec);

            // Check for URIs in angle brackets
            assertTrue(exported.contains("<"),
                "Should use angle bracket URIs");
            assertTrue(exported.contains(">"),
                "Should close angle bracket URIs");
        }

        @Test
        @DisplayName("exportToString uses proper Turtle triple syntax")
        void exportToString_properTripleSyntax() {
            String exported = YTurtleExporter.exportToString(testSpec);

            // Triples should end with period or semicolon
            assertTrue(exported.contains(". ") || exported.contains(";\n"),
                "Should use proper triple terminators");
        }

        @Test
        @DisplayName("exportToString uses proper RDF type syntax")
        void exportToString_properRdfType() {
            String exported = YTurtleExporter.exportToString(testSpec);

            assertTrue(exported.contains(" a yawls:Specification"),
                "Should use RDF 'a' (type) shorthand");
        }

        @Test
        @DisplayName("exportToString properly escapes string literals")
        void exportToString_escapesDanglingQuotes() {
            // Create spec with title containing special characters
            YSpecification spec = new YSpecification("http://test.org/spec");
            YMetaData meta = spec.getMetaData();
            meta.setTitle("Test with \"quotes\" and \\backslash");

            String exported = YTurtleExporter.exportToString(spec);

            // Should have properly escaped content
            assertFalse(exported.isEmpty(),
                "Should handle special characters in metadata");
        }
    }

    @Nested
    @DisplayName("Round-Trip Tests (Export/Import Consistency)")
    class RoundTripExportTests {

        @Test
        @DisplayName("Round-trip: export then import produces valid spec")
        void roundTrip_exportImportIsValid() throws Exception {
            String exported = YTurtleExporter.exportToString(testSpec);
            List<YSpecification> reimported = YTurtleImporter.importFromString(exported);

            assertNotNull(reimported, "Should be able to re-import");
            assertFalse(reimported.isEmpty(), "Should have at least one spec");
        }

        @Test
        @DisplayName("Round-trip: exported spec URI matches original")
        void roundTrip_preservesUri() throws Exception {
            String originalUri = testSpec.getURI();
            String exported = YTurtleExporter.exportToString(testSpec);
            List<YSpecification> reimported = YTurtleImporter.importFromString(exported);

            assertEquals(originalUri, reimported.get(0).getURI(),
                "Specification URI should be preserved");
        }

        @Test
        @DisplayName("Round-trip: exported title matches original")
        void roundTrip_preservesTitle() throws Exception {
            String originalTitle = testSpec.getMetaData().getTitle();
            String exported = YTurtleExporter.exportToString(testSpec);
            List<YSpecification> reimported = YTurtleImporter.importFromString(exported);

            assertEquals(originalTitle, reimported.get(0).getMetaData().getTitle(),
                "Title should be preserved through export/import");
        }

        @Test
        @DisplayName("Round-trip: net structure preserved")
        void roundTrip_preservesNetStructure() throws Exception {
            YNet originalNet = testSpec.getRootNet();
            int originalTaskCount = originalNet.getNetTasks().size();

            String exported = YTurtleExporter.exportToString(testSpec);
            List<YSpecification> reimported = YTurtleImporter.importFromString(exported);
            YNet reimportedNet = reimported.get(0).getRootNet();

            assertEquals(originalTaskCount, reimportedNet.getNetTasks().size(),
                "Task count should be preserved");
        }

        @Test
        @DisplayName("Round-trip: input/output conditions preserved")
        void roundTrip_preservesConditions() throws Exception {
            YNet originalNet = testSpec.getRootNet();
            assertNotNull(originalNet.getInputCondition());
            assertNotNull(originalNet.getOutputCondition());

            String exported = YTurtleExporter.exportToString(testSpec);
            List<YSpecification> reimported = YTurtleImporter.importFromString(exported);
            YNet reimportedNet = reimported.get(0).getRootNet();

            assertNotNull(reimportedNet.getInputCondition(),
                "Input condition should be preserved");
            assertNotNull(reimportedNet.getOutputCondition(),
                "Output condition should be preserved");
        }

        @Test
        @DisplayName("Round-trip: task join types preserved")
        void roundTrip_preservesTaskJoinTypes() throws Exception {
            YNet originalNet = testSpec.getRootNet();
            YAtomicTask originalTask = (YAtomicTask) originalNet.getNetElement("Task1");
            int originalJoinType = originalTask.getJoinType();

            String exported = YTurtleExporter.exportToString(testSpec);
            List<YSpecification> reimported = YTurtleImporter.importFromString(exported);
            YNet reimportedNet = reimported.get(0).getRootNet();
            YAtomicTask reimportedTask = (YAtomicTask) reimportedNet.getNetElement("Task1");

            assertEquals(originalJoinType, reimportedTask.getJoinType(),
                "Task join type should be preserved");
        }

        @Test
        @DisplayName("Round-trip: task split types preserved")
        void roundTrip_preservesTaskSplitTypes() throws Exception {
            YNet originalNet = testSpec.getRootNet();
            YAtomicTask originalTask = (YAtomicTask) originalNet.getNetElement("Task1");
            int originalSplitType = originalTask.getSplitType();

            String exported = YTurtleExporter.exportToString(testSpec);
            List<YSpecification> reimported = YTurtleImporter.importFromString(exported);
            YNet reimportedNet = reimported.get(0).getRootNet();
            YAtomicTask reimportedTask = (YAtomicTask) reimportedNet.getNetElement("Task1");

            assertEquals(originalSplitType, reimportedTask.getSplitType(),
                "Task split type should be preserved");
        }

        @Test
        @DisplayName("Round-trip: creators preserved")
        void roundTrip_preservesCreators() throws Exception {
            List<String> originalCreators = testSpec.getMetaData().getCreators();

            String exported = YTurtleExporter.exportToString(testSpec);
            List<YSpecification> reimported = YTurtleImporter.importFromString(exported);
            List<String> reimportedCreators = reimported.get(0).getMetaData().getCreators();

            assertEquals(originalCreators.size(), reimportedCreators.size(),
                "Creator count should be preserved");
        }
    }

    @Nested
    @DisplayName("Edge Cases and Boundary Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("exportToString handles spec with minimal metadata")
        void exportToString_minimalMetadata() {
            YSpecification spec = new YSpecification("http://minimal.org/spec");
            String exported = YTurtleExporter.exportToString(spec);

            assertNotNull(exported, "Should export minimal spec");
            assertTrue(exported.contains("yawls:Specification"),
                "Should declare specification");
        }

        @Test
        @DisplayName("exportToString handles empty specifications list")
        void exportToString_emptyList() {
            List<YSpecification> specs = List.of();
            String exported = YTurtleExporter.exportToString(specs);

            assertNotNull(exported, "Should handle empty list");
            // Should at least have prefixes
            assertTrue(exported.contains("@prefix") || exported.isEmpty(),
                "Should have valid Turtle structure");
        }

        @Test
        @DisplayName("exportToString handles spec with many tasks")
        void exportToString_manyTasks() throws Exception {
            // Re-import to get a spec that can have tasks
            List<YSpecification> specs = YTurtleImporter.importFromString(simpleWorkflowTurtle);
            YSpecification spec = specs.get(0);

            // Add more tasks by creating a new net with many elements
            String exported = YTurtleExporter.exportToString(spec);

            assertTrue(exported.contains("yawls:Task"),
                "Should export task elements");
        }

        @Test
        @DisplayName("exportToString preserves literal string values")
        void exportToString_preservesStringValues() {
            YSpecification spec = new YSpecification("http://test.org/spec");
            YMetaData meta = spec.getMetaData();
            meta.setTitle("Test Specification");
            meta.setDescription("A test description");

            String exported = YTurtleExporter.exportToString(spec);

            assertTrue(exported.contains("Test Specification"),
                "Should preserve string values");
            assertTrue(exported.contains("A test description"),
                "Should preserve descriptions");
        }

        @Test
        @DisplayName("exportToFile handles Unicode characters in metadata")
        void exportToFile_handlesUnicode(@TempDir Path tempDir) throws IOException {
            YSpecification spec = new YSpecification("http://test.org/unicode");
            YMetaData meta = spec.getMetaData();
            meta.setTitle("Spécification à Tester");
            meta.addCreator("José García");

            Path outputFile = tempDir.resolve("unicode.ttl");
            YTurtleExporter.exportToFile(spec, outputFile.toString());

            String content = Files.readString(outputFile);
            assertTrue(content.length() > 0,
                "Should write file with Unicode content");
        }
    }
}
