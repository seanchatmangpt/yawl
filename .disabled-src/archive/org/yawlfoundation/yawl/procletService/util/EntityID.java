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
 * Immutable entity identifier record combining model ID and specific ID.
 * Converted to Java 25 record for improved immutability and type safety.
 *
 * @author YAWL Foundation
 * @author YAWL Foundation (Java 25 conversion)
 * @since 2.0
 * @version 5.2
 *
 * @param emid Entity model identifier
 * @param esid Entity specific identifier
 */
public record EntityID(EntityMID emid, EntitySID esid) implements Serializable {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    /**
     * Constructor from string values.
     * @param emidValue Entity model identifier string
     * @param esidValue Entity specific identifier string
     */
    public EntityID(String emidValue, String esidValue) {
        this(new EntityMID(emidValue), new EntitySID(esidValue));
    }

    /**
     * Canonical constructor ensuring non-null components.
     */
    public EntityID {
        if (emid == null) {
            emid = new EntityMID();
        }
        if (esid == null) {
            esid = new EntitySID();
        }
    }

    /**
     * Returns the entity model identifier.
     * @deprecated Use emid() accessor instead
     * @return the entity model identifier
     */
    @Deprecated
    public EntityMID getEmid() {
        return emid;
    }

    /**
     * Returns the entity specific identifier.
     * @deprecated Use esid() accessor instead
     * @return the entity specific identifier
     */
    @Deprecated
    public EntitySID getEsid() {
        return esid;
    }

    @Override
    public String toString() {
        return emid + "," + esid;
    }
}
