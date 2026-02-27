# How to Tune Kubernetes HPA for Workflow Case Load

## Problem

The YAWL engine handles variable case load throughout the day (peak hours: 10,000 cases/hour; off-peak: 500 cases/hour). Kubernetes Horizontal Pod Autoscaler (HPA) should scale pods up during peaks and down during troughs, but default settings may scale too slowly, too aggressively, or waste resources. You need to tune HPA thresholds to match your traffic patterns and cost constraints.

## Prerequisites

- Kubernetes 1.25+ cluster with HPA controller (`metrics-server` installed)
- YAWL engine deployed with Prometheus metrics (`yawl_workitem_queue_depth` gauge enabled)
- `kubectl` admin access to the YAWL namespace
- Understanding of HPA metrics: queue depth, CPU utilization
- Load testing capability (to validate scaling behavior)

## Steps

### 1. Review the Current HPA Configuration

The default HPA configuration from `k8s/base/hpa-engine.yaml`:

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: yawl-engine-hpa
  namespace: yawl
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: engine-deployment
  minReplicas: 3
  maxReplicas: 20
  metrics:
    - type: Pods
      pods:
        metric:
          name: yawl_workitem_queue_depth   # YWorkflowMetrics gauge (Phase 6)
        target:
          type: AverageValue
          averageValue: "5000"             # scale-out at 5K queued items/pod
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70           # secondary: CPU safety ceiling
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 60
      policies:
        - type: Pods
          value: 3
          periodSeconds: 60               # add at most 3 pods/min to avoid thundering herd
    scaleDown:
      stabilizationWindowSeconds: 300     # wait 5 min before removing a pod
      policies:
        - type: Pods
          value: 1
          periodSeconds: 120
```

Key parameters:
- **minReplicas/maxReplicas**: Minimum 3 pods, maximum 20 pods
- **averageValue: 5000**: Scale when avg queue depth exceeds 5,000 items per pod
- **averageUtilization: 70**: Scale when CPU exceeds 70% (secondary metric)
- **stabilizationWindowSeconds**: Time HPA waits before scaling (prevents thrashing)
- **policies**: Scale rate limits (max 3 pods added per minute)

### 2. Understand the Queue Depth Metric

The `yawl_workitem_queue_depth` gauge shows how many work items are waiting across all engine pods:

```
Queue depth = Total enabled work items / Number of pods

Example:
- 10,000 items enabled
- 3 pods active
- Average queue depth per pod = 10,000 / 3 ≈ 3,333 items/pod

If averageValue is 5,000, HPA will NOT scale yet (3,333 < 5,000).
If queue grows to 15,000 items with 3 pods:
- Average depth = 15,000 / 3 = 5,000 items/pod
- HPA will trigger scale-out to 4-6 pods
```

### 3. Determine Your Load Profile

Before tuning, measure actual queue depths during peak and off-peak hours:

```bash
# Watch the queue depth metric for 24 hours
kubectl port-forward -n yawl svc/prometheus 9090:9090 &

# In your browser, query Prometheus:
# Metric: yawl_workitem_queue_depth
# Range: 24h
# Find: min, max, and avg values

# Or via CLI
kubectl exec -it prometheus-0 -n monitoring -- \
  promtool query instant \
  'yawl_workitem_queue_depth{job="yawl-engine"}'
```

Record:
- Peak queue depth (e.g., 15,000 items)
- Off-peak queue depth (e.g., 500 items)
- Average case processing time (e.g., 30 seconds)
- Acceptable latency per case (e.g., <5 min queue wait time)

### 4. Calculate Optimal averageValue Threshold

The `averageValue` is the target queue depth per pod. Choose based on:

```
Target queue depth per pod = Acceptable queue wait time × Cases per second per pod

Example calculation:
- Acceptable queue wait: 5 minutes (300 seconds)
- Peak throughput: 30 cases/second/pod
- Target: 300 sec × 30 cases/sec = 9,000 items per pod
```

Edit the HPA:

```bash
kubectl edit hpa yawl-engine-hpa -n yawl
```

Change `averageValue` based on your load:

```yaml
# Conservative (fast scale-out): 3,000 items/pod
# Moderate (balance): 5,000 items/pod (default)
# Aggressive (higher utilization, less scaling): 8,000 items/pod

target:
  type: AverageValue
  averageValue: "5000"  # Adjust this value
```

### 5. Tune Scale-Up Behavior

Adjust how quickly pods are added during traffic spikes:

```yaml
behavior:
  scaleUp:
    stabilizationWindowSeconds: 60     # Wait 60s after spike before scaling
    policies:
      - type: Pods
        value: 3                        # Add 3 pods per minute (default)
        periodSeconds: 60
```

For faster response to spikes:

```yaml
  scaleUp:
    stabilizationWindowSeconds: 30     # Reduce to 30s (faster reaction)
    policies:
      - type: Pods
        value: 5                        # Add 5 pods per minute (more aggressive)
        periodSeconds: 60
```

For slower, more gradual scaling (to avoid overprovisioning):

```yaml
  scaleUp:
    stabilizationWindowSeconds: 120    # Wait 2 minutes before scaling
    policies:
      - type: Pods
        value: 2                        # Add 2 pods per minute (slower)
        periodSeconds: 60
```

### 6. Tune Scale-Down Behavior

Adjust how quickly pods are removed when load drops:

```yaml
behavior:
  scaleDown:
    stabilizationWindowSeconds: 300    # Wait 5 minutes (default)
    policies:
      - type: Pods
        value: 1                        # Remove 1 pod per 2 minutes (default)
        periodSeconds: 120
```

For faster cost reduction (risk: flapping):

```yaml
  scaleDown:
    stabilizationWindowSeconds: 180    # Reduce to 3 minutes
    policies:
      - type: Pods
        value: 2                        # Remove 2 pods per minute
        periodSeconds: 60
```

For slower scale-down (conservative, prevents thrashing):

```yaml
  scaleDown:
    stabilizationWindowSeconds: 600    # Wait 10 minutes
    policies:
      - type: Pods
        value: 1                        # Remove 1 pod per 2 minutes
        periodSeconds: 120
```

### 7. Apply Changes and Monitor

Save the edited HPA:

```bash
kubectl apply -f k8s/base/hpa-engine.yaml
```

Watch scaling in real-time:

```bash
# Terminal 1: Monitor HPA status
kubectl get hpa yawl-engine-hpa -n yawl -w

# Terminal 2: Watch pods scale up/down
kubectl get pods -n yawl -l app.kubernetes.io/name=yawl-engine -w

# Terminal 3: Monitor metrics
kubectl port-forward -n monitoring svc/prometheus 9090:9090 &
# Then open browser to http://localhost:9090 and graph:
# - yawl_workitem_queue_depth
# - count(kube_pod_container_status_running{pod=~"yawl-engine-.*"})
```

### 8. Run a Load Test to Validate Scaling

Generate artificial load to test HPA behavior:

```bash
# Get a workflow spec
SPEC_ID=$(kubectl exec -it deployment/yawl-engine -n yawl -- \
  curl -s "http://localhost:8080/yawl/ib?action=listSpecifications" | \
  grep -o '"specID":"[^"]*' | head -1 | cut -d'"' -f4)

echo "Testing with spec: $SPEC_ID"

# Launch 1000 cases to spike the queue
for i in {1..1000}; do
  (
    kubectl exec -it deployment/yawl-engine -n yawl -- \
      curl -s -X POST \
      "http://localhost:8080/yawl/ib?action=launchCase&specID=$SPEC_ID&sessionHandle=..." \
      > /dev/null 2>&1
  ) &
  if [ $((i % 10)) -eq 0 ]; then
    echo "Launched $i cases..."
  fi
done
wait

echo "Load test complete. Monitoring scaling..."

# Watch HPA for 15 minutes
for i in {1..15}; do
  echo "=== $(date '+%H:%M:%S') ==="
  kubectl get hpa yawl-engine-hpa -n yawl
  kubectl get pods -n yawl -l app.kubernetes.io/name=yawl-engine | grep yawl-engine | wc -l
  sleep 60
done
```

## Verification

### 1. Check Current HPA Configuration

```bash
kubectl describe hpa yawl-engine-hpa -n yawl
```

Expected output:
```
Name:             yawl-engine-hpa
Namespace:        yawl
Metrics:
  Pods metric yawl_workitem_queue_depth:
    Target average value: 5000
  Resource cpu:
    Target average utilization: 70%
Min replicas:  3
Max replicas:  20
Behavior:
  Scale-up:
    Stabilization: 60s
    Max change: 3 pods per 60s
  Scale-down:
    Stabilization: 300s
    Max change: 1 pod per 120s
```

### 2. View Current and Desired Replica Counts

```bash
kubectl get hpa yawl-engine-hpa -n yawl -o wide
```

Expected columns:
```
NAME                   REFERENCE                    TARGETS       MINPODS MAXPODS REPLICAS DESIRED
yawl-engine-hpa        Deployment/yawl-engine       4500/5000     3       20      4        4
```

- `TARGETS`: Current average queue depth / target threshold
- `REPLICAS`: Currently running pods
- `DESIRED`: Target pod count (may be different if stabilization window active)

### 3. Query Historical Scaling Events

```bash
kubectl get events -n yawl --field-selector involvedObject.name=yawl-engine-hpa | tail -20
```

Expected output:
```
LAST SEEN   TYPE      REASON             MESSAGE
2m13s       Normal    SuccessfulRescale  the HPA scaled the target up to 4 replicas
5m42s       Normal    SuccessfulRescale  the HPA scaled the target down to 3 replicas
```

### 4. Verify Metrics Are Flowing

```bash
kubectl exec -it deployment/yawl-engine -n yawl -- \
  curl -s http://localhost:8080/engine/metrics | grep yawl_workitem_queue_depth
```

Expected output:
```
# HELP yawl_workitem_queue_depth Total work items waiting for processing
# TYPE yawl_workitem_queue_depth gauge
yawl_workitem_queue_depth 2314.0
```

If no output, metrics are not being collected. Check:

```bash
# Is metrics-server running?
kubectl get deployment metrics-server -n kube-system

# Are Prometheus scrape configs correct?
kubectl describe prometheus prometheus -n monitoring | grep job_name
```

## Troubleshooting

### HPA Not Scaling Even with High Queue Depth

The metrics may not be reaching the HPA controller. Check:

```bash
# 1. Are metrics being scraped?
kubectl top pods -n yawl | grep yawl-engine
# Should show CPU and memory

# 2. Is the custom metric registered?
kubectl get --all-namespaces=true custom.metrics.k8s.io
# Should include yawl_workitem_queue_depth

# 3. Check the HPA status
kubectl describe hpa yawl-engine-hpa -n yawl
# Look for "unknown" in TARGETS, which means metric is missing
```

If metrics are missing, ensure Prometheus adapter is installed:

```bash
kubectl apply -f k8s/prometheus-adapter/config.yaml
kubectl rollout status deployment prometheus-adapter -n monitoring
```

### HPA Flapping (Scaling Up and Down Repeatedly)

The stabilization window is too short or the threshold is at the edge of your load. Increase stabilization:

```yaml
behavior:
  scaleUp:
    stabilizationWindowSeconds: 120      # Increase from 60
  scaleDown:
    stabilizationWindowSeconds: 600      # Increase from 300
```

Or adjust the threshold to provide a larger buffer:

```yaml
metrics:
  - type: Pods
    pods:
      metric:
        name: yawl_workitem_queue_depth
      target:
        type: AverageValue
        averageValue: "6000"             # Increase from 5000 (more buffer)
```

### Pods Lingering at Max Replicas

HPA hit the `maxReplicas: 20` limit. Either increase max or optimize pod performance:

```bash
# Option 1: Increase max replicas
kubectl patch hpa yawl-engine-hpa -n yawl -p '{"spec":{"maxReplicas":30}}'

# Option 2: Optimize pod performance (reduce queue depth)
# - Increase pod CPU/memory requests
# - Add caching
# - Optimize database queries
```

### CPU Metric Secondary Limit Being Triggered

The engine is hitting 70% CPU before queue depth reaches 5,000. Adjust the CPU target:

```yaml
- type: Resource
  resource:
    name: cpu
    target:
      type: Utilization
      averageUtilization: 80            # Increase from 70 (allow higher utilization)
```

Or reduce the queue depth threshold to scale out sooner:

```yaml
- type: Pods
  pods:
    metric:
      name: yawl_workitem_queue_depth
    target:
      type: AverageValue
      averageValue: "3000"               # Decrease from 5000 (scale sooner)
```

### Scale-Up Too Slow During Spike

Reduce stabilization and increase pod addition rate:

```yaml
behavior:
  scaleUp:
    stabilizationWindowSeconds: 30       # Reduce from 60
    policies:
      - type: Pods
        value: 10                        # Increase from 3
        periodSeconds: 60
```

Warning: This may overprovision during transient spikes. Test carefully.

### Scale-Down Never Happens (Pods Stay Running)

The HPA thinks load is still high. Check:

```bash
# Is queue depth actually dropping?
kubectl exec -it deployment/yawl-engine -n yawl -- \
  curl -s http://localhost:8080/engine/metrics | grep yawl_workitem_queue_depth

# If queue depth is still high, load is genuinely high.
# If queue depth is low but pods aren't scaling down, check for:
# 1. Stabilization window still active (wait 5 min)
# 2. CPU metric still above threshold
# 3. Manual pod scaling (kubectl scale deployment ...)

# Check HPA conditions
kubectl describe hpa yawl-engine-hpa -n yawl | grep -A5 Conditions
```

### Out-of-Memory Errors When Scaling Up

Pod memory requests are too small. Increase:

```bash
kubectl set resources deployment yawl-engine -n yawl \
  --limits=memory=12Gi \
  --requests=memory=8Gi
```

Then adjust minReplicas down if necessary to avoid cost overrun:

```yaml
minReplicas: 2  # Reduce from 3 if constraints are tight
```
