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
package org.yawlfoundation.yawl.erlang.error;

/**
 * Thrown when an Erlang RPC call returns {@code {badrpc, Reason}} or fails at the
 * erl_interface layer (timeout, disconnection, bad argument encoding).
 *
 * <p>Remediation: check the module/function exists on the remote node, verify
 * argument types match the function spec, and confirm the connection is alive.</p>
 */
public class ErlangRpcException extends ErlangException {

    private static final long serialVersionUID = 1L;

    private final String module;
    private final String function;

    /**
     * @param module   Erlang module name (e.g. {@code "erlang"})
     * @param function Erlang function name (e.g. {@code "node"})
     * @param reason   failure reason (e.g. {@code "{badrpc,noconnection}"})
     */
    public ErlangRpcException(String module, String function, String reason) {
        super("RPC " + module + ":" + function + "/? failed: " + reason);
        this.module = module;
        this.function = function;
    }

    /**
     * @param module   Erlang module name
     * @param function Erlang function name
     * @param reason   failure reason
     * @param cause    underlying erl_interface or decode error
     */
    public ErlangRpcException(String module, String function, String reason, Throwable cause) {
        super("RPC " + module + ":" + function + "/? failed: " + reason, cause);
        this.module = module;
        this.function = function;
    }

    /** Returns the Erlang module that was the target of the failed RPC. */
    public String getModule() { return module; }

    /** Returns the Erlang function that was the target of the failed RPC. */
    public String getFunction() { return function; }
}
