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

package org.yawlfoundation.yawl.signature.compiler;

import org.yawlfoundation.yawl.signature.model.*;
import org.yawlfoundation.yawl.signature.model.annotations.*;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Compiles annotated interfaces into runtime Signatures.
 *
 * <p>Uses reflection to extract field metadata from {@link In},
 * {@link Out}, and {@link SigDef} annotations.
 * Results are cached for performance.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class SignatureCompiler {

    private static final ConcurrentHashMap<Class<?>, Signature> CACHE = new ConcurrentHashMap<>();

    private SignatureCompiler() {} // utility class

    /**
     * Compile a signature template class to a runtime Signature.
     *
     * @param templateClass the annotated interface
     * @return compiled Signature
     * @throws IllegalArgumentException if the class is not properly annotated
     */
    public static Signature compile(Class<?> templateClass) {
        return CACHE.computeIfAbsent(templateClass, SignatureCompiler::doCompile);
    }

    /**
     * Clear the compilation cache.
     */
    public static void clearCache() {
        CACHE.clear();
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

        // Extract input and output fields from methods
        List<Signature.InputField> inputs = new ArrayList<>();
        List<Signature.OutputField> outputs = new ArrayList<>();

        for (Method method : templateClass.getMethods()) {
            // Skip Object methods
            if (method.getDeclaringClass() == Object.class) continue;
            // Skip default methods from SignatureTemplate
            if (method.isDefault()) continue;

            String fieldName = method.getName();
            Class<?> fieldType = method.getReturnType();

            // Check for @In annotation
            In inAnnotation = method.getAnnotation(In.class);
            if (inAnnotation != null) {
                inputs.add(new Signature.InputField(fieldName, inAnnotation.desc()));
                continue;
            }

            // Check for @Out annotation
            Out outAnnotation = method.getAnnotation(Out.class);
            if (outAnnotation != null) {
                outputs.add(new Signature.OutputField(fieldName, outAnnotation.desc()));
            }
        }

        return new Signature(description, inputs, outputs);
    }
}
