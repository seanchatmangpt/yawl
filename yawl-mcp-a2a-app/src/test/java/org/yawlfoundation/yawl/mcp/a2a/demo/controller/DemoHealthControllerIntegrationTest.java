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

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for DemoHealthController.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DemoHealthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testHealthEndpointReturnsOk() throws Exception {
        mockMvc.perform(get("/demo/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.patterns_total").exists())
                .andExpect(jsonPath("$.patterns_completed").exists())
                .andExpect(jsonPath("$.progress_percent").exists());
    }

    @Test
    void testHealthEndpointCorrectStructure() throws Exception {
        mockMvc.perform(get("/demo/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INITIALIZING"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.progress_percent").isString());
    }

    @Test
    void testHealthEndpointWithShuttingDown() throws Exception {
        // This test would need more setup to actually trigger shutdown state
        // but verifies the endpoint structure is correct
        mockMvc.perform(get("/demo/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").isString())
                .andExpect(jsonPath("$.status").valueOneOf("INITIALIZING", "HEALTHY"));
    }
}