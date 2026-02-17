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

package org.yawlfoundation.yawl.util;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;


/**
 * Modern null-safety utility class for YAWL engine code.
 *
 * <p>Provides type-safe, null-safe operations that replace verbose legacy patterns
 * such as repeated {@code if (obj != null)} and {@code if (obj == null) return null}
 * guard chains. All methods are stateless and thread-safe.
 *
 * <h3>Pattern Migration Guide</h3>
 *
 * <pre>
 * BEFORE (legacy guard clause):
 *   if (obj == null) throw new YStateException("obj must not be null");
 * AFTER:
 *   NullCheckModernizer.requirePresent(obj, "obj must not be null", YStateException::new);
 *
 * BEFORE (optional action):
 *   if (obj != null) { doSomething(obj); }
 * AFTER:
 *   NullCheckModernizer.ifPresent(obj, o -> doSomething(o));
 *
 * BEFORE (null-safe getter chain):
 *   String result = obj != null ? obj.getValue() : null;
 * AFTER:
 *   String result = NullCheckModernizer.mapOrNull(obj, MyClass::getValue);
 *
 * BEFORE (null-safe getter with default):
 *   String result = obj != null ? obj.getValue() : "default";
 * AFTER:
 *   String result = NullCheckModernizer.mapOrElse(obj, MyClass::getValue, "default");
 *
 * BEFORE (conditional with two paths):
 *   if (obj != null) { processA(obj); } else { processB(); }
 * AFTER:
 *   NullCheckModernizer.ifPresentOrElse(obj, o -> processA(o), () -> processB());
 * </pre>
 *
 * @author YAWL Foundation
 * @since YAWL 6.0.0-Alpha (null-modernization Phase 1, 2026)
 */
public final class NullCheckModernizer {

    private NullCheckModernizer() {
        throw new UnsupportedOperationException(
                "NullCheckModernizer is a static utility class and must not be instantiated.");
    }


    // -------------------------------------------------------------------------
    // GUARD CLAUSES — require-not-null with typed exceptions
    // -------------------------------------------------------------------------

    /**
     * Requires that {@code value} is non-null; throws a caller-supplied checked
     * exception otherwise.
     *
     * <p>Usage replaces:
     * <pre>
     *   if (value == null) throw new YStateException("message");
     * </pre>
     *
     * @param <T>              the value type
     * @param <E>              the exception type
     * @param value            the value to check
     * @param message          the error message passed to the exception factory
     * @param exceptionFactory a factory function that creates the exception from a message
     * @return {@code value}, guaranteed non-null
     * @throws E if {@code value} is null
     */
    public static <T, E extends Exception> T requirePresent(
            T value, String message, Function<String, E> exceptionFactory) throws E {
        if (value == null) {
            throw exceptionFactory.apply(message);
        }
        return value;
    }


    /**
     * Requires that {@code value} is non-null; throws {@link NullPointerException}
     * with {@code fieldName} as the message.
     *
     * <p>Delegates to {@link Objects#requireNonNull(Object, String)} for standard
     * null contract enforcement on public API boundaries.
     *
     * @param <T>       the value type
     * @param value     the value to check
     * @param fieldName the field or parameter name for the exception message
     * @return {@code value}, guaranteed non-null
     * @throws NullPointerException if {@code value} is null
     */
    public static <T> T requireNonNull(T value, String fieldName) {
        return Objects.requireNonNull(value, fieldName + " must not be null");
    }


    // -------------------------------------------------------------------------
    // SAFE CONSUMERS — execute action only when value is non-null
    // -------------------------------------------------------------------------

    /**
     * Executes {@code action} with {@code value} only if {@code value} is non-null.
     *
     * <p>Usage replaces:
     * <pre>
     *   if (obj != null) { action(obj); }
     * </pre>
     *
     * @param <T>    the value type
     * @param value  the value to check
     * @param action the consumer executed when {@code value} is non-null
     */
    public static <T> void ifPresent(T value, Consumer<T> action) {
        if (value != null) {
            action.accept(value);
        }
    }


    /**
     * Executes {@code presentAction} with {@code value} when non-null, or
     * {@code absentAction} when null.
     *
     * <p>Usage replaces:
     * <pre>
     *   if (obj != null) { doA(obj); } else { doB(); }
     * </pre>
     *
     * @param <T>           the value type
     * @param value         the value to check
     * @param presentAction the consumer executed when {@code value} is non-null
     * @param absentAction  the runnable executed when {@code value} is null
     */
    public static <T> void ifPresentOrElse(
            T value, Consumer<T> presentAction, Runnable absentAction) {
        if (value != null) {
            presentAction.accept(value);
        } else {
            absentAction.run();
        }
    }


    // -------------------------------------------------------------------------
    // SAFE MAPPERS — transform or return null/default when input is null
    // -------------------------------------------------------------------------

    /**
     * Applies {@code mapper} to {@code value} if non-null; returns null otherwise.
     *
     * <p>Usage replaces:
     * <pre>
     *   String result = obj != null ? obj.getValue() : null;
     * </pre>
     *
     * @param <T>    the input type
     * @param <R>    the result type
     * @param value  the value to map, may be null
     * @param mapper the mapping function, applied only when {@code value} is non-null
     * @return the mapped result, or null if {@code value} is null
     */
    public static <T, R> R mapOrNull(T value, Function<T, R> mapper) {
        return value != null ? mapper.apply(value) : null;
    }


    /**
     * Applies {@code mapper} to {@code value} if non-null; returns {@code defaultValue}
     * otherwise.
     *
     * <p>Usage replaces:
     * <pre>
     *   String result = obj != null ? obj.getValue() : "default";
     * </pre>
     *
     * @param <T>          the input type
     * @param <R>          the result type
     * @param value        the value to map, may be null
     * @param mapper       the mapping function, applied only when {@code value} is non-null
     * @param defaultValue the value returned when {@code value} is null
     * @return the mapped result, or {@code defaultValue} if {@code value} is null
     */
    public static <T, R> R mapOrElse(T value, Function<T, R> mapper, R defaultValue) {
        return value != null ? mapper.apply(value) : defaultValue;
    }


    /**
     * Applies {@code mapper} to {@code value} if non-null; evaluates and returns the
     * result of {@code defaultSupplier} otherwise.
     *
     * <p>Use when the default value is expensive to compute and should be lazy.
     *
     * @param <T>             the input type
     * @param <R>             the result type
     * @param value           the value to map, may be null
     * @param mapper          the mapping function, applied only when {@code value} is non-null
     * @param defaultSupplier supplier evaluated only when {@code value} is null
     * @return the mapped result, or the supplied default if {@code value} is null
     */
    public static <T, R> R mapOrGet(
            T value, Function<T, R> mapper, Supplier<R> defaultSupplier) {
        return value != null ? mapper.apply(value) : defaultSupplier.get();
    }


    // -------------------------------------------------------------------------
    // OPTIONAL BRIDGES — create Optional instances from possibly-null values
    // -------------------------------------------------------------------------

    /**
     * Wraps {@code value} in an {@link Optional}, returning {@link Optional#empty()}
     * when {@code value} is null.
     *
     * <p>Prefer this over {@link Optional#ofNullable(Object)} at call sites that
     * need explicit documentation that the value may legally be null.
     *
     * @param <T>   the value type
     * @param value the possibly-null value
     * @return an {@code Optional} wrapping {@code value}
     */
    public static <T> Optional<T> toOptional(T value) {
        return Optional.ofNullable(value);
    }


    // -------------------------------------------------------------------------
    // NULL-SAFE COLLECTION RETURNS — return empty collections instead of null
    // -------------------------------------------------------------------------

    /**
     * Returns {@code collection} if non-null; returns {@link Collections#emptyList()}
     * otherwise.
     *
     * <p>Usage replaces:
     * <pre>
     *   return list != null ? list : Collections.emptyList();
     * </pre>
     *
     * @param <T>        the element type
     * @param collection the possibly-null list
     * @return {@code collection} if non-null, or an empty immutable list
     */
    public static <T> List<T> emptyIfNull(List<T> collection) {
        return collection != null ? collection : Collections.emptyList();
    }


    /**
     * Returns {@code set} if non-null; returns {@link Collections#emptySet()} otherwise.
     *
     * @param <T> the element type
     * @param set the possibly-null set
     * @return {@code set} if non-null, or an empty immutable set
     */
    public static <T> Set<T> emptyIfNull(Set<T> set) {
        return set != null ? set : Collections.emptySet();
    }


    /**
     * Returns {@code map} if non-null; returns {@link Collections#emptyMap()} otherwise.
     *
     * @param <K> the key type
     * @param <V> the value type
     * @param map the possibly-null map
     * @return {@code map} if non-null, or an empty immutable map
     */
    public static <K, V> Map<K, V> emptyIfNull(Map<K, V> map) {
        return map != null ? map : Collections.emptyMap();
    }


    /**
     * Returns {@code collection} if non-null and non-empty; otherwise returns the
     * result of {@code defaultSupplier}.
     *
     * @param <T>             the element type
     * @param <C>             the collection type
     * @param collection      the possibly-null or empty collection
     * @param defaultSupplier supplier evaluated when collection is null or empty
     * @return {@code collection} if non-null and non-empty, else the supplied default
     */
    public static <T, C extends Collection<T>> C nonEmptyOrElseGet(
            C collection, Supplier<C> defaultSupplier) {
        return (collection != null && !collection.isEmpty()) ? collection
                : defaultSupplier.get();
    }


    // -------------------------------------------------------------------------
    // NULL-SAFE STRING OPERATIONS
    // -------------------------------------------------------------------------

    /**
     * Returns {@code value} if non-null; returns {@code ""} otherwise.
     *
     * <p>Usage replaces:
     * <pre>
     *   String s = value != null ? value : "";
     * </pre>
     *
     * @param value the possibly-null String
     * @return {@code value} if non-null, or {@code ""}
     */
    public static String emptyIfNull(String value) {
        return value != null ? value : "";
    }


    /**
     * Returns true if {@code value} is null or empty after trimming whitespace.
     *
     * @param value the String to check, may be null
     * @return true if null, empty, or whitespace-only
     */
    public static boolean isNullOrBlank(String value) {
        return value == null || value.isBlank();
    }


    /**
     * Returns true if {@code value} is non-null and non-empty after trimming whitespace.
     *
     * @param value the String to check, may be null
     * @return true if non-null and contains at least one non-whitespace character
     */
    public static boolean hasContent(String value) {
        return value != null && !value.isBlank();
    }


    // -------------------------------------------------------------------------
    // BOOLEAN UTILITIES — safe boolean checks on possibly-null Boolean objects
    // -------------------------------------------------------------------------

    /**
     * Returns true if {@code value} is non-null and {@link Boolean#TRUE}.
     *
     * <p>Usage replaces:
     * <pre>
     *   if (flag != null &amp;&amp; flag) { ... }
     * </pre>
     *
     * @param value the possibly-null Boolean
     * @return true only when {@code value} is {@link Boolean#TRUE}
     */
    public static boolean isTrue(Boolean value) {
        return Boolean.TRUE.equals(value);
    }


    /**
     * Returns true if {@code value} is null or {@link Boolean#FALSE}.
     *
     * @param value the possibly-null Boolean
     * @return true when {@code value} is null or false
     */
    public static boolean isFalseOrNull(Boolean value) {
        return !Boolean.TRUE.equals(value);
    }


    // -------------------------------------------------------------------------
    // FIRST-NON-NULL — select the first non-null value from a sequence
    // -------------------------------------------------------------------------

    /**
     * Returns the first non-null value from {@code first} and {@code second}.
     *
     * <p>Usage replaces:
     * <pre>
     *   String result = first != null ? first : second;
     * </pre>
     *
     * @param <T>    the value type
     * @param first  the preferred value, may be null
     * @param second the fallback value, may be null
     * @return {@code first} if non-null, else {@code second}
     */
    public static <T> T firstNonNull(T first, T second) {
        return first != null ? first : second;
    }


    /**
     * Returns the first non-null value from the provided arguments.
     *
     * @param <T>    the value type
     * @param values the candidates, evaluated in order
     * @return the first non-null candidate, or null if all are null
     */
    @SafeVarargs
    public static <T> T firstNonNull(T... values) {
        for (T v : values) {
            if (v != null) return v;
        }
        return null;
    }
}
