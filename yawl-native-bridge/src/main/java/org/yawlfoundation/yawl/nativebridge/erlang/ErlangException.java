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
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */
package org.yawlfoundation.yawl.nativebridge.erlang;

/**
 * Exception hierarchy for Erlang domain errors.
 * All exceptions can be converted to Erlang error terms.
 */
public sealed interface ErlangException extends Exception
    permits ErlangConnectionException, ErlangRpcException,
            ErlangSendException, ErlangReceiveException,
            ErlangExitException, ErlangDecodeException {

    /**
     * Converts this exception to an Erlang error term.
     * Used for propagating errors back to the Erlang domain.
     *
     * @return Erlang term representing this error
     */
    ErlTerm toErlangTerm();
}

/**
 * Exception thrown when connection to Erlang node fails.
 */
public final class ErlangConnectionException extends RuntimeException implements ErlangException {
    private final String target;
    private final int errorCode;

    public ErlangConnectionException(String target, String message) {
        this(target, message, -1);
    }

    public ErlangConnectionException(String target, String message, int errorCode) {
        super(message);
        this.target = target;
        this.errorCode = errorCode;
    }

    public String getTarget() {
        return target;
    }

    public int getErrorCode() {
        return errorCode;
    }

    @Override
    public ErlTerm toErlangTerm() {
        return ErlTerm.ErlTuple.of(
            ErlTerm.ErlAtom.of("badrpc"),
            ErlTerm.ErlTuple.of(
                ErlTerm.ErlAtom.of("noproc"),
                ErlTerm.ErlAtom.of(target)
            )
        );
    }
}

/**
 * Exception thrown when RPC call to Erlang function fails.
 */
public final class ErlangRpcException extends RuntimeException implements ErlangException {
    private final String module;
    private final String function;

    public ErlangRpcException(String module, String function, String message) {
        super(message);
        this.module = module;
        this.function = function;
    }

    public String getModule() {
        return module;
    }

    public String getFunction() {
        return function;
    }

    @Override
    public ErlTerm toErlangTerm() {
        return ErlTerm.ErlTuple.of(
            ErlTerm.ErlAtom.of("badrpc"),
            ErlTerm.ErlAtom.of("RPC call failed: " + getMessage())
        );
    }
}

/**
 * Exception thrown when sending message to Erlang process fails.
 */
public final class ErlangSendException extends RuntimeException implements ErlangException {
    private final String targetProcess;

    public ErlangSendException(String targetProcess, String message) {
        super(message);
        this.targetProcess = targetProcess;
    }

    public String getTargetProcess() {
        return targetProcess;
    }

    @Override
    public ErlTerm toErlangTerm() {
        return ErlTerm.ErlTuple.of(
            ErlTerm.ErlAtom.of("badsend"),
            ErlTerm.ErlAtom.of(targetProcess)
        );
    }
}

/**
 * Exception thrown when receiving message from Erlang fails.
 */
public final class ErlangReceiveException extends RuntimeException implements ErlangException {
    public ErlangReceiveException(String message) {
        super(message);
    }

    public ErlangReceiveException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public ErlTerm toErlangTerm() {
        return ErlTerm.ErlTuple.of(
            ErlTerm.ErlAtom.of("badreceive"),
            ErlTerm.ErlAtom.of(getMessage())
        );
    }
}

/**
 * Exception thrown when Erlang process exits with an error.
 */
public final class ErlangExitException extends RuntimeException implements ErlangException {
    private final ErlTerm reason;

    public ErlangExitException(ErlTerm reason) {
        super("Erlang exit: " + reason);
        this.reason = reason;
    }

    public ErlExitException(String reason) {
        super("Erlang exit: " + reason);
        this.reason = ErlTerm.ErlAtom.of(reason);
    }

    public ErlTerm getReason() {
        return reason;
    }

    @Override
    public ErlTerm toErlangTerm() {
        return ErlTerm.ErlTuple.of(
            ErlTerm.ErlAtom.of("badrpc"),
            ErlTerm.ErlTuple.of(
                ErlTerm.ErlAtom.of("EXIT"),
                reason
            )
        );
    }
}

/**
 * Exception thrown when decoding Erlang External Term Format fails.
 */
public final class ErlangDecodeException extends RuntimeException implements ErlangException {
    private final byte[] invalidData;

    public ErlangDecodeException(String message, byte[] invalidData) {
        super(message);
        this.invalidData = invalidData.clone();
    }

    public ErlangDecodeException(String message, byte[] invalidData, Throwable cause) {
        super(message, cause);
        this.invalidData = invalidData.clone();
    }

    public byte[] getInvalidData() {
        return invalidData.clone();
    }

    @Override
    public ErlTerm toErlangTerm() {
        return ErlTerm.ErlTuple.of(
            ErlTerm.ErlAtom.of("badarg"),
            ErlTerm.ErlBinary.of(invalidData)
        );
    }
}