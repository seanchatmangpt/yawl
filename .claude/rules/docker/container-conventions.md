---
paths:
  - "docker/**"
  - "kubernetes/**"
  - "gitops/**"
  - "**/Dockerfile*"
  - "**/docker-compose*.yml"
  - "**/*.Dockerfile"
---

# Docker & Kubernetes Rules

## Container Security
- Run as non-root user (`dev:yawl`, UID 1000) — never `USER root` in production images
- Use `_FILE` suffix env vars for secrets: `YAWL_PASSWORD_FILE=/run/secrets/yawl_password`
- Never bake credentials into images or docker-compose files
- Set memory/CPU resource limits in all compose and k8s manifests
- Multi-stage builds: build stage with JDK, runtime stage with JRE only

## Docker Compose Files (8 variants)
- `docker-compose.yml` — Primary production stack
- `docker-compose.test.yml` — Integration test environment
- `docker-compose.a2a-mcp-test.yml` — MCP/A2A integration tests
- Each service must have health checks defined
- Use named volumes for persistent data, tmpfs for ephemeral

## Image Conventions
- Tag format: `yawl-<module>:6.0.0-alpha` (match pom.xml version)
- Multi-arch: Support `linux/amd64` and `linux/arm64` via buildx
- Base image: Eclipse Temurin JRE 25 (or equivalent)

## Kubernetes
- Liveness probe: `/actuator/health/liveness` (initialDelay: 90s)
- Readiness probe: `/actuator/health/readiness` (initialDelay: 60s)
- Always define resource requests AND limits
- Use ConfigMaps for non-secret config, Secrets for credentials

## JVM in Containers
- `-XX:+UseContainerSupport` — respect container memory limits
- `-XX:MaxRAMPercentage=75.0` — leave headroom for OS
- `-XX:+UseZGC -XX:+ZGenerational` — low-latency GC for containers
