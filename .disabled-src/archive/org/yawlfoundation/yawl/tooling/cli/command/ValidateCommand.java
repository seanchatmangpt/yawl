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

package org.yawlfoundation.yawl.tooling.cli.command;

import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.YSpecificationValidator;
import org.yawlfoundation.yawl.elements.YSpecificationValidator.ValidationError;
import org.yawlfoundation.yawl.schema.SchemaHandler;
import org.yawlfoundation.yawl.schema.YSchemaVersion;
import org.yawlfoundation.yawl.tooling.cli.YawlCliCommand;
import org.yawlfoundation.yawl.unmarshal.YMarshal;
import org.yawlfoundation.yawl.util.YVerificationHandler;
import org.yawlfoundation.yawl.util.YVerificationMessage;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * CLI subcommand: {@code yawl validate <spec-file> [--schema-only] [--strict]}
 *
 * Performs two levels of validation:
 * <ol>
 *   <li>XSD schema validation against {@code YAWL_Schema4.0.xsd} via {@link SchemaHandler}</li>
 *   <li>Semantic structural validation via {@link YSpecificationValidator}
 *       (Petri-net soundness, split/join consistency, data-type correctness)</li>
 * </ol>
 *
 * Options:
 *   --schema-only   Run only XSD validation; skip semantic checks.
 *   --strict        Treat warnings as errors.
 *
 * Exit codes:
 *   0 - valid
 *   1 - invalid (errors reported to stderr)
 *   2 - I/O or parse error
 *
 * @author YAWL Development Team
 * @since 6.0.0
 */
public class ValidateCommand extends YawlCliCommand {

    public ValidateCommand(PrintStream out, PrintStream err) {
        super(out, err);
    }

    @Override
    public String name() { return "validate"; }

    @Override
    public String synopsis() { return "Validate a specification against YAWL_Schema4.0.xsd and semantic rules"; }

    @Override
    public int execute(String[] args) {
        if (isHelpRequest(args)) {
            printHelp();
            return 0;
        }
        if (args.length == 0) {
            return fail("No specification file provided. Run 'yawl validate --help'.");
        }

        String filePath = null;
        boolean schemaOnly = false;
        boolean strict = false;

        for (String arg : args) {
            switch (arg) {
                case "--schema-only" -> schemaOnly = true;
                case "--strict"      -> strict = true;
                default -> {
                    if (arg.startsWith("--")) {
                        return fail("Unknown option: " + arg);
                    }
                    if (filePath != null) {
                        return fail("Only one specification file may be provided at a time.");
                    }
                    filePath = arg;
                }
            }
        }

        if (filePath == null) {
            return fail("No specification file provided.");
        }

        File specFile = new File(filePath);
        if (!specFile.exists() || !specFile.isFile()) {
            return fail("File not found: " + filePath);
        }

        // Phase 1: XSD schema validation
        out.println("[validate] XSD schema validation: " + specFile.getAbsolutePath());
        String specXml;
        try {
            specXml = Files.readString(specFile.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return fail("Cannot read file: " + e.getMessage());
        }

        SchemaHandler schemaHandler = new SchemaHandler(YSchemaVersion.defaultVersion().getSchemaURL());
        boolean schemaValid = schemaHandler.compileAndValidate(specXml);
        if (!schemaValid) {
            for (String schemaError : schemaHandler.getErrorMessages()) {
                err.println("[XSD ERROR] " + schemaError);
            }
            err.println("[validate] XSD validation FAILED");
            return 1;
        }
        out.println("[validate] XSD validation PASSED");

        if (schemaOnly) {
            out.println("[validate] Result: VALID (schema-only mode)");
            return 0;
        }

        // Phase 2: Semantic validation
        out.println("[validate] Semantic validation ...");
        List<YSpecification> specs;
        try {
            specs = YMarshal.unmarshalSpecifications(specXml, false);
        } catch (Exception e) {
            return fail("Parse error: " + e.getMessage());
        }

        if (specs.isEmpty()) {
            return fail("No specifications found in file.");
        }

        boolean allValid = true;
        for (YSpecification spec : specs) {
            // Use YVerificationHandler for full structural verification
            YVerificationHandler vh = new YVerificationHandler();
            spec.verify(vh);

            // Additionally run the YSpecificationValidator for structural checks
            YSpecificationValidator validator = new YSpecificationValidator(spec);
            boolean semValid = validator.validate();
            List<ValidationError> semErrors = validator.getErrors();

            if (!semValid) {
                allValid = false;
                for (ValidationError ve : semErrors) {
                    err.printf("[SEMANTIC ERROR] [%s] %s%n", ve.getElementID(), ve.getMessage());
                }
            }

            // Warnings from the verification handler
            for (YVerificationMessage vw : vh.getWarnings()) {
                if (strict) {
                    allValid = false;
                    err.printf("[WARNING as ERROR] %s%n", vw.getMessage());
                } else {
                    out.printf("[WARN] %s%n", vw.getMessage());
                }
            }
            for (YVerificationMessage ve : vh.getErrors()) {
                allValid = false;
                err.printf("[VERIFY ERROR] %s%n", ve.getMessage());
            }

            String status = (semValid && !vh.hasErrors()) ? "VALID" : "INVALID";
            out.printf("[validate] Specification '%s': %s (%d semantic error(s), %d verify error(s), %d warning(s))%n",
                    spec.getURI(), status, semErrors.size(), vh.getErrors().size(), vh.getWarnings().size());
        }

        if (allValid) {
            out.println("[validate] Result: VALID");
            return 0;
        } else {
            out.println("[validate] Result: INVALID");
            return 1;
        }
    }

    @Override
    protected void printHelp() {
        out.println("Usage: yawl validate <spec-file> [options]");
        out.println();
        out.println("Validate a YAWL specification XML file.");
        out.println();
        out.println("Arguments:");
        out.println("  <spec-file>      Path to the .xml or .yawl specification file");
        out.println();
        out.println("Options:");
        out.println("  --schema-only    Run only XSD schema validation (skip semantic checks)");
        out.println("  --strict         Treat warnings as errors");
        out.println("  -h, --help       Show this help message");
        out.println();
        out.println("Exit codes:");
        out.println("  0  Specification is valid");
        out.println("  1  Specification has validation errors");
        out.println("  2  I/O or parse failure");
    }
}
