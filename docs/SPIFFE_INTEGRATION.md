# SPIFFE/SPIRE Workload Identity Integration for YAWL

## Overview

YAWL 5.2 integrates **SPIFFE** (Secure Production Identity Framework For Everyone) and **SPIRE** (SPIFFE Runtime Environment) to provide standards-based workload identity that replaces API keys and secrets with cryptographically verifiable identities.

### Why SPIFFE?

**Before SPIFFE (API Keys Everywhere):**
```
ZAI_API_KEY=sk_1234567890abcdef...
YAWL_PASSWORD=MySecretPassword123
DATABASE_PASSWORD=hunter2
```
Problems:
- Long-lived credentials
- Manual rotation
- Secret sprawl
- No attestation

**After SPIFFE (Workload Identity Everywhere):**
```
spiffe://yawl.cloud/engine/us-central1-a/instance-1
spiffe://yawl.cloud/agent/order-processor
spiffe://yawl.cloud/service/zai-api
```
Benefits:
- Short-lived certificates (1 hour default)
- Automatic rotation
- No secrets in environment
- Cryptographic attestation

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     YAWL Workload                           │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  SpiffeCredentialProvider.getInstance()              │  │
│  │    ↓                                                  │  │
│  │  getCredential("zai-api")                            │  │
│  │    ↓                                                  │  │
│  │  1. Try SPIFFE JWT (if available)                    │  │
│  │  2. Fallback to ZAI_API_KEY env var                  │  │
│  └──────────────────────────────────────────────────────┘  │
└────────────────┬────────────────────────────────────────────┘
                 │ Unix Domain Socket
                 ↓
┌─────────────────────────────────────────────────────────────┐
│                    SPIRE Agent                              │
│  - Workload attestation (PID/UID/Docker/K8s)                │
│  - SVID issuance (X.509 + JWT)                              │
│  - Automatic rotation                                       │
│  - Socket: /run/spire/sockets/agent.sock                    │
└────────────────┬────────────────────────────────────────────┘
                 │ mTLS
                 ↓
┌─────────────────────────────────────────────────────────────┐
│                    SPIRE Server                             │
│  - Node attestation (AWS/GCP/Azure)                         │
│  - Registration entries                                     │
│  - CA management                                            │
│  - Federation (cross-cloud)                                 │
└─────────────────────────────────────────────────────────────┘
```

## Components

### Core Classes

1. **`SpiffeWorkloadIdentity`**
   - Represents a SPIFFE SVID (X.509 or JWT)
   - Contains SPIFFE ID, certificates/token, expiration
   - Validates identity freshness

2. **`SpiffeWorkloadApiClient`**
   - Connects to SPIRE Agent via Unix socket
   - Fetches X.509 and JWT SVIDs
   - Implements automatic rotation
   - No external dependencies (pure Java)

3. **`SpiffeCredentialProvider`**
   - Unified credential interface
   - SPIFFE-first, fallback to env vars
   - Singleton pattern
   - Transparent migration path

4. **`SpiffeMtlsHttpClient`**
   - HTTP client with mTLS support
   - Uses X.509 SVID for client certificates
   - Validates server SPIFFE ID

5. **`SpiffeFederationConfig`**
   - Multi-cloud trust configuration
   - Cross-domain authentication
   - YAML-based configuration

### Integration Wrappers

- **`SpiffeEnabledZaiService`**: Drop-in replacement for `ZaiService`
- **Spring Configuration**: `SpiffeConfiguration` for Spring-based apps

## Quick Start

### 1. Install SPIRE Agent

```bash
# Deploy SPIRE Agent
sudo ./scripts/spiffe/deploy-spire-agent.sh spire-server.yawl.cloud

# Verify socket
ls -la /run/spire/sockets/agent.sock

# Check agent status
systemctl status spire-agent
```

### 2. Register YAWL Workload

```bash
# Create registration entry on SPIRE Server
spire-server entry create \
  -parentID spiffe://yawl.cloud/node/myhost \
  -spiffeID spiffe://yawl.cloud/yawl-engine \
  -selector unix:uid:1000 \
  -ttl 3600

# For Docker
spire-server entry create \
  -parentID spiffe://yawl.cloud/node/myhost \
  -spiffeID spiffe://yawl.cloud/yawl-engine \
  -selector docker:label:app:yawl-engine \
  -ttl 3600

# For Kubernetes
spire-server entry create \
  -parentID spiffe://yawl.cloud/node/k8s-node-1 \
  -spiffeID spiffe://yawl.cloud/yawl-engine \
  -selector k8s:ns:yawl \
  -selector k8s:sa:yawl-engine \
  -ttl 3600
```

### 3. Configure YAWL

```bash
# Set environment variable (optional, auto-detected)
export SPIFFE_ENDPOINT_SOCKET=unix:///run/spire/sockets/agent.sock

# Remove API key (SPIFFE will be used instead)
# unset ZAI_API_KEY

# Start YAWL
java -jar yawl-engine.jar
```

### 4. Use in Code

```java
// Automatic credential selection (SPIFFE or env var)
SpiffeCredentialProvider provider = SpiffeCredentialProvider.getInstance();
String credential = provider.getCredential("zai-api");

// Use with Z.AI service
ZaiService zaiService = SpiffeEnabledZaiService.create();
String response = zaiService.chat("Hello from SPIFFE!");

// Check credential source
CredentialSource source = provider.getCredentialSource("zai-api");
System.out.println("Using: " + source); // SPIFFE_JWT or ENVIRONMENT_VARIABLE

// Get workload SPIFFE ID
Optional<String> spiffeId = provider.getWorkloadSpiffeId();
spiffeId.ifPresent(id -> System.out.println("My identity: " + id));
```

## Multi-Cloud Federation

### Configuration

Edit `/etc/spiffe/federation.yaml`:

```yaml
local_trust_domain: yawl.local

federations:
  - trust_domain: gcp.yawl.cloud
    bundle_endpoint: https://spire-server-gcp.yawl.cloud/bundle
    cloud: gcp
    region: us-central1

  - trust_domain: aws.yawl.cloud
    bundle_endpoint: https://spire-server-aws.yawl.cloud/bundle
    cloud: aws
    region: us-east-1

  - trust_domain: azure.yawl.cloud
    bundle_endpoint: https://spire-server-azure.yawl.cloud/bundle
    cloud: azure
    region: eastus
```

### Usage

```java
SpiffeFederationConfig federation = new SpiffeFederationConfig();

// Check if a service is in federated domain
boolean isGcp = federation.isFederated("gcp.yawl.cloud");

// Get cloud provider
Optional<String> cloud = federation.getCloudProvider("aws.yawl.cloud");

// List all GCP domains
List<TrustDomain> gcpDomains = federation.getGcpDomains();
```

## Deployment Scenarios

### 1. Single Cloud (GCP)

```bash
# GCP: Use Workload Identity
spire-server entry create \
  -parentID spiffe://yawl.cloud/node/gcp-instance \
  -spiffeID spiffe://yawl.cloud/yawl-engine \
  -selector gcp:project-id:yawl-prod \
  -selector gcp:zone:us-central1-a
```

### 2. Single Cloud (AWS)

```bash
# AWS: Use IAM Roles
spire-server entry create \
  -parentID spiffe://yawl.cloud/node/aws-instance \
  -spiffeID spiffe://yawl.cloud/yawl-engine \
  -selector aws:account-id:123456789012 \
  -selector aws:arn:arn:aws:iam::123456789012:role/yawl-engine
```

### 3. Multi-Cloud (GCP + AWS + Azure)

```bash
# Configure federation on each SPIRE Server
# GCP Server
spire-server federation create \
  -bundleEndpointURL https://spire-server-aws.yawl.cloud:8443 \
  -trustDomain aws.yawl.cloud

# AWS Server
spire-server federation create \
  -bundleEndpointURL https://spire-server-gcp.yawl.cloud:8443 \
  -trustDomain gcp.yawl.cloud

# Azure Server
spire-server federation create \
  -bundleEndpointURL https://spire-server-gcp.yawl.cloud:8443 \
  -trustDomain gcp.yawl.cloud
```

### 4. Kubernetes

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: yawl-engine
  namespace: yawl

---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: yawl-engine
spec:
  template:
    spec:
      serviceAccountName: yawl-engine
      containers:
      - name: yawl
        image: yawl:5.2
        env:
        - name: SPIFFE_ENDPOINT_SOCKET
          value: unix:///run/spire/sockets/agent.sock
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

## Migration from API Keys

### Phase 1: Parallel Operation

Both API keys and SPIFFE work:

```java
// Automatically uses SPIFFE if available, falls back to API key
SpiffeCredentialProvider provider = SpiffeCredentialProvider.getInstance();
String credential = provider.getCredential("zai-api");
// Uses SPIFFE JWT if SPIRE Agent running, else ZAI_API_KEY env var
```

### Phase 2: SPIFFE Required

Remove API keys after validation:

```bash
# After confirming SPIFFE works
unset ZAI_API_KEY
unset YAWL_PASSWORD
```

```java
// Will throw exception if SPIFFE not available
String credential = provider.getCredential("zai-api");
```

### Phase 3: Full SPIFFE

All services use workload identity:

```bash
# No environment variables needed
# Identity comes from workload attestation
java -jar yawl-engine.jar
```

## Security Benefits

### 1. Short-lived Credentials
- Default TTL: 1 hour
- Automatic rotation every 30 seconds before expiry
- No long-lived secrets

### 2. Cryptographic Attestation
- Prove workload identity without passwords
- Cloud provider verifies instance identity
- Kubernetes verifies service account

### 3. Zero-Trust Networking
- Every service has unique SPIFFE ID
- mTLS with automatic certificate rotation
- No network-based trust assumptions

### 4. Audit Trail
- All SVID issuances logged
- Track which workloads accessed what
- Detect anomalous identity requests

## Troubleshooting

### Socket Not Found

```bash
# Check SPIRE Agent is running
systemctl status spire-agent

# Check socket permissions
ls -la /run/spire/sockets/agent.sock

# Check logs
journalctl -u spire-agent -n 50
```

### Attestation Failed

```bash
# Check registration entries
spire-server entry show

# Verify workload selector
docker inspect <container> | grep -A5 Labels
kubectl get pod <pod> -o yaml | grep serviceAccount
```

### Federation Issues

```bash
# Test bundle endpoint
curl https://spire-server-gcp.yawl.cloud/bundle

# Check federation status
spire-server federation list
```

## Performance

- **SVID fetch latency**: <10ms (local Unix socket)
- **Auto-rotation overhead**: Negligible (background thread)
- **Memory footprint**: ~5MB per workload
- **No external dependencies**: Pure Java implementation

## Resources

- SPIFFE Specification: https://spiffe.io/docs/latest/spiffe-about/
- SPIRE Documentation: https://spiffe.io/docs/latest/spire-about/
- Java SPIFFE Library: https://github.com/spiffe/java-spiffe
- SPIRE Production Deployment: https://spiffe.io/docs/latest/deploying/

## Support

For YAWL-specific SPIFFE issues:
- GitHub: https://github.com/yawlfoundation/yawl/issues
- Mailing List: yawl-users@lists.sourceforge.net

For SPIFFE/SPIRE issues:
- SPIFFE Slack: https://spiffe.slack.com
- GitHub: https://github.com/spiffe/spire/issues
