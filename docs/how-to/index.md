# How-To Guides Index

Task-oriented guides for practitioners. You have a specific goal and need concrete instructions to achieve it.

---

## Configuration

| Guide | Task |
|-------|------|
| [configure-multi-tenancy.md](configure-multi-tenancy.md) | Set up isolated tenant namespaces |
| [configure-resource-service.md](configure-resource-service.md) | Define participants, roles, capabilities |
| [configure-spiffe.md](configure-spiffe.md) | Enable workload identity with SPIFFE/SVID |
| [enable-stateless-persistence.md](enable-stateless-persistence.md) | Switch to event-sourced stateless mode |
| [setup-org-model.md](setup-org-model.md) | Define the organisational hierarchy |
| [implement-worklet-service.md](implement-worklet-service.md) | Add runtime workflow adaptation via RDR rules |
| [configure-autonomous-agents.md](configure-autonomous-agents.md) | Configure autonomous agent integration |
| [java25-setup.md](java25-setup.md) | Set up Java 25 development environment |
| [validate-spec.md](validate-spec.md) | Validate a YAWL specification against the schema |
| [stateless-engine/migrate-to-stateless.md](stateless-engine/migrate-to-stateless.md) | Migrate from YEngine to YStatelessEngine for high-throughput workflows |

## Deployment

| Guide | Task |
|-------|------|
| [deployment/overview.md](deployment/overview.md) | Deployment approaches overview |
| [deployment/docker.md](deployment/docker.md) | Deploy with Docker containers |
| [deployment/jetty.md](deployment/jetty.md) | Embedded Jetty deployment |
| [deployment/tomcat.md](deployment/tomcat.md) | Apache Tomcat deployment |
| [deployment/wildfly.md](deployment/wildfly.md) | JBoss WildFly deployment |
| [deployment/production.md](deployment/production.md) | Full production deployment process |
| [deployment/checklist.md](deployment/checklist.md) | Pre-deployment checklist |
| [deployment/java25-upgrade.md](deployment/java25-upgrade.md) | Migrate from Java 21 to Java 25 |
| [deployment/spring-boot.md](deployment/spring-boot.md) | Migrate to Spring Boot 3.x |
| [deployment/virtual-threads.md](deployment/virtual-threads.md) | Enable Java virtual threads |
| [deployment/cloud-runbooks.md](deployment/cloud-runbooks.md) | Cloud deployment runbooks |

## CI/CD

| Guide | Task |
|-------|------|
| [cicd/build.md](cicd/build.md) | Configure automated builds |
| [cicd/setup.md](cicd/setup.md) | Full CI/CD pipeline setup |
| [cicd/implementation.md](cicd/implementation.md) | CI/CD implementation guide |
| [cicd/integration-testing.md](cicd/integration-testing.md) | Add integration test stage |
| [cicd/testing.md](cicd/testing.md) | Testing in CI environments |
| [cicd/maven-implementation.md](cicd/maven-implementation.md) | Maven build pipeline |

## Migration

| Guide | Task |
|-------|------|
| [migration/v5-to-v6.md](migration/v5-to-v6.md) | Upgrade from YAWL 5.x to 6.x |
| [migration/v6-guide.md](migration/v6-guide.md) | v6 migration guide |
| [migration/v6-upgrade.md](migration/v6-upgrade.md) | v6 upgrade process |
| [migration/javax-to-jakarta.md](migration/javax-to-jakarta.md) | Migrate javax.* → jakarta.* |
| [migration/checklist.md](migration/checklist.md) | Migration checklist |
| [migration/orm-migration.md](migration/orm-migration.md) | ORM (JPA/Hibernate) migration |

## Integration

| Guide | Task |
|-------|------|
| [integration/mcp-server.md](integration/mcp-server.md) | Configure and run the MCP server |
| [integration/a2a-server.md](integration/a2a-server.md) | Configure and run the A2A server |
| [integration/a2a-auth.md](integration/a2a-auth.md) | Secure A2A with JWT/OAuth |
| [integration/marketplace-quickstart.md](integration/marketplace-quickstart.md) | Quick marketplace integration |
| [integration/docker-validation.md](integration/docker-validation.md) | Validate integration in Docker |

## Operations

| Guide | Task |
|-------|------|
| [operations/disaster-recovery.md](operations/disaster-recovery.md) | Recover from system failure |
| [operations/scaling.md](operations/scaling.md) | Scale YAWL under load |
| [operations/upgrade.md](operations/upgrade.md) | Upgrade YAWL in-place |
| [operations/guide.md](operations/guide.md) | Operations reference guide |
| [rollback.md](rollback.md) | Roll back a deployment |
| [recovery-procedures.md](recovery-procedures.md) | Recovery procedures |
| [release-checklist.md](release-checklist.md) | Pre-release validation steps |
| [scaling.md](scaling.md) | Scaling and observability |

## Security

| Guide | Task |
|-------|------|
| [security/testing.md](security/testing.md) | Run security test suite |
| [spiffe.md](spiffe.md) | Configure SPIFFE integration |

## Performance

| Guide | Task |
|-------|------|
| [performance-testing.md](performance-testing.md) | Run performance benchmarks |

## Observability

| Guide | Task |
|-------|------|
| [observability/debug-with-observatory.md](observability/debug-with-observatory.md) | Debug YAWL using the Observatory system and fact analysis |

## GODSPEED

| Guide | Task |
|-------|------|
| [godspeed/run-the-full-circuit.md](godspeed/run-the-full-circuit.md) | Run complete GODSPEED validation circuit (Ψ→Λ→H→Q→Ω) |

## H-Guards (Production Quality Standards)

| Guide | Task |
|-------|------|
| [h-guards/fix-guard-violations.md](h-guards/fix-guard-violations.md) | Fix H-Guards violations (TODO, mock, stub, empty, fallback, lie, silent) |

## Development

| Guide | Task |
|-------|------|
| [contributing.md](contributing.md) | Contribute code to YAWL |
| [developer-guide.md](developer-guide.md) | Developer setup and workflow |
| [developer-build.md](developer-build.md) | Build YAWL for development |
| [development.md](development.md) | Development environment guide |
| [testing.md](testing.md) | Write and run tests |
| [quick-test.md](quick-test.md) | Quick test execution |
| [troubleshooting.md](troubleshooting.md) | Diagnose and fix common problems |

## Marketplace

| Guide | Task |
|-------|------|
| [marketplace/aws-deploy.md](marketplace/aws-deploy.md) | Deploy to AWS Marketplace |
| [marketplace/azure-deploy.md](marketplace/azure-deploy.md) | Deploy to Azure Marketplace |
| [marketplace/gcp-deploy.md](marketplace/gcp-deploy.md) | Deploy to GCP Marketplace |
| [marketplace/agent-integration.md](marketplace/agent-integration.md) | Integrate agents with marketplace |
| [marketplace-overview.md](marketplace-overview.md) | Marketplace overview |
| [marketplace-integration.md](marketplace-integration.md) | Integrate with a marketplace |

## Resilience

| Guide | Task |
|-------|------|
| [resilience/overview.md](resilience/overview.md) | Resilience patterns overview |
| [resilience/operations.md](resilience/operations.md) | Resilience operations guide |
| [resilience/quick-start.md](resilience/quick-start.md) | Get started with resilience patterns |
