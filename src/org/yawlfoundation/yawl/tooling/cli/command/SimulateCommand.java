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

import org.yawlfoundation.yawl.tooling.cli.YawlCliCommand;
import org.yawlfoundation.yawl.tooling.simulation.WorkflowSimulator;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * CLI subcommand: {@code yawl simulate <spec-file> [options]}
 *
 * Runs an automated step-by-step token-firing simulation of a YAWL specification
 * using the stateless engine. All enabled work items are completed in order,
 * and the state trace is printed to stdout.
 *
 * Options:
 *   --max-steps <n>     Stop simulation after n token firings (default: 1000)
 *   --trace             Print full token marking after each step
 *   --data <json>       Initial case data as inline JSON (e.g. '{"amount":100}')
 *
 * Exit codes:
 *   0 - Simulation completed (output condition reached)
 *   1 - Simulation error
 *   2 - I/O or parse error
 *   3 - Max steps exceeded without reaching output condition
 *
 * @author YAWL Development Team
 * @since 6.0.0
 */
public class SimulateCommand extends YawlCliCommand {

    private static final int DEFAULT_MAX_STEPS = 1000;

    public SimulateCommand(PrintStream out, PrintStream err) {
        super(out, err);
    }

    @Override
    public String name() { return "simulate"; }

    @Override
    public String synopsis() { return "Run a step-by-step token-firing simulation via the stateless engine"; }

    @Override
    public int execute(String[] args) {
        if (isHelpRequest(args)) {
            printHelp();
            return 0;
        }
        if (args.length == 0) {
            return fail("No specification file provided. Run 'yawl simulate --help'.");
        }

        String filePath = null;
        int maxSteps    = DEFAULT_MAX_STEPS;
        boolean trace   = false;
        String initData = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--max-steps" -> maxSteps = parseIntArg(args, ++i, "--max-steps");
                case "--trace"     -> trace = true;
                case "--data"      -> initData = requireArg(args, ++i, "--data");
                default -> {
                    if (args[i].startsWith("--")) {
                        return fail("Unknown option: " + args[i]);
                    }
                    if (filePath != null) {
                        return fail("Only one specification file may be provided.");
                    }
                    filePath = args[i];
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

        out.println("[simulate] Specification: " + specFile.getAbsolutePath());
        out.println("[simulate] Max steps    : " + maxSteps);

        WorkflowSimulator simulator = new WorkflowSimulator(out, err);
        WorkflowSimulator.SimulationResult result;
        try {
            result = simulator.simulate(specXml, maxSteps, trace, initData);
        } catch (Exception e) {
            return fail("Simulation error: " + e.getMessage());
        }

        out.println();
        out.println("[simulate] ---- Simulation Summary ----");
        out.printf("[simulate] Steps executed   : %d%n", result.stepsExecuted());
        out.printf("[simulate] Tasks completed  : %d%n", result.tasksCompleted());
        out.printf("[simulate] Cases completed  : %d%n", result.casesCompleted());

        return switch (result.outcome()) {
            case COMPLETED    -> { out.println("[simulate] Outcome: COMPLETED"); yield 0; }
            case MAX_EXCEEDED -> { out.println("[simulate] Outcome: MAX STEPS EXCEEDED"); yield 3; }
            case ERROR        -> { err.println("[simulate] Outcome: ERROR - " + result.errorMessage()); yield 1; }
        };
    }

    private String requireArg(String[] args, int index, String flag) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Option " + flag + " requires a value.");
        }
        return args[index];
    }

    private int parseIntArg(String[] args, int index, String flag) {
        String val = requireArg(args, index, flag);
        try {
            int n = Integer.parseInt(val);
            if (n <= 0) {
                throw new IllegalArgumentException(flag + " must be a positive integer.");
            }
            return n;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(flag + " must be an integer, got: " + val);
        }
    }

    @Override
    protected void printHelp() {
        out.println("Usage: yawl simulate <spec-file> [options]");
        out.println();
        out.println("Run a token-firing simulation of a YAWL specification.");
        out.println();
        out.println("Arguments:");
        out.println("  <spec-file>          Path to the .xml or .yawl specification file");
        out.println();
        out.println("Options:");
        out.println("  --max-steps <n>      Maximum token firings before abort (default: " + DEFAULT_MAX_STEPS + ")");
        out.println("  --trace              Print full token marking after each step");
        out.println("  --data <json>        Initial case data as JSON string");
        out.println("  -h, --help           Show this help message");
        out.println();
        out.println("Exit codes:");
        out.println("  0  Simulation completed (output condition reached)");
        out.println("  1  Simulation error");
        out.println("  2  I/O or parse error");
        out.println("  3  Max steps exceeded without reaching output condition");
    }
}
