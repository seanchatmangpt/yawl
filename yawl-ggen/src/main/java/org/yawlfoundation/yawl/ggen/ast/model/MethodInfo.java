/*
 * Copyright 2026 YAWL Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ggen.ast.model;

import java.util.List;
import java.util.Objects;

/**
 * Represents information about a method extracted from Java AST.
 *
 * <p>This class encapsulates method metadata including name, return type,
 * parameter types, modifiers, line number, and method body content.
 *
 * @since 1.0
 */
public record MethodInfo(
    String name,
    String returnType,
    List<String> parameterTypes,
    List<String> parameterNames,
    List<String> modifiers,
    int lineNumber,
    String body,
    List<CommentInfo> comments
) {

    /**
     * Creates a new MethodInfo instance.
     *
     * @param name the method name (must not be null)
     * @param returnType the return type (must not be null)
     * @param parameterTypes list of parameter types (must not be null)
     * @param parameterNames list of parameter names (must not be null)
     * @param modifiers list of modifiers (e.g., "public", "static") (must not be null)
     * @param lineNumber the line number where the method starts (must be >= 0)
     * @param body the method body content (must not be null)
     * @param comments list of comments associated with this method (must not be null)
     * @throws NullPointerException if any required parameter is null
     * @throws IllegalArgumentException if line number is negative
     */
    public MethodInfo {
        Objects.requireNonNull(name, "Method name cannot be null");
        Objects.requireNonNull(returnType, "Return type cannot be null");
        Objects.requireNonNull(parameterTypes, "Parameter types cannot be null");
        Objects.requireNonNull(parameterNames, "Parameter names cannot be null");
        Objects.requireNonNull(modifiers, "Modifiers cannot be null");
        Objects.requireNonNull(body, "Method body cannot be null");
        Objects.requireNonNull(comments, "Comments cannot be null");

        if (lineNumber < 0) {
            throw new IllegalArgumentException("Line number must be >= 0, got: " + lineNumber);
        }
    }

    /**
     * Gets a formatted signature of this method.
     *
     * @return the method signature in format: "returnType name(paramTypes)"
     */
    public String getSignature() {
        StringBuilder sb = new StringBuilder();

        // Append modifiers
        if (!modifiers.isEmpty()) {
            sb.append(String.join(" ", modifiers)).append(" ");
        }

        // Append return type
        sb.append(returnType).append(" ");

        // Append method name
        sb.append(name).append("(");

        // Append parameters
        for (int i = 0; i < parameterTypes.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(parameterTypes.get(i));
            if (i < parameterNames.size() && parameterNames.get(i) != null) {
                sb.append(" ").append(parameterNames.get(i));
            }
        }

        sb.append(")");

        return sb.toString();
    }

    /**
     * Gets the fully qualified signature including class context if available.
     *
     * @return the fully qualified method signature
     */
    public String getFullyQualifiedSignature() {
        return getSignature(); // For now, just returns the signature
        // Could be extended to include class name when available
    }

    /**
     * Checks if this method is public.
     *
     * @return true if the method has "public" modifier
     */
    public boolean isPublic() {
        return modifiers.contains("public");
    }

    /**
     * Checks if this method is private.
     *
     * @return true if the method has "private" modifier
     */
    public boolean isPrivate() {
        return modifiers.contains("private");
    }

    /**
     * Checks if this method is protected.
     *
     * @return true if the method has "protected" modifier
     */
    public boolean isProtected() {
        return modifiers.contains("protected");
    }

    /**
     * Checks if this method is static.
     *
     * @return true if the method has "static" modifier
     */
    public boolean isStatic() {
        return modifiers.contains("static");
    }

    /**
     * Checks if this method is synchronized.
     *
     * @return true if the method has "synchronized" modifier
     */
    public boolean isSynchronized() {
        return modifiers.contains("synchronized");
    }

    /**
     * Checks if this method is final.
     *
     * @return true if the method has "final" modifier
     */
    public boolean isFinal() {
        return modifiers.contains("final");
    }

    /**
     * Gets the number of parameters.
     *
     * @return the number of parameters
     */
    public int getParameterCount() {
        return parameterTypes.size();
    }

    /**
     * Gets the complete parameter list as a single string.
     *
     * @return parameters formatted as "type1 name1, type2 name2"
     */
    public String getParametersString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parameterTypes.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(parameterTypes.get(i));
            if (i < parameterNames.size() && parameterNames.get(i) != null) {
                sb.append(" ").append(parameterNames.get(i));
            }
        }
        return sb.toString();
    }

    /**
     * Checks if this method has any comments.
     *
     * @return true if the method has comments
     */
    public boolean hasComments() {
        return !comments.isEmpty();
    }

    /**
     * Gets the number of comments associated with this method.
     *
     * @return the number of comments
     */
    public int getCommentCount() {
        return comments.size();
    }

    /**
     * Gets the first comment if available.
     *
     * @return the first comment or null if no comments exist
     */
    public CommentInfo getFirstComment() {
        return comments.isEmpty() ? null : comments.get(0);
    }
}