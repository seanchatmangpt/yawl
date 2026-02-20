# How to Deploy YAWL to Kubernetes

## Problem Statement

You need to deploy the YAWL workflow engine to a Kubernetes cluster for production workloads. This guide covers:

- Namespace isolation and resource allocation
- Horizontal scaling with automatic pod autoscaling
- Persistent storage for workflow data
- Health monitoring and observability
- Security hardening with network policies
- Rolling updates with zero downtime

## Prerequisites

- Kubernetes cluster (GKE, EKS, AKS, or self-managed) version 1.28+
- `kubectl` configured with cluster access
- Container registry access (Docker Hub, GCR, ECR)
- PostgreSQL database (managed or in-cluster)
- OpenTelemetry collector for observability (optional but recommended)

## Architecture Overview

```
                    +------------------+
                    |  Ingress Controller
                    +--------+---------+
                             |
           +-----------------+-----------------+
           |                 |                 |
    +------v------+  +-------v-------+  +------v------+
    | yawl-engine |  | yawl-engine   |  | yawl-engine |
    |   (pod-1)   |  |   (pod-2)     |  |   (pod-3)   |
    +------+------+  +-------+-------+  +------+------+
           |                 |                 |
           +-----------------+-----------------+
                             |
           +-----------------+-----------------+
           |                 |                 |
    +------v------+  +-------v-------+  +------v------+
    |  PostgreSQL |  | Redis Cache   |  | OTEL Collector
    | (primary)   |  | (session)     |  | (traces)    |
    +-------------+  +---------------+  +-------------+
```

## Step-by-Step Deployment

### Step 1: Create Namespace and Base Resources

```bash
# Create namespace
kubectl create namespace yawl

# Add standard labels
kubectl label namespace yawl \
    app.kubernetes.io/name=yawl \
    app.kubernetes.io/version=6.0.0-alpha
```

### Step 2: Create Secrets

```bash
# Generate secure secrets
JWT_SIGNING_KEY=$(openssl rand -base64 64 | tr -d '\n')
ADMIN_API_KEY=$(openssl rand -base64 32 | tr -d '\n')
ENCRYPTION_KEY=$(openssl rand -base64 32 | tr -d '\n')

# Create secret
kubectl create secret generic yawl-secrets \
    --namespace=yawl \
    --from-literal=DB_USER=yawl_user \
    --from-literal=DB_PASSWORD="$(openssl rand -base64 24)" \
    --from-literal=JWT_SIGNING_KEY="$JWT_SIGNING_KEY" \
    --from-literal=ADMIN_API_KEY="$ADMIN_API_KEY" \
    --from-literal=ENCRYPTION_KEY="$ENCRYPTION_KEY"
```

**Important:** Store these secrets in a secure vault (HashiCorp Vault, AWS Secrets Manager) for production. The Kubernetes secret shown is for demonstration only.

### Step 3: Create ConfigMap

```bash
kubectl apply -f - <<'EOF'
apiVersion: v1
kind: ConfigMap
metadata:
  name: yawl-config
  namespace: yawl
data:
  # Application profile
  SPRING_PROFILES_ACTIVE: "production,kubernetes"

  # Database configuration
  DB_TYPE: "postgres"
  DB_HOST: "postgres.yawl.svc.cluster.local"
  DB_PORT: "5432"
  DB_NAME: "yawl"

  # JVM Options (Java 25 optimized)
  JAVA_OPTS: |-
    -XX:+UseContainerSupport
    -XX:MaxRAMPercentage=75.0
    -XX:InitialRAMPercentage=50.0
    -XX:+UseZGC
    -XX:+ZGenerational
    -XX:+UseStringDeduplication
    -XX:+ExitOnOutOfMemoryError
    -XX:+HeapDumpOnOutOfMemoryError
    -XX:HeapDumpPath=/app/logs/heap-dump.hprof
    -Djava.security.egd=file:/dev/./urandom
    -Djava.io.tmpdir=/app/temp
    -Dfile.encoding=UTF-8
    -Djdk.virtualThreadScheduler.parallelism=200
    -Djdk.virtualThreadScheduler.maxPoolSize=256
    -Djdk.tracePinnedThreads=short

  # Actuator endpoints
  MANAGEMENT_HEALTH_PROBES_ENABLED: "true"
  MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS: "when-authorized"
  MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE: "health,info,metrics,prometheus"

  # Logging
  LOGGING_LEVEL_ROOT: "INFO"
  LOGGING_LEVEL_ORG_YAWLFOUNDATION: "DEBUG"
  TZ: "UTC"

  # OpenTelemetry
  OTEL_SERVICE_NAME: "yawl-engine"
  OTEL_TRACES_EXPORTER: "otlp"
  OTEL_METRICS_EXPORTER: "prometheus"
  OTEL_LOGS_EXPORTER: "otlp"
  OTEL_EXPORTER_OTLP_ENDPOINT: "http://otel-collector.yawl.svc.cluster.local:4317"

  # Ports
  MAIN_PORT: "8080"
  MANAGEMENT_PORT: "9090"
  RESOURCE_PORT: "8081"
EOF
```

### Step 4: Create Persistent Storage

```bash
kubectl apply -f - <<'EOF'
# Data persistence
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: yawl-data-pvc
  namespace: yawl
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 10Gi
  storageClassName: standard  # Adjust for your cluster
---
# Logs persistence
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: yawl-logs-pvc
  namespace: yawl
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 5Gi
  storageClassName: standard
EOF
```

### Step 5: Create Service Account and RBAC

```bash
kubectl apply -f - <<'EOF'
apiVersion: v1
kind: ServiceAccount
metadata:
  name: yawl-service-account
  namespace: yawl
automountServiceAccountToken: false  # Disable if not needed
---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: yawl-role
  namespace: yawl
rules:
  # Allow reading configmaps and secrets
  - apiGroups: [""]
    resources: ["configmaps", "secrets"]
    verbs: ["get", "list"]
  # Allow reading pods (for service discovery)
  - apiGroups: [""]
    resources: ["pods"]
    verbs: ["get", "list"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: yawl-role-binding
  namespace: yawl
subjects:
  - kind: ServiceAccount
    name: yawl-service-account
    namespace: yawl
roleRef:
  kind: Role
  name: yawl-role
  apiGroup: rbac.authorization.k8s.io
EOF
```

### Step 6: Deploy the Engine

```bash
kubectl apply -f - <<'EOF'
apiVersion: apps/v1
kind: Deployment
metadata:
  name: yawl-engine
  namespace: yawl
  labels:
    app.kubernetes.io/name: yawl
    app.kubernetes.io/component: engine
    app.kubernetes.io/version: "6.0.0-alpha"
spec:
  replicas: 2
  revisionHistoryLimit: 10
  selector:
    matchLabels:
      app.kubernetes.io/name: yawl
      app.kubernetes.io/component: engine
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0  # Zero downtime during updates
  template:
    metadata:
      labels:
        app.kubernetes.io/name: yawl
        app.kubernetes.io/component: engine
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "9090"
        prometheus.io/path: "/actuator/prometheus"
    spec:
      serviceAccountName: yawl-service-account
      terminationGracePeriodSeconds: 60

      # Pod anti-affinity for high availability
      affinity:
        podAntiAffinity:
          preferredDuringSchedulingIgnoredDuringExecution:
            - weight: 100
              podAffinityTerm:
                labelSelector:
                  matchLabels:
                    app.kubernetes.io/name: yawl
                    app.kubernetes.io/component: engine
                topologyKey: kubernetes.io/hostname

      # Security context
      securityContext:
        runAsNonRoot: true
        runAsUser: 1000
        runAsGroup: 1000
        fsGroup: 1000
        seccompProfile:
          type: RuntimeDefault

      containers:
        - name: yawl-engine
          image: yawl/engine:6.0.0-alpha
          imagePullPolicy: IfNotPresent

          ports:
            - name: http
              containerPort: 8080
              protocol: TCP
            - name: management
              containerPort: 9090
              protocol: TCP
            - name: resource
              containerPort: 8081
              protocol: TCP

          envFrom:
            - configMapRef:
                name: yawl-config
            - secretRef:
                name: yawl-secrets

          env:
            - name: POD_NAME
              valueFrom:
                fieldRef:
                  fieldPath: metadata.name
            - name: POD_NAMESPACE
              valueFrom:
                fieldRef:
                  fieldPath: metadata.namespace
            - name: POD_IP
              valueFrom:
                fieldRef:
                  fieldPath: status.podIP

          resources:
            requests:
              cpu: "500m"
              memory: "1Gi"
            limits:
              cpu: "4000m"
              memory: "8Gi"

          # Liveness probe - restart if unhealthy
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: management
            initialDelaySeconds: 120
            periodSeconds: 30
            timeoutSeconds: 15
            failureThreshold: 3

          # Readiness probe - remove from service if not ready
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: management
            initialDelaySeconds: 60
            periodSeconds: 10
            timeoutSeconds: 10
            failureThreshold: 3

          # Startup probe - allow slow startup
          startupProbe:
            httpGet:
              path: /actuator/health/liveness
              port: management
            initialDelaySeconds: 30
            periodSeconds: 10
            failureThreshold: 30  # 5 minutes max startup

          volumeMounts:
            - name: yawl-data
              mountPath: /app/data
            - name: yawl-logs
              mountPath: /app/logs
            - name: yawl-specifications
              mountPath: /app/specifications
            - name: yawl-temp
              mountPath: /app/temp

          securityContext:
            allowPrivilegeEscalation: false
            readOnlyRootFilesystem: false
            capabilities:
              drop:
                - ALL

      volumes:
        - name: yawl-data
          persistentVolumeClaim:
            claimName: yawl-data-pvc
        - name: yawl-logs
          persistentVolumeClaim:
            claimName: yawl-logs-pvc
        - name: yawl-specifications
          emptyDir: {}
        - name: yawl-temp
          emptyDir: {}
EOF
```

### Step 7: Create Services

```bash
kubectl apply -f - <<'EOF'
# Internal ClusterIP service
apiVersion: v1
kind: Service
metadata:
  name: yawl-engine
  namespace: yawl
spec:
  type: ClusterIP
  selector:
    app.kubernetes.io/name: yawl
    app.kubernetes.io/component: engine
  ports:
    - name: http
      port: 8080
      targetPort: http
    - name: management
      port: 9090
      targetPort: management
    - name: resource
      port: 8081
      targetPort: resource
---
# External NodePort service (for development)
apiVersion: v1
kind: Service
metadata:
  name: yawl-engine-external
  namespace: yawl
spec:
  type: NodePort
  selector:
    app.kubernetes.io/name: yawl
    app.kubernetes.io/component: engine
  ports:
    - name: http
      port: 8080
      targetPort: http
      nodePort: 30080
EOF
```

### Step 8: Configure Horizontal Pod Autoscaler

```bash
kubectl apply -f - <<'EOF'
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: yawl-engine-hpa
  namespace: yawl
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: yawl-engine
  minReplicas: 2
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 80
  behavior:
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
        - type: Percent
          value: 10
          periodSeconds: 60
        - type: Pods
          value: 1
          periodSeconds: 60
      selectPolicy: Min
    scaleUp:
      stabilizationWindowSeconds: 60
      policies:
        - type: Percent
          value: 100
          periodSeconds: 30
        - type: Pods
          value: 2
          periodSeconds: 30
      selectPolicy: Max
EOF
```

### Step 9: Configure Pod Disruption Budget

```bash
kubectl apply -f - <<'EOF'
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: yawl-engine-pdb
  namespace: yawl
spec:
  minAvailable: 1
  selector:
    matchLabels:
      app.kubernetes.io/name: yawl
      app.kubernetes.io/component: engine
EOF
```

### Step 10: Configure Network Policy

```bash
kubectl apply -f - <<'EOF'
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: yawl-network-policy
  namespace: yawl
spec:
  podSelector:
    matchLabels:
      app.kubernetes.io/name: yawl
  policyTypes:
    - Ingress
    - Egress
  ingress:
    # Allow from ingress controller
    - from:
        - namespaceSelector:
            matchLabels:
              name: ingress-nginx
      ports:
        - port: 8080
        - port: 9090
    # Allow from monitoring
    - from:
        - namespaceSelector:
            matchLabels:
              name: monitoring
      ports:
        - port: 9090
  egress:
    # Allow DNS
    - to:
        - namespaceSelector: {}
      ports:
        - port: 53
          protocol: UDP
        - port: 53
          protocol: TCP
    # Allow to PostgreSQL
    - to:
        - podSelector:
            matchLabels:
              app.kubernetes.io/name: postgres
      ports:
        - port: 5432
    # Allow to OpenTelemetry
    - to:
        - podSelector:
            matchLabels:
              app.kubernetes.io/name: otel-collector
      ports:
        - port: 4317
        - port: 4318
EOF
```

## Verify Deployment

### Check Pod Status

```bash
# List pods
kubectl get pods -n yawl

# Expected output:
# NAME                          READY   STATUS    RESTARTS   AGE
# yawl-engine-7d8f9c5b4-x2k1n   1/1     Running   0          2m
# yawl-engine-7d8f9c5b4-p9m3q   1/1     Running   0          2m
```

### Check Service Health

```bash
# Port-forward for local testing
kubectl port-forward -n yawl svc/yawl-engine 8080:8080 9090:9090 &

# Check health endpoint
curl http://localhost:8080/actuator/health

# Expected response:
# {"status":"UP","groups":["liveness","readiness"]}
```

### Check Logs

```bash
# Stream logs from all pods
kubectl logs -n yawl -l app.kubernetes.io/name=yawl -f --max-log-requests=10
```

### Check HPA Status

```bash
kubectl get hpa -n yawl

# Expected output:
# NAME              REFERENCE                TARGETS         MINPODS   MAXPODS   REPLICAS   AGE
# yawl-engine-hpa   Deployment/yawl-engine   45%/70%         2         10        2          5m
```

## Ingress Configuration (Production)

For production, use an Ingress controller with TLS:

```bash
kubectl apply -f - <<'EOF'
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: yawl-ingress
  namespace: yawl
  annotations:
    kubernetes.io/ingress.class: "nginx"
    cert-manager.io/cluster-issuer: "letsencrypt-prod"
    nginx.ingress.kubernetes.io/proxy-body-size: "50m"
    nginx.ingress.kubernetes.io/proxy-read-timeout: "300"
    nginx.ingress.kubernetes.io/proxy-send-timeout: "300"
spec:
  tls:
    - hosts:
        - yawl.example.com
      secretName: yawl-tls
  rules:
    - host: yawl.example.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: yawl-engine
                port:
                  number: 8080
EOF
```

## Deploying the Stateless Engine

For stateless engine deployments (horizontal scaling without database):

```yaml
# Modified deployment for stateless engine
apiVersion: apps/v1
kind: Deployment
metadata:
  name: yawl-stateless
  namespace: yawl
spec:
  replicas: 3
  template:
    spec:
      containers:
        - name: yawl-engine
          env:
            - name: YAWL_ENGINE_MODE
              value: "stateless"
            - name: YAWL_IDLE_TIMEOUT_MS
              value: "300000"
            - name: YAWL_CASE_STORE_PATH
              value: "/app/cases"
          volumeMounts:
            - name: cases
              mountPath: /app/cases
      volumes:
        - name: cases
          emptyDir: {}  # Or use shared PVC for persistence
```

## Upgrading

### Rolling Update

```bash
# Update image version
kubectl set image deployment/yawl-engine \
    yawl-engine=yawl/engine:6.1.0 \
    -n yawl

# Watch rollout status
kubectl rollout status deployment/yawl-engine -n yawl

# Rollback if needed
kubectl rollout undo deployment/yawl-engine -n yawl
```

### Zero-Downtime Verification

```bash
# During upgrade, verify no failed requests
while true; do
    curl -s -o /dev/null -w "%{http_code}\n" https://yawl.example.com/actuator/health
    sleep 1
done
```

## Troubleshooting

### Pod stuck in Pending

```bash
# Check events
kubectl describe pod -n yawl <pod-name>

# Common causes:
# - Insufficient resources: Adjust resource requests
# - PVC not bound: Check storage class
# - Node selector mismatch: Check node labels
```

### Pod in CrashLoopBackOff

```bash
# Check logs
kubectl logs -n yawl <pod-name> --previous

# Common causes:
# - Database connection failure: Verify DB credentials and network
# - OOM killed: Increase memory limits
# - Configuration error: Check ConfigMap values
```

### Liveness probe failing

```bash
# Check probe response
kubectl exec -n yawl <pod-name> -- \
    curl -s http://localhost:9090/actuator/health/liveness

# Increase initialDelaySeconds if JVM warmup is slow
```

### HPA not scaling

```bash
# Check metrics server
kubectl get pods -n kube-system | grep metrics-server

# Check HPA details
kubectl describe hpa -n yawl yawl-engine-hpa
```

### Network policy blocking traffic

```bash
# Temporarily disable for debugging
kubectl delete networkpolicy -n yawl yawl-network-policy

# Test connectivity
kubectl exec -n yawl <pod-name> -- \
    curl -s http://postgres.yawl.svc.cluster.local:5432
```

## Related Documentation

- [Migrate to Stateless Engine](/docs/how-to/migrate-to-stateless-engine.md) — Stateless migration guide
- [Configure A2A Authentication](/docs/how-to/configure-a2a-authentication.md) — A2A authentication setup
- [Configure SPIFFE/SPIRE](/docs/how-to/configure-spiffe.md) — Zero-trust identity
- `kubernetes/yawl-deployment.yaml` — Full deployment manifest
