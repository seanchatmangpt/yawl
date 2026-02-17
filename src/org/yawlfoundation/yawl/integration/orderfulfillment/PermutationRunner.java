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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.interfce.interfaceA.InterfaceA_EnvironmentBasedClient;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;

/**
 * Runs orderfulfillment scenario permutations to challenge autonomous agents.
 * Supports concurrent cases, sequential runs, and configurable timeouts.
 *
 * Config: config/orderfulfillment-permutations.json
 * Env: YAWL_ENGINE_URL, YAWL_USERNAME, YAWL_PASSWORD.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public final class PermutationRunner {


    private static final Logger logger = LogManager.getLogger(PermutationRunner.class);
    private static final String SPEC_ID = "UID_ae0b797c-2ac8-4d5e-9421-ece89d8043d0";
    private static final String SPEC_URI = "orderfulfillment";
    private static final String SPEC_VERSION = "1.2";
    private static final long POLL_MS = 2000;

    private final String engineUrl;
    private final String username;
    private final String password;
    private final Path specPath;
    private final Path configPath;
    private final boolean uploadSpec;

    public PermutationRunner(String engineUrl, String username, String password,
                             Path specPath, Path configPath, boolean uploadSpec) {
        this.engineUrl = engineUrl;
        this.username = username;
        this.password = password;
        this.specPath = specPath;
        this.configPath = configPath;
        this.uploadSpec = uploadSpec;
    }

    /**
     * Run all permutations from config.
     * Optional env PERMUTATION_IDS: comma-separated ids to run (e.g. baseline,rapid).
     *
     * @return number of permutations that passed
     */
    public int runAll() throws IOException {
        List<Permutation> perms = loadPermutations();
        String filterIds = System.getenv("PERMUTATION_IDS");
        if (filterIds != null && !filterIds.trim().isEmpty()) {
            java.util.Set<String> ids = new java.util.HashSet<>();
            for (String id : filterIds.split(",")) {
                ids.add(id.trim());
            }
            perms.removeIf(p -> !ids.contains(p.id));
        }
        perms.removeIf(p -> Boolean.TRUE.equals(p.disabled));
        if (perms.isEmpty()) {
            System.err.println("No permutations found in " + configPath);
            return 0;
        }

        System.out.println("Order Fulfillment Permutation Suite");
        System.out.println("  Engine: " + engineUrl);
        System.out.println("  Permutations: " + perms.size());
        System.out.println();

        int passed = 0;
        for (Permutation p : perms) {
            boolean ok = runPermutation(p);
            if (ok) passed++;
            System.out.println();
        }

        System.out.println("=== Summary: " + passed + "/" + perms.size() + " passed ===");
        return passed;
    }

    private boolean runPermutation(Permutation p) {
        System.out.println("[" + p.id + "] " + p.name + " - " + p.description);
        System.out.println("  Challenge: " + p.challenge + " | timeout=" + p.timeoutSec + "s");

        try {
            if (p.repeatCount != null && p.repeatCount > 1) {
                return runSequential(p);
            }
            if (p.concurrentCases != null && p.concurrentCases > 1) {
                return runConcurrent(p);
            }
            return runSingle(p);
        } catch (Exception e) {
            System.err.println("  FAIL: " + e.getMessage());
            return false;
        }
    }

    private boolean runSingle(Permutation p) throws IOException {
        OrderfulfillmentLauncher launcher = new OrderfulfillmentLauncher(
            engineUrl, username, password, specPath, p.timeoutSec, uploadSpec);
        launcher.run();
        System.out.println("  PASS");
        return true;
    }

    private boolean runSequential(Permutation p) throws IOException {
        int n = p.repeatCount != null ? p.repeatCount : 1;
        long pauseMs = p.pauseBetweenMs != null ? p.pauseBetweenMs : 0;

        for (int i = 0; i < n; i++) {
            System.out.println("  Run " + (i + 1) + "/" + n + "...");
            OrderfulfillmentLauncher launcher = new OrderfulfillmentLauncher(
                engineUrl, username, password, specPath, p.timeoutSec,
                uploadSpec && i == 0);
            launcher.run();
            if (i < n - 1 && pauseMs > 0) {
                try {
                    Thread.sleep(pauseMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted", e);
                }
            }
        }
        System.out.println("  PASS (" + n + " cases)");
        return true;
    }

    private boolean runConcurrent(Permutation p) throws IOException {
        int n = p.concurrentCases != null ? p.concurrentCases : 1;
        if (n <= 1) {
            return runSingle(p);
        }

        String base = engineUrl.endsWith("/") ? engineUrl.substring(0, engineUrl.length() - 1) : engineUrl;
        String iaUrl = base + "/ia";
        String ibUrl = base + "/ib";

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
                System.out.println("  Spec uploaded.");
            }

            YSpecificationID specId = new YSpecificationID(SPEC_ID, SPEC_VERSION, SPEC_URI);
            List<String> caseIds = new ArrayList<>();

            for (int i = 0; i < n; i++) {
                String caseId = ibClient.launchCase(specId, null, sessionB, null, null);
                if (caseId == null || caseId.contains("failure") || caseId.contains("error")) {
                    throw new IOException("Launch case " + (i + 1) + " failed: " + caseId);
                }
                caseIds.add(stripXmlTags(caseId));
            }
            System.out.println("  Launched " + n + " cases: " + caseIds);

            long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(p.timeoutSec);
            while (System.currentTimeMillis() < deadline) {
                String runningXml = ibClient.getAllRunningCases(sessionB);
                if (runningXml == null || runningXml.contains("<failure>")) {
                    throw new IOException("getAllRunningCases failed: " + runningXml);
                }
                boolean anyRunning = false;
                for (String cid : caseIds) {
                    if (isCaseRunning(runningXml, cid)) {
                        anyRunning = true;
                        break;
                    }
                }
                if (!anyRunning) {
                    System.out.println("  PASS (all " + n + " cases completed)");
                    return true;
                }
                try {
                    Thread.sleep(POLL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted", e);
                }
            }
            throw new IOException("Timeout: not all cases completed within " + p.timeoutSec + "s");
        } finally {
            try {
                iaClient.disconnect(sessionA);
            } catch (IOException e) {
                logger.warn("Failed to disconnect Interface A client: " + e.getMessage(), e);
            }
            try {
                ibClient.disconnect(sessionB);
            } catch (IOException e) {
                logger.warn("Failed to disconnect Interface B client: " + e.getMessage(), e);
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
        return Pattern.compile("<caseID>" + Pattern.quote(caseId) + "</caseID>").matcher(runningXml).find();
    }

    private List<Permutation> loadPermutations() throws IOException {
        String json = Files.readString(configPath);
        List<Permutation> out = new ArrayList<>();

        int arrayStart = json.indexOf("\"permutations\"");
        if (arrayStart < 0) return out;
        int braceStart = json.indexOf("[", arrayStart);
        if (braceStart < 0) return out;

        int depth = 0;
        int objStart = -1;
        for (int i = braceStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') {
                if (depth == 1) objStart = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 1 && objStart >= 0) {
                    out.add(parsePermutation(json.substring(objStart, i + 1)));
                    objStart = -1;
                }
            } else if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
                if (depth == 0) break;
            }
        }
        return out;
    }

    private static Permutation parsePermutation(String obj) {
        Permutation p = new Permutation();
        p.id = extractString(obj, "id");
        p.name = extractString(obj, "name");
        p.description = extractString(obj, "description");
        p.challenge = extractString(obj, "challenge");
        p.concurrentCases = extractInt(obj, "concurrentCases");
        p.repeatCount = extractInt(obj, "repeatCount");
        p.pauseBetweenMs = extractLong(obj, "pauseBetweenMs");
        p.timeoutSec = extractLong(obj, "timeoutSec");
        p.disabled = "true".equalsIgnoreCase(extractString(obj, "disabled"));
        if (p.timeoutSec == null) p.timeoutSec = 600L;
        return p;
    }

    private static String extractString(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return "";
        int colon = json.indexOf(":", idx);
        int start = json.indexOf("\"", colon + 1) + 1;
        int end = json.indexOf("\"", start);
        return end > start ? json.substring(start, end) : "";
    }

    private static Integer extractInt(String json, String key) {
        Long v = extractLong(json, key);
        return v != null ? v.intValue() : null;
    }

    private static Long extractLong(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int colon = json.indexOf(":", idx);
        int start = colon + 1;
        while (start < json.length() && !Character.isDigit(json.charAt(start)) && json.charAt(start) != '-') {
            start++;
        }
        if (start >= json.length()) return null;
        int end = start;
        while (end < json.length()) {
            char c = json.charAt(end);
            if (Character.isDigit(c) || c == '-') end++;
            else break;
        }
        try {
            return Long.parseLong(json.substring(start, end));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static final class Permutation {
        String id, name, description, challenge;
        Integer concurrentCases, repeatCount;
        Long pauseBetweenMs, timeoutSec;
        Boolean disabled;
    }

    public static void main(String[] args) {
        String engineUrl = System.getenv("YAWL_ENGINE_URL");
        if (engineUrl == null || engineUrl.isEmpty()) engineUrl = "http://localhost:8080/yawl";
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
            System.err.println("Spec not found: " + specPath);
            System.exit(1);
        }

        String configStr = System.getenv("PERMUTATION_CONFIG");
        if (configStr == null || configStr.isEmpty()) {
            configStr = "config/orderfulfillment-permutations.json";
        }
        Path configPath = Paths.get(configStr);
        if (!Files.isReadable(configPath)) {
            System.err.println("Config not found: " + configPath);
            System.exit(1);
        }

        boolean uploadSpec = !"false".equalsIgnoreCase(System.getenv("UPLOAD_SPEC"));

        PermutationRunner runner = new PermutationRunner(
            engineUrl, username, password, specPath, configPath, uploadSpec);

        try {
            int passed = runner.runAll();
            System.exit(passed > 0 ? 0 : 1);
        } catch (IOException e) {
            System.err.println("Runner failed: " + e.getMessage());
            System.exit(1);
        }
    }
}
