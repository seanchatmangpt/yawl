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
 * Exception thrown when Erlang operations fail.
 */
public class ErlangException extends Exception {

    public ErlangException(String message) {
        super(message);
    }

    public ErlangException(String message, Throwable cause) {
        super(message, cause);
    }

    public static ErlangException connectionFailed(String nodeName) {
        return new ErlangException("Failed to connect to Erlang node: " + nodeName);
    }

    public static ErlangException encodingFailed(ErlTerm term) {
        return new ErlangException("Failed to encode Erlang term: " + term);
    }

    public static ErlangException decodingFailed(String type) {
        return new ErlangException("Failed to decode Erlang term of type: " + type);
    }

    public static ErlangException rpcFailed(String module, String function, String details) {
        return new ErlangException("RPC call failed: " + module + ":" + function + " - " + details);
    }
}