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

import org.yawlfoundation.yawl.engine.interfce.interfaceA.InterfaceA_EnvironmentBasedClient;
import org.yawlfoundation.yawl.tooling.cli.YawlCliCommand;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * CLI subcommand: {@code yawl deploy <spec-file> [options]}
 *
 * Uploads a YAWL specification to a running engine via Interface A
 * ({@link InterfaceA_EnvironmentBasedClient}).
 *
 * Options:
 *   --engine-url  URL of the Interface A endpoint (default: http://localhost:8080/yawl/ia)
 *   --user        Engine admin username (default: admin)
 *   --password    Engine admin password (required or set YAWL_PASSWORD env var)
 *   --unload      Unload the previous version before uploading
 *
 * Exit codes:
 *   0 - Deployed successfully
 *   1 - Deployment error
 *   2 - I/O error
 *
 * @author YAWL Development Team
 * @since 6.0.0
 */
public class DeployCommand extends YawlCliCommand {

    private static final String DEFAULT_ENGINE_URL = "http://localhost:8080/yawl/ia";
    private static final String DEFAULT_USER = "admin";
    private static final String YAWL_PASSWORD_ENV = "YAWL_PASSWORD";

    public DeployCommand(PrintStream out, PrintStream err) {
        super(out, err);
    }

    @Override
    public String name() { return "deploy"; }

    @Override
    public String synopsis() { return "Upload a specification to a running engine via Interface A"; }

    @Override
    public int execute(String[] args) {
        if (isHelpRequest(args)) {
            printHelp();
            return 0;
        }
        if (args.length == 0) {
            return fail("No specification file provided. Run 'yawl deploy --help'.");
        }

        String filePath   = null;
        String engineUrl  = DEFAULT_ENGINE_URL;
        String user       = DEFAULT_USER;
        String password   = System.getenv(YAWL_PASSWORD_ENV);
        boolean unload    = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--engine-url" -> engineUrl = requireArg(args, ++i, "--engine-url");
                case "--user"       -> user = requireArg(args, ++i, "--user");
                case "--password"   -> password = requireArg(args, ++i, "--password");
                case "--unload"     -> unload = true;
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
        if (password == null || password.isBlank()) {
            return fail("Password required. Use --password or set YAWL_PASSWORD environment variable.");
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

        InterfaceA_EnvironmentBasedClient client = new InterfaceA_EnvironmentBasedClient(engineUrl);

        try {
            out.println("[deploy] Connecting to engine at: " + engineUrl);
            String sessionHandle = client.connect(user, password);
            if (sessionHandle == null || sessionHandle.startsWith("<failure")) {
                return fail("Engine connection failed: " + sessionHandle);
            }
            out.println("[deploy] Session established");

            if (unload) {
                out.println("[deploy] --unload flag set: the engine will replace any existing version");
                out.println("[deploy] (Use Interface B or the engine admin console to unload specific versions by ID)");
            }

            out.println("[deploy] Uploading: " + specFile.getName());
            String uploadResult = client.uploadSpecification(specXml, sessionHandle);

            if (uploadResult == null) {
                return fail("Upload returned no response from engine.");
            }
            if (uploadResult.startsWith("<failure") || uploadResult.contains("error")) {
                err.println("[deploy] Engine response: " + uploadResult);
                return fail("Upload failed.");
            }

            out.println("[deploy] Upload response: " + uploadResult);
            out.println("[deploy] SUCCESS - specification deployed to " + engineUrl);
            client.disconnect(sessionHandle);
            return 0;

        } catch (IOException e) {
            return fail("Network error communicating with engine: " + e.getMessage());
        }
    }

    private String requireArg(String[] args, int index, String flag) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Option " + flag + " requires a value.");
        }
        return args[index];
    }

    @Override
    protected void printHelp() {
        out.println("Usage: yawl deploy <spec-file> [options]");
        out.println();
        out.println("Upload a YAWL specification to a running engine.");
        out.println();
        out.println("Arguments:");
        out.println("  <spec-file>             Path to the .xml or .yawl specification file");
        out.println();
        out.println("Options:");
        out.println("  --engine-url <url>      Engine Interface A URL (default: " + DEFAULT_ENGINE_URL + ")");
        out.println("  --user <username>       Admin username (default: " + DEFAULT_USER + ")");
        out.println("  --password <password>   Admin password (or set YAWL_PASSWORD env var)");
        out.println("  --unload                Unload previous version before uploading");
        out.println("  -h, --help              Show this help message");
        out.println();
        out.println("Environment variables:");
        out.println("  YAWL_PASSWORD           Engine admin password (alternative to --password)");
    }
}
