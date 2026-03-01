# YAWL 1M Agent - Kubernetes Deployment Guide

**Status**: PRODUCTION READY ✅
**Version**: 6.0.0
**Date**: 2026-02-28

---

## Table of Contents

1. [Pre-Deployment Checklist](#pre-deployment-checklist)
2. [Environment Setup](#environment-setup)
3. [Secrets & ConfigMaps](#secrets--configmaps)
4. [Deployment Steps](#deployment-steps)
5. [Verification](#verification)
6. [Troubleshooting](#troubleshooting)
7. [Post-Deployment Validation](#post-deployment-validation)

---

## Pre-Deployment Checklist

### Infrastructure Requirements

- [ ] Kubernetes cluster version 1.24+
- [ ] At least 3 worker nodes available
- [ ] Minimum cluster capacity: 24 CPU, 18 GB memory (for 3 pods)
- [ ] Network policies support enabled
- [ ] RBAC enabled

### Software Requirements

- [ ] `kubectl` installed (v1.24+)
- [ ] NGINX Ingress Controller deployed
- [ ] cert-manager installed (for TLS)
- [ ] OpenTelemetry Collector running (optional but recommended)
- [ ] Prometheus & Grafana for monitoring (optional)

### Pre-Deployment Decisions

Confirm before proceeding:

```bash
# Q1: What is your Kubernetes cluster name?
CLUSTER_NAME="prod-yawl-us-west-2"

# Q2: What is your domain for the 1m-agent ingress?
DOMAIN="1m-agent.yawl.example.com"

# Q3: What is the YAWL engine endpoint?
YAWL_ENGINE_URL="http://yawl-engine:8080/api"

# Q4: Do you have YAWL credentials?
YAWL_USERNAME="agent-service"
YAWL_PASSWORD="<secure-password>"

# Q5: What OpenTelemetry endpoint?
OTEL_ENDPOINT="http://otel-collector:4317"
```

---

## Environment Setup

### Step 1: Verify Cluster Access

```bash
# Test kubectl access
kubectl cluster-info
kubectl get nodes -o wide

# Expected output: 3+ nodes in Ready state
# Example:
# NAME          STATUS   ROLES           AGE   VERSION
# worker-1      Ready    <none>          30d   v1.28.0
# worker-2      Ready    <none>          30d   v1.28.0
# worker-3      Ready    <none>          30d   v1.28.0
```

### Step 2: Check Prerequisites

```bash
# Check NGINX Ingress Controller
kubectl get deployments -n ingress-nginx ingress-nginx-controller

# Check cert-manager
kubectl get deployments -n cert-manager cert-manager

# Check OpenTelemetry Collector (if deployed)
kubectl get statefulset -n observability otel-collector

# All should show 1+ ready replicas
```

### Step 3: Verify RBAC is Enabled

```bash
# This should return valid output if RBAC is enabled
kubectl auth can-i get pods --as=system:serviceaccount:default:default

# Expected: yes (if authorized) or no (if denied)
# Both responses mean RBAC is working
```

### Step 4: Check Available Resources

```bash
# View node resources
kubectl top nodes

# Expected output:
# NAME          CPU(cores)   CPU%   MEMORY(Mi)   MEMORY%
# worker-1      2000m        10%    8000Mi       20%
# worker-2      2000m        10%    8000Mi       20%
# worker-3      2000m        10%    8000Mi       20%

# Ensure sufficient capacity (3 pods × 8 CPU, 6 GB each)
```

---

## Secrets & ConfigMaps

### Step 1: Create Namespace

```bash
# Create namespace
kubectl create namespace yawl

# Label namespace for network policies
kubectl label namespace yawl name=yawl

# Verify
kubectl get namespace yawl
```

### Step 2: Create YAWL Credentials Secret

```bash
# Create secret with YAWL engine credentials
kubectl create secret generic yawl-credentials \
  --from-literal=username='agent-service' \
  --from-literal=password='<your-secure-password>' \
  -n yawl

# Verify (you should see the secret, but not the data)
kubectl get secret yawl-credentials -n yawl
kubectl describe secret yawl-credentials -n yawl
```

### Step 3: Create YAWL Engine ConfigMap

```bash
# Create configmap with YAWL engine connection details
kubectl create configmap yawl-engine-config \
  --from-literal=YAWL_ENGINE_URL='http://yawl-engine:8080/api' \
  --from-literal=OTEL_EXPORTER_OTLP_ENDPOINT='http://otel-collector:4317' \
  -n yawl

# Verify
kubectl get configmap yawl-engine-config -n yawl
kubectl describe configmap yawl-engine-config -n yawl
```

### Step 4: Verify Secrets and ConfigMaps

```bash
# List all secrets and configmaps
kubectl get secrets,configmaps -n yawl

# Expected output:
# NAME                             TYPE     DATA   AGE
# secret/yawl-credentials          Opaque   2      10s
# configmap/yawl-engine-config     3        5s
```

---

## Deployment Steps

### Step 1: Apply RBAC Resources (First)

RBAC must be applied first to ensure the ServiceAccount exists before the Deployment references it.

```bash
# Apply RBAC: ServiceAccount, ClusterRole, ClusterRoleBinding, Role, RoleBinding
kubectl apply -f k8s/1m-agent-rbac.yaml

# Verify
kubectl get serviceaccount,clusterrole,role -l app.kubernetes.io/name=1m-agent

# Expected output: 1 ServiceAccount, 1 ClusterRole, 1 ClusterRoleBinding, 1 Role, 1 RoleBinding
```

### Step 2: Apply ConfigMap for 1M Agent

```bash
# Apply 1m-agent specific configuration
kubectl apply -f k8s/1m-agent-configmap.yaml

# Verify
kubectl get configmap 1m-agent-config -n yawl -o yaml | head -20

# Should show 78 configuration keys
```

### Step 3: Deploy Application

```bash
# Deploy the 1m-agent Deployment
kubectl apply -f k8s/1m-agent-deployment.yaml

# Watch rollout progress (CTRL+C to stop)
kubectl rollout status deployment/1m-agent -n yawl --timeout=5m

# Expected output:
# deployment "1m-agent" successfully rolled out
```

### Step 4: Apply Scaling Rules

```bash
# Apply HorizontalPodAutoscaler
kubectl apply -f k8s/1m-agent-hpa.yaml

# Verify HPA
kubectl get hpa -n yawl

# Expected output:
# NAME              REFERENCE              TARGETS              MINPODS   MAXPODS   REPLICAS   AGE
# 1m-agent-hpa      Deployment/1m-agent    <unknown>/70%, ...   3         10        3          5s
```

### Step 5: Apply Availability Policy

```bash
# Apply PodDisruptionBudget
kubectl apply -f k8s/1m-agent-pdb.yaml

# Verify PDB
kubectl get pdb -n yawl

# Expected output:
# NAME            MIN AVAILABLE   ALLOWED DISRUPTIONS   AGE
# 1m-agent-pdb    2               1                     5s
```

### Step 6: Apply Networking Rules

```bash
# Apply NetworkPolicies
kubectl apply -f k8s/1m-agent-networkpolicy.yaml

# Verify
kubectl get networkpolicies -n yawl -l app.kubernetes.io/name=1m-agent

# Expected output: 2 policies
```

### Step 7: Apply Service and Ingress

```bash
# Apply Service and Ingress
kubectl apply -f k8s/1m-agent-ingress.yaml

# Verify Service
kubectl get svc -n yawl -l app.kubernetes.io/name=1m-agent

# Verify Ingress
kubectl get ingress -n yawl -l app.kubernetes.io/name=1m-agent

# Expected output:
# Service: 1m-agent, ClusterIP, port 8080
# Ingress: 1m-agent-ingress, 1m-agent.yawl.example.com
```

### Complete Deployment (One Command)

Alternatively, deploy everything at once:

```bash
# Apply all manifests in correct order
kubectl apply -f k8s/1m-agent-rbac.yaml && \
kubectl apply -f k8s/1m-agent-configmap.yaml && \
kubectl apply -f k8s/1m-agent-deployment.yaml && \
kubectl apply -f k8s/1m-agent-hpa.yaml && \
kubectl apply -f k8s/1m-agent-pdb.yaml && \
kubectl apply -f k8s/1m-agent-networkpolicy.yaml && \
kubectl apply -f k8s/1m-agent-ingress.yaml

# Wait for rollout
kubectl rollout status deployment/1m-agent -n yawl --timeout=5m
```

---

## Verification

### Step 1: Check Pod Status

```bash
# List all pods
kubectl get pods -n yawl -l app.kubernetes.io/name=1m-agent

# Expected output: 3 pods in Running state with 1/1 Ready
# Example:
# NAME                        READY   STATUS    RESTARTS   AGE
# 1m-agent-5b9c8d6f4b-abc12   1/1     Running   0          1m
# 1m-agent-5b9c8d6f4b-def34   1/1     Running   0          1m
# 1m-agent-5b9c8d6f4b-ghi56   1/1     Running   0          1m
```

### Step 2: Check Detailed Pod Status

```bash
# Get detailed pod information
kubectl describe pod <pod-name> -n yawl

# Look for:
# - Status: Running
# - Conditions: PodScheduled=True, Ready=True, ContainersReady=True
# - Events: No error events (warnings are OK)
```

### Step 3: Check HPA Status

```bash
# Get HPA metrics
kubectl get hpa -n yawl -w

# Expected output:
# NAME              REFERENCE              TARGETS              MINPODS   MAXPODS   REPLICAS
# 1m-agent-hpa      Deployment/1m-agent    2%/70%, 10%/75%      3         10        3

# Press CTRL+C to stop watching
```

### Step 4: Test Health Endpoints

```bash
# Port-forward to a pod
kubectl port-forward -n yawl svc/1m-agent 8080:8080 &

# Wait for connection
sleep 2

# Test health endpoints
echo "Readiness probe:"
curl -s http://localhost:8080/actuator/health/readiness | jq .

echo "Liveness probe:"
curl -s http://localhost:8080/actuator/health/liveness | jq .

echo "Full health:"
curl -s http://localhost:8080/actuator/health | jq .

# Stop port-forward
pkill -f "port-forward.*8080"
```

### Step 5: Check Resource Usage

```bash
# Check CPU and memory usage
kubectl top pods -n yawl -l app.kubernetes.io/name=1m-agent

# Expected output (initial state):
# NAME                        CPU(m)   MEMORY(Mi)
# 1m-agent-5b9c8d6f4b-abc12   50m      400Mi
# 1m-agent-5b9c8d6f4b-def34   50m      400Mi
# 1m-agent-5b9c8d6f4b-ghi56   50m      400Mi

# CPU should be <8000m (8 cores), memory <6144 Mi (6 GB)
```

### Step 6: Verify Networking

```bash
# Check Service endpoints
kubectl get endpoints 1m-agent -n yawl

# Should show 3 IP addresses (one per pod)

# Check Ingress assignment
kubectl get ingress 1m-agent-ingress -n yawl

# Should show an external IP or CNAME
```

### Step 7: Run Verification Script

```bash
# Run comprehensive verification script
bash scripts/verify-1m-agent-deployment.sh

# Should report all green checks (✓)
```

---

## Troubleshooting

### Issue: Pods Stuck in Pending

**Symptoms**: `kubectl get pods` shows pods in "Pending" state

**Diagnosis**:
```bash
# Describe the pod to see events
kubectl describe pod <pod-name> -n yawl

# Look for messages like:
# - "0/3 nodes are available: 3 Insufficient cpu"
# - "0/3 nodes are available: 3 Insufficient memory"
```

**Solutions**:

1. **Insufficient Resources**:
   ```bash
   # Check node capacity
   kubectl describe nodes | grep -A5 "Allocated resources"

   # Either:
   # a) Scale down other workloads
   # b) Add more worker nodes
   # c) Reduce 1m-agent replicas (temporary)
   kubectl scale deployment 1m-agent --replicas=2 -n yawl
   ```

2. **PDB Preventing Scheduling**:
   ```bash
   # Check PDB status
   kubectl get pdb -n yawl

   # If PDB is blocking, temporarily remove it
   kubectl delete pdb 1m-agent-pdb -n yawl
   # Recreate after pods are scheduled
   kubectl apply -f k8s/1m-agent-pdb.yaml
   ```

3. **Network Policy Blocking**:
   ```bash
   # Check if NetworkPolicy is preventing pod scheduling
   kubectl get networkpolicies -n yawl

   # Temporarily disable to test
   kubectl delete networkpolicy 1m-agent -n yawl
   kubectl apply -f k8s/1m-agent-networkpolicy.yaml
   ```

### Issue: Pods Crash on Startup

**Symptoms**: Pods restart frequently (RESTARTS > 0)

**Diagnosis**:
```bash
# Check pod logs
kubectl logs <pod-name> -n yawl --previous

# Check events
kubectl describe pod <pod-name> -n yawl | tail -20

# Look for:
# - ClassNotFoundException (missing dependency)
# - OutOfMemoryError (increase limits)
# - Connection refused (YAWL engine not accessible)
```

**Solutions**:

1. **Memory Issues**:
   ```bash
   # Check heap usage
   kubectl exec <pod-name> -n yawl -- jcmd 1 VM.heap_info

   # Increase memory limits in deployment
   kubectl set resources deployment 1m-agent \
     --limits=memory=16Gi,cpu=20 \
     -n yawl
   ```

2. **Configuration Issues**:
   ```bash
   # Check environment variables
   kubectl exec <pod-name> -n yawl -- env | grep YAWL

   # Verify secrets and configmaps
   kubectl get secrets,configmaps -n yawl

   # Recreate if needed
   kubectl delete secret yawl-credentials -n yawl
   kubectl create secret generic yawl-credentials \
     --from-literal=username='...' \
     --from-literal=password='...' \
     -n yawl
   ```

### Issue: Health Probes Failing

**Symptoms**: Pods showing "NotReady" or "CrashLoopBackOff"

**Diagnosis**:
```bash
# Check probe configuration
kubectl get deployment 1m-agent -n yawl -o yaml | grep -A10 "livenessProbe\|readinessProbe"

# Test health endpoint directly
kubectl exec <pod-name> -n yawl -- curl -s http://localhost:8080/actuator/health

# Check application logs
kubectl logs <pod-name> -n yawl | tail -50
```

**Solutions**:

1. **Startup Takes Too Long**:
   ```bash
   # Increase startup probe timeout
   kubectl patch deployment 1m-agent -n yawl --type json \
     -p='[{"op":"replace","path":"/spec/template/spec/containers/0/startupProbe/failureThreshold","value":60}]'

   # This allows 5 minutes (60 × 5s) for startup
   ```

2. **Health Endpoint Not Ready**:
   ```bash
   # Check if application is fully initialized
   kubectl logs <pod-name> -n yawl | grep -i "started\|ready"

   # Wait for logs to show "Application is running"
   ```

### Issue: Network Connectivity Problems

**Symptoms**: Pods can't reach YAWL engine or external services

**Diagnosis**:
```bash
# Test DNS resolution
kubectl exec <pod-name> -n yawl -- nslookup yawl-engine

# Test connectivity to YAWL engine
kubectl exec <pod-name> -n yawl -- curl -v http://yawl-engine:8080/api

# Check network policies
kubectl get networkpolicies -n yawl -o yaml
```

**Solutions**:

1. **Network Policy Too Restrictive**:
   ```bash
   # Review egress rules
   kubectl describe networkpolicy 1m-agent -n yawl

   # Add missing destination if needed
   kubectl edit networkpolicy 1m-agent -n yawl
   ```

2. **Service Discovery Issue**:
   ```bash
   # Check if yawl-engine service exists
   kubectl get svc yawl-engine -n yawl

   # Update environment variable if service name is different
   kubectl set env deployment/1m-agent \
     YAWL_ENGINE_URL='http://yawl-engine-service:8080/api' \
     -n yawl
   ```

### Issue: Ingress Not Working

**Symptoms**: External access to 1m-agent failing, Ingress shows no external IP

**Diagnosis**:
```bash
# Check Ingress status
kubectl describe ingress 1m-agent-ingress -n yawl

# Check NGINX controller logs
kubectl logs -n ingress-nginx deployment/ingress-nginx-controller | tail -20

# Check cert-manager certificates
kubectl get certificates -n yawl
```

**Solutions**:

1. **NGINX Controller Not Ready**:
   ```bash
   # Verify NGINX ingress controller is running
   kubectl get deployment -n ingress-nginx ingress-nginx-controller

   # If not ready, check pod logs
   kubectl logs -n ingress-nginx deployment/ingress-nginx-controller
   ```

2. **TLS Certificate Issues**:
   ```bash
   # Check certificate status
   kubectl describe certificate -n yawl 1m-agent-tls-secret

   # If cert is pending, check cert-manager logs
   kubectl logs -n cert-manager deployment/cert-manager
   ```

3. **DNS Not Resolving**:
   ```bash
   # Verify DNS records are set up
   # Contact your DNS provider or network team

   # Alternatively, test with IP address
   INGRESS_IP=$(kubectl get ingress 1m-agent-ingress -n yawl -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
   curl -H "Host: 1m-agent.yawl.example.com" https://$INGRESS_IP/api
   ```

---

## Post-Deployment Validation

### Step 1: Generate Load (Optional)

```bash
# Create a simple load test
kubectl exec -it <pod-name> -n yawl -- bash

# Inside the pod:
for i in {1..100}; do
  curl -s http://localhost:8080/api/health > /dev/null
  echo "Request $i completed"
done
```

### Step 2: Monitor Scaling Behavior

```bash
# Watch HPA scaling
kubectl get hpa -n yawl -w

# Monitor as load increases
kubectl top pods -n yawl -l app.kubernetes.io/name=1m-agent --use-protocol-buffers

# After load stops, watch scale-down (takes 5 minutes)
```

### Step 3: Verify Logging and Metrics

```bash
# Check if logs are being collected
kubectl logs -n yawl -l app.kubernetes.io/name=1m-agent --all-containers --tail=50

# Check if metrics are being exported
kubectl port-forward -n yawl svc/1m-agent 8080:8080 &
curl http://localhost:8080/actuator/prometheus | head -20
pkill -f "port-forward.*8080"
```

### Step 4: Run Full Validation Suite

```bash
# Run all verification checks
bash scripts/verify-1m-agent-deployment.sh

# All checks should be green (✓)
```

### Step 5: Document Environment

```bash
# Create deployment summary
kubectl get all -n yawl -l app.kubernetes.io/name=1m-agent -o wide > /tmp/1m-agent-deployment.txt

# Save cluster information
kubectl cluster-info > /tmp/cluster-info.txt
kubectl version > /tmp/kubernetes-version.txt

# Archive for records
tar czf /tmp/yawl-deployment-$(date +%Y%m%d).tar.gz /tmp/cluster-info.txt /tmp/kubernetes-version.txt /tmp/1m-agent-deployment.txt
```

---

## Rollback Procedures

### Quick Rollback (< 1 minute)

```bash
# Revert Deployment to previous revision
kubectl rollout undo deployment/1m-agent -n yawl

# Wait for rollout
kubectl rollout status deployment/1m-agent -n yawl --timeout=5m
```

### Full Rollback (Keep current state)

```bash
# Delete only the Deployment, keep everything else
kubectl delete deployment 1m-agent -n yawl

# Reapply previous version
kubectl apply -f k8s/1m-agent-deployment-v5.0.0.yaml

# Verify
kubectl rollout status deployment/1m-agent -n yawl --timeout=5m
```

### Complete Cleanup

```bash
# Delete all 1m-agent resources
kubectl delete all -n yawl -l app.kubernetes.io/name=1m-agent

# Verify deletion
kubectl get all -n yawl -l app.kubernetes.io/name=1m-agent

# Note: This does NOT delete secrets, configmaps, or RBAC resources
```

---

## Support & Escalation

If deployment issues persist:

1. **Collect Diagnostics**:
   ```bash
   kubectl describe all -n yawl -l app.kubernetes.io/name=1m-agent > /tmp/diagnostics.txt
   kubectl logs -n yawl -l app.kubernetes.io/name=1m-agent --all-containers > /tmp/logs.txt
   kubectl get events -n yawl | sort -k5 -r > /tmp/events.txt
   ```

2. **Contact Infrastructure Team**:
   - Provide: diagnostics.txt, logs.txt, events.txt
   - Include: cluster name, namespace, time of issue

3. **Escalation**:
   - Platform Team: Network, storage, cluster issues
   - YAWL Team: Application behavior, performance
   - Security Team: RBAC, network policy issues

---

**Document Version**: 1.0
**Last Updated**: 2026-02-28
**Status**: PRODUCTION READY ✅
