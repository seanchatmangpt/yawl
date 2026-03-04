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
 * Represents information about a class extracted from Java AST.
 *
 * <p>This class encapsulates class metadata including name, modifiers,
 * line number, package name, and methods/fields.
 *
 * @since 1.0
 */
public record ClassInfo(
    String name,
    String packageName,
    List<String> modifiers,
    int lineNumber,
    List<MethodInfo> methods,
    List<ClassInfo> innerClasses,
    List<CommentInfo> comments
) {

    /**
     * Creates a new ClassInfo instance.
     *
     * @param name the class name (must not be null)
     * @param packageName the package name (empty string for default package)
     * @param modifiers list of modifiers (e.g., "public", "final") (must not be null)
     * @param lineNumber the line number where the class starts (must be >= 0)
     * @param methods list of methods in this class (must not be null)
     * @param innerClasses list of inner classes (must not be null)
     * @param comments list of comments associated with this class (must not be null)
     * @throws NullPointerException if any required parameter is null
     * @throws IllegalArgumentException if line number is negative
     */
    public ClassInfo {
        Objects.requireNonNull(name, "Class name cannot be null");
        Objects.requireNonNull(modifiers, "Modifiers cannot be null");
        Objects.requireNonNull(methods, "Methods cannot be null");
        Objects.requireNonNull(innerClasses, "Inner classes cannot be null");
        Objects.requireNonNull(comments, "Comments cannot be null");

        if (lineNumber < 0) {
            throw new IllegalArgumentException("Line number must be >= 0, got: " + lineNumber);
        }
    }

    /**
     * Gets the fully qualified class name including package.
     *
     * @return the fully qualified class name
     */
    public String getQualifiedName() {
        if (packageName != null && !packageName.isEmpty()) {
            return packageName + "." + name;
        }
        return name;
    }

    /**
     * Checks if this class is public.
     *
     * @return true if the class has "public" modifier
     */
    public boolean isPublic() {
        return modifiers.contains("public");
    }

    /**
     * Checks if this class is private.
     *
     * @return true if the class has "private" modifier
     */
    public boolean isPrivate() {
        return modifiers.contains("private");
    }

    /**
     * Checks if this class is protected.
     *
     * @return true if the class has "protected" modifier
     */
    public boolean isProtected() {
        return modifiers.contains("protected");
    }

    /**
     * Checks if this class is final.
     *
     * @return true if the class has "final" modifier
     */
    public boolean isFinal() {
        return modifiers.contains("final");
    }

    /**
     * Checks if this class is abstract.
     *
     * @return true if the class has "abstract" modifier
     */
    public boolean isAbstract() {
        return modifiers.contains("abstract");
    }

    /**
     * Checks if this class is an interface.
     *
     * @return true if this is an interface
     */
    public boolean isInterface() {
        return modifiers.contains("interface");
    }

    /**
     * Checks if this class is an enum.
     *
     * @return true if this is an enum
     */
    public boolean isEnum() {
        return modifiers.contains("enum");
    }

    /**
     * Checks if this class is a record.
     *
     * @return true if this is a record
     */
    public boolean isRecord() {
        return modifiers.contains("record");
    }

    /**
     * Checks if this class has any inner classes.
     *
     * @return true if the class has inner classes
     */
    public boolean hasInnerClasses() {
        return !innerClasses.isEmpty();
    }

    /**
     * Gets the number of inner classes.
     *
     * @return the number of inner classes
     */
    public int getInnerClassCount() {
        return innerClasses.size();
    }

    /**
     * Checks if this class has any methods.
     *
     * @return true if the class has methods
     */
    public boolean hasMethods() {
        return !methods.isEmpty();
    }

    /**
     * Gets the number of methods in this class.
     *
     * @return the number of methods
     */
    public int getMethodCount() {
        return methods.size();
    }

    /**
     * Gets the number of public methods.
     *
     * @return the number of public methods
     */
    public int getPublicMethodCount() {
        return (int) methods.stream().filter(MethodInfo::isPublic).count();
    }

    /**
     * Gets the number of private methods.
     *
     * @return the number of private methods
     */
    public int getPrivateMethodCount() {
        return (int) methods.stream().filter(MethodInfo::isPrivate).count();
    }

    /**
     * Gets the number of protected methods.
     *
     * @return the number of protected methods
     */
    public int getProtectedMethodCount() {
        return (int) methods.stream().filter(MethodInfo::isProtected).count();
    }

    /**
     * Gets the number of static methods.
     *
     * @return the number of static methods
     */
    public int getStaticMethodCount() {
        return (int) methods.stream().filter(MethodInfo::isStatic).count();
    }

    /**
     * Checks if the class has comments.
     *
     * @return true if the class has comments
     */
    public boolean hasComments() {
        return !comments.isEmpty();
    }

    /**
     * Gets the number of comments associated with this class.
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

    /**
     * Checks if the class appears to be a mock class (based on naming patterns).
     *
     * @return true if the class name suggests it's a mock
     */
    public boolean isLikelyMock() {
        String lowerName = name.toLowerCase();
        return lowerName.contains("mock") ||
               lowerName.contains("stub") ||
               lowerName.contains("fake") ||
               lowerName.contains("demo") ||
               lowerName.contains("sample");
    }

    /**
     * Gets the total number of lines spanned by this class (estimate).
     *
     * @return estimated total lines (rough approximation)
     */
    public int estimateTotalLines() {
        // This is a rough estimate - in a real implementation, we'd track
        // the actual end line number from the AST
        return Math.max(1, methods.stream().mapToInt(m -> m.body().split("\n").length).sum());
    }
}