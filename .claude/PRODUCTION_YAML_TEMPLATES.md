# YAWL Production Kubernetes Templates

**Ready-to-use YAML files for 1M agent deployment**

---

## 1. PostgreSQL Configuration

**File: `postgresql-config.yaml`**

Apply with:
```bash
kubectl apply -f postgresql-config.yaml -n yawl-prod
```

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: postgresql-production
  namespace: yawl-prod
data:
  postgresql.conf: |
    # Performance tuning for 1M agents
    max_connections = 5000
    shared_buffers = 16GB
    effective_cache_size = 48GB
    work_mem = 8MB
    maintenance_work_mem = 2GB
    checkpoint_timeout = 15min
    max_wal_size = 8GB
    min_wal_size = 2GB
    wal_buffers = 16MB
    wal_level = replica
    max_wal_senders = 10
    wal_keep_size = 10GB
    shared_preload_libraries = 'pg_stat_statements,pg_trgm,btree_gin'
    log_statement = 'all'
    log_duration = true
    log_min_duration_statement = 1000
    log_connections = true
    log_disconnections = true

---
apiVersion: v1
kind: Secret
metadata:
  name: postgresql-credentials
  namespace: yawl-prod
type: Opaque
data:
  PGPASSWORD: cGFzc3dvcmQxMjM=  # base64: password123, change this!
  DB_USER: cG9zdGdyZXM=          # base64: postgres
  DB_PASSWORD: YWRtaW4MTIz        # base64: admin123, change this!
```

---

## 2. Redis Configuration

**File: `redis-config.yaml`**

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: redis-production
  namespace: yawl-prod
data:
  redis.conf: |
    # Redis configuration for 1M agent caching
    maxmemory 8gb
    maxmemory-policy allkeys-lru
    timeout 300
    tcp-keepalive 60
    loglevel notice
    databases 16
    save 900 1
    save 300 10
    save 60 10000
    stop-writes-on-bgsave-error yes
    rdbcompression yes
    rdbchecksum yes
    dbfilename dump.rdb
    slave-read-only yes
    repl-diskless-sync no
    repl-diskless-sync-delay 5

---
apiVersion: v1
kind: Secret
metadata:
  name: redis-credentials
  namespace: yawl-prod
type: Opaque
data:
  password: cmVkaXNwYXNzMTIz  # base64: redispass123, change this!
```

---

## 3. YAWL Engine Deployment

**File: `yawl-engine-deployment.yaml`**

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: yawl-engine
  namespace: yawl-prod
  labels:
    app.kubernetes.io/name: yawl-engine
    app.kubernetes.io/version: "6.0.0"
    app.kubernetes.io/component: engine
spec:
  replicas: 500
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 100
      maxUnavailable: 0
  selector:
    matchLabels:
      app.kubernetes.io/name: yawl-engine
  template:
    metadata:
      labels:
        app.kubernetes.io/name: yawl-engine
        app.kubernetes.io/version: "6.0.0"
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "9090"
        prometheus.io/path: "/actuator/prometheus"
    spec:
      affinity:
        podAntiAffinity:
          preferredDuringSchedulingIgnoredDuringExecution:
          - weight: 100
            podAffinityTerm:
              labelSelector:
                matchLabels:
                  app.kubernetes.io/name: yawl-engine
              topologyKey: kubernetes.io/hostname
          - weight: 50
            podAffinityTerm:
              labelSelector:
                matchLabels:
                  app.kubernetes.io/name: yawl-engine
              topologyKey: topology.kubernetes.io/zone
      topologySpreadConstraints:
      - maxSkew: 1
        topologyKey: topology.kubernetes.io/zone
        whenUnsatisfiable: DoNotSchedule
        labelSelector:
          matchLabels:
            app.kubernetes.io/name: yawl-engine
      securityContext:
        runAsNonRoot: true
        runAsUser: 10001
        fsGroup: 10001
        fsGroupChangePolicy: OnRootMismatch
      priorityClassName: high-priority
      serviceAccountName: yawl-engine
      containers:
      - name: engine
        image: ghcr.io/yawlfoundation/yawl/engine:6.0.0
        imagePullPolicy: IfNotPresent
        ports:
        - name: http
          containerPort: 8080
          protocol: TCP
        - name: management
          containerPort: 9090
          protocol: TCP
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "production"
        - name: JAVA_OPTS
          value: >-
            -XX:+UseContainerSupport
            -XX:MaxRAMPercentage=75.0
            -XX:InitialRAMPercentage=50.0
            -XX:+UseZGC
            -XX:+ZGenerational
            -XX:+UseCompactObjectHeaders
            -XX:+UseStringDeduplication
            -XX:+ExitOnOutOfMemoryError
            -XX:+HeapDumpOnOutOfMemoryError
            -XX:HeapDumpPath=/app/logs/heap-dump.hprof
            -Djdk.virtualThreadScheduler.parallelism=200
            -Djdk.virtualThreadScheduler.maxPoolSize=512
            -Djdk.tls.disabledAlgorithms=SSLv3,TLSv1,TLSv1.1,RC4,MD5,SHA-1,DES,3DES
        - name: SPRING_DATASOURCE_URL
          value: "jdbc:postgresql://postgres-prod-postgresql:5432/yawl"
        - name: SPRING_DATASOURCE_USERNAME
          valueFrom:
            secretKeyRef:
              name: postgresql-credentials
              key: DB_USER
        - name: SPRING_DATASOURCE_PASSWORD
          valueFrom:
            secretKeyRef:
              name: postgresql-credentials
              key: DB_PASSWORD
        - name: SPRING_REDIS_HOST
          value: "redis-prod-master"
        - name: SPRING_REDIS_PORT
          value: "6379"
        - name: SPRING_REDIS_PASSWORD
          valueFrom:
            secretKeyRef:
              name: redis-credentials
              key: password
        resources:
          requests:
            cpu: 2000m
            memory: 4Gi
          limits:
            cpu: 4000m
            memory: 6Gi
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: management
            scheme: HTTP
          initialDelaySeconds: 60
          periodSeconds: 15
          timeoutSeconds: 10
          failureThreshold: 3
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: management
            scheme: HTTP
          initialDelaySeconds: 30
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3
        volumeMounts:
        - name: tmp
          mountPath: /app/temp
        - name: logs
          mountPath: /app/logs
      volumes:
      - name: tmp
        emptyDir:
          sizeLimit: 5Gi
      - name: logs
        emptyDir:
          sizeLimit: 2Gi
      terminationGracePeriodSeconds: 120

---
apiVersion: v1
kind: Service
metadata:
  name: yawl-engine
  namespace: yawl-prod
  labels:
    app.kubernetes.io/name: yawl-engine
spec:
  type: ClusterIP
  selector:
    app.kubernetes.io/name: yawl-engine
  ports:
  - name: http
    port: 8080
    targetPort: http
    protocol: TCP
  - name: management
    port: 9090
    targetPort: management
    protocol: TCP
```

---

## 4. HorizontalPodAutoscaler

**File: `yawl-engine-hpa.yaml`**

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: yawl-engine
  namespace: yawl-prod
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: yawl-engine
  minReplicas: 500
  maxReplicas: 5000
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 65
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 75
  - type: Pods
    pods:
      metric:
        name: yawl_active_cases
      target:
        type: AverageValue
        averageValue: "1000"
  behavior:
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
      - type: Percent
        value: 10
        periodSeconds: 60
    scaleUp:
      stabilizationWindowSeconds: 0
      policies:
      - type: Percent
        value: 100
        periodSeconds: 30
      - type: Pods
        value: 100
        periodSeconds: 30
      selectPolicy: Max
```

---

## 5. PodDisruptionBudget

**File: `yawl-engine-pdb.yaml`**

```yaml
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: yawl-engine
  namespace: yawl-prod
spec:
  minAvailable: 500
  selector:
    matchLabels:
      app.kubernetes.io/name: yawl-engine
```

---

## 6. ServiceAccount & RBAC

**File: `yawl-rbac.yaml`**

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: yawl-engine
  namespace: yawl-prod
  labels:
    app.kubernetes.io/name: yawl-engine

---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: yawl-engine
  namespace: yawl-prod
rules:
- apiGroups: [""]
  resources: ["configmaps", "secrets"]
  verbs: ["get", "list", "watch"]
- apiGroups: [""]
  resources: ["pods"]
  verbs: ["get", "list", "watch"]
- apiGroups: ["batch"]
  resources: ["jobs"]
  verbs: ["get", "list", "watch"]

---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: yawl-engine
  namespace: yawl-prod
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: Role
  name: yawl-engine
subjects:
- kind: ServiceAccount
  name: yawl-engine
  namespace: yawl-prod
```

---

## 7. NetworkPolicy

**File: `yawl-network-policy.yaml`**

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: yawl-engine
  namespace: yawl-prod
spec:
  podSelector:
    matchLabels:
      app.kubernetes.io/name: yawl-engine
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - namespaceSelector:
        matchLabels:
          kubernetes.io/metadata.name: ingress-nginx
    - namespaceSelector:
        matchLabels:
          kubernetes.io/metadata.name: istio-system
    ports:
    - protocol: TCP
      port: 8080
    - protocol: TCP
      port: 9090
  egress:
  # Allow to other pods in yawl-prod
  - to:
    - namespaceSelector:
        matchLabels:
          kubernetes.io/metadata.name: yawl-prod
  # Allow to PostgreSQL
  - to:
    - podSelector:
        matchLabels:
          app.kubernetes.io/name: postgresql
    ports:
    - protocol: TCP
      port: 5432
  # Allow to Redis
  - to:
    - podSelector:
        matchLabels:
          app.kubernetes.io/name: redis
    ports:
    - protocol: TCP
      port: 6379
  # Allow DNS
  - to:
    - namespaceSelector: {}
    ports:
    - protocol: UDP
      port: 53
  # Allow external APIs (if needed)
  - to:
    - namespaceSelector: {}
    ports:
    - protocol: TCP
      port: 443

---
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-monitoring
  namespace: yawl-prod
spec:
  podSelector:
    matchLabels:
      app.kubernetes.io/name: yawl-engine
  policyTypes:
  - Ingress
  ingress:
  - from:
    - namespaceSelector:
        matchLabels:
          kubernetes.io/metadata.name: monitoring
    ports:
    - protocol: TCP
      port: 9090
```

---

## Usage Instructions

### Deploy All Templates

```bash
# 1. Apply configurations
kubectl apply -f postgresql-config.yaml
kubectl apply -f redis-config.yaml

# 2. Apply RBAC
kubectl apply -f yawl-rbac.yaml

# 3. Deploy engine
kubectl apply -f yawl-engine-deployment.yaml

# 4. Apply autoscaling
kubectl apply -f yawl-engine-hpa.yaml

# 5. Apply disruption budget
kubectl apply -f yawl-engine-pdb.yaml

# 6. Apply network policies
kubectl apply -f yawl-network-policy.yaml

# 7. Verify deployment
kubectl rollout status deployment/yawl-engine -n yawl-prod --timeout=10m
```

### Customize for Your Environment

Edit the YAML files to customize:
- Image digest (specify exact SHA256 for immutability)
- Resource limits (adjust CPU/memory based on your cluster)
- Replica counts (start with 500, scale up as needed)
- Database credentials (use proper secret management like Sealed Secrets or HashiCorp Vault)
- Node affinity (if using specific node pools)

---

## Verification Commands

```bash
# Check all components deployed
kubectl get all -n yawl-prod

# Verify pods are ready
kubectl get pods -n yawl-prod -l app.kubernetes.io/name=yawl-engine

# Check HPA status
kubectl get hpa -n yawl-prod

# View pod logs
kubectl logs -n yawl-prod -l app.kubernetes.io/name=yawl-engine --tail=50

# Test API
kubectl port-forward -n yawl-prod svc/yawl-engine 8080:8080
curl http://localhost:8080/yawl/ib
```

