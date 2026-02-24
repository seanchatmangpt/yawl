/*
 * Copyright 2026 YAWL Foundation
 */
package org.yawlfoundation.yawl.mcp.a2a.gregverse.ai.model;

import java.util.Objects;

/**
 * Contact information record for emergency contacts.
 *
 * @param name contact name
 * @param relationship relationship to patient
 * @param phoneNumber phone number
 * @param email email address (optional)
 */
public record ContactInfo(
    String name,
    String relationship,
    String phoneNumber,
    String email
) {
    public ContactInfo {
        Objects.requireNonNull(name, "Name cannot be null");
        Objects.requireNonNull(phoneNumber, "Phone number cannot be null");
    }

    public ContactInfo(String name, String relationship, String phoneNumber) {
        this(name, relationship, phoneNumber, null);
    }
}
