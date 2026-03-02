/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.validation;

import org.yawlfoundation.yawl.validation.shacl.ShaclValidationChecker;
import org.apache.jena.shacl.ValidationReport;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Hyper-standards validator that orchestrates all guard checkers.
 *
 * Integrates multiple validation strategies into a unified pipeline.
 */
public class HyperStandardsValidator {

    private final List<GuardChecker> checkers;
    private GuardReceipt currentReceipt;

    public HyperStandardsValidator() {
        this.checkers = initializeCheckers();
    }

    /**
     * Initializes all guard checkers.
     */
    private List<GuardChecker> initializeCheckers() {
        List<GuardChecker> checkers = new ArrayList<>();

        // Add SHACL validation checker
        checkers.add(new ShaclValidationChecker());

        // Additional checkers can be added here
        // checkers.add(new HTodoChecker());
        // checkers.add(new HMockChecker());
        // checkers.add(new HEmptyChecker());

        return checkers;
    }

    /**
     * Validates a single file against all guard checkers.
     *
     * @param filePath Path to the file to validate
     * @return GuardReceipt containing all validation results
     */
    public GuardReceipt validateFile(Path filePath) {
        currentReceipt = new GuardReceipt();
        currentReceipt.setPhase("hyper-standards");
        currentReceipt.setTimestamp(Instant.now());
        currentReceipt.setFilesScanned(1);

        List<GuardViolation> allViolations = new ArrayList<>();

        for (GuardChecker checker : checkers) {
            try {
                List<GuardViolation> violations = checker.check(filePath);
                for (GuardViolation violation : violations) {
                    violation.setFile(filePath.toString());
                    allViolations.add(violation);
                }
            } catch (Exception e) {
                GuardViolation errorViolation = new GuardViolation(
                    checker.patternName() + "_ERROR",
                    "FAIL",
                    0,
                    "Validation failed: " + e.getMessage()
                );
                errorViolation.setFile(filePath.toString());
                errorViolation.setFixGuidance("Check file format and content");
                allViolations.add(errorViolation);
            }
        }

        currentReceipt.setViolations(allViolations);
        currentReceipt.setStatus(allViolations.isEmpty() ? "GREEN" : "RED");

        if (!allViolations.isEmpty()) {
            currentReceipt.setErrorMessage(
                "Validation failed with " + allViolations.size() + " violations"
            );
        }

        return currentReceipt;
    }

    /**
     * Validates all files in a directory against all guard checkers.
     *
     * @param dirPath Directory containing files to validate
     * @return GuardReceipt containing all validation results
     */
    public GuardReceipt validateDirectory(Path dirPath) {
        currentReceipt = new GuardReceipt();
        currentReceipt.setPhase("hyper-standards");
        currentReceipt.setTimestamp(Instant.now());

        List<GuardViolation> allViolations = new ArrayList<>();
        int filesScanned = 0;

        try {
            java.nio.file.Files.walk(dirPath)
                .filter(Files::isRegularFile)
                .filter(p -> isValidFileExtension(p))
                .forEach(filePath -> {
                    filesScanned++;
                    List<GuardViolation> fileViolations = validateFile(filePath).getViolations();
                    allViolations.addAll(fileViolations);
                });

            currentReceipt.setFilesScanned(filesScanned);
            currentReceipt.setViolations(allViolations);
            currentReceipt.setStatus(allViolations.isEmpty() ? "GREEN" : "RED");

            if (!allViolations.isEmpty()) {
                currentReceipt.setErrorMessage(
                    "Validation failed with " + allViolations.size() + " violations in " + filesScanned + " files"
                );
            }
        } catch (IOException e) {
            currentReceipt.setStatus("RED");
            currentReceipt.setErrorMessage("Failed to scan directory: " + e.getMessage());
        }

        return currentReceipt;
    }

    /**
     * Validates only specific file extensions.
     */
    private boolean isValidFileExtension(Path filePath) {
        String fileName = filePath.getFileName().toString().toLowerCase();
        return fileName.endsWith(".java") ||
               fileName.endsWith(".yawl") ||
               fileName.endsWith(".ttl") ||
               fileName.endsWith(".xml");
    }

    /**
     * Gets the current validation receipt.
     */
    public GuardReceipt getCurrentReceipt() {
        return currentReceipt;
    }

    /**
     * Gets a checker by name.
     */
    public GuardChecker getCheckerByName(String patternName) {
        return checkers.stream()
            .filter(checker -> checker.patternName().equals(patternName))
            .findFirst()
            .orElse(null);
    }

    /**
     * Adds a custom checker to the validation pipeline.
     */
    public void addChecker(GuardChecker checker) {
        checkers.add(checker);
    }

    /**
     * Gets the number of registered checkers.
     */
    public int getCheckerCount() {
        return checkers.size();
    }

    /**
     * Gets a summary of all registered checkers.
     */
    public String getCheckersSummary() {
        StringBuilder sb = new StringBuilder("Registered Checkers:\n");
        checkers.forEach(checker -> {
            sb.append("  - ")
              .append(checker.patternName())
              .append(" (")
              .append(checker.severity())
              .append(")\n");
        });
        return sb.toString();
    }
}