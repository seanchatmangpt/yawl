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

package org.yawlfoundation.yawl.integration.mcp.safe;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Versioned catalog of SAFe Responsible AI Non-Functional Requirements (NFRs).
 *
 * Enforces 6 Responsible AI attributes as static code policies:
 * - PRIVACY: no plaintext personal data markers in production code
 * - FAIRNESS: require explicit bias check or test for model-related code
 * - SECURITY: no hardcoded credentials (passwords, API keys)
 * - RELIABILITY: no bare catch(Exception) with empty blocks
 * - TRANSPARENCY: public AI methods must have non-empty Javadoc
 * - ACCOUNTABILITY: Agent/Model classes need owner annotation or comment
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class NfrCatalog {
    private static final String VERSION = "1.0.0";

    // NFR regex patterns
    private static final Pattern HARDCODED_PASSWORD =
        Pattern.compile("password\\s*=\\s*\"[^\"]+\"|apiKey\\s*=\\s*\"[^\"]+\"");
    private static final Pattern EMPTY_CATCH =
        Pattern.compile("catch\\s*\\(\\s*Exception\\s+\\w+\\s*\\)\\s*\\{\\s*\\}");
    private static final Pattern OWNER_PATTERN =
        Pattern.compile("@Owner|@Responsible|@AccountableParty");

    /**
     * Validate a Java source file against all 6 NFR policies.
     *
     * @param javaSource path to Java file to validate
     * @return list of violations found (empty if all policies satisfied)
     */
    public List<NfrViolation> validate(Path javaSource) {
        List<NfrViolation> violations = new ArrayList<>();

        try {
            String content = Files.readString(javaSource, StandardCharsets.UTF_8);
            String[] lines = content.split("\n", -1);

            for (int lineNum = 0; lineNum < lines.length; lineNum++) {
                String line = lines[lineNum];
                int actualLineNumber = lineNum + 1;

                // SECURITY: hardcoded passwords or API keys
                if (HARDCODED_PASSWORD.matcher(line).find()) {
                    violations.add(new NfrViolation(
                        "SECURITY",
                        javaSource.toString(),
                        actualLineNumber,
                        "hardcoded credentials detected. Use environment variables or secure vaults."));
                }

                // RELIABILITY: bare catch(Exception e) {}
                if (EMPTY_CATCH.matcher(line).find()) {
                    violations.add(new NfrViolation(
                        "RELIABILITY",
                        javaSource.toString(),
                        actualLineNumber,
                        "Empty exception catch block. Log or propagate exception."));
                }

                // TRANSPARENCY: check for missing Javadoc on public AI methods
                if (line.trim().startsWith("public") && line.contains("Agent") || line.contains("Model")) {
                    // Look backward for Javadoc
                    boolean hasJavadoc = false;
                    for (int prevIdx = lineNum - 1; prevIdx >= Math.max(0, lineNum - 5); prevIdx--) {
                        if (lines[prevIdx].trim().contains("*/")) {
                            hasJavadoc = true;
                            break;
                        }
                    }
                    if (!hasJavadoc && (line.contains("public") && line.trim().startsWith("public"))) {
                        violations.add(new NfrViolation(
                            "TRANSPARENCY",
                            javaSource.toString(),
                            actualLineNumber,
                            "Public AI-related method missing Javadoc. Document behavior, inputs, outputs."));
                    }
                }

                // ACCOUNTABILITY: Agent/Model classes without owner
                if ((line.contains("class ") && (line.contains("Agent") || line.contains("Model"))) &&
                    !line.contains("abstract")) {
                    // Check following few lines for owner annotation
                    boolean hasOwner = false;
                    for (int nextIdx = lineNum + 1; nextIdx < Math.min(lines.length, lineNum + 10); nextIdx++) {
                        if (OWNER_PATTERN.matcher(lines[nextIdx]).find() || lines[nextIdx].contains("owner")) {
                            hasOwner = true;
                            break;
                        }
                    }
                    if (!hasOwner) {
                        violations.add(new NfrViolation(
                            "ACCOUNTABILITY",
                            javaSource.toString(),
                            actualLineNumber,
                            "Agent/Model class needs @Owner annotation or ownership comment (e.g., owner=alice@example.com)."));
                    }
                }

                // FAIRNESS: model-related code needs bias check
                if ((line.contains("prediction") || line.contains("inference") || line.contains("decision")) &&
                    (line.contains("return") || line.contains("="))) {
                    // Very permissive: look for any comment or test about bias
                    boolean hasBiasCheck = false;
                    for (int contextIdx = Math.max(0, lineNum - 2);
                         contextIdx <= Math.min(lines.length - 1, lineNum + 2); contextIdx++) {
                        if (lines[contextIdx].toLowerCase().contains("bias") ||
                            lines[contextIdx].toLowerCase().contains("fairness") ||
                            lines[contextIdx].toLowerCase().contains("discrimination")) {
                            hasBiasCheck = true;
                            break;
                        }
                    }
                    if (!hasBiasCheck) {
                        violations.add(new NfrViolation(
                            "FAIRNESS",
                            javaSource.toString(),
                            actualLineNumber,
                            "Model prediction/inference code should include bias check or fairness test reference."));
                    }
                }

                // PRIVACY: check for personal data markers in production paths
                if ((line.contains("System.out") || line.contains("System.err") || line.contains("log.")) &&
                    (line.contains("name") || line.contains("email") || line.contains("ssn") ||
                     line.contains("phone") || line.contains("address"))) {
                    violations.add(new NfrViolation(
                        "PRIVACY",
                        javaSource.toString(),
                        actualLineNumber,
                        "Personal data exposure risk: logging name/email/ssn/phone/address. Sanitize before output."));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read source file: " + javaSource, e);
        }

        return violations;
    }

    /**
     * Get the NFR policy documentation.
     *
     * @return JSON string describing all 6 NFR attributes and their policies
     */
    public String getPolicy() {
        return """
            {
              "version": "1.0.0",
              "attributes": [
                {
                  "name": "PRIVACY",
                  "description": "No plaintext personal data markers (name, email, SSN) in production logging",
                  "examples": ["System.out.println(user.email)", "log.info('Processing ' + ssn)"]
                },
                {
                  "name": "FAIRNESS",
                  "description": "Model prediction/inference code must have explicit bias check or fairness test reference",
                  "examples": ["decision = model.predict(features) // needs bias test"]
                },
                {
                  "name": "SECURITY",
                  "description": "No hardcoded credentials (password, apiKey). Use environment variables or vaults.",
                  "examples": ["password = \\"secret\\"; apiKey = \\"key123\\""]
                },
                {
                  "name": "RELIABILITY",
                  "description": "No bare catch(Exception) with empty blocks. Log or propagate exception.",
                  "examples": ["catch (Exception e) { }"]
                },
                {
                  "name": "TRANSPARENCY",
                  "description": "Public AI-related methods must have non-empty Javadoc describing behavior",
                  "examples": ["public Agent performDecision() without Javadoc"]
                },
                {
                  "name": "ACCOUNTABILITY",
                  "description": "Classes named *Agent or *Model must declare @Owner annotation or ownership comment",
                  "examples": ["class DecisionAgent { ... } without @Owner"]
                }
              ]
            }
            """;
    }

    /**
     * Get the version of this NFR catalog.
     *
     * @return semantic version string
     */
    public String version() {
        return VERSION;
    }
}
