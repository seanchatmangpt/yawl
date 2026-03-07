/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.mcp.a2a.demo.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.yawlfoundation.yawl.mcp.a2a.demo.PatternDemoRunner;

import java.util.Map;

/**
 * REST controller for demo health status endpoint.
 */
@RestController
@RequestMapping("/demo")
public class PatternHealthController {

    private final PatternDemoRunner patternRunner;

    public PatternHealthController(PatternDemoRunner patternRunner) {
        this.patternRunner = patternRunner;
    }

    /**
     * Get the current health status of the demo runner.
     *
     * @return health status response
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> healthStatus = patternRunner.getHealthStatus();

        // Determine HTTP status based on health status
        String status = (String) healthStatus.get("status");
        HttpStatus httpStatus;

        switch (status) {
            case "HEALTHY":
            case "INITIALIZING":
                httpStatus = HttpStatus.OK;
                break;
            case "DEGRADED":
                httpStatus = HttpStatus.SERVICE_UNAVAILABLE;
                break;
            case "SHUTTING_DOWN":
                httpStatus = HttpStatus.OK;
                break;
            default:
                httpStatus = HttpStatus.SERVICE_UNAVAILABLE;
        }

        return ResponseEntity.status(httpStatus).body(healthStatus);
    }
}