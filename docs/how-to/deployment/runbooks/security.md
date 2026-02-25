# Kubernetes Security Runbook - YAWL v6.0.0

**Production Security Operations Guide**
**Version:** 6.0.0
**Date:** 2026-02-16

---

## Table of Contents

1. [SPIFFE/SPIRE Operations](#1-spiffespire-operations)
2. [mTLS Configuration](#2-mtls-configuration)
3. [Network Policies](#3-network-policies)
4. [RBAC Configuration](#4-rbac-configuration)
5. [Pod Security Standards](#5-pod-security-standards)
6. [Secret Rotation](#6-secret-rotation)
7. [Security Incident Response](#7-security-incident-response)
8. [Vulnerability Management](#8-vulnerability-management)

---

## 1. SPIFFE/SPIRE Operations

### 1.1 Deploy SPIRE Infrastructure

#### Deploy SPIRE Server

```bash
# Create SPIRE namespace
kubectl create namespace spire

# Deploy SPIRE server
kubectl apply -f - <<EOF
apiVersion: v1
kind: ServiceAccount
metadata:
  name: spire-server
  namespace: spire
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: spire-server
  namespace: spire
data:
  server.conf: |
    server {
      bind_address = "0.0.0.0"
      bind_port = "8081"
      trust_domain = "yawl.cloud"
      data_dir = "/run/spire/data"
      log_level = "INFO"
      default_x509_svid_ttl = "1h"
      default_jwt_svid_ttl = "5m"
    }

    plugins {
      DataStore "sql" {
        plugin_data {
          database_type = "postgres"
          connection_string = "postgresql://spire:password@postgres-service:5432/spire"
        }
      }

      KeyManager "memory" {
        plugin_data {}
      }

      NodeAttestor "k8s_sat" {
        plugin_data {
          clusters = {
            "production" = {
              service_account_allow_list = ["spire:spire-agent"]
            }
          }
        }
      }

      UpstreamAuthority "disk" {
        plugin_data {
          cert_file_path = "/run/spire/ca/ca.pem"
          key_file_path = "/run/spire/ca/ca-key.pem"
        }
      }
    }
---
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
          ports:
            - containerPort: 8081
              name: grpc
            - containerPort: 8080
              name: healthz
          volumeMounts:
            - name: spire-config
              mountPath: /run/spire/config
            - name: spire-data
              mountPath: /run/spire/data
            - name: spire-ca
              mountPath: /run/spire/ca
          livenessProbe:
            httpGet:
              path: /live
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 30
          readinessProbe:
            httpGet:
              path: /ready
              port: 8080
            initialDelaySeconds: 10
            periodSeconds: 10
          resources:
            requests:
              memory: "256Mi"
              cpu: "250m"
            limits:
              memory: "512Mi"
              cpu: "500m"
      volumes:
        - name: spire-config
          configMap:
            name: spire-server
        - name: spire-ca
          secret:
            secretName: spire-ca
  volumeClaimTemplates:
    - metadata:
        name: spire-data
      spec:
        accessModes: ["ReadWriteOnce"]
        resources:
          requests:
            storage: 1Gi
---
apiVersion: v1
kind: Service
metadata:
  name: spire-server
  namespace: spire
spec:
  type: ClusterIP
  selector:
    app: spire-server
  ports:
    - name: grpc
      port: 8081
      targetPort: 8081
EOF
```

#### Deploy SPIRE Agent

```bash
kubectl apply -f - <<EOF
apiVersion: v1
kind: ServiceAccount
metadata:
  name: spire-agent
  namespace: spire
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: spire-agent
  namespace: spire
data:
  agent.conf: |
    agent {
      data_dir = "/run/spire"
      log_level = "INFO"
      trust_domain = "yawl.cloud"
      server_address = "spire-server.spire"
      server_port = "8081"
      socket_path = "/run/spire/sockets/agent.sock"
    }

    plugins {
      NodeAttestor "k8s_sat" {
        plugin_data {
          cluster = "production"
        }
      }

      KeyManager "memory" {
        plugin_data {}
      }

      WorkloadAttestor "k8s" {
        plugin_data {
          skip_kubelet_verification = true
        }
      }
    }
---
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
      serviceAccountName: spire-agent
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
            - name: spire-agent-socket
              mountPath: /run/spire/sockets
            - name: spire-token
              mountPath: /var/run/secrets/tokens
          livenessProbe:
            httpGet:
              path: /live
              port: 8080
            initialDelaySeconds: 15
            periodSeconds: 60
          readinessProbe:
            httpGet:
              path: /ready
              port: 8080
            initialDelaySeconds: 5
            periodSeconds: 5
          securityContext:
            privileged: true
          resources:
            requests:
              memory: "128Mi"
              cpu: "100m"
            limits:
              memory: "256Mi"
              cpu: "200m"
      volumes:
        - name: spire-config
          configMap:
            name: spire-agent
        - name: spire-agent-socket
          hostPath:
            path: /run/spire/sockets
            type: DirectoryOrCreate
        - name: spire-token
          projected:
            sources:
              - serviceAccountToken:
                  path: spire-agent
                  expirationSeconds: 7200
                  audience: spire-server
EOF
```

### 1.2 Register YAWL Workloads

```bash
#!/bin/bash
# Register YAWL Engine
kubectl exec -n spire spire-server-0 -- \
  /opt/spire/bin/spire-server entry create \
  -spiffeID spiffe://yawl.cloud/engine \
  -parentID spiffe://yawl.cloud/spire/agent/k8s_sat/production/spire/spire-agent \
  -selector k8s:ns:yawl \
  -selector k8s:sa:yawl-service-account \
  -selector k8s:container-name:yawl-engine \
  -ttl 3600

# Register Resource Service
kubectl exec -n spire spire-server-0 -- \
  /opt/spire/bin/spire-server entry create \
  -spiffeID spiffe://yawl.cloud/resource-service \
  -parentID spiffe://yawl.cloud/spire/agent/k8s_sat/production/spire/spire-agent \
  -selector k8s:ns:yawl \
  -selector k8s:sa:yawl-service-account \
  -selector k8s:container-name:resource-service \
  -ttl 3600

# Register Autonomous Agent
kubectl exec -n spire spire-server-0 -- \
  /opt/spire/bin/spire-server entry create \
  -spiffeID spiffe://yawl.cloud/autonomous-agent \
  -parentID spiffe://yawl.cloud/spire/agent/k8s_sat/production/spire/spire-agent \
  -selector k8s:ns:yawl \
  -selector k8s:sa:yawl-service-account \
  -selector k8s:container-name:autonomous-agent \
  -ttl 3600
```

### 1.3 Verify SPIRE Health

```bash
# Check SPIRE server health
kubectl exec -n spire spire-server-0 -- \
  /opt/spire/bin/spire-server healthcheck

# List registered entries
kubectl exec -n spire spire-server-0 -- \
  /opt/spire/bin/spire-server entry show

# Check agent health
kubectl get pods -n spire -l app=spire-agent

# View agent logs
kubectl logs -n spire -l app=spire-agent --tail=50
```

---

## 2. mTLS Configuration

### 2.1 Configure YAWL for mTLS

```yaml
# YAWL Deployment with mTLS
apiVersion: apps/v1
kind: Deployment
metadata:
  name: yawl-engine
  namespace: yawl
spec:
  template:
    spec:
      serviceAccountName: yawl-service-account
      containers:
        - name: yawl-engine
          image: yawl/engine:5.2.0
          volumeMounts:
            - name: spire-agent-socket
              mountPath: /run/spire/sockets
              readOnly: true
          env:
            - name: SPIFFE_SOCKET_PATH
              value: /run/spire/sockets/agent.sock
            - name: YAWL_MTLS_ENABLED
              value: "true"
      volumes:
        - name: spire-agent-socket
          hostPath:
            path: /run/spire/sockets
            type: Directory
```

### 2.2 Test mTLS Connectivity

```bash
# Test mTLS between services
kubectl run -it --rm test-mtls --image=curlimages/curl --restart=Never -- sh

# Inside the pod, test connection
curl --cert /run/spire/svid.pem \
     --key /run/spire/key.pem \
     --cacert /run/spire/bundle.pem \
     https://yawl-engine.yawl.svc.cluster.local:8443/engine/health
```

---

## 3. Network Policies

### 3.1 Default Deny All

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: default-deny-all
  namespace: yawl
spec:
  podSelector: {}
  policyTypes:
    - Ingress
    - Egress
```

### 3.2 Allow YAWL Engine Traffic

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: yawl-engine-policy
  namespace: yawl
spec:
  podSelector:
    matchLabels:
      app: yawl-engine
  policyTypes:
    - Ingress
    - Egress
  ingress:
    # Allow from resource service
    - from:
        - podSelector:
            matchLabels:
              app: resource-service
      ports:
        - protocol: TCP
          port: 8080
    # Allow from ingress controller
    - from:
        - namespaceSelector:
            matchLabels:
              name: ingress-nginx
      ports:
        - protocol: TCP
          port: 8080
  egress:
    # Allow to database
    - to:
        - podSelector:
            matchLabels:
              app: postgres
      ports:
        - protocol: TCP
          port: 5432
    # Allow to SPIRE agent (hostPath socket access handled separately)
    # Allow DNS
    - to:
        - namespaceSelector:
            matchLabels:
              name: kube-system
        - podSelector:
            matchLabels:
              k8s-app: kube-dns
      ports:
        - protocol: UDP
          port: 53
```

---

## 4. RBAC Configuration

### 4.1 Service Account

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: yawl-service-account
  namespace: yawl
```

### 4.2 Role for YAWL Engine

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: yawl-engine-role
  namespace: yawl
rules:
  # Read ConfigMaps
  - apiGroups: [""]
    resources: ["configmaps"]
    verbs: ["get", "list", "watch"]
  # Read Secrets
  - apiGroups: [""]
    resources: ["secrets"]
    verbs: ["get", "list"]
  # Read Pods (for health checks)
  - apiGroups: [""]
    resources: ["pods"]
    verbs: ["get", "list"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: yawl-engine-rolebinding
  namespace: yawl
subjects:
  - kind: ServiceAccount
    name: yawl-service-account
    namespace: yawl
roleRef:
  kind: Role
  name: yawl-engine-role
  apiGroup: rbac.authorization.k8s.io
```

---

## 5. Pod Security Standards

### 5.1 Pod Security Policy (Restricted)

```yaml
apiVersion: policy/v1beta1
kind: PodSecurityPolicy
metadata:
  name: yawl-restricted
spec:
  privileged: false
  allowPrivilegeEscalation: false
  requiredDropCapabilities:
    - ALL
  volumes:
    - 'configMap'
    - 'emptyDir'
    - 'projected'
    - 'secret'
    - 'downwardAPI'
    - 'persistentVolumeClaim'
  hostNetwork: false
  hostIPC: false
  hostPID: false
  runAsUser:
    rule: 'MustRunAsNonRoot'
  seLinux:
    rule: 'RunAsAny'
  supplementalGroups:
    rule: 'RunAsAny'
  fsGroup:
    rule: 'RunAsAny'
  readOnlyRootFilesystem: false
```

### 5.2 Security Context

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: yawl-engine
  namespace: yawl
spec:
  template:
    spec:
      securityContext:
        runAsNonRoot: true
        runAsUser: 1000
        fsGroup: 1000
        seccompProfile:
          type: RuntimeDefault
      containers:
        - name: yawl-engine
          securityContext:
            allowPrivilegeEscalation: false
            readOnlyRootFilesystem: true
            capabilities:
              drop:
                - ALL
          volumeMounts:
            - name: tmp
              mountPath: /tmp
            - name: cache
              mountPath: /app/cache
      volumes:
        - name: tmp
          emptyDir: {}
        - name: cache
          emptyDir: {}
```

---

## 6. Secret Rotation

### 6.1 External Secrets Operator

```yaml
apiVersion: external-secrets.io/v1beta1
kind: SecretStore
metadata:
  name: gcpsm-secret-store
  namespace: yawl
spec:
  provider:
    gcpsm:
      projectID: "yawl-prod"
      auth:
        workloadIdentity:
          clusterLocation: us-central1
          clusterName: yawl-prod
          serviceAccountRef:
            name: yawl-service-account
---
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: yawl-db-credentials
  namespace: yawl
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: gcpsm-secret-store
    kind: SecretStore
  target:
    name: yawl-db-credentials
    creationPolicy: Owner
  data:
    - secretKey: DATABASE_PASSWORD
      remoteRef:
        key: yawl-db-password
```

### 6.2 Manual Secret Rotation

```bash
# Rotate database password
NEW_PASSWORD=$(openssl rand -base64 32)

# Update in Secret Manager (GCP)
echo -n "$NEW_PASSWORD" | \
  gcloud secrets versions add yawl-db-password --data-file=-

# Update database user password
kubectl exec -it postgres-0 -n yawl -- psql -U postgres -c \
  "ALTER USER yawl_engine WITH PASSWORD '$NEW_PASSWORD';"

# Force secret refresh
kubectl delete secret yawl-db-credentials -n yawl
# External Secrets Operator will recreate it

# Rolling restart to pick up new secret
kubectl rollout restart deployment/yawl-engine -n yawl
```

---

## 7. Security Incident Response

### 7.1 Unauthorized Access Detected

**Symptoms:**
- Authentication failures in logs
- SPIFFE ID rejections
- Unusual access patterns

**Immediate Actions:**
```bash
# 1. Check recent authentication failures
kubectl logs -n yawl deployment/yawl-engine | grep "403\|401" | tail -100

# 2. Review SPIFFE entries
kubectl exec -n spire spire-server-0 -- \
  /opt/spire/bin/spire-server entry show | grep -A 10 "Entry ID"

# 3. Revoke compromised SPIFFE entry
kubectl exec -n spire spire-server-0 -- \
  /opt/spire/bin/spire-server entry delete -entryID <ENTRY_ID>

# 4. Rotate all secrets
./rotate-secrets.sh

# 5. Review audit logs
kubectl logs -n yawl deployment/yawl-engine | grep "SECURITY"
```

### 7.2 Certificate Expiry

**Symptoms:**
- TLS handshake failures
- "Certificate expired" errors

**Resolution:**
```bash
# Check SVID expiry
kubectl exec -n spire spire-server-0 -- \
  /opt/spire/bin/spire-server entry show | grep -A 5 "SPIFFE ID"

# Force SVID renewal
kubectl rollout restart deployment/yawl-engine -n yawl

# Verify new SVID
kubectl logs -n yawl deployment/yawl-engine | grep "SPIFFE identity initialized"
```

---

## 8. Vulnerability Management

### 8.1 Scan Container Images

```bash
# Scan with Trivy
trivy image yawl/engine:5.2.0

# Scan with Grype
grype yawl/engine:5.2.0
```

### 8.2 Dependency Scanning

```bash
# OWASP Dependency Check
./mvnw org.owasp:dependency-check-maven:check

# Snyk scan
snyk test --all-projects
```

### 8.3 Apply Security Patches

```bash
# Update base image
docker build --build-arg BASE_IMAGE=eclipse-temurin:25-jre-jammy \
  -t yawl/engine:5.2.1 .

# Rebuild and deploy
./mvnw clean package jib:build
kubectl set image deployment/yawl-engine \
  yawl-engine=yawl/engine:5.2.1 -n yawl
```

---

## Appendix: Security Checklist

### Pre-Deployment Security Checklist

- [ ] SPIRE server deployed and healthy
- [ ] SPIRE agents running on all nodes
- [ ] Workload entries registered
- [ ] mTLS configured and tested
- [ ] Network policies applied
- [ ] RBAC roles configured
- [ ] Pod Security Standards enforced
- [ ] Secrets stored in external provider
- [ ] Container images scanned for vulnerabilities
- [ ] Security context configured (non-root, read-only filesystem)
- [ ] Audit logging enabled
- [ ] Security monitoring alerts configured

### Monthly Security Review

- [ ] Review SPIFFE entries for unauthorized workloads
- [ ] Rotate database passwords
- [ ] Update container base images
- [ ] Run vulnerability scans
- [ ] Review authentication logs for anomalies
- [ ] Test disaster recovery procedures
- [ ] Review and update network policies
- [ ] Audit RBAC permissions

---

**Document Owner:** Security Team
**Last Updated:** 2026-02-16
**Review Cycle:** Monthly
