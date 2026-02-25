/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.mcp.a2a.controller;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.a2a.spec.AgentCard;

import org.yawlfoundation.yawl.mcp.a2a.a2a.YawlA2AAgentCard;

/**
 * REST controller exposing application status and agent card endpoints.
 *
 * <p>Provides operational visibility into the YAWL MCP-A2A service for
 * monitoring, load balancers, and AI agent discovery.</p>
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>{@code GET /api/v1/status} - Combined MCP/A2A service status</li>
 *   <li>{@code GET /api/v1/agent} - A2A agent card and capabilities</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(prefix = "yawl.a2a", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AppStatusController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AppStatusController.class);

    @Autowired
    private YawlA2AAgentCard agentCard;

    @Value("${yawl.engine.url:http://localhost:8080/yawl}")
    private String engineUrl;

    @Value("${yawl.mcp.transport:http}")
    private String mcpTransport;

    @Value("${yawl.mcp.http.enabled:true}")
    private boolean mcpHttpEnabled;

    @Value("${yawl.a2a.transport.rest.enabled:true}")
    private boolean a2aRestEnabled;

    @Value("${yawl.a2a.transport.rest.port:8082}")
    private int a2aRestPort;

    /**
     * Returns the combined MCP and A2A service status.
     *
     * @return status map with service metadata, transport modes, and engine config
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        LOGGER.debug("Status endpoint called");

        Map<String, Object> mcpInfo = new LinkedHashMap<>();
        mcpInfo.put("transport", mcpTransport);
        mcpInfo.put("httpEnabled", mcpHttpEnabled);
        mcpInfo.put("endpoints", Map.of(
            "sse", "/mcp/sse",
            "message", "/mcp/message",
            "health", "/mcp/health"
        ));

        Map<String, Object> a2aInfo = new LinkedHashMap<>();
        a2aInfo.put("restEnabled", a2aRestEnabled);
        a2aInfo.put("restPort", a2aRestPort);
        a2aInfo.put("agentName", agentCard.getAgentName());
        a2aInfo.put("skillCount", agentCard.getAgentCard().skills().size());
        a2aInfo.put("endpoints", Map.of(
            "agentCard", "/.well-known/agent.json",
            "tasks", "/a2a"
        ));

        Map<String, Object> engineInfo = new LinkedHashMap<>();
        engineInfo.put("url", engineUrl);
        engineInfo.put("interfaceB", engineUrl + "/ib");
        engineInfo.put("interfaceA", engineUrl + "/ia");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("service", "yawl-mcp-a2a-app");
        response.put("version", "6.0.0");
        response.put("status", "UP");
        response.put("mcp", mcpInfo);
        response.put("a2a", a2aInfo);
        response.put("engine", engineInfo);
        response.put("timestamp", Instant.now().toString());

        return ResponseEntity.ok(response);
    }

    /**
     * Returns the A2A agent card describing this agent's capabilities and skills.
     *
     * @return the A2A AgentCard
     */
    @GetMapping("/agent")
    public ResponseEntity<AgentCard> agent() {
        LOGGER.debug("Agent card endpoint called");
        return ResponseEntity.ok(agentCard.getAgentCard());
    }
}
