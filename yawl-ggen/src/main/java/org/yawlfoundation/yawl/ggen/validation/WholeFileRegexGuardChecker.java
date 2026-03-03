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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Regex-based guard checker that reads the entire file as a string.
 * This enables detection of multi-line patterns that line-by-line regex cannot match.
 *
 * <p>Use cases:
 * <ul>
 *   <li>H_SWALLOWED: Empty catch blocks spanning multiple lines</li>
 *   <li>Multi-line comment patterns</li>
 *   <li>Code patterns with arbitrary whitespace</li>
 * </ul>
 *
 * @since 1.0
 */
public class WholeFileRegexGuardChecker implements GuardChecker {

    private final String patternName;
    private final Pattern regexPattern;
    private final Severity severity;

    /**
     * Constructs a new WholeFileRegexGuardChecker with FAIL severity.
     *
     * @param patternName the name of the guard pattern (e.g., "H_SWALLOWED")
     * @param regex the regex pattern to match against entire file content
     */
    public WholeFileRegexGuardChecker(String patternName, String regex) {
        this(patternName, regex, Severity.FAIL);
    }

    /**
     * Constructs a new WholeFileRegexGuardChecker with specified severity.
     *
     * @param patternName the name of the guard pattern (e.g., "H_SWALLOWED")
     * @param regex the regex pattern to match against entire file content
     * @param severity the severity level for violations
     */
    public WholeFileRegexGuardChecker(String patternName, String regex, Severity severity) {
        this.patternName = patternName;
        this.regexPattern = Pattern.compile(regex, Pattern.DOTALL);
        this.severity = severity;
    }

    @Override
    public List<GuardViolation> check(Path javaSource) throws IOException {
        List<GuardViolation> violations = new ArrayList<>();

        String content = Files.readString(javaSource);
        Matcher matcher = regexPattern.matcher(content);

        while (matcher.find()) {
            // Calculate line number from character position
            int line = calculateLineNumber(content, matcher.start());
            String matchedText = matcher.group();

            violations.add(new GuardViolation(
                patternName,
                severity,
                javaSource.toString(),
                line,
                matchedText.trim()
            ));
        }

        return violations;
    }

    /**
     * Calculate line number from character position in file content.
     *
     * @param content the full file content
     * @param charPosition the character position (0-based)
     * @return line number (1-based)
     */
    private int calculateLineNumber(String content, int charPosition) {
        int line = 1;
        for (int i = 0; i < charPosition && i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    @Override
    public String patternName() {
        return patternName;
    }

    @Override
    public Severity severity() {
        return severity;
    }
}
