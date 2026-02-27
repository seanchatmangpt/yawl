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

package org.yawlfoundation.yawl.engine.interfce.interfaceX;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InterfaceX_EngineSideClient with retry and dead letter queue support.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
class InterfaceX_EngineSideClientTest {

    private static final String TEST_OBSERVER_URI = "http://localhost:9999/exception-service";

    @BeforeEach
    void setUp() {
        InterfaceXDeadLetterQueue.shutdownInstance();
    }

    @AfterEach
    void tearDown() {
        InterfaceXDeadLetterQueue.shutdownInstance();
    }

    @Test
    @DisplayName("Should create client with default retry configuration")
    void shouldCreateClientWithDefaultConfiguration() {
        InterfaceX_EngineSideClient client = new InterfaceX_EngineSideClient(TEST_OBSERVER_URI);

        assertEquals(TEST_OBSERVER_URI, client.getURI());
        assertEquals("http", client.getScheme());
    }

    @Test
    @DisplayName("Should create client with custom retry configuration")
    void shouldCreateClientWithCustomRetryConfiguration() {
        int maxAttempts = 5;
        Duration initialWait = Duration.ofMillis(200);

        InterfaceX_EngineSideClient client = new InterfaceX_EngineSideClient(
                TEST_OBSERVER_URI, maxAttempts, initialWait);

        assertEquals(TEST_OBSERVER_URI, client.getURI());
    }

    @Test
    @DisplayName("Should correctly implement equals and hashCode based on URI")
    void shouldImplementEqualsAndHashCode() {
        InterfaceX_EngineSideClient client1 = new InterfaceX_EngineSideClient(TEST_OBSERVER_URI);
        InterfaceX_EngineSideClient client2 = new InterfaceX_EngineSideClient(TEST_OBSERVER_URI);
        InterfaceX_EngineSideClient client3 = new InterfaceX_EngineSideClient("http://localhost:8888/other");

        assertEquals(client1, client2);
        assertEquals(client1.hashCode(), client2.hashCode());
        assertNotEquals(client1, client3);
    }

    @Test
    @DisplayName("Should allow URI modification")
    void shouldAllowUriModification() {
        InterfaceX_EngineSideClient client = new InterfaceX_EngineSideClient(TEST_OBSERVER_URI);

        String newUri = "http://localhost:7777/new-service";
        client.setURI(newUri);

        assertEquals(newUri, client.getURI());
    }

    @Test
    @DisplayName("Should convert command codes to names correctly")
    void shouldConvertCommandCodesToNames() {
        assertEquals("NOTIFY_CHECK_CASE_CONSTRAINTS", InterfaceX_EngineSideClient.getCommandName(0));
        assertEquals("NOTIFY_CHECK_ITEM_CONSTRAINTS", InterfaceX_EngineSideClient.getCommandName(1));
        assertEquals("NOTIFY_WORKITEM_ABORT", InterfaceX_EngineSideClient.getCommandName(2));
        assertEquals("NOTIFY_TIMEOUT", InterfaceX_EngineSideClient.getCommandName(3));
        assertEquals("NOTIFY_RESOURCE_UNAVAILABLE", InterfaceX_EngineSideClient.getCommandName(4));
        assertEquals("NOTIFY_CONSTRAINT_VIOLATION", InterfaceX_EngineSideClient.getCommandName(5));
        assertEquals("NOTIFY_CANCELLED_CASE", InterfaceX_EngineSideClient.getCommandName(6));
        assertEquals("UNKNOWN_COMMAND_99", InterfaceX_EngineSideClient.getCommandName(99));
    }

    @Test
    @DisplayName("Should shutdown executor cleanly")
    void shouldShutdownExecutorCleanly() {
        InterfaceX_EngineSideClient client = new InterfaceX_EngineSideClient(TEST_OBSERVER_URI);

        assertDoesNotThrow(client::shutdown);
    }
}
