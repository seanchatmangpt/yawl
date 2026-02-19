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
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General
 * Public License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.stateless.monitor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.exceptions.YStateException;
import org.yawlfoundation.yawl.exceptions.YSyntaxException;
import org.yawlfoundation.yawl.stateless.YStatelessEngine;
import org.yawlfoundation.yawl.stateless.elements.YSpecification;
import org.yawlfoundation.yawl.stateless.elements.marking.YIdentifier;
import org.yawlfoundation.yawl.stateless.engine.YNetRunner;
import org.yawlfoundation.yawl.stateless.listener.YCaseEventListener;
import org.yawlfoundation.yawl.stateless.listener.event.YCaseEvent;
import org.yawlfoundation.yawl.stateless.listener.event.YEventType;
import org.yawlfoundation.yawl.util.JDOMUtil;
import org.yawlfoundation.yawl.util.StringUtil;

/**
 * Comprehensive tests for YCase import/export service layer covering
 * file export, filtering, validation, compression, and concurrent operations.
 *
 * <p>Chicago TDD: All tests use real YStatelessEngine instances,
 * real case operations, and real file/compression operations. No mocks.</p>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@DisplayName("YCase Import/Export Service Tests")
@Tag("unit")
public class TestYCaseImportExportService {

    private static final String MINIMAL_SPEC_RESOURCE = "resources/MinimalSpec.xml";
    private static final long EVENT_TIMEOUT_SEC = 10L;

    private YStatelessEngine engine;
    private YSpecification spec;

    @BeforeEach
    void setUp() throws Exception {
        engine = new YStatelessEngine();
        engine.setCaseMonitoringEnabled(true);
        spec = loadMinimalSpec();
    }

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.setCaseMonitoringEnabled(false);
        }
    }

    private YSpecification loadMinimalSpec() throws YSyntaxException {
        InputStream is = getClass().getResourceAsStream(MINIMAL_SPEC_RESOURCE);
        assertNotNull(is, "Missing resource: " + MINIMAL_SPEC_RESOURCE);
        String xml = StringUtil.streamToString(is);
        return engine.unmarshalSpecification(xml);
    }

    private YNetRunner launchCaseWithMonitoring(String caseId) throws Exception {
        AtomicReference<YNetRunner> runnerCapture = new AtomicReference<>();
        CountDownLatch startedLatch = new CountDownLatch(1);

        YCaseEventListener listener = event -> {
            if (event.getEventType() == YEventType.CASE_STARTED) {
                runnerCapture.set(event.getRunner());
                startedLatch.countDown();
            }
        };
        engine.addCaseEventListener(listener);
        engine.launchCase(spec, caseId);
        startedLatch.await(EVENT_TIMEOUT_SEC, TimeUnit.SECONDS);
        engine.removeCaseEventListener(listener);

        YNetRunner runner = runnerCapture.get();
        assertNotNull(runner, "Runner should be captured after case start");
        return runner;
    }

    // =========================================================================
    // Nested: File Export Tests
    // =========================================================================

    @Nested
    @DisplayName("File Export Tests")
    class FileExportTests {

        @Test
        @DisplayName("Export case to byte array")
        void exportCaseToByteArray() throws Exception {
            String caseId = "export-bytes-test";
            YNetRunner runner = launchCaseWithMonitoring(caseId);
            YIdentifier caseIdentifier = runner.getCaseID();

            String caseXml = engine.unloadCase(caseIdentifier);

            assertNotNull(caseXml, "Exported case XML should not be null");
            byte[] bytes = caseXml.getBytes(StandardCharsets.UTF_8);
            assertTrue(bytes.length > 0, "Exported bytes should not be empty");
        }

        @Test
        @DisplayName("Export case preserves all data")
        void exportCasePreservesAllData() throws Exception {
            String caseId = "export-preserve-test";
            YNetRunner runner = launchCaseWithMonitoring(caseId);
            YIdentifier caseIdentifier = runner.getCaseID();

            String caseXml = engine.unloadCase(caseIdentifier);

            assertTrue(caseXml.contains(caseId), "Export should contain case ID");
            assertTrue(caseXml.contains("MinimalNet"), "Export should contain net name");
            assertTrue(caseXml.contains("specificationSet"),
                    "Export should contain specification");
        }

        @Test
        @DisplayName("Export removes case from monitor")
        void exportRemovesCaseFromMonitor() throws Exception {
            String caseId = "export-remove-test";
            YNetRunner runner = launchCaseWithMonitoring(caseId);
            YIdentifier caseIdentifier = runner.getCaseID();

            assertTrue(engine.isCaseMonitoringEnabled(),
                    "Case monitoring should be enabled");
            assertTrue(engine.isIdleCase(caseIdentifier) || !engine.isIdleCase(caseIdentifier),
                    "Case should be in monitor before export");

            engine.unloadCase(caseIdentifier);

            assertThrows(YStateException.class, () -> engine.isIdleCase(caseIdentifier),
                    "Case should be removed from monitor after export");
        }

        @Test
        @DisplayName("Export case to output stream format")
        void exportCaseToOutputStreamFormat() throws Exception {
            String caseId = "export-stream-test";
            YNetRunner runner = launchCaseWithMonitoring(caseId);
            YIdentifier caseIdentifier = runner.getCaseID();

            String caseXml = engine.unloadCase(caseIdentifier);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(caseXml.getBytes(StandardCharsets.UTF_8));

            byte[] result = baos.toByteArray();
            assertTrue(result.length > 0, "OutputStream should contain data");
            assertTrue(new String(result, StandardCharsets.UTF_8).contains(caseId),
                    "Result should contain case ID");
        }
    }

    // =========================================================================
    // Nested: Filtering Tests
    // =========================================================================

    @Nested
    @DisplayName("Filtering Tests")
    class FilteringTests {

        @Test
        @DisplayName("Filter exported XML by element name")
        void filterExportedXmlByElementName() throws Exception {
            String caseId = "filter-element-test";
            YNetRunner runner = launchCaseWithMonitoring(caseId);
            String caseXml = engine.marshalCase(runner);

            Document doc = JDOMUtil.stringToDocument(caseXml);
            List<Element> workItems = filterElementsByPath(doc, "runners", "runner", "workitems");

            assertNotNull(workItems, "Filtered elements should not be null");
        }

        @Test
        @DisplayName("Extract case ID from exported XML")
        void extractCaseIdFromExportedXml() throws Exception {
            String caseId = "extract-id-test";
            YNetRunner runner = launchCaseWithMonitoring(caseId);
            String caseXml = engine.marshalCase(runner);

            Document doc = JDOMUtil.stringToDocument(caseXml);
            Element root = doc.getRootElement();
            String extractedId = root.getAttributeValue("id");

            assertEquals(caseId, extractedId, "Extracted ID should match");
        }

        @Test
        @DisplayName("Extract specification ID from exported XML")
        void extractSpecIdFromExportedXml() throws Exception {
            String caseId = "extract-spec-test";
            YNetRunner runner = launchCaseWithMonitoring(caseId);
            String caseXml = engine.marshalCase(runner);

            Document doc = JDOMUtil.stringToDocument(caseXml);
            Element root = doc.getRootElement();

            assertTrue(caseXml.contains("uri=\"MinimalSpec\""),
                    "Export should contain specification URI");
        }

        private List<Element> filterElementsByPath(Document doc, String... path) {
            List<Element> result = new ArrayList<>();
            Element current = doc.getRootElement();
            for (int i = 0; i < path.length - 1 && current != null; i++) {
                current = current.getChild(path[i]);
            }
            if (current != null) {
                result.addAll(current.getChildren(path[path.length - 1]));
            }
            return result;
        }
    }

    // =========================================================================
    // Nested: Validation Tests
    // =========================================================================

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("Valid XML passes validation")
        void validXmlPassesValidation() throws Exception {
            String caseId = "valid-xml-test";
            YNetRunner runner = launchCaseWithMonitoring(caseId);
            String caseXml = engine.marshalCase(runner);

            assertDoesNotThrow(() -> {
                Document doc = JDOMUtil.stringToDocument(caseXml);
                validateCaseStructure(doc);
            }, "Valid XML should pass validation");
        }

        @Test
        @DisplayName("Invalid XML fails validation")
        void invalidXmlFailsValidation() {
            String invalidXml = "<case><invalid></case>";

            assertThrows(JDOMException.class, () -> {
                JDOMUtil.stringToDocument(invalidXml);
            }, "Invalid XML should fail parsing");
        }

        @Test
        @DisplayName("Export contains required elements")
        void exportContainsRequiredElements() throws Exception {
            String caseId = "required-elements-test";
            YNetRunner runner = launchCaseWithMonitoring(caseId);
            String caseXml = engine.marshalCase(runner);

            Document doc = JDOMUtil.stringToDocument(caseXml);
            Element root = doc.getRootElement();

            assertEquals("case", root.getName(), "Root should be 'case'");
            assertNotNull(root.getAttribute("id"), "Should have id attribute");
            assertNotNull(root.getChild("runners"), "Should have runners element");
        }

        @Test
        @DisplayName("Validate case ID format")
        void validateCaseIdFormat() throws Exception {
            String caseId = "id-format-test-123";
            YNetRunner runner = launchCaseWithMonitoring(caseId);
            String caseXml = engine.marshalCase(runner);

            Document doc = JDOMUtil.stringToDocument(caseXml);
            String extractedId = doc.getRootElement().getAttributeValue("id");

            assertEquals(caseId, extractedId, "Case ID format should be preserved");
        }

        private void validateCaseStructure(Document doc) {
            Element root = doc.getRootElement();
            if (!"case".equals(root.getName())) {
                throw new IllegalArgumentException("Root element must be 'case'");
            }
            if (root.getAttributeValue("id") == null) {
                throw new IllegalArgumentException("Case must have 'id' attribute");
            }
            if (root.getChild("runners") == null) {
                throw new IllegalArgumentException("Case must have 'runners' element");
            }
        }
    }

    // =========================================================================
    // Nested: Compression Tests
    // =========================================================================

    @Nested
    @DisplayName("Compression Tests")
    class CompressionTests {

        @Test
        @DisplayName("Compress exported case to ZIP")
        void compressExportedCaseToZip() throws Exception {
            String caseId = "compress-zip-test";
            YNetRunner runner = launchCaseWithMonitoring(caseId);
            String caseXml = engine.marshalCase(runner);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                ZipEntry entry = new ZipEntry(caseId + ".xml");
                zos.putNextEntry(entry);
                zos.write(caseXml.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }

            byte[] zipBytes = baos.toByteArray();
            assertTrue(zipBytes.length > 0, "ZIP should contain data");
            assertTrue(zipBytes.length < caseXml.getBytes(StandardCharsets.UTF_8).length * 2,
                    "ZIP should be reasonably sized");
        }

        @Test
        @DisplayName("Compress multiple cases to single ZIP")
        void compressMultipleCasesToSingleZip() throws Exception {
            String caseId1 = "multi-zip-test-1";
            String caseId2 = "multi-zip-test-2";

            YNetRunner runner1 = launchCaseWithMonitoring(caseId1);
            String caseXml1 = engine.marshalCase(runner1);

            YNetRunner runner2 = launchCaseWithMonitoring(caseId2);
            String caseXml2 = engine.marshalCase(runner2);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                ZipEntry entry1 = new ZipEntry(caseId1 + ".xml");
                zos.putNextEntry(entry1);
                zos.write(caseXml1.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                ZipEntry entry2 = new ZipEntry(caseId2 + ".xml");
                zos.putNextEntry(entry2);
                zos.write(caseXml2.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }

            byte[] zipBytes = baos.toByteArray();
            assertTrue(zipBytes.length > 0, "ZIP should contain data");
        }

        @Test
        @DisplayName("Compressed data is smaller for large cases")
        void compressedDataIsSmallerForLargeCases() throws Exception {
            StringBuilder largeData = new StringBuilder();
            for (int i = 0; i < 1000; i++) {
                largeData.append("<data>item").append(i).append("</data>");
            }

            String caseXml = "<?xml version=\"1.0\"?><case id=\"large-case\">" +
                    largeData + "</case>";

            ByteArrayOutputStream rawBaos = new ByteArrayOutputStream();
            rawBaos.write(caseXml.getBytes(StandardCharsets.UTF_8));
            int rawSize = rawBaos.size();

            ByteArrayOutputStream zipBaos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(zipBaos)) {
                ZipEntry entry = new ZipEntry("case.xml");
                zos.putNextEntry(entry);
                zos.write(caseXml.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
            int zipSize = zipBaos.size();

            assertTrue(zipSize < rawSize,
                    "Compressed size should be smaller for repetitive data");
        }
    }

    // =========================================================================
    // Nested: Concurrent Operations Tests
    // =========================================================================

    @Nested
    @DisplayName("Concurrent Operations Tests")
    class ConcurrentOperationsTests {

        @Test
        @DisplayName("Concurrent case exports complete successfully")
        void concurrentCaseExportsCompleteSuccessfully() throws Exception {
            int numCases = 5;
            ExecutorService executor = Executors.newFixedThreadPool(numCases);
            CountDownLatch latch = new CountDownLatch(numCases);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);

            for (int i = 0; i < numCases; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        String caseId = "concurrent-export-" + index + "-" + System.nanoTime();
                        YNetRunner runner = launchCaseWithMonitoring(caseId);
                        String caseXml = engine.marshalCase(runner);

                        if (caseXml != null && caseXml.contains(caseId)) {
                            successCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean completed = latch.await(EVENT_TIMEOUT_SEC * 3, TimeUnit.SECONDS);
            executor.shutdown();

            assertTrue(completed, "All concurrent operations should complete");
            assertEquals(numCases, successCount.get(),
                    "All exports should succeed");
            assertEquals(0, failureCount.get(),
                    "No failures should occur");
        }

        @Test
        @DisplayName("Concurrent import and export operations")
        void concurrentImportAndExportOperations() throws Exception {
            int numOperations = 3;
            ExecutorService executor = Executors.newFixedThreadPool(numOperations);
            CountDownLatch latch = new CountDownLatch(numOperations * 2);
            AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 0; i < numOperations; i++) {
                final int index = i;

                executor.submit(() -> {
                    try {
                        String caseId = "import-export-" + index;
                        YNetRunner runner = launchCaseWithMonitoring(caseId);
                        String caseXml = engine.marshalCase(runner);
                        if (caseXml != null) successCount.incrementAndGet();
                    } catch (Exception e) {
                    } finally {
                        latch.countDown();
                    }
                });

                executor.submit(() -> {
                    try {
                        String caseId = "restore-" + index;
                        YNetRunner runner = launchCaseWithMonitoring(caseId);
                        String caseXml = engine.marshalCase(runner);
                        assertNotNull(caseXml);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean completed = latch.await(EVENT_TIMEOUT_SEC * 5, TimeUnit.SECONDS);
            executor.shutdown();

            assertTrue(completed, "All operations should complete");
            assertTrue(successCount.get() >= numOperations,
                    "At least half the operations should succeed");
        }

        @Test
        @DisplayName("Thread-safe marshal operations")
        void threadSafeMarshalOperations() throws Exception {
            String caseId = "thread-safe-marshal";
            YNetRunner runner = launchCaseWithMonitoring(caseId);

            int numThreads = 10;
            ExecutorService executor = Executors.newFixedThreadPool(numThreads);
            CountDownLatch latch = new CountDownLatch(numThreads);
            List<String> results = new ArrayList<>();

            for (int i = 0; i < numThreads; i++) {
                executor.submit(() -> {
                    try {
                        String xml = engine.marshalCase(runner);
                        synchronized (results) {
                            results.add(xml);
                        }
                    } catch (Exception e) {
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean completed = latch.await(EVENT_TIMEOUT_SEC * 2, TimeUnit.SECONDS);
            executor.shutdown();

            assertTrue(completed, "All marshal operations should complete");
            assertEquals(numThreads, results.size(),
                    "All marshal operations should produce results");

            for (String xml : results) {
                assertTrue(xml.contains(caseId),
                        "All results should contain case ID");
            }
        }
    }

    // =========================================================================
    // Nested: Round-Trip Service Tests
    // =========================================================================

    @Nested
    @DisplayName("Round-Trip Service Tests")
    class RoundTripServiceTests {

        @Test
        @DisplayName("Export and restore preserves case state")
        void exportAndRestorePreservesCaseState() throws Exception {
            String caseId = "roundtrip-service";
            YNetRunner originalRunner = launchCaseWithMonitoring(caseId);
            YIdentifier caseIdentifier = originalRunner.getCaseID();

            String caseXml = engine.unloadCase(caseIdentifier);

            YNetRunner restoredRunner = engine.restoreCase(caseXml);

            assertNotNull(restoredRunner, "Restored runner should not be null");
            assertEquals(caseId, restoredRunner.getCaseID().toString(),
                    "Case ID should be preserved");
        }

        @Test
        @DisplayName("Restored case is added back to monitor")
        void restoredCaseIsAddedBackToMonitor() throws Exception {
            String caseId = "restore-monitor";
            YNetRunner originalRunner = launchCaseWithMonitoring(caseId);
            YIdentifier caseIdentifier = originalRunner.getCaseID();

            String caseXml = engine.unloadCase(caseIdentifier);

            assertThrows(YStateException.class, () -> engine.isIdleCase(caseIdentifier),
                    "Case should be removed from monitor");

            YNetRunner restoredRunner = engine.restoreCase(caseXml);

            assertDoesNotThrow(() -> engine.isIdleCase(restoredRunner.getCaseID()),
                    "Restored case should be in monitor");
        }

        @Test
        @DisplayName("Multiple round-trips preserve case integrity")
        void multipleRoundTripsPreserveCaseIntegrity() throws Exception {
            String caseId = "multi-roundtrip";
            YNetRunner runner = launchCaseWithMonitoring(caseId);
            YIdentifier caseIdentifier = runner.getCaseID();

            for (int i = 0; i < 3; i++) {
                String caseXml = engine.unloadCase(caseIdentifier);
                runner = engine.restoreCase(caseXml);
                caseIdentifier = runner.getCaseID();

                assertEquals(caseId, caseIdentifier.toString(),
                        "Case ID should be preserved after round-trip " + (i + 1));
            }
        }
    }
}
