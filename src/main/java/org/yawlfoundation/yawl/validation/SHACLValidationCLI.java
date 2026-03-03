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

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Command Line Interface for SHACL validation.
 *
 * Provides a simple way to run SHACL validation from the command line.
 */
public class SHACLValidationCLI {

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }

        try {
            if (args[0].equals("--help") || args[0].equals("-h")) {
                printUsage();
                System.exit(0);
            }

            Path targetPath = Paths.get(args[0]);
            HyperStandardsValidator validator = new HyperStandardsValidator();
            GuardReceipt receipt = targetPath.toFile().isDirectory()
                ? validator.validateDirectory(targetPath)
                : validator.validateFile(targetPath);

            // Print results
            System.out.println("=== SHACL Validation Results ===");
            System.out.println("Phase: " + receipt.getPhase());
            System.out.println("Timestamp: " + receipt.getTimestamp());
            System.out.println("Status: " + receipt.getStatus());
            System.out.println("Files Scanned: " + receipt.getFilesScanned());

            if (receipt.getErrorMessage() != null) {
                System.out.println("Error: " + receipt.getErrorMessage());
            }

            System.out.println("\n" + receipt.getSummary().formatSummary());

            // Print detailed violations if any
            if (!receipt.getViolations().isEmpty()) {
                System.out.println("\n=== Violations ===");
                receipt.getViolations().forEach(violation -> {
                    System.out.println("\nPattern: " + violation.getPattern());
                    System.out.println("File: " + violation.getFile());
                    System.out.println("Line: " + violation.getLine());
                    System.out.println("Severity: " + violation.getSeverity());
                    System.out.println("Message: " + violation.getContent());
                    System.out.println("Fix: " + violation.getFixGuidance());
                });
            }

            // Print registered checkers
            System.out.println("\n" + validator.getCheckersSummary());

            // Exit with appropriate code
            System.exit(receipt.getStatus().equals("GREEN") ? 0 : 1);

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(2);
        }
    }

    private static void printUsage() {
        System.out.println("SHACL Validation CLI for YAWL");
        System.out.println("==============================");
        System.out.println();
        System.out.println("Usage: java org.yawlfoundation.yawl.validation.SHACLValidationCLI <path>");
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("  <path>         Path to a file or directory to validate");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -h, --help     Show this help message");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java org.yawlfoundation.yawl.validation.SHACLValidationCLI workflow.yawl");
        System.out.println("  java org.yawlfoundation.yawl.validation.SHACLValidationCLI ./specs/");
        System.out.println();
        System.out.println("Exit Codes:");
        System.out.println("  0 - Validation passed (GREEN)");
        System.out.println("  1 - Validation failed (RED)");
        System.out.println("  2 - Error occurred");
        System.out.println();
        System.out.println("Supported file formats:");
        System.out.println("  .yawl - YAWL workflow specification");
        System.out.println("  .ttl  - Turtle/RDF format");
        System.out.println("  .xml  - XML format");
        System.out.println("  .java - Java source files");
    }
}