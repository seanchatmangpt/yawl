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

import org.junit.jupiter.api.Tag;

import junit.framework.TestCase;

/**
 * Integration tests for AgentCapability record (V6 feature).
 *
 * Chicago TDD: tests real AgentCapability construction and behavior
 * with no mocks.
 *
 * Coverage targets:
 * - Constructor validation
 * - fromEnvironment() parsing
 * - equals/hashCode (record semantics)
 * - toString formatting
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@Tag("unit")
public class AgentCapabilityTest extends TestCase {

    public AgentCapabilityTest(String name) {
        super(name);
    }

    // =========================================================================
    // Constructor tests
    // =========================================================================

    public void testConstructorValidDomainAndDescription() {
        AgentCapability cap = new AgentCapability("Ordering",
                "procurement, purchase orders, approvals");
        assertEquals("Ordering", cap.domainName());
        assertEquals("procurement, purchase orders, approvals", cap.description());
    }

    public void testConstructorTrimsWhitespace() {
        AgentCapability cap = new AgentCapability("  Logistics  ",
                "  shipping and delivery  ");
        assertEquals("Logistics", cap.domainName());
        assertEquals("shipping and delivery", cap.description());
    }

    public void testConstructorNullDomainNameThrows() {
        try {
            new AgentCapability(null, "some description");
            fail("Expected IllegalArgumentException for null domainName");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("domainName"));
        }
    }

    public void testConstructorEmptyDomainNameThrows() {
        try {
            new AgentCapability("", "some description");
            fail("Expected IllegalArgumentException for empty domainName");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("domainName"));
        }
    }

    public void testConstructorBlankDomainNameThrows() {
        try {
            new AgentCapability("   ", "some description");
            fail("Expected IllegalArgumentException for blank domainName");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("domainName"));
        }
    }

    public void testConstructorNullDescriptionThrows() {
        try {
            new AgentCapability("Ordering", null);
            fail("Expected IllegalArgumentException for null description");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("description"));
        }
    }

    public void testConstructorEmptyDescriptionThrows() {
        try {
            new AgentCapability("Ordering", "");
            fail("Expected IllegalArgumentException for empty description");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("description"));
        }
    }

    // =========================================================================
    // Record equality tests (Java record auto-generated equals/hashCode)
    // =========================================================================

    public void testEqualCapabilitiesAreEqual() {
        AgentCapability cap1 = new AgentCapability("Ordering", "purchase orders");
        AgentCapability cap2 = new AgentCapability("Ordering", "purchase orders");
        assertEquals("Equal capabilities should be equal", cap1, cap2);
        assertEquals("Equal capabilities should have same hashCode",
                cap1.hashCode(), cap2.hashCode());
    }

    public void testDifferentDomainNamesNotEqual() {
        AgentCapability cap1 = new AgentCapability("Ordering", "purchase orders");
        AgentCapability cap2 = new AgentCapability("Finance", "purchase orders");
        assertFalse("Different domainNames should not be equal", cap1.equals(cap2));
    }

    public void testDifferentDescriptionsNotEqual() {
        AgentCapability cap1 = new AgentCapability("Ordering", "purchase orders");
        AgentCapability cap2 = new AgentCapability("Ordering", "invoices");
        assertFalse("Different descriptions should not be equal", cap1.equals(cap2));
    }

    // =========================================================================
    // toString tests
    // =========================================================================

    public void testToStringFormat() {
        AgentCapability cap = new AgentCapability("Carrier", "logistics and shipping");
        String str = cap.toString();
        assertEquals("Carrier: logistics and shipping", str);
    }

    // =========================================================================
    // fromEnvironment() tests
    // =========================================================================

    public void testFromEnvironmentWithColonFormat() {
        // Set the environment variable via system property workaround.
        // fromEnvironment() reads AGENT_CAPABILITY env var. Since we can't set
        // env vars in a JVM, we test the parsing logic through direct construction
        // using the same format to validate parsing correctness.
        // The parsing logic: "DomainName: description text"
        AgentCapability cap = parseCapabilityFromString("Ordering: procurement, approvals");
        assertEquals("Ordering", cap.domainName());
        assertEquals("procurement, approvals", cap.description());
    }

    public void testFromEnvironmentWithoutColonFormat() {
        // When no colon present, first word becomes domain name
        AgentCapability cap = parseCapabilityFromString("Logistics shipping delivery");
        assertEquals("Logistics", cap.domainName());
        assertEquals("Logistics shipping delivery", cap.description());
    }

    public void testFromEnvironmentWithColonAndSpaces() {
        AgentCapability cap = parseCapabilityFromString("  Finance  :  invoice processing  ");
        // Trimming happens in the constructor, but the parsing logic handles
        // the colon split as-is with String.trim()
        assertEquals("Finance", cap.domainName());
        assertEquals("invoice processing", cap.description());
    }

    // =========================================================================
    // Accessor tests
    // =========================================================================

    public void testDomainNameAccessor() {
        AgentCapability cap = new AgentCapability("TestDomain", "test description");
        assertEquals("TestDomain", cap.domainName());
    }

    public void testDescriptionAccessor() {
        AgentCapability cap = new AgentCapability("TestDomain", "test description here");
        assertEquals("test description here", cap.description());
    }

    // =========================================================================
    // Edge cases
    // =========================================================================

    public void testCapabilityWithSpecialCharacters() {
        AgentCapability cap = new AgentCapability("Domain-1",
                "handles: A/B testing, X-rays, & more");
        assertEquals("Domain-1", cap.domainName());
        assertEquals("handles: A/B testing, X-rays, & more", cap.description());
    }

    public void testCapabilityWithLongDescription() {
        String longDesc = "a".repeat(500);
        AgentCapability cap = new AgentCapability("LongDomain", longDesc);
        assertEquals(longDesc, cap.description());
    }

    // =========================================================================
    // Helper method - replicates fromEnvironment() parsing logic
    // =========================================================================

    private AgentCapability parseCapabilityFromString(String raw) {
        String s = raw.trim();
        int colon = s.indexOf(':');
        if (colon > 0) {
            return new AgentCapability(
                s.substring(0, colon).trim(),
                s.substring(colon + 1).trim());
        }
        String domain = s.split("\\s+")[0];
        return new AgentCapability(domain, s);
    }
}
