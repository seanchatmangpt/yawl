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

package org.yawlfoundation.yawl.integration.autonomous.launcher;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jdom2.Element;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.interfce.interfaceA.InterfaceA_EnvironmentBasedClient;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generic workflow launcher that can launch any YAWL specification via command-line
 * arguments or environment variables. Replaces domain-specific launchers with a
 * flexible, configurable approach.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public final class GenericWorkflowLauncher {

    private static final long POLL_INTERVAL_MS = 2000;
    private static final long DEFAULT_TIMEOUT_SEC = 600;

    private final String engineUrl;
    private final String username;
    private final String password;
    private final YSpecificationID specId;
    private final Path specPath;
    private final String caseData;
    private final long timeoutSec;
    private final boolean uploadSpec;

    public GenericWorkflowLauncher(String engineUrl, String username, String password,
                                   YSpecificationID specId, Path specPath, String caseData,
                                   long timeoutSec, boolean uploadSpec) {
        if (engineUrl == null || engineUrl.isEmpty()) {
            throw new IllegalArgumentException("Engine URL must not be null or empty");
        }
        if (username == null || username.isEmpty()) {
            throw new IllegalArgumentException("Username must not be null or empty");
        }
        if (password == null) {
            throw new IllegalArgumentException("Password must not be null");
        }
        if (specId == null) {
            throw new IllegalArgumentException("Specification ID must not be null");
        }
        if (uploadSpec && (specPath == null || !Files.isReadable(specPath))) {
            throw new IllegalArgumentException("Spec path must be readable when upload is enabled: " + specPath);
        }

        this.engineUrl = engineUrl;
        this.username = username;
        this.password = password;
        this.specId = specId;
        this.specPath = specPath;
        this.caseData = caseData;
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
                System.out.println("Specification uploaded successfully: " + specId);
            }

            String caseDataXml = convertJsonToYawlXml(caseData);
            String caseId = ibClient.launchCase(specId, caseDataXml, sessionB, null, null);
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
            } catch (IOException ignored) {
            }
            try {
                ibClient.disconnect(sessionB);
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Convert JSON case data to YAWL XML format. Handles both JSON objects and null input.
     *
     * @param jsonData JSON string or null
     * @return XML string in YAWL format or null if input is null/empty
     */
    private static String convertJsonToYawlXml(String jsonData) {
        if (jsonData == null || jsonData.trim().isEmpty()) {
            return null;
        }

        try {
            JsonElement jsonElement = JsonParser.parseString(jsonData);
            if (!jsonElement.isJsonObject()) {
                throw new IllegalArgumentException("Case data must be a JSON object");
            }

            JsonObject jsonObject = jsonElement.getAsJsonObject();
            Element dataElement = new Element("data");

            for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                String key = entry.getKey();
                JsonElement value = entry.getValue();
                Element paramElement = new Element(key);

                if (value.isJsonPrimitive()) {
                    paramElement.setText(value.getAsString());
                } else if (value.isJsonNull()) {
                    paramElement.setText("");
                } else {
                    paramElement.setText(value.toString());
                }

                dataElement.addContent(paramElement);
            }

            return org.yawlfoundation.yawl.util.JDOMUtil.elementToString(dataElement);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to convert JSON to YAWL XML: " + e.getMessage(), e);
        }
    }

    private static String stripXmlTags(String s) {
        if (s == null) {
            throw new IllegalArgumentException("Cannot strip XML tags from null string");
        }
        String t = s.trim();
        if (t.isEmpty()) {
            throw new IllegalArgumentException("Cannot strip XML tags from empty string");
        }
        Matcher m = Pattern.compile(">([^<]+)<").matcher(t);
        return m.find() ? m.group(1).trim() : t.replaceAll("<[^>]+>", "").trim();
    }

    private static boolean isCaseRunning(String runningXml, String caseId) {
        if (runningXml == null || caseId == null) return false;
        return Pattern.compile("<caseID>" + Pattern.quote(caseId) + "</caseID>")
            .matcher(runningXml).find();
    }

    /**
     * Parse command-line arguments into a launcher configuration.
     * Supports both --key=value and --key value formats.
     */
    private static class Config {
        String specId;
        String specUri;
        String specVersion;
        String specPath;
        String caseData;
        String engineUrl;
        String username;
        String password;
        long timeoutSec = DEFAULT_TIMEOUT_SEC;
        boolean uploadSpec = true;

        void parseArgs(String[] args) throws IllegalArgumentException {
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                String value = null;

                if (arg.startsWith("--")) {
                    int eqIndex = arg.indexOf('=');
                    String key;
                    if (eqIndex > 0) {
                        key = arg.substring(2, eqIndex);
                        value = arg.substring(eqIndex + 1);
                    } else {
                        key = arg.substring(2);
                        if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                            value = args[++i];
                        }
                    }

                    switch (key) {
                        case "spec-id":
                            specId = value;
                            break;
                        case "spec-uri":
                            specUri = value;
                            break;
                        case "spec-version":
                            specVersion = value;
                            break;
                        case "spec-path":
                            specPath = value;
                            break;
                        case "case-data":
                            caseData = value;
                            break;
                        case "engine-url":
                            engineUrl = value;
                            break;
                        case "username":
                            username = value;
                            break;
                        case "password":
                            password = value;
                            break;
                        case "timeout-sec":
                            if (value != null) {
                                try {
                                    timeoutSec = Long.parseLong(value);
                                } catch (NumberFormatException e) {
                                    throw new IllegalArgumentException("Invalid timeout-sec: " + value);
                                }
                            }
                            break;
                        case "upload-spec":
                            if (value != null) {
                                uploadSpec = Boolean.parseBoolean(value);
                            }
                            break;
                        case "help":
                            printUsage();
                            System.exit(0);
                            break;
                        default:
                            throw new IllegalArgumentException("Unknown argument: --" + key);
                    }
                } else {
                    throw new IllegalArgumentException("Arguments must start with '--': " + arg);
                }
            }

            applyEnvironmentDefaults();
            validate();
        }

        void applyEnvironmentDefaults() {
            if (engineUrl == null) engineUrl = System.getenv("YAWL_ENGINE_URL");
            if (engineUrl == null) engineUrl = "http://localhost:8080/yawl";

            if (username == null) username = System.getenv("YAWL_USERNAME");
            if (username == null) username = "admin";

            if (password == null) password = System.getenv("YAWL_PASSWORD");
            if (password == null) password = "YAWL";

            if (specId == null) specId = System.getenv("SPEC_ID");
            if (specUri == null) specUri = System.getenv("SPEC_URI");
            if (specVersion == null) specVersion = System.getenv("SPEC_VERSION");
            if (specPath == null) specPath = System.getenv("SPEC_PATH");
            if (caseData == null) caseData = System.getenv("CASE_DATA");

            String timeoutStr = System.getenv("TIMEOUT_SEC");
            if (timeoutStr != null && !timeoutStr.isEmpty()) {
                try {
                    timeoutSec = Long.parseLong(timeoutStr);
                } catch (NumberFormatException ignored) {
                }
            }

            String uploadStr = System.getenv("UPLOAD_SPEC");
            if (uploadStr != null && !uploadStr.isEmpty()) {
                uploadSpec = Boolean.parseBoolean(uploadStr);
            }
        }

        void validate() {
            if (specId == null || specId.isEmpty()) {
                throw new IllegalArgumentException("--spec-id is required (or set SPEC_ID environment variable)");
            }
            if (uploadSpec && (specPath == null || specPath.isEmpty())) {
                throw new IllegalArgumentException("--spec-path is required when --upload-spec=true (or set SPEC_PATH environment variable)");
            }

            if (caseData != null && caseData.startsWith("@")) {
                String filePath = caseData.substring(1);
                try {
                    caseData = Files.readString(Paths.get(filePath));
                } catch (IOException e) {
                    throw new IllegalArgumentException("Failed to read case data from file: " + filePath, e);
                }
            }
        }

        YSpecificationID getSpecificationId() {
            if (specUri != null && specVersion != null) {
                return new YSpecificationID(specId, specVersion, specUri);
            } else if (specUri != null) {
                return new YSpecificationID(specId, "0.1", specUri);
            } else {
                return new YSpecificationID(specId);
            }
        }
    }

    private static void printUsage() {
        System.out.println("Generic YAWL Workflow Launcher");
        System.out.println();
        System.out.println("USAGE:");
        System.out.println("  java org.yawlfoundation.yawl.integration.autonomous.launcher.GenericWorkflowLauncher [OPTIONS]");
        System.out.println();
        System.out.println("OPTIONS:");
        System.out.println("  --spec-id <id>          Specification identifier (required, e.g., UID_xxx)");
        System.out.println("  --spec-uri <uri>        Specification URI (optional, e.g., 'orderfulfillment')");
        System.out.println("  --spec-version <ver>    Specification version (optional, default: 0.1)");
        System.out.println("  --spec-path <path>      Path to .yawl specification file (required if --upload-spec=true)");
        System.out.println("  --case-data <json>      Case input data as JSON string or @file.json (optional)");
        System.out.println("  --engine-url <url>      YAWL Engine URL (default: http://localhost:8080/yawl)");
        System.out.println("  --username <user>       Engine username (default: admin)");
        System.out.println("  --password <pass>       Engine password (default: YAWL)");
        System.out.println("  --timeout-sec <sec>     Timeout in seconds (default: 600)");
        System.out.println("  --upload-spec <bool>    Upload spec before launch (default: true)");
        System.out.println("  --help                  Show this help message");
        System.out.println();
        System.out.println("ENVIRONMENT VARIABLES:");
        System.out.println("  YAWL_ENGINE_URL         Engine URL");
        System.out.println("  YAWL_USERNAME           Engine username");
        System.out.println("  YAWL_PASSWORD           Engine password");
        System.out.println("  SPEC_ID                 Specification identifier");
        System.out.println("  SPEC_URI                Specification URI");
        System.out.println("  SPEC_VERSION            Specification version");
        System.out.println("  SPEC_PATH               Path to specification file");
        System.out.println("  CASE_DATA               Case input data (JSON string or @file.json)");
        System.out.println("  TIMEOUT_SEC             Timeout in seconds");
        System.out.println("  UPLOAD_SPEC             Upload spec flag (true/false)");
        System.out.println();
        System.out.println("EXAMPLES:");
        System.out.println("  # Launch with command-line args:");
        System.out.println("  java ... --spec-id UID_xxx --spec-uri myworkflow --spec-version 1.0 \\");
        System.out.println("           --spec-path /path/to/spec.yawl --case-data '{\"param1\":\"value1\"}'");
        System.out.println();
        System.out.println("  # Launch with environment variables:");
        System.out.println("  export SPEC_ID=UID_xxx");
        System.out.println("  export SPEC_URI=myworkflow");
        System.out.println("  export SPEC_PATH=/path/to/spec.yawl");
        System.out.println("  java ...");
        System.out.println();
        System.out.println("  # Launch without uploading spec:");
        System.out.println("  java ... --spec-id UID_xxx --upload-spec false");
        System.out.println();
        System.out.println("EXIT CODES:");
        System.out.println("  0  Success - case completed");
        System.out.println("  1  Failure - see error message");
    }

    /**
     * Entry point for generic workflow launcher.
     * Accepts command-line arguments or reads from environment variables.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        try {
            Config config = new Config();
            config.parseArgs(args);

            Path specPath = config.specPath != null ? Paths.get(config.specPath) : null;
            YSpecificationID specId = config.getSpecificationId();

            GenericWorkflowLauncher launcher = new GenericWorkflowLauncher(
                config.engineUrl,
                config.username,
                config.password,
                specId,
                specPath,
                config.caseData,
                config.timeoutSec,
                config.uploadSpec
            );

            launcher.run();
            System.exit(0);
        } catch (IllegalArgumentException e) {
            System.err.println("Configuration error: " + e.getMessage());
            System.err.println();
            printUsage();
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Launcher failed: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
