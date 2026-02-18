# ADR-009: Multi-Cloud Strategy

## Status

**ACCEPTED**

## Date

2026-02-18

## Context

YAWL v5.x deployments are typically single-cloud (on-premise or single cloud provider). Enterprise customers increasingly require:

1. **Multi-cloud portability** - Deploy on AWS, Azure, GCP, or on-premise without code changes
2. **Cloud-native integration** - Leverage cloud-specific services (AWS Textract, Azure Cognitive Services, GCP Document AI)
3. **Data sovereignty** - Keep data in specific geographic regions
4. **Vendor independence** - Avoid lock-in to any single cloud provider
5. **Disaster recovery** - Cross-region and cross-cloud failover

The existing codebase has no cloud-specific dependencies in the core engine, making it cloud-agnostic by default. However, the autonomous agent framework needs cloud-specific integrations.

## Decision

**YAWL v6.0 adopts a three-tier cloud strategy with cloud-agnostic core, cloud-native agent extensions, and Kubernetes as the universal deployment platform.**

### Architecture Overview

```
+-----------------------------------------------------------+
|                    Cloud-Agnostic Layer                    |
|  YAWL Engine, Stateless Engine, Interface A/B/X, MCP, A2A |
|  Deployed identically across AWS, Azure, GCP, on-premise  |
+-----------------------------------------------------------+
                              |
+-----------------------------------------------------------+
|                   Kubernetes Abstraction                   |
|  Helm charts, Operators, Service meshes, Ingress controllers|
|  Standardized deployment across all clouds                 |
+-----------------------------------------------------------+
                              |
+-----------------------------------------------------------+
|                  Cloud-Native Extensions                   |
|  AWS: Textract, Bedrock, S3, SQS                         |
|  Azure: Form Recognizer, Cognitive Services, Blob Storage |
|  GCP: Document AI, Vertex AI, Cloud Storage               |
+-----------------------------------------------------------+
```

### Tier 1: Cloud-Agnostic Core

The YAWL engine core remains 100% cloud-agnostic:

| Component | Cloud Dependencies | Portability |
|-----------|-------------------|-------------|
| YEngine | PostgreSQL, Redis | Any cloud |
| YStatelessEngine | PostgreSQL | Any cloud |
| Interface A/B/X | HTTP/REST | Any cloud |
| YawlMcpServer | STDIO/SSE | Any cloud |
| YawlA2AServer | HTTP/JWT | Any cloud |
| AgentRegistry | PostgreSQL/CockroachDB | Any cloud |

Configuration via environment variables:
```properties
# No cloud-specific properties in core
YAWL_DB_URL=jdbc:postgresql://${DB_HOST}:5432/yawl
YAWL_REDIS_URL=${REDIS_URL}
YAWL_ENGINE_URL=http://${ENGINE_HOST}:8080/yawl
```

### Tier 2: Kubernetes Deployment Platform

Kubernetes provides cloud abstraction for deployment:

| Cloud | Kubernetes Service | Ingress | Service Mesh |
|-------|-------------------|---------|--------------|
| AWS | EKS | ALB / NGINX | App Mesh / Istio |
| Azure | AKS | Application Gateway | Open Service Mesh |
| GCP | GKE | GCE Ingress / NGINX | Anthos Service Mesh |
| On-premise | k3s / OpenShift | NGINX / Traefik | Istio / Linkerd |

Standard Helm chart structure:
```
charts/yawl/
├── Chart.yaml
├── values.yaml              # Cloud-agnostic defaults
├── values-aws.yaml          # AWS-specific overrides
├── values-azure.yaml        # Azure-specific overrides
├── values-gcp.yaml          # GCP-specific overrides
└── templates/
    ├── engine-deployment.yaml
    ├── a2a-service.yaml
    ├── mcp-service.yaml
    └── ingress.yaml
```

### Tier 3: Cloud-Native Agent Extensions

Cloud-specific capabilities exposed through agent interfaces:

#### AWS Integration
```java
// org.yawlfoundation.yawl.integration.cloud.aws.AwsDocumentAgent
public class AwsDocumentAgent extends AbstractCloudAgent {
    private final TextractClient textract;
    private final S3Client s3;
    private final BedrockRuntimeClient bedrock;

    // Capability: document-extraction
    // Services: AWS Textract, S3
}
```

#### Azure Integration
```java
// org.yawlfoundation.yawl.integration.cloud.azure.AzureCognitiveAgent
public class AzureCognitiveAgent extends AbstractCloudAgent {
    private final DocumentAnalysisClient formRecognizer;
    private final BlobServiceClient blobStorage;
    private final CognitiveServicesClient cognitive;

    // Capability: document-extraction
    // Services: Azure Form Recognizer, Blob Storage
}
```

#### GCP Integration
```java
// org.yawlfoundation.yawl.integration.cloud.gcp.GcpDocumentAgent
public class GcpDocumentAgent extends AbstractCloudAgent {
    private final DocumentProcessorServiceClient documentAi;
    private final StorageClient cloudStorage;
    private final VertexAIClient vertex;

    // Capability: document-extraction
    // Services: GCP Document AI, Cloud Storage
}
```

### Cloud-Agnostic Abstraction Layer

Common interface for cloud-specific operations:

```java
// org.yawlfoundation.yawl.integration.cloud.CloudStorageProvider
public interface CloudStorageProvider {
    String upload(String bucket, String key, byte[] data);
    byte[] download(String bucket, String key);
    void delete(String bucket, String key);
    String getSignedUrl(String bucket, String key, Duration expiry);
}

// org.yawlfoundation.yawl.integration.cloud.DocumentExtractionProvider
public interface DocumentExtractionProvider {
    ExtractionResult extract(byte[] document, ExtractionOptions options);
    CompletableFuture<ExtractionResult> extractAsync(byte[] document);
}
```

Implementation selection via environment:
```properties
# Cloud provider selection
YAWL_CLOUD_PROVIDER=aws  # aws, azure, gcp, or none

# Auto-detection (Kubernetes)
YAWL_CLOUD_PROVIDER=${KUBERNETES_SERVICE_HOST:aws}
```

### Region and Data Sovereignty

Multi-region deployment with data residency:

```yaml
# values-aws-eu.yaml
cloud:
  provider: aws
  region: eu-west-1
  dataResidency:
    enforced: true
    allowedRegions:
      - eu-west-1
      - eu-central-1
    restrictedRegions:
      - us-east-1  # No EU data in US regions
```

### Disaster Recovery and Failover

Cross-region failover architecture:

```
Primary Region (us-east-1)     Secondary Region (eu-west-1)
+---------------------+        +---------------------+
| YAWL Engine (active)|        | YAWL Engine (standby)|
| PostgreSQL Primary  |------->| PostgreSQL Replica   |
| Redis Primary       |        | Redis Replica        |
+---------------------+        +---------------------+
         |                              ^
         | DNS Failover (Route 53)      |
         +------------------------------+
                (30s TTL, health checks)
```

## Consequences

### Positive

1. **Vendor independence**: Core engine runs anywhere without modification
2. **Cloud optimization**: Leverage best-in-class services per cloud
3. **Portability**: Migrate between clouds with Helm value changes only
4. **Compliance**: Data sovereignty enforced via configuration
5. **Resilience**: Cross-region and cross-cloud failover

### Negative

1. **Complexity**: Multiple Helm values files and cloud-specific configurations
2. **Testing burden**: Must test on each supported cloud platform
3. **Feature parity**: Cloud-specific agents may have different capabilities
4. **Cost**: Cross-region replication and standby infrastructure

### Risks and Mitigations

| Risk | Probability | Impact | Mitigation |
|------|------------|--------|------------|
| Cloud API deprecation | MEDIUM | MEDIUM | Abstraction layer isolates changes |
| Feature divergence between clouds | HIGH | LOW | Document capability matrix per cloud |
| Multi-cloud deployment complexity | MEDIUM | MEDIUM | GitOps (ArgoCD) for consistent deployment |
| Cross-region latency | HIGH | MEDIUM | Read replicas, edge caching |

## Alternatives Considered

### Single-Cloud Strategy

**Rejected.** Locks customers into one provider. Enterprise customers increasingly require multi-cloud for negotiating leverage and compliance.

### Cloud-Agnostic Only (No Native Services)

**Rejected.** Misses significant value from cloud-native services (managed AI, serverless). Customers expect cloud-optimized deployments.

### Serverless-First (Lambda/Azure Functions)

**Rejected for core engine.** YAWL's stateful engine model doesn't fit serverless well. Stateless engine could be serverless, but the complexity outweighs benefits for v6.0.

## Implementation Notes

### Helm Chart Structure

```yaml
# values.yaml (cloud-agnostic defaults)
global:
  imageRegistry: ghcr.io/yawlfoundation
  imageTag: "6.0.0"

engine:
  replicas: 2
  resources:
    requests:
      memory: "2Gi"
      cpu: "1000m"

# Cloud-agnostic storage class selection
storage:
  class: standard  # Override per cloud
```

```yaml
# values-aws.yaml
cloud:
  provider: aws

storage:
  class: gp3

ingress:
  class: alb
  annotations:
    kubernetes.io/ingress.class: alb
    alb.ingress.kubernetes.io/scheme: internet-facing

cloudNative:
  enabled: true
  agents:
    - name: aws-document-agent
      image: yawl-aws-agent:6.0.0
      replicas: 2
      env:
        - name: AWS_REGION
          value: us-east-1
```

### Cloud Detection Logic

```java
// org.yawlfoundation.yawl.integration.cloud.CloudProvider
public enum CloudProvider {
    AWS, AZURE, GCP, ON_PREMISE, UNKNOWN;

    public static CloudProvider detect() {
        if (System.getenv("KUBERNESES_SERVICE_HOST") != null) {
            String k8sHost = System.getenv("KUBERNETES_SERVICE_HOST");
            if (k8sHost.contains("eks")) return AWS;
            if (k8sHost.contains("azmk8s")) return AZURE;
            if (k8sHost.contains("gke")) return GCP;
        }
        if (System.getenv("AWS_REGION") != null) return AWS;
        if (System.getenv("AZURE_SUBSCRIPTION_ID") != null) return AZURE;
        if (System.getenv("GOOGLE_CLOUD_PROJECT") != null) return GCP;
        return ON_PREMISE;
    }
}
```

## Related ADRs

- ADR-010: Virtual Threads Scalability (thread-per-case for cloud scale)
- ADR-014: Clustering and Horizontal Scaling (multi-node deployment)
- ADR-024: Multi-Cloud Agent Deployment (agent-specific cloud topology)
- ADR-025: Agent Coordination Protocol (cross-cloud agent communication)

## Approval

**Approved by:** YAWL Architecture Team, Infrastructure Team
**Date:** 2026-02-18
**Implementation Status:** PLANNED (v6.0.0)
**Review Date:** 2026-08-18

---

**Revision History:**
- 2026-02-18: Initial ADR establishing multi-cloud strategy
