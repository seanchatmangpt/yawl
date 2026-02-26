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

package org.yawlfoundation.yawl.graalpy;

import org.graalvm.polyglot.Value;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bidirectional type marshaller for Java ↔ Python value conversion.
 *
 * <p>GraalPy's Polyglot API returns {@link Value} objects that wrap Python objects.
 * This class converts those values to standard Java types and vice-versa, following
 * the type mapping table defined in the GraalVM Polyglot type system specification.</p>
 *
 * <h2>Python → Java mapping</h2>
 * <pre>
 * Python type          Java type
 * ─────────────────── ──────────────────────────────
 * int                  long (for large values) / int
 * float                double
 * str                  String
 * bool                 boolean
 * None                 null
 * list / tuple         List&lt;Object&gt;
 * dict                 Map&lt;String, Object&gt;
 * bytes                byte[]
 * </pre>
 *
 * <h2>Java → Python mapping</h2>
 * <p>Java primitive wrappers, {@code String}, {@code List}, and {@code Map} are
 * automatically marshalled to the corresponding Python types by the Polyglot API
 * when passed as arguments to Python invocations.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class TypeMarshaller {

    private TypeMarshaller() {
        throw new UnsupportedOperationException("TypeMarshaller is a utility class");
    }

    /**
     * Converts a GraalPy {@link Value} to the most appropriate Java type.
     *
     * <p>The conversion follows this priority order:
     * <ol>
     *   <li>null / None → {@code null}</li>
     *   <li>boolean → {@code Boolean}</li>
     *   <li>integer (fits in int) → {@code Integer}</li>
     *   <li>integer (large) → {@code Long}</li>
     *   <li>floating point → {@code Double}</li>
     *   <li>String → {@code String}</li>
     *   <li>array/list/tuple → {@code List<Object>} (recursive)</li>
     *   <li>hash/dict → {@code Map<String, Object>} (recursive)</li>
     *   <li>Polyglot Value (host objects, callables) → returned as-is {@link Value}</li>
     * </ol>
     * </p>
     *
     * @param value  the GraalPy Value to convert; may be null
     * @return the Java representation of the Python value; may be null
     * @throws PythonException  if conversion fails due to an unsupported type
     */
    public static @Nullable Object toJava(@Nullable Value value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isNumber()) {
            return toJavaNumber(value);
        }
        if (value.isString()) {
            return value.asString();
        }
        if (value.hasArrayElements()) {
            return toJavaList(value);
        }
        if (value.hasHashEntries()) {
            return toJavaMap(value);
        }
        // Return polyglot Value for host objects, callables, and unknown Python types
        return value;
    }

    /**
     * Converts a GraalPy {@link Value} to a Java {@link String}.
     *
     * @param value  the GraalPy Value to convert; must not be null
     * @return the string representation
     * @throws PythonException  if the value is not a string type
     */
    public static String toString(Value value) {
        if (value == null || value.isNull()) {
            throw new PythonException(
                    "Cannot convert Python None to String. Expected str, got None.",
                    PythonException.ErrorKind.TYPE_CONVERSION_ERROR);
        }
        if (!value.isString()) {
            // Fallback: use Python's str() representation
            try {
                return value.toString();
            } catch (Exception e) {
                throw new PythonException(
                        "Cannot convert Python value to String: " + e.getMessage(),
                        PythonException.ErrorKind.TYPE_CONVERSION_ERROR, e);
            }
        }
        return value.asString();
    }

    /**
     * Converts a GraalPy {@link Value} to a Java {@code double}.
     *
     * @param value  the GraalPy Value; must be a numeric type
     * @return the double value
     * @throws PythonException  if the value is not numeric
     */
    public static double toDouble(Value value) {
        if (value == null || value.isNull()) {
            throw new PythonException(
                    "Cannot convert Python None to double.",
                    PythonException.ErrorKind.TYPE_CONVERSION_ERROR);
        }
        if (!value.isNumber()) {
            throw new PythonException(
                    "Cannot convert Python " + value.getMetaObject() + " to double. Expected int or float.",
                    PythonException.ErrorKind.TYPE_CONVERSION_ERROR);
        }
        return value.asDouble();
    }

    /**
     * Converts a GraalPy {@link Value} to a Java {@code long}.
     *
     * @param value  the GraalPy Value; must be an integer type
     * @return the long value
     * @throws PythonException  if the value is not an integer
     */
    public static long toLong(Value value) {
        if (value == null || value.isNull()) {
            throw new PythonException(
                    "Cannot convert Python None to long.",
                    PythonException.ErrorKind.TYPE_CONVERSION_ERROR);
        }
        if (!value.isNumber() || !value.fitsInLong()) {
            throw new PythonException(
                    "Cannot convert Python " + value.getMetaObject() + " to long. "
                    + "Expected int that fits in 64 bits.",
                    PythonException.ErrorKind.TYPE_CONVERSION_ERROR);
        }
        return value.asLong();
    }

    /**
     * Converts a GraalPy dict {@link Value} to a Java {@code Map<String, Object>}.
     *
     * <p>Keys are converted to String via their Python {@code str()} representation.
     * Values are recursively converted via {@link #toJava(Value)}.</p>
     *
     * @param value  a GraalPy Value that has hash entries (dict); must not be null
     * @return a mutable Map with String keys and converted values
     * @throws PythonException  if the value is not a dict or conversion fails
     */
    public static Map<String, Object> toMap(Value value) {
        if (value == null || value.isNull()) {
            throw new PythonException(
                    "Cannot convert Python None to Map.",
                    PythonException.ErrorKind.TYPE_CONVERSION_ERROR);
        }
        if (!value.hasHashEntries()) {
            throw new PythonException(
                    "Cannot convert Python " + value.getMetaObject() + " to Map. Expected dict.",
                    PythonException.ErrorKind.TYPE_CONVERSION_ERROR);
        }
        return toJavaMap(value);
    }

    /**
     * Converts a GraalPy list/tuple {@link Value} to a Java {@code List<Object>}.
     *
     * @param value  a GraalPy Value that has array elements (list/tuple); must not be null
     * @return a mutable List with converted values
     * @throws PythonException  if the value is not a list/tuple or conversion fails
     */
    public static List<Object> toList(Value value) {
        if (value == null || value.isNull()) {
            throw new PythonException(
                    "Cannot convert Python None to List.",
                    PythonException.ErrorKind.TYPE_CONVERSION_ERROR);
        }
        if (!value.hasArrayElements()) {
            throw new PythonException(
                    "Cannot convert Python " + value.getMetaObject() + " to List. Expected list or tuple.",
                    PythonException.ErrorKind.TYPE_CONVERSION_ERROR);
        }
        return toJavaList(value);
    }

    /**
     * Converts a GraalPy Value to the requested Java type using the Polyglot API's
     * {@code as(Class)} mechanism.
     *
     * <p>This enables automatic mapping of Python objects to Java interfaces or
     * concrete classes when GraalPy's polyglot interop supports it.</p>
     *
     * @param value  the GraalPy Value; must not be null
     * @param targetType  the Java type to convert to; must not be null
     * @param <T>  the target Java type
     * @return the converted value
     * @throws PythonException  if the conversion is not supported
     */
    public static <T> T as(Value value, Class<T> targetType) {
        if (value == null || value.isNull()) {
            throw new PythonException(
                    "Cannot convert Python None to " + targetType.getSimpleName(),
                    PythonException.ErrorKind.TYPE_CONVERSION_ERROR);
        }
        try {
            return value.as(targetType);
        } catch (ClassCastException | UnsupportedOperationException e) {
            throw new PythonException(
                    "Cannot convert Python " + value.getMetaObject()
                    + " to Java " + targetType.getSimpleName() + ": " + e.getMessage(),
                    PythonException.ErrorKind.TYPE_CONVERSION_ERROR, e);
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────────────

    private static Object toJavaNumber(Value value) {
        if (value.fitsInInt()) {
            return value.asInt();
        }
        if (value.fitsInLong()) {
            return value.asLong();
        }
        return value.asDouble();
    }

    private static List<Object> toJavaList(Value value) {
        long length = value.getArraySize();
        List<Object> result = new ArrayList<>((int) Math.min(length, Integer.MAX_VALUE));
        for (long i = 0; i < length; i++) {
            result.add(toJava(value.getArrayElement(i)));
        }
        return result;
    }

    private static Map<String, Object> toJavaMap(Value value) {
        Map<String, Object> result = new HashMap<>();
        Value keys = value.getHashKeysIterator();
        while (keys.hasIteratorNextElement()) {
            Value key = keys.getIteratorNextElement();
            String strKey = key.isString() ? key.asString() : key.toString();
            Value val = value.getHashValue(key);
            result.put(strKey, toJava(val));
        }
        return result;
    }
}
