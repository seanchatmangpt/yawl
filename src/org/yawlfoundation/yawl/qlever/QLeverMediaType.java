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

package org.yawlfoundation.yawl.qlever;

import java.util.Map;
import java.util.Optional;

/**
 * Media types supported by QLever for query result formatting.
 */
public enum QLeverMediaType {
    /** SPARQL JSON results format */
    JSON("application/sparql-results+json"),

    /** SPARQL XML results format */
    XML("application/sparql-results+xml"),

    /** Turtle (RDF) format */
    TURTLE("text/turtle"),

    /** Tab-Separated Values format */
    TSV("text/tab-separated-values"),

    /** Comma-Separated Values format */
    CSV("text/csv"),

    /** N-Triples (RDF) format */
    N_TRIPLES("application/n-triples"),

    /** RDF/XML format */
    RDF_XML("application/rdf+xml");

    private final String headerValue;

    QLeverMediaType(String headerValue) {
        this.headerValue = headerValue;
    }

    /**
     * Returns the HTTP Accept header value for this media type.
     */
    public String headerValue() {
        return headerValue;
    }

    /**
     * Returns the media type for the given header value, case-insensitive.
     */
    public static Optional<QLeverMediaType> fromHeader(String header) {
        if (header == null) {
            return Optional.empty();
        }

        for (QLeverMediaType type : values()) {
            if (header.equalsIgnoreCase(type.headerValue)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}