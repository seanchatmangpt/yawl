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

import io.modelcontextprotocol.spec.McpSchema;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.integration.mcp.YawlMcpClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP-based task context supplier. Spawns YawlMcpServer via STDIO and fetches
 * task_completion_guide prompt for richer output generation.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public final class McpTaskContextSupplierImpl implements McpTaskContextSupplier {

    private final YawlMcpClient mcpClient;
    private final String javaPath;
    private final String classpath;

    public McpTaskContextSupplierImpl(String javaPath, String classpath) {
        this.mcpClient = new YawlMcpClient();
        this.javaPath = javaPath != null ? javaPath : "java";
        this.classpath = classpath != null ? classpath : "";
    }

    /**
     * Connect to MCP server. Call before getTaskCompletionGuide.
     * Spawns YawlMcpServer with inherited env (YAWL_ENGINE_URL, etc.).
     */
    public void connect() {
        if (mcpClient.isConnected()) {
            return;
        }
        String cp = classpath.isEmpty() ? System.getProperty("java.class.path") : classpath;
        mcpClient.connectStdio(javaPath, "-cp", cp,
            "org.yawlfoundation.yawl.integration.mcp.YawlMcpServer");
    }

    @Override
    public String getTaskCompletionGuide(WorkItemRecord workItem) {
        if (!mcpClient.isConnected()) {
            return null;
        }
        try {
            Map<String, Object> args = new HashMap<>();
            args.put("work_item_id", workItem.getID());
            args.put("context", "");
            McpSchema.GetPromptResult result = mcpClient.getPrompt("task_completion_guide", args);
            if (result == null || result.messages() == null) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            for (McpSchema.PromptMessage msg : result.messages()) {
                McpSchema.Content content = msg.content();
                if (content != null && content instanceof McpSchema.TextContent) {
                    sb.append(((McpSchema.TextContent) content).text()).append("\n");
                }
            }
            return sb.length() > 0 ? sb.toString().trim() : null;
        } catch (Exception e) {
            return null;
        }
    }

    public void close() {
        mcpClient.close();
    }
}
