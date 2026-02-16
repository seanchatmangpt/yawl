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

package org.yawlfoundation.yawl.procletService.util;

import java.io.Serializable;

/**
 * Immutable entity model identifier (EMID) record.
 * Converted to Java 25 record for improved immutability and type safety.
 *
 * @author YAWL Foundation
 * @author YAWL Foundation (Java 25 conversion)
 * @since 2.0
 * @version 5.2
 *
 * @param emid Entity model identifier value
 */
public record EntityMID(String emid) implements Serializable {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    /**
     * Default constructor with empty emid.
     */
    public EntityMID() {
        this("");
    }

    /**
     * Canonical constructor ensuring non-null emid.
     */
    public EntityMID {
        if (emid == null) {
            emid = "";
        }
    }

    /**
     * Returns the emid value.
     * @deprecated Use emid() accessor instead
     * @return the emid value
     */
    @Deprecated
    public String getValue() {
        return emid;
    }

    /**
     * Creates a new EntityMID with the specified value.
     * @deprecated Use constructor EntityMID(String) instead
     * @param newEmid the new emid value
     */
    @Deprecated
    public void setEmid(String newEmid) {
        throw new UnsupportedOperationException(
            "EntityMID is immutable. Create a new instance: new EntityMID(\"" + newEmid + "\")"
        );
    }

    @Override
    public String toString() {
        return emid;
    }
}
