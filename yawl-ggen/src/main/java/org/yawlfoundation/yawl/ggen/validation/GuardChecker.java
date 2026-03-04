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

package org.yawlfoundation.yawl.ggen.validation;

import org.yawlfoundation.yawl.ggen.validation.model.GuardViolation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Interface for detecting guard violations in Java source code.
 *
 * <p>Implementations of this interface detect specific guard patterns (TODO, mock, stub, etc.)
 * in Java source files. Each checker is responsible for one specific pattern type.
 *
 * <p>Key characteristics:
 * <ul>
 *   <li>Supports both single-file and batch file checking</li>
 *   <li>Provides detailed violation information including location and context</li>
 *   <li>Uses Java 25 patterns for clean, expressive implementations</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * // Using an implementation class
 * GuardChecker todoRegexChecker = new RegexGuardChecker("H_TODO", "//\\s*TODO");
 *
 * // Checking multiple files
 * List<Path> files = List.of(Path.of("Class1.java"), Path.of("Class2.java"));
 * List<GuardViolation> violations = todoRegexChecker.checkAll(files);
 * }</pre>
 *
 * <p>Implementations should follow these contracts:
 * <ul>
 *   <li>Must return an empty list for files with no violations</li>
 *   <li>Must throw GuardCheckerException for IO/parsing errors</li>
 *   <li>Must provide accurate line numbers for violations</li>
 *   <li>Should be thread-safe for concurrent file checking</li>
 * </ul>
 *
 * @see Severity
 * @see GuardViolation
 * @see GuardCheckerException
 * @since 1.0
 */
public interface GuardChecker {

    /**
     * Checks a single Java source file for guard violations.
     *
     * <p>This is the core method that implementations must implement. It should
     * analyze the specified Java source file and return a list of all guard
     * violations found according to the specific pattern this checker detects.
     *
     * <p>Contract:
     * <ul>
     *   <li>Must return an empty list if no violations are found</li>
     *   <li>Must throw GuardCheckerException for any IO or parsing errors</li>
     *   <li>Violation line numbers must be 1-based (first line = line 1)</li>
     *   <li>Should handle large files efficiently</li>
     * </ul>
     *
     * @param javaSource the path to the Java source file to check
     * @return list of guard violations found in the file (never null)
     * @throws GuardCheckerException if there are IO or parsing errors
     * @throws IllegalArgumentException if javaSource is null
     * @throws IOException if there are file access errors not wrapped in GuardCheckerException
     */
    List<GuardViolation> check(Path javaSource) throws GuardCheckerException, IOException;

    /**
     * Returns the name of the guard pattern this checker detects.
     *
     * <p>Pattern names follow the H_ convention established in the YAWL
     * hyper-standards specification (H_TODO, H_MOCK, H_STUB, etc.).
     *
     * @return the pattern name string (e.g., "H_TODO", "H_MOCK")
     */
    String patternName();

    /**
     * Returns the severity level for violations detected by this checker.
     *
     * <p>Severity determines whether violations block code generation (FAIL)
     * or only generate warnings (WARN).
     *
     * @return the severity level for this checker's violations
     */
    Severity severity();

    /**
     * Checks multiple Java source files for guard violations.
     *
     * <p>Default implementation that applies this checker to all specified files
     * and aggregates all violations. Files are processed sequentially in the
     * order provided.
     *
     * <p>Note: For performance-critical applications, implementations may override
     * this method to provide parallel processing.
     *
     * @param files list of Java source files to check (must not be null or contain null)
     * @return aggregated list of all violations found in all files (never null)
     * @throws GuardCheckerException if any file cannot be processed
     * @throws IOException if there are file access errors
     * @throws IllegalArgumentException if files is null or contains null elements
     */
    default List<GuardViolation> checkAll(List<Path> files)
            throws GuardCheckerException, IOException {

        if (files == null) {
            throw new IllegalArgumentException("Files list cannot be null");
        }

        List<GuardViolation> allViolations = new ArrayList<>();

        for (Path file : files) {
            if (file == null) {
                throw new IllegalArgumentException("Files list cannot contain null elements");
            }

            try {
                List<GuardViolation> violations = check(file);
                allViolations.addAll(violations);
            } catch (GuardCheckerException e) {
                // Add file context to make error more specific
                throw new GuardCheckerException(
                    file,
                    patternName(),
                    "Error checking file: " + file,
                    e.getCause() != null ? e.getCause() : e
                );
            } catch (IOException e) {
                // Wrap IOException in GuardCheckerException
                throw new GuardCheckerException(
                    file,
                    patternName(),
                    "IO error checking file: " + file,
                    e
                );
            }
        }

        return allViolations;
    }

    /**
     * Creates a composed checker that runs this checker and another after it.
     *
     * <p>The composed checker will execute both checkers on the same file and
     * return the combined list of violations. This is useful for creating
     * composite checks that detect multiple related patterns.
     *
     * @param other the other checker to run after this one
     * @return a composed checker that runs both checkers
     * @throws IllegalArgumentException if other is null
     */
    default GuardChecker andThen(GuardChecker other) {
        if (other == null) {
            throw new IllegalArgumentException("Other checker cannot be null");
        }

        return new GuardChecker() {
            @Override
            public List<GuardViolation> check(Path javaSource) throws GuardCheckerException, IOException {
                List<GuardViolation> violations = List.copyOf(GuardChecker.this.check(javaSource));
                violations.addAll(other.check(javaSource));
                return violations;
            }

            @Override
            public String patternName() {
                return GuardChecker.this.patternName() + "+" + other.patternName();
            }

            @Override
            public Severity severity() {
                // Use the more severe of the two checkers
                return GuardChecker.this.severity().ordinal() >= other.severity().ordinal()
                    ? GuardChecker.this.severity()
                    : other.severity();
            }
        };
    }

    /**
     * Creates a checker function that converts this GuardChecker to a function.
     *
     * <p>Utility method for functional-style usage that allows GuardCheckers
     * to be used where Function<Path, List<GuardViolation>> is expected.
     *
     * @return a function equivalent to this GuardChecker
     */
    default Function<Path, List<GuardViolation>> asFunction() {
        return path -> {
            try {
                return check(path);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (GuardCheckerException e) {
                throw new RuntimeException(e);
            }
        };
    }
}