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

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Unit tests for {@link JsTypeMarshaller}.
 *
 * Tests focus on error handling and utility class behavior, since Value objects
 * cannot be instantiated on non-GraalVM JDK.
 */
class JsTypeMarshallerTest {

    @Test
    void utilityClass_constructorThrowsUnsupportedOperation() {
        try {
            Constructor<JsTypeMarshaller> constructor = JsTypeMarshaller.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            constructor.newInstance();
        } catch (Exception e) {
            Throwable cause = e.getCause();
            if (cause instanceof UnsupportedOperationException) {
                return;
            }
            throw new AssertionError("Expected UnsupportedOperationException, got: " + e);
        }
        throw new AssertionError("Expected UnsupportedOperationException to be thrown");
    }

    @Test
    void toJava_withNullValue_returnsNull() {
        Object result = JsTypeMarshaller.toJava(null);
        assertThat(result, nullValue());
    }

    @Test
    void toString_withNullValue_throwsTypeConversionError() {
        JavaScriptException ex = null;
        try {
            JsTypeMarshaller.toString(null);
        } catch (JavaScriptException e) {
            ex = e;
        }

        assertThat(ex, notNullValue());
        assertThat(ex.getErrorKind(), is(JavaScriptException.ErrorKind.TYPE_CONVERSION_ERROR));
    }

    @Test
    void toDouble_withNullValue_throwsTypeConversionError() {
        JavaScriptException ex = null;
        try {
            JsTypeMarshaller.toDouble(null);
        } catch (JavaScriptException e) {
            ex = e;
        }

        assertThat(ex, notNullValue());
        assertThat(ex.getErrorKind(), is(JavaScriptException.ErrorKind.TYPE_CONVERSION_ERROR));
    }

    @Test
    void toLong_withNullValue_throwsTypeConversionError() {
        JavaScriptException ex = null;
        try {
            JsTypeMarshaller.toLong(null);
        } catch (JavaScriptException e) {
            ex = e;
        }

        assertThat(ex, notNullValue());
        assertThat(ex.getErrorKind(), is(JavaScriptException.ErrorKind.TYPE_CONVERSION_ERROR));
    }

    @Test
    void toMap_withNullValue_throwsTypeConversionError() {
        JavaScriptException ex = null;
        try {
            JsTypeMarshaller.toMap(null);
        } catch (JavaScriptException e) {
            ex = e;
        }

        assertThat(ex, notNullValue());
        assertThat(ex.getErrorKind(), is(JavaScriptException.ErrorKind.TYPE_CONVERSION_ERROR));
    }

    @Test
    void toList_withNullValue_throwsTypeConversionError() {
        JavaScriptException ex = null;
        try {
            JsTypeMarshaller.toList(null);
        } catch (JavaScriptException e) {
            ex = e;
        }

        assertThat(ex, notNullValue());
        assertThat(ex.getErrorKind(), is(JavaScriptException.ErrorKind.TYPE_CONVERSION_ERROR));
    }

    @Test
    void as_withNullValue_throwsTypeConversionError() {
        JavaScriptException ex = null;
        try {
            JsTypeMarshaller.as(null, String.class);
        } catch (JavaScriptException e) {
            ex = e;
        }

        assertThat(ex, notNullValue());
        assertThat(ex.getErrorKind(), is(JavaScriptException.ErrorKind.TYPE_CONVERSION_ERROR));
    }

    @Test
    void parseJsonString_validJsonObject_returnsMap() {
        Map<String, Object> result = JsTypeMarshaller.parseJsonString("{\"key\": \"value\"}");
        assertThat(result, notNullValue());
        assertThat(result, hasKey("key"));
        assertThat(result.get("key"), is("value"));
    }

    @Test
    void parseJsonString_parsesStringValue() {
        Map<String, Object> result = JsTypeMarshaller.parseJsonString("{\"name\": \"test\"}");
        assertThat(result.get("name"), is("test"));
    }

    @Test
    void parseJsonString_parsesNumericValue() {
        Map<String, Object> result = JsTypeMarshaller.parseJsonString("{\"count\": 42}");
        assertThat(result.get("count"), is(42));
    }

    @Test
    void parseJsonString_invalidJson_throwsTypeConversionError() {
        JavaScriptException ex = null;
        try {
            JsTypeMarshaller.parseJsonString("{ invalid json }");
        } catch (JavaScriptException e) {
            ex = e;
        }

        assertThat(ex, notNullValue());
        assertThat(ex.getErrorKind(), is(JavaScriptException.ErrorKind.TYPE_CONVERSION_ERROR));
    }

    @Test
    void parseJsonString_emptyObject_returnsEmptyMap() {
        Map<String, Object> result = JsTypeMarshaller.parseJsonString("{}");
        assertThat(result, notNullValue());
        assertThat(result, anEmptyMap());
    }

    @Test
    void parseJsonString_nestedObject() {
        Map<String, Object> result = JsTypeMarshaller.parseJsonString(
                "{\"outer\": {\"inner\": \"value\"}}");
        assertThat(result, hasKey("outer"));
        Object outer = result.get("outer");
        assertThat(outer, instanceOf(Map.class));
        Map<String, Object> outerMap = (Map<String, Object>) outer;
        assertThat(outerMap.get("inner"), is("value"));
    }

    @Test
    void parseJsonString_arrayValue() {
        Map<String, Object> result = JsTypeMarshaller.parseJsonString(
                "{\"items\": [1, 2, 3]}");
        assertThat(result, hasKey("items"));
        Object items = result.get("items");
        assertThat(items, instanceOf(java.util.List.class));
    }

    @Test
    void parseJsonString_booleanValue() {
        Map<String, Object> result = JsTypeMarshaller.parseJsonString(
                "{\"flag\": true}");
        assertThat(result.get("flag"), is(true));
    }

    @Test
    void parseJsonString_nullValue() {
        Map<String, Object> result = JsTypeMarshaller.parseJsonString(
                "{\"nullable\": null}");
        assertThat(result, hasKey("nullable"));
        assertThat(result.get("nullable"), nullValue());
    }
}
