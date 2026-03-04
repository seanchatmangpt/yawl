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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.integration.a2a.skills.A2ASkill;
import org.yawlfoundation.yawl.integration.a2a.skills.SkillRequest;
import org.yawlfoundation.yawl.integration.a2a.skills.SkillResult;

/**
 * A2A Skill for committing changes to git with explicit file selection.
 *
 * <p>Stages specified files and commits with the provided message. Never uses
 * {@code git add .} for safety—requires explicit file list.
 *
 * <p><b>Required Permission:</b> {@code code:commit}
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class CommitSkill implements A2ASkill {

    private static final Logger _logger = LogManager.getLogger(CommitSkill.class);
    private static final String SKILL_ID = "rl_commit";
    private static final String SKILL_NAME = "Commit Changes";
    private static final String SKILL_DESCRIPTION =
        "Commit changes to git with explicit file selection. Never uses 'git add .' " +
        "for safety. Requires comma-separated list of files and commit message.";

    private static final int TIMEOUT_SECONDS = 30;

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
        return Set.of("code:commit");
    }

    @Override
    public SkillResult execute(SkillRequest request) {
        String message = request.getParameter("message");
        if (message == null || message.isBlank()) {
            return SkillResult.error("message parameter is required (non-empty string)");
        }

        String filesParam = request.getParameter("files");
        if (filesParam == null || filesParam.isBlank()) {
            return SkillResult.error("files parameter is required (comma-separated list)");
        }

        String[] files = filesParam.split(",");
        for (int i = 0; i < files.length; i++) {
            files[i] = files[i].trim();
        }

        _logger.info("Committing {} files with message: {}", files.length, message);

        long startTime = System.currentTimeMillis();

        try {
            // Stage files
            gitAdd(files);

            // Commit
            String commitHash = gitCommit(message);
            long duration = System.currentTimeMillis() - startTime;

            Map<String, Object> result = new HashMap<>();
            result.put("commitHash", commitHash);
            result.put("filesCommitted", filesParam);
            result.put("message", message);
            result.put("durationMs", String.valueOf(duration));

            _logger.info("Commit completed successfully: {}", commitHash);
            return SkillResult.success(result, duration);

        } catch (Exception e) {
            _logger.error("Commit failed: {}", e.getMessage());
            return SkillResult.error("Commit failed: " + e.getMessage());
        }
    }

    private void gitAdd(String[] files) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("add");
        for (String file : files) {
            command.add(file);
        }

        ProcessBuilder pb = new ProcessBuilder(command)
            .directory(new File(System.getProperty("user.dir", "/home/user/yawl")));
        pb.redirectErrorStream(true);

        Process process = pb.start();
        boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("git add timed out");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new RuntimeException("git add failed with exit code " + exitCode);
        }
    }

    private String gitCommit(String message) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("git", "commit", "-m", message)
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
                _logger.warn("Error reading commit output: {}", e.getMessage());
            }
        });

        boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("git commit timed out");
        }

        outputThread.join(5000);

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new RuntimeException("git commit failed: " + output.toString());
        }

        // Get the commit hash
        ProcessBuilder hashPb = new ProcessBuilder("git", "rev-parse", "HEAD")
            .directory(new File(System.getProperty("user.dir", "/home/user/yawl")));
        pb.redirectErrorStream(true);

        Process hashProcess = hashPb.start();
        StringBuilder hashOutput = new StringBuilder();

        Thread hashThread = Thread.ofVirtual().start(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(hashProcess.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    hashOutput.append(line);
                }
            } catch (Exception e) {
                _logger.warn("Error reading commit hash: {}", e.getMessage());
            }
        });

        hashProcess.waitFor(5, TimeUnit.SECONDS);
        hashThread.join(1000);

        String hash = hashOutput.toString().trim();
        return hash.length() >= 8 ? hash.substring(0, 8) : hash;
    }
}
