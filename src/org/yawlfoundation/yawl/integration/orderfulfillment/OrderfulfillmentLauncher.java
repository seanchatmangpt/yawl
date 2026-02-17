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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.interfce.interfaceA.InterfaceA_EnvironmentBasedClient;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;

/**
 * Launches an orderfulfillment case, optionally uploads the spec, and polls until
 * the case completes. Used for end-to-end simulation validation.
 *
 * @author YAWL Foundation
 * @version 5.2
 * @deprecated Use {@link org.yawlfoundation.yawl.integration.autonomous.launcher.GenericWorkflowLauncher} instead.
 *             This class is specific to orderfulfillment and will be removed in a future version.
 */
@Deprecated
public final class OrderfulfillmentLauncher {


    private static final Logger logger = LogManager.getLogger(OrderfulfillmentLauncher.class);
    private static final String SPEC_IDENTIFIER = "UID_ae0b797c-2ac8-4d5e-9421-ece89d8043d0";
    private static final String SPEC_URI = "orderfulfillment";
    private static final String SPEC_VERSION = "1.2";
    private static final long POLL_INTERVAL_MS = 2000;
    private static final long DEFAULT_TIMEOUT_SEC = 600;

    private final String engineUrl;
    private final String username;
    private final String password;
    private final Path specPath;
    private final long timeoutSec;
    private final boolean uploadSpec;

    public OrderfulfillmentLauncher(String engineUrl, String username, String password,
                                    Path specPath, long timeoutSec, boolean uploadSpec) {
        this.engineUrl = engineUrl;
        this.username = username;
        this.password = password;
        this.specPath = specPath;
        this.timeoutSec = timeoutSec > 0 ? timeoutSec : DEFAULT_TIMEOUT_SEC;
        this.uploadSpec = uploadSpec;
    }

    /**
     * Run the launcher: connect, optionally upload spec, launch case, poll until complete.
     *
     * @return the launched case ID on success
     * @throws IOException on connection or engine errors
     */
    public String run() throws IOException {
        String baseUrl = engineUrl.endsWith("/") ? engineUrl.substring(0, engineUrl.length() - 1) : engineUrl;
        String iaUrl = baseUrl + "/ia";
        String ibUrl = baseUrl + "/ib";

        InterfaceA_EnvironmentBasedClient iaClient = new InterfaceA_EnvironmentBasedClient(iaUrl);
        InterfaceB_EnvironmentBasedClient ibClient = new InterfaceB_EnvironmentBasedClient(ibUrl);

        String sessionA = iaClient.connect(username, password);
        if (sessionA == null || sessionA.contains("failure") || sessionA.contains("error")) {
            throw new IOException("InterfaceA connect failed: " + sessionA);
        }

        String sessionB = ibClient.connect(username, password);
        if (sessionB == null || sessionB.contains("failure") || sessionB.contains("error")) {
            iaClient.disconnect(sessionA);
            throw new IOException("InterfaceB connect failed: " + sessionB);
        }

        try {
            if (uploadSpec) {
                String specXml = Files.readString(specPath);
                String uploadResult = iaClient.uploadSpecification(specXml, sessionA);
                if (uploadResult == null || uploadResult.contains("<failure>")) {
                    throw new IOException("Spec upload failed: " + uploadResult);
                }
                System.out.println("Specification uploaded successfully.");
            }

            YSpecificationID specId = new YSpecificationID(SPEC_IDENTIFIER, SPEC_VERSION, SPEC_URI);
            String caseId = ibClient.launchCase(specId, null, sessionB, null, null);
            if (caseId == null || caseId.contains("failure") || caseId.contains("error")) {
                throw new IOException("Launch case failed: " + caseId);
            }
            caseId = stripXmlTags(caseId);
            System.out.println("Case launched: " + caseId);

            long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutSec);
            while (System.currentTimeMillis() < deadline) {
                String runningXml = ibClient.getAllRunningCases(sessionB);
                if (runningXml == null || runningXml.contains("<failure>")) {
                    throw new IOException("getAllRunningCases failed: " + runningXml);
                }
                if (!isCaseRunning(runningXml, caseId)) {
                    System.out.println("Case " + caseId + " completed successfully.");
                    return caseId;
                }
                try {
                    Thread.sleep(POLL_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting for case completion", e);
                }
            }
            throw new IOException("Timeout waiting for case " + caseId + " to complete after " + timeoutSec + "s");
        } finally {
            try {
                iaClient.disconnect(sessionA);
            } catch (IOException e) {
                logger.warn("Failed to disconnect client: " + e.getMessage(), e);
            }
            try {
                ibClient.disconnect(sessionB);
            } catch (IOException e) {
                logger.warn("Failed to disconnect client: " + e.getMessage(), e);
            }
        }
    }

    private static String stripXmlTags(String s) {
        if (s == null) return "";
        String t = s.trim();
        Matcher m = Pattern.compile(">([^<]+)<").matcher(t);
        return m.find() ? m.group(1).trim() : t.replaceAll("<[^>]+>", "").trim();
    }

    private static boolean isCaseRunning(String runningXml, String caseId) {
        if (runningXml == null || caseId == null) return false;
        return Pattern.compile("<caseID>" + Pattern.quote(caseId) + "</caseID>")
            .matcher(runningXml).find();
    }

    /**
     * Entry point. Env: YAWL_ENGINE_URL, YAWL_USERNAME, YAWL_PASSWORD.
     * Optional: SPEC_PATH, TIMEOUT_SEC, UPLOAD_SPEC (true/false, default true).
     */
    public static void main(String[] args) {
        String engineUrl = System.getenv("YAWL_ENGINE_URL");
        if (engineUrl == null || engineUrl.isEmpty()) {
            engineUrl = "http://localhost:8080/yawl";
        }
        String username = System.getenv("YAWL_USERNAME");
        if (username == null || username.isEmpty()) username = "admin";
        String password = System.getenv("YAWL_PASSWORD");
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException(
                "YAWL_PASSWORD environment variable must be set. " +
                "See deployment runbook for credential configuration."
            );
        }

        String specPathStr = System.getenv("SPEC_PATH");
        if (specPathStr == null || specPathStr.isEmpty()) {
            specPathStr = "exampleSpecs/orderfulfillment/_examples/orderfulfillment.yawl";
        }
        Path specPath = Paths.get(specPathStr);
        if (!Files.isReadable(specPath)) {
            System.err.println("Spec file not found or not readable: " + specPath);
            System.exit(1);
        }

        long timeoutSec = DEFAULT_TIMEOUT_SEC;
        String timeoutStr = System.getenv("TIMEOUT_SEC");
        if (timeoutStr != null && !timeoutStr.isEmpty()) {
            try {
                timeoutSec = Long.parseLong(timeoutStr);
            } catch (NumberFormatException e) {
                logger.warn("Invalid number format in parameter: " + e.getMessage(), e);
            }
        }

        boolean uploadSpec = !"false".equalsIgnoreCase(System.getenv("UPLOAD_SPEC"));

        OrderfulfillmentLauncher launcher = new OrderfulfillmentLauncher(
            engineUrl, username, password, specPath, timeoutSec, uploadSpec);

        try {
            launcher.run();
            System.exit(0);
        } catch (IOException e) {
            System.err.println("Launcher failed: " + e.getMessage());
            System.exit(1);
        }
    }
}
