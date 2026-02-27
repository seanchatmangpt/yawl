# How to Configure ZGC Generational GC and Compact Object Headers

## Problem

The YAWL engine running in Kubernetes uses large heap sizes (8GB) and processes many short-lived objects during workflow execution. Default GC pauses can reach 100-200ms, causing case throughput degradation and timeout-sensitive APIs to fail. You need to minimize pause times and reduce memory footprint for large-scale deployments.

## Prerequisites

- Kubernetes 1.25+ with YAWL engine pods running
- Java 21+ on the engine container (ZGC and compact object headers require Java 21)
- `kubectl` access to the YAWL namespace
- Knowledge of Kubernetes env var syntax in pod specs

## Steps

### 1. Verify Java Version in the Engine Pod

Before enabling ZGC, confirm your container has Java 21 or later:

```bash
kubectl exec -it -n yawl deployment/yawl-engine -- java -version
```

Expected output:
```
openjdk version "21" 2023-09-19
OpenJDK Runtime Environment (build 21+35-2513)
OpenJDK 64-Bit Server VM (build 21+35-2513, mixed mode, sharing)
```

If your output shows Java 17 or earlier, upgrade the base image in `k8s/base/deployments/engine-deployment.yaml` to `openjdk:21-jdk-slim` or later.

### 2. Edit the Engine Deployment to Enable ZGC Flags

Open the engine deployment YAML:

```bash
kubectl edit deployment yawl-engine -n yawl
```

Locate the `JAVA_TOOL_OPTIONS` environment variable in the `env:` section (near line 45). Update it with these JVM flags:

```yaml
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
```

Breakdown of each flag:
- `-XX:+UseZGC` — Enables the ZGC garbage collector (low-latency, sub-millisecond pauses)
- `-XX:+ZGenerational` — Enables generational mode (separates young/old objects, reduces full heap scans)
- `-XX:+UseCompactObjectHeaders` — Reduces object header size from 16 to 12 bytes (saves ~10% memory, improves CPU cache)
- `-Xmx8g -Xms4g` — Heap size (set initial to 50% of max to avoid thrashing during startup)

### 3. Save and Trigger a Rolling Deployment

Exit the editor (`:wq` in vim) to save. Kubernetes automatically applies the changes:

```bash
kubectl rollout status deployment/yawl-engine -n yawl --timeout=5m
```

Watch the rollout:
```bash
kubectl get pods -n yawl -w | grep yawl-engine
```

Each pod will terminate and restart with the new JVM options. This takes ~2-3 minutes per pod (with `maxUnavailable: 0`, at least one pod remains available).

### 4. Verify ZGC Flags Are Active

Once all pods are running, exec into one and verify the flags were applied:

```bash
kubectl exec -it -n yawl deployment/yawl-engine -- \
  java -XX:+PrintFlagsFinal -version 2>&1 | grep -E "UseZGC|ZGenerational|CompactObjectHeaders"
```

Expected output:
```
bool UseZGC                        = true {product} {default}
bool ZGenerational                 = true {product} {default}
bool UseCompactObjectHeaders       = true {product} {default}
```

### 5. Monitor GC Metrics (Optional)

Enable GC logging to confirm pause times have improved:

```yaml
- name: JAVA_TOOL_OPTIONS
  value: >-
    -XX:+UseZGC
    -XX:+ZGenerational
    -XX:+UseCompactObjectHeaders
    -Xmx8g
    -Xms4g
    -XX:+UnlockDiagnosticVMOptions
    -XX:ZUncommitDelay=30
    -Xlog:gc:file=/opt/yawl/logs/gc.log
```

Then monitor logs:
```bash
kubectl logs -f deployment/yawl-engine -n yawl | grep -i "ZGC"
```

## Verification

### 1. Check GC Behavior via JVM Flags

```bash
kubectl exec -it -n yawl deployment/yawl-engine -- \
  java -XX:+PrintFlagsFinal -version | grep -c "true"
```

Should return a high count (hundreds of flags), confirming the JVM started successfully with no conflicts.

### 2. Launch a Test Case and Monitor Throughput

Submit a workflow specification and launch 10 cases in parallel:

```bash
for i in {1..10}; do
  curl -s http://yawl-engine.yawl.svc.cluster.local:8080/yawl/ib \
    -d "action=launchCase&specID=MySpec_1_0&sessionHandle=..." &
done
wait
echo "All cases launched successfully"
```

Expect sub-second response times per case launch. With default GC, pauses would be visible in response latency.

### 3. Compare Heap Size Pre/Post

Check heap usage in pod metrics:

```bash
kubectl top pod -n yawl -l app.kubernetes.io/name=yawl-engine
```

Record memory usage (should be ~5-15% lower than before due to compact headers). Run the same workload pre- and post-upgrade to measure the improvement.

## Troubleshooting

### `java.lang.UnsupportedOperationException: UseZGC requires Java 21+`

Your container is running Java 17 or earlier. Upgrade the base image:

```bash
# In k8s/base/deployments/engine-deployment.yaml
spec:
  containers:
  - image: openjdk:21-jdk-slim  # Change from openjdk:17-jdk-slim
```

Then redeploy:
```bash
kubectl set image deployment/yawl-engine -n yawl \
  yawl-engine=openjdk:21-jdk-slim:latest
kubectl rollout status deployment/yawl-engine -n yawl
```

### `XX:+ZGenerational is disabled by default (requires special build)`

You are using an OpenJDK build that doesn't include ZGC. Use the official Oracle JDK 21 or OpenJDK 21 with ZGC support:

```bash
# Verify JDK includes ZGC
docker inspect openjdk:21-jdk-slim | grep -i "zgc\|garbage"

# If not found, use Oracle's image
image: container-registry.oracle.com/java/openjdk:21
```

### `OutOfMemoryError: Java heap space` after enabling ZGC

Compact object headers reduce memory usage, but ZGC's generational mode may expose application memory leaks. Enable heap dump on OOM:

```yaml
- name: JAVA_TOOL_OPTIONS
  value: >-
    -XX:+UseZGC
    -XX:+ZGenerational
    -XX:+UseCompactObjectHeaders
    -Xmx8g
    -Xms4g
    -XX:+HeapDumpOnOutOfMemoryError
    -XX:HeapDumpPath=/opt/yawl/logs/heapdump.hprof
```

Then analyze the dump:
```bash
kubectl cp yawl/yawl-engine-xyz:/opt/yawl/logs/heapdump.hprof ./heapdump.hprof
jhat heapdump.hprof
```

### High GC activity (constant ZGC logging to stderr)

ZGC is a concurrent GC that may show high activity under sustained load. This is expected. Monitor actual pause times instead:

```bash
kubectl logs deployment/yawl-engine -n yawl | \
  grep -o "Pause Phase 1.*" | head -5
```

Pause times should be consistently <10ms. If pause times exceed 20ms, increase heap size:

```yaml
-Xmx12g  # Increase from 8g
-Xms6g   # Increase from 4g
```

### GC log file grows too large

By default, GC logs are unbounded. Rotate them:

```yaml
-Xlog:gc:file=/opt/yawl/logs/gc.log:level=debug:filecount=5:filesize=100m
```

This keeps the 5 most recent 100MB logs, automatically deleting older ones.
