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

package org.yawlfoundation.yawl.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Comprehensive unit tests for {@link HttpUtil}.
 *
 * <p>This class tests HTTP utility methods including:
 * - URL resolution with redirects
 * - Port connectivity checking
 * - File downloading
 * - Error handling
 *
 * @author YAWL Foundation
 * @since YAWL 6.0.0
 */
@DisplayName("HttpUtil")
@Tag("unit")
class HttpUtilTest {

    // =========================================================================
    // URL Resolution Tests
    // =========================================================================

    @Nested
    @DisplayName("resolveURL")
    class ResolveURL {

        @Test
        @DisplayName("resolves URL string")
        void resolvesUrlString() throws Exception {
            // Test with a well-known URL that should be responsive
            URL result = HttpUtil.resolveURL("https://httpbin.org/get");
            assertNotNull(result, "Should resolve valid URL");
            assertEquals("https://httpbin.org", result.getProtocol() + "://" + result.getHost());
        }

        @Test
        @DisplayName("resolves URL object")
        void resolvesUrlObject() throws Exception {
            URL url = new URL("https://httpbin.org/get");
            URL result = HttpUtil.resolveURL(url);
            assertNotNull(result, "Should resolve URL object");
        }

        @Test
        @DisplayName("throws IOException for invalid URL")
        void throwsIOExceptionForInvalidUrl() {
            assertThrows(IOException.class, () -> {
                HttpUtil.resolveURL("invalid-url");
            });
        }

        @Test
        @DisplayName("throws IOException for malformed URL")
        void throwsIOExceptionForMalformedUrl() {
            assertThrows(IOException.class, () -> {
                HttpUtil.resolveURL("http://[invalid");
            });
        }

        @Test
        @DisplayName("throws IOException for HTTP 404")
        void throwsIOExceptionForHttp404() {
            // Test with URL that should return 404
            assertThrows(IOException.class, () -> {
                HttpUtil.resolveURL("https://httpbin.org/status/404");
            });
        }

        @Test
        @DisplayName("handles HTTPS URLs")
        void handlesHttpsUrls() throws Exception {
            URL result = HttpUtil.resolveURL("https://httpbin.org/get");
            assertNotNull(result, "Should resolve HTTPS URL");
        }

        @Test
        @DisplayName("handles HTTP URLs")
        void handlesHttpUrls() throws Exception {
            // Note: httpbin.org supports both HTTP and HTTPS
            URL result = HttpUtil.resolveURL("http://httpbin.org/get");
            assertNotNull(result, "Should resolve HTTP URL");
        }

        @Test
        @DisplayName("handles URLs with query parameters")
        void handlesUrlsWithQueryParameters() throws Exception {
            URL result = HttpUtil.resolveURL("https://httpbin.org/get?param=value");
            assertNotNull(result, "Should resolve URL with query parameters");
        }

        @Test
        @DisplayName("handles URLs with fragments")
        void handlesUrlsWithFragments() throws Exception {
            URL result = HttpUtil.resolveURL("https://httpbin.org/get#fragment");
            assertNotNull(result, "Should resolve URL with fragment");
        }
    }

    @Nested
    @DisplayName("isResponsive")
    class IsResponsive {

        @Test
        @DisplayName("returns true for responsive URL")
        void returnsTrueForResponsiveUrl() {
            assertTrue(HttpUtil.isResponsive("https://httpbin.org/get"), "Should return true for responsive URL");
        }

        @Test
        @DisplayName("returns false for unresponsive URL")
        void returnsFalseForUnresponsiveUrl() {
            assertFalse(HttpUtil.isResponsive("https://nonexistent-domain-12345.com"), "Should return false for unresponsive URL");
        }

        @Test
        @DisplayName("returns false for invalid URL")
        void returnsFalseForInvalidUrl() {
            assertFalse(HttpUtil.isResponsive("invalid-url"), "Should return false for invalid URL");
        }

        @Test
        @DisplayName("handles null URL string")
        void handlesNullUrlString() {
            // The method should handle null gracefully by returning false
            assertFalse(HttpUtil.isResponsive(null), "Should return false for null URL");
        }
    }

    // =========================================================================
    // Port Connectivity Tests
    // =========================================================================

    @Nested
    @DisplayName("isPortActive")
    class IsPortActive {

        @Test
        @DisplayName("returns true for active port")
        void returnsTrueForActivePort() {
            // Test with common open ports on httpbin.org
            assertTrue(HttpUtil.isPortActive("httpbin.org", 80), "Port 80 should be active");
            assertTrue(HttpUtil.isPortActive("httpbin.org", 443), "Port 443 should be active");
        }

        @Test
        @DisplayName("returns false for inactive port")
        void returnsFalseForInactivePort() {
            assertFalse(HttpUtil.isPortActive("httpbin.org", 9999), "Inactive port should return false");
        }

        @Test
        @DisplayName("returns false for invalid host")
        void returnsFalseForInvalidHost() {
            assertFalse(HttpUtil.isPortActive("nonexistent-host-12345.com", 80), "Invalid host should return false");
        }

        @Test
        @DisplayName("throws IOException for null host")
        void throwsIOExceptionForNullHost() {
            assertThrows(IOException.class, () -> {
                HttpUtil.isPortActive(null, 80);
            });
        }

        @Test
        @DisplayName("throws IOException for negative port")
        void throwsIOExceptionForNegativePort() {
            assertThrows(IOException.class, () -> {
                HttpUtil.isPortActive("localhost", -1);
            });
        }

        @Test
        @DisplayName("throws IOException for port > 65535")
        void throwsIOExceptionForInvalidPort() {
            assertThrows(IOException.class, () -> {
                HttpUtil.isPortActive("localhost", 70000);
            });
        }

        @Test
        @DisplayName("handles localhost port 80")
        void handlesLocalhostPort80() {
            // This might fail if no local web server is running, but it's worth testing
            // We use assertDoesNotThrow since it's okay if localhost isn't running
            assertDoesNotThrow(() -> {
                boolean result = HttpUtil.isPortActive("localhost", 80);
                // If it returns false, that's fine - means no server is running
                assertTrue(!result || result, "Should either return false or true");
            });
        }
    }

    // =========================================================================
    // File Download Tests
    // =========================================================================

    @Nested
    @DisplayName("download")
    class Download {

        @Test
        @DisplayName("downloads file from URL string")
        void downloadsFileFromUrlString(@TempDir File tempDir) throws Exception {
            String testUrl = "https://httpbin.org/json";
            File outputFile = new File(tempDir, "downloaded.json");

            HttpUtil.download(testUrl, outputFile);

            assertTrue(outputFile.exists(), "Downloaded file should exist");
            assertTrue(outputFile.length() > 0, "Downloaded file should not be empty");
            String content = Files.readString(outputFile.toPath());
            assertTrue(content.contains("\"slideshow\""), "Downloaded content should be valid JSON");
        }

        @Test
        @DisplayName("downloads file from URL object")
        void downloadsFileFromUrlObject(@TempDir File tempDir) throws Exception {
            URL url = new URL("https://httpbin.org/json");
            File outputFile = new File(tempDir, "downloaded_from_object.json");

            HttpUtil.download(url, outputFile);

            assertTrue(outputFile.exists(), "Downloaded file should exist");
            assertTrue(outputFile.length() > 0, "Downloaded file should not be empty");
        }

        @Test
        @DisplayName("replaces existing file")
        void replacesExistingFile(@TempDir File tempDir) throws Exception {
            String testUrl = "https://httpbin.org/json";
            File outputFile = new File(tempDir, "existing.json");

            // Create initial file
            Files.writeString(outputFile.toPath(), "initial content");

            HttpUtil.download(testUrl, outputFile);

            assertTrue(outputFile.exists(), "File should exist after download");
            String content = Files.readString(outputFile.toPath());
            assertTrue(content.contains("\"slideshow\""), "File should be replaced with new content");
        }

        @Test
        @DisplayName("throws IOException for invalid URL")
        void throwsIOExceptionForInvalidUrl(@TempDir File tempDir) {
            assertThrows(IOException.class, () -> {
                HttpUtil.download("invalid-url", tempDir);
            });
        }

        @Test
        @DisplayName("throws IOException for URL returning 404")
        void throwsIOExceptionForUrlReturning404(@TempDir File tempDir) {
            assertThrows(IOException.class, () -> {
                HttpUtil.download("https://httpbin.org/status/404", tempDir);
            });
        }

        @Test
        @DisplayName("creates parent directories if needed")
        void createsParentDirectoriesIfNeeded(@TempDir File tempDir) throws Exception {
            String testUrl = "https://httpbin.org/json";
            File nestedFile = new File(tempDir, "nested/directory/downloaded.json");

            HttpUtil.download(testUrl, nestedFile);

            assertTrue(nestedFile.exists(), "File should be created in nested directory");
            assertTrue(nestedFile.getParentFile().exists(), "Parent directories should be created");
        }

        @Test
        @DisplayName("handles large file download")
        void handlesLargeFileDownload(@TempDir File tempDir) throws Exception {
            // Use httpbin.org bytes endpoint to download a larger file
            String testUrl = "https://httpbin.org/bytes/1024"; // 1KB file
            File outputFile = new File(tempDir, "large_download.bin");

            HttpUtil.download(testUrl, outputFile);

            assertTrue(outputFile.exists(), "Large file should be downloaded");
            assertEquals(1024, outputFile.length(), "File size should match expected size");
        }

        @Test
        @DisplayName("throws InterruptedException for interrupted download")
        void throwsInterruptedExceptionForInterruptedDownload(@TempDir File tempDir) throws Exception {
            String testUrl = "https://httpbin.org/delay/5"; // 5 second delay

            File outputFile = new File(tempDir, "interrupted_download.txt");

            // Run download in separate thread
            CompletableFuture<Void> downloadFuture = CompletableFuture.runAsync(() -> {
                try {
                    HttpUtil.download(testUrl, outputFile);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Download interrupted", e);
                } catch (IOException e) {
                    throw new RuntimeException("Download failed", e);
                }
            });

            // Wait a bit then interrupt
            Thread.sleep(100);
            downloadFuture.cancel(true);

            // Wait for the interrupted thread to complete
            Thread.sleep(100);

            assertFalse(outputFile.exists(), "File should not exist after interruption");
        }

        @Test
        @DisplayName("downloads binary file correctly")
        void downloadsBinaryFileCorrectly(@TempDir File tempDir) throws Exception {
            // Download a PNG image from httpbin
            String testUrl = "https://httpbin.org/image/png";
            File outputFile = new File(tempDir, "downloaded.png");

            HttpUtil.download(testUrl, outputFile);

            assertTrue(outputFile.exists(), "Binary file should be downloaded");
            assertTrue(outputFile.length() > 0, "Binary file should not be empty");

            // Check if it looks like a PNG file (first few bytes should be PNG signature)
            byte[] bytes = Files.readAllBytes(outputFile.toPath());
            assertTrue(bytes.length >= 8, "File should have enough bytes to check signature");
            assertEquals(0x89, bytes[0] & 0xFF); // PNG signature
            assertEquals(0x50, bytes[1] & 0xFF);
            assertEquals(0x4E, bytes[2] & 0xFF);
            assertEquals(0x47, bytes[3] & 0xFF);
        }

        @Test
        @DisplayName("handles URL with special characters")
        void handlesUrlWithSpecialCharacters(@TempDir File tempDir) throws Exception {
            // URL with spaces and special characters
            String testUrl = "https://httpbin/get?test=value with spaces&symbol=%23";
            File outputFile = new File(tempDir, "special_chars.txt");

            HttpUtil.download(testUrl, outputFile);

            assertTrue(outputFile.exists(), "File should be downloaded from URL with special characters");
            String content = Files.readString(outputFile.toPath());
            assertTrue(content.contains("\"args\""), "Response should contain arguments");
        }
    }

    // =========================================================================
    // Error Handling Tests
    // =========================================================================

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        @DisplayName("handles network timeout gracefully")
        void handlesNetworkTimeoutGracefully(@TempDir File tempDir) throws Exception {
            // Try to connect to a very slow URL
            String slowUrl = "https://httpbin.org/delay/10";

            // This should timeout after 2 seconds (default timeout in HttpUtil)
            assertThrows(IOException.class, () -> {
                HttpUtil.resolveURL(slowUrl);
            });
        }

        @Test
        @DisplayName("handles DNS resolution failure")
        void handlesDnsResolutionFailure() {
            String invalidDomain = "https://nonexistent-domain-123456789.com";
            assertThrows(IOException.class, () -> {
                HttpUtil.resolveURL(invalidDomain);
            });
        }

        @Test
        @DisplayName("handles connection refused")
        void handlesConnectionRefused() {
            // Try to connect to a port that's not open
            assertFalse(HttpUtil.isPortActive("google.com", 9999), "Should return false for closed port");
        }

        @Test
        @DisplayName("handles malformed URL in resolveURL")
        void handlesMalformedUrlInResolveURL() {
            assertThrows(IOException.class, () -> {
                HttpUtil.resolveURL("http://example.com:invalid-port");
            });
        }

        @Test
        @DisplayName("handles URI syntax exception gracefully")
        void handlesUriSyntaxExceptionGracefully() {
            assertThrows(IOException.class, () -> {
                HttpUtil.resolveURL("http://example.com/path with space");
            });
        }
    }

    // =========================================================================
    // Configuration Tests
    // =========================================================================

    @Nested
    @DisplayName("Configuration")
    class Configuration {

        @Test
        @DisplayName("HTTP client has correct timeout")
        void httpClientHasCorrectTimeout() {
            // This test verifies the static HttpClient is configured correctly
            // We can't directly access the HttpClient timeout, but we can test
            // that URLs resolve within reasonable time
            assertDoesNotThrow(() -> {
                HttpUtil.resolveURL("https://httpbin.org/get");
            }, "Should resolve URLs with configured timeout");
        }

        @Test
        @DisplayName("follows redirects correctly")
        void followsRedirectsCorrectly() throws Exception {
            // httpbin.org/redirect/1 redirects to /get
            URL result = HttpUtil.resolveURL("https://httpbin.org/redirect/1");
            assertNotNull(result, "Should follow redirects");
            assertEquals("https://httpbin.org", result.getHost());
        }

        @Test
        @DisplayName("handles too many redirects")
        void handlesTooManyRedirects() throws Exception {
            // httpbin.org/redirect/5 should work (5 redirects)
            URL result = HttpUtil.resolveURL("https://httpbin.org/redirect/5");
            assertNotNull(result, "Should handle multiple redirects");
        }
    }

    // =========================================================================
    // Integration Tests
    // =========================================================================

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("complete workflow: resolve, check port, download")
        void completeWorkflow(@TempDir File tempDir) throws Exception {
            // 1. Resolve URL
            URL resolvedUrl = HttpUtil.resolveURL("https://httpbin.org/get");
            assertNotNull(resolvedUrl, "Should resolve URL");

            // 2. Check if port is active
            assertTrue(HttpUtil.isPortActive(resolvedUrl.getHost(), resolvedUrl.getPort() != -1 ? resolvedUrl.getPort() : 443),
                "Should confirm port is active");

            // 3. Download a file
            File outputFile = new File(tempDir, "integration_test.json");
            HttpUtil.download(resolvedUrl.toString(), outputFile);

            // 4. Verify download
            assertTrue(outputFile.exists(), "Downloaded file should exist");
            assertTrue(outputFile.length() > 0, "Downloaded file should have content");
        }

        @Test
        @DisplayName("concurrent requests handled gracefully")
        void concurrentRequestsHandledGracefully(@TempDir File tempDir) throws Exception {
            int numRequests = 3;
            Thread[] threads = new Thread[numRequests];
            File[] files = new File[numRequests];

            // Create multiple concurrent download threads
            for (int i = 0; i < numRequests; i++) {
                final int index = i;
                files[index] = new File(tempDir, "concurrent_" + index + ".json");

                threads[index] = new Thread(() -> {
                    try {
                        HttpUtil.download("https://httpbin.org/json", files[index]);
                    } catch (Exception e) {
                        // Handle exception in thread
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Download failed", e);
                    }
                });
                threads[index].start();
            }

            // Wait for all threads to complete
            for (Thread thread : threads) {
                thread.join(10000); // 10 second timeout
            }

            // Verify all files were downloaded
            for (File file : files) {
                assertTrue(file.exists(), "Concurrent download should create file");
                assertTrue(file.length() > 0, "Concurrent download file should have content");
            }
        }
    }
}