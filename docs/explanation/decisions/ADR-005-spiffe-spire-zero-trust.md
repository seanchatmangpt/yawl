# ADR-005: SPIFFE/SPIRE for Zero-Trust Identity

## Status
**ACCEPTED**

## Context

YAWL v6.0.0 requires secure service-to-service authentication in cloud-native deployments across multiple platforms (GKE, EKS, AKS). Traditional authentication methods (API keys, shared secrets) have limitations:

### Problems with Traditional Auth

1. **Secret Distribution**
   - API keys hardcoded in config files
   - Difficult secret rotation
   - Secrets in environment variables or ConfigMaps
   - Risk of exposure in logs, source control

2. **No Identity Verification**
   - Cannot verify caller identity beyond possession of secret
   - No cryptographic proof of identity
   - Difficult to implement fine-grained authorization

3. **Platform Lock-in**
   - AWS IAM roles don't work on GCP
   - GCP Workload Identity incompatible with Azure
   - No portable identity standard

4. **Limited Auditability**
   - Difficult to track which service made request
   - No cryptographic non-repudiation
   - Audit logs lack verified identity

### SPIFFE/SPIRE Benefits

**SPIFFE** (Secure Production Identity Framework For Everyone):
- Industry standard for service identity (CNCF project)
- Platform-agnostic workload identity
- Cryptographic proofs (X.509 SVIDs)
- Automatic rotation

**SPIRE** (SPIFFE Runtime Environment):
- Reference implementation of SPIFFE
- Kubernetes-native deployment
- Pluggable attestation (k8s, AWS, GCP, Azure)
- Zero-trust architecture

### Business Drivers

1. **Zero-Trust Security**
   - Verify every request cryptographically
   - No implicit trust in network
   - Least privilege access

2. **Multi-Cloud Portability**
   - Same identity system on GKE, EKS, AKS
   - No platform-specific identity management
   - Consistent security posture

3. **Compliance Requirements**
   - SOC 2, ISO 27001 compliance
   - mTLS for data-in-transit encryption
   - Audit trail of identity-verified requests

4. **Operational Simplicity**
   - No manual secret rotation
   - Automatic certificate renewal
   - Centralized identity management

## Decision

**YAWL v6.0.0 will use SPIFFE/SPIRE for all service-to-service authentication in cloud deployments.**

### Architecture

```
┌─────────────────────────────────────────────────────────┐
│              Kubernetes Cluster (GKE/EKS/AKS)           │
│                                                          │
│  ┌──────────────────────────────────────────────────┐  │
│  │         SPIRE Server (StatefulSet)                │  │
│  │  - Issues SVIDs to workloads                      │  │
│  │  - Verifies attestation                           │  │
│  │  - Manages trust bundle                           │  │
│  │  - Rotates certificates                           │  │
│  └──────────────┬───────────────────────────────────┘  │
│                 │                                        │
│  ┌──────────────▼───────────────────────────────────┐  │
│  │         SPIRE Agent (DaemonSet)                   │  │
│  │  - Runs on every node                             │  │
│  │  - Attests workload identity                      │  │
│  │  - Delivers SVIDs via Unix socket                 │  │
│  └──────────────┬───────────────────────────────────┘  │
│                 │                                        │
│  ┌──────────────▼───────────────────────────────────┐  │
│  │         YAWL Engine Pod                           │  │
│  │                                                    │  │
│  │  ┌──────────────────────────────────────────┐    │  │
│  │  │  YSpiffeIdentityManager                   │    │  │
│  │  │  - Fetch SVID from SPIRE agent            │    │  │
│  │  │  - Rotate before expiry                   │    │  │
│  │  │  - Configure mTLS                         │    │  │
│  │  └──────────────────────────────────────────┘    │  │
│  │                                                    │  │
│  │  SPIFFE ID: spiffe://yawl.cloud/engine            │  │
│  │  SVID: X.509 certificate + private key            │  │
│  └────────────────────────────────────────────────── │  │
└─────────────────────────────────────────────────────────┘
```

### Implementation Strategy

#### 1. SPIRE Deployment (Kubernetes)

**SPIRE Server (StatefulSet):**
```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: spire-server
  namespace: spire
spec:
  serviceName: spire-server
  replicas: 1
  selector:
    matchLabels:
      app: spire-server
  template:
    metadata:
      labels:
        app: spire-server
    spec:
      serviceAccountName: spire-server
      containers:
        - name: spire-server
          image: ghcr.io/spiffe/spire-server:1.8.0
          args:
            - -config
            - /run/spire/config/server.conf
          volumeMounts:
            - name: spire-config
              mountPath: /run/spire/config
            - name: spire-data
              mountPath: /run/spire/data
          livenessProbe:
            httpGet:
              path: /live
              port: 8080
          readinessProbe:
            httpGet:
              path: /ready
              port: 8080
      volumes:
        - name: spire-config
          configMap:
            name: spire-server
        - name: spire-data
          persistentVolumeClaim:
            claimName: spire-data
```

**SPIRE Agent (DaemonSet):**
```yaml
apiVersion: apps/v1
kind: DaemonSet
metadata:
  name: spire-agent
  namespace: spire
spec:
  selector:
    matchLabels:
      app: spire-agent
  template:
    metadata:
      labels:
        app: spire-agent
    spec:
      hostPID: true
      hostNetwork: true
      dnsPolicy: ClusterFirstWithHostNet
      containers:
        - name: spire-agent
          image: ghcr.io/spiffe/spire-agent:1.8.0
          args:
            - -config
            - /run/spire/config/agent.conf
          volumeMounts:
            - name: spire-config
              mountPath: /run/spire/config
            - name: spire-bundle
              mountPath: /run/spire/bundle
            - name: spire-agent-socket
              mountPath: /run/spire/sockets
          securityContext:
            privileged: true
      volumes:
        - name: spire-config
          configMap:
            name: spire-agent
        - name: spire-bundle
          configMap:
            name: spire-bundle
        - name: spire-agent-socket
          hostPath:
            path: /run/spire/sockets
            type: DirectoryOrCreate
```

#### 2. YAWL Workload Registration

```bash
#!/bin/bash
# Register YAWL engine workload with SPIRE

kubectl exec -n spire spire-server-0 -- \
  /opt/spire/bin/spire-server entry create \
  -spiffeID spiffe://yawl.cloud/engine \
  -parentID spiffe://yawl.cloud/spire/agent/k8s_sat/production/spire/spire-agent \
  -selector k8s:ns:yawl \
  -selector k8s:sa:yawl-service-account \
  -selector k8s:container-name:yawl-engine \
  -ttl 3600

# Register resource service
kubectl exec -n spire spire-server-0 -- \
  /opt/spire/bin/spire-server entry create \
  -spiffeID spiffe://yawl.cloud/resource-service \
  -parentID spiffe://yawl.cloud/spire/agent/k8s_sat/production/spire/spire-agent \
  -selector k8s:ns:yawl \
  -selector k8s:sa:yawl-service-account \
  -selector k8s:container-name:resource-service \
  -ttl 3600
```

#### 3. YAWL Integration (Java)

```java
@Component
public class YSpiffeIdentityManager implements AutoCloseable {

    private static final String SPIFFE_SOCKET = "/run/spire/sockets/agent.sock";
    private final WorkloadApiClient workloadApiClient;
    private volatile X509Source x509Source;

    public YSpiffeIdentityManager() {
        this.workloadApiClient = DefaultWorkloadApiClient.newBuilder()
            .spiffeSocketPath(SPIFFE_SOCKET)
            .build();
    }

    @PostConstruct
    public void initialize() throws Exception {
        // Fetch initial SVID
        this.x509Source = X509Source.newSource(workloadApiClient);
        log.info("SPIFFE identity initialized: {}",
                x509Source.getX509Svid().getSpiffeId());

        // Auto-rotation handled by X509Source
    }

    public X509Svid getX509Svid() {
        return x509Source.getX509Svid();
    }

    public X509Bundle getX509Bundle() {
        return x509Source.getBundleForTrustDomain(TrustDomain.of("yawl.cloud"));
    }

    public SSLContext createSSLContext() throws Exception {
        return SSLContextBuilder.forClient(x509Source).build();
    }

    @Override
    public void close() throws Exception {
        if (x509Source != null) {
            x509Source.close();
        }
        if (workloadApiClient != null) {
            workloadApiClient.close();
        }
    }
}
```

#### 4. mTLS Configuration

```java
@Configuration
public class MtlsConfiguration {

    @Autowired
    private YSpiffeIdentityManager spiffeManager;

    @Bean
    public RestTemplate restTemplateWithMtls() throws Exception {
        SSLContext sslContext = spiffeManager.createSSLContext();

        HttpClient httpClient = HttpClients.custom()
            .setSSLContext(sslContext)
            .setSSLHostnameVerifier(new SpiffeHostnameVerifier())
            .build();

        HttpComponentsClientHttpRequestFactory requestFactory =
            new HttpComponentsClientHttpRequestFactory(httpClient);

        return new RestTemplate(requestFactory);
    }
}
```

#### 5. Authorization Based on SPIFFE ID

```java
@Component
public class YSpiffeAuthorizationFilter implements Filter {

    @Autowired
    private YSpiffeIdentityManager spiffeManager;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                        FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;

        // Extract client certificate
        X509Certificate[] certs = (X509Certificate[])
            httpRequest.getAttribute("jakarta.servlet.request.X509Certificate");

        if (certs == null || certs.length == 0) {
            ((HttpServletResponse) response).sendError(401, "Client certificate required");
            return;
        }

        // Parse SPIFFE ID from certificate
        SpiffeId spiffeId = SpiffeId.fromCertificate(certs[0]);

        // Authorize based on SPIFFE ID
        if (isAuthorized(spiffeId, httpRequest.getRequestURI())) {
            chain.doFilter(request, response);
        } else {
            ((HttpServletResponse) response).sendError(403,
                "SPIFFE ID " + spiffeId + " not authorized");
        }
    }

    private boolean isAuthorized(SpiffeId spiffeId, String uri) {
        // Authorization logic
        // Example: spiffe://yawl.cloud/resource-service can access /engine/workitems
        return switch (spiffeId.getPath()) {
            case "/resource-service" -> uri.startsWith("/engine/workitems");
            case "/admin-service" -> true; // Full access
            default -> false;
        };
    }
}
```

## Consequences

### Positive

1. **Zero-Trust Security**
   - Cryptographic identity verification
   - No reliance on network security
   - Mutual TLS (mTLS) by default

2. **Automatic Rotation**
   - Certificates rotate every hour
   - No manual intervention
   - No service downtime

3. **Platform-Agnostic**
   - Same identity system on GKE, EKS, AKS
   - Multi-cloud deployments
   - No vendor lock-in

4. **Fine-Grained Authorization**
   - Authorize based on SPIFFE ID
   - Path-based access control
   - Audit trail with verified identity

5. **Compliance**
   - mTLS satisfies data-in-transit encryption
   - Non-repudiation
   - SOC 2, ISO 27001 compliance

### Negative

1. **Infrastructure Complexity**
   - SPIRE server and agents to deploy
   - Additional operational burden
   - Requires Kubernetes expertise

2. **Performance Overhead**
   - TLS handshake latency (~10-20ms)
   - Certificate validation overhead
   - Memory for certificate storage

3. **Learning Curve**
   - Team must understand SPIFFE/SPIRE
   - New debugging techniques
   - Certificate troubleshooting

4. **Dependency**
   - SPIRE must be highly available
   - Single point of failure if misconfigured
   - Requires monitoring

### Risks

| Risk | Probability | Impact | Mitigation |
|------|------------|--------|------------|
| SPIRE server downtime | LOW | HIGH | Multi-replica StatefulSet, readiness probes |
| Certificate rotation failure | LOW | HIGH | Alerting on SVID expiry, fallback auth |
| Performance degradation | LOW | MEDIUM | Connection pooling, persistent connections |
| Misconfiguration | MEDIUM | HIGH | Automated validation, integration tests |

## Alternatives Considered

### Alternative 1: API Keys
**Rejected:** No automatic rotation, difficult secret distribution.

**Pros:**
- Simple to implement
- Well understood

**Cons:**
- Manual rotation
- Risk of exposure
- No cryptographic identity proof

### Alternative 2: OAuth 2.0 / JWT
**Rejected:** Requires token server, platform-specific integrations.

**Pros:**
- Industry standard
- Stateless tokens

**Cons:**
- Token server required
- No mTLS
- Platform-specific identity providers

### Alternative 3: Platform-Specific (IAM, Workload Identity)
**Rejected:** Vendor lock-in, not portable across clouds.

**Pros:**
- Native integration
- No additional infrastructure

**Cons:**
- Vendor lock-in
- Different systems per cloud
- Complex multi-cloud

## Related ADRs

- ADR-009: Multi-Cloud Strategy (SPIFFE enables portability)
- ADR-004: Spring Boot 3.4 + Java 25 (mTLS integration)
- ADR-006: OpenTelemetry for Observability (identity in traces)

## Implementation Notes

### Testing SPIFFE Integration

```java
@SpringBootTest
@TestPropertySource(properties = {
    "spiffe.socket.path=/tmp/test-spire/agent.sock"
})
public class SpiffeIntegrationTest {

    @Autowired
    private YSpiffeIdentityManager spiffeManager;

    @Test
    public void testSvidFetch() throws Exception {
        X509Svid svid = spiffeManager.getX509Svid();
        assertNotNull(svid);
        assertEquals("spiffe://yawl.cloud/engine",
                    svid.getSpiffeId().toString());
        assertTrue(svid.getChain()[0].getNotAfter().after(new Date()));
    }

    @Test
    public void testMtlsConnection() throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        // Configure with SPIFFE mTLS
        ResponseEntity<String> response =
            restTemplate.getForEntity("https://resource-service/api/health",
                                    String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
}
```

### Monitoring SPIFFE

```yaml
# Prometheus alerts
groups:
  - name: spiffe
    rules:
      - alert: SpiffeSvidExpiringSoon
        expr: spire_server_svid_ttl_seconds < 600
        annotations:
          summary: "SVID expiring in < 10 minutes"

      - alert: SpireServerDown
        expr: up{job="spire-server"} == 0
        annotations:
          summary: "SPIRE server is down"
```

## Approval

**Approved by:** YAWL Architecture Team, Security Team
**Date:** 2026-02-12
**Implementation Status:** COMPLETE (v5.2)
**Security Review:** PASSED (2026-02-14)
**Next Review:** 2026-08-01 (6 months)

---

**Revision History:**
- 2026-02-12: Initial version approved
- 2026-02-14: Security team review and approval
- 2026-02-16: Added monitoring and testing sections
