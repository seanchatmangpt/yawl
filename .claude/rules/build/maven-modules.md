---
paths:
  - "**/pom.xml"
  - ".mvn/**"
---

# Maven Module Rules

## Build Order (reactor sequence)
1. `yawl-utilities` — Foundation: shared utils, schema, unmarshal
2. `yawl-elements` — Domain: Petri net model (YNet, YTask, YSpec)
3. `yawl-authentication` — Security: session, JWT, CSRF
4. `yawl-engine` — Core: stateful engine + persistence
5. `yawl-stateless` — Cloud: event-driven engine, no DB
6. `yawl-resourcing` — Resources: allocators, work queues
7. `yawl-scheduling` — Time: timers, calendar
8. `yawl-security` — Crypto: PKI, digital signatures
9. `yawl-integration` — Agents: MCP + A2A connectors
10. `yawl-monitoring` — Ops: OpenTelemetry, Prometheus
11. `yawl-webapps` — Web: WAR aggregator
12. `yawl-control-panel` — UI: Swing desktop client
13. `yawl-mcp-a2a-app` — Spring Boot MCP/A2A server

## Dependency Rules
- All versions centralized in parent POM (`yawl-parent`) — children never declare versions
- Use Maven BOM for dependency alignment
- `mvn enforcer:enforce` fails on: Maven < 3.9, Java < 25, duplicate deps, unversioned plugins

## JVM Configurations (.mvn/)
- `jvm.config` — Default (dev): `-Xms2g -Xmx8g`, ZGC, compact headers, preview features
- `jvm.config.ci` — CI: optimized for build speed
- `jvm.config.dev` — Development: debugging enabled
- `jvm.config.prod` — Production: tuned for throughput
- Switch: `cp .mvn/jvm.config.ci .mvn/jvm.config`

## Module Targeting
- Single module: `mvn -pl yawl-engine clean test`
- Module + dependents: `mvn -pl yawl-elements -amd clean test`
- dx.sh auto-detects changed modules from git diff
