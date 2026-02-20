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

package org.yawlfoundation.yawl.stateless.monitor;

import java.time.Instant;

/**
 * Immutable snapshot of a stateless YAWL case at a specific point in time.
 *
 * <p>CaseSnapshot is a Java 25 record that captures the marshalled XML state of a case
 * together with its identifying metadata. It is the canonical value type returned by
 * {@link YCaseExporter} and consumed by {@link YCaseImporter}.</p>
 *
 * <h2>Immutability guarantee</h2>
 * <p>All fields are value types (String, Instant). The marshalled XML is a defensive
 * copy taken at snapshot time. Once constructed the record cannot be mutated — any
 * update produces a new record via the {@code with*} factory helpers.</p>
 *
 * <h2>Serialisation</h2>
 * <p>CaseSnapshot instances are safe to share across virtual-thread boundaries without
 * synchronisation. They may be placed in {@code ConcurrentHashMap} or passed as
 * {@link java.lang.ScopedValue} payloads.</p>
 *
 * @param caseID         the string representation of the YAWL case identifier
 * @param specID         the string representation of the specification identifier
 * @param marshalledXML  the full XML encoding of the case state, as produced by YCaseExporter
 * @param capturedAt     the instant at which the snapshot was taken
 *
 * @author YAWL Foundation
 * @see YCaseExporter
 * @see YCaseImporter
 */
public record CaseSnapshot(
        String caseID,
        String specID,
        String marshalledXML,
        Instant capturedAt
) {

    /**
     * Compact canonical constructor — validates required fields.
     *
     * @throws IllegalArgumentException if any identifier or XML is null or blank
     */
    public CaseSnapshot {
        if (caseID == null || caseID.isBlank()) {
            throw new IllegalArgumentException("CaseSnapshot: caseID must not be null or blank");
        }
        if (specID == null || specID.isBlank()) {
            throw new IllegalArgumentException("CaseSnapshot: specID must not be null or blank");
        }
        if (marshalledXML == null || marshalledXML.isBlank()) {
            throw new IllegalArgumentException("CaseSnapshot: marshalledXML must not be null or blank");
        }
        if (capturedAt == null) {
            throw new IllegalArgumentException("CaseSnapshot: capturedAt must not be null");
        }
    }

    /**
     * Convenience factory — captures the snapshot at the current instant.
     *
     * @param caseID        string form of the case identifier
     * @param specID        string form of the specification identifier
     * @param marshalledXML the full XML encoding of the case state
     * @return a new snapshot stamped with {@code Instant.now()}
     */
    public static CaseSnapshot of(String caseID, String specID, String marshalledXML) {
        return new CaseSnapshot(caseID, specID, marshalledXML, Instant.now());
    }

    /**
     * Returns the age of this snapshot in milliseconds relative to the current time.
     *
     * @return milliseconds elapsed since {@code capturedAt}
     */
    public long ageMillis() {
        return Instant.now().toEpochMilli() - capturedAt.toEpochMilli();
    }

    /**
     * Returns a short description suitable for log messages.
     *
     * @return {@code "CaseSnapshot[case=<caseID>, spec=<specID>, capturedAt=<capturedAt>]"}
     */
    @Override
    public String toString() {
        return "CaseSnapshot[case=" + caseID + ", spec=" + specID + ", capturedAt=" + capturedAt + "]";
    }
}
