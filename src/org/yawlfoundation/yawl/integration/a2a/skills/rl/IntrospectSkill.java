/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.integration.a2a.skills.rl;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.integration.a2a.skills.A2ASkill;
import org.yawlfoundation.yawl.integration.a2a.skills.SkillRequest;
import org.yawlfoundation.yawl.integration.a2a.skills.SkillResult;

/**
 * A2A Skill for introspecting the YAWL codebase via the Observatory.
 *
 * <p>Runs the Observatory collection script and returns computed facts about
 * modules, dependencies, build structure, and integration status for 100x
 * context compression.
 *
 * <p><b>Required Permission:</b> {@code code:read}
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class IntrospectSkill implements A2ASkill {

    private static final Logger _logger = LogManager.getLogger(IntrospectSkill.class);
    private static final String SKILL_ID = "rl_introspect";
    private static final String SKILL_NAME = "Introspect Codebase";
    private static final String SKILL_DESCRIPTION =
        "Run Observatory collection script and return 100x compressed codebase facts " +
        "including modules, dependencies, and integration status.";

    private static final int TIMEOUT_SECONDS = 60;
    private static final String OBSERVATORY_SCRIPT = "scripts/observatory/observatory.sh";
    private static final String FACTS_DIR = "scripts/observatory/facts";

    @Override
    public String getId() {
        return SKILL_ID;
    }

    @Override
    public String getName() {
        return SKILL_NAME;
    }

    @Override
    public String getDescription() {
        return SKILL_DESCRIPTION;
    }

    @Override
    public Set<String> getRequiredPermissions() {
        return Set.of("code:read");
    }

    @Override
    public SkillResult execute(SkillRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            // Run the Observatory script
            int exitCode = runObservatory();
            long duration = System.currentTimeMillis() - startTime;

            // Read the modules.json fact file
            String modulesContent = readFactFile("modules.json");

            Map<String, Object> result = new HashMap<>();
            result.put("modules", modulesContent);
            result.put("observatoryStatus", exitCode == 0 ? "success" : "failed");
            result.put("exitCode", String.valueOf(exitCode));
            result.put("durationMs", String.valueOf(duration));

            if (exitCode == 0) {
                _logger.info("Observatory introspection completed successfully in {}ms", duration);
                return SkillResult.success(result, duration);
            } else {
                _logger.warn("Observatory script exited with code {}", exitCode);
                // Still return success with whatever we collected
                return SkillResult.success(result, duration);
            }

        } catch (Exception e) {
            _logger.error("Introspection failed: {}", e.getMessage());
            return SkillResult.error("Introspection failed: " + e.getMessage());
        }
    }

    private int runObservatory() throws Exception {
        ProcessBuilder pb = new ProcessBuilder("bash", OBSERVATORY_SCRIPT)
            .directory(new File(System.getProperty("user.dir", "/home/user/yawl")));
        pb.redirectErrorStream(true);

        Process process = pb.start();
        StringBuilder output = new StringBuilder();

        Thread outputThread = Thread.ofVirtual().start(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            } catch (Exception e) {
                _logger.warn("Error reading observatory output: {}", e.getMessage());
            }
        });

        boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Observatory script timed out after " + TIMEOUT_SECONDS + " seconds");
        }

        outputThread.join(5000);
        int exitCode = process.exitValue();

        _logger.debug("Observatory output: {}", output.toString().substring(0, Math.min(500, output.length())));
        return exitCode;
    }

    private String readFactFile(String filename) {
        try {
            Path factFile = Path.of(FACTS_DIR, filename);
            if (Files.exists(factFile)) {
                return Files.readString(factFile);
            }
        } catch (Exception e) {
            _logger.warn("Could not read fact file {}: {}", filename, e.getMessage());
        }
        return "{}";
    }
}
