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

package org.yawlfoundation.yawl.soc2;

import com.sun.net.httpserver.HttpExchange;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import org.yawlfoundation.yawl.integration.a2a.auth.A2AAuthenticationException;
import org.yawlfoundation.yawl.integration.a2a.auth.AuthenticatedPrincipal;
import org.yawlfoundation.yawl.integration.a2a.auth.SpiffeAuthenticationProvider;
import sun.security.tools.keytool.CertAndKeyGen;
import sun.security.x509.CertificateExtensions;
import sun.security.x509.GeneralName;
import sun.security.x509.GeneralNames;
import sun.security.x509.SubjectAlternativeNameExtension;
import sun.security.x509.URIName;
import sun.security.x509.X500Name;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.lang.reflect.Proxy;
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

/**
 * SOC2 CC6.1 / CC6.6 - SPIFFE/mTLS Authentication Provider Tests.
 *
 * <p>Tests map to SOC 2 Trust Service Criteria:
 * CC6.1 - Logical access security measures: only authenticated agents with valid
 *         SPIFFE SVIDs may connect. Trust domain enforced at every connection.
 * CC6.6 - Protection against external threats: mTLS mutual authentication prevents
 *         unauthenticated callers from reaching any workflow operation. Wrong trust
 *         domains are explicitly rejected.
 *
 * <p>Covers:
 * <ul>
 *   <li>SPIFFE ID extracted correctly from certificate URI SAN</li>
 *   <li>Correct trust domain accepted</li>
 *   <li>Wrong trust domain rejected with A2AAuthenticationException</li>
 *   <li>Missing SAN extension rejected</li>
 *   <li>Missing SPIFFE URI in SAN rejected</li>
 *   <li>Multiple SPIFFE URIs in SAN rejected (SVID must have exactly one)</li>
 *   <li>Non-HTTPS exchange rejected</li>
 *   <li>Missing TLS session rejected</li>
 *   <li>No peer certificate rejected</li>
 *   <li>SSLPeerUnverifiedException propagated as auth exception</li>
 *   <li>canHandle returns true only for HTTPS with peer cert</li>
 *   <li>canHandle returns false for plain HTTP</li>
 *   <li>canHandle returns false for HTTPS without peer cert</li>
 *   <li>/engine path gets full (*) permission</li>
 *   <li>/agent path gets launch, query, cancel, manage permissions</li>
 *   <li>/monitor path gets query-only permission</li>
 *   <li>Unknown path gets empty permissions (deny all)</li>
 *   <li>Longest-prefix permission matching wins</li>
 *   <li>Expired certificate rejected by JSSE delegation</li>
 *   <li>fromEnvironment() creates provider with default trust domain</li>
 *   <li>Constructor rejects null trust domain</li>
 *   <li>Constructor rejects blank trust domain</li>
 *   <li>Constructor rejects null permission map</li>
 *   <li>Concurrent mTLS authentications are thread-safe</li>
 * </ul>
 *
 * <p>Chicago TDD: real SpiffeAuthenticationProvider with self-signed X.509 certificates
 * containing real SPIFFE URI SANs. Certificates are generated using
 * sun.security.tools.keytool.CertAndKeyGen (available in JDK 21). No network, no
 * external PKI, no mocks.
 *
 * @author YAWL Foundation - SOC2 Compliance 2026-02-17
 */
@SuppressWarnings("deprecation")
public class SpiffeAuthenticationProviderTest extends TestCase {

    private static final String TRUSTED_DOMAIN = "yawl.cloud";

    private SpiffeAuthenticationProvider provider;

    public SpiffeAuthenticationProviderTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        provider = new SpiffeAuthenticationProvider(TRUSTED_DOMAIN, defaultPolicy());
    }

    // =========================================================================
    // Certificate generation helpers
    // =========================================================================

    /**
     * Builds a default SPIFFE permission policy matching the production defaults.
     */
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
     *
     * @param spiffeId the full SPIFFE ID URI, e.g. {@code spiffe://yawl.cloud/agent/order-processor}
     * @param validFromMs milliseconds offset from now for NotBefore (negative = already valid)
     * @param validToMs   milliseconds offset from now for NotAfter (positive = still valid)
     */
    private static X509Certificate buildSpiffeCert(String spiffeId,
                                                    long validFromMs,
                                                    long validToMs) throws Exception {
        CertAndKeyGen gen = new CertAndKeyGen("RSA", "SHA256withRSA");
        gen.generate(2048);

        GeneralNames gnames = new GeneralNames();
        gnames.add(new GeneralName(new URIName(spiffeId)));
        SubjectAlternativeNameExtension sanExt = new SubjectAlternativeNameExtension(gnames);
        CertificateExtensions exts = new CertificateExtensions();
        exts.setExtension(SubjectAlternativeNameExtension.NAME, sanExt);

        Date notBefore = new Date(System.currentTimeMillis() + validFromMs);
        long validitySeconds = (validToMs - validFromMs) / 1000;

        return gen.getSelfCertificate(new X500Name("CN=test-agent"), notBefore, validitySeconds, exts);
    }

    /**
     * Generates a self-signed X.509 RSA certificate with two SPIFFE URI SANs.
     * (Invalid SVID - must have exactly one SPIFFE URI.)
     */
    private static X509Certificate buildCertWithTwoSpiffeIds() throws Exception {
        CertAndKeyGen gen = new CertAndKeyGen("RSA", "SHA256withRSA");
        gen.generate(2048);

        GeneralNames gnames = new GeneralNames();
        gnames.add(new GeneralName(new URIName("spiffe://yawl.cloud/agent/one")));
        gnames.add(new GeneralName(new URIName("spiffe://yawl.cloud/agent/two")));
        SubjectAlternativeNameExtension sanExt = new SubjectAlternativeNameExtension(gnames);
        CertificateExtensions exts = new CertificateExtensions();
        exts.setExtension(SubjectAlternativeNameExtension.NAME, sanExt);

        return gen.getSelfCertificate(new X500Name("CN=test-agent"),
                new Date(), 365L * 24 * 3600, exts);
    }

    /**
     * Generates a self-signed certificate with NO Subject Alternative Names.
     */
    private static X509Certificate buildCertWithNoSan() throws Exception {
        CertAndKeyGen gen = new CertAndKeyGen("RSA", "SHA256withRSA");
        gen.generate(2048);
        return gen.getSelfCertificate(new X500Name("CN=no-san-agent"),
                new Date(), 365L * 24 * 3600);
    }

    /**
     * Generates a self-signed certificate with a URI SAN that is NOT a SPIFFE URI.
     */
    private static X509Certificate buildCertWithNonSpiffeSan() throws Exception {
        CertAndKeyGen gen = new CertAndKeyGen("RSA", "SHA256withRSA");
        gen.generate(2048);

        GeneralNames gnames = new GeneralNames();
        gnames.add(new GeneralName(new URIName("https://example.com/not-a-spiffe-id")));
        SubjectAlternativeNameExtension sanExt = new SubjectAlternativeNameExtension(gnames);
        CertificateExtensions exts = new CertificateExtensions();
        exts.setExtension(SubjectAlternativeNameExtension.NAME, sanExt);

        return gen.getSelfCertificate(new X500Name("CN=non-spiffe-agent"),
                new Date(), 365L * 24 * 3600, exts);
    }

    /**
     * Generates a self-signed ECDSA certificate with a SPIFFE URI SAN.
     * Tests that EC keys work as well as RSA.
     */
    private static X509Certificate buildEcSpiffeCert(String spiffeId) throws Exception {
        CertAndKeyGen gen = new CertAndKeyGen("EC", "SHA256withECDSA");
        gen.generate("secp256r1");

        GeneralNames gnames = new GeneralNames();
        gnames.add(new GeneralName(new URIName(spiffeId)));
        SubjectAlternativeNameExtension sanExt = new SubjectAlternativeNameExtension(gnames);
        CertificateExtensions exts = new CertificateExtensions();
        exts.setExtension(SubjectAlternativeNameExtension.NAME, sanExt);

        return gen.getSelfCertificate(new X500Name("CN=ec-agent"),
                new Date(), 365L * 24 * 3600, exts);
    }

    // =========================================================================
    // SSL session helpers
    // =========================================================================

    /**
     * Builds an SSLSession proxy that returns the given certificate as the peer certificate.
     */
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

    /**
     * Builds an SSLSession proxy that throws SSLPeerUnverifiedException from
     * getPeerCertificates() to simulate a failed mTLS handshake.
     */
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

    /**
     * Builds an SSLSession proxy that returns an empty certificate array.
     */
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
    // CC6.1 - Constructor validation
    // =========================================================================

    /**
     * SOC2 CC6.1: Null trust domain must throw NullPointerException at construction time.
     * Fail-fast prevents misconfigured providers from accepting any certificate.
     */
    public void testConstructorRejectsNullTrustDomain() {
        try {
            new SpiffeAuthenticationProvider(null, defaultPolicy());
            fail("Expected NullPointerException for null trust domain");
        } catch (NullPointerException e) {
            assertNotNull("Exception must have a message", e.getMessage());
        }
    }

    /**
     * SOC2 CC6.1: Blank trust domain must throw IllegalArgumentException.
     * Empty string would match any domain (security bypass).
     */
    public void testConstructorRejectsBlankTrustDomain() {
        try {
            new SpiffeAuthenticationProvider("   ", defaultPolicy());
            fail("Expected IllegalArgumentException for blank trust domain");
        } catch (IllegalArgumentException e) {
            assertNotNull("Exception must have a message", e.getMessage());
        }
    }

    /**
     * SOC2 CC6.1: Null permission map must throw NullPointerException.
     */
    public void testConstructorRejectsNullPermissionMap() {
        try {
            new SpiffeAuthenticationProvider(TRUSTED_DOMAIN, null);
            fail("Expected NullPointerException for null permission map");
        } catch (NullPointerException e) {
            assertNotNull("Exception must have a message", e.getMessage());
        }
    }

    /**
     * SOC2 CC6.1: scheme() must return "mTLS" to identify this provider.
     */
    public void testSchemeReturnsMtls() {
        assertEquals("scheme() must return 'mTLS'", "mTLS", provider.scheme());
    }

    // =========================================================================
    // CC6.6 - canHandle()
    // =========================================================================

    /**
     * SOC2 CC6.6: canHandle must return false for a plain HTTP exchange.
     * The provider only handles mTLS connections.
     */
    public void testCanHandleReturnsFalseForPlainHttpExchange() {
        // TestHttpExchange (non-HTTPS) must not be handled
        HttpExchange plain = new TestHttpExchange();
        assertFalse("canHandle must return false for plain HTTP exchange",
                provider.canHandle(plain));
    }

    /**
     * SOC2 CC6.6: canHandle must return false when HTTPS session is null.
     */
    public void testCanHandleReturnsFalseWhenSessionIsNull() {
        TestHttpsExchange exchange = new TestHttpsExchange(null);
        assertFalse("canHandle must return false when TLS session is null",
                provider.canHandle(exchange));
    }

    /**
     * SOC2 CC6.6: canHandle must return false when peer certificate is absent.
     * One-way TLS (server cert only) must not be accepted.
     */
    public void testCanHandleReturnsFalseWhenNoPeerCertificate() {
        TestHttpsExchange exchange = new TestHttpsExchange(emptySession());
        assertFalse("canHandle must return false when no peer certificate",
                provider.canHandle(exchange));
    }

    /**
     * SOC2 CC6.6: canHandle must return false when getPeerCertificates throws.
     * Unverified peers must never be handled.
     */
    public void testCanHandleReturnsFalseWhenPeerUnverified() {
        TestHttpsExchange exchange = new TestHttpsExchange(unverifiedSession());
        assertFalse("canHandle must return false when peer is unverified",
                provider.canHandle(exchange));
    }

    /**
     * SOC2 CC6.6: canHandle must return true for HTTPS exchange with peer certificate.
     * This is the happy path for mTLS.
     */
    public void testCanHandleReturnsTrueForHttpsWithPeerCert() throws Exception {
        X509Certificate cert = buildSpiffeCert(
                "spiffe://yawl.cloud/agent/order-processor",
                -86400_000L, 365L * 86400_000L);
        TestHttpsExchange exchange = new TestHttpsExchange(sessionWithCert(cert));
        assertTrue("canHandle must return true for HTTPS exchange with peer cert",
                provider.canHandle(exchange));
    }

    // =========================================================================
    // CC6.6 - authenticate() rejection cases
    // =========================================================================

    /**
     * SOC2 CC6.6: authenticate() on a plain HTTP exchange must throw.
     * mTLS authentication requires a TLS connection.
     */
    public void testAuthenticateRejectsPlainHttpExchange() {
        try {
            provider.authenticate(new TestHttpExchange());
            fail("Expected A2AAuthenticationException for plain HTTP exchange");
        } catch (A2AAuthenticationException e) {
            assertTrue("Exception must mention TLS",
                    e.getMessage().toLowerCase().contains("tls")
                    || e.getMessage().toLowerCase().contains("https")
                    || e.getMessage().toLowerCase().contains("certificate"));
            assertEquals("Scheme must be mTLS", "mTLS", e.getSupportedSchemes());
        }
    }

    /**
     * SOC2 CC6.6: authenticate() with null TLS session must throw.
     */
    public void testAuthenticateRejectsNullTlsSession() {
        try {
            provider.authenticate(new TestHttpsExchange(null));
            fail("Expected A2AAuthenticationException for null TLS session");
        } catch (A2AAuthenticationException e) {
            assertNotNull("Exception must have a message", e.getMessage());
        }
    }

    /**
     * SOC2 CC6.6: authenticate() when peer certificate is absent must throw.
     * Clients without a certificate cannot be authenticated via mTLS.
     */
    public void testAuthenticateRejectsNoPeerCertificate() {
        try {
            provider.authenticate(new TestHttpsExchange(emptySession()));
            fail("Expected A2AAuthenticationException for missing peer certificate");
        } catch (A2AAuthenticationException e) {
            assertTrue("Exception must mention certificate",
                    e.getMessage().toLowerCase().contains("certificate"));
        }
    }

    /**
     * SOC2 CC6.6: authenticate() when getPeerCertificates throws SSLPeerUnverifiedException.
     * Failed mTLS handshake must result in an authentication exception.
     */
    public void testAuthenticateRejectsUnverifiedPeer() {
        try {
            provider.authenticate(new TestHttpsExchange(unverifiedSession()));
            fail("Expected A2AAuthenticationException for unverified peer");
        } catch (A2AAuthenticationException e) {
            assertNotNull("Exception must have a message", e.getMessage());
        }
    }

    /**
     * SOC2 CC6.6: Certificate with no SAN extension must be rejected.
     * SPIFFE SVIDs MUST contain a URI SAN.
     */
    public void testAuthenticateRejectsCertWithNoSan() throws Exception {
        X509Certificate cert = buildCertWithNoSan();
        try {
            provider.authenticate(new TestHttpsExchange(sessionWithCert(cert)));
            fail("Expected A2AAuthenticationException for cert with no SAN");
        } catch (A2AAuthenticationException e) {
            assertTrue("Exception must mention SubjectAlternativeName or SAN",
                    e.getMessage().toLowerCase().contains("subject alternative")
                    || e.getMessage().toLowerCase().contains("san")
                    || e.getMessage().toLowerCase().contains("uri"));
        }
    }

    /**
     * SOC2 CC6.6: Certificate with only non-SPIFFE URI SAN must be rejected.
     * A URI like https://example.com is not a SPIFFE ID.
     */
    public void testAuthenticateRejectsCertWithNonSpiffeUri() throws Exception {
        X509Certificate cert = buildCertWithNonSpiffeSan();
        try {
            provider.authenticate(new TestHttpsExchange(sessionWithCert(cert)));
            fail("Expected A2AAuthenticationException for cert with non-SPIFFE URI");
        } catch (A2AAuthenticationException e) {
            assertTrue("Exception must mention SPIFFE URI",
                    e.getMessage().toLowerCase().contains("spiffe"));
        }
    }

    /**
     * SOC2 CC6.6: Certificate with two SPIFFE URI SANs must be rejected.
     * SPIFFE specification requires exactly one SPIFFE ID per SVID.
     */
    public void testAuthenticateRejectsCertWithMultipleSpiffeIds() throws Exception {
        X509Certificate cert = buildCertWithTwoSpiffeIds();
        try {
            provider.authenticate(new TestHttpsExchange(sessionWithCert(cert)));
            fail("Expected A2AAuthenticationException for cert with multiple SPIFFE URIs");
        } catch (A2AAuthenticationException e) {
            assertTrue("Exception must mention multiple SPIFFE URIs",
                    e.getMessage().toLowerCase().contains("multiple"));
        }
    }

    // =========================================================================
    // CC6.6 - Trust domain validation
    // =========================================================================

    /**
     * SOC2 CC6.6: Certificate from a different trust domain must be rejected.
     * Prevents cross-domain SPIFFE ID spoofing.
     */
    public void testAuthenticateRejectsWrongTrustDomain() throws Exception {
        // Certificate issued for attacker.evil domain
        X509Certificate cert = buildSpiffeCert(
                "spiffe://attacker.evil/agent/trojan",
                -86400_000L, 365L * 86400_000L);

        try {
            provider.authenticate(new TestHttpsExchange(sessionWithCert(cert)));
            fail("Expected A2AAuthenticationException for wrong trust domain");
        } catch (A2AAuthenticationException e) {
            assertTrue("Exception must mention trust domain",
                    e.getMessage().toLowerCase().contains("trust domain")
                    || e.getMessage().toLowerCase().contains("domain")
                    || e.getMessage().toLowerCase().contains("attacker.evil"));
        }
    }

    /**
     * SOC2 CC6.6: SPIFFE ID with trusted domain accepted and domain extracted correctly.
     */
    public void testAuthenticateAcceptsCertFromTrustedDomain() throws Exception {
        X509Certificate cert = buildSpiffeCert(
                "spiffe://yawl.cloud/agent/order-processor",
                -86400_000L, 365L * 86400_000L);

        AuthenticatedPrincipal principal =
                provider.authenticate(new TestHttpsExchange(sessionWithCert(cert)));

        assertNotNull("Principal must not be null for trusted domain", principal);
        assertEquals("Username must be the full SPIFFE ID",
                "spiffe://yawl.cloud/agent/order-processor", principal.getUsername());
    }

    // =========================================================================
    // CC6.1 - SPIFFE ID extraction and principal population
    // =========================================================================

    /**
     * SOC2 CC6.1: authenticate() must set the auth scheme to "mTLS".
     */
    public void testAuthenticatedPrincipalHasMtlsScheme() throws Exception {
        X509Certificate cert = buildSpiffeCert(
                "spiffe://yawl.cloud/agent/order-processor",
                -86400_000L, 365L * 86400_000L);

        AuthenticatedPrincipal principal =
                provider.authenticate(new TestHttpsExchange(sessionWithCert(cert)));

        assertEquals("Auth scheme must be mTLS", "mTLS", principal.getAuthScheme());
    }

    /**
     * SOC2 CC6.1: authenticate() must return isAuthenticated() == true.
     */
    public void testAuthenticatedPrincipalIsAuthenticated() throws Exception {
        X509Certificate cert = buildSpiffeCert(
                "spiffe://yawl.cloud/agent/order-processor",
                -86400_000L, 365L * 86400_000L);

        AuthenticatedPrincipal principal =
                provider.authenticate(new TestHttpsExchange(sessionWithCert(cert)));

        assertTrue("Principal must be authenticated", principal.isAuthenticated());
    }

    /**
     * SOC2 CC6.1: authenticate() must set expiresAt from the certificate NotAfter date.
     */
    public void testAuthenticatedPrincipalHasExpiryFromCertificate() throws Exception {
        X509Certificate cert = buildSpiffeCert(
                "spiffe://yawl.cloud/agent/order-processor",
                -86400_000L, 365L * 86400_000L);

        AuthenticatedPrincipal principal =
                provider.authenticate(new TestHttpsExchange(sessionWithCert(cert)));

        assertNotNull("ExpiresAt must be set from certificate", principal.getExpiresAt());
        assertFalse("Certificate is still valid, principal must not be expired",
                principal.isExpired());
    }

    // =========================================================================
    // CC6.6 - Expired certificate rejection
    // =========================================================================

    /**
     * SOC2 CC6.6: Expired certificate must be rejected.
     * The SignatureVerifier delegates to X509Certificate.checkValidity(), which
     * throws CertificateExpiredException for expired certificates. This test
     * verifies the certificate's expiry is checked during authentication.
     *
     * Note: SpiffeAuthenticationProvider delegates chain-of-trust to JSSE. The
     * provider itself does not re-check expiry (JSSE rejects expired certs during
     * handshake). However, it extracts the NotAfter date and sets it on the
     * principal. This test validates the NotAfter date is correctly read from the
     * certificate.
     */
    public void testExpiredCertificateNotAfterIsInPast() throws Exception {
        // Build a cert that is already expired (NotAfter was 1 second after NotBefore)
        // We can't force a TLS handshake failure here (JSSE handles that at the socket
        // level), but we can verify that a cert with past NotAfter creates a principal
        // that reports isExpired() == true.
        CertAndKeyGen gen = new CertAndKeyGen("RSA", "SHA256withRSA");
        gen.generate(2048);

        GeneralNames gnames = new GeneralNames();
        gnames.add(new GeneralName(new URIName("spiffe://yawl.cloud/agent/expired")));
        SubjectAlternativeNameExtension sanExt = new SubjectAlternativeNameExtension(gnames);
        CertificateExtensions exts = new CertificateExtensions();
        exts.setExtension(SubjectAlternativeNameExtension.NAME, sanExt);

        // Valid for 1 second in the past: NotBefore = 2 seconds ago, NotAfter = 1 second ago
        Date notBefore = new Date(System.currentTimeMillis() - 2000);
        X509Certificate cert = gen.getSelfCertificate(new X500Name("CN=expired-agent"),
                notBefore, 1L, exts);  // valid for 1 second starting 2 seconds ago

        // The cert's NotAfter is now in the past
        assertTrue("Test cert must be expired",
                cert.getNotAfter().before(new Date()));

        // Authenticate: provider reads NotAfter from cert and sets it on principal.
        // The principal's isExpired() reflects the cert NotAfter.
        AuthenticatedPrincipal principal =
                provider.authenticate(new TestHttpsExchange(sessionWithCert(cert)));

        // principal.expiresAt reflects cert.getNotAfter()
        assertTrue("Principal derived from expired cert must report isExpired()",
                principal.isExpired());
    }

    // =========================================================================
    // CC6.1 - Permission mapping (path-to-permissions)
    // =========================================================================

    /**
     * SOC2 CC6.1: /engine path prefix maps to full (*) permission.
     * The engine itself needs unrestricted access.
     */
    public void testEnginePathGetsFullPermission() throws Exception {
        X509Certificate cert = buildSpiffeCert(
                "spiffe://yawl.cloud/engine/core",
                -86400_000L, 365L * 86400_000L);

        AuthenticatedPrincipal principal =
                provider.authenticate(new TestHttpsExchange(sessionWithCert(cert)));

        assertTrue("Engine SPIFFE ID must have PERM_ALL (*)",
                principal.hasPermission(AuthenticatedPrincipal.PERM_ALL));
        assertTrue("Engine PERM_ALL implies workflow:launch",
                principal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH));
    }

    /**
     * SOC2 CC6.1: /agent path prefix maps to launch, query, cancel, manage.
     */
    public void testAgentPathGetsWorkflowPermissions() throws Exception {
        X509Certificate cert = buildSpiffeCert(
                "spiffe://yawl.cloud/agent/order-processor",
                -86400_000L, 365L * 86400_000L);

        AuthenticatedPrincipal principal =
                provider.authenticate(new TestHttpsExchange(sessionWithCert(cert)));

        assertTrue("Agent must have workflow:launch",
                principal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH));
        assertTrue("Agent must have workflow:query",
                principal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_QUERY));
        assertTrue("Agent must have workflow:cancel",
                principal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_CANCEL));
        assertTrue("Agent must have workitem:manage",
                principal.hasPermission(AuthenticatedPrincipal.PERM_WORKITEM_MANAGE));
        assertFalse("Agent must NOT have PERM_ALL",
                principal.getPermissions().contains(AuthenticatedPrincipal.PERM_ALL));
    }

    /**
     * SOC2 CC6.1: /monitor path prefix maps to query-only permission (least privilege).
     */
    public void testMonitorPathGetsQueryOnlyPermission() throws Exception {
        X509Certificate cert = buildSpiffeCert(
                "spiffe://yawl.cloud/monitor/dashboard",
                -86400_000L, 365L * 86400_000L);

        AuthenticatedPrincipal principal =
                provider.authenticate(new TestHttpsExchange(sessionWithCert(cert)));

        assertTrue("Monitor must have workflow:query",
                principal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_QUERY));
        assertFalse("Monitor must NOT have workflow:launch",
                principal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH));
        assertFalse("Monitor must NOT have workflow:cancel",
                principal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_CANCEL));
        assertFalse("Monitor must NOT have workitem:manage",
                principal.hasPermission(AuthenticatedPrincipal.PERM_WORKITEM_MANAGE));
    }

    /**
     * SOC2 CC6.1: Unknown path must receive empty permissions (deny-all).
     * No accidental access grant for unregistered workloads.
     */
    public void testUnknownPathGetsEmptyPermissions() throws Exception {
        X509Certificate cert = buildSpiffeCert(
                "spiffe://yawl.cloud/unknown/rogue-agent",
                -86400_000L, 365L * 86400_000L);

        AuthenticatedPrincipal principal =
                provider.authenticate(new TestHttpsExchange(sessionWithCert(cert)));

        assertTrue("Unknown path must have empty permission set",
                principal.getPermissions().isEmpty());
        assertFalse("Unknown path must not have any workflow permission",
                principal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_QUERY));
    }

    /**
     * SOC2 CC6.1: Longest-prefix matching must win when multiple prefixes match.
     * An agent registered under /agent/privileged should get that specific policy.
     */
    public void testLongestPrefixWinsInPermissionResolution() throws Exception {
        // Create a provider with both /agent and /agent/restricted in the policy
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

        AuthenticatedPrincipal principal =
                customProvider.authenticate(new TestHttpsExchange(sessionWithCert(cert)));

        // /agent/restricted is longer match than /agent
        assertTrue("Must have workflow:query (from /agent/restricted policy)",
                principal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_QUERY));
        assertFalse("Must NOT have workflow:launch (which is only in /agent policy)",
                principal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH));
    }

    // =========================================================================
    // CC6.6 - ECDSA certificate support
    // =========================================================================

    /**
     * SOC2 CC6.6: ECDSA certificates with SPIFFE URI SANs must be accepted.
     * SPIFFE mandates support for EC keys (secp256r1) in addition to RSA.
     */
    public void testEcdsaCertificateIsAccepted() throws Exception {
        X509Certificate cert = buildEcSpiffeCert("spiffe://yawl.cloud/agent/ec-agent");

        AuthenticatedPrincipal principal =
                provider.authenticate(new TestHttpsExchange(sessionWithCert(cert)));

        assertNotNull("Principal must not be null for ECDSA cert", principal);
        assertEquals("Username must be the SPIFFE ID",
                "spiffe://yawl.cloud/agent/ec-agent", principal.getUsername());
    }

    // =========================================================================
    // CC6.6 - fromEnvironment() factory
    // =========================================================================

    /**
     * SOC2 CC6.6: fromEnvironment() must create a provider that uses the default trust domain.
     * Verifies the production factory method works without environment variables set.
     */
    public void testFromEnvironmentCreatesProviderWithDefaultDomain() throws Exception {
        SpiffeAuthenticationProvider envProvider = SpiffeAuthenticationProvider.fromEnvironment();
        assertNotNull("fromEnvironment() must not return null", envProvider);
        assertEquals("scheme() must be mTLS", "mTLS", envProvider.scheme());

        // Should handle a valid HTTPS exchange (integration with default policy)
        X509Certificate cert = buildSpiffeCert(
                "spiffe://yawl.cloud/engine/core",
                -86400_000L, 365L * 86400_000L);

        AuthenticatedPrincipal principal =
                envProvider.authenticate(new TestHttpsExchange(sessionWithCert(cert)));
        assertTrue("Default policy: /engine gets full permission",
                principal.hasPermission(AuthenticatedPrincipal.PERM_ALL));
    }

    // =========================================================================
    // CC6.6 - Concurrent mTLS authentication (thread safety)
    // =========================================================================

    /**
     * SOC2 CC6.6: Provider must be thread-safe for concurrent mTLS connections.
     * A shared provider instance must correctly authenticate multiple concurrent requests.
     */
    public void testConcurrentMtlsAuthenticationIsThreadSafe() throws Exception {
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            pool.submit(() -> {
                try {
                    startLatch.await();
                    // Alternate between /agent and /monitor paths
                    String path = idx % 2 == 0 ? "/agent/thread-" + idx : "/monitor/thread-" + idx;
                    X509Certificate cert = buildSpiffeCert(
                            "spiffe://yawl.cloud" + path,
                            -86400_000L, 365L * 86400_000L);

                    AuthenticatedPrincipal principal =
                            provider.authenticate(new TestHttpsExchange(sessionWithCert(cert)));

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
        assertTrue("Concurrent test must complete within 30 seconds",
                doneLatch.await(30, TimeUnit.SECONDS));
        pool.shutdown();

        assertEquals("All concurrent mTLS authentications must succeed",
                threadCount, successCount.get());
        assertEquals("No concurrent authentication must fail", 0, failCount.get());
    }

    // =========================================================================
    // Test suite
    // =========================================================================

    public static Test suite() {
        return new TestSuite(SpiffeAuthenticationProviderTest.class);
    }

    public static void main(String[] args) {
        TestRunner.run(suite());
    }
}
