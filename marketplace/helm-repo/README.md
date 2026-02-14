# YAWL Helm Chart

Official Helm Chart for deploying YAWL (Yet Another Workflow Language) on Kubernetes clusters.

## Overview

This Helm Chart provides a production-ready deployment for YAWL with support for:

- Multiple cloud providers (GCP, AWS, Azure)
- High availability and auto-scaling
- Monitoring and observability
- Security best practices
- Persistent data storage
- Database and cache management

## Prerequisites

- Kubernetes 1.24+
- Helm 3.10+
- 2GB+ memory per pod
- Persistent Volume support

## Installation

### Add Helm Repository

```bash
helm repo add yawl https://helm.yawl.org
helm repo update
```

### Quick Start

```bash
# Install with default values
helm install yawl yawl/yawl --namespace yawl --create-namespace

# Install with custom domain
helm install yawl yawl/yawl \
  --namespace yawl \
  --create-namespace \
  --set ingress.hosts[0].host=yawl.example.com \
  --set postgresql.auth.password=changeme123!
```

## Configuration

### Basic Configuration

```bash
helm install yawl yawl/yawl \
  --set replicaCount=3 \
  --set image.tag=1.0.0 \
  --set postgresql.auth.password=<secure-password> \
  --set redis.auth.password=<secure-password> \
  --set ingress.hosts[0].host=yawl.example.com
```

### Cloud-Specific Installation

#### Google Cloud Platform (GCP)

```bash
helm install yawl yawl/yawl \
  --values values-gcp.yaml \
  --set global.gcp.project=my-gcp-project \
  --set global.gcp.region=us-central1
```

#### Amazon Web Services (AWS)

```bash
helm install yawl yawl/yawl \
  --values values-aws.yaml \
  --set global.aws.region=us-east-1 \
  --set global.aws.accountId=123456789012
```

#### Microsoft Azure

```bash
helm install yawl yawl/yawl \
  --values values-azure.yaml \
  --set global.azure.resourceGroup=yawl-rg \
  --set global.azure.region=eastus
```

## Key Configuration Options

### Application

| Parameter | Default | Description |
|-----------|---------|-------------|
| `replicaCount` | `3` | Number of YAWL pod replicas |
| `image.repository` | `yawl/yawl` | Docker image repository |
| `image.tag` | `1.0.0` | Docker image version |
| `yawl.logLevel` | `INFO` | Application log level |
| `yawl.enableMonitoring` | `true` | Enable Prometheus monitoring |

### Database

| Parameter | Default | Description |
|-----------|---------|-------------|
| `postgresql.enabled` | `true` | Use PostgreSQL subchart |
| `postgresql.auth.database` | `yawldb` | Database name |
| `postgresql.auth.username` | `yawladmin` | Database user |
| `postgresql.auth.password` | (required) | Database password |
| `postgresql.primary.persistence.size` | `10Gi` | Database storage size |

### Cache

| Parameter | Default | Description |
|-----------|---------|-------------|
| `redis.enabled` | `true` | Use Redis subchart |
| `redis.auth.password` | (required) | Redis password |
| `redis.master.persistence.size` | `2Gi` | Cache storage size |

### Ingress

| Parameter | Default | Description |
|-----------|---------|-------------|
| `ingress.enabled` | `true` | Enable ingress |
| `ingress.className` | `nginx` | Ingress class |
| `ingress.hosts[0].host` | `yawl.example.com` | Application hostname |

### Monitoring

| Parameter | Default | Description |
|-----------|---------|-------------|
| `monitoring.enabled` | `true` | Enable monitoring |
| `monitoring.serviceMonitor.enabled` | `true` | Enable Prometheus ServiceMonitor |

## Using External Services

To use external PostgreSQL or Redis instead of the Helm subchart:

```bash
helm install yawl yawl/yawl \
  --set postgresql.enabled=false \
  --set externalPostgresql.enabled=true \
  --set externalPostgresql.host=my-db.example.com \
  --set externalPostgresql.password=<password> \
  --set redis.enabled=false \
  --set externalRedis.enabled=true \
  --set externalRedis.host=my-redis.example.com
```

## Upgrading

```bash
# Update chart repository
helm repo update yawl

# Upgrade to latest version
helm upgrade yawl yawl/yawl

# Upgrade to specific version
helm upgrade yawl yawl/yawl --version 1.0.0

# Upgrade with new values
helm upgrade yawl yawl/yawl -f custom-values.yaml
```

## High Availability

For production deployments with HA:

```bash
helm install yawl yawl/yawl \
  --set replicaCount=3 \
  --set postgresql.replica.replicaCount=2 \
  --set redis.replica.replicaCount=2 \
  --set podDisruptionBudget.enabled=true \
  --set autoscaling.enabled=true \
  --set affinity.podAntiAffinity.preferredDuringSchedulingIgnoredDuringExecution[0].weight=100
```

## Monitoring and Observability

### Prometheus Metrics

The chart includes Prometheus monitoring. Access metrics at:

```bash
kubectl port-forward -n yawl svc/yawl 8080:8080

# Metrics endpoint: http://localhost:8080/metrics
```

### Service Monitor

If using Prometheus Operator:

```bash
kubectl get servicemonitor -n yawl
```

## Backup and Recovery

### Enable Backups

```bash
helm install yawl yawl/yawl \
  --set backup.enabled=true \
  --set backup.schedule="0 2 * * *" \
  --set backup.retention=30
```

### Restore from Backup

```bash
kubectl exec -it yawl-0 -- /scripts/restore-backup.sh
```

## Security

### Enable Security Features

```bash
helm install yawl yawl/yawl \
  --set securityContext.runAsNonRoot=true \
  --set securityContext.allowPrivilegeEscalation=false \
  --set networkPolicy.enabled=true \
  --set podSecurityPolicy.enabled=true
```

### RBAC

```bash
helm install yawl yawl/yawl \
  --set rbac.create=true \
  --set serviceAccount.create=true
```

## Troubleshooting

### Check Pod Status

```bash
kubectl get pods -n yawl
kubectl describe pod <pod-name> -n yawl
```

### View Logs

```bash
# Application logs
kubectl logs -n yawl deployment/yawl

# Follow logs
kubectl logs -f -n yawl deployment/yawl

# Specific pod
kubectl logs -n yawl <pod-name>
```

### Debug Pod

```bash
kubectl debug pod/<pod-name> -n yawl -it
```

### Check Database Connection

```bash
kubectl exec -it -n yawl deployment/yawl -- psql -h yawl-postgresql -U yawladmin -d yawldb
```

### Health Check

```bash
kubectl exec -it -n yawl deployment/yawl -- curl http://localhost:8080/health
```

## Uninstall

```bash
# Remove release
helm uninstall yawl -n yawl

# Delete namespace
kubectl delete namespace yawl
```

## Chart Values

Full values can be viewed with:

```bash
helm show values yawl/yawl
```

To customize, create a `custom-values.yaml` file and install with:

```bash
helm install yawl yawl/yawl -f custom-values.yaml
```

## Contributing

Contributions are welcome! Please see the repository for guidelines.

## Support

- Documentation: https://docs.yawl.org
- GitHub: https://github.com/yawl/yawl
- Community: https://forum.yawl.org
- Issues: https://github.com/yawl/yawl/issues

## License

See LICENSE file for licensing information.
