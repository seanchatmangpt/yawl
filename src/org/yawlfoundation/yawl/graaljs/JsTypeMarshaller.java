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

package org.yawlfoundation.yawl.graaljs;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.graalvm.polyglot.Value;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bidirectional type marshaller for Java ↔ JavaScript value conversion.
 *
 * <p>GraalJS's Polyglot API returns {@link Value} objects that wrap JavaScript objects.
 * This class converts those values to standard Java types and vice-versa, following
 * the type mapping table defined in the GraalVM Polyglot type system specification.</p>
 *
 * <h2>Critical difference from Python's TypeMarshaller</h2>
 * <p>Python dicts use hasHashEntries() / getHashValue().</p>
 * <p>JavaScript objects use hasMembers() / getMember() and getMemberKeys().</p>
 *
 * <h2>JavaScript → Java mapping</h2>
 * <pre>
 * JavaScript type      Java type
 * ─────────────────── ──────────────────────────────
 * undefined / null     null
 * boolean              boolean
 * int (fits)           Integer
 * large int            Long
 * float / double       Double
 * string               String
 * Array                List&lt;Object&gt;
 * Object               Map&lt;String, Object&gt;
 * Function / other     Value (returned as-is)
 * </pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class JsTypeMarshaller {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JsTypeMarshaller() {
        throw new UnsupportedOperationException("JsTypeMarshaller is a utility class");
    }

    /**
     * Converts a GraalJS {@link Value} to the most appropriate Java type.
     *
     * @param value  the GraalJS Value to convert; may be null
     * @return the Java representation of the JavaScript value; may be null
     * @throws JavaScriptException  if conversion fails due to an unsupported type
     */
    public static @Nullable Object toJava(@Nullable Value value) {
        if (value == null || value.isNull()) return null;
        if (value.isBoolean()) return value.asBoolean();
        if (value.isNumber()) return toJavaNumber(value);
        if (value.isString()) return value.asString();
        if (value.hasArrayElements()) return toJavaList(value);
        if (value.hasMembers()) return toJavaMap(value);
        return value;
    }

    /**
     * Converts a GraalJS {@link Value} to a Java {@link String}.
     *
     * @param value  the GraalJS Value to convert; must not be null
     * @return the string representation
     * @throws JavaScriptException  if the value is not a string type
     */
    public static String toString(Value value) {
        if (value == null || value.isNull()) throw new JavaScriptException(
            "Cannot convert JS null/undefined to String.",
            JavaScriptException.ErrorKind.TYPE_CONVERSION_ERROR);
        if (!value.isString()) {
            try { return value.toString(); }
            catch (Exception e) { throw new JavaScriptException(
                "Cannot convert JS value to String: " + e.getMessage(),
                JavaScriptException.ErrorKind.TYPE_CONVERSION_ERROR, e); }
        }
        return value.asString();
    }

    /**
     * Converts a GraalJS {@link Value} to a Java {@code double}.
     *
     * @param value  the GraalJS Value; must be a numeric type
     * @return the double value
     * @throws JavaScriptException  if the value is not numeric
     */
    public static double toDouble(Value value) {
        if (value == null || value.isNull()) throw new JavaScriptException(
            "Cannot convert JS null/undefined to double.",
            JavaScriptException.ErrorKind.TYPE_CONVERSION_ERROR);
        if (!value.isNumber()) throw new JavaScriptException(
            "Cannot convert JS " + value.getMetaObject() + " to double.",
            JavaScriptException.ErrorKind.TYPE_CONVERSION_ERROR);
        return value.asDouble();
    }

    /**
     * Converts a GraalJS {@link Value} to a Java {@code long}.
     *
     * @param value  the GraalJS Value; must be an integer type
     * @return the long value
     * @throws JavaScriptException  if the value is not an integer
     */
    public static long toLong(Value value) {
        if (value == null || value.isNull()) throw new JavaScriptException(
            "Cannot convert JS null/undefined to long.",
            JavaScriptException.ErrorKind.TYPE_CONVERSION_ERROR);
        if (!value.isNumber() || !value.fitsInLong()) throw new JavaScriptException(
            "Cannot convert JS " + value.getMetaObject() + " to long.",
            JavaScriptException.ErrorKind.TYPE_CONVERSION_ERROR);
        return value.asLong();
    }

    /**
     * Converts a GraalJS Object {@link Value} to a Java {@code Map<String, Object>}.
     *
     * <p>Uses getMemberKeys() + getMember() — NOT getHashValue() which is for Python dicts.</p>
     *
     * @param value  a GraalJS Value that has members (object); must not be null
     * @return a mutable Map with String keys and converted values
     * @throws JavaScriptException  if the value is not an object or conversion fails
     */
    public static Map<String, Object> toMap(Value value) {
        if (value == null || value.isNull()) throw new JavaScriptException(
            "Cannot convert JS null/undefined to Map.",
            JavaScriptException.ErrorKind.TYPE_CONVERSION_ERROR);
        if (!value.hasMembers()) throw new JavaScriptException(
            "Cannot convert JS " + value.getMetaObject() + " to Map. Expected object.",
            JavaScriptException.ErrorKind.TYPE_CONVERSION_ERROR);
        return toJavaMap(value);
    }

    /**
     * Converts a GraalJS Array {@link Value} to a Java {@code List<Object>}.
     *
     * @param value  a GraalJS Value that has array elements (array); must not be null
     * @return a mutable List with converted values
     * @throws JavaScriptException  if the value is not an array or conversion fails
     */
    public static List<Object> toList(Value value) {
        if (value == null || value.isNull()) throw new JavaScriptException(
            "Cannot convert JS null/undefined to List.",
            JavaScriptException.ErrorKind.TYPE_CONVERSION_ERROR);
        if (!value.hasArrayElements()) throw new JavaScriptException(
            "Cannot convert JS " + value.getMetaObject() + " to List. Expected Array.",
            JavaScriptException.ErrorKind.TYPE_CONVERSION_ERROR);
        return toJavaList(value);
    }

    /**
     * Converts a GraalJS Value to the requested Java type using the Polyglot API's
     * {@code as(Class)} mechanism.
     *
     * @param value  the GraalJS Value; must not be null
     * @param targetType  the Java type to convert to; must not be null
     * @param <T>  the target Java type
     * @return the converted value
     * @throws JavaScriptException  if the conversion is not supported
     */
    public static <T> T as(Value value, Class<T> targetType) {
        if (value == null || value.isNull()) throw new JavaScriptException(
            "Cannot convert JS null/undefined to " + targetType.getSimpleName(),
            JavaScriptException.ErrorKind.TYPE_CONVERSION_ERROR);
        try { return value.as(targetType); }
        catch (ClassCastException | UnsupportedOperationException e) { throw new JavaScriptException(
            "Cannot convert JS value to " + targetType.getSimpleName() + ": " + e.getMessage(),
            JavaScriptException.ErrorKind.TYPE_CONVERSION_ERROR, e); }
    }

    /**
     * Parses a JSON string (e.g. from JSON.stringify()) into Map<String, Object>.
     *
     * <p>Used by JavaScriptExecutionEngine.evalAsJson() for reliable complex-object conversion.</p>
     *
     * @param json  JSON string to parse; must not be null
     * @return a mutable Map representing the parsed JSON object
     * @throws JavaScriptException  if the string is not valid JSON
     */
    public static Map<String, Object> parseJsonString(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (IOException e) {
            throw new JavaScriptException(
                "Cannot parse JSON string from JS: " + e.getMessage(),
                JavaScriptException.ErrorKind.TYPE_CONVERSION_ERROR, e);
        }
    }

    private static Object toJavaNumber(Value value) {
        if (value.fitsInInt()) return value.asInt();
        if (value.fitsInLong()) return value.asLong();
        return value.asDouble();
    }

    private static List<Object> toJavaList(Value value) {
        long length = value.getArraySize();
        List<Object> result = new ArrayList<>((int) Math.min(length, Integer.MAX_VALUE));
        for (long i = 0; i < length; i++) result.add(toJava(value.getArrayElement(i)));
        return result;
    }

    private static Map<String, Object> toJavaMap(Value value) {
        Map<String, Object> result = new HashMap<>();
        for (String key : value.getMemberKeys()) {
            result.put(key, toJava(value.getMember(key)));
        }
        return result;
    }
}
