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

package org.yawlfoundation.yawl.unmarshal.turtle;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TempDir;

import org.yawlfoundation.yawl.elements.YAtomicTask;
import org.yawlfoundation.yawl.elements.YCondition;
import org.yawlfoundation.yawl.elements.YInputCondition;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YOutputCondition;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.exceptions.YSyntaxException;
import org.yawlfoundation.yawl.schema.turtle.YTurtleExporter;
import org.yawlfoundation.yawl.unmarshal.YMetaData;

/**
 * Comprehensive test suite for YTurtleImporter.
 *
 * Tests cover:
 * - Importing valid Turtle specifications
 * - Parsing specification metadata and properties
 * - Extracting workflow nets and elements (conditions, tasks, flows)
 * - Error handling for malformed Turtle
 * - Round-trip import/export consistency
 * - File-based imports from Turtle fixtures
 *
 * @author Test Suite
 */
@DisplayName("YTurtleImporter Test Suite")
class YTurtleImporterTest {

    private String simpleWorkflowTurtle;

    @BeforeEach
    void setUp() throws IOException {
        // Load the simple workflow fixture
        Path fixturePath = new File("test/resources/turtle/simple-workflow.ttl").toPath();
        simpleWorkflowTurtle = Files.readString(fixturePath);
    }

    @Nested
    @DisplayName("Basic Import Tests")
    class BasicImportTests {

        @Test
        @DisplayName("importFromString returns non-null list")
        void importFromString_returnsNonNullList() throws YSyntaxException {
            List<YSpecification> specs = YTurtleImporter.importFromString(simpleWorkflowTurtle);

            assertNotNull(specs, "Import should return non-null list");
            assertFalse(specs.isEmpty(), "Import should return non-empty list");
        }

        @Test
        @DisplayName("importFromString parses single specification")
        void importFromString_parsesSingleSpecification() throws YSyntaxException {
            List<YSpecification> specs = YTurtleImporter.importFromString(simpleWorkflowTurtle);

            assertEquals(1, specs.size(), "Should parse exactly one specification");
            assertNotNull(specs.get(0), "Specification should not be null");
        }

        @Test
        @DisplayName("importFromString extracts specification URI")
        void importFromString_extractsSpecificationUri() throws YSyntaxException {
            List<YSpecification> specs = YTurtleImporter.importFromString(simpleWorkflowTurtle);
            YSpecification spec = specs.get(0);

            assertNotNull(spec.getURI(), "Specification URI should be extracted");
            assertFalse(spec.getURI().isEmpty(), "Specification URI should not be empty");
            assertTrue(spec.getURI().contains("SimpleWorkflow"),
                "Specification URI should contain workflow name");
        }

        @Test
        @DisplayName("importFromString extracts specification title from dcterms:title")
        void importFromString_extractsTitle() throws YSyntaxException {
            List<YSpecification> specs = YTurtleImporter.importFromString(simpleWorkflowTurtle);
            YSpecification spec = specs.get(0);
            YMetaData metadata = spec.getMetaData();

            assertNotNull(metadata, "Metadata should be extracted");
            assertNotNull(metadata.getTitle(), "Title should be extracted from dcterms:title");
            assertEquals("Simple Test Workflow", metadata.getTitle(),
                "Title should match dcterms:title value");
        }

        @Test
        @DisplayName("importFromString extracts specification description")
        void importFromString_extractsDescription() throws YSyntaxException {
            List<YSpecification> specs = YTurtleImporter.importFromString(simpleWorkflowTurtle);
            YSpecification spec = specs.get(0);
            YMetaData metadata = spec.getMetaData();

            assertNotNull(metadata.getDescription(), "Description should be extracted");
            assertTrue(metadata.getDescription().contains("minimal workflow"),
                "Description should match dcterms:description");
        }

        @Test
        @DisplayName("importFromString extracts creators from dcterms:creator")
        void importFromString_extractsCreators() throws YSyntaxException {
            List<YSpecification> specs = YTurtleImporter.importFromString(simpleWorkflowTurtle);
            YSpecification spec = specs.get(0);
            YMetaData metadata = spec.getMetaData();

            assertNotNull(metadata.getCreators(), "Creators list should exist");
            assertFalse(metadata.getCreators().isEmpty(), "Should have at least one creator");
            assertTrue(metadata.getCreators().contains("Test Suite"),
                "Creator should match dcterms:creator value");
        }

        @Test
        @DisplayName("importFromString extracts creation date")
        void importFromString_extractsCreationDate() throws YSyntaxException {
            List<YSpecification> specs = YTurtleImporter.importFromString(simpleWorkflowTurtle);
            YSpecification spec = specs.get(0);
            YMetaData metadata = spec.getMetaData();

            assertNotNull(metadata.getCreated(), "Creation date should be extracted");
            assertEquals(21, metadata.getCreated().getDayOfMonth(),
                "Day should be correctly parsed");
            assertEquals(2, metadata.getCreated().getMonthValue(),
                "Month should be correctly parsed");
        }
    }

    @Nested
    @DisplayName("Workflow Net Structure Tests")
    class WorkflowNetStructureTests {

        @Test
        @DisplayName("importFromString creates root net")
        void importFromString_createsRootNet() throws YSyntaxException {
            List<YSpecification> specs = YTurtleImporter.importFromString(simpleWorkflowTurtle);
            YSpecification spec = specs.get(0);

            assertNotNull(spec.getRootNet(), "Root net should be created");
        }

        @Test
        @DisplayName("importFromString sets root net ID")
        void importFromString_setsRootNetId() throws YSyntaxException {
            List<YSpecification> specs = YTurtleImporter.importFromString(simpleWorkflowTurtle);
            YSpecification spec = specs.get(0);
            YNet rootNet = spec.getRootNet();

            assertNotNull(rootNet.getID(), "Net ID should be set");
            assertEquals("RootNet", rootNet.getID(), "Net ID should match yawls:id");
        }

        @Test
        @DisplayName("importFromString creates input condition")
        void importFromString_createsInputCondition() throws YSyntaxException {
            List<YSpecification> specs = YTurtleImporter.importFromString(simpleWorkflowTurtle);
            YSpecification spec = specs.get(0);
            YNet rootNet = spec.getRootNet();

            assertNotNull(rootNet.getInputCondition(), "Input condition should be created");
            assertInstanceOf(YInputCondition.class, rootNet.getInputCondition(),
                "Should be an instance of YInputCondition");
        }

        @Test
        @DisplayName("importFromString creates output condition")
        void importFromString_createsOutputCondition() throws YSyntaxException {
            List<YSpecification> specs = YTurtleImporter.importFromString(simpleWorkflowTurtle);
            YSpecification spec = specs.get(0);
            YNet rootNet = spec.getRootNet();

            assertNotNull(rootNet.getOutputCondition(), "Output condition should be created");
            assertInstanceOf(YOutputCondition.class, rootNet.getOutputCondition(),
                "Should be an instance of YOutputCondition");
        }

        @Test
        @DisplayName("importFromString creates intermediate conditions")
        void importFromString_createsIntermediateConditions() throws YSyntaxException {
            List<YSpecification> specs = YTurtleImporter.importFromString(simpleWorkflowTurtle);
            YSpecification spec = specs.get(0);
            YNet rootNet = spec.getRootNet();

            var conditions = rootNet.getConditions();
            assertNotNull(conditions, "Conditions should exist");
            assertEquals(1, conditions.size(), "Should have one intermediate condition");

            YCondition cond = conditions.iterator().next();
            assertEquals("Condition1", cond.getID(), "Condition ID should match yawls:id");
        }

        @Test
        @DisplayName("importFromString creates tasks")
        void importFromString_createsTasks() throws YSyntaxException {
            List<YSpecification> specs = YTurtleImporter.importFromString(simpleWorkflowTurtle);
            YSpecification spec = specs.get(0);
            YNet rootNet = spec.getRootNet();

            var tasks = rootNet.getNetTasks();
            assertNotNull(tasks, "Tasks should exist");
            assertEquals(2, tasks.size(), "Should have two tasks");

            var taskIds = tasks.stream().map(t -> t.getID()).toList();
            assertTrue(taskIds.contains("Task1"), "Should contain Task1");
            assertTrue(taskIds.contains("Task2"), "Should contain Task2");
        }

        @Test
        @DisplayName("importFromString sets task join types")
        void importFromString_setsTaskJoinTypes() throws YSyntaxException {
            List<YSpecification> specs = YTurtleImporter.importFromString(simpleWorkflowTurtle);
            YSpecification spec = specs.get(0);
            YNet rootNet = spec.getRootNet();

            YAtomicTask task1 = (YAtomicTask) rootNet.getNetElement("Task1");
            assertNotNull(task1, "Task1 should exist");
            assertEquals(YAtomicTask._XOR, task1.getJoinType(),
                "Task1 join type should be XOR");

            YAtomicTask task2 = (YAtomicTask) rootNet.getNetElement("Task2");
            assertNotNull(task2, "Task2 should exist");
            assertEquals(YAtomicTask._AND, task2.getJoinType(),
                "Task2 join type should be AND");
        }

        @Test
        @DisplayName("importFromString sets task split types")
        void importFromString_setsTaskSplitTypes() throws YSyntaxException {
            List<YSpecification> specs = YTurtleImporter.importFromString(simpleWorkflowTurtle);
            YSpecification spec = specs.get(0);
            YNet rootNet = spec.getRootNet();

            YAtomicTask task1 = (YAtomicTask) rootNet.getNetElement("Task1");
            assertEquals(YAtomicTask._XOR, task1.getSplitType(),
                "Task1 split type should be XOR");

            YAtomicTask task2 = (YAtomicTask) rootNet.getNetElement("Task2");
            assertEquals(YAtomicTask._XOR, task2.getSplitType(),
                "Task2 split type should be XOR");
        }

        @Test
        @DisplayName("importFromString creates flows between elements")
        void importFromString_createsFlows() throws YSyntaxException {
            List<YSpecification> specs = YTurtleImporter.importFromString(simpleWorkflowTurtle);
            YSpecification spec = specs.get(0);
            YNet rootNet = spec.getRootNet();

            YInputCondition inputCond = rootNet.getInputCondition();
            assertNotNull(inputCond, "Input condition should exist");
            assertFalse(inputCond.getPostsetFlow().isEmpty(),
                "Input condition should have outgoing flows");

            YAtomicTask task1 = (YAtomicTask) rootNet.getNetElement("Task1");
            assertNotNull(task1, "Task1 should exist");
            assertFalse(task1.getPostsetFlow().isEmpty(),
                "Task1 should have outgoing flows");
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("importFromString throws on invalid Turtle syntax")
        void importFromString_throwsOnInvalidTurtle() {
            String invalidTurtle = "@prefix invalid syntax without proper format";

            assertThrows(YSyntaxException.class, () ->
                YTurtleImporter.importFromString(invalidTurtle),
                "Should throw YSyntaxException for malformed Turtle");
        }

        @Test
        @DisplayName("importFromString throws when no specifications found")
        void importFromString_throwsWhenNoSpecifications() {
            String turtleWithoutSpec = """
                @prefix yawls: <http://www.yawlfoundation.org/yawlschema#> .
                @prefix dcterms: <http://purl.org/dc/terms/> .

                <urn:example:empty> a yawls:WorkflowNet ;
                    yawls:id "EmptyNet" .
                """;

            assertThrows(YSyntaxException.class, () ->
                YTurtleImporter.importFromString(turtleWithoutSpec),
                "Should throw YSyntaxException when no Specification found");
        }

        @Test
        @DisplayName("importFromString throws on empty content")
        void importFromString_throwsOnEmptyContent() {
            assertThrows(YSyntaxException.class, () ->
                YTurtleImporter.importFromString(""),
                "Should throw YSyntaxException for empty content");
        }

        @Test
        @DisplayName("importFromFile throws on missing file")
        void importFromFile_throwsOnMissingFile() {
            assertThrows(YSyntaxException.class, () ->
                YTurtleImporter.importFromFile("/nonexistent/path/file.ttl"),
                "Should throw YSyntaxException for missing file");
        }

        @Test
        @DisplayName("importFromFile throws on malformed file content")
        void importFromFile_throwsOnMalformedContent(@TempDir Path tempDir) throws IOException {
            Path badFile = tempDir.resolve("bad.ttl");
            Files.writeString(badFile, "This is not valid Turtle syntax !!!!");

            assertThrows(YSyntaxException.class, () ->
                YTurtleImporter.importFromFile(badFile.toString()),
                "Should throw YSyntaxException for malformed file content");
        }
    }

    @Nested
    @DisplayName("File-Based Import Tests")
    class FileBasedImportTests {

        @Test
        @DisplayName("importFromFile loads simple-workflow.ttl fixture")
        void importFromFile_loadsSampleFixture() throws YSyntaxException {
            String fixturePath = "test/resources/turtle/simple-workflow.ttl";
            List<YSpecification> specs = YTurtleImporter.importFromFile(fixturePath);

            assertNotNull(specs, "Should return non-null list");
            assertEquals(1, specs.size(), "Should parse one specification");

            YSpecification spec = specs.get(0);
            assertNotNull(spec.getRootNet(), "Should have root net");
            assertEquals("RootNet", spec.getRootNet().getID(),
                "Root net ID should match fixture");
        }

        @Test
        @DisplayName("importFromFile preserves all metadata from file")
        void importFromFile_preservesMetadata() throws YSyntaxException {
            String fixturePath = "test/resources/turtle/simple-workflow.ttl";
            List<YSpecification> specs = YTurtleImporter.importFromFile(fixturePath);

            YSpecification spec = specs.get(0);
            YMetaData metadata = spec.getMetaData();

            assertEquals("Simple Test Workflow", metadata.getTitle(),
                "Title should be preserved");
            assertTrue(metadata.getCreators().contains("Test Suite"),
                "Creators should be preserved");
        }

        @Test
        @DisplayName("importFromFile reads file from correct path")
        void importFromFile_readsFromCorrectPath(@TempDir Path tempDir) throws YSyntaxException, IOException {
            // Create a temporary Turtle file
            Path turtleFile = tempDir.resolve("test.ttl");
            Files.writeString(turtleFile, simpleWorkflowTurtle);

            List<YSpecification> specs = YTurtleImporter.importFromFile(turtleFile.toString());
            assertNotNull(specs, "Should load from specified path");
            assertFalse(specs.isEmpty(), "Should parse specifications from file");
        }
    }

    @Nested
    @DisplayName("Round-Trip Tests (Import/Export Consistency)")
    class RoundTripTests {

        @Test
        @DisplayName("Round-trip: import then export produces valid Turtle")
        void roundTrip_importExport_producesValidTurtle() throws YSyntaxException {
            // Import
            List<YSpecification> importedSpecs = YTurtleImporter.importFromString(simpleWorkflowTurtle);
            YSpecification spec = importedSpecs.get(0);

            // Export back to Turtle
            String exportedTurtle = YTurtleExporter.exportToString(spec);

            // Verify exported Turtle is valid by importing it again
            List<YSpecification> reimportedSpecs = YTurtleImporter.importFromString(exportedTurtle);
            assertNotNull(reimportedSpecs, "Should be able to re-import exported Turtle");
            assertFalse(reimportedSpecs.isEmpty(), "Should parse re-imported Turtle");
        }

        @Test
        @DisplayName("Round-trip: imported spec has same URI after export/import cycle")
        void roundTrip_preservesSpecificationUri() throws YSyntaxException {
            List<YSpecification> importedSpecs = YTurtleImporter.importFromString(simpleWorkflowTurtle);
            YSpecification spec1 = importedSpecs.get(0);
            String originalUri = spec1.getURI();

            String exported = YTurtleExporter.exportToString(spec1);
            List<YSpecification> reimported = YTurtleImporter.importFromString(exported);
            YSpecification spec2 = reimported.get(0);

            assertEquals(originalUri, spec2.getURI(),
                "Specification URI should be preserved through export/import cycle");
        }

        @Test
        @DisplayName("Round-trip: net structure preserved")
        void roundTrip_preservesNetStructure() throws YSyntaxException {
            List<YSpecification> importedSpecs = YTurtleImporter.importFromString(simpleWorkflowTurtle);
            YSpecification spec1 = importedSpecs.get(0);
            YNet net1 = spec1.getRootNet();
            int originalTaskCount = net1.getNetTasks().size();

            String exported = YTurtleExporter.exportToString(spec1);
            List<YSpecification> reimported = YTurtleImporter.importFromString(exported);
            YSpecification spec2 = reimported.get(0);
            YNet net2 = spec2.getRootNet();

            assertEquals(originalTaskCount, net2.getNetTasks().size(),
                "Task count should be preserved through cycle");
        }

        @Test
        @DisplayName("Round-trip: task types preserved")
        void roundTrip_preservesTaskTypes() throws YSyntaxException {
            List<YSpecification> importedSpecs = YTurtleImporter.importFromString(simpleWorkflowTurtle);
            YSpecification spec1 = importedSpecs.get(0);
            YAtomicTask task1Original = (YAtomicTask) spec1.getRootNet().getNetElement("Task2");
            int originalJoinType = task1Original.getJoinType();

            String exported = YTurtleExporter.exportToString(spec1);
            List<YSpecification> reimported = YTurtleImporter.importFromString(exported);
            YSpecification spec2 = reimported.get(0);
            YAtomicTask task1Reimported = (YAtomicTask) spec2.getRootNet().getNetElement("Task2");

            assertEquals(originalJoinType, task1Reimported.getJoinType(),
                "Task join type should be preserved through cycle");
        }
    }

    @Nested
    @DisplayName("Edge Cases and Boundary Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("importFromString handles Turtle with multiple prefixes")
        void importFromString_handlesMultiplePrefixes() throws YSyntaxException {
            String turtleWithPrefixes = """
                @prefix yawls: <http://www.yawlfoundation.org/yawlschema#> .
                @prefix dcterms: <http://purl.org/dc/terms/> .
                @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
                @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
                @prefix custom: <http://example.org/custom#> .

                <urn:yawl:test> a yawls:Specification ;
                    yawls:uri "http://example.org/test" ;
                    dcterms:title "Test" ;
                    yawls:hasDecomposition <urn:yawl:test#net-main> ;
                    yawls:hasRootNet <urn:yawl:test#net-main> .

                <urn:yawl:test#net-main> a yawls:WorkflowNet ;
                    yawls:id "Main" ;
                    yawls:hasInputCondition <urn:yawl:test#net-main#element-in> ;
                    yawls:hasOutputCondition <urn:yawl:test#net-main#element-out> .

                <urn:yawl:test#net-main#element-in> a yawls:InputCondition ;
                    yawls:id "In" .

                <urn:yawl:test#net-main#element-out> a yawls:OutputCondition ;
                    yawls:id "Out" .
                """;

            List<YSpecification> specs = YTurtleImporter.importFromString(turtleWithPrefixes);
            assertNotNull(specs, "Should handle multiple prefixes");
            assertEquals(1, specs.size(), "Should parse specification");
        }

        @Test
        @DisplayName("importFromString tolerates missing optional metadata")
        void importFromString_toleratesMissingOptionalMetadata() throws YSyntaxException {
            String minimalTurtle = """
                @prefix yawls: <http://www.yawlfoundation.org/yawlschema#> .

                <urn:yawl:minimal> a yawls:Specification ;
                    yawls:hasDecomposition <urn:yawl:minimal#net-root> ;
                    yawls:hasRootNet <urn:yawl:minimal#net-root> .

                <urn:yawl:minimal#net-root> a yawls:WorkflowNet ;
                    yawls:id "Root" ;
                    yawls:hasInputCondition <urn:yawl:minimal#net-root#in> ;
                    yawls:hasOutputCondition <urn:yawl:minimal#net-root#out> .

                <urn:yawl:minimal#net-root#in> a yawls:InputCondition ;
                    yawls:id "In" .

                <urn:yawl:minimal#net-root#out> a yawls:OutputCondition ;
                    yawls:id "Out" .
                """;

            List<YSpecification> specs = YTurtleImporter.importFromString(minimalTurtle);
            assertNotNull(specs, "Should handle minimal specification");
            YSpecification spec = specs.get(0);
            assertNull(spec.getMetaData().getTitle(),
                "Should tolerate missing title");
        }

        @Test
        @DisplayName("importFromString handles specifications with many tasks")
        void importFromString_handlesMultipleTasks() throws YSyntaxException {
            // Build a Turtle spec with multiple tasks
            StringBuilder sb = new StringBuilder();
            sb.append("@prefix yawls: <http://www.yawlfoundation.org/yawlschema#> .\n");
            sb.append("@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .\n\n");
            sb.append("<urn:yawl:multi> a yawls:Specification ;\n");
            sb.append("    yawls:hasDecomposition <urn:yawl:multi#net-root> ;\n");
            sb.append("    yawls:hasRootNet <urn:yawl:multi#net-root> .\n\n");
            sb.append("<urn:yawl:multi#net-root> a yawls:WorkflowNet ;\n");
            sb.append("    yawls:id \"Root\" ;\n");
            sb.append("    yawls:hasInputCondition <urn:yawl:multi#net-root#in> ;\n");
            sb.append("    yawls:hasOutputCondition <urn:yawl:multi#net-root#out> ;\n");

            // Add 5 tasks
            for (int i = 1; i <= 5; i++) {
                sb.append("    yawls:hasTask <urn:yawl:multi#net-root#task").append(i).append("> ;\n");
            }
            sb.setLength(sb.length() - 2); // Remove last comma and newline
            sb.append(" .\n\n");

            // Define input/output conditions
            sb.append("<urn:yawl:multi#net-root#in> a yawls:InputCondition ;\n");
            sb.append("    yawls:id \"In\" .\n\n");
            sb.append("<urn:yawl:multi#net-root#out> a yawls:OutputCondition ;\n");
            sb.append("    yawls:id \"Out\" .\n\n");

            // Define tasks
            for (int i = 1; i <= 5; i++) {
                sb.append("<urn:yawl:multi#net-root#task").append(i).append("> a yawls:Task ;\n");
                sb.append("    yawls:id \"Task").append(i).append("\" ;\n");
                sb.append("    yawls:hasJoin <urn:yawl:multi#net-root#task").append(i).append("#join> ;\n");
                sb.append("    yawls:hasSplit <urn:yawl:multi#net-root#task").append(i).append("#split> .\n");
                sb.append("<urn:yawl:multi#net-root#task").append(i).append("#join> a yawls:Join ;\n");
                sb.append("    yawls:code yawls:XOR .\n");
                sb.append("<urn:yawl:multi#net-root#task").append(i).append("#split> a yawls:Split ;\n");
                sb.append("    yawls:code yawls:XOR .\n\n");
            }

            String multiTaskTurtle = sb.toString();
            List<YSpecification> specs = YTurtleImporter.importFromString(multiTaskTurtle);

            YSpecification spec = specs.get(0);
            YNet net = spec.getRootNet();
            assertEquals(5, net.getNetTasks().size(),
                "Should parse all 5 tasks");
        }
    }
}
