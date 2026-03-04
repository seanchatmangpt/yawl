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

import java.io.IOException;
import java.nio.file.Path;

/**
 * Exception thrown when guard checking encounters errors during validation.
 *
 * <p>This exception wraps underlying IO and parsing errors that occur during
 * guard validation. It provides context about which file and which guard pattern
 * caused the error.
 *
 * <p>Usage scenarios:
 * <ul>
 *   <li>File not found or permission issues</li>
 *   <li>Invalid Java syntax preventing AST parsing</li>
 *   <li>SPARQL query execution failures</li>
 *   <li>RDF model conversion errors</li>
 * </ul>
 *
 * @since 1.0
 */
public class GuardCheckerException extends Exception {

    private final Path problematicFile;
    private final String patternName;

    /**
     * Constructs a GuardCheckerException with a message and cause.
     *
     * @param message the detail message
     * @param cause the underlying cause
     */
    public GuardCheckerException(String message, Throwable cause) {
        super(message, cause);
        this.problematicFile = null;
        this.patternName = null;
    }

    /**
     * Constructs a GuardCheckerException with context about the problematic file and pattern.
     *
     * @param problematicFile the file that caused the error
     * @param patternName the guard pattern being checked (or null if unknown)
     * @param message the detail message
     * @param cause the underlying cause
     */
    public GuardCheckerException(Path problematicFile, String patternName,
                                 String message, Throwable cause) {
        super(buildMessage(problematicFile, patternName, message), cause);
        this.problematicFile = problematicFile;
        this.patternName = patternName;
    }

    /**
     * Constructs a GuardCheckerException with a message only.
     *
     * @param message the detail message
     */
    public GuardCheckerException(String message) {
        super(message);
        this.problematicFile = null;
        this.patternName = null;
    }

    /**
     * Constructs a GuardCheckerException with a message, file, and pattern.
     *
     * @param problematicFile the file that caused the error
     * @param patternName the guard pattern being checked (or null if unknown)
     * @param message the detail message
     */
    public GuardCheckerException(Path problematicFile, String patternName, String message) {
        super(buildMessage(problematicFile, patternName, message));
        this.problematicFile = problematicFile;
        this.patternName = patternName;
    }

    /**
     * Returns the problematic file that caused the exception.
     *
     * @return the problematic file path, or null if not specified
     */
    public Path getProblematicFile() {
        return problematicFile;
    }

    /**
     * Returns the guard pattern name that was being checked.
     *
     * @return the pattern name, or null if not specified
     */
    public String getPatternName() {
        return patternName;
    }

    /**
     * Returns whether this exception is related to a specific file.
     *
     * @return true if a problematic file is specified, false otherwise
     */
    public boolean hasFileContext() {
        return problematicFile != null;
    }

    /**
     * Returns whether this exception is related to a specific pattern.
     *
     * @return true if a pattern name is specified, false otherwise
     */
    public boolean hasPatternContext() {
        return patternName != null;
    }

    private static String buildMessage(Path file, String pattern, String message) {
        StringBuilder sb = new StringBuilder();
        if (file != null) {
            sb.append("File: ").append(file).append(" - ");
        }
        if (pattern != null) {
            sb.append("Pattern: ").append(pattern).append(" - ");
        }
        sb.append(message);
        return sb.toString();
    }
}