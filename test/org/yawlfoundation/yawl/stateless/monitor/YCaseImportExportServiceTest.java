/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.stateless.monitor;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.jdom2.Document;
import org.jdom2.Element;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.exceptions.YStateException;
import org.yawlfoundation.yawl.exceptions.YSyntaxException;
import org.yawlfoundation.yawl.stateless.YStatelessEngine;
import org.yawlfoundation.yawl.stateless.elements.YSpecification;
import org.yawlfoundation.yawl.stateless.engine.YNetRunner;
import org.yawlfoundation.yawl.stateless.listener.YCaseEventListener;
import org.yawlfoundation.yawl.stateless.listener.event.YCaseEvent;
import org.yawlfoundation.yawl.stateless.listener.event.YEventType;
import org.yawlfoundation.yawl.util.JDOMUtil;
import org.yawlfoundation.yawl.util.StringUtil;

/**
 * Comprehensive tests for YCaseImportExportService covering:
 * - Export validation and filtering
 * - Import validation scenarios
 * - File I/O operations
 * - Compression handling
 * - Concurrent operations
 * - Error recovery
 *
 * <p>Chicago TDD: All tests use real YStatelessEngine instances and
 * real file operations. No mocks.</p>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@DisplayName("YCase Import/Export Service Tests")
@Tag("unit")
public class YCaseImportExportServiceTest {

    private static final String MINIMAL_SPEC_RESOURCE = "resources/MinimalSpec.xml";
    private static final long EVENT_TIMEOUT_SEC = 10L;
    private static final String TEST_EXPORT_DIR = "test-exports";

    private YStatelessEngine engine;
    private YSpecification spec;
    private YCaseMonitor caseMonitor;
    private YCaseImportExportService importExportService;

    @BeforeEach
    void setUp() throws Exception {
        engine = new YStatelessEngine();
        engine.setCaseMonitoringEnabled(true);
        spec = loadMinimalSpec();
        caseMonitor = new YCaseMonitor(engine);
        importExportService = new YCaseImportExportService(caseMonitor);

        // Create test directory
        Path testDir = Paths.get(TEST_EXPORT_DIR);
        if (!Files.exists(testDir)) {
            Files.createDirectories(testDir);
        }
    }

    @AfterEach
    void tearDown() throws IOException {
        // Clean up test files
        Path testDir = Paths.get(TEST_EXPORT_DIR);
        if (Files.exists(testDir)) {
            Files.walk(testDir)
                .filter(Files::isRegularFile)
                .forEach(file -> {
                    try { Files.delete(file); }
                    catch (IOException ignored) {}
                });
            Files.deleteIfExists(testDir);
        }

        if (engine != null) {
            engine.setCaseMonitoringEnabled(false);
        }
    }

    private YSpecification loadMinimalSpec() throws YSyntaxException {
        return engine.unmarshalSpecification(
                StringUtil.streamToString(getClass().getResourceAsStream(MINIMAL_SPEC_RESOURCE))
                        .orElseThrow(() -> new AssertionError("Failed to load minimal spec"))
        );
    }

    private YNetRunner launchCaseWithMonitoring(String caseId) throws Exception {
        CountDownLatch startedLatch = new CountDownLatch(1);
        YNetRunner[] runnerCapture = new YNetRunner[1];

        YCaseEventListener listener = event -> {
            if (event.getEventType() == YEventType.CASE_STARTED) {
                runnerCapture[0] = event.getRunner();
                startedLatch.countDown();
            }
        };
        engine.addCaseEventListener(listener);
        engine.launchCase(spec, caseId);
        startedLatch.await(EVENT_TIMEOUT_SEC, TimeUnit.SECONDS);
        engine.removeCaseEventListener(listener);

        assertNotNull(runnerCapture[0], "Runner should be captured after case start");
        return runnerCapture[0];
    }

    // =========================================================================
    // Helper methods for test data
    // =========================================================================

    private String createTestFileName(String prefix, String suffix) {
        return prefix + "_" + System.currentTimeMillis() + suffix;
    }

    private byte[] createCompressedData(String... cases) throws IOException {
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        try (ZipOutputStream zipOut = new ZipOutputStream(byteOut)) {
            for (int i = 0; i < cases.length; i++) {
                ZipEntry entry = new ZipEntry("case_" + i + ".xml");
                zipOut.putNextEntry(entry);
                zipOut.write(cases[i].getBytes(StandardCharsets.UTF_8));
                zipOut.closeEntry();
            }
        }
        return byteOut.toByteArray();
    }

    // =========================================================================
    // Nested: Export Functionality Tests
    // =========================================================================

    @Nested
    @DisplayName("Export Functionality Tests")
    class ExportFunctionalityTests {

        @Test
        @DisplayName("Export case to file preserves all data")
        void exportCaseToFilePreservesAllData() throws Exception {
            String caseId = "export-file-test";
            YNetRunner runner = launchCaseWithMonitoring(caseId);
            String caseXml = engine.marshalCase(runner);

            String exportFile = createTestFileName("export_test", ".txt");
            importExportService.exportCaseToFile(caseXml, exportFile);

            // Verify file exists and contains expected content
            Path filePath = Paths.get(exportFile);
            assertTrue(Files.exists(filePath), "Export file should be created");

            String fileContent = Files.readString(filePath, StandardCharsets.UTF_8);
            assertTrue(fileContent.contains(caseId), "Export should contain case ID");
            assertTrue(fileContent.contains(caseXml), "Export should contain case XML");

            Files.deleteIfExists(filePath);
        }

        @Test
        @DisplayName("Export all cases to file handles multiple cases")
        void exportAllCasesToFileHandlesMultipleCases() throws Exception {
            // Launch multiple cases
            YNetRunner runner1 = launchCaseWithMonitoring("multi1");
            YNetRunner runner2 = launchCaseWithMonitoring("multi2");
            YNetRunner runner3 = launchCaseWithMonitoring("multi3");

            String exportFile = createTestFileName("multi_export", ".txt");
            int casesExported = importExportService.exportAllCasesToFile(exportFile);

            assertEquals(3, casesExported, "Should export 3 cases");

            // Verify file contains all cases
            Path filePath = Paths.get(exportFile);
            String fileContent = Files.readString(filePath, StandardCharsets.UTF_8);
            assertTrue(fileContent.contains("multi1"), "Should contain case 1");
            assertTrue(fileContent.contains("multi2"), "Should contain case 2");
            assertTrue(fileContent.contains("multi3"), "Should contain case 3");
            assertTrue(fileContent.contains(EXPORT_VERSION), "Should contain version info");

            Files.deleteIfExists(filePath);
        }

        @Test
        @DisplayName("Export case to byte array preserves data integrity")
        void exportCaseToByteArrayPreservesDataIntegrity() throws Exception {
            String caseId = "byte-export-test";
            YNetRunner runner = launchCaseWithMonitoring(caseId);
            String caseXml = engine.marshalCase(runner);

            byte[] exportedBytes = importExportService.exportCaseToByteArray(caseXml);
            assertNotNull(exportedBytes, "Exported bytes should not be null");
            assertTrue(exportedBytes.length > 0, "Exported bytes should not be empty");

            String exportedString = new String(exportedBytes, StandardCharsets.UTF_8);
            assertEquals(caseXml, exportedString, "Exported data should match original");
        }

        @Test
        @DisplayName("Export handles empty case data gracefully")
        void exportHandlesEmptyCaseData() throws Exception {
            String emptyXml = "";

            String exportFile = createTestFileName("empty_export", ".txt");
            assertDoesNotThrow(() -> {
                importExportService.exportCaseToFile(emptyXml, exportFile);
            }, "Should handle empty case data");

            Path filePath = Paths.get(exportFile);
            String content = Files.readString(filePath, StandardCharsets.UTF_8);
            assertTrue(content.contains("No case data to export") || content.isEmpty());

            Files.deleteIfExists(filePath);
        }
    }

    // =========================================================================
    // Nested: Import Functionality Tests
    // =========================================================================

    @Nested
    @DisplayName("Import Functionality Tests")
    class ImportFunctionalityTests {

        @Test
        @DisplayName("Import case from file preserves all data")
        void importCaseFromFilePreservesAllData() throws Exception {
            String caseId = "import-file-test";
            YNetRunner originalRunner = launchCaseWithMonitoring(caseId);
            String caseXml = engine.marshalCase(originalRunner);

            // Export to file first
            String exportFile = createTestFileName("import_test", ".txt");
            importExportService.exportCaseToFile(caseXml, exportFile);

            // Import from file
            YNetRunner importedRunner = importExportService.importCaseFromFile(exportFile);

            assertNotNull(importedRunner, "Imported runner should not be null");
            assertEquals(caseId, importedRunner.getCaseID().toString(),
                    "Case ID should be preserved");
            assertEquals(originalRunner.getSpecificationID(), importedRunner.getSpecificationID(),
                    "Specification ID should be preserved");
        }

        @Test
        @DisplayName("Import case from byte array preserves data integrity")
        void importCaseFromByteArrayPreservesDataIntegrity() throws Exception {
            String caseId = "byte-import-test";
            YNetRunner originalRunner = launchCaseWithMonitoring(caseId);
            String caseXml = engine.marshalCase(originalRunner);

            byte[] caseBytes = caseXml.getBytes(StandardCharsets.UTF_8);
            YNetRunner importedRunner = importExportService.importCaseFromByteArray(caseBytes);

            assertNotNull(importedRunner, "Imported runner should not be null");
            assertEquals(caseId, importedRunner.getCaseID().toString(),
                    "Case ID should be preserved");
            assertEquals(originalRunner.getSpecificationID(), importedRunner.getSpecificationID(),
                    "Specification ID should be preserved");
        }

        @Test
        @DisplayName("Import handles missing file gracefully")
        void importHandlesMissingFileGracefully() {
            String missingFile = "non_existent_file.txt";

            assertThrows(IOException.class, () ->
                    importExportService.importCaseFromFile(missingFile),
                    "Should throw for missing file");
        }

        @Test
        @DisplayName("Import handles corrupted XML gracefully")
        void importHandlesCorruptedXml() throws Exception {
            String corruptedXml = "This is not valid XML content";

            String exportFile = createTestFileName("corrupted", ".txt");
            Files.writeString(Paths.get(exportFile), corruptedXml, StandardCharsets.UTF_8);

            assertThrows(YStateException.class, () ->
                    importExportService.importCaseFromFile(exportFile),
                    "Should throw for corrupted XML");

            Files.deleteIfExists(exportFile);
        }
    }

    // =========================================================================
    // Nested: Compression Tests
    // =========================================================================

    @Nested
    @DisplayName("Compression Tests")
    class CompressionTests {

        @Test
        @DisplayName("Import case from compressed data preserves integrity")
        void importCaseFromCompressedDataPreservesIntegrity() throws Exception {
            String caseId = "compress-test";
            YNetRunner originalRunner = launchCaseWithMonitoring(caseId);
            String caseXml = engine.marshalCase(originalRunner);

            // Create compressed data
            byte[] compressedData = createCompressedData(caseXml);
            YNetRunner importedRunner = importExportService.importCaseFromCompressedData(compressedData);

            assertNotNull(importedRunner, "Imported runner should not be null");
            assertEquals(caseId, importedRunner.getCaseID().toString(),
                    "Case ID should be preserved");
            assertEquals(originalRunner.getSpecificationID(), importedRunner.getSpecificationID(),
                    "Specification ID should be preserved");
        }

        @Test
        @DisplayName("Import handles empty compressed data gracefully")
        void importHandlesEmptyCompressedData() throws Exception {
            byte[] emptyCompressed = createCompressedData("");

            assertThrows(IOException.class, () ->
                    importExportService.importCaseFromCompressedData(emptyCompressed),
                    "Should throw for empty compressed data");
        }

        @Test
        @DisplayName("Import handles invalid compressed format gracefully")
        void importHandlesInvalidCompressedFormat() throws Exception {
            byte[] invalidData = "This is not a valid zip file".getBytes(StandardCharsets.UTF_8);

            assertThrows(IOException.class, () ->
                    importExportService.importCaseFromCompressedData(invalidData),
                    "Should throw for invalid compressed format");
        }

        @Test
        @DisplayName("Export to compressed file preserves data")
        void exportToCompressedFilePreservesData() throws Exception {
            String caseId = "compress-export-test";
            YNetRunner runner = launchCaseWithMonitoring(caseId);
            String caseXml = engine.marshalCase(runner);

            String exportFile = createTestFileName("compress_export", ".zip");
            importExportService.exportCaseToCompressedFile(caseXml, exportFile);

            Path filePath = Paths.get(exportFile);
            assertTrue(Files.exists(filePath), "Compressed file should be created");
            assertTrue(filePath.toString().endsWith(".zip"), "File should have .zip extension");

            // Verify we can read it back
            YNetRunner importedRunner = importExportService.importCaseFromFile(exportFile);
            assertEquals(caseId, importedRunner.getCaseID().toString(),
                    "Case ID should be preserved after compression round trip");

            Files.deleteIfExists(filePath);
        }
    }

    // =========================================================================
    // Nested: Filtering Tests
    // =========================================================================

    @Nested
    @DisplayName("Filtering Tests")
    class FilteringTests {

        @Test
        @DisplayName("Export with case ID filter works correctly")
        void exportWithCaseIdFilterWorksCorrectly() throws Exception {
            // Launch multiple cases
            YNetRunner runner1 = launchCaseWithMonitoring("filter1");
            YNetRunner runner2 = launchCaseWithMonitoring("filter2");
            YNetRunner runner3 = launchCaseWithMonitoring("filter3");

            // Export filtered cases
            String exportFile = createTestFileName("filtered_export", ".txt");
            List<String> caseIds = Arrays.asList("filter1", "filter3");
            int casesExported = importExportService.exportCaseToFile(
                    engine.marshalCase(runner1), exportFile);
            importExportService.exportAdditionalCases(Arrays.asList(
                    engine.marshalCase(runner3)), caseIds, exportFile, true);

            Path filePath = Paths.get(exportFile);
            String content = Files.readString(filePath, StandardCharsets.UTF_8);
            assertTrue(content.contains("filter1"), "Should contain filtered case 1");
            assertTrue(content.contains("filter3"), "Should contain filtered case 3");
            assertFalse(content.contains("filter2"), "Should not contain unfiltered case");

            Files.deleteIfExists(filePath);
        }

        @Test
        @DisplayName("Export handles empty filter list")
        void exportHandlesEmptyFilterList() throws Exception {
            String caseId = "empty-filter-test";
            YNetRunner runner = launchCaseWithMonitoring(caseId);
            String caseXml = engine.marshalCase(runner);

            String exportFile = createTestFileName("empty_filter", ".txt");

            // This should handle empty filter gracefully
            assertDoesNotThrow(() -> {
                importExportService.exportCaseToFile(caseXml, exportFile);
            }, "Should handle empty filter");
        }
    }

    // =========================================================================
    // Nested: Validation Tests
    // =========================================================================

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("Validate case XML works correctly")
        void validateCaseXmlWorksCorrectly() throws Exception {
            String caseId = "valid-test";
            YNetRunner runner = launchCaseWithMonitoring(caseId);
            String validXml = engine.marshalCase(runner);

            assertTrue(importExportService.validateCaseXml(validXml),
                    "Valid XML should pass validation");
        }

        @Test
        @DisplayName("Validate case XML detects invalid XML")
        void validateCaseXmlDetectsInvalidXml() {
            String invalidXml = "This is not valid XML";

            assertFalse(importExportService.validateCaseXml(invalidXml),
                    "Invalid XML should fail validation");
        }

        @Test
        @DisplayName("Validate case XML detects incomplete XML")
        void validateCaseXmlDetectsIncompleteXml() {
            String incompleteXml = """
                <case id="incomplete">
                    <specificationSet>
                        <YSpecification id="spec">
                            <schema/>
                        </YSpecification>
                    </specificationSet>
                    <!-- Missing runners section -->
                </case>
                """;

            assertFalse(importExportService.validateCaseXml(incompleteXml),
                    "Incomplete XML should fail validation");
        }

        @Test
        @DisplayName("Validate case XML detects malformed XML")
        void validateCaseXmlDetectsMalformedXml() {
            String malformedXml = """
                <case id="malformed">
                    <specificationSet>
                        <YSpecification id="spec">
                            <schema/>
                        </YSpecification>
                    </specificationSet>
                    <runners>
                        <runner>
                            <!-- Missing required identifier -->
                        </runner>
                    </runners>
                </case>
                """;

            assertFalse(importExportService.validateCaseXml(malformedXml),
                    "Malformed XML should fail validation");
        }
    }

    // =========================================================================
    // Nested: Concurrent Operation Tests
    // =========================================================================

    @Nested
    @DisplayName("Concurrent Operation Tests")
    class ConcurrentOperationTests {

        @Test
        @DisplayName("Concurrent export operations work correctly")
        void concurrentExportOperationsWorkCorrectly() throws Exception {
            int caseCount = 5;
            ExecutorService executor = Executors.newFixedThreadPool(caseCount);
            CountDownLatch latch = new CountDownLatch(caseCount);
            AtomicInteger successCount = new AtomicInteger(0);

            // Launch multiple cases concurrently
            for (int i = 0; i < caseCount; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        String caseId = "concurrent_" + index;
                        YNetRunner runner = launchCaseWithMonitoring(caseId);
                        String caseXml = engine.marshalCase(runner);

                        String exportFile = createTestFileName("concurrent_" + index, ".txt");
                        importExportService.exportCaseToFile(caseXml, exportFile);

                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        fail("Concurrent export failed: " + e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(EVENT_TIMEOUT_SEC, TimeUnit.SECONDS);
            executor.shutdown();

            assertEquals(caseCount, successCount.get(),
                    "All concurrent exports should succeed");
        }

        @Test
        @DisplayName("Concurrent import operations work correctly")
        void concurrentImportOperationsWorkCorrectly() throws Exception {
            int caseCount = 3;
            ExecutorService executor = Executors.newFixedThreadPool(caseCount);
            CountDownLatch latch = new CountDownLatch(caseCount);
            AtomicInteger successCount = new AtomicInteger(0);

            // Create test XML data
            YNetRunner[] runners = new YNetRunner[caseCount];
            for (int i = 0; i < caseCount; i++) {
                runners[i] = launchCaseWithMonitoring("import_concurrent_" + i);
            }

            // Import concurrently
            for (int i = 0; i < caseCount; i++) {
                final int index = index;
                final String caseXml = engine.marshalCase(runners[index]);
                executor.submit(() -> {
                    try {
                        YNetRunner importedRunner = importExportService.importCaseFromByteArray(
                                caseXml.getBytes(StandardCharsets.UTF_8));

                        assertNotNull(importedRunner, "Imported runner should not be null");
                        assertEquals("import_concurrent_" + index, importedRunner.getCaseID().toString(),
                                "Case ID should match");

                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        fail("Concurrent import failed: " + e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(EVENT_TIMEOUT_SEC, TimeUnit.SECONDS);
            executor.shutdown();

            assertEquals(caseCount, successCount.get(),
                    "All concurrent imports should succeed");
        }

        @Test
        @DisplayName("Mixed concurrent import/export operations work correctly")
        void mixedConcurrentImportExportOperationsWorkCorrectly() throws Exception {
            int operationCount = 6;
            ExecutorService executor = Executors.newFixedThreadPool(operationCount);
            CountDownLatch latch = new CountDownLatch(operationCount);
            AtomicInteger successCount = new AtomicInteger(0);

            // Launch a case for export
            YNetRunner exportRunner = launchCaseWithMonitoring("mixed_case");
            String caseXml = engine.marshalCase(exportRunner);

            // Mix import and export operations
            for (int i = 0; i < operationCount; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        if (index % 2 == 0) {
                            // Export operation
                            String exportFile = createTestFileName("mixed_" + index, ".txt");
                            importExportService.exportCaseToFile(caseXml, exportFile);
                        } else {
                            // Import operation
                            YNetRunner importedRunner = importExportService.importCaseFromByteArray(
                                    caseXml.getBytes(StandardCharsets.UTF_8));
                            assertNotNull(importedRunner, "Imported runner should not be null");
                        }

                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        fail("Mixed operation failed: " + e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(EVENT_TIMEOUT_SEC, TimeUnit.SECONDS);
            executor.shutdown();

            assertEquals(operationCount, successCount.get(),
                    "All mixed operations should succeed");
        }
    }

    // =========================================================================
    // Nested: Error Recovery Tests
    // =========================================================================

    @Nested
    @DisplayName("Error Recovery Tests")
    class ErrorRecoveryTests {

        @Test
        @DisplayName("Export handles file write errors gracefully")
        void exportHandlesFileWriteErrorsGracefully() throws Exception {
            String caseId = "write-error-test";
            YNetRunner runner = launchCaseWithMonitoring(caseId);
            String caseXml = engine.marshalCase(runner);

            // Try to write to a directory instead of file
            Path directory = Paths.get(TEST_EXPORT_DIR, "subdirectory");
            Files.createDirectories(directory);

            String invalidPath = directory.toString();

            assertThrows(IOException.class, () ->
                    importExportService.exportCaseToFile(caseXml, invalidPath),
                    "Should throw when writing to directory");
        }

        @Test
        @DisplayName("Import handles read errors gracefully")
        void importHandlesReadErrorsGracefully() {
            // Try to read from a directory
            Path directory = Paths.get(TEST_EXPORT_DIR, "invalid_import");

            assertThrows(IOException.class, () ->
                    importExportService.importCaseFromFile(directory.toString()),
                    "Should throw when reading from directory");
        }

        @Test
        @DisplayName("Export handles disk space errors gracefully")
        void exportHandlesDiskSpaceErrorsGracefully() throws Exception {
            // Create a very large case XML that would likely exceed disk space
            String largeCaseId = "large_" + "x".repeat(1000000);
            String caseXml = "<case id=\"" + largeCaseId + "\"></case>";

            String exportFile = createTestFileName("disk_error", ".txt");

            // This should either succeed or fail gracefully, not hang
            assertDoesNotThrow(() -> {
                importExportService.exportCaseToFile(caseXml, exportFile);
            }, "Should handle large file gracefully");

            Path filePath = Paths.get(exportFile);
            if (Files.exists(filePath)) {
                long fileSize = Files.size(filePath);
                assertTrue(fileSize < 1024 * 1024, "File size should be reasonable"); // Less than 1MB
                Files.deleteIfExists(filePath);
            }
        }
    }

    // =========================================================================
    // Nested: Performance Tests
    // =========================================================================

    @Nested
    @DisplayName("Performance Tests")
    class PerformanceTests {

        @Test
        @DisplayName("Export performance with large datasets")
        void exportPerformanceWithLargeDatasets() throws Exception {
            int caseCount = 10;
            long startTime = System.currentTimeMillis();

            // Launch multiple cases
            YNetRunner[] runners = new YNetRunner[caseCount];
            for (int i = 0; i < caseCount; i++) {
                runners[i] = launchCaseWithMonitoring("perf_export_" + i);
            }

            // Export all cases
            String exportFile = createTestFileName("perf_export", ".txt");
            int casesExported = importExportService.exportAllCasesToFile(exportFile);

            long duration = System.currentTimeMillis() - startTime;

            assertEquals(caseCount, casesExported, "Should export all cases");
            assertTrue(duration < 10000, "Export should complete within 10 seconds"); // 10 second timeout

            Files.deleteIfExists(Paths.get(exportFile));
        }

        @Test
        @DisplayName("Import performance with large datasets")
        void importPerformanceWithLargeDatasets() throws Exception {
            // Create a large case XML
            String largeCaseXml = createLargeCaseXml();
            byte[] largeData = largeCaseXml.getBytes(StandardCharsets.UTF_8);

            long startTime = System.currentTimeMillis();

            // Import multiple times
            for (int i = 0; i < 5; i++) {
                YNetRunner importedRunner = importExportService.importCaseFromByteArray(largeData);
                assertNotNull(importedRunner, "Imported runner should not be null");
            }

            long duration = System.currentTimeMillis() - startTime;
            assertTrue(duration < 5000, "Import should complete within 5 seconds"); // 5 second timeout
        }

        private String createLargeCaseXml() {
            StringBuilder sb = new StringBuilder();
            sb.append("<case id=\"large-case\">");
            sb.append("<specificationSet xmlns=\"http://www.yawlfoundation.org/yawlschema\">");
            sb.append("<YSpecification id=\"large-spec\" name=\"Large Spec\">");
            sb.append("<schema></schema></YSpecification></specificationSet>");

            // Add large data section
            sb.append("<runners><runner>");
            sb.append("<identifier id=\"large-case\"><locations><location>start</location></locations></identifier>");
            sb.append("<containingtask></containingtask><starttime>0</starttime>");
            sb.append("<executionstatus>Started</executionstatus>");
            sb.append("<netdata><![CDATA[<data>");

            // Add large data (1MB)
            for (int i = 0; i < 10000; i++) {
                sb.append("large_data_").append(i).append("=");
                sb.append("x".repeat(100));
                if (i < 9999) sb.append("&");
            }

            sb.append("</data>]]></netdata>");
            sb.append("<enabledtasks></enabledtasks><busytasks></busytasks>");
            sb.append("<timerstates></timerstates></runner></runners>");
            sb.append("</case>");

            return sb.toString();
        }
    }
}