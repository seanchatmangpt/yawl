/**
 * SPIFFE/SPIRE Workload Identity Integration for YAWL
 *
 * <h2>Overview</h2>
 * This package provides SPIFFE (Secure Production Identity Framework For Everyone)
 * workload identity integration for YAWL, replacing API keys and secrets with
 * cryptographically verifiable, automatically rotated identities.
 *
 * <h2>Key Components</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.spiffe.SpiffeWorkloadIdentity} -
 *       Represents a SPIFFE SVID (X.509 or JWT) with certificate chain or token</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.spiffe.SpiffeWorkloadApiClient} -
 *       Client for SPIRE Agent Workload API via Unix domain socket</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.spiffe.SpiffeCredentialProvider} -
 *       Unified credential provider with SPIFFE-first, env-var fallback</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.spiffe.SpiffeMtlsHttpClient} -
 *       HTTP client with mTLS using SPIFFE X.509 certificates</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.spiffe.SpiffeFederationConfig} -
 *       Multi-cloud trust domain federation configuration</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Basic Credential Retrieval</h3>
 * <pre>
 * // Automatically uses SPIFFE if available, falls back to env var
 * SpiffeCredentialProvider provider = SpiffeCredentialProvider.getInstance();
 * String credential = provider.getCredential("zai-api");
 * // Returns SPIFFE JWT if available, else ZAI_API_KEY from environment
 * </pre>
 *
 * <h3>Direct Workload API Access</h3>
 * <pre>
 * SpiffeWorkloadApiClient client = new SpiffeWorkloadApiClient();
 * client.enableAutoRotation(Duration.ofSeconds(30));
 *
 * // Fetch X.509 SVID for mTLS
 * SpiffeWorkloadIdentity x509 = client.fetchX509Svid();
 * System.out.println("My identity: " + x509.getSpiffeId());
 *
 * // Fetch JWT SVID for API authentication
 * SpiffeWorkloadIdentity jwt = client.fetchJwtSvid("api.example.com");
 * String bearerToken = jwt.toBearerToken().orElse("");
 * </pre>
 *
 * <h3>Multi-Cloud Federation</h3>
 * <pre>
 * SpiffeFederationConfig federation = new SpiffeFederationConfig();
 * boolean canTrustGcp = federation.isFederated("gcp.yawl.cloud");
 * boolean canTrustAws = federation.isFederated("aws.yawl.cloud");
 *
 * // Get all GCP domains
 * List&lt;TrustDomain&gt; gcpDomains = federation.getGcpDomains();
 * </pre>
 *
 * <h2>Architecture</h2>
 * <pre>
 * Application Code
 *      ↓ SpiffeCredentialProvider
 *      ↓ Unix Domain Socket
 * SPIRE Agent (Workload API)
 *      ↓ mTLS
 * SPIRE Server (Control Plane)
 *      ↓ Cloud APIs
 * Cloud Identity (AWS IAM, GCP Workload Identity, Azure MSI)
 * </pre>
 *
 * <h2>Deployment</h2>
 * <ol>
 *   <li>Deploy SPIRE Agent: {@code ./scripts/spiffe/deploy-spire-agent.sh}</li>
 *   <li>Register workloads: {@code ./scripts/spiffe/register-yawl-workloads.sh}</li>
 *   <li>Start YAWL (automatically detects SPIRE)</li>
 * </ol>
 *
 * <h2>Security Benefits</h2>
 * <ul>
 *   <li>Short-lived credentials (1 hour default TTL)</li>
 *   <li>Automatic rotation (every 30 seconds before expiry)</li>
 *   <li>No secrets in environment variables or config files</li>
 *   <li>Cryptographic attestation of workload identity</li>
 *   <li>Multi-cloud federation without VPNs or shared secrets</li>
 *   <li>mTLS for all service-to-service communication</li>
 * </ul>
 *
 * <h2>Standards Compliance</h2>
 * <ul>
 *   <li>SPIFFE Specification: https://github.com/spiffe/spiffe/blob/main/standards/SPIFFE.md</li>
 *   <li>SPIFFE Workload API: https://github.com/spiffe/spiffe/blob/main/standards/SPIFFE_Workload_API.md</li>
 *   <li>SPIFFE Federation: https://github.com/spiffe/spiffe/blob/main/standards/SPIFFE_Federation.md</li>
 * </ul>
 *
 * <h2>Migration Path</h2>
 * <h3>Phase 1: Parallel Operation</h3>
 * SPIFFE and API keys both work. Deploy SPIRE, validate functionality.
 *
 * <h3>Phase 2: SPIFFE Required</h3>
 * Remove API keys after validation. Throw exceptions if SPIFFE unavailable.
 *
 * <h3>Phase 3: Full Zero-Trust</h3>
 * All authentication via SPIFFE. No environment variables needed.
 *
 * @see <a href="https://spiffe.io">SPIFFE Official Website</a>
 * @see <a href="https://spiffe.io/docs/latest/spire-about/">SPIRE Documentation</a>
 * @author YAWL Foundation
 * @version 5.2
 * @since 5.2
 */
package org.yawlfoundation.yawl.integration.spiffe;
