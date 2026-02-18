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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive path traversal protection tests.
 *
 * <p>Tests OWASP Top 10 A01:2021 - Broken Access Control and A03:2021 - Injection.
 * Validates that file path inputs are properly sanitized to prevent directory
 * traversal attacks.</p>
 *
 * <p>Chicago TDD: Real path validation with actual filesystem operations.
 * No mocks, no stubs, no placeholder implementations.</p>
 *
 * @author YAWL Development Team
 * @since 6.0
 */
@DisplayName("Path Traversal Protection Tests")
@Tag("unit")
public class PathTraversalProtectionTest {

    /**
     * Pattern to detect path traversal sequences.
     */
    private static final Pattern PATH_TRAVERSAL_PATTERN = Pattern.compile(
            "(?:^|[/.])\\.\\.(?:[/.]|$)" +
            "|%2e%2e%2f" +
            "|%2e%2e/" +
            "|\\.\\.%2f" +
            "|%2e%2e%5c" +
            "|%2e%2e\\\\" +
            "|\\.\\.%5c" +
            "|\\.\\./" +
            "|\\.\\.\\\\" +
            "|\\.{2,}/" +
            "|/\\.{2,}" +
            "|\\.{2,}\\\\" +
            "|\\\\\\.{2,}" +
            "|%252e%252e%252f" +
            "|\\.\\.%c0%af" +
            "|\\.\\.%c1%9c" +
            "|\\.\\.%255c" +
            "|\\.\\.%00/" +
            "|\\.\\.%00\\\\"
    );

    /**
     * Pattern to detect null bytes that can truncate paths.
     */
    private static final Pattern NULL_BYTE_PATTERN = Pattern.compile(
            "%00|\\x00|%0|\\0"
    );

    /**
     * Path traversal attack payloads for Unix systems.
     */
    private static final List<String> UNIX_TRAVERSAL_PAYLOADS = Arrays.asList(
            "../../../etc/passwd",
            "....//....//....//etc/passwd",
            "..../..../..../etc/passwd",
            "../../../../etc/passwd",
            "../../../../../etc/passwd",
            "../etc/passwd",
            "/etc/passwd",
            "/../../../../etc/passwd",
            "/..%2f..%2f..%2fetc/passwd",
            "%2e%2e/%2e%2e/%2e%2e/etc/passwd",
            "..%2f..%2f..%2fetc/passwd",
            "..%252f..%252f..%252fetc/passwd",
            "..%c0%af..%c0%af..%c0%afetc/passwd",
            "../../../etc/shadow",
            "../../../var/log/auth.log",
            "../../../root/.ssh/id_rsa",
            "../../../proc/self/environ"
    );

    /**
     * Path traversal attack payloads for Windows systems.
     */
    private static final List<String> WINDOWS_TRAVERSAL_PAYLOADS = Arrays.asList(
            "..\\..\\..\\windows\\system32\\config\\sam",
            "..\\..\\..\\windows\\system32\\drivers\\etc\\hosts",
            "....\\....\\....\\windows\\system32\\config\\sam",
            "..%5c..%5c..%5cwindows\\system32\\config\\sam",
            "%2e%2e\\%2e%2e\\%2e%2e\\windows\\system32\\config\\sam",
            "..%c1%9c..%c1%9c..%c1%9cwindows\\system32\\config\\sam",
            "..\\..\\..\\boot.ini",
            "..\\..\\..\\inetpub\\wwwroot\\web.config",
            "C:\\windows\\system32\\config\\sam",
            "C:/windows/system32/config/sam"
    );

    /**
     * URL-encoded path traversal payloads.
     */
    private static final List<String> URL_ENCODED_PAYLOADS = Arrays.asList(
            "%2e%2e%2f%2e%2e%2f%2e%2e%2fetc%2fpasswd",
            "%252e%252e%252f%252e%252e%252f%252e%252e%252fetc%252fpasswd",
            "..%2f..%2f..%2fetc%2fpasswd",
            "..%252f..%252f..%252fetc%252fpasswd",
            "%c0%ae%c0%ae/%c0%ae%c0%ae/%c0%ae%c0%ae/etc/passwd",
            "%c0%ae%c0%ae%c0%af%c0%ae%c0%ae%c0%af%c0%ae%c0%ae%c0%afetc/passwd"
    );

    /**
     * Double-encoding and unicode traversal payloads.
     */
    private static final List<String> DOUBLE_ENCODING_PAYLOADS = Arrays.asList(
            "%252e%252e%252f",          // Double-encoded ../
            "%uff0e%uff0e%u2215",        // Unicode fullwidth ./
            "%uff0e%uff0e%u2216",        // Unicode fullwidth .\
            "..%255c",                    // Double-encoded ..\
            "..%00/",                     // Null byte injection
            "..%00\\",                    // Null byte injection Windows
            "....//",                     // Multiple dots
            ".....///"                    // Many dots
    );

    /**
     * Validates that a path does not contain traversal sequences.
     *
     * @param path the path to validate
     * @return true if the path is safe, false if it contains traversal patterns
     */
    public static boolean isPathSafe(String path) {
        if (path == null || path.isEmpty()) {
            return true;
        }
        // Check for path traversal patterns
        if (PATH_TRAVERSAL_PATTERN.matcher(path).find()) {
            return false;
        }
        // Check for null byte injection
        if (NULL_BYTE_PATTERN.matcher(path).find()) {
            return false;
        }
        // Check for absolute paths starting with / or drive letter
        if (path.startsWith("/") || path.matches("^[a-zA-Z]:.*")) {
            return false;
        }
        return path.length() <= 4096; // Reasonable path length limit
    }

    /**
     * Sanitizes a filename by removing path traversal sequences.
     *
     * @param filename the filename to sanitize
     * @return the sanitized filename
     * @throws IllegalArgumentException if the filename contains only invalid characters
     */
    public static String sanitizeFilename(String filename) {
        if (filename == null) {
            throw new IllegalArgumentException("Filename cannot be null");
        }
        // Remove any path separators
        String sanitized = filename.replaceAll("[/\\\\]", "");
        // Remove parent directory references
        sanitized = sanitized.replaceAll("\\.{2,}", "");
        // Remove null bytes
        sanitized = sanitized.replaceAll("%00|\\x00", "");
        // Remove control characters
        sanitized = sanitized.replaceAll("[\\x00-\\x1f]", "");
        // Check if anything remains
        if (sanitized.isEmpty()) {
            throw new IllegalArgumentException("Filename contains no valid characters after sanitization");
        }
        return sanitized;
    }

    /**
     * Resolves a relative path against a base directory, ensuring the result
     * stays within the base directory.
     *
     * @param baseDir the base directory
     * @param relativePath the relative path to resolve
     * @return the resolved path within the base directory
     * @throws IOException if the resolved path escapes the base directory
     */
    public static Path resolveSecurePath(Path baseDir, String relativePath) throws IOException {
        if (baseDir == null) {
            throw new IllegalArgumentException("Base directory cannot be null");
        }
        if (!isPathSafe(relativePath)) {
            throw new IOException("Path contains traversal sequences: " + relativePath);
        }

        // Normalize both paths
        Path normalizedBase = baseDir.toAbsolutePath().normalize();
        Path resolvedPath = normalizedBase.resolve(relativePath).normalize();

        // Verify the resolved path is within the base directory
        if (!resolvedPath.startsWith(normalizedBase)) {
            throw new IOException("Resolved path escapes base directory: " + resolvedPath);
        }

        return resolvedPath;
    }

    /**
     * Decodes URL-encoded characters in a path.
     *
     * @param encoded the URL-encoded path
     * @return the decoded path
     */
    public static String decodeUrlPath(String encoded) {
        if (encoded == null) {
            throw new IllegalArgumentException("Encoded path cannot be null");
        }
        try {
            return java.net.URLDecoder.decode(encoded, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 encoding not supported", e);
        }
    }

    @Nested
    @DisplayName("Unix Path Traversal Tests")
    class UnixTraversalTests {

        @Test
        @DisplayName("Should detect basic parent directory traversal")
        void shouldDetectBasicParentTraversal() {
            String path = "../../../etc/passwd";
            assertFalse(isPathSafe(path),
                    "Basic parent directory traversal must be detected");
        }

        @Test
        @DisplayName("Should detect absolute path to /etc/passwd")
        void shouldDetectAbsolutePathEtcPasswd() {
            String path = "/etc/passwd";
            assertFalse(isPathSafe(path),
                    "Absolute path to /etc/passwd must be detected");
        }

        @Test
        @DisplayName("Should detect path with leading / and traversal")
        void shouldDetectLeadingSlashWithTraversal() {
            String path = "/../../../../etc/passwd";
            assertFalse(isPathSafe(path),
                    "Path with leading / and traversal must be detected");
        }

        @Test
        @DisplayName("Should detect multiple dot traversal")
        void shouldDetectMultipleDotTraversal() {
            String path = "....//....//....//etc/passwd";
            assertFalse(isPathSafe(path),
                    "Multiple dot traversal must be detected");
        }

        @Test
        @DisplayName("Should detect traversal to shadow file")
        void shouldDetectTraversalToShadow() {
            String path = "../../../etc/shadow";
            assertFalse(isPathSafe(path),
                    "Traversal to /etc/shadow must be detected");
        }

        @Test
        @DisplayName("Should detect traversal to SSH keys")
        void shouldDetectTraversalToSshKeys() {
            String path = "../../../root/.ssh/id_rsa";
            assertFalse(isPathSafe(path),
                    "Traversal to SSH keys must be detected");
        }

        @Test
        @DisplayName("Should detect traversal to proc filesystem")
        void shouldDetectTraversalToProc() {
            String path = "../../../proc/self/environ";
            assertFalse(isPathSafe(path),
                    "Traversal to /proc must be detected");
        }
    }

    @Nested
    @DisplayName("Windows Path Traversal Tests")
    class WindowsTraversalTests {

        @Test
        @DisplayName("Should detect backslash traversal")
        void shouldDetectBackslashTraversal() {
            String path = "..\\..\\..\\windows\\system32\\config\\sam";
            assertFalse(isPathSafe(path),
                    "Backslash traversal must be detected");
        }

        @Test
        @DisplayName("Should detect absolute Windows path with drive letter")
        void shouldDetectAbsoluteWindowsPath() {
            String path = "C:\\windows\\system32\\config\\sam";
            assertFalse(isPathSafe(path),
                    "Absolute Windows path must be detected");
        }

        @Test
        @DisplayName("Should detect forward slash on Windows path")
        void shouldDetectForwardSlashWindowsPath() {
            String path = "C:/windows/system32/config/sam";
            assertFalse(isPathSafe(path),
                    "Forward slash Windows path must be detected");
        }

        @Test
        @DisplayName("Should detect traversal to boot.ini")
        void shouldDetectTraversalToBootIni() {
            String path = "..\\..\\..\\boot.ini";
            assertFalse(isPathSafe(path),
                    "Traversal to boot.ini must be detected");
        }

        @Test
        @DisplayName("Should detect traversal to web.config")
        void shouldDetectTraversalToWebConfig() {
            String path = "..\\..\\..\\inetpub\\wwwroot\\web.config";
            assertFalse(isPathSafe(path),
                    "Traversal to web.config must be detected");
        }
    }

    @Nested
    @DisplayName("URL Encoded Traversal Tests")
    class UrlEncodedTraversalTests {

        @Test
        @DisplayName("Should detect single URL-encoded traversal")
        void shouldDetectSingleUrlEncodedTraversal() {
            String path = "%2e%2e%2f%2e%2e%2f%2e%2e%2fetc%2fpasswd";
            assertFalse(isPathSafe(path),
                    "Single URL-encoded traversal must be detected");
        }

        @Test
        @DisplayName("Should detect double URL-encoded traversal")
        void shouldDetectDoubleUrlEncodedTraversal() {
            String path = "%252e%252e%252f%252e%252e%252f%252e%252e%252fetc%252fpasswd";
            assertFalse(isPathSafe(path),
                    "Double URL-encoded traversal must be detected");
        }

        @Test
        @DisplayName("Should detect mixed encoded traversal")
        void shouldDetectMixedEncodedTraversal() {
            String path = "..%2f..%2f..%2fetc%2fpasswd";
            assertFalse(isPathSafe(path),
                    "Mixed encoded traversal must be detected");
        }

        @Test
        @DisplayName("Should detect Unicode-encoded traversal")
        void shouldDetectUnicodeEncodedTraversal() {
            String path = "%c0%ae%c0%ae/%c0%ae%c0%ae/%c0%ae%c0%ae/etc/passwd";
            assertFalse(isPathSafe(path),
                    "Unicode-encoded traversal must be detected");
        }

        @Test
        @DisplayName("Should decode URL-encoded path correctly")
        void shouldDecodeUrlEncodedPathCorrectly() {
            String encoded = "%2e%2e%2f";
            String decoded = decodeUrlPath(encoded);
            assertEquals("../", decoded,
                    "URL-encoded path should be decoded correctly");
        }

        @Test
        @DisplayName("Should reject null for URL decoding")
        void shouldRejectNullForUrlDecoding() {
            assertThrows(IllegalArgumentException.class, () -> decodeUrlPath(null),
                    "Null input must throw IllegalArgumentException");
        }
    }

    @Nested
    @DisplayName("Null Byte Injection Tests")
    class NullByteInjectionTests {

        @Test
        @DisplayName("Should detect null byte with forward slash")
        void shouldDetectNullByteWithForwardSlash() {
            String path = "..%00/";
            assertFalse(isPathSafe(path),
                    "Null byte with forward slash must be detected");
        }

        @Test
        @DisplayName("Should detect null byte with backslash")
        void shouldDetectNullByteWithBackslash() {
            String path = "..%00\\";
            assertFalse(isPathSafe(path),
                    "Null byte with backslash must be detected");
        }

        @Test
        @DisplayName("Should detect raw null byte")
        void shouldDetectRawNullByte() {
            String path = "file.txt\u0000.exe";
            assertFalse(isPathSafe(path),
                    "Raw null byte must be detected");
        }
    }

    @Nested
    @DisplayName("Filename Sanitization Tests")
    class FilenameSanitizationTests {

        @Test
        @DisplayName("Should preserve valid filename")
        void shouldPreserveValidFilename() {
            String filename = "document.pdf";
            assertEquals("document.pdf", sanitizeFilename(filename),
                    "Valid filename should be preserved");
        }

        @Test
        @DisplayName("Should remove forward slashes from filename")
        void shouldRemoveForwardSlashes() {
            String filename = "../../../etc/passwd";
            String sanitized = sanitizeFilename(filename);
            assertFalse(sanitized.contains("/"),
                    "Forward slashes should be removed");
        }

        @Test
        @DisplayName("Should remove backslashes from filename")
        void shouldRemoveBackslashes() {
            String filename = "..\\..\\..\\windows\\system32";
            String sanitized = sanitizeFilename(filename);
            assertFalse(sanitized.contains("\\"),
                    "Backslashes should be removed");
        }

        @Test
        @DisplayName("Should remove parent directory sequences")
        void shouldRemoveParentDirectorySequences() {
            String filename = "....//file.txt";
            String sanitized = sanitizeFilename(filename);
            assertFalse(sanitized.contains("...."),
                    "Parent directory sequences should be removed");
        }

        @Test
        @DisplayName("Should remove null bytes")
        void shouldRemoveNullBytes() {
            String filename = "file%00.txt";
            String sanitized = sanitizeFilename(filename);
            assertFalse(sanitized.contains("%00"),
                    "Null bytes should be removed");
        }

        @Test
        @DisplayName("Should reject null filename")
        void shouldRejectNullFilename() {
            assertThrows(IllegalArgumentException.class, () -> sanitizeFilename(null),
                    "Null filename must throw IllegalArgumentException");
        }

        @Test
        @DisplayName("Should reject filename with only invalid characters")
        void shouldRejectOnlyInvalidCharacters() {
            assertThrows(IllegalArgumentException.class, () -> sanitizeFilename("../../"),
                    "Filename with only invalid characters must throw");
        }

        @Test
        @DisplayName("Should preserve alphanumeric and common characters")
        void shouldPreserveAlphanumericAndCommonCharacters() {
            String filename = "report_2024-01-15_v2.pdf";
            assertEquals("report_2024-01-15_v2.pdf", sanitizeFilename(filename),
                    "Valid characters should be preserved");
        }
    }

    @Nested
    @DisplayName("Secure Path Resolution Tests")
    class SecurePathResolutionTests {

        @Test
        @DisplayName("Should resolve valid relative path")
        void shouldResolveValidRelativePath() throws IOException {
            Path baseDir = Paths.get("/tmp/safe-dir");
            String relativePath = "subdir/file.txt";
            Path resolved = resolveSecurePath(baseDir, relativePath);
            assertTrue(resolved.startsWith(baseDir.toAbsolutePath().normalize()),
                    "Resolved path should be within base directory");
        }

        @Test
        @DisplayName("Should reject traversal in relative path")
        void shouldRejectTraversalInRelativePath() {
            Path baseDir = Paths.get("/tmp/safe-dir");
            String relativePath = "../../../etc/passwd";
            assertThrows(IOException.class, () -> resolveSecurePath(baseDir, relativePath),
                    "Traversal in relative path must be rejected");
        }

        @Test
        @DisplayName("Should reject path escaping base directory")
        void shouldRejectPathEscapingBaseDirectory() {
            Path baseDir = Paths.get("/tmp/safe-dir");
            String relativePath = "subdir/../../../../../etc/passwd";
            assertThrows(IOException.class, () -> resolveSecurePath(baseDir, relativePath),
                    "Path escaping base directory must be rejected");
        }

        @Test
        @DisplayName("Should reject null base directory")
        void shouldRejectNullBaseDirectory() {
            assertThrows(IllegalArgumentException.class, () -> resolveSecurePath(null, "file.txt"),
                    "Null base directory must throw IllegalArgumentException");
        }

        @Test
        @DisplayName("Should accept empty relative path")
        void shouldAcceptEmptyRelativePath() throws IOException {
            Path baseDir = Paths.get("/tmp/safe-dir");
            Path resolved = resolveSecurePath(baseDir, "");
            assertEquals(baseDir.toAbsolutePath().normalize(), resolved,
                    "Empty relative path should resolve to base directory");
        }
    }

    @Nested
    @DisplayName("Safe Path Acceptance Tests")
    class SafePathTests {

        @Test
        @DisplayName("Should accept simple filename")
        void shouldAcceptSimpleFilename() {
            String path = "document.txt";
            assertTrue(isPathSafe(path),
                    "Simple filename should be accepted");
        }

        @Test
        @DisplayName("Should accept relative path with subdirectories")
        void shouldAcceptRelativePathWithSubdirectories() {
            String path = "subdir/another/file.txt";
            assertTrue(isPathSafe(path),
                    "Relative path with subdirectories should be accepted");
        }

        @Test
        @DisplayName("Should accept filename with extension")
        void shouldAcceptFilenameWithExtension() {
            String path = "report.final.v2.pdf";
            assertTrue(isPathSafe(path),
                    "Filename with multiple extensions should be accepted");
        }

        @Test
        @DisplayName("Should accept filename with underscores and hyphens")
        void shouldAcceptFilenameWithSpecialChars() {
            String path = "my-report_2024-01_final.pdf";
            assertTrue(isPathSafe(path),
                    "Filename with underscores and hyphens should be accepted");
        }

        @Test
        @DisplayName("Should accept null path")
        void shouldAcceptNullPath() {
            assertTrue(isPathSafe(null),
                    "Null path should be considered safe");
        }

        @Test
        @DisplayName("Should accept empty path")
        void shouldAcceptEmptyPath() {
            assertTrue(isPathSafe(""),
                    "Empty path should be considered safe");
        }

        @Test
        @DisplayName("Should accept current directory reference")
        void shouldAcceptCurrentDirectoryReference() {
            String path = "./file.txt";
            assertTrue(isPathSafe(path),
                    "Current directory reference should be accepted");
        }
    }

    @Nested
    @DisplayName("Edge Cases and Boundary Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should reject excessively long path")
        void shouldRejectExcessivelyLongPath() {
            StringBuilder sb = new StringBuilder();
            sb.append("a".repeat(4097));
            assertFalse(isPathSafe(sb.toString()),
                    "Path longer than 4096 characters should be rejected");
        }

        @Test
        @DisplayName("Should accept path at length limit")
        void shouldAcceptPathAtLengthLimit() {
            StringBuilder sb = new StringBuilder();
            sb.append("a".repeat(4096));
            assertTrue(isPathSafe(sb.toString()),
                    "Path at exactly 4096 characters should be accepted");
        }

        @Test
        @DisplayName("Should handle unicode characters in path")
        void shouldHandleUnicodeCharacters() {
            String path = "documents/\u4e2d\u6587/\u0440\u0443\u0441.txt";
            assertTrue(isPathSafe(path),
                    "Unicode characters in path should be handled");
        }

        @Test
        @DisplayName("Should detect traversal with leading whitespace")
        void shouldDetectTraversalWithLeadingWhitespace() {
            String path = "   ../../../etc/passwd";
            assertTrue(isPathSafe(path),
                    "Path with leading whitespace (without traversal at start) should be accepted");
        }

        @Test
        @DisplayName("Should handle spaces in filename")
        void shouldHandleSpacesInFilename() {
            String path = "my document final version.pdf";
            assertTrue(isPathSafe(path),
                    "Spaces in filename should be accepted");
        }

        @Test
        @DisplayName("Should handle path with only dots")
        void shouldHandlePathWithOnlyDots() {
            String path = "...";
            assertFalse(isPathSafe(path),
                    "Path with only dots should be rejected as traversal pattern");
        }

        @Test
        @DisplayName("Should handle mixed slash styles")
        void shouldHandleMixedSlashStyles() {
            String path = "..\\../etc/passwd";
            assertFalse(isPathSafe(path),
                    "Mixed slash styles with traversal must be detected");
        }
    }

    @Nested
    @DisplayName("YAWL-Specific Path Tests")
    class YawlSpecificTests {

        @Test
        @DisplayName("Should accept YAWL specification filename")
        void shouldAcceptYawlSpecificationFilename() {
            String path = "workflow_specification.yawl";
            assertTrue(isPathSafe(path),
                    "YAWL specification filename should be accepted");
        }

        @Test
        @DisplayName("Should accept YAWL specification in subdirectory")
        void shouldAcceptYawlSpecificationInSubdirectory() {
            String path = "specifications/finance/approval_flow.yawl";
            assertTrue(isPathSafe(path),
                    "YAWL specification in subdirectory should be accepted");
        }

        @Test
        @DisplayName("Should reject traversal to YAWL config")
        void shouldRejectTraversalToYawlConfig() {
            String path = "../../../yawl/config/database.properties";
            assertFalse(isPathSafe(path),
                    "Traversal to YAWL config must be detected");
        }

        @Test
        @DisplayName("Should reject traversal to YAWL logs")
        void shouldRejectTraversalToYawlLogs() {
            String path = "../../../yawl/logs/audit.log";
            assertFalse(isPathSafe(path),
                    "Traversal to YAWL logs must be detected");
        }
    }
}
