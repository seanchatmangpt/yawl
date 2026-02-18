# How to Configure SPIFFE/SPIRE Zero-Trust Identity

## Prerequisites

- Kubernetes cluster (GKE, EKS, or AKS) running YAWL engine and services
- `kubectl` configured with cluster-admin access
- `spire-server` CLI available on the machine performing registration
- YAWL engine image built with the `spiffe` integration module
  (`src/org/yawlfoundation/yawl/integration/spiffe/`)

For non-Kubernetes deployments (bare-metal or VM), replace Kubernetes-specific steps
with the equivalent systemd/Docker instructions shown in the alternatives below.

## Background

YAWL uses SPIFFE SVIDs (X.509 certificates) to authenticate service-to-service calls
between the engine and dependent services (resource service, document store, custom
codelets). The `SpiffeWorkloadApiClient` fetches the SVID from the local SPIRE Agent
over a Unix socket at `/run/spire/sockets/agent.sock`. `SpiffeCredentialProvider`
wraps this with a fallback to environment variables so you can adopt SPIFFE
incrementally without breaking existing deployments.

## Steps

### 1. Deploy the SPIRE Server

Apply the SPIRE Server as a `StatefulSet` in the `spire` namespace:

```bash
kubectl create namespace spire

kubectl apply -f - <<'EOF'
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
          args: ["-config", "/run/spire/config/server.conf"]
          ports:
            - containerPort: 8081
          livenessProbe:
            httpGet:
              path: /live
              port: 8080
          readinessProbe:
            httpGet:
              path: /ready
              port: 8080
          volumeMounts:
            - name: spire-config
              mountPath: /run/spire/config
            - name: spire-data
              mountPath: /run/spire/data
      volumes:
        - name: spire-config
          configMap:
            name: spire-server
        - name: spire-data
          persistentVolumeClaim:
            claimName: spire-data
EOF
```

Provide a `ConfigMap` with `server.conf`. The critical fields are `trust_domain`
(must match across all YAWL components) and `node_attestor`:

```bash
kubectl create configmap spire-server -n spire --from-literal=server.conf='
server {
  bind_address = "0.0.0.0"
  bind_port = "8081"
  socket_path = "/tmp/spire-server/private/api.sock"
  trust_domain = "yawl.cloud"
  data_dir = "/run/spire/data"
  log_level = "INFO"
  default_svid_ttl = "1h"
}

plugins {
  DataStore "sql" {
    plugin_data {
      database_type = "sqlite3"
      connection_string = "/run/spire/data/datastore.sqlite3"
    }
  }
  NodeAttestor "k8s_sat" {
    plugin_data {
      clusters = {
        "production" = {
          use_token_review_api_validation = true
          service_account_allow_list = ["spire:spire-agent"]
        }
      }
    }
  }
  KeyManager "disk" {
    plugin_data {
      keys_path = "/run/spire/data/keys.json"
    }
  }
}
'
```

### 2. Deploy the SPIRE Agent as a DaemonSet

The SPIRE Agent runs on every node and delivers SVIDs to workloads via the Unix socket:

```bash
kubectl apply -f - <<'EOF'
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
      serviceAccountName: spire-agent
      containers:
        - name: spire-agent
          image: ghcr.io/spiffe/spire-agent:1.8.0
          args: ["-config", "/run/spire/config/agent.conf"]
          securityContext:
            privileged: true
          volumeMounts:
            - name: spire-config
              mountPath: /run/spire/config
            - name: spire-agent-socket
              mountPath: /run/spire/sockets
      volumes:
        - name: spire-config
          configMap:
            name: spire-agent
        - name: spire-agent-socket
          hostPath:
            path: /run/spire/sockets
            type: DirectoryOrCreate
EOF
```

`agent.conf` must point at the SPIRE Server and reference the same `trust_domain`:

```bash
kubectl create configmap spire-agent -n spire --from-literal=agent.conf='
agent {
  data_dir = "/run/spire"
  log_level = "INFO"
  trust_domain = "yawl.cloud"
  server_address = "spire-server.spire.svc.cluster.local"
  server_port = "8081"
  socket_path = "/run/spire/sockets/agent.sock"
}

plugins {
  NodeAttestor "k8s_sat" {
    plugin_data {
      cluster = "production"
    }
  }
  KeyManager "memory" {}
  WorkloadAttestor "k8s" {
    plugin_data {}
  }
}
'
```

### 3. Register YAWL Workloads with the SPIRE Server

Each YAWL service needs a registration entry that maps Kubernetes selectors to a SPIFFE
ID. Run these commands after the SPIRE Server pod is `Running`:

```bash
# Register the engine
kubectl exec -n spire spire-server-0 -- \
  /opt/spire/bin/spire-server entry create \
  -spiffeID spiffe://yawl.cloud/engine \
  -parentID spiffe://yawl.cloud/spire/agent/k8s_sat/production/spire/spire-agent \
  -selector k8s:ns:yawl \
  -selector k8s:sa:yawl-engine \
  -selector k8s:container-name:yawl-engine \
  -ttl 3600

# Register the resource service
kubectl exec -n spire spire-server-0 -- \
  /opt/spire/bin/spire-server entry create \
  -spiffeID spiffe://yawl.cloud/resource-service \
  -parentID spiffe://yawl.cloud/spire/agent/k8s_sat/production/spire/spire-agent \
  -selector k8s:ns:yawl \
  -selector k8s:sa:yawl-resource-service \
  -selector k8s:container-name:resource-service \
  -ttl 3600
```

Add one entry per YAWL service. The SPIFFE ID path (`/engine`, `/resource-service`)
is the identity used in the `YSpiffeAuthorizationFilter` to grant or deny access.

### 4. Mount the SPIRE Socket into YAWL Pods

The YAWL engine reads its SVID from the SPIRE Agent socket. Mount the host path in
the engine Deployment:

```yaml
# Excerpt from yawl-engine Deployment
spec:
  serviceAccountName: yawl-engine
  containers:
    - name: yawl-engine
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

Setting `SPIFFE_ENDPOINT_SOCKET` is optional but explicit — `SpiffeWorkloadApiClient`
defaults to `/run/spire/sockets/agent.sock` when the variable is absent.

### 5. Configure YAWL to Use SPIFFE Credentials in Java Code

In components that make outbound HTTP calls to other YAWL services, replace plain HTTP
clients with `SpiffeMtlsHttpClient`:

```java
// Obtain mTLS HTTP client using the workload's SVID
SpiffeWorkloadIdentity identity = SpiffeWorkloadApiClient.fetchX509Identity();
SpiffeMtlsHttpClient client = new SpiffeMtlsHttpClient(identity);

// Make a call to the resource service — mutual TLS is negotiated automatically
String response = client.get(
    "https://resource-service.yawl.svc.cluster.local/gateway?action=getRoles&...");
```

For components using `SpiffeCredentialProvider` for JWT-based service credentials:

```java
SpiffeCredentialProvider provider = SpiffeCredentialProvider.getInstance();

// Returns a JWT SVID if SPIRE is available, falls back to ZAI_API_KEY env var
String credential = provider.getCredential("zai-api");

// Log which source is in use for audit purposes
CredentialSource source = provider.getCredentialSource("zai-api");
// source == CredentialSource.SPIFFE_JWT  or  CredentialSource.ENVIRONMENT_VARIABLE
```

### 6. Enable the SPIFFE Authorization Filter

Register `YSpiffeAuthorizationFilter` in the engine's servlet configuration so that
incoming requests from services carry a valid SVID:

```java
@Component
@Order(1)
public class YSpiffeAuthorizationFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpReq = (HttpServletRequest) request;

        X509Certificate[] certs = (X509Certificate[])
            httpReq.getAttribute("jakarta.servlet.request.X509Certificate");

        if (certs == null || certs.length == 0) {
            ((HttpServletResponse) response).sendError(401, "Client certificate required");
            return;
        }

        SpiffeId spiffeId = SpiffeId.fromCertificate(certs[0]);

        boolean allowed = switch (spiffeId.getPath()) {
            case "/resource-service" -> httpReq.getRequestURI().startsWith("/yawl/ib");
            case "/engine"           -> true;
            default                  -> false;
        };

        if (allowed) {
            chain.doFilter(request, response);
        } else {
            ((HttpServletResponse) response)
                .sendError(403, "SPIFFE ID not authorised: " + spiffeId);
        }
    }
}
```

## Verify

**Check that the SPIRE Agent is issuing SVIDs:**

```bash
# From inside a YAWL pod, query the workload API directly
kubectl exec -n yawl <engine-pod> -- \
  /opt/spire/bin/spire-agent api fetch x509 \
  -socketPath /run/spire/sockets/agent.sock

# Expected: a block starting with "Received 1 svid after ..." showing a SPIFFE URI
```

**Verify the SPIFFE ID from Java:**

```java
SpiffeWorkloadIdentity identity = SpiffeWorkloadApiClient.fetchX509Identity();
System.out.println("SPIFFE ID: " + identity.getSpiffeId());
// Expected: spiffe://yawl.cloud/engine
System.out.println("Expires: " + identity.getExpiry());
// Expected: a timestamp ~1 hour from now
System.out.println("Is valid: " + identity.isValid());
// Expected: true
```

**Confirm mutual TLS handshake succeeds:**

```bash
# Test mTLS from engine pod to resource service
kubectl exec -n yawl <engine-pod> -- \
  curl -v --cert /run/spire/sockets/svid.pem \
       --key /run/spire/sockets/svid-key.pem \
       --cacert /run/spire/sockets/bundle.pem \
       https://resource-service.yawl.svc.cluster.local/gateway?action=isRunning
```

A `200 OK` response with `<response>true</response>` confirms mTLS is working.

## Troubleshooting

**`SpiffeException: failed to connect to workload API`**
The SPIRE Agent socket is not mounted or the agent is not running. Check:
```bash
kubectl get pods -n spire
ls -la /run/spire/sockets/agent.sock  # must be a socket file, not absent
```

**`Attestation failed` in SPIRE Agent logs**
The Kubernetes service account used by the pod does not match the selector in the
registration entry. Re-run `entry create` with the correct `-selector k8s:sa:` value
and restart the pod so the agent re-attests.

**SVID expiry during a long operation**
`SpiffeWorkloadApiClient` rotates SVIDs automatically in the background. If you cache
the `SpiffeWorkloadIdentity` reference across calls, always check `identity.isValid()`
before use and re-fetch from the client if it returns `false`.

**mTLS handshake fails with "certificate unknown"**
Both sides must trust the same SPIRE Server CA bundle. If the resource service is in a
different SPIRE trust domain, configure federation as described in
`docs/SPIFFE_INTEGRATION.md` under "Multi-Cloud Federation."

**Running without Kubernetes (bare-metal/Docker)**
Install the SPIRE Agent as a systemd service and set the selector to `unix:uid:<UID>`:
```bash
spire-server entry create \
  -spiffeID spiffe://yawl.cloud/engine \
  -parentID spiffe://yawl.cloud/node/myhost \
  -selector unix:uid:1000 \
  -ttl 3600
systemctl start spire-agent
```
The socket path and `SPIFFE_ENDPOINT_SOCKET` variable remain the same.
