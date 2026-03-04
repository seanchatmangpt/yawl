/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.nativebridge.erlang;

/**
 * Stub interface for Erlang terms.
 *
 * This interface requires jextract-generated native code to function properly.
 */
public interface ErlTerm {

    /**
     * Encodes this term to an Erlang buffer.
     *
     * @param buffer The buffer to encode to
     * @return 0 on success, -1 on failure
     * @throws ErlangException if encoding fails
     */
    int encodeTo(Object buffer) throws ErlangException;

    /**
     * Gets the string representation of this term.
     *
     * @return The string representation
     */
    String toString();
}