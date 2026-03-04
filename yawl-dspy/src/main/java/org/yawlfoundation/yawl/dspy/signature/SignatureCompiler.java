/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.dspy.signature;

import java.lang.annotation.*;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Compiles annotated interfaces into runtime Signatures.
 *
 * <p>This is the core of DSPy-style type-safe signatures in Java 25.
 *
 * <h2>Example:</h2>
 * {@snippet :
 * @SigDef(description = "Predict case outcome")
 * interface CasePredictor extends SignatureTemplate {
 *     @In(desc = "case duration ms") long durationMs();
 *     @In(desc = "task count") int taskCount();
 *
 *     @Out(desc = "predicted outcome") String outcome();
 *     @Out(desc = "confidence score") double confidence();
 * }
 *
 * // Compile and use
 * Signature sig = SignatureCompiler.compile(CasePredictor.class);
 * }
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class SignatureCompiler {

    private static final ConcurrentHashMap<Class<?>, Signature> CACHE = new ConcurrentHashMap<>();

    private SignatureCompiler() {} // utility class

    /**
     * Compile a signature template interface to a Signature.
     * Results are cached for performance.
     */
    public static Signature compile(Class<? extends SignatureTemplate> templateClass) {
        return CACHE.computeIfAbsent(templateClass, SignatureCompiler::doCompile);
    }

    private static Signature doCompile(Class<?> templateClass) {
        // Validate it's an interface
        if (!templateClass.isInterface()) {
            throw new IllegalArgumentException(
                "Signature template must be an interface: " + templateClass.getName());
        }

        // Extract description from @SigDef
        SigDef sigDef = templateClass.getAnnotation(SigDef.class);
        String description = sigDef != null ? sigDef.description() : templateClass.getSimpleName();

        // Extract instructions from @SigDef
        String instructions = sigDef != null && sigDef.instructions().length > 0
            ? String.join("\n", sigDef.instructions())
            : "";

        // Extract input and output fields from methods
        List<InputField> inputs = new ArrayList<>();
        List<OutputField> outputs = new ArrayList<>();

        for (Method method : templateClass.getMethods()) {
            // Skip Object methods and default methods
            if (method.getDeclaringClass() == Object.class) continue;
            if (method.isDefault()) continue;

            String fieldName = method.getName();
            Class<?> fieldType = method.getReturnType();

            // Check for @In annotation
            In inAnnotation = method.getAnnotation(In.class);
            if (inAnnotation != null) {
                Class<?> type = inAnnotation.type() != String.class || fieldType == void.class
                    ? inAnnotation.type()
                    : fieldType;
                inputs.add(new InputField(fieldName, inAnnotation.desc(), type, inAnnotation.optional()));
                continue;
            }

            // Check for @Out annotation
            Out outAnnotation = method.getAnnotation(Out.class);
            if (outAnnotation != null) {
                Class<?> type = outAnnotation.type() != String.class || fieldType == void.class
                    ? outAnnotation.type()
                    : fieldType;
                outputs.add(new OutputField(fieldName, outAnnotation.desc(), type, outAnnotation.reasoning()));
            }
        }

        // Validate
        if (inputs.isEmpty()) {
            throw new IllegalArgumentException(
                "Signature must have at least one @In field: " + templateClass.getName());
        }
        if (outputs.isEmpty()) {
            throw new IllegalArgumentException(
                "Signature must have at least one @Out field: " + templateClass.getName());
        }

        return new Signature.Impl(description, inputs, outputs, instructions, List.of());
    }

    /**
     * Check if a class is a valid signature template.
     */
    public static boolean isValidTemplate(Class<?> clazz) {
        if (!clazz.isInterface()) return false;
        if (clazz.getAnnotation(SigDef.class) == null) return false;

        boolean hasIn = false;
        boolean hasOut = false;

        for (Method method : clazz.getMethods()) {
            if (method.getAnnotation(In.class) != null) hasIn = true;
            if (method.getAnnotation(Out.class) != null) hasOut = true;
        }

        return hasIn && hasOut;
    }

    // ── Annotations ──────────────────────────────────────────────────────────

    /**
     * Marks an interface as a signature definition.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface SigDef {
        String description();
        String[] instructions() default {};
    }

    /**
     * Marks a method as an input field.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface In {
        String desc();
        Class<?> type() default String.class;
        boolean optional() default false;
    }

    /**
     * Marks a method as an output field.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Out {
        String desc();
        Class<?> type() default String.class;
        boolean reasoning() default false;
    }
}
