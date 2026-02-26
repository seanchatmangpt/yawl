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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.integration.a2a.skills.A2ASkill;
import org.yawlfoundation.yawl.integration.a2a.skills.SkillRequest;
import org.yawlfoundation.yawl.integration.a2a.skills.SkillResult;

/**
 * A2A Skill for executing Maven builds via the dx.sh script.
 *
 * <p>Supports targeted module builds and full system compilation with timeout
 * and output capture for CI/CD integration.
 *
 * <p><b>Required Permission:</b> {@code code:build}
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class ExecuteSkill implements A2ASkill {

    private static final Logger _logger = LogManager.getLogger(ExecuteSkill.class);
    private static final String SKILL_ID = "rl_execute";
    private static final String SKILL_NAME = "Execute Maven Build";
    private static final String SKILL_DESCRIPTION =
        "Execute Maven builds using dx.sh. Supports targeted module builds and " +
        "full system compilation with timeout and output capture.";

    private static final int TIMEOUT_SECONDS = 300;
    private static final Pattern MODULE_PATTERN = Pattern.compile(
        "^[a-zA-Z][a-zA-Z0-9_-]*(,[a-zA-Z][a-zA-Z0-9_-]*)*$"
    );

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
        return Set.of("code:build");
    }

    @Override
    public SkillResult execute(SkillRequest request) {
        String module = request.getParameter("module", "");

        if (!module.isEmpty() && !MODULE_PATTERN.matcher(module).matches()) {
            return SkillResult.error("Invalid module name format: " + module +
                ". Must start with letter and contain only alphanumeric, underscore, or hyphen.");
        }

        _logger.info("Executing Maven build{}", module.isEmpty() ? "" : " for module: " + module);

        long startTime = System.currentTimeMillis();

        try {
            BuildResult result = executeBuild(module);
            long duration = System.currentTimeMillis() - startTime;

            Map<String, Object> resultData = new HashMap<>();
            resultData.put("exitCode", String.valueOf(result.exitCode));
            resultData.put("output", truncateOutput(result.output, 2000));
            resultData.put("durationMs", String.valueOf(duration));

            if (result.exitCode == 0) {
                _logger.info("Build completed successfully in {}ms", duration);
                return SkillResult.success(resultData, duration);
            } else {
                _logger.warn("Build failed with exit code {}", result.exitCode);
                return SkillResult.error("Build failed with exit code " + result.exitCode, resultData);
            }

        } catch (Exception e) {
            _logger.error("Build execution failed: {}", e.getMessage());
            return SkillResult.error("Build execution failed: " + e.getMessage());
        }
    }

    private BuildResult executeBuild(String module) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("bash");
        command.add("scripts/dx.sh");

        if (!module.isEmpty()) {
            command.add("-pl");
            command.add(module);
        }

        ProcessBuilder pb = new ProcessBuilder(command)
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
                _logger.warn("Error reading build output: {}", e.getMessage());
            }
        });

        boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Build timed out after " + TIMEOUT_SECONDS + " seconds");
        }

        outputThread.join(5000);
        int exitCode = process.exitValue();

        return new BuildResult(exitCode, output.toString());
    }

    private String truncateOutput(String output, int maxLength) {
        if (output == null || output.length() <= maxLength) {
            return output;
        }
        return output.substring(0, maxLength) + "... (truncated)";
    }

    private record BuildResult(int exitCode, String output) {}
}
