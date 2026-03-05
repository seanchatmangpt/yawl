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
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YDecomposition;
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
 * CLI subcommand: {@code yawl compile <spec-file> [--verbose]}
 *
 * Parses a YAWL specification and performs full structural compilation:
 * <ul>
 *   <li>XML parsing via {@link YMarshal}</li>
 *   <li>Full verification via {@link YSpecification#verify(YVerificationHandler)}</li>
 *   <li>Reports net element counts, decomposition counts, and any issues</li>
 * </ul>
 *
 * Exit codes:
 *   0 - Compiled successfully
 *   1 - Compilation errors found
 *   2 - I/O or parse error
 *
 * @author YAWL Development Team
 * @since 6.0.0
 */
public class CompileCommand extends YawlCliCommand {

    public CompileCommand(PrintStream out, PrintStream err) {
        super(out, err);
    }

    @Override
    public String name() { return "compile"; }

    @Override
    public String synopsis() { return "Parse and structurally verify a specification (full Petri-net check)"; }

    @Override
    public int execute(String[] args) {
        if (isHelpRequest(args)) {
            printHelp();
            return 0;
        }
        if (args.length == 0) {
            return fail("No specification file provided. Run 'yawl compile --help'.");
        }

        String filePath = null;
        boolean verbose = false;

        for (String arg : args) {
            switch (arg) {
                case "--verbose", "-v" -> verbose = true;
                default -> {
                    if (arg.startsWith("--")) {
                        return fail("Unknown option: " + arg);
                    }
                    if (filePath != null) {
                        return fail("Only one specification file may be provided.");
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

        String specXml;
        try {
            specXml = Files.readString(specFile.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return fail("Cannot read file: " + e.getMessage());
        }

        out.println("[compile] Parsing: " + specFile.getAbsolutePath());

        List<YSpecification> specs;
        try {
            specs = YMarshal.unmarshalSpecifications(specXml, false);
        } catch (Exception e) {
            return fail("Parse error: " + e.getMessage());
        }

        if (specs.isEmpty()) {
            return fail("No specifications found in file.");
        }

        boolean allPassed = true;

        for (YSpecification spec : specs) {
            out.println("[compile] Specification URI: " + spec.getURI());
            out.println("[compile] Schema version  : " + spec.getSchemaVersion());

            YVerificationHandler verificationHandler = new YVerificationHandler();
            spec.verify(verificationHandler);

            List<YVerificationMessage> errors   = verificationHandler.getErrors();
            List<YVerificationMessage> warnings = verificationHandler.getWarnings();

            if (verbose) {
                // Emit decomposition summary
                for (YDecomposition decomp : spec.getDecompositions()) {
                    out.printf("[compile]   Decomposition: %-30s type=%s%n",
                            decomp.getID(),
                            decomp.getClass().getSimpleName());
                    if (decomp instanceof YNet net) {
                        out.printf("[compile]     Net elements  : %d%n",
                                net.getNetElements().size());
                        out.printf("[compile]     Local variables: %d%n",
                                net.getLocalVariables().size());
                    }
                }
            }

            for (YVerificationMessage warning : warnings) {
                out.println("[WARN] " + warning.getMessage());
            }
            for (YVerificationMessage error : errors) {
                err.println("[ERROR] " + error.getMessage());
                allPassed = false;
            }

            String status = errors.isEmpty() ? "OK" : "FAILED";
            out.printf("[compile] Specification '%s': %s (%d error(s), %d warning(s))%n",
                    spec.getURI(), status, errors.size(), warnings.size());
        }

        if (allPassed) {
            out.println("[compile] BUILD SUCCESS");
            return 0;
        } else {
            out.println("[compile] BUILD FAILED");
            return 1;
        }
    }

    @Override
    protected void printHelp() {
        out.println("Usage: yawl compile <spec-file> [options]");
        out.println();
        out.println("Parse and structurally verify a YAWL specification.");
        out.println();
        out.println("Arguments:");
        out.println("  <spec-file>      Path to the .xml or .yawl specification file");
        out.println();
        out.println("Options:");
        out.println("  -v, --verbose    Print decomposition details and element counts");
        out.println("  -h, --help       Show this help message");
        out.println();
        out.println("Exit codes:");
        out.println("  0  Compiled successfully");
        out.println("  1  Compilation errors found");
        out.println("  2  I/O or parse failure");
    }
}
