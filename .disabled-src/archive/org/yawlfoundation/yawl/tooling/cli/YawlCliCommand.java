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

import java.io.PrintStream;

/**
 * Contract for all YAWL CLI subcommands.
 *
 * Each implementation provides:
 * <ul>
 *   <li>{@link #name()} - the token used on the command line (e.g. "validate")</li>
 *   <li>{@link #synopsis()} - one-line description shown in global help</li>
 *   <li>{@link #execute(String[])} - runs the command and returns an exit code</li>
 * </ul>
 *
 * @author YAWL Development Team
 * @since 6.0.0
 */
public abstract class YawlCliCommand {

    protected final PrintStream out;
    protected final PrintStream err;

    protected YawlCliCommand(PrintStream out, PrintStream err) {
        this.out = out;
        this.err = err;
    }

    /**
     * The subcommand token as typed on the command line.
     * @return subcommand name, e.g. "validate"
     */
    public abstract String name();

    /**
     * One-line description shown in the global usage listing.
     * @return synopsis string
     */
    public abstract String synopsis();

    /**
     * Execute this subcommand.
     *
     * @param args arguments following the subcommand token
     * @return 0 on success, non-zero on failure
     */
    public abstract int execute(String[] args);

    /** Print subcommand-specific help to {@code out}. */
    protected abstract void printHelp();

    /**
     * Convenience: write an error line and return 1.
     */
    protected int fail(String message) {
        err.println("[ERROR] " + name() + ": " + message);
        return 1;
    }

    /**
     * Check whether the first argument is a help flag.
     */
    protected boolean isHelpRequest(String[] args) {
        return args.length > 0 && ("--help".equals(args[0]) || "-h".equals(args[0]));
    }
}
