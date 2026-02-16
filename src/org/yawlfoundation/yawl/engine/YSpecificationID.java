/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.engine;

import org.yawlfoundation.yawl.elements.YSpecVersion;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.util.StringUtil;
import org.yawlfoundation.yawl.util.XNode;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The unique identifier of a specification.
 * <p/>
 * NOTE: For schema versions prior to 2.0, the spec's uri was used as its identifier, but
 * since a user-defined uri cannot guarantee uniqueness, the identifier field was
 * introduced for v2.0 (which will theoretically always be unique). Specification
 * versioning was also introduced in v2.0. Therefore, to handle specifications of
 * different schema versions:
 * - all pre-2.0 schema based specifications are given a default version of '0.1'
 * - all pre-2.0 schema based specifications will have a null 'identifier' field
 * and the 'uri' field will be used to 'uniquely' identify the specification
 * - all 2.0 and later schema based specifications will use the 'identifier' field
 * to uniquely identify a specification-version family (all versions of a
 * specification have the same identifier).
 * <p/>
 * The getKey method is used to determine which of 'identifier' or 'uri' is used as
 * the unique identifier.
 *
 * @author Mike Fowler
 *         Date: 05-Sep-2006
 * @author Michael Adams 08-09 heavily modified for versions 2.0 - 2.1
 * @author Claude Code 2026-02 converted to Java 25 record
 */

public record YSpecificationID(
        String identifier,
        YSpecVersion version,
        String uri
) implements Comparable<YSpecificationID> {

    /**
     * Compact constructor with validation and normalization.
     * Ensures version is never null (defaults to "0.1" for pre-2.0 specs).
     */
    public YSpecificationID {
        version = (version != null) ? version : new YSpecVersion("0.1");
    }

    /**
     * Constructor with String version.
     */
    public YSpecificationID(String identifier, String version, String uri) {
        this(identifier, new YSpecVersion(version), uri);
    }

    /**
     * Constructor from WorkItemRecord.
     */
    public YSpecificationID(WorkItemRecord wir) {
        this(wir.getSpecIdentifier(), wir.getSpecVersion(), wir.getSpecURI());
    }

    /**
     * Default constructor for pre-2.0 specs (uri-based identification).
     */
    public YSpecificationID(String uri) {
        this(null, new YSpecVersion("0.1"), uri);
    }

    /**
     * Constructor from XNode.
     */
    public YSpecificationID(XNode node) {
        this(fromXNode(node));
    }

    /**
     * Copy constructor for creating modified instances.
     */
    private YSpecificationID(YSpecificationID other) {
        this(other.identifier, other.version, other.uri);
    }

    /**
     * Factory method to create from XNode.
     */
    public static YSpecificationID fromXNode(XNode node) {
        String identifier = node.getChildText("identifier");
        YSpecVersion version = new YSpecVersion(node.getChildText("version"));
        String uri = node.getChildText("uri");
        return new YSpecificationID(identifier, version, uri);
    }

    /**
     * Factory method to parse from full string format.
     * Format: "identifier:version:uri" or "uri" for pre-2.0 specs.
     */
    public static YSpecificationID fromFullString(String s) throws IllegalArgumentException {
        String[] parts = s.split(":");
        if (parts.length == 1) {            // pre release 2: uri only
            return new YSpecificationID(null, new YSpecVersion("0.1"), parts[0]);
        }
        else if (parts.length == 3) {
            String identifier = !StringUtil.isNullOrEmpty(parts[0]) ? parts[0] : null;
            YSpecVersion version = new YSpecVersion(parts[1]);
            String uri = parts[2];
            return new YSpecificationID(identifier, version, uri);
        }
        else {
            throw new IllegalArgumentException("Invalid specification ID string: " + s);
        }
    }

    /**
     * Get version as string.
     */
    public String getVersionAsString() {
        return version.toString();
    }

    /**
     * Get the key used for unique identification.
     * Returns identifier if non-null, otherwise uri (for pre-2.0 specs).
     */
    public String getKey() {
        return (identifier != null) ? identifier : uri;
    }

    /**
     * Create a new instance with updated identifier.
     */
    public YSpecificationID withIdentifier(String identifier) {
        return new YSpecificationID(identifier, this.version, this.uri);
    }

    /**
     * Create a new instance with updated version.
     */
    public YSpecificationID withVersion(String version) {
        return new YSpecificationID(this.identifier, new YSpecVersion(version), this.uri);
    }

    /**
     * Create a new instance with updated version.
     */
    public YSpecificationID withVersion(YSpecVersion version) {
        return new YSpecificationID(this.identifier, version, this.uri);
    }

    /**
     * Create a new instance with updated uri.
     */
    public YSpecificationID withUri(String uri) {
        return new YSpecificationID(this.identifier, this.version, uri);
    }

    /**
     * Validate specification ID.
     * Only 2.0 or later ids (with non-null identifier) can have a version other than 0.1.
     */
    public boolean isValid() {
        return (identifier != null) || version.getVersion().equals("0.1");
    }

    /**
     * Check if this is a previous version of another specification.
     */
    public boolean isPreviousVersionOf(YSpecificationID other) {
        return hasMatchingIdentifier(other) && (version.compareTo(other.version()) < 0);
    }

    /**
     * Check if this has the same identifier as another specification.
     * A null identifier means pre-2.0, which only have one version.
     */
    public boolean hasMatchingIdentifier(YSpecificationID other) {
        return (identifier != null) && identifier.equals(other.identifier());
    }

    /**
     * Custom equals implementation for backward compatibility.
     * Handles pre-2.0 specs (null identifier) specially.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof YSpecificationID other)) return false;

        // Both pre-2.0 (null identifiers) - compare uri and version
        if (other.identifier() == null && identifier == null) {
            return Objects.equals(uri, other.uri()) &&
                   Objects.equals(version, other.version());
        }

        // Post-2.0 - compare identifier and version
        return Objects.equals(identifier, other.identifier()) &&
               Objects.equals(version, other.version());
    }

    /**
     * Custom hashCode implementation for backward compatibility.
     */
    @Override
    public int hashCode() {
        String key = getKey();
        int subCode = key != null ? key.hashCode() : 31;
        return 17 * subCode * version.hashCode();
    }

    @Override
    public String toString() {
        return uri + " - version " + version.toString();
    }

    public String toKeyString() {
        return getKey() + ":" + version.toString();
    }

    public String toFullString() {
        String id = identifier != null ? identifier : "";
        return "%s:%s:%s".formatted(id, getVersionAsString(), uri);
    }

    @Override
    public int compareTo(YSpecificationID other) {
        String key = getKey();
        String otherKey = other.getKey();
        if (key == null) {
            return -1;
        } else if (otherKey == null) {
            return 1;
        } else if (key.equals(otherKey)) {
            return version.compareTo(other.version());
        } else return otherKey.compareTo(key);
    }

    /**
     * Utility method for bundling up specIDs for passing across the interfaces.
     */
    public Map<String, String> toMap() {
        Map<String, String> result = new HashMap<>();
        if (identifier != null) result.put("specidentifier", identifier);
        result.put("specversion", version.getVersion());
        result.put("specuri", uri);
        return result;
    }

    /**
     * Convert to XNode representation.
     */
    public XNode toXNode() {
        XNode node = new XNode("specificationid");
        if (identifier != null) {
            node.addChild("identifier", identifier);
        }
        node.addChild("version", version.getVersion());
        node.addChild("uri", uri);
        return node;
    }

    /**
     * Convert to XML string.
     */
    public String toXML() {
        return toXNode().toPrettyString();
    }

    /**
     * Accessor methods for backward compatibility with getter naming convention.
     * Records generate accessors without "get" prefix, but legacy code may expect them.
     */
    public String getIdentifier() { return identifier; }
    public YSpecVersion getVersion() { return version; }
    public String getUri() { return uri; }
}
