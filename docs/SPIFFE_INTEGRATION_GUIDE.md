# SPIFFE Integration Guide for YAWL Developers

## Integration Points

This guide shows how to integrate SPIFFE workload identity into existing YAWL components.

## 1. Updating ZAI Service Clients

### Before (API Key)

```java
public class MyAgent {
    private ZaiService zaiService;

    public MyAgent() {
        String apiKey = System.getenv("ZAI_API_KEY");
        if (apiKey == null) {
            throw new IllegalStateException("ZAI_API_KEY required");
        }
        this.zaiService = new ZaiService(apiKey);
    }
}
```

### After (SPIFFE with Fallback)

```java
public class MyAgent {
    private ZaiService zaiService;

    public MyAgent() {
        // Automatically uses SPIFFE if available, falls back to ZAI_API_KEY
        this.zaiService = SpiffeEnabledZaiService.create();
    }
}
```

### After (SPIFFE Required)

```java
public class MyAgent {
    private ZaiService zaiService;
    private final SpiffeCredentialProvider credentialProvider;

    public MyAgent() {
        this.credentialProvider = SpiffeCredentialProvider.getInstance();

        if (!credentialProvider.isSpiffeAvailable()) {
            throw new IllegalStateException(
                "SPIFFE required but SPIRE Agent not available. " +
                "Deploy SPIRE: ./scripts/spiffe/deploy-spire-agent.sh"
            );
        }

        String credential = credentialProvider.getCredential("zai-api");
        this.zaiService = new ZaiService(credential);
    }
}
```

## 2. HTTP Clients with mTLS

### Before (No mTLS)

```java
HttpURLConnection conn = (HttpURLConnection) url.openConnection();
conn.setRequestProperty("Authorization", "Bearer " + apiKey);
```

### After (SPIFFE mTLS)

```java
SpiffeMtlsHttpClient client = new SpiffeMtlsHttpClient();
String response = client.post(
    "https://api.example.com/endpoint",
    requestBody,
    "application/json"
);
// Automatically uses X.509 SVID for mTLS
```

## 3. MCP Server Integration

### Update YawlMcpServer.java

```java
public class YawlMcpServer {
    private final SpiffeCredentialProvider credentialProvider;

    public YawlMcpServer(String engineUrl, String username, String password) {
        this.credentialProvider = SpiffeCredentialProvider.getInstance();

        // Log identity source
        if (credentialProvider.isSpiffeAvailable()) {
            System.err.println("SPIFFE: MCP Server using workload identity");
            credentialProvider.getWorkloadSpiffeId().ifPresent(id ->
                System.err.println("  SPIFFE ID: " + id));
        } else {
            System.err.println("SPIFFE: Not available, using environment credentials");
        }

        // Existing initialization...
    }
}
```

## 4. A2A Server Integration

### Update YawlA2AServer.java

```java
public class YawlA2AServer {
    private final SpiffeCredentialProvider credentialProvider;

    public void start() throws IOException {
        this.credentialProvider = SpiffeCredentialProvider.getInstance();

        // Get ZAI credentials via SPIFFE
        if (zaiFunctionService == null) {
            try {
                String credential = credentialProvider.getCredential("zai-api");
                this.zaiFunctionService = new ZaiFunctionService(
                    credential, yawlEngineUrl, yawlUsername, yawlPassword);
                System.err.println("SPIFFE: ZAI service initialized with " +
                    credentialProvider.getCredentialSource("zai-api"));
            } catch (SpiffeException e) {
                System.err.println("SPIFFE: ZAI not available: " + e.getMessage());
            }
        }

        // Existing initialization...
    }
}
```

## 5. Generic Agent Pattern

### Create SpiffeEnabledAgent.java

```java
public abstract class SpiffeEnabledAgent extends AutonomousAgent {
    protected final SpiffeCredentialProvider credentialProvider;
    protected final Optional<String> workloadIdentity;

    public SpiffeEnabledAgent(String capability) {
        super(capability);
        this.credentialProvider = SpiffeCredentialProvider.getInstance();
        this.workloadIdentity = credentialProvider.getWorkloadSpiffeId();

        workloadIdentity.ifPresent(id ->
            System.err.println("Agent " + capability + " identity: " + id));
    }

    @Override
    protected String getApiCredential(String service) {
        try {
            return credentialProvider.getCredential(service);
        } catch (SpiffeException e) {
            throw new RuntimeException("Failed to get credential for " + service, e);
        }
    }

    protected CredentialSource getCredentialSource(String service) {
        return credentialProvider.getCredentialSource(service);
    }
}
```

## 6. InterfaceB Client with SPIFFE

### Update InterfaceB_EnvironmentBasedClient.java

```java
public class InterfaceB_EnvironmentBasedClient {
    private final SpiffeCredentialProvider credentialProvider;

    public InterfaceB_EnvironmentBasedClient(String backEndURI) {
        this.credentialProvider = SpiffeCredentialProvider.getInstance();
        // Existing initialization...
    }

    @Override
    public String connect(String userID, String password) throws IOException {
        // Use SPIFFE identity if available for authentication
        if (credentialProvider.isSpiffeAvailable()) {
            try {
                SpiffeWorkloadIdentity identity =
                    credentialProvider.getX509Identity().orElseThrow();
                // Could implement custom auth with SPIFFE ID
                System.err.println("Authenticating as: " + identity.getSpiffeId());
            } catch (Exception e) {
                System.err.println("SPIFFE auth failed, using password: " + e.getMessage());
            }
        }

        // Fallback to existing password auth
        return super.connect(userID, password);
    }
}
```

## 7. Docker Container Labels

### docker-compose.yml

```yaml
services:
  order-processor-agent:
    image: yawl-agent:5.2
    labels:
      # SPIRE uses these labels for workload attestation
      app: yawl-agent
      capability: order-processing
      component: autonomous-agent
    environment:
      SPIFFE_ENDPOINT_SOCKET: unix:///run/spire/sockets/agent.sock
      AGENT_CAPABILITY: order-processing
    volumes:
      - spire-agent-socket:/run/spire/sockets:ro
```

## 8. Kubernetes Deployment

### deployment.yaml

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: yawl-agent-order-processor
  namespace: yawl

---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-processor
  namespace: yawl
spec:
  selector:
    matchLabels:
      app: yawl-agent
      capability: order-processing
  template:
    metadata:
      labels:
        app: yawl-agent
        capability: order-processing
    spec:
      serviceAccountName: yawl-agent-order-processor
      containers:
      - name: agent
        image: yawl-agent:5.2
        env:
        - name: SPIFFE_ENDPOINT_SOCKET
          value: unix:///run/spire/sockets/agent.sock
        - name: AGENT_CAPABILITY
          value: order-processing
        volumeMounts:
        - name: spire-agent-socket
          mountPath: /run/spire/sockets
          readOnly: true
      volumes:
      - name: spire-agent-socket
        hostPath:
          path: /run/spire/sockets
          type: Directory
```

## 9. Configuration Best Practices

### Environment Variable Strategy

```bash
# Phase 1: Parallel (SPIFFE + API keys)
export SPIFFE_ENDPOINT_SOCKET=unix:///run/spire/sockets/agent.sock
export ZAI_API_KEY=sk_1234...  # Fallback

# Phase 2: SPIFFE Required
export SPIFFE_ENDPOINT_SOCKET=unix:///run/spire/sockets/agent.sock
# Remove ZAI_API_KEY

# Phase 3: Full SPIFFE
# No env vars needed - identity from SPIRE
```

### Federation Configuration

```yaml
# /etc/spiffe/federation.yaml
local_trust_domain: prod.yawl.cloud

federations:
  - trust_domain: dev.yawl.cloud
    bundle_endpoint: https://spire-dev.yawl.cloud/bundle
    cloud: gcp
    region: us-west1

  - trust_domain: staging.yawl.cloud
    bundle_endpoint: https://spire-staging.yawl.cloud/bundle
    cloud: aws
    region: us-east-1
```

## 10. Testing SPIFFE Integration

### Unit Test Pattern

```java
@Test
public void testWithSpiffe() {
    SpiffeCredentialProvider provider = SpiffeCredentialProvider.getInstance();

    if (provider.isSpiffeAvailable()) {
        // Test with real SPIFFE
        String credential = provider.getCredential("test-service");
        assertEquals(CredentialSource.SPIFFE_JWT,
            provider.getCredentialSource("test-service"));
    } else {
        // Test fallback behavior
        String credential = provider.getCredentialOrDefault("test-service", "fallback");
        assertEquals("fallback", credential);
    }
}
```

### Integration Test with Docker

```bash
# Start SPIFFE stack
docker-compose -f docker-compose.spiffe.yml up -d

# Wait for SPIRE
sleep 10

# Run tests
docker exec yawl-engine ant test -Dtest.class=SpiffeIntegrationTest

# Verify identities
docker exec spire-server spire-server entry show
```

## 11. Monitoring and Observability

### Log SPIFFE Events

```java
public class SpiffeAwareLogger {
    private final SpiffeCredentialProvider provider;

    public void logRequest(String operation) {
        String identity = provider.getWorkloadSpiffeId()
            .orElse("no-spiffe-identity");

        StructuredLogger.info("operation", operation,
            "spiffe_id", identity,
            "credential_source", provider.getCredentialSource("zai-api"));
    }
}
```

### Metrics

```java
public class SpiffeMetrics {
    public static void recordCredentialFetch(String service, CredentialSource source) {
        MetricsCollector.increment("spiffe.credential.fetch",
            "service", service,
            "source", source.name());
    }

    public static void recordSvidRotation(String spiffeId) {
        MetricsCollector.increment("spiffe.svid.rotation",
            "spiffe_id", spiffeId);
    }
}
```

## 12. Migration Checklist

### Pre-Migration
- [ ] Deploy SPIRE Server in target environment
- [ ] Deploy SPIRE Agent on workload hosts
- [ ] Create registration entries for all workloads
- [ ] Test SVID fetch manually
- [ ] Configure federation (if multi-cloud)

### Migration Phase 1 (Parallel)
- [ ] Update code to use SpiffeCredentialProvider
- [ ] Keep existing API keys as fallback
- [ ] Deploy updated code
- [ ] Monitor both SPIFFE and env var usage
- [ ] Verify SPIFFE credentials work

### Migration Phase 2 (SPIFFE Required)
- [ ] Remove API key fallbacks from code
- [ ] Remove API keys from environment
- [ ] Redeploy
- [ ] Monitor for errors
- [ ] Verify all services using SPIFFE

### Migration Phase 3 (Full Zero-Trust)
- [ ] Implement mTLS for all service-to-service calls
- [ ] Remove all environment credentials
- [ ] Enable federation for multi-cloud
- [ ] Audit all SVID issuances
- [ ] Document final architecture

## 13. Troubleshooting Common Issues

### Issue: "Socket not found"
```bash
# Check agent is running
systemctl status spire-agent

# Check socket exists
ls -la /run/spire/sockets/agent.sock

# Check permissions
sudo chmod 755 /run/spire/sockets
```

### Issue: "Workload not attested"
```bash
# List entries
spire-server entry show

# Create entry
spire-server entry create \
  -parentID spiffe://yawl.cloud/node/myhost \
  -spiffeID spiffe://yawl.cloud/my-service \
  -selector unix:uid:$(id -u)
```

### Issue: "SVID expired"
```java
// Enable auto-rotation
SpiffeWorkloadApiClient client = new SpiffeWorkloadApiClient();
client.enableAutoRotation(Duration.ofSeconds(30));
```

## Summary

SPIFFE integration in YAWL follows these principles:

1. **Transparent Migration**: Code works with or without SPIFFE
2. **Fail Explicit**: Clear errors when credentials unavailable
3. **Zero Configuration**: Auto-detect SPIRE Agent
4. **Standards Compliant**: Follow SPIFFE specifications
5. **Production Ready**: Rotation, federation, monitoring built-in

Start with Phase 1 (parallel), validate, then move to Phase 2 and 3 when ready.
