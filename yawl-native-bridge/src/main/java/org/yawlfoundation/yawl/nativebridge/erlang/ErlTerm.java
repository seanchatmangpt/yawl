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
 * Sealed interface for Erlang External Term Format (ETF) types.
 * All 13 Erlang term types that appear over the distribution protocol are represented.
 *
 * <p>Use exhaustive pattern matching to process all term types:
 * <pre>
 *   switch (term) {
 *       case ErlAtom(var value) -> ...
 *       case ErlLong(var value) -> ...
 *       case ErlFloat(var value) -> ...
 *       case ErlBinary(var data) -> ...
 *       case ErlBitstring(var data, var bits) -> ...
 *       case ErlNil() -> ...
 *       case ErlList(var elements, var tail) -> ...
 *       case ErlTuple(var elements) -> ...
 *       case ErlMap(var entries) -> ...
 *       case ErlPid(var node, var id, var serial, var creation) -> ...
 *       case ErlRef(var node, var ids, var creation) -> ...
 *       case ErlPort(var node, var id, var creation) -> ...
 *       case ErlFun fun -> ... // ExternalFun or Closure
 *   }
 * </pre>
 *
 * <p>All record types are immutable and support pattern destructuring.</p>
 */
public sealed interface ErlTerm
    permits ErlAtom, ErlLong, ErlFloat, ErlBinary, ErlBitstring,
            ErlNil, ErlList, ErlTuple, ErlMap,
            ErlPid, ErlRef, ErlPort, ErlFun {

    /**
     * Encodes this term to Erlang External Term Format (ETF) bytes.
     *
     * @return ETF-encoded byte array
     */
    byte[] encode();

    /**
     * Decodes Erlang External Term Format (ETF) bytes to the appropriate ErlTerm.
     *
     * @param bytes ETF-encoded byte array
     * @return decoded ErlTerm
     * @throws ErlangDecodeException if decoding fails
     */
    static ErlTerm decode(byte[] bytes) throws ErlangDecodeException {
        return ErlTermCodec.decode(bytes);
    }

    /**
     * Creates a nil/empty list.
     */
    static ErlTerm Nil() {
        return ErlNil.INSTANCE;
    }
}

/**
 * Represents an Erlang atom.
 */
public final record ErlAtom(String value) implements ErlTerm {
    public ErlAtom {
        if (value == null) {
            throw new IllegalArgumentException("Atom value cannot be null");
        }
    }

    @Override
    public byte[] encode() {
        return ErlTermCodec.encodeAtom(this);
    }
}

/**
 * Represents an Erlang integer.
 */
public final record ErlLong(long value) implements ErlTerm {
    @Override
    public byte[] encode() {
        return ErlTermCodec.encodeLong(this);
    }
}

/**
 * Represents an Erlang float.
 */
public final record ErlFloat(double value) implements ErlTerm {
    @Override
    public byte[] encode() {
        return ErlTermCodec.encodeFloat(this);
    }
}

/**
 * Represents an Erlang binary (byte array).
 */
public final record ErlBinary(byte[] data) implements ErlTerm {
    public ErlBinary {
        if (data == null) {
            throw new IllegalArgumentException("Binary data cannot be null");
        }
    }

    @Override
    public byte[] encode() {
        return ErlTermCodec.encodeBinary(this);
    }
}

/**
 * Represents an Erlang bitstring (binary with bit length).
 */
public final record ErlBitstring(byte[] data, int bits) implements ErlTerm {
    public ErlBitstring {
        if (data == null) {
            throw new IllegalArgumentException("Bitstring data cannot be null");
        }
        if (bits < 0 || bits > data.length * 8) {
            throw new IllegalArgumentException("Invalid bit length");
        }
    }

    @Override
    public byte[] encode() {
        return ErlTermCodec.encodeBitstring(this);
    }
}

/**
 * Represents an empty Erlang list.
 */
public enum ErlNil implements ErlTerm {
    INSTANCE;

    @Override
    public byte[] encode() {
        return ErlTermCodec.encodeNil();
    }
}

/**
 * Represents an Erlant list (head-tail or proper list).
 */
public final record ErlList(List<ErlTerm> elements, ErlTerm tail) implements ErlTerm {
    public ErlList {
        if (elements == null) {
            throw new IllegalArgumentException("List elements cannot be null");
        }
    }

    public ErlList(List<ErlTerm> elements) {
        this(elements, null);
    }

    @Override
    public byte[] encode() {
        return ErlTermCodec.encodeList(this);
    }
}

/**
 * Represents an Erlang tuple.
 */
public final record ErlTuple(List<ErlTerm> elements) implements ErlTerm {
    public ErlTuple {
        if (elements == null) {
            throw new IllegalArgumentException("Tuple elements cannot be null");
        }
    }

    @Override
    public byte[] encode() {
        return ErlTermCodec.encodeTuple(this);
    }
}

/**
 * Represents an Erlang map.
 */
public final record ErlMap(List<MapEntry> entries) implements ErlTerm {
    public ErlMap {
        if (entries == null) {
            throw new IllegalArgumentException("Map entries cannot be null");
        }
    }

    @Override
    public byte[] encode() {
        return ErlTermCodec.encodeMap(this);
    }
}

/**
 * Represents a key-value pair in a map.
 */
public record MapEntry(ErlTerm key, ErlTerm value) {
    public MapEntry {
        if (key == null || value == null) {
            throw new IllegalArgumentException("Map key and value cannot be null");
        }
    }
}

/**
 * Represents an Erlang process identifier.
 */
public final record ErlPid(
    String node,
    int id,
    int serial,
    int creation
) implements ErlTerm {
    public ErlPid {
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }
    }

    @Override
    public byte[] encode() {
        return ErlTermCodec.encodePid(this);
    }
}

/**
 * Represents an Erlang reference.
 */
public final record ErlRef(
    String node,
    int[] ids,
    int creation
) implements ErlTerm {
    public ErlRef {
        if (node == null || ids == null) {
            throw new IllegalArgumentException("Node and ids cannot be null");
        }
        if (ids.length != 3) {
            throw new IllegalArgumentException("Reference must have exactly 3 ids");
        }
    }

    @Override
    public byte[] encode() {
        return ErlTermCodec.encodeRef(this);
    }
}

/**
 * Represents an Erlang port.
 */
public final record ErlPort(
    String node,
    int id,
    int creation
) implements ErlTerm {
    public ErlPort {
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }
    }

    @Override
    public byte[] encode() {
        return ErlTermCodec.encodePort(this);
    }
}

/**
 * Represents an Erlang function (external fun or closure).
 */
public sealed interface ErlFun extends ErlTerm
    permits ErlExternalFun, ErlClosure {
}

/**
 * Represents an external Erlang function.
 */
public final record ErlExternalFun(
    String module,
    String function,
    int arity
) implements ErlFun {
    public ErlExternalFun {
        if (module == null || function == null) {
            throw new IllegalArgumentException("Module and function cannot be null");
        }
    }

    @Override
    public byte[] encode() {
        return ErlTermCodec.encodeExternalFun(this);
    }
}

/**
 * Represents an Erlang closure.
 */
public final record ErlClosure(
    int unique,
    int index,
    int numFree,
    ErlTerm[] freeVars
) implements ErlFun {
    public ErlClosure {
        if (freeVars == null) {
            throw new IllegalArgumentException("Free variables cannot be null");
        }
    }

    @Override
    public byte[] encode() {
        return ErlTermCodec.encodeClosure(this);
    }
}