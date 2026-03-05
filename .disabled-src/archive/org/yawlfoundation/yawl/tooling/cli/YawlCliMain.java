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

package org.yawlfoundation.yawl.tooling.cli;

import org.yawlfoundation.yawl.tooling.cli.command.CompileCommand;
import org.yawlfoundation.yawl.tooling.cli.command.DeployCommand;
import org.yawlfoundation.yawl.tooling.cli.command.SimulateCommand;
import org.yawlfoundation.yawl.tooling.cli.command.TemplateCommand;
import org.yawlfoundation.yawl.tooling.cli.command.ValidateCommand;
import org.yawlfoundation.yawl.tooling.docgen.WorkflowDocumentationGenerator;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Entry point for the YAWL v6.0.0 command-line interface.
 *
 * Provides the following subcommands:
 *   validate   - Validate a .yawl / .xml specification against YAWL_Schema4.0.xsd
 *   compile    - Parse and structurally verify a specification (full Petri-net check)
 *   test       - Execute specification unit tests via the stateless engine
 *   deploy     - Upload a specification to a running engine via Interface A
 *   template   - Generate a new specification from a named template
 *   simulate   - Run a step-by-step token-firing simulation
 *   docs       - Generate Markdown or HTML documentation for a specification
 *
 * Usage: yawl &lt;subcommand&gt; [options]
 *
 * @author YAWL Development Team
 * @since 6.0.0
 */
public class YawlCliMain {

    private static final String VERSION = "6.0.0";
    private static final String BANNER =
            "YAWL CLI v" + VERSION + " - Yet Another Workflow Language Developer Tools";

    /** Ordered registry of subcommand name to implementation. */
    private final Map<String, YawlCliCommand> commands = new LinkedHashMap<>();
    private final PrintStream out;
    private final PrintStream err;

    public YawlCliMain(PrintStream out, PrintStream err) {
        this.out = out;
        this.err = err;
        register(new ValidateCommand(out, err));
        register(new CompileCommand(out, err));
        register(new TemplateCommand(out, err));
        register(new SimulateCommand(out, err));
        register(new DeployCommand(out, err));
        register(new WorkflowDocumentationGenerator.DocsCommand(out, err));
    }

    private void register(YawlCliCommand cmd) {
        commands.put(cmd.name(), cmd);
    }

    /**
     * Dispatch to the appropriate subcommand.
     *
     * @param args raw command-line argument array
     * @return exit code: 0 = success, non-zero = failure
     */
    public int run(String[] args) {
        if (args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0])) {
            printUsage();
            return 0;
        }
        if ("--version".equals(args[0]) || "-v".equals(args[0])) {
            out.println(BANNER);
            return 0;
        }

        String subcommand = args[0];
        YawlCliCommand cmd = commands.get(subcommand);
        if (cmd == null) {
            err.println("Unknown subcommand: " + subcommand);
            err.println("Run 'yawl --help' for available subcommands.");
            return 1;
        }

        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        return cmd.execute(subArgs);
    }

    private void printUsage() {
        out.println(BANNER);
        out.println();
        out.println("Usage: yawl <subcommand> [options]");
        out.println();
        out.println("Available subcommands:");
        for (Map.Entry<String, YawlCliCommand> entry : commands.entrySet()) {
            out.printf("  %-12s  %s%n", entry.getKey(), entry.getValue().synopsis());
        }
        out.println();
        out.println("Run 'yawl <subcommand> --help' for subcommand-specific options.");
    }

    /**
     * Main entry point used by the executable wrapper script.
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        int exitCode = new YawlCliMain(System.out, System.err).run(args);
        System.exit(exitCode);
    }
}
