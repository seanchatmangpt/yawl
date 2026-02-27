# Tutorial 11: Scale YAWL to 1,000,000 Active Cases

By the end of this tutorial you will have a YAWL cluster running with ZGC generational garbage collection, UUID-based case identifiers, tenant isolation via ScopedValue, and a Kubernetes HPA (Horizontal Pod Autoscaler) capable of managing 1M concurrent workflow cases across 20 replicas.

---

## Prerequisites

You need the following tools and infrastructure already running:

```bash
kubectl version --client
```

Expected: `v1.28` or higher. Your cluster must support HPA with metrics: `metrics.k8s.io` API available.

```bash
helm version
```

Expected: `v3.10.x` or higher. Used to template and deploy the YAWL stack.

```bash
java -version
```

Expected: `openjdk version "25.x.x"`. YAWL v6.0.0 is compiled against Java 25.

```bash
docker version
```

Expected: `Docker version 24.x` or higher. Needed to build and test container images.

Verify your Kubernetes cluster has at least three worker nodes with 4 CPU cores and 10 GB RAM each. Check:

```bash
kubectl get nodes -o wide
```

Expected output shows 3+ nodes each with capacity `cpu: 4000m`, `memory: 10Gi`.

Verify Kubernetes metrics are available (required for HPA CPU target):

```bash
kubectl api-resources | grep HorizontalPodAutoscaler
```

Expected: `horizontalpodautoscalers` resource exists. If not available, install metrics-server on your cluster:

```bash
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
```

---

## Step 1: Verify Java 25 ScopedValue context

YAWL v6.0.0 uses Java 25's ScopedValue API for lightweight tenant context propagation to virtual threads. Verify your engine image is built with Java 25:

```bash
docker inspect yawl/engine:6.0.0 | jq '.ContainerConfig.Env'
```

Look for the image tag confirming Java 25 is present. If not already built, build the modernized image locally:

```bash
cd /home/user/yawl
docker build -f Dockerfile.modernized -t yawl/engine:6.0.0 .
```

Expected output ends with:

```
 => => naming to docker.io/library/yawl/engine:6.0.0                0.2s
```

The engine uses `ScopedTenantContext` to bind a tenant context for each incoming request without thread-local state that pins virtual threads. Read the implementation to understand:

```bash
cat /home/user/yawl/src/org/yawlfoundation/yawl/engine/ScopedTenantContext.java | head -100
```

Key methods you will use:
- `ScopedTenantContext.runWithTenant(ctx, () -> {...})` — binds tenant context for the lambda
- `ScopedTenantContext.getTenantContext()` — retrieves the current tenant within scope
- `ScopedTenantContext.runParallel(ctx, tasks)` — executes multiple tasks in parallel, all sharing the same tenant context via `StructuredTaskScope`

The context is automatically released when the scope exits — no manual cleanup required.

---

## Step 2: Subscribe to WorkflowEventBus for case lifecycle events

YAWL publishes workflow events (case created, task completed, case terminated) to a `WorkflowEventBus` without blocking the hot execution path. This is essential for scaling to 1M cases: event publishing is non-blocking and back-pressure is applied only if subscribers are slow.

The default implementation uses Java 21+ `java.util.concurrent.Flow` API with one `SubmissionPublisher` per event type. No external Kafka needed for single-cluster deployments.

Create a test listener that subscribes to case completion events:

```bash
cat > /tmp/CaseEventListener.java << 'EOF'
package org.yawlfoundation.yawl.integration;

import org.yawlfoundation.yawl.engine.spi.WorkflowEventBus;
import org.yawlfoundation.yawl.stateless.listener.event.YEventType;
import java.util.concurrent.atomic.AtomicLong;

public class CaseEventListener {
    private final WorkflowEventBus eventBus;
    private final AtomicLong completedCases = new AtomicLong(0);

    public CaseEventListener(WorkflowEventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void start() {
        // Subscribe to case completion events
        eventBus.subscribe(YEventType.CASE_COMPLETED, event -> {
            completedCases.incrementAndGet();
            if (completedCases.get() % 100_000 == 0) {
                System.out.println("Cases completed: " + completedCases.get());
            }
        });
    }

    public long getCompletedCount() {
        return completedCases.get();
    }
}
EOF
```

Note: The `FlowWorkflowEventBus` implementation handles back-pressure via bounded buffers (256 items per event type by default). At 1M cases, ensure your subscribers keep up or consider a Kafka adapter (out of scope for this tutorial).

---

## Step 3: Launch cases with UUID identifiers

YAWL v6.0.0 generates case IDs as UUIDs instead of sequential integers. This eliminates cross-replica coordination for case ID allocation and allows each pod to independently launch cases without atomic counter contention.

The `YCaseNbrStore` class generates UUIDs via `getNextCaseNbr(pmgr)`:

```java
public String getNextCaseNbr(YPersistenceManager pmgr) {
    return UUID.randomUUID().toString();
}
```

Each case gets a unique UUID (e.g., `123e4567-e89b-12d3-a456-426614174000`). UUIDs are never persisted; the legacy integer counter is kept only for backward compatibility with old code paths.

Create a test client that launches 10,000 cases concurrently against the engine running in Kubernetes:

```bash
cat > /tmp/CaseLauncher.java << 'EOF'
package org.yawlfoundation.yawl.integration;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

public class CaseLauncher {
    private final String engineUrl;
    private final HttpClient httpClient;
    private final ExecutorService executor;
    private final AtomicLong launchCount = new AtomicLong(0);

    public CaseLauncher(String engineUrl) {
        this.engineUrl = engineUrl;
        this.httpClient = HttpClient.newBuilder().build();
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public void launchCases(String specVersion, int count) throws InterruptedException {
        for (int i = 0; i < count; i++) {
            executor.submit(() -> launchSingleCase(specVersion));
        }
        executor.shutdown();
        executor.awaitTermination(Long.MAX_VALUE, java.util.concurrent.TimeUnit.SECONDS);
    }

    private void launchSingleCase(String specVersion) {
        try {
            String url = engineUrl + "/api/v1/case/launch";
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                    "{\"specificationVersion\": \"" + specVersion + "\"}"))
                .build();

            HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 201 || response.statusCode() == 200) {
                launchCount.incrementAndGet();
                if (launchCount.get() % 1000 == 0) {
                    System.out.println("Cases launched: " + launchCount.get());
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to launch case: " + e.getMessage());
        }
    }

    public long getLaunchCount() {
        return launchCount.get();
    }
}
EOF
```

The launcher uses virtual threads (one per case) to avoid thread pool saturation. At 10K concurrent cases, the OS carries ~10K virtual threads on ~2-4 platform threads via the carrier pool.

---

## Step 4: Create Kubernetes namespace and deploy engine replicas

YAWL's Kubernetes deployment uses a Deployment with 3 initial replicas and a HorizontalPodAutoscaler to scale up to 20 replicas when case queue depth exceeds 5000 items per pod.

First, create the YAWL namespace:

```bash
kubectl create namespace yawl
```

Expected output:

```
namespace/yawl created
```

Create the ConfigMap with engine configuration:

```bash
kubectl create configmap yawl-config \
  --from-literal=JAVA_OPTS="-Djdk.virtualThreadScheduler.parallelism=200 -Djdk.virtualThreadScheduler.maxPoolSize=256" \
  --from-literal=DATABASE_TYPE=postgresql \
  --from-literal=DATABASE_PATH=/opt/yawl/data \
  -n yawl
```

Expected:

```
configmap/yawl-config created
```

Create a Secret with database credentials (replace with your actual values):

```bash
kubectl create secret generic yawl-db-credentials \
  --from-literal=DATABASE_USER=yawl \
  --from-literal=DATABASE_PASSWORD=SecurePassword123 \
  --from-literal=DATABASE_URL=jdbc:postgresql://postgres.yawl.svc.cluster.local:5432/yawl \
  -n yawl
```

Expected:

```
secret/yawl-db-credentials created
```

Create a ServiceAccount for the engine pods:

```bash
kubectl apply -f - << 'EOF'
apiVersion: v1
kind: ServiceAccount
metadata:
  name: yawl-service-account
  namespace: yawl
EOF
```

Expected:

```
serviceaccount/yawl-service-account created
```

---

## Step 5: Deploy the engine Deployment manifest

The engine Deployment configures ZGC with compact object headers, sets heap limits, and uses pod anti-affinity to spread replicas across nodes.

Apply the Deployment:

```bash
kubectl apply -f - << 'EOF'
apiVersion: apps/v1
kind: Deployment
metadata:
  name: yawl-engine
  namespace: yawl
  labels:
    app.kubernetes.io/name: yawl-engine
    app.kubernetes.io/component: engine
    app.kubernetes.io/part-of: yawl
    app.kubernetes.io/version: "6.0.0"
spec:
  replicas: 3
  selector:
    matchLabels:
      app.kubernetes.io/name: yawl-engine
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  template:
    metadata:
      labels:
        app.kubernetes.io/name: yawl-engine
        app.kubernetes.io/component: engine
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "8080"
        prometheus.io/path: "/engine/metrics"
    spec:
      serviceAccountName: yawl-service-account
      securityContext:
        runAsNonRoot: true
        runAsUser: 1000
        fsGroup: 1000
      containers:
        - name: yawl-engine
          image: yawl/engine:6.0.0
          imagePullPolicy: IfNotPresent
          ports:
            - name: http
              containerPort: 8080
              protocol: TCP
          env:
            - name: JAVA_TOOL_OPTIONS
              value: >-
                -XX:+UseZGC
                -XX:+ZGenerational
                -XX:+UseCompactObjectHeaders
                -Xmx8g
                -Xms4g
                -XX:+HeapDumpOnOutOfMemoryError
                -XX:HeapDumpPath=/opt/yawl/logs/heapdump.hprof
            - name: JAVA_OPTS
              valueFrom:
                configMapKeyRef:
                  name: yawl-config
                  key: JAVA_OPTS
            - name: DATABASE_TYPE
              valueFrom:
                configMapKeyRef:
                  name: yawl-config
                  key: DATABASE_TYPE
            - name: DATABASE_PATH
              valueFrom:
                configMapKeyRef:
                  name: yawl-config
                  key: DATABASE_PATH
            - name: DATABASE_USER
              valueFrom:
                secretKeyRef:
                  name: yawl-db-credentials
                  key: DATABASE_USER
            - name: DATABASE_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: yawl-db-credentials
                  key: DATABASE_PASSWORD
            - name: DATABASE_URL
              valueFrom:
                secretKeyRef:
                  name: yawl-db-credentials
                  key: DATABASE_URL
          envFrom:
            - configMapRef:
                name: yawl-config
          resources:
            requests:
              cpu: "2000m"
              memory: "4Gi"
            limits:
              cpu: "4000m"
              memory: "10Gi"
          livenessProbe:
            httpGet:
              path: /engine/health
              port: 8080
            initialDelaySeconds: 120
            periodSeconds: 30
            timeoutSeconds: 10
            failureThreshold: 3
          readinessProbe:
            httpGet:
              path: /engine/ready
              port: 8080
            initialDelaySeconds: 60
            periodSeconds: 10
            timeoutSeconds: 5
            failureThreshold: 3
          volumeMounts:
            - name: yawl-data
              mountPath: /opt/yawl/data
            - name: yawl-logs
              mountPath: /opt/yawl/logs
      volumes:
        - name: yawl-data
          emptyDir: {}
        - name: yawl-logs
          emptyDir: {}
      affinity:
        podAntiAffinity:
          preferredDuringSchedulingIgnoredDuringExecution:
            - weight: 100
              podAffinityTerm:
                labelSelector:
                  matchLabels:
                    app.kubernetes.io/name: yawl-engine
                topologyKey: kubernetes.io/hostname
EOF
```

Expected output:

```
deployment.apps/yawl-engine created
```

Wait for the replicas to become ready (this takes 60-120 seconds):

```bash
kubectl rollout status deployment/yawl-engine -n yawl --timeout=3m
```

Expected:

```
Waiting for deployment "yawl-engine" rollout to finish: 0 of 3 updated, 0 of 3 total...
...
deployment "yawl-engine" successfully rolled out
```

Verify all three replicas are running:

```bash
kubectl get pods -n yawl -l app.kubernetes.io/name=yawl-engine
```

Expected output:

```
NAME                          READY   STATUS    RESTARTS   AGE
yawl-engine-5bf8f6d5ff-2zz6h   1/1     Running   0          90s
yawl-engine-5bf8f6d5ff-6bnq7   1/1     Running   0          85s
yawl-engine-5bf8f6d5ff-lpw4x   1/1     Running   0          75s
```

Verify each pod is ready by checking the liveness probe:

```bash
kubectl logs -n yawl -l app.kubernetes.io/name=yawl-engine --tail=5
```

Expected: Logs show `Starting YAWL Engine...` and eventual `Listening on port 8080`.

---

## Step 6: Apply the HorizontalPodAutoscaler manifest

The HPA scales the engine Deployment based on two metrics:
1. **Primary**: Average work item queue depth per pod (target: 5000 items/pod)
2. **Secondary**: CPU utilization (target: 70%)

The HPA scales up at most 3 pods per minute to avoid thundering herd and down at most 1 pod every 2 minutes after a 5-minute wait.

Apply the HPA:

```bash
kubectl apply -f - << 'EOF'
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
  minReplicas: 3
  maxReplicas: 20
  metrics:
    - type: Pods
      pods:
        metric:
          name: yawl_workitem_queue_depth
        target:
          type: AverageValue
          averageValue: "5000"
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 60
      policies:
        - type: Pods
          value: 3
          periodSeconds: 60
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
        - type: Pods
          value: 1
          periodSeconds: 120
EOF
```

Expected output:

```
horizontalpodautoscaler.autoscaling/yawl-engine-hpa created
```

Verify the HPA is created and observing metrics:

```bash
kubectl get hpa -n yawl
```

Expected output:

```
NAME              REFERENCE                   TARGETS   MINPODS   MAXPODS   REPLICAS   AGE
yawl-engine-hpa   Deployment/yawl-engine      0%/70%    3         20        3          10s
```

The `TARGETS` column shows `<queue-depth>/<cpu-utilization>`. Initially both metrics should be low since no cases are running yet.

---

## Step 7: Create a Service to expose the engine

Create a LoadBalancer or NodePort Service so test clients can reach the engine from outside the cluster:

```bash
kubectl apply -f - << 'EOF'
apiVersion: v1
kind: Service
metadata:
  name: yawl-engine-service
  namespace: yawl
spec:
  type: LoadBalancer
  selector:
    app.kubernetes.io/name: yawl-engine
  ports:
    - name: http
      port: 8080
      targetPort: 8080
      protocol: TCP
EOF
```

Expected:

```
service/yawl-engine-service created
```

Get the external IP address (this may take a few seconds):

```bash
kubectl get service yawl-engine-service -n yawl
```

Expected output (example):

```
NAME                    TYPE           CLUSTER-IP      EXTERNAL-IP      PORT(S)          AGE
yawl-engine-service     LoadBalancer   10.96.123.45    34.56.78.90      8080:31234/TCP   5s
```

If `EXTERNAL-IP` is pending, wait and retry:

```bash
kubectl get service yawl-engine-service -n yawl -w
```

Once you have the external IP (e.g., `34.56.78.90`), test connectivity to the engine health endpoint:

```bash
curl -s http://34.56.78.90:8080/engine/health
```

Expected response:

```json
{"status":"UP","components":{"db":{"status":"UP"}}}
```

---

## Step 8: Launch test load and watch HPA scale

Now launch a wave of test cases and watch the HPA respond by scaling the Deployment.

Using the CaseLauncher from Step 3, launch 50,000 cases concurrently:

```bash
# In a test environment, simulate case launches via a load generator
# (In production, replace with your real workflow specifications)

java -cp /tmp CaseLauncher http://34.56.78.90:8080 my-spec-version 50000
```

As cases queue up in the work item queue, the custom metric `yawl_workitem_queue_depth` will increase. Once the average queue depth per pod exceeds 5000 items, the HPA will scale up.

Open a terminal and watch the HPA and Deployment scaling in real time:

```bash
kubectl get hpa -n yawl -w
```

Expected output (you should see the REPLICAS column increase every 60 seconds):

```
NAME              REFERENCE                   TARGETS         MINPODS   MAXPODS   REPLICAS   AGE
yawl-engine-hpa   Deployment/yawl-engine      8500/60%        3         20        3          2m
yawl-engine-hpa   Deployment/yawl-engine      8500/60%        3         20        6          3m
yawl-engine-hpa   Deployment/yawl-engine      7000/58%        3         20        9          4m
yawl-engine-hpa   Deployment/yawl-engine      4500/50%        3         20        12         5m
yawl-engine-hpa   Deployment/yawl-engine      2000/40%        3         20        15         6m
```

In parallel, watch the Deployment status:

```bash
kubectl rollout status deployment/yawl-engine -n yawl --timeout=10m
```

And watch the pods being created:

```bash
kubectl get pods -n yawl -l app.kubernetes.io/name=yawl-engine -w
```

Expected: You will see new pods in `ContainerCreating` status, then `Running`. Eventually all pods reach `Ready 1/1`.

Once the case queue drains (all 50,000 cases complete), the HPA will begin scaling down. After 5 minutes of low utilization, it will remove one pod every 2 minutes until it returns to the minimum of 3 replicas.

---

## Step 9: Confirm ZGC pause times and heap behavior

ZGC (Z Garbage Collector) with `-XX:+ZGenerational` achieves sub-millisecond pause times even at 1M cases. Verify this by inspecting the GC logs.

Check the JVM logs for pause time metrics:

```bash
kubectl logs -n yawl deployment/yawl-engine | grep -i "gc\|pause"
```

Expected output includes lines like:

```
[0.123s][info][gc] GC(1) Garbage Collection (Generational)
[0.124s][info][gc,phases] GC(1)  Pause Mark Start 0.001ms
[0.150s][info][gc,phases] GC(1)  Mark 26ms
[0.180s][info][gc,phases] GC(1)  Pause Mark End 0.002ms
[0.181s][info][gc] GC(1) Garbage Collection (Generational) 1000M->450M(8192M) 26.001ms
```

Note: All pause times (Mark Start, Mark End) are sub-millisecond. This is what enables YAWL to handle 1M cases with <1ms GC latency.

For a single pod, inspect memory usage during the load test:

```bash
kubectl top pods -n yawl -l app.kubernetes.io/name=yawl-engine
```

Expected output (example):

```
NAME                          CPU(cores)   MEMORY(Mi)
yawl-engine-5bf8f6d5ff-2zz6h   2200m        5400Mi
yawl-engine-5bf8f6d5ff-6bnq7   2100m        5350Mi
yawl-engine-5bf8f6d5ff-lpw4x   2050m        5380Mi
```

Each pod uses 4-6 GB of heap during peak load. The heap quickly shrinks after cases complete (generational GC collects the dead case objects).

Verify compact object headers are active. Check the engine logs for the GC configuration:

```bash
kubectl logs -n yawl deployment/yawl-engine | grep -i "compact"
```

Expected: The startup logs should show that `-XX:+UseCompactObjectHeaders` is enabled. This saves 4-8 bytes per Java object, which at 1M cases translates to 4-8 GB heap savings.

---

## Step 10: Scale down and clean up

Once you are done with the load test, delete the engine stack:

```bash
kubectl delete deployment yawl-engine -n yawl
```

Expected:

```
deployment.apps "yawl-engine" deleted
```

Delete the HPA:

```bash
kubectl delete hpa yawl-engine-hpa -n yawl
```

Expected:

```
horizontalpodautoscaler.autoscaling "yawl-engine-hpa" deleted
```

Delete the Service:

```bash
kubectl delete service yawl-engine-service -n yawl
```

Expected:

```
service "yawl-engine-service" deleted
```

Delete the namespace (which removes all remaining resources):

```bash
kubectl delete namespace yawl
```

Expected:

```
namespace "yawl" deleted
```

---

## What you learned

- **ScopedValue for tenant isolation**: Java 25 ScopedValue binds tenant context automatically to virtual threads without pinning the carrier thread. Use `ScopedTenantContext.runWithTenant(ctx, () -> {...})` to scope tenant context for request handling.

- **UUID case identifiers**: `YCaseNbrStore.getNextCaseNbr()` generates UUIDs, eliminating cross-replica coordination for case ID allocation. Each pod independently launches cases without atomic counter contention.

- **WorkflowEventBus for decoupled publishing**: Events are published non-blocking via `FlowWorkflowEventBus` using Java 21+ Flow API. No external Kafka required for single-cluster deployments. Subscribe to events (e.g., `YEventType.CASE_COMPLETED`) without blocking the engine's hot path.

- **ZGC with generational mode**: The `-XX:+UseZGC -XX:+ZGenerational` flags achieve sub-millisecond GC pause times even at 1M cases. Compact object headers (`-XX:+UseCompactObjectHeaders`) save 4-8 bytes per object — significant at scale.

- **HorizontalPodAutoscaler on custom metrics**: Kubernetes HPA scales the engine Deployment based on work item queue depth and CPU utilization. The configuration scales up 3 pods per minute and down 1 pod per 2 minutes after a 5-minute stability window.

- **Pod anti-affinity**: The Deployment uses `podAntiAffinity` to spread replicas across nodes, improving fault tolerance and load distribution.

- **Helm-style templating**: ConfigMaps and Secrets externalize engine configuration (Java options, database credentials) from the Deployment manifest, enabling easy replication across environments.

---

## Next steps

- **How-to**: [Configure ZGC and Compact Object Headers](../how-to/configure-zgc-compact-headers.md) — Deep dive into GC tuning for 1M+ cases.
- **Reference**: [GlobalCaseRegistry SPI](../reference/spi-case-registry.md) — Learn to replace the in-memory case registry with a distributed Redis-backed variant.
- **Explanation**: [Why ScopedValue?](../explanation/why-scoped-value.md) — Understand virtual thread safety and tenant context propagation.
- **Tutorial**: [Add Prometheus metrics to the engine](../tutorials/12-prometheus-metrics.md) — Instrument the workflow engine with custom metrics for production observability.

For production deployments at 1M cases:
1. Use a dedicated PostgreSQL cluster with read replicas for horizontal scaling of queries.
2. Consider a Kafka adapter for WorkflowEventBus if event subscribers are slow or geographically distributed.
3. Monitor ZGC pause times and heap utilization via Prometheus + Grafana.
4. Use a distributed case registry (e.g., Redis) if pods need to look up cases for other tenants on different nodes.
