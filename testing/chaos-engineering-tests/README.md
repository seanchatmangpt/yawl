# YAWL Chaos Engineering Tests

Chaos Toolkit experiments for testing YAWL Workflow Engine resilience under failure conditions.

## Overview

This test suite validates YAWL's ability to:

- Handle infrastructure failures gracefully
- Recover from service disruptions
- Maintain data consistency under adverse conditions
- Scale and degrade performance appropriately
- Minimize recovery time objectives (RTO)
- Maintain recovery point objectives (RPO)

## Installation

### Prerequisites

- Python 3.7+
- Docker and Docker Compose (for Docker experiments)
- Kubernetes cluster (for K8s experiments)
- kubectl (for K8s experiments)

### Setup

```bash
# Install Chaos Toolkit
pip install chaostoolkit chaostoolkit-kubernetes chaostoolkit-http

# Optional: Install additional extensions
pip install chaostoolkit-docker
pip install chaostoolkit-aws
pip install chaostoolkit-azure

# Verify installation
chaos --version
```

## Configuration

Create `settings.yaml` for Chaos Toolkit:

```yaml
transports:
  http:
    timeout: 30
    max_retries: 3

kubernetes:
  context: "minikube"
  namespace: "yawl"

logging:
  level: INFO
  format: json
```

## Running Experiments

### Run All Experiments

```bash
chaos run experiments.yaml
```

### Run Specific Experiment

```bash
chaos run experiments.yaml --filter pod-restart
```

### Dry Run (no execution)

```bash
chaos run experiments.yaml --dry
```

### With Custom Settings

```bash
chaos run experiments.yaml --settings settings.yaml
```

## Experiments

### Kubernetes Experiments

#### 1. Pod Restart (`pod-restart`)

**Description**: Test pod restart handling

**Steps**:
1. Kill a YAWL pod with 0 grace period
2. Verify pod is restarted
3. Confirm service availability

**Expected**: Pod restarts and service remains available

**Recovery Time**: < 30 seconds

```bash
chaos run experiments.yaml --filter pod-restart
```

#### 2. Node Drain (`node-drain`)

**Description**: Test handling of node drain

**Steps**:
1. Drain a Kubernetes node (evict pods)
2. Verify pods are rescheduled
3. Uncordon the node

**Expected**: Pods reschedule to other nodes

**Recovery Time**: < 2 minutes

```bash
chaos run experiments.yaml --filter node-drain
```

#### 3. Resource Exhaustion (`resource-exhaustion`)

**Description**: Test handling when resources are exhausted

**Steps**:
1. Create stress pod consuming resources
2. Monitor service availability
3. Verify graceful degradation
4. Clean up stress pod

**Expected**: Service remains responsive despite resource pressure

```bash
chaos run experiments.yaml --filter resource-exhaustion
```

#### 4. Network Partition (`network-partition`)

**Description**: Test handling of network partitions

**Steps**:
1. Create restrictive network policy
2. Verify pod-to-pod connectivity failures
3. Remove network policy

**Expected**: System gracefully handles isolation

```bash
chaos run experiments.yaml --filter network-partition
```

### Database Experiments

#### 5. Database Unavailable (`database-unavailable`)

**Description**: Test service behavior when database is unavailable

**Steps**:
1. Scale down PostgreSQL deployment
2. Monitor service degradation
3. Scale up PostgreSQL
4. Verify recovery

**Expected**: Service recovers when database is restored

**Recovery Time**: < 2 minutes

```bash
chaos run experiments.yaml --filter database-unavailable
```

#### 6. Database Latency (`database-latency`)

**Description**: Test handling of high database query latency

**Steps**:
1. Inject network latency to database
2. Monitor API response times
3. Remove latency injection

**Expected**: Service handles latency without cascading failures

```bash
chaos run experiments.yaml --filter database-latency
```

### Service Experiments

#### 7. Service Degradation (`service-degradation`)

**Description**: Test graceful degradation under load

**Steps**:
1. Generate high load on service
2. Monitor error rates
3. Verify service maintains responsiveness

**Expected**: Error rate remains < 1% under heavy load

```bash
chaos run experiments.yaml --filter service-degradation
```

#### 8. Cascading Failure (`cascading-failure`)

**Description**: Test handling of cascading failures

**Steps**:
1. Kill first pod
2. Kill second pod
3. Verify minimum availability is maintained

**Expected**: At least 1 pod remains ready

```bash
chaos run experiments.yaml --filter cascading-failure
```

### Resource Experiments

#### 9. Memory Leak Detection (`memory-leak-detection`)

**Description**: Monitor for memory leaks during sustained operation

**Steps**:
1. Capture baseline memory usage
2. Generate continuous requests
3. Monitor memory growth over time

**Expected**: Memory growth is linear and bounded

```bash
chaos run experiments.yaml --filter memory-leak-detection
```

### Docker Compose Experiments

#### 10. Container Crash Recovery (`docker-compose-container-crash`)

**Description**: Test container auto-restart on crash

**Steps**:
1. Force crash a container
2. Verify auto-restart
3. Monitor recovery

**Expected**: Container restarts automatically

```bash
chaos run experiments.yaml --filter docker-compose-container-crash
```

### Disk Space Experiments

#### 11. Disk Space Exhaustion (`disk-space-exhaustion`)

**Description**: Test handling of disk space exhaustion

**Steps**:
1. Fill available disk space
2. Monitor system behavior
3. Clean up disk space

**Expected**: System provides meaningful error messages

```bash
chaos run experiments.yaml --filter disk-space-exhaustion
```

### Recovery Validation

#### 12. Recovery Time Objective (`recovery-time-objective`)

**Description**: Validate RTO meets requirements

**Steps**:
1. Establish baseline state
2. Trigger simulated failure
3. Measure recovery time to full availability

**Target RTO**: < 5 minutes

```bash
chaos run experiments.yaml --filter recovery-time-objective
```

## Experiment Selectors

Use selectors to run groups of experiments:

```bash
# Run all Kubernetes experiments
chaos run experiments.yaml -k "provider.type == 'kubernetes'"

# Run all database experiments
chaos run experiments.yaml -k "name contains 'database'"

# Run fast experiments (exclude slow ones)
chaos run experiments.yaml -k "duration < 300"
```

## Probes

### HTTP Probes

Monitor service availability:

```yaml
- type: probe
  name: "service-availability"
  provider:
    type: "http"
    method: "GET"
    url: "http://yawl-service:8080/resourceService/"
    expected_status: 200
```

### Kubernetes Probes

Verify cluster state:

```yaml
- type: probe
  name: "pods-ready"
  provider:
    type: "kubernetes"
    probe: "all_pods_ready"
    arguments:
      namespace: "yawl"
      selector: "app=yawl"
```

### Shell Probes

Execute custom commands:

```yaml
- type: probe
  name: "custom-check"
  provider:
    type: "shell"
    command: "curl -s http://localhost:8080/health"
```

## Rollbacks

Automatic rollback procedures:

```yaml
rollback:
  strategy: "always"  # always, on_failure, never
  steps:
    - type: shell
      command: "docker-compose -f docker-compose.yml up -d"
```

## Reporting

### Generate HTML Report

```bash
chaos run experiments.yaml --export-html-report report.html
```

### JSON Report

```bash
chaos run experiments.yaml --export-json-report report.json
```

### View Report

Reports are saved to `/tmp/yawl-chaos-report.*`

```bash
# HTML report
open /tmp/yawl-chaos-report.html

# JSON report
cat /tmp/yawl-chaos-report.json
```

## Metrics and Observability

### Key Metrics to Monitor

- **Pod restart count**: Should not increase indefinitely
- **Service availability**: Should remain ≥ 99%
- **API response time**: Should stay < 2s
- **Error rate**: Should stay < 1%
- **Database connection pool**: Should remain healthy
- **Memory usage**: Should remain stable

### Integration with Prometheus

Add Prometheus endpoint to experiments:

```yaml
- type: probe
  name: "prometheus-query"
  provider:
    type: "http"
    method: "GET"
    url: "http://prometheus:9090/api/v1/query?query=up{job='yawl'}"
    expected_status: 200
```

## Best Practices

1. **Start small**: Run experiments one at a time first
2. **Monitor closely**: Watch system behavior during experiments
3. **Have rollback plans**: Ensure all failures trigger rollbacks
4. **Document findings**: Record what you learn from each experiment
5. **Automate regularly**: Run experiments in CI/CD pipeline
6. **Iterate**: Refine experiments based on results

## Continuous Integration

### GitHub Actions

```yaml
name: Chaos Engineering

on: [pull_request]

jobs:
  chaos:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - uses: actions/setup-python@v2
      - run: pip install chaostoolkit chaostoolkit-kubernetes
      - run: chaos run testing/chaos-engineering-tests/experiments.yaml
      - uses: actions/upload-artifact@v2
        with:
          name: chaos-report
          path: /tmp/yawl-chaos-report.html
```

## Troubleshooting

### Experiment Fails to Connect

```bash
# Check connectivity
kubectl cluster-info
kubectl get pods -n yawl

# Verify network policies don't block experiments
kubectl get networkpolicies -n yawl
```

### Probe Timeouts

Increase timeout in experiment:

```yaml
steps:
  - type: probe
    name: "slow-probe"
    timeout: 120  # seconds
```

### Pod Not Restarting

Check pod restart policy:

```bash
kubectl get deployment yawl -n yawl -o jsonpath='{.spec.template.spec.restartPolicy}'
```

## Examples

### Custom Experiment

Create `my-experiment.yaml`:

```yaml
version: 1.0.0
title: Custom YAWL Experiment
description: My custom chaos experiment

experiments:
  - name: custom-test
    description: Test custom scenario
    steps:
      - type: action
        name: custom-action
        provider:
          type: shell
          command: "echo 'Testing'"

      - type: probe
        name: custom-probe
        provider:
          type: http
          method: GET
          url: "http://localhost:8080/resourceService/"
```

Run it:

```bash
chaos run my-experiment.yaml
```

## References

- [Chaos Toolkit Documentation](https://docs.chaostoolkit.org/)
- [Chaos Toolkit Kubernetes](https://github.com/chaostoolkit-incubator/chaostoolkit-kubernetes)
- [Chaos Toolkit HTTP](https://github.com/chaostoolkit-incubator/chaostoolkit-http)
- [Principles of Chaos Engineering](https://principlesofchaos.org/)

## Support

For issues or questions:

1. Check existing experiments for examples
2. Review Chaos Toolkit documentation
3. Consult YAWL Foundation support
