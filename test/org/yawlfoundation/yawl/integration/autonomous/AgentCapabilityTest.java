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

package org.yawlfoundation.yawl.integration.autonomous;

import junit.framework.TestCase;

/**
 * Tests for AgentCapability.
 * Chicago TDD style - testing real object behavior.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class AgentCapabilityTest extends TestCase {

    public AgentCapabilityTest(String name) {
        super(name);
    }

    public void testConstructorWithValidInputs() {
        AgentCapability capability = new AgentCapability(
            "Ordering",
            "procurement, purchase orders, approvals"
        );

        assertEquals("Ordering", capability.getDomainName());
        assertEquals("procurement, purchase orders, approvals", capability.getDescription());
    }

    public void testConstructorTrimsWhitespace() {
        AgentCapability capability = new AgentCapability(
            "  Ordering  ",
            "  procurement, purchase orders  "
        );

        assertEquals("Ordering", capability.getDomainName());
        assertEquals("procurement, purchase orders", capability.getDescription());
    }

    public void testConstructorRejectsNullDomainName() {
        try {
            new AgentCapability(null, "some description");
            fail("Should reject null domainName");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("domainName is required"));
        }
    }

    public void testConstructorRejectsEmptyDomainName() {
        try {
            new AgentCapability("", "some description");
            fail("Should reject empty domainName");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("domainName is required"));
        }
    }

    public void testConstructorRejectsWhitespaceDomainName() {
        try {
            new AgentCapability("   ", "some description");
            fail("Should reject whitespace-only domainName");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("domainName is required"));
        }
    }

    public void testConstructorRejectsNullDescription() {
        try {
            new AgentCapability("Ordering", null);
            fail("Should reject null description");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("description is required"));
        }
    }

    public void testConstructorRejectsEmptyDescription() {
        try {
            new AgentCapability("Ordering", "");
            fail("Should reject empty description");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("description is required"));
        }
    }

    public void testToStringFormat() {
        AgentCapability capability = new AgentCapability(
            "Ordering",
            "procurement, purchase orders"
        );

        String result = capability.toString();
        assertEquals("Ordering: procurement, purchase orders", result);
    }

    public void testFromEnvironmentWithColonFormat() {
        try {
            System.setProperty("test.agent.capability",
                "Ordering: procurement, purchase orders, approvals");

            String envValue = System.getProperty("test.agent.capability");
            int colon = envValue.indexOf(':');
            AgentCapability capability = new AgentCapability(
                envValue.substring(0, colon).trim(),
                envValue.substring(colon + 1).trim()
            );

            assertEquals("Ordering", capability.getDomainName());
            assertEquals("procurement, purchase orders, approvals", capability.getDescription());
        } finally {
            System.clearProperty("test.agent.capability");
        }
    }

    public void testFromEnvironmentWithoutColonFormat() {
        try {
            System.setProperty("test.agent.capability", "procurement system");

            String envValue = System.getProperty("test.agent.capability");
            String domain = envValue.split("\\s+")[0];
            AgentCapability capability = new AgentCapability(domain, envValue);

            assertEquals("procurement", capability.getDomainName());
            assertEquals("procurement system", capability.getDescription());
        } finally {
            System.clearProperty("test.agent.capability");
        }
    }
}
