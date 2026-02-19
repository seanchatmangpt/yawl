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

package org.yawlfoundation.yawl.integration.a2a.auth;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import com.sun.net.httpserver.HttpsExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestInstance;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SPIFFE certificate-based authentication provider tests.
 *
 * <p>Tests SPIFFE X.509 SVID authentication with real certificate generation
 * and validation. No mocks - uses actual X.509 certificates generated via
 * sun.security.tools.keytool.CertAndKeyGen (available in JDK 25).
 *
 * <p>Test scenarios:
 * <ul>
 *   <li>Valid SPIFFE ID - correct trust domain, valid certificate</li>
 *   <li>Wrong trust domain - certificate from different trust domain</li>
 *   <li>Expired certificate - should reject or mark as expired</li>
 *   <li>Certificate rotation - test rotation without disruption</li>
 *   <li>Permission resolution by path prefix</li>
 * </ul>
 *
 * <p>Chicago TDD: Real certificate validation using self-signed certificates
 * with SPIFFE URI SANs. No external PKI, no network, no mocks.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
@SuppressWarnings("deprecation")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class SpiffeAuthenticationProviderTest {

    private static final String TRUSTED_DOMAIN = "yawl.cloud";

    private SpiffeAuthenticationProvider provider;

    @BeforeEach
    void setUp() {
        provider = new SpiffeAuthenticationProvider(TRUSTED_DOMAIN, defaultPolicy());
    }

    @AfterEach
    void tearDown() {
        provider = null;
    }

    // =========================================================================
    // Certificate generation helpers
    // =========================================================================

    private static Map<String, Set<String>> defaultPolicy() {
        return Map.of(
            "/engine",  Set.of(AuthenticatedPrincipal.PERM_ALL),
            "/agent",   Set.of(
                AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH,
                AuthenticatedPrincipal.PERM_WORKFLOW_QUERY,
                AuthenticatedPrincipal.PERM_WORKFLOW_CANCEL,
                AuthenticatedPrincipal.PERM_WORKITEM_MANAGE
            ),
            "/monitor", Set.of(AuthenticatedPrincipal.PERM_WORKFLOW_QUERY)
        );
    }

    /**
     * Generates a self-signed X.509 RSA certificate with a single SPIFFE URI SAN.
     */
    private static X509Certificate buildSpiffeCert(String spiffeId,
                                                    long validFromMs,
                                                    long validToMs) throws Exception {
        sun.security.tools.keytool.CertAndKeyGen gen =
            new sun.security.tools.keytool.CertAndKeyGen("RSA", "SHA256withRSA");
        gen.generate(2048);

        sun.security.x509.GeneralNames gnames = new sun.security.x509.GeneralNames();
        gnames.add(new sun.security.x509.GeneralName(
            new sun.security.x509.URIName(spiffeId)));
        sun.security.x509.SubjectAlternativeNameExtension sanExt =
            new sun.security.x509.SubjectAlternativeNameExtension(gnames);
        sun.security.x509.CertificateExtensions exts =
            new sun.security.x509.CertificateExtensions();
        exts.setExtension(sun.security.x509.SubjectAlternativeNameExtension.NAME, sanExt);

        Date notBefore = new Date(System.currentTimeMillis() + validFromMs);
        long validitySeconds = (validToMs - validFromMs) / 1000;

        return gen.getSelfCertificate(
            new sun.security.x509.X500Name("CN=test-agent"), notBefore, validitySeconds, exts);
    }

    /**
     * Generates a self-signed X.509 RSA certificate with two SPIFFE URI SANs.
     */
    private static X509Certificate buildCertWithTwoSpiffeIds() throws Exception {
        sun.security.tools.keytool.CertAndKeyGen gen =
            new sun.security.tools.keytool.CertAndKeyGen("RSA", "SHA256withRSA");
        gen.generate(2048);

        sun.security.x509.GeneralNames gnames = new sun.security.x509.GeneralNames();
        gnames.add(new sun.security.x509.GeneralName(
            new sun.security.x509.URIName("spiffe://yawl.cloud/agent/one")));
        gnames.add(new sun.security.x509.GeneralName(
            new sun.security.x509.URIName("spiffe://yawl.cloud/agent/two")));
        sun.security.x509.SubjectAlternativeNameExtension sanExt =
            new sun.security.x509.SubjectAlternativeNameExtension(gnames);
        sun.security.x509.CertificateExtensions exts =
            new sun.security.x509.CertificateExtensions();
        exts.setExtension(sun.security.x509.SubjectAlternativeNameExtension.NAME, sanExt);

        return gen.getSelfCertificate(
            new sun.security.x509.X500Name("CN=test-agent"),
            new Date(), 365L * 24 * 3600, exts);
    }

    /**
     * Generates a self-signed certificate with NO Subject Alternative Names.
     */
    private static X509Certificate buildCertWithNoSan() throws Exception {
        sun.security.tools.keytool.CertAndKeyGen gen =
            new sun.security.tools.keytool.CertAndKeyGen("RSA", "SHA256withRSA");
        gen.generate(2048);
        return gen.getSelfCertificate(
            new sun.security.x509.X500Name("CN=no-san-agent"),
            new Date(), 365L * 24 * 3600);
    }

    /**
     * Generates a self-signed certificate with a URI SAN that is NOT a SPIFFE URI.
     */
    private static X509Certificate buildCertWithNonSpiffeSan() throws Exception {
        sun.security.tools.keytool.CertAndKeyGen gen =
            new sun.security.tools.keytool.CertAndKeyGen("RSA", "SHA256withRSA");
        gen.generate(2048);

        sun.security.x509.GeneralNames gnames = new sun.security.x509.GeneralNames();
        gnames.add(new sun.security.x509.GeneralName(
            new sun.security.x509.URIName("https://example.com/not-a-spiffe-id")));
        sun.security.x509.SubjectAlternativeNameExtension sanExt =
            new sun.security.x509.SubjectAlternativeNameExtension(gnames);
        sun.security.x509.CertificateExtensions exts =
            new sun.security.x509.CertificateExtensions();
        exts.setExtension(sun.security.x509.SubjectAlternativeNameExtension.NAME, sanExt);

        return gen.getSelfCertificate(
            new sun.security.x509.X500Name("CN=non-spiffe-agent"),
            new Date(), 365L * 24 * 3600, exts);
    }

    /**
     * Generates a self-signed ECDSA certificate with a SPIFFE URI SAN.
     */
    private static X509Certificate buildEcSpiffeCert(String spiffeId) throws Exception {
        sun.security.tools.keytool.CertAndKeyGen gen =
            new sun.security.tools.keytool.CertAndKeyGen("EC", "SHA256withECDSA");
        gen.generate("secp256r1");

        sun.security.x509.GeneralNames gnames = new sun.security.x509.GeneralNames();
        gnames.add(new sun.security.x509.GeneralName(
            new sun.security.x509.URIName(spiffeId)));
        sun.security.x509.SubjectAlternativeNameExtension sanExt =
            new sun.security.x509.SubjectAlternativeNameExtension(gnames);
        sun.security.x509.CertificateExtensions exts =
            new sun.security.x509.CertificateExtensions();
        exts.setExtension(sun.security.x509.SubjectAlternativeNameExtension.NAME, sanExt);

        return gen.getSelfCertificate(
            new sun.security.x509.X500Name("CN=ec-agent"),
            new Date(), 365L * 24 * 3600, exts);
    }

    // =========================================================================
    // SSL session helpers
    // =========================================================================

    private static SSLSession sessionWithCert(X509Certificate cert) {
        return (SSLSession) Proxy.newProxyInstance(
            SpiffeAuthenticationProviderTest.class.getClassLoader(),
            new Class<?>[]{ SSLSession.class },
            (proxy, method, args) -> switch (method.getName()) {
                case "getPeerCertificates" -> new Certificate[]{ cert };
                default -> null;
            }
        );
    }

    private static SSLSession unverifiedSession() {
        return (SSLSession) Proxy.newProxyInstance(
            SpiffeAuthenticationProviderTest.class.getClassLoader(),
            new Class<?>[]{ SSLSession.class },
            (proxy, method, args) -> {
                if ("getPeerCertificates".equals(method.getName())) {
                    throw new SSLPeerUnverifiedException("mTLS handshake failed");
                }
                return null;
            }
        );
    }

    private static SSLSession emptySession() {
        return (SSLSession) Proxy.newProxyInstance(
            SpiffeAuthenticationProviderTest.class.getClassLoader(),
            new Class<?>[]{ SSLSession.class },
            (proxy, method, args) -> switch (method.getName()) {
                case "getPeerCertificates" -> new Certificate[0];
                default -> null;
            }
        );
    }

    // =========================================================================
    // Test exchange implementations
    // =========================================================================

    private static class TestHttpExchange extends HttpExchange {
        private final Headers requestHeaders = new Headers();
        private final Headers responseHeaders = new Headers();
        private int responseCode = 200;

        public Headers requestHeaders() {
            return requestHeaders;
        }

        @Override
        public Headers getRequestHeaders() { return requestHeaders; }

        @Override
        public Headers getResponseHeaders() { return responseHeaders; }

        @Override
        public URI getRequestURI() {
            try {
                return new URI("/test");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public String getRequestMethod() { return "POST"; }

        @Override
        public HttpContext getHttpContext() { return null; }

        @Override
        public void close() {
            // Intentionally empty: test exchange requires no resource cleanup
        }

        @Override
        public InputStream getRequestBody() { return InputStream.nullInputStream(); }

        @Override
        public OutputStream getResponseBody() { return OutputStream.nullOutputStream(); }

        @Override
        public void sendResponseHeaders(int rCode, long responseLength) {
            this.responseCode = rCode;
        }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return new InetSocketAddress("127.0.0.1", 12345);
        }

        @Override
        public int getResponseCode() { return responseCode; }

        @Override
        public InetSocketAddress getLocalAddress() {
            return new InetSocketAddress("127.0.0.1", 19876);
        }

        @Override
        public String getProtocol() { return "HTTP/1.1"; }

        @Override
        public Object getAttribute(String name) { return null; }

        @Override
        public void setAttribute(String name, Object value) {
            // Intentionally empty: test exchange does not store attributes
        }

        @Override
        public void setStreams(InputStream i, OutputStream o) {
            // Intentionally empty: test exchange uses null streams
        }

        @Override
        public HttpPrincipal getPrincipal() { return null; }
    }

    private static class TestHttpsExchange extends HttpsExchange {
        private final SSLSession sslSession;
        private final Headers requestHeaders = new Headers();
        private final Headers responseHeaders = new Headers();
        private int responseCode = 200;

        TestHttpsExchange(SSLSession sslSession) {
            this.sslSession = sslSession;
        }

        public Headers requestHeaders() {
            return requestHeaders;
        }

        @Override
        public SSLSession getSSLSession() { return sslSession; }

        @Override
        public Headers getRequestHeaders() { return requestHeaders; }

        @Override
        public Headers getResponseHeaders() { return responseHeaders; }

        @Override
        public URI getRequestURI() {
            try {
                return new URI("/test");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public String getRequestMethod() { return "POST"; }

        @Override
        public HttpContext getHttpContext() { return null; }

        @Override
        public void close() {
            // Intentionally empty: test exchange requires no resource cleanup
        }

        @Override
        public InputStream getRequestBody() { return InputStream.nullInputStream(); }

        @Override
        public OutputStream getResponseBody() { return OutputStream.nullOutputStream(); }

        @Override
        public void sendResponseHeaders(int rCode, long responseLength) {
            this.responseCode = rCode;
        }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return new InetSocketAddress("127.0.0.1", 12345);
        }

        @Override
        public int getResponseCode() { return responseCode; }

        @Override
        public InetSocketAddress getLocalAddress() {
            return new InetSocketAddress("127.0.0.1", 19876);
        }

        @Override
        public String getProtocol() { return "TLSv1.3"; }

        @Override
        public Object getAttribute(String name) { return null; }

        @Override
        public void setAttribute(String name, Object value) {
            // Intentionally empty: test exchange does not store attributes
        }

        @Override
        public void setStreams(InputStream i, OutputStream o) {
            // Intentionally empty: test exchange uses null streams
        }

        @Override
        public HttpPrincipal getPrincipal() { return null; }
    }

    // =========================================================================
    // NESTED: Constructor validation tests
    // =========================================================================

    @Nested
    @DisplayName("Constructor validation")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class ConstructorTests {

        @Test
        @Order(1)
        @DisplayName("Null trust domain throws NullPointerException")
        void nullTrustDomainThrows() {
            NullPointerException ex = assertThrows(
                NullPointerException.class,
                () -> new SpiffeAuthenticationProvider(null, defaultPolicy())
            );
            assertNotNull(ex.getMessage(), "Exception must have a message");
        }

        @Test
        @Order(2)
        @DisplayName("Blank trust domain throws IllegalArgumentException")
        void blankTrustDomainThrows() {
            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new SpiffeAuthenticationProvider("   ", defaultPolicy())
            );
            assertNotNull(ex.getMessage(), "Exception must have a message");
        }

        @Test
        @Order(3)
        @DisplayName("Null permission map throws NullPointerException")
        void nullPermissionMapThrows() {
            NullPointerException ex = assertThrows(
                NullPointerException.class,
                () -> new SpiffeAuthenticationProvider(TRUSTED_DOMAIN, null)
            );
            assertNotNull(ex.getMessage(), "Exception must have a message");
        }

        @Test
        @Order(4)
        @DisplayName("scheme() returns 'mTLS'")
        void schemeReturnsMtls() {
            assertEquals("mTLS", provider.scheme(), "scheme() must return 'mTLS'");
        }
    }

    // =========================================================================
    // NESTED: canHandle() tests
    // =========================================================================

    @Nested
    @DisplayName("canHandle() detection")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class CanHandleTests {

        @Test
        @Order(1)
        @DisplayName("Returns false for plain HTTP exchange")
        void returnsFalseForPlainHttp() {
            HttpExchange plain = new TestHttpExchange();
            assertFalse(provider.canHandle(plain),
                "canHandle must return false for plain HTTP exchange");
        }

        @Test
        @Order(2)
        @DisplayName("Returns false when TLS session is null")
        void returnsFalseWhenSessionIsNull() {
            TestHttpsExchange exchange = new TestHttpsExchange(null);
            assertFalse(provider.canHandle(exchange),
                "canHandle must return false when TLS session is null");
        }

        @Test
        @Order(3)
        @DisplayName("Returns false when no peer certificate")
        void returnsFalseWhenNoPeerCert() {
            TestHttpsExchange exchange = new TestHttpsExchange(emptySession());
            assertFalse(provider.canHandle(exchange),
                "canHandle must return false when no peer certificate");
        }

        @Test
        @Order(4)
        @DisplayName("Returns false when peer is unverified")
        void returnsFalseWhenPeerUnverified() {
            TestHttpsExchange exchange = new TestHttpsExchange(unverifiedSession());
            assertFalse(provider.canHandle(exchange),
                "canHandle must return false when peer is unverified");
        }

        @Test
        @Order(5)
        @DisplayName("Returns true for HTTPS with peer certificate")
        void returnsTrueForHttpsWithPeerCert() throws Exception {
            X509Certificate cert = buildSpiffeCert(
                "spiffe://yawl.cloud/agent/order-processor",
                -86400_000L, 365L * 86400_000L);
            TestHttpsExchange exchange = new TestHttpsExchange(sessionWithCert(cert));
            assertTrue(provider.canHandle(exchange),
                "canHandle must return true for HTTPS exchange with peer cert");
        }
    }

    // =========================================================================
    // NESTED: authenticate() rejection tests
    // =========================================================================

    @Nested
    @DisplayName("authenticate() rejection cases")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class AuthenticateRejectionTests {

        @Test
        @Order(1)
        @DisplayName("Rejects plain HTTP exchange")
        void rejectsPlainHttp() {
            A2AAuthenticationException ex = assertThrows(
                A2AAuthenticationException.class,
                () -> provider.authenticate(new TestHttpExchange())
            );
            String msg = ex.getMessage().toLowerCase();
            assertTrue(msg.contains("tls") || msg.contains("https") || msg.contains("certificate"),
                "Exception must mention TLS/HTTPS/certificate");
            assertEquals("mTLS", ex.getSupportedSchemes(), "Scheme must be mTLS");
        }

        @Test
        @Order(2)
        @DisplayName("Rejects null TLS session")
        void rejectsNullTlsSession() {
            A2AAuthenticationException ex = assertThrows(
                A2AAuthenticationException.class,
                () -> provider.authenticate(new TestHttpsExchange(null))
            );
            assertNotNull(ex.getMessage(), "Exception must have a message");
        }

        @Test
        @Order(3)
        @DisplayName("Rejects missing peer certificate")
        void rejectsNoPeerCertificate() {
            A2AAuthenticationException ex = assertThrows(
                A2AAuthenticationException.class,
                () -> provider.authenticate(new TestHttpsExchange(emptySession()))
            );
            assertTrue(ex.getMessage().toLowerCase().contains("certificate"),
                "Exception must mention certificate");
        }

        @Test
        @Order(4)
        @DisplayName("Rejects unverified peer")
        void rejectsUnverifiedPeer() {
            A2AAuthenticationException ex = assertThrows(
                A2AAuthenticationException.class,
                () -> provider.authenticate(new TestHttpsExchange(unverifiedSession()))
            );
            assertNotNull(ex.getMessage(), "Exception must have a message");
        }

        @Test
        @Order(5)
        @DisplayName("Rejects certificate with no SAN")
        void rejectsCertWithNoSan() throws Exception {
            X509Certificate cert = buildCertWithNoSan();
            A2AAuthenticationException ex = assertThrows(
                A2AAuthenticationException.class,
                () -> provider.authenticate(
                    new TestHttpsExchange(sessionWithCert(cert)))
            );
            String msg = ex.getMessage().toLowerCase();
            assertTrue(msg.contains("subject alternative") || msg.contains("san") || msg.contains("uri"),
                "Exception must mention SubjectAlternativeName/SAN/URI");
        }

        @Test
        @Order(6)
        @DisplayName("Rejects certificate with non-SPIFFE URI SAN")
        void rejectsCertWithNonSpiffeUri() throws Exception {
            X509Certificate cert = buildCertWithNonSpiffeSan();
            A2AAuthenticationException ex = assertThrows(
                A2AAuthenticationException.class,
                () -> provider.authenticate(
                    new TestHttpsExchange(sessionWithCert(cert)))
            );
            assertTrue(ex.getMessage().toLowerCase().contains("spiffe"),
                "Exception must mention SPIFFE");
        }

        @Test
        @Order(7)
        @DisplayName("Rejects certificate with multiple SPIFFE URIs")
        void rejectsCertWithMultipleSpiffeIds() throws Exception {
            X509Certificate cert = buildCertWithTwoSpiffeIds();
            A2AAuthenticationException ex = assertThrows(
                A2AAuthenticationException.class,
                () -> provider.authenticate(
                    new TestHttpsExchange(sessionWithCert(cert)))
            );
            assertTrue(ex.getMessage().toLowerCase().contains("multiple"),
                "Exception must mention multiple SPIFFE URIs");
        }
    }

    // =========================================================================
    // NESTED: Trust domain validation tests
    // =========================================================================

    @Nested
    @DisplayName("Trust domain validation")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class TrustDomainTests {

        @Test
        @Order(1)
        @DisplayName("Rejects certificate from wrong trust domain")
        void rejectsWrongTrustDomain() throws Exception {
            X509Certificate cert = buildSpiffeCert(
                "spiffe://attacker.evil/agent/trojan",
                -86400_000L, 365L * 86400_000L);

            A2AAuthenticationException ex = assertThrows(
                A2AAuthenticationException.class,
                () -> provider.authenticate(
                    new TestHttpsExchange(sessionWithCert(cert)))
            );
            String msg = ex.getMessage().toLowerCase();
            assertTrue(msg.contains("trust domain") || msg.contains("domain")
                    || msg.contains("attacker.evil"),
                "Exception must mention trust domain");
        }

        @Test
        @Order(2)
        @DisplayName("Accepts certificate from trusted domain")
        void acceptsCertFromTrustedDomain() throws Exception {
            X509Certificate cert = buildSpiffeCert(
                "spiffe://yawl.cloud/agent/order-processor",
                -86400_000L, 365L * 86400_000L);

            AuthenticatedPrincipal principal = provider.authenticate(
                new TestHttpsExchange(sessionWithCert(cert)));

            assertNotNull(principal, "Principal must not be null for trusted domain");
            assertEquals("spiffe://yawl.cloud/agent/order-processor",
                principal.getUsername(), "Username must be the full SPIFFE ID");
        }
    }

    // =========================================================================
    // NESTED: SPIFFE ID extraction and principal tests
    // =========================================================================

    @Nested
    @DisplayName("SPIFFE ID extraction and principal population")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class SpiffeExtractionTests {

        @Test
        @Order(1)
        @DisplayName("Authenticated principal has mTLS scheme")
        void principalHasMtlsScheme() throws Exception {
            X509Certificate cert = buildSpiffeCert(
                "spiffe://yawl.cloud/agent/order-processor",
                -86400_000L, 365L * 86400_000L);

            AuthenticatedPrincipal principal = provider.authenticate(
                new TestHttpsExchange(sessionWithCert(cert)));

            assertEquals("mTLS", principal.getAuthScheme(),
                "Auth scheme must be mTLS");
        }

        @Test
        @Order(2)
        @DisplayName("Authenticated principal is authenticated")
        void principalIsAuthenticated() throws Exception {
            X509Certificate cert = buildSpiffeCert(
                "spiffe://yawl.cloud/agent/order-processor",
                -86400_000L, 365L * 86400_000L);

            AuthenticatedPrincipal principal = provider.authenticate(
                new TestHttpsExchange(sessionWithCert(cert)));

            assertTrue(principal.isAuthenticated(),
                "Principal must be authenticated");
        }

        @Test
        @Order(3)
        @DisplayName("Principal expiry set from certificate NotAfter")
        void principalExpiryFromCert() throws Exception {
            X509Certificate cert = buildSpiffeCert(
                "spiffe://yawl.cloud/agent/order-processor",
                -86400_000L, 365L * 86400_000L);

            AuthenticatedPrincipal principal = provider.authenticate(
                new TestHttpsExchange(sessionWithCert(cert)));

            assertNotNull(principal.getExpiresAt(),
                "ExpiresAt must be set from certificate");
            assertFalse(principal.isExpired(),
                "Certificate is still valid, principal must not be expired");
        }
    }

    // =========================================================================
    // NESTED: Expired certificate tests
    // =========================================================================

    @Nested
    @DisplayName("Expired certificate handling")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class ExpiredCertificateTests {

        @Test
        @Order(1)
        @DisplayName("Expired certificate NotAfter is in the past")
        void expiredCertNotAfterInPast() throws Exception {
            sun.security.tools.keytool.CertAndKeyGen gen =
                new sun.security.tools.keytool.CertAndKeyGen("RSA", "SHA256withRSA");
            gen.generate(2048);

            sun.security.x509.GeneralNames gnames = new sun.security.x509.GeneralNames();
            gnames.add(new sun.security.x509.GeneralName(
                new sun.security.x509.URIName("spiffe://yawl.cloud/agent/expired")));
            sun.security.x509.SubjectAlternativeNameExtension sanExt =
                new sun.security.x509.SubjectAlternativeNameExtension(gnames);
            sun.security.x509.CertificateExtensions exts =
                new sun.security.x509.CertificateExtensions();
            exts.setExtension(sun.security.x509.SubjectAlternativeNameExtension.NAME, sanExt);

            // Valid for 1 second in the past: NotBefore = 2 seconds ago, NotAfter = 1 second ago
            Date notBefore = new Date(System.currentTimeMillis() - 2000);
            X509Certificate cert = gen.getSelfCertificate(
                new sun.security.x509.X500Name("CN=expired-agent"),
                notBefore, 1L, exts);

            assertTrue(cert.getNotAfter().before(new Date()),
                "Test cert must be expired");

            // Authenticate: provider reads NotAfter from cert and sets it on principal
            AuthenticatedPrincipal principal = provider.authenticate(
                new TestHttpsExchange(sessionWithCert(cert)));

            // principal.expiresAt reflects cert.getNotAfter()
            assertTrue(principal.isExpired(),
                "Principal derived from expired cert must report isExpired()");
        }
    }

    // =========================================================================
    // NESTED: Permission mapping tests
    // =========================================================================

    @Nested
    @DisplayName("Permission mapping by path prefix")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class PermissionMappingTests {

        @Test
        @Order(1)
        @DisplayName("/engine path gets full (*) permission")
        void enginePathGetsFullPermission() throws Exception {
            X509Certificate cert = buildSpiffeCert(
                "spiffe://yawl.cloud/engine/core",
                -86400_000L, 365L * 86400_000L);

            AuthenticatedPrincipal principal = provider.authenticate(
                new TestHttpsExchange(sessionWithCert(cert)));

            assertTrue(principal.hasPermission(AuthenticatedPrincipal.PERM_ALL),
                "Engine SPIFFE ID must have PERM_ALL (*)");
            assertTrue(principal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH),
                "Engine PERM_ALL implies workflow:launch");
        }

        @Test
        @Order(2)
        @DisplayName("/agent path gets workflow permissions")
        void agentPathGetsWorkflowPermissions() throws Exception {
            X509Certificate cert = buildSpiffeCert(
                "spiffe://yawl.cloud/agent/order-processor",
                -86400_000L, 365L * 86400_000L);

            AuthenticatedPrincipal principal = provider.authenticate(
                new TestHttpsExchange(sessionWithCert(cert)));

            assertTrue(principal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH),
                "Agent must have workflow:launch");
            assertTrue(principal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_QUERY),
                "Agent must have workflow:query");
            assertTrue(principal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_CANCEL),
                "Agent must have workflow:cancel");
            assertTrue(principal.hasPermission(AuthenticatedPrincipal.PERM_WORKITEM_MANAGE),
                "Agent must have workitem:manage");
            assertFalse(principal.getPermissions().contains(AuthenticatedPrincipal.PERM_ALL),
                "Agent must NOT have PERM_ALL");
        }

        @Test
        @Order(3)
        @DisplayName("/monitor path gets query-only permission")
        void monitorPathGetsQueryOnlyPermission() throws Exception {
            X509Certificate cert = buildSpiffeCert(
                "spiffe://yawl.cloud/monitor/dashboard",
                -86400_000L, 365L * 86400_000L);

            AuthenticatedPrincipal principal = provider.authenticate(
                new TestHttpsExchange(sessionWithCert(cert)));

            assertTrue(principal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_QUERY),
                "Monitor must have workflow:query");
            assertFalse(principal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH),
                "Monitor must NOT have workflow:launch");
            assertFalse(principal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_CANCEL),
                "Monitor must NOT have workflow:cancel");
            assertFalse(principal.hasPermission(AuthenticatedPrincipal.PERM_WORKITEM_MANAGE),
                "Monitor must NOT have workitem:manage");
        }

        @Test
        @Order(4)
        @DisplayName("Unknown path gets empty permissions (deny all)")
        void unknownPathGetsEmptyPermissions() throws Exception {
            X509Certificate cert = buildSpiffeCert(
                "spiffe://yawl.cloud/unknown/rogue-agent",
                -86400_000L, 365L * 86400_000L);

            AuthenticatedPrincipal principal = provider.authenticate(
                new TestHttpsExchange(sessionWithCert(cert)));

            assertTrue(principal.getPermissions().isEmpty(),
                "Unknown path must have empty permission set");
            assertFalse(principal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_QUERY),
                "Unknown path must not have any workflow permission");
        }

        @Test
        @Order(5)
        @DisplayName("Longest prefix wins in permission resolution")
        void longestPrefixWins() throws Exception {
            SpiffeAuthenticationProvider customProvider = new SpiffeAuthenticationProvider(
                TRUSTED_DOMAIN,
                Map.of(
                    "/agent",            Set.of(AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH,
                                                AuthenticatedPrincipal.PERM_WORKFLOW_QUERY),
                    "/agent/restricted", Set.of(AuthenticatedPrincipal.PERM_WORKFLOW_QUERY)
                )
            );

            X509Certificate cert = buildSpiffeCert(
                "spiffe://yawl.cloud/agent/restricted/reader",
                -86400_000L, 365L * 86400_000L);

            AuthenticatedPrincipal principal = customProvider.authenticate(
                new TestHttpsExchange(sessionWithCert(cert)));

            // /agent/restricted is longer match than /agent
            assertTrue(principal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_QUERY),
                "Must have workflow:query (from /agent/restricted policy)");
            assertFalse(principal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH),
                "Must NOT have workflow:launch (which is only in /agent policy)");
        }
    }

    // =========================================================================
    // NESTED: ECDSA certificate tests
    // =========================================================================

    @Nested
    @DisplayName("ECDSA certificate support")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class EcdsaCertificateTests {

        @Test
        @Order(1)
        @DisplayName("ECDSA certificate is accepted")
        void ecdsaCertAccepted() throws Exception {
            X509Certificate cert = buildEcSpiffeCert("spiffe://yawl.cloud/agent/ec-agent");

            AuthenticatedPrincipal principal = provider.authenticate(
                new TestHttpsExchange(sessionWithCert(cert)));

            assertNotNull(principal, "Principal must not be null for ECDSA cert");
            assertEquals("spiffe://yawl.cloud/agent/ec-agent",
                principal.getUsername(), "Username must be the SPIFFE ID");
        }
    }

    // =========================================================================
    // NESTED: fromEnvironment() factory tests
    // =========================================================================

    @Nested
    @DisplayName("fromEnvironment() factory")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class FromEnvironmentTests {

        @Test
        @Order(1)
        @DisplayName("Creates provider with default trust domain")
        void createsProviderWithDefaultDomain() throws Exception {
            SpiffeAuthenticationProvider envProvider =
                SpiffeAuthenticationProvider.fromEnvironment();

            assertNotNull(envProvider, "fromEnvironment() must not return null");
            assertEquals("mTLS", envProvider.scheme(), "scheme() must be mTLS");

            // Should handle a valid HTTPS exchange (integration with default policy)
            X509Certificate cert = buildSpiffeCert(
                "spiffe://yawl.cloud/engine/core",
                -86400_000L, 365L * 86400_000L);

            AuthenticatedPrincipal principal = envProvider.authenticate(
                new TestHttpsExchange(sessionWithCert(cert)));

            assertTrue(principal.hasPermission(AuthenticatedPrincipal.PERM_ALL),
                "Default policy: /engine gets full permission");
        }
    }

    // =========================================================================
    // NESTED: Certificate rotation tests
    // =========================================================================

    @Nested
    @DisplayName("Certificate rotation")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class CertificateRotationTests {

        @Test
        @Order(1)
        @DisplayName("Rotation without disruption - both certs valid during overlap")
        void rotationWithoutDisruption() throws Exception {
            // Old cert: valid for 1 more day
            X509Certificate oldCert = buildSpiffeCert(
                "spiffe://yawl.cloud/agent/rotating",
                -86400_000L, 2L * 86400_000L);

            // New cert: valid starting now for 365 days
            X509Certificate newCert = buildSpiffeCert(
                "spiffe://yawl.cloud/agent/rotating",
                0L, 365L * 86400_000L);

            // Both should authenticate successfully during rotation window
            AuthenticatedPrincipal oldPrincipal = provider.authenticate(
                new TestHttpsExchange(sessionWithCert(oldCert)));
            AuthenticatedPrincipal newPrincipal = provider.authenticate(
                new TestHttpsExchange(sessionWithCert(newCert)));

            assertNotNull(oldPrincipal, "Old cert should still authenticate during rotation");
            assertNotNull(newPrincipal, "New cert should authenticate");

            // Both should have same SPIFFE ID and permissions
            assertEquals(oldPrincipal.getUsername(), newPrincipal.getUsername(),
                "Both certs should produce same SPIFFE ID");
            assertEquals(oldPrincipal.getPermissions(), newPrincipal.getPermissions(),
                "Both certs should have same permissions");
        }

        @Test
        @Order(2)
        @DisplayName("Multiple concurrent auths during rotation")
        void concurrentAuthDuringRotation() throws Exception {
            int threadCount = 10;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            // Pre-generate certs to avoid race in cert generation
            X509Certificate[] certs = new X509Certificate[threadCount];
            for (int i = 0; i < threadCount; i++) {
                certs[i] = buildSpiffeCert(
                    "spiffe://yawl.cloud/agent/rotation-test-" + i,
                    -86400_000L, 365L * 86400_000L);
            }

            try (ExecutorService pool = Executors.newFixedThreadPool(threadCount)) {
                for (int i = 0; i < threadCount; i++) {
                    final int idx = i;
                    pool.submit(() -> {
                        try {
                            startLatch.await();
                            AuthenticatedPrincipal principal = provider.authenticate(
                                new TestHttpsExchange(sessionWithCert(certs[idx])));

                            if (principal != null && principal.isAuthenticated()) {
                                successCount.incrementAndGet();
                            } else {
                                failCount.incrementAndGet();
                            }
                        } catch (Exception e) {
                            failCount.incrementAndGet();
                        } finally {
                            doneLatch.countDown();
                        }
                    });
                }

                startLatch.countDown();
                assertTrue(doneLatch.await(30, TimeUnit.SECONDS),
                    "Concurrent test must complete within 30 seconds");

                assertEquals(threadCount, successCount.get(),
                    "All concurrent mTLS authentications must succeed");
                assertEquals(0, failCount.get(),
                    "No concurrent authentication must fail");
            }
        }
    }

    // =========================================================================
    // NESTED: Thread safety tests
    // =========================================================================

    @Nested
    @DisplayName("Thread safety")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class ThreadSafetyTests {

        @Test
        @Order(1)
        @DisplayName("Concurrent mTLS authentications are thread-safe")
        void concurrentMtlsAuthIsThreadSafe() throws Exception {
            int threadCount = 10;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            try (ExecutorService pool = Executors.newFixedThreadPool(threadCount)) {
                for (int i = 0; i < threadCount; i++) {
                    final int idx = i;
                    pool.submit(() -> {
                        try {
                            startLatch.await();
                            // Alternate between /agent and /monitor paths
                            String path = idx % 2 == 0
                                ? "/agent/thread-" + idx
                                : "/monitor/thread-" + idx;
                            X509Certificate cert = buildSpiffeCert(
                                "spiffe://yawl.cloud" + path,
                                -86400_000L, 365L * 86400_000L);

                            AuthenticatedPrincipal principal = provider.authenticate(
                                new TestHttpsExchange(sessionWithCert(cert)));

                            if (principal != null && principal.isAuthenticated()) {
                                successCount.incrementAndGet();
                            } else {
                                failCount.incrementAndGet();
                            }
                        } catch (Exception e) {
                            failCount.incrementAndGet();
                        } finally {
                            doneLatch.countDown();
                        }
                    });
                }

                startLatch.countDown();
                assertTrue(doneLatch.await(30, TimeUnit.SECONDS),
                    "Concurrent test must complete within 30 seconds");

                assertEquals(threadCount, successCount.get(),
                    "All concurrent mTLS authentications must succeed");
                assertEquals(0, failCount.get(),
                    "No concurrent authentication must fail");
            }
        }
    }

    // =========================================================================
    // NESTED: SPIFFE ID format validation tests
    // =========================================================================

    @Nested
    @DisplayName("SPIFFE ID format validation")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class SpiffeIdFormatTests {

        @Test
        @Order(1)
        @DisplayName("Accepts SPIFFE ID with simple path")
        void acceptsSimplePath() throws Exception {
            X509Certificate cert = buildSpiffeCert(
                "spiffe://yawl.cloud/agent",
                -86400_000L, 365L * 86400_000L);

            AuthenticatedPrincipal principal = provider.authenticate(
                new TestHttpsExchange(sessionWithCert(cert)));

            assertEquals("spiffe://yawl.cloud/agent", principal.getUsername(),
                "Simple path SPIFFE ID should be accepted");
        }

        @Test
        @Order(2)
        @DisplayName("Accepts SPIFFE ID with nested path")
        void acceptsNestedPath() throws Exception {
            X509Certificate cert = buildSpiffeCert(
                "spiffe://yawl.cloud/agent/namespace/service/workload",
                -86400_000L, 365L * 86400_000L);

            AuthenticatedPrincipal principal = provider.authenticate(
                new TestHttpsExchange(sessionWithCert(cert)));

            assertEquals("spiffe://yawl.cloud/agent/namespace/service/workload",
                principal.getUsername(), "Nested path SPIFFE ID should be accepted");
        }

        @Test
        @Order(3)
        @DisplayName("Accepts SPIFFE ID with trust domain only (root path)")
        void acceptsTrustDomainOnly() throws Exception {
            // Note: This will get "/" as the workload path, which has no permissions
            X509Certificate cert = buildSpiffeCert(
                "spiffe://yawl.cloud",
                -86400_000L, 365L * 86400_000L);

            AuthenticatedPrincipal principal = provider.authenticate(
                new TestHttpsExchange(sessionWithCert(cert)));

            assertEquals("spiffe://yawl.cloud", principal.getUsername(),
                "Trust-domain-only SPIFFE ID should be accepted");
            assertTrue(principal.getPermissions().isEmpty(),
                "Root path should have no permissions");
        }
    }
}
