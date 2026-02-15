# CIS Benchmarks for YAWL Multi-Cloud Deployment

## Overview

This document provides CIS (Center for Internet Security) Benchmark implementations for securing YAWL deployments across different platforms.

## CIS Docker Benchmark

### 1 - Host Configuration

#### 1.1 - Ensure a separate partition for containers has been created

```bash
# Verify container storage is on separate partition
df -h /var/lib/docker

# Recommended: Create dedicated LVM volume
lvcreate -L 100G -n docker vg0
mkfs.xfs /dev/vg0/docker
echo "/dev/vg0/docker /var/lib/docker xfs defaults 0 0" >> /etc/fstab
```

#### 1.2 - Ensure the container host has been hardened

```bash
# Apply CIS OS benchmark
# Use hardened base images
```

#### 1.6 - Ensure /etc/default/docker is not world-writable

```bash
stat -c %a /etc/default/docker
# Should be 644 or more restrictive
chmod 644 /etc/default/docker
```

### 2 - Docker daemon configuration

#### 2.1 - Ensure network traffic is restricted between containers

```json
// /etc/docker/daemon.json
{
  "icc": false,
  "userns-remap": "default"
}
```

#### 2.2 - Ensure the logging level is set to 'info'

```json
{
  "log-level": "info"
}
```

#### 2.3 - Ensure Docker is allowed to make changes to iptables

```json
{
  "iptables": true
}
```

#### 2.4 - Ensure insecure registries are not used

```json
{
  "insecure-registries": []
}
```

#### 2.5 - Ensure aufs storage driver is not used

```bash
docker info | grep "Storage Driver"
# Should NOT be aufs
```

#### 2.6 - Ensure TLS authentication for Docker daemon is configured

```json
{
  "tls": true,
  "tlsverify": true,
  "tlscacert": "/etc/docker/ssl/ca.pem",
  "tlscert": "/etc/docker/ssl/server-cert.pem",
  "tlskey": "/etc/docker/ssl/server-key.pem",
  "hosts": ["tcp://0.0.0.0:2376"]
}
```

#### 2.7 - Ensure the default ulimit is configured appropriately

```json
{
  "default-ulimits": {
    "nofile": {
      "Name": "nofile",
      "Hard": 65535,
      "Soft": 1024
    }
  }
}
```

#### 2.8 - Enable user namespace support

```json
{
  "userns-remap": "default"
}
```

#### 2.9 - Ensure the default cgroup usage has been confirmed

```json
{
  "cgroup-parent": ""
}
```

#### 2.10 - Ensure base device size is not too small

```json
{
  "storage-opts": ["dm.basesize=20G"]
}
```

#### 2.11 - Ensure that authorization for Docker client commands is enabled

```json
{
  "authorization-plugins": ["yawl-authz-plugin"]
}
```

#### 2.12 - Ensure centralized and remote logging is configured

```json
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "3",
    "labels": "application,environment"
  }
}
```

#### 2.13 - Ensure live restore is enabled

```json
{
  "live-restore": true
}
```

#### 2.14 - Ensure Userland Proxy is disabled

```json
{
  "userland-proxy": false
}
```

#### 2.15 - Ensure daemon is confined to appropriate capabilities

```json
// systemd unit file override
[Service]
CapabilityBoundingSet=CAP_NET_BIND_SERVICE CAP_CHOWN CAP_DAC_OVERRIDE CAP_FOWNER CAP_FSETID CAP_KILL CAP_SETGID CAP_SETUID CAP_SETPCAP CAP_NET_RAW CAP_SYS_CHROOT CAP_MKNOD CAP_AUDIT_WRITE CAP_SETFCAP
```

#### 2.16 - Ensure daemon is confined with seccomp profile

```json
{
  "seccomp-profile": "/etc/docker/seccomp.json"
}
```

#### 2.17 - Ensure experimental features are not used in production

```json
{
  "experimental": false
}
```

### 3 - Docker daemon configuration files

#### 3.1 - Ensure docker.service file permissions are set correctly

```bash
stat -c "%a %n" /usr/lib/systemd/system/docker.service
# Should be 644 or more restrictive
chmod 644 /usr/lib/systemd/system/docker.service
```

#### 3.2 - Ensure docker.service file ownership is set correctly

```bash
stat -c "%U:%G" /usr/lib/systemd/system/docker.service
# Should be root:root
chown root:root /usr/lib/systemd/system/docker.service
```

#### 3.3 - Ensure docker.socket file permissions are set correctly

```bash
chmod 644 /usr/lib/systemd/system/docker.socket
```

#### 3.4 - Ensure docker.socket file ownership is set correctly

```bash
chown root:root /usr/lib/systemd/system/docker.socket
```

#### 3.5 - Ensure /etc/docker directory permissions are set correctly

```bash
stat -c "%a" /etc/docker
# Should be 755 or more restrictive
chmod 755 /etc/docker
```

#### 3.6 - Ensure /etc/docker directory ownership is set correctly

```bash
chown root:root /etc/docker
```

#### 3.7 - Ensure daemon.json file permissions are set correctly

```bash
chmod 644 /etc/docker/daemon.json
```

#### 3.8 - Ensure daemon.json file ownership is set correctly

```bash
chown root:root /etc/docker/daemon.json
```

#### 3.9 - Ensure /etc/default/docker file permissions are set correctly

```bash
chmod 644 /etc/default/docker
```

#### 3.10 - Ensure /etc/default/docker file ownership is set correctly

```bash
chown root:root /etc/default/docker
```

### 4 - Container Images and Build File Configuration

#### 4.1 - Ensure a user for the container has been created

```dockerfile
# In Dockerfile
RUN addgroup -S yawlgroup && adduser -S yawluser -G yawlgroup
USER yawluser
```

#### 4.2 - Ensure containers use trusted base images

```bash
# Use signed images only
docker trust inspect yawl/engine:5.2
```

#### 4.3 - Ensure unnecessary packages are not installed

```dockerfile
# Multi-stage builds, minimal packages
FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl
```

#### 4.4 - Ensure images are scanned and rebuilt periodically

```bash
# Trivy scanning
trivy image yawl/engine:5.2

# Grype scanning
grype yawl/engine:5.2
```

#### 4.5 - Ensure Content Trust for Docker is enabled

```bash
export DOCKER_CONTENT_TRUST=1
export DOCKER_CONTENT_TRUST_SERVER=https://notary.example.com
```

#### 4.6 - Ensure HEALTHCHECK instructions have been added to containers

```dockerfile
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/health || exit 1
```

#### 4.7 - Ensure update instructions are not used alone

```dockerfile
# Pin versions instead of using latest or update
RUN apk add --no-cache curl=8.5.0-r0
```

#### 4.8 - Ensure setuid and setgid permissions are removed

```dockerfile
RUN find /usr -type f -perm /u+s -exec chmod u-s {} \; 2>/dev/null || true && \
    find /usr -type f -perm /g+s -exec chmod g-s {} \; 2>/dev/null || true
```

#### 4.9 - Ensure COPY is used instead of ADD

```dockerfile
# Use COPY for files
COPY app.jar /app/app.jar

# Only use ADD for remote URLs or extraction
# ADD https://example.com/file.tar.gz /tmp/
```

#### 4.10 - Ensure secrets are not stored in Dockerfiles

```dockerfile
# Use secret mounting instead
# RUN --mount=type=secret,id=db_password cat /run/secrets/db_password
```

#### 4.11 - Ensure only verified packages are installed

```bash
# Verify package signatures
apk verify --no-cache *.apk
```

### 5 - Container Runtime Configuration

#### 5.1 - Ensure AppArmor Profile is enabled

```bash
docker run --security-opt apparmor=docker-default yawl/engine:5.2
```

#### 5.2 - Ensure SELinux security options are set

```bash
docker run --security-opt label=level:s0:c100,c200 yawl/engine:5.2
```

#### 5.3 - Ensure Linux Kernel Capabilities are restricted

```bash
docker run --cap-drop=ALL --cap-add=NET_BIND_SERVICE yawl/engine:5.2
```

#### 5.4 - Ensure privileged containers are not used

```bash
# Do not use --privileged flag
docker run yawl/engine:5.2  # Without --privileged
```

#### 5.5 - Ensure sensitive host system directories are not mounted

```bash
# Avoid mounting sensitive directories
# NOT: -v /:/host
# NOT: -v /etc:/host-etc
```

#### 5.6 - Ensure ssh is not run within containers

```dockerfile
# Do not install SSH in containers
# Use docker exec instead
```

#### 5.7 - Ensure privileged ports are not mapped

```bash
# Do not map privileged ports (< 1024)
# NOT: -p 80:8080
# Use: -p 8080:8080
```

#### 5.8 - Ensure only needed ports are open

```dockerfile
# Expose only required ports
EXPOSE 8080
```

#### 5.9 - Ensure the host's network namespace is not shared

```bash
# Do not use --network host
docker run yawl/engine:5.2  # Without --network host
```

#### 5.10 - Ensure memory usage for container is limited

```bash
docker run --memory="2g" --memory-swap="2g" yawl/engine:5.2
```

#### 5.11 - Ensure CPU priority is set appropriately

```bash
docker run --cpus="2" --cpu-shares=1024 yawl/engine:5.2
```

#### 5.12 - Ensure the container's root filesystem is mounted as read only

```bash
docker run --read-only yawl/engine:5.2
```

#### 5.13 - Ensure incoming container traffic is bound to a specific host interface

```bash
# Bind to specific interface
docker run -p 10.0.0.1:8080:8080 yawl/engine:5.2
```

#### 5.14 - Ensure 'on-failure' container restart policy is set

```bash
docker run --restart=on-failure:5 yawl/engine:5.2
```

#### 5.15 - Ensure the host's process namespace is not shared

```bash
# Do not use --pid=host
docker run yawl/engine:5.2  # Without --pid=host
```

#### 5.16 - Ensure the host's IPC namespace is not shared

```bash
# Do not use --ipc=host
docker run yawl/engine:5.2  # Without --ipc=host
```

#### 5.17 - Ensure host devices are not directly exposed to containers

```bash
# Avoid --device flag unless absolutely necessary
docker run yawl/engine:5.2  # Without --device
```

#### 5.18 - Ensure the default ulimit is overwritten at runtime if needed

```bash
docker run --ulimit nofile=65535:65535 yawl/engine:5.2
```

#### 5.19 - Ensure mount propagation mode is not set to shared

```bash
# Avoid shared propagation
docker run -v /data:/data:slave yawl/engine:5.2
```

#### 5.20 - Ensure the host's UTS namespace is not shared

```bash
# Do not use --uts=host
docker run yawl/engine:5.2  # Without --uts=host
```

#### 5.21 - Ensure the default seccomp profile is not disabled

```bash
# Do not use --security-opt seccomp=unconfined
docker run --security-opt seccomp=unconfined yawl/engine:5.2  # AVOID
```

#### 5.22 - Ensure docker exec commands are not used with privileged option

```bash
# Do not use --privileged with exec
docker exec -it container_id /bin/sh  # Without --privileged
```

#### 5.23 - Ensure docker exec commands are not used with user option

```bash
# Be cautious with --user flag
docker exec -it container_id /bin/sh  # Run as container user
```

#### 5.24 - Ensure cgroup usage is confirmed

```bash
docker run --cgroup-parent=yawl-engine yawl/engine:5.2
```

#### 5.25 - Ensure container is restricted from acquiring additional privileges

```bash
docker run --security-opt=no-new-privileges yawl/engine:5.2
```

#### 5.26 - Ensure container health is checked at runtime

```bash
docker run --health-cmd="curl -f http://localhost:8080/health || exit 1" \
           --health-interval=30s \
           --health-timeout=10s \
           --health-retries=3 \
           yawl/engine:5.2
```

#### 5.27 - Ensure docker commands always get the latest image

```bash
# Use --pull always for docker run
docker run --pull always yawl/engine:5.2
```

#### 5.28 - Ensure pid cgroup limit is used

```bash
docker run --pids-limit 100 yawl/engine:5.2
```

#### 5.29 - Ensure Docker's secret management capabilities are used

```bash
# Use Docker secrets
docker service create --secret db_password yawl/engine:5.2
```

#### 5.30 - Ensure container is run with a minimum Linux Kernel version

```bash
# Ensure kernel >= 4.x with support for all security features
uname -r
```

#### 5.31 - Ensure the Docker socket is not mounted inside any containers

```bash
# Do not mount Docker socket
# NOT: -v /var/run/docker.sock:/var/run/docker.sock
```

### 6 - Docker Security Operations

#### 6.1 - Ensure image sprawl is avoided

```bash
# Regularly clean unused images
docker image prune -a --filter "until=720h"
```

#### 6.2 - Ensure container sprawl is avoided

```bash
# Regularly clean unused containers
docker container prune --filter "until=24h"
```

## CIS Kubernetes Benchmark

### 1 - Control Plane Security

#### 1.1 - Control Plane Node Configuration

```yaml
# /etc/kubernetes/manifests/kube-apiserver.yaml
spec:
  containers:
  - command:
    - kube-apiserver
    - --anonymous-auth=false
    - --authorization-mode=Node,RBAC
    - --enable-admission-plugins=NodeRestriction,PodSecurityPolicy
    - --secure-port=6443
    - --tls-cert-file=/etc/kubernetes/pki/apiserver.crt
    - --tls-private-key-file=/etc/kubernetes/pki/apiserver.key
    - --client-ca-file=/etc/kubernetes/pki/ca.crt
    - --etcd-certfile=/etc/kubernetes/pki/etcd/client.crt
    - --etcd-keyfile=/etc/kubernetes/pki/etcd/client.key
    - --etcd-cafile=/etc/kubernetes/pki/etcd/ca.crt
```

#### 1.2 - API Server

| Recommendation | Setting | Value |
|----------------|---------|-------|
| 1.2.1 | --anonymous-auth | false |
| 1.2.2 | --token-auth-file | Not set |
| 1.2.4 | --kubelet-https | true |
| 1.2.5-8 | --authorization-mode | Node,RBAC |
| 1.2.16 | --audit-log-path | /var/log/kubernetes/audit.log |
| 1.2.20 | --profiling | false |

#### 1.3 - Controller Manager

```yaml
spec:
  containers:
  - command:
    - kube-controller-manager
    - --use-service-account-credentials=true
    - --service-account-private-key-file=/etc/kubernetes/pki/sa.key
    - --root-ca-file=/etc/kubernetes/pki/ca.crt
    - --profiling=false
    - --terminated-pod-gc-threshold=1000
```

#### 1.4 - Scheduler

```yaml
spec:
  containers:
  - command:
    - kube-scheduler
    - --profiling=false
    - --bind-address=127.0.0.1
```

### 2 - Etcd

```yaml
# etcd configuration
spec:
  containers:
  - command:
    - etcd
    - --client-cert-auth=true
    - --cert-file=/etc/kubernetes/pki/etcd/server.crt
    - --key-file=/etc/kubernetes/pki/etcd/server.key
    - --peer-client-cert-auth=true
    - --peer-cert-file=/etc/kubernetes/pki/etcd/peer.crt
    - --peer-key-file=/etc/kubernetes/pki/etcd/peer.key
```

### 3 - Control Plane Configuration

#### 3.1 - Authentication and Authorization

- Use RBAC for authorization
- Disable anonymous authentication
- Use Node authorization mode

#### 3.2 - Logging

```yaml
# Audit policy
apiVersion: audit.k8s.io/v1
kind: Policy
rules:
- level: Metadata
  resources:
  - group: ""
    resources: ["secrets"]
  verbs: ["get", "list", "watch"]
- level: Request
  resources:
  - group: ""
    resources: ["pods"]
  verbs: ["create", "update", "patch", "delete"]
```

### 4 - Worker Nodes

#### 4.1 - Worker Node Configuration

```yaml
# kubelet configuration
apiVersion: kubelet.config.k8s.io/v1beta1
kind: KubeletConfiguration
authentication:
  anonymous:
    enabled: false
  webhook:
    enabled: true
authorization:
  mode: Webhook
readOnlyPort: 0
protectKernelDefaults: true
```

### 5 - Policies

#### 5.1 - RBAC

- Use least privilege
- Regular access reviews
- Disable default service account token mounting

#### 5.2 - Pod Security Policies/Standards

```yaml
apiVersion: policy/v1beta1
kind: PodSecurityPolicy
metadata:
  name: restricted
spec:
  privileged: false
  allowPrivilegeEscalation: false
  requiredDropCapabilities:
    - ALL
  volumes:
    - configMap
    - downwardAPI
    - emptyDir
    - persistentVolumeClaim
    - secret
    - projected
  hostNetwork: false
  hostIPC: false
  hostPID: false
  runAsUser:
    rule: MustRunAsNonRoot
  seLinux:
    rule: RunAsAny
  supplementalGroups:
    rule: MustRunAs
    ranges:
      - min: 1
        max: 65535
  fsGroup:
    rule: MustRunAs
    ranges:
      - min: 1
        max: 65535
  readOnlyRootFilesystem: true
```

## CIS Benchmark Scanner Configuration

### kube-bench Configuration

```yaml
# kube-bench job
apiVersion: batch/v1
kind: Job
metadata:
  name: kube-bench
spec:
  template:
    spec:
      hostPID: true
      containers:
        - name: kube-bench
          image: aquasec/kube-bench:latest
          command: ["kube-bench"]
          args:
            - --benchmark
            - cis-1.8
            - --json
            - --outputfile
            - /results/results.json
          volumeMounts:
            - name: var-lib-etcd
              mountPath: /var/lib/etcd
            - name: var-lib-kubelet
              mountPath: /var/lib/kubelet
            - name: etc-kubernetes
              mountPath: /etc/kubernetes
            - name: results
              mountPath: /results
      volumes:
        - name: var-lib-etcd
          hostPath:
            path: /var/lib/etcd
        - name: var-lib-kubelet
          hostPath:
            path: /var/lib/kubelet
        - name: etc-kubernetes
          hostPath:
            path: /etc/kubernetes
        - name: results
          emptyDir: {}
      restartPolicy: Never
```

## Remediation Tracking

| Benchmark | Section | Finding | Severity | Status | Remediation Date |
|-----------|---------|---------|----------|--------|------------------|
| Docker | 2.6 | TLS not configured | High | Fixed | 2024-01-15 |
| Kubernetes | 1.2.1 | Anonymous auth enabled | High | Fixed | 2024-01-15 |
| Docker | 5.1 | AppArmor not enabled | Medium | In Progress | 2024-01-20 |
