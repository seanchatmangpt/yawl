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
 * Immutable entity specific identifier (ESID) record.
 * Converted to Java 25 record for improved immutability and type safety.
 *
 * @author YAWL Foundation
 * @author YAWL Foundation (Java 25 conversion)
 * @since 2.0
 * @version 5.2
 *
 * @param esid Entity specific identifier value
 */
public record EntitySID(String esid) implements Serializable {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    /**
     * Default constructor with empty esid.
     */
    public EntitySID() {
        this("");
    }

    /**
     * Canonical constructor ensuring non-null esid.
     */
    public EntitySID {
        if (esid == null) {
            esid = "";
        }
    }

    /**
     * Returns the esid value.
     * @deprecated Use esid() accessor instead
     * @return the esid value
     */
    @Deprecated
    public String getValue() {
        return esid;
    }

    /**
     * Creates a new EntitySID with the specified value.
     * @deprecated Use constructor EntitySID(String) instead
     * @param newEsid the new esid value
     */
    @Deprecated
    public void setEsid(String newEsid) {
        throw new UnsupportedOperationException(
            "EntitySID is immutable. Create a new instance: new EntitySID(\"" + newEsid + "\")"
        );
    }

    @Override
    public String toString() {
        return esid;
    }
}
