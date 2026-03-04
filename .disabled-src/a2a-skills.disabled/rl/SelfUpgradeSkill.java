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
 * A2A Skill for self-upgrading agents via spec updates.
 *
 * <p>Supports loading updated agent specification files or querying recent
 * git commits for agent versioning.
 *
 * <p><b>Required Permission:</b> {@code code:upgrade}
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class SelfUpgradeSkill implements A2ASkill {

    private static final Logger _logger = LogManager.getLogger(SelfUpgradeSkill.class);
    private static final String SKILL_ID = "rl_self_upgrade";
    private static final String SKILL_NAME = "Self Upgrade Agent";
    private static final String SKILL_DESCRIPTION =
        "Load updated agent specification files or query recent git commits " +
        "for agent version tracking and self-upgrade support.";

    private static final int TIMEOUT_SECONDS = 10;

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
        return Set.of("code:upgrade");
    }

    @Override
    public SkillResult execute(SkillRequest request) {
        String specPath = request.getParameter("specPath");

        if (specPath != null && !specPath.isBlank()) {
            return loadSpecFile(specPath);
        } else {
            return queryRecentCommits();
        }
    }

    private SkillResult loadSpecFile(String specPath) {
        _logger.info("Loading spec file: {}", specPath);

        try {
            Path filePath = Path.of(specPath);
            String content = Files.readString(filePath);

            Map<String, Object> result = new HashMap<>();
            result.put("status", "loaded");
            result.put("specPath", specPath);
            result.put("contentLength", String.valueOf(content.length()));

            _logger.info("Successfully loaded spec file ({} bytes)", content.length());
            return SkillResult.success(result);

        } catch (Exception e) {
            _logger.error("Failed to load spec file: {}", e.getMessage());
            return SkillResult.error("Failed to load spec file: " + e.getMessage());
        }
    }

    private SkillResult queryRecentCommits() {
        _logger.info("Querying recent git commits");

        try {
            String commits = getRecentCommits();

            Map<String, Object> result = new HashMap<>();
            result.put("status", "unchanged");
            result.put("recentCommits", commits);

            _logger.info("Retrieved recent commits");
            return SkillResult.success(result);

        } catch (Exception e) {
            _logger.error("Failed to query commits: {}", e.getMessage());
            return SkillResult.error("Failed to query commits: " + e.getMessage());
        }
    }

    private String getRecentCommits() throws Exception {
        ProcessBuilder pb = new ProcessBuilder("git", "log", "--oneline", "-5")
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
                _logger.warn("Error reading git output: {}", e.getMessage());
            }
        });

        boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("git log timed out");
        }

        outputThread.join(5000);

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new RuntimeException("git log failed with exit code " + exitCode);
        }

        return output.toString().trim();
    }
}
