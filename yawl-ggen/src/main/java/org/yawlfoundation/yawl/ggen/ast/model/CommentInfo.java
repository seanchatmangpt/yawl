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

import java.util.Objects;

/**
 * Represents information about a comment extracted from Java AST.
 *
 * <p>This class encapsulates comment metadata including type (line/block),
 * line number, and the comment content.
 *
 * @since 1.0
 */
public record CommentInfo(
    String type,  // "line" or "block"
    int lineNumber,
    String content,
    String rawContent
) {

    /**
     * Creates a new CommentInfo instance for a line comment.
     *
     * @param lineNumber the line number where the comment appears (must be >= 0)
     * @param content the cleaned comment content without // or /* *&#47; markers (must not be null)
     * @param rawContent the raw comment content including // or /* *&#47; markers (must not be null)
     * @return a new CommentInfo instance with type "line"
     * @throws NullPointerException if content or rawContent is null
     * @throws IllegalArgumentException if line number is negative
     */
    public static CommentInfo lineComment(int lineNumber, String content, String rawContent) {
        Objects.requireNonNull(content, "Comment content cannot be null");
        Objects.requireNonNull(rawContent, "Raw comment content cannot be null");

        if (lineNumber < 0) {
            throw new IllegalArgumentException("Line number must be >= 0, got: " + lineNumber);
        }

        return new CommentInfo("line", lineNumber, content, rawContent);
    }

    /**
     * Creates a new CommentInfo instance for a block comment.
     *
     * @param startLineNumber the line number where the comment starts (must be >= 0)
     * @param content the cleaned comment content without /* *&#47; markers (must not be null)
     * @param rawContent the raw comment content including /* *&#47; markers (must not be null)
     * @return a new CommentInfo instance with type "block"
     * @throws NullPointerException if content or rawContent is null
     * @throws IllegalArgumentException if line number is negative
     */
    public static CommentInfo blockComment(int startLineNumber, String content, String rawContent) {
        Objects.requireNonNull(content, "Comment content cannot be null");
        Objects.requireNonNull(rawContent, "Raw comment content cannot be null");

        if (startLineNumber < 0) {
            throw new IllegalArgumentException("Start line number must be >= 0, got: " + startLineNumber);
        }

        return new CommentInfo("block", startLineNumber, content, rawContent);
    }

    /**
     * Checks if this is a line comment.
     *
     * @return true if this is a line comment
     */
    public boolean isLineComment() {
        return "line".equals(type);
    }

    /**
     * Checks if this is a block comment.
     *
     * @return true if this is a block comment
     */
    public boolean isBlockComment() {
        return "block".equals(type);
    }

    /**
     * Gets the cleaned comment content (without comment markers).
     *
     * @return the comment content
     */
    public String getContent() {
        return content;
    }

    /**
     * Gets the raw comment content (with comment markers).
     *
     * @return the raw comment content
     */
    public String getRawContent() {
        return rawContent;
    }

    /**
     * Checks if the comment contains TODO, FIXME, or similar markers.
     *
     * @return true if the comment contains a work marker
     */
    public boolean containsWorkMarker() {
        String lowerContent = content.toLowerCase();
        return lowerContent.contains("todo") ||
               lowerContent.contains("fixme") ||
               lowerContent.contains("hack") ||
               lowerContent.contains("xxx") ||
               lowerContent.contains("later") ||
               lowerContent.contains("future") ||
               lowerContent.contains("@incomplete") ||
               lowerContent.contains("@stub") ||
               lowerContent.contains("placeholder");
    }

    /**
     * Checks if the comment contains documentation-related content.
     *
     * @return true if the comment appears to be documentation
     */
    public boolean isDocumentation() {
        String lowerContent = content.toLowerCase();
        return lowerContent.contains("@param") ||
               lowerContent.contains("@return") ||
               lowerContent.contains("@throws") ||
               lowerContent.contains("@deprecated") ||
               lowerContent.contains("@since") ||
               lowerContent.contains("@author") ||
               lowerContent.contains("@version");
    }

    /**
     * Checks if the comment is empty or contains only whitespace.
     *
     * @return true if the comment is empty
     */
    public boolean isEmpty() {
        return content.trim().isEmpty();
    }

    /**
     * Gets the trimmed comment content.
     *
     * @return trimmed content
     */
    public String getTrimmedContent() {
        return content.trim();
    }

    /**
     * Gets the comment content without leading and trailing asterisks (common in block comments).
     *
     * @return cleaned content
     */
    public String getCleanedContent() {
        String cleaned = content.trim();

        // Remove leading * from each line in block comments
        if (isBlockComment()) {
            String[] lines = cleaned.split("\n");
            StringBuilder sb = new StringBuilder();
            for (String line : lines) {
                String trimmedLine = line.trim();
                if (trimmedLine.startsWith("*")) {
                    sb.append(trimmedLine.substring(1).trim());
                } else {
                    sb.append(trimmedLine);
                }
                if (!line.equals(lines[lines.length - 1])) {
                    sb.append("\n");
                }
            }
            cleaned = sb.toString();
        }

        return cleaned.trim();
    }

    /**
     * Creates a short summary of the comment (first 50 characters).
     *
     * @return a summary of the comment
     */
    public String getSummary() {
        String summary = getCleanedContent();
        if (summary.length() > 50) {
            summary = summary.substring(0, 47) + "...";
        }
        return summary;
    }
}