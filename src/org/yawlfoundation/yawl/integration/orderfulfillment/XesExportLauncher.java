/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

package org.yawlfoundation.yawl.integration.orderfulfillment;

import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.integration.processmining.EventLogExporter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Exports orderfulfillment XES log to file for process mining.
 * Used by simulation to feed PM4Py after case completion.
 *
 * Env: YAWL_ENGINE_URL, YAWL_USERNAME, YAWL_PASSWORD.
 * Optional: OUTPUT_PATH (default: orderfulfillment.xes in current dir).
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public final class XesExportLauncher {

    private static final String SPEC_ID = "UID_ae0b797c-2ac8-4d5e-9421-ece89d8043d0";
    private static final String SPEC_URI = "orderfulfillment";
    private static final String SPEC_VERSION = "1.2";

    public static void main(String[] args) {
        String engineUrl = System.getenv("YAWL_ENGINE_URL");
        if (engineUrl == null || engineUrl.isEmpty()) {
            engineUrl = "http://localhost:8080/yawl";
        }
        String username = System.getenv("YAWL_USERNAME");
        if (username == null || username.isEmpty()) username = "admin";
        String password = System.getenv("YAWL_PASSWORD");
        if (password == null || password.isEmpty()) password = "YAWL";

        String outputStr = System.getenv("OUTPUT_PATH");
        Path outputPath = outputStr != null && !outputStr.isEmpty()
            ? Paths.get(outputStr)
            : Paths.get("orderfulfillment.xes");

        try {
            EventLogExporter exporter = new EventLogExporter(engineUrl, username, password);
            YSpecificationID specId = new YSpecificationID(SPEC_ID, SPEC_VERSION, SPEC_URI);
            exporter.exportToFile(specId, false, outputPath);
            exporter.close();
            System.out.println("XES exported to " + outputPath.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Export failed: " + e.getMessage());
            System.exit(1);
        }
    }
}
