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

/**
 * Domain capability description for an autonomous agent.
 *
 * <p>Agents use this (with reasoning engines) to determine eligibility for work items.
 * The capability is a natural-language domain description that the agent reasons over.</p>
 *
 * <p>Converted to Java 25 record for immutability, automatic equals/hashCode, and
 * reduced boilerplate (eliminated 30+ lines of getters/constructor/equals/hashCode).</p>
 *
 * @param domainName short name (e.g. "Ordering", "Carrier")
 * @param description domain description for reasoning (e.g. "procurement, purchase orders")
 * @author YAWL Foundation
 * @version 5.2
 */
public record AgentCapability(String domainName, String description) {

    /**
     * Canonical constructor with validation.
     */
    public AgentCapability {
        if (domainName == null || domainName.trim().isEmpty()) {
            throw new IllegalArgumentException("domainName is required");
        }
        if (description == null || description.trim().isEmpty()) {
            throw new IllegalArgumentException("description is required");
        }
        domainName = domainName.trim();
        description = description.trim();
    }

    /**
     * Create from environment variable AGENT_CAPABILITY.
     * Format: "DomainName: description text" or just "description text" (domain inferred).
     */
    public static AgentCapability fromEnvironment() {
        String raw = System.getenv("AGENT_CAPABILITY");
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalStateException(
                "AGENT_CAPABILITY environment variable is required.\n" +
                "Example: AGENT_CAPABILITY=\"Ordering: procurement, purchase orders, approvals\"");
        }
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

    @Override
    public String toString() {
        return domainName + ": " + description;
    }
}
