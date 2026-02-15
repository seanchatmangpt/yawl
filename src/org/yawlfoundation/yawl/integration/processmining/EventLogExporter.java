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

package org.yawlfoundation.yawl.integration.processmining;

import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.engine.interfce.interfaceE.YLogGatewayClient;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Exports YAWL event logs to XES (eXtensible Event Stream) format for process mining.
 * Uses InterfaceE (log gateway) getSpecificationXESLog for full XES export.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public final class EventLogExporter {

    private final InterfaceB_EnvironmentBasedClient ibClient;
    private final YLogGatewayClient logClient;
    private final String sessionHandle;

    public EventLogExporter(String engineUrl, String username, String password)
            throws IOException {
        String base = engineUrl.endsWith("/") ? engineUrl.substring(0, engineUrl.length() - 1) : engineUrl;
        this.ibClient = new InterfaceB_EnvironmentBasedClient(base + "/ib");
        this.logClient = new YLogGatewayClient(base + "/logGateway");
        String session = ibClient.connect(username, password);
        if (session == null || session.contains("failure") || session.contains("error")) {
            throw new IOException("Failed to connect: " + session);
        }
        this.sessionHandle = session;
    }

    /**
     * Export XES log for a specification (all completed cases).
     *
     * @param specId specification ID
     * @param withData include data attributes
     * @return XES XML string
     */
    public String exportSpecificationToXes(YSpecificationID specId, boolean withData)
            throws IOException {
        String xes = logClient.getSpecificationXESLog(specId, withData, sessionHandle);
        if (xes == null || xes.contains("<failure>")) {
            throw new IOException("Failed to export XES: " + xes);
        }
        return xes;
    }

    /**
     * Export event log to a file.
     */
    public void exportToFile(YSpecificationID specId, boolean withData, Path outputPath)
            throws IOException {
        String xes = exportSpecificationToXes(specId, withData);
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(outputPath))) {
            w.print(xes);
        }
    }

    public void close() throws IOException {
        ibClient.disconnect(sessionHandle);
    }
}
