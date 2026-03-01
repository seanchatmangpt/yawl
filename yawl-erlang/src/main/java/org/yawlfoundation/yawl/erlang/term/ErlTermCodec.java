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
package org.yawlfoundation.yawl.erlang.term;

import org.yawlfoundation.yawl.erlang.error.ErlangReceiveException;
import org.yawlfoundation.yawl.erlang.error.ErlangRpcException;
import org.yawlfoundation.yawl.erlang.generated.ei_h;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ETF (External Term Format) encoder and decoder for all 13 Erlang term types.
 * Performs pure-Java byte manipulation — no OTP native library required for codec operations.
 *
 * <p>All encode methods produce bytes prefixed with the ETF version byte (131).
 * The {@link #decode(byte[])} methods expect the version byte at position 0.
 * The {@link #encodeArgs(List)} method produces bytes WITHOUT a version byte
 * (for use as RPC argument lists in {@code ei_rpc}).</p>
 *
 * <p>Encoding strategy:</p>
 * <ul>
 *   <li>SMALL_INTEGER_EXT for 0..255</li>
 *   <li>INTEGER_EXT for ±2³¹-1</li>
 *   <li>SMALL_BIG_EXT / LARGE_BIG_EXT for arbitrary precision</li>
 *   <li>SMALL_TUPLE_EXT for arity ≤ 255, LARGE_TUPLE_EXT otherwise</li>
 *   <li>SMALL_ATOM_UTF8_EXT for ≤ 255 UTF-8 bytes, ATOM_UTF8_EXT otherwise</li>
 * </ul>
 */
public final class ErlTermCodec {

    private ErlTermCodec() {
    }

    // -----------------------------------------------------------------------
    // Public API: Encode
    // -----------------------------------------------------------------------

    /**
     * Encodes a term to ETF bytes with the version byte (131) prefix.
     *
     * @param term the term to encode
     * @return ETF bytes starting with version byte 131
     */
    public static byte[] encode(ErlTerm term) {
        ByteArrayWriter w = new ByteArrayWriter();
        w.writeByte(ei_h.ERL_VERSION_MAGIC);
        writeTerm(term, w);
        return w.toByteArray();
    }

    /**
     * Encodes a list of terms as an Erlang list WITHOUT the version byte.
     * Used as the argument buffer for {@code ei_rpc}.
     *
     * @param args terms to encode as argument list
     * @return ETF bytes for the argument list (no version prefix)
     */
    public static byte[] encodeArgs(List<ErlTerm> args) {
        ByteArrayWriter w = new ByteArrayWriter();
        writeTerm(new ErlList(args), w);
        return w.toByteArray();
    }

    // -----------------------------------------------------------------------
    // Public API: Decode
    // -----------------------------------------------------------------------

    /**
     * Decodes an ETF byte array (with version byte 131 at position 0).
     *
     * @param bytes ETF bytes starting with version byte 131
     * @return decoded term
     * @throws ErlangReceiveException if the version byte is wrong or the term is malformed
     */
    public static ErlTerm decode(byte[] bytes) throws ErlangReceiveException {
        if (bytes == null || bytes.length < 2) {
            throw new ErlangReceiveException("ETF bytes too short: " + (bytes == null ? 0 : bytes.length));
        }
        if (bytes[0] != ei_h.ERL_VERSION_MAGIC) {
            throw new ErlangReceiveException(
                "ETF version byte mismatch: expected 131, got " + (bytes[0] & 0xFF));
        }
        ByteArrayReader r = new ByteArrayReader(bytes, 1);
        return readTerm(r);
    }

    /**
     * Decodes the result of an {@code ei_rpc} call.
     * Unwraps the {@code {rex, Result}} tuple that OTP wraps RPC responses in.
     *
     * @param bytes ETF-encoded RPC response (with version byte)
     * @return the inner result term
     * @throws ErlangReceiveException if bytes are malformed
     * @throws ErlangRpcException if the response is {@code {badrpc, Reason}}
     */
    public static ErlTerm decodeRpcResult(byte[] bytes)
            throws ErlangReceiveException, ErlangRpcException {
        ErlTerm raw = decode(bytes);
        if (raw instanceof ErlTuple(var elements) && elements.size() == 2) {
            ErlTerm first = elements.get(0);
            ErlTerm second = elements.get(1);
            if (first instanceof ErlAtom(var name)) {
                if ("rex".equals(name)) {
                    if (second instanceof ErlTuple(var inner) && inner.size() == 2
                            && inner.get(0) instanceof ErlAtom(var badRpc)
                            && "badrpc".equals(badRpc)) {
                        throw new ErlangRpcException("?", "?",
                            "badrpc: " + inner.get(1).toString());
                    }
                    return second;
                }
                if ("badrpc".equals(name)) {
                    throw new ErlangRpcException("?", "?",
                        "badrpc: " + second.toString());
                }
            }
        }
        return raw;
    }

    // -----------------------------------------------------------------------
    // Internal: ETF writer
    // -----------------------------------------------------------------------

    private static void writeTerm(ErlTerm term, ByteArrayWriter w) {
        switch (term) {
            case ErlAtom(var value) -> {
                byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
                if (utf8.length <= 255) {
                    w.writeByte(ei_h.ERL_SMALL_ATOM_UTF8_EXT);
                    w.writeByte(utf8.length);
                } else {
                    w.writeByte(ei_h.ERL_ATOM_UTF8_EXT);
                    w.writeShortBE(utf8.length);
                }
                w.writeBytes(utf8);
            }
            case ErlInteger(var bigVal) -> {
                if (bigVal.bitLength() < 8 && bigVal.signum() >= 0 && bigVal.intValueExact() <= 255) {
                    w.writeByte(ei_h.ERL_SMALL_INTEGER_EXT);
                    w.writeByte(bigVal.intValueExact());
                } else if (bigVal.bitLength() < 32
                        && bigVal.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) >= 0
                        && bigVal.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) <= 0) {
                    w.writeByte(ei_h.ERL_INTEGER_EXT);
                    w.writeIntBE(bigVal.intValueExact());
                } else {
                    writeBigInteger(bigVal, w);
                }
            }
            case ErlFloat(var d) -> {
                w.writeByte(ei_h.NEW_FLOAT_EXT);
                w.writeLongBE(Double.doubleToRawLongBits(d));
            }
            case ErlBinary(var data) -> {
                w.writeByte(ei_h.BINARY_EXT);
                w.writeIntBE(data.length);
                w.writeBytes(data);
            }
            case ErlBitstring(var data, var bits) -> {
                w.writeByte(ei_h.BIT_BINARY_EXT);
                w.writeIntBE(data.length);
                w.writeByte(bits);
                w.writeBytes(data);
            }
            case ErlNil() -> w.writeByte(ei_h.NIL_EXT);
            case ErlList(var elems, var tail) -> {
                w.writeByte(ei_h.LIST_EXT);
                w.writeIntBE(elems.size());
                for (ErlTerm e : elems) {
                    writeTerm(e, w);
                }
                writeTerm(tail, w);
            }
            case ErlTuple(var elems) -> {
                if (elems.size() <= 255) {
                    w.writeByte(ei_h.SMALL_TUPLE_EXT);
                    w.writeByte(elems.size());
                } else {
                    w.writeByte(ei_h.LARGE_TUPLE_EXT);
                    w.writeIntBE(elems.size());
                }
                for (ErlTerm e : elems) {
                    writeTerm(e, w);
                }
            }
            case ErlMap(var entries) -> {
                w.writeByte(ei_h.MAP_EXT);
                w.writeIntBE(entries.size());
                for (Map.Entry<ErlTerm, ErlTerm> entry : entries.entrySet()) {
                    writeTerm(entry.getKey(), w);
                    writeTerm(entry.getValue(), w);
                }
            }
            case ErlPid(var node, var id, var serial, var creation) -> {
                w.writeByte(ei_h.NEW_PID_EXT);
                writeTerm(new ErlAtom(node), w);
                w.writeIntBE(id);
                w.writeIntBE(serial);
                w.writeIntBE(creation);
            }
            case ErlRef(var node, var ids, var creation) -> {
                w.writeByte(ei_h.NEWER_REFERENCE_EXT);
                w.writeShortBE(ids.length);
                writeTerm(new ErlAtom(node), w);
                w.writeIntBE(creation);
                for (int id : ids) {
                    w.writeIntBE(id);
                }
            }
            case ErlPort(var node, var id, var creation) -> {
                w.writeByte(ei_h.V4_PORT_EXT);
                writeTerm(new ErlAtom(node), w);
                w.writeLongBE(id);
                w.writeIntBE(creation);
            }
            case ErlExternalFun(var module, var function, var arity) -> {
                w.writeByte(ei_h.EXPORT_EXT);
                writeTerm(new ErlAtom(module), w);
                writeTerm(new ErlAtom(function), w);
                writeTerm(new ErlInteger(arity), w);
            }
            case ErlClosure(var module, var arity, var uniq, var index,
                             var oldIndex, var oldUniq, var pid, var freeVars) -> {
                w.writeByte(ei_h.NEW_FUN_EXT);
                ByteArrayWriter inner = new ByteArrayWriter();
                inner.writeByte(arity);
                byte[] u = new byte[16];
                System.arraycopy(uniq, 0, u, 0, Math.min(uniq.length, 16));
                inner.writeBytes(u);
                inner.writeIntBE(index);
                inner.writeIntBE(freeVars.size());
                writeTerm(new ErlAtom(module), inner);
                writeTerm(new ErlInteger(oldIndex), inner);
                writeTerm(new ErlInteger(oldUniq), inner);
                writeTerm(pid, inner);
                for (ErlTerm fv : freeVars) {
                    writeTerm(fv, inner);
                }
                byte[] body = inner.toByteArray();
                w.writeIntBE(body.length + 4);
                w.writeBytes(body);
            }
        }
    }

    private static void writeBigInteger(BigInteger val, ByteArrayWriter w) {
        int sign = val.signum() < 0 ? 1 : 0;
        byte[] magnitude = val.abs().toByteArray();
        int start = (magnitude.length > 1 && magnitude[0] == 0) ? 1 : 0;
        int len = magnitude.length - start;
        byte[] leBytes = new byte[len];
        for (int i = 0; i < len; i++) {
            leBytes[i] = magnitude[start + len - 1 - i];
        }
        if (len <= 255) {
            w.writeByte(ei_h.SMALL_BIG_EXT);
            w.writeByte(len);
        } else {
            w.writeByte(ei_h.LARGE_BIG_EXT);
            w.writeIntBE(len);
        }
        w.writeByte(sign);
        w.writeBytes(leBytes);
    }

    // -----------------------------------------------------------------------
    // Internal: ETF reader
    // -----------------------------------------------------------------------

    private static ErlTerm readTerm(ByteArrayReader r) throws ErlangReceiveException {
        int tag = r.readByte() & 0xFF;
        return switch (tag) {
            case 118 -> {
                int len = r.readShortBEUnsigned();
                yield new ErlAtom(new String(r.readBytes(len), StandardCharsets.UTF_8));
            }
            case 119 -> {
                int len = r.readByte() & 0xFF;
                yield new ErlAtom(new String(r.readBytes(len), StandardCharsets.UTF_8));
            }
            case 97 -> new ErlInteger(r.readByte() & 0xFF);
            case 98 -> new ErlInteger(r.readIntBE());
            case 70 -> new ErlFloat(Double.longBitsToDouble(r.readLongBE()));
            case 109 -> {
                int len = r.readIntBE();
                yield new ErlBinary(r.readBytes(len));
            }
            case 77 -> {
                int len = r.readIntBE();
                int bits = r.readByte() & 0xFF;
                yield new ErlBitstring(r.readBytes(len), bits == 0 ? 8 : bits);
            }
            case 106 -> ErlNil.INSTANCE;
            case 108 -> {
                int arity = r.readIntBE();
                List<ErlTerm> elems = new ArrayList<>(arity);
                for (int i = 0; i < arity; i++) {
                    elems.add(readTerm(r));
                }
                ErlTerm tail = readTerm(r);
                yield new ErlList(elems, tail);
            }
            case 104 -> {
                int arity = r.readByte() & 0xFF;
                List<ErlTerm> elems = new ArrayList<>(arity);
                for (int i = 0; i < arity; i++) {
                    elems.add(readTerm(r));
                }
                yield new ErlTuple(elems);
            }
            case 105 -> {
                int arity = r.readIntBE();
                List<ErlTerm> elems = new ArrayList<>(arity);
                for (int i = 0; i < arity; i++) {
                    elems.add(readTerm(r));
                }
                yield new ErlTuple(elems);
            }
            case 116 -> {
                int arity = r.readIntBE();
                Map<ErlTerm, ErlTerm> entries = new LinkedHashMap<>(arity * 2);
                for (int i = 0; i < arity; i++) {
                    ErlTerm key = readTerm(r);
                    ErlTerm val = readTerm(r);
                    entries.put(key, val);
                }
                yield new ErlMap(entries);
            }
            case 88 -> {
                ErlTerm nodeAtom = readTerm(r);
                String node = nodeAtom instanceof ErlAtom(var n) ? n : nodeAtom.toString();
                int id = r.readIntBE();
                int serial = r.readIntBE();
                int creation = r.readIntBE();
                yield new ErlPid(node, id, serial, creation);
            }
            case 90 -> {
                int len = r.readShortBEUnsigned();
                ErlTerm nodeAtom = readTerm(r);
                String node = nodeAtom instanceof ErlAtom(var n) ? n : nodeAtom.toString();
                int creation = r.readIntBE();
                int[] ids = new int[len];
                for (int i = 0; i < len; i++) {
                    ids[i] = r.readIntBE();
                }
                yield new ErlRef(node, ids, creation);
            }
            case 120 -> {
                ErlTerm nodeAtom = readTerm(r);
                String node = nodeAtom instanceof ErlAtom(var n) ? n : nodeAtom.toString();
                long id = r.readLongBE();
                int creation = r.readIntBE();
                yield new ErlPort(node, id, creation);
            }
            case 113 -> {
                ErlTerm modAtom = readTerm(r);
                ErlTerm funAtom = readTerm(r);
                ErlTerm arityTerm = readTerm(r);
                String mod = modAtom instanceof ErlAtom(var m) ? m : modAtom.toString();
                String fun = funAtom instanceof ErlAtom(var f) ? f : funAtom.toString();
                int arity = arityTerm instanceof ErlInteger(var v) ? v.intValueExact() : 0;
                yield new ErlExternalFun(mod, fun, arity);
            }
            case 112 -> {
                int totalSize = r.readIntBE();
                int arity = r.readByte() & 0xFF;
                byte[] uniq = r.readBytes(16);
                int index = r.readIntBE();
                int numFree = r.readIntBE();
                ErlTerm modAtom = readTerm(r);
                ErlTerm oldIndexTerm = readTerm(r);
                ErlTerm oldUniqTerm = readTerm(r);
                ErlTerm pidTerm = readTerm(r);
                String mod = modAtom instanceof ErlAtom(var m) ? m : modAtom.toString();
                int oldIndex = oldIndexTerm instanceof ErlInteger(var v) ? v.intValueExact() : 0;
                int oldUniq = oldUniqTerm instanceof ErlInteger(var v) ? v.intValueExact() : 0;
                ErlPid pid = pidTerm instanceof ErlPid p ? p : new ErlPid("nonode@nohost", 0, 0, 0);
                List<ErlTerm> freeVars = new ArrayList<>(numFree);
                for (int i = 0; i < numFree; i++) {
                    freeVars.add(readTerm(r));
                }
                yield new ErlClosure(mod, arity, uniq, index, oldIndex, oldUniq, pid, freeVars);
            }
            case 110 -> readBigInteger(r, r.readByte() & 0xFF);
            case 111 -> readBigInteger(r, r.readIntBE());
            default -> throw new ErlangReceiveException(
                "Unknown ETF tag: " + tag + " (0x" + Integer.toHexString(tag) + ")");
        };
    }

    private static ErlInteger readBigInteger(ByteArrayReader r, int len) throws ErlangReceiveException {
        int sign = r.readByte() & 0xFF;
        byte[] leBytes = r.readBytes(len);
        byte[] beBytes = new byte[len + 1];
        beBytes[0] = 0;
        for (int i = 0; i < len; i++) {
            beBytes[1 + (len - 1 - i)] = leBytes[i];
        }
        BigInteger magnitude = new BigInteger(beBytes);
        return new ErlInteger(sign == 0 ? magnitude : magnitude.negate());
    }

    // -----------------------------------------------------------------------
    // Internal: byte writer / reader helpers
    // -----------------------------------------------------------------------

    private static final class ByteArrayWriter {

        private byte[] buf = new byte[64];
        private int pos = 0;

        void ensureCapacity(int n) {
            if (pos + n > buf.length) {
                int newCap = Math.max(buf.length * 2, pos + n);
                byte[] newBuf = new byte[newCap];
                System.arraycopy(buf, 0, newBuf, 0, pos);
                buf = newBuf;
            }
        }

        void writeByte(int b) {
            ensureCapacity(1);
            buf[pos++] = (byte) b;
        }

        void writeBytes(byte[] bytes) {
            ensureCapacity(bytes.length);
            System.arraycopy(bytes, 0, buf, pos, bytes.length);
            pos += bytes.length;
        }

        void writeShortBE(int v) {
            ensureCapacity(2);
            buf[pos++] = (byte) (v >> 8);
            buf[pos++] = (byte) v;
        }

        void writeIntBE(int v) {
            ensureCapacity(4);
            buf[pos++] = (byte) (v >> 24);
            buf[pos++] = (byte) (v >> 16);
            buf[pos++] = (byte) (v >> 8);
            buf[pos++] = (byte) v;
        }

        void writeLongBE(long v) {
            ensureCapacity(8);
            for (int i = 56; i >= 0; i -= 8) {
                buf[pos++] = (byte) (v >> i);
            }
        }

        byte[] toByteArray() {
            byte[] out = new byte[pos];
            System.arraycopy(buf, 0, out, 0, pos);
            return out;
        }
    }

    private static final class ByteArrayReader {

        private final byte[] buf;
        private int pos;

        ByteArrayReader(byte[] buf, int startPos) {
            this.buf = buf;
            this.pos = startPos;
        }

        byte readByte() throws ErlangReceiveException {
            if (pos >= buf.length) {
                throw new ErlangReceiveException("ETF buffer underflow at position " + pos);
            }
            return buf[pos++];
        }

        int readShortBEUnsigned() throws ErlangReceiveException {
            return ((readByte() & 0xFF) << 8) | (readByte() & 0xFF);
        }

        int readIntBE() throws ErlangReceiveException {
            return ((readByte() & 0xFF) << 24)
                    | ((readByte() & 0xFF) << 16)
                    | ((readByte() & 0xFF) << 8)
                    | (readByte() & 0xFF);
        }

        long readLongBE() throws ErlangReceiveException {
            long high = readIntBE() & 0xFFFFFFFFL;
            long low = readIntBE() & 0xFFFFFFFFL;
            return (high << 32) | low;
        }

        byte[] readBytes(int n) throws ErlangReceiveException {
            if (pos + n > buf.length) {
                throw new ErlangReceiveException(
                    "ETF buffer underflow: need " + n + " bytes at " + pos
                            + " but only " + (buf.length - pos) + " remain");
            }
            byte[] result = new byte[n];
            System.arraycopy(buf, pos, result, 0, n);
            pos += n;
            return result;
        }
    }
}
