/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.stateless.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.exceptions.YStateException;
import org.yawlfoundation.yawl.stateless.elements.marking.YIdentifier;
import org.yawlfoundation.yawl.stateless.engine.YNetRunner;
import org.yawlfoundation.yawl.util.JDOMUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Exports and imports case data for backup, recovery, and migration.
 * Supports filtering, compression, and validation of exported data.
 *
 * @author YAWL Development Team
 * @since 5.2
 */
public class YCaseImportExportService {

    private static final Logger logger = LoggerFactory.getLogger(YCaseImportExportService.class);
    private static final String EXPORT_VERSION = "5.2";

    private final YCaseMonitor _caseMonitor;
    private final YCaseExporter _exporter;
    private final YCaseImporter _importer;

    /**
     * Creates a new import/export service.
     * @param caseMonitor the case monitor containing cases to export
     */
    public YCaseImportExportService(YCaseMonitor caseMonitor) {
        if (caseMonitor == null) {
            throw new IllegalArgumentException("Case monitor cannot be null");
        }
        _caseMonitor = caseMonitor;
        _exporter = new YCaseExporter();
        _importer = new YCaseImporter();
    }

    /**
     * Exports all monitored cases to a file.
     * @param filename the output file path
     * @return number of cases exported
     * @throws IOException if export fails
     */
    public int exportAllCasesToFile(String filename) throws IOException {
        List<YCase> cases = _caseMonitor.getAllCases();

        logger.info("Exporting {} cases to {}", cases.size(), filename);

        try (BufferedWriter writer = createWriter(filename)) {
            writer.write(createExportHeader());
            writer.newLine();

            for (YCase yCase : cases) {
                String caseXML = exportCase(yCase);
                if (caseXML != null) {
                    writer.write(caseXML);
                    writer.newLine();
                }
            }
        }

        logger.info("Successfully exported {} cases", cases.size());
        return cases.size();
    }

    /**
     * Exports a single case to XML string.
     * @param yCase the case to export
     * @return XML representation of the case, or null if export fails
     */
    public String exportCase(YCase yCase) {
        try {
            YNetRunner runner = yCase.getRunner();
            if (runner == null) {
                logger.warn("Cannot export case with null runner");
                return null;
            }
            return _exporter.marshal(runner);
        }
        catch (Exception e) {
            logger.error("Failed to export case", e);
            return null;
        }
    }

    /**
     * Exports cases matching the specified filter criteria.
     * @param specID the specification ID to filter by (null for all)
     * @param minStartTime minimum case start time (0 for no limit)
     * @param maxStartTime maximum case start time (Long.MAX_VALUE for no limit)
     * @param filename the output file path
     * @return number of cases exported
     * @throws IOException if export fails
     */
    public int exportFilteredCases(String specID, long minStartTime, long maxStartTime,
                                    String filename) throws IOException {
        List<YCase> allCases = _caseMonitor.getAllCases();

        List<YCase> filteredCases = allCases.stream()
            .filter(c -> matchesFilter(c, specID, minStartTime, maxStartTime))
            .toList();

        logger.info("Exporting {} filtered cases (out of {} total) to {}",
                   filteredCases.size(), allCases.size(), filename);

        try (BufferedWriter writer = createWriter(filename)) {
            writer.write(createExportHeader());
            writer.newLine();

            for (YCase yCase : filteredCases) {
                String caseXML = exportCase(yCase);
                if (caseXML != null) {
                    writer.write(caseXML);
                    writer.newLine();
                }
            }
        }

        logger.info("Successfully exported {} filtered cases", filteredCases.size());
        return filteredCases.size();
    }

    /**
     * Exports a specific case to a file.
     * @param caseID the case identifier
     * @param filename the output file path
     * @throws IOException if export fails
     * @throws YStateException if case not found
     */
    public void exportCaseToFile(YIdentifier caseID, String filename)
            throws IOException, YStateException {
        if (!_caseMonitor.hasCase(caseID)) {
            throw new YStateException("Case not found: " + caseID);
        }

        List<YCase> allCases = _caseMonitor.getAllCases();
        YCase targetCase = allCases.stream()
            .filter(c -> {
                YNetRunner runner = c.getRunner();
                return runner != null && runner.getCaseID().equals(caseID);
            })
            .findFirst()
            .orElseThrow(() -> new YStateException("Case not found: " + caseID));

        String caseXML = exportCase(targetCase);
        if (caseXML == null) {
            throw new IOException("Failed to export case: " + caseID);
        }

        try (BufferedWriter writer = createWriter(filename)) {
            writer.write(createExportHeader());
            writer.newLine();
            writer.write(caseXML);
        }

        logger.info("Exported case {} to {}", caseID, filename);
    }

    /**
     * Validates the integrity of an exported file.
     * @param filename the file to validate
     * @return validation result
     * @throws IOException if validation fails
     */
    public ValidationResult validateExportedFile(String filename) throws IOException {
        ValidationResult result = new ValidationResult();
        result.filename = filename;

        try (BufferedReader reader = createReader(filename)) {
            String header = reader.readLine();
            if (header == null || !header.contains("YAWL Case Export")) {
                result.isValid = false;
                result.errors.add("Invalid or missing export header");
                return result;
            }

            String line;
            int lineNumber = 2;
            while ((line = reader.readLine()) != null) {
                if (!line.strip().isEmpty()) {
                    if (!isValidXML(line)) {
                        result.isValid = false;
                        result.errors.add("Invalid XML at line " + lineNumber);
                    }
                    else {
                        result.validCaseCount++;
                    }
                }
                lineNumber++;
            }
        }

        result.isValid = result.errors.isEmpty();
        logger.info("Validation result for {}: valid={}, cases={}, errors={}",
                   filename, result.isValid, result.validCaseCount, result.errors.size());

        return result;
    }

    /**
     * Creates an export header with metadata.
     * @return formatted header string
     */
    private String createExportHeader() {
        return "<!-- YAWL Case Export | Version: %s | Date: %s -->".formatted(
                           EXPORT_VERSION, new Date());
    }

    /**
     * Creates a buffered writer for the given filename.
     * Supports .gz compression if filename ends with .gz.
     * @param filename the output file path
     * @return buffered writer
     * @throws IOException if writer creation fails
     */
    private BufferedWriter createWriter(String filename) throws IOException {
        Path path = Paths.get(filename);
        Files.createDirectories(path.getParent());

        OutputStream os = Files.newOutputStream(path);
        if (filename.endsWith(".gz")) {
            os = new GZIPOutputStream(os);
        }

        return new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
    }

    /**
     * Creates a buffered reader for the given filename.
     * Supports .gz decompression if filename ends with .gz.
     * @param filename the input file path
     * @return buffered reader
     * @throws IOException if reader creation fails
     */
    private BufferedReader createReader(String filename) throws IOException {
        InputStream is = Files.newInputStream(Paths.get(filename));
        if (filename.endsWith(".gz")) {
            is = new GZIPInputStream(is);
        }

        return new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
    }

    /**
     * Checks if a case matches the filter criteria.
     * @param yCase the case to check
     * @param specID specification ID filter (null for all)
     * @param minStartTime minimum start time
     * @param maxStartTime maximum start time
     * @return true if case matches filter
     */
    private boolean matchesFilter(YCase yCase, String specID, long minStartTime,
                                   long maxStartTime) {
        YNetRunner runner = yCase.getRunner();
        if (runner == null) {
            return false;
        }

        if (specID != null && !runner.getSpecificationID().equals(specID)) {
            return false;
        }

        long startTime = runner.getStartTime();
        return startTime >= minStartTime && startTime <= maxStartTime;
    }

    /**
     * Validates XML structure.
     * @param xml the XML string to validate
     * @return true if valid XML
     */
    private boolean isValidXML(String xml) {
        try {
            JDOMUtil.stringToDocument(xml);
            return true;
        }
        catch (Exception e) {
            return false;
        }
    }

    /**
     * Result of export file validation.
     */
    public static class ValidationResult {
        public String filename;
        public boolean isValid;
        public int validCaseCount;
        public List<String> errors = new ArrayList<>();

        @Override
        public String toString() {
            return "ValidationResult[file=%s, valid=%s, cases=%d, errors=%d]".formatted(
                               filename, isValid, validCaseCount, errors.size());
        }
    }
}
