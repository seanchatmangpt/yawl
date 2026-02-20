# Archived: Legacy Deployment Docs (2026-02-20)

These documents described deployment of YAWL onto standalone Java application servers
(Tomcat, WildFly, Jetty) as WAR files. They were the primary deployment model for
YAWL v5.x.

## Why Archived

YAWL v6.0.0 is packaged as a self-contained Spring Boot JAR with an embedded servlet
container. The build system is Maven (not Ant). The canonical deployment is:

- Docker container using `docker/production/Dockerfile.engine`
- Kubernetes via `kubernetes/yawl-deployment.yaml`
- Docker Compose via `docker-compose.prod.yml`

Deploying to an external application server is no longer a supported or tested path.

## Archived Files

| File | Original Path | Reason |
|------|---------------|--------|
| `DEPLOY-TOMCAT.md` | `docs/DEPLOY-TOMCAT.md` | WAR/Tomcat deployment: superseded by Docker |
| `DEPLOY-WILDFLY.md` | `docs/DEPLOY-WILDFLY.md` | WAR/WildFly deployment: superseded by Docker |
| `DEPLOY-JETTY.md` | `docs/DEPLOY-JETTY.md` | WAR/Jetty deployment: superseded by Docker |

## Current Deployment Docs

See `docs/v6-DEPLOYMENT-GUIDE.md` for the canonical v6.0.0 production deployment guide.
