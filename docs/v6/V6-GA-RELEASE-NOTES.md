# YAWL v6.0.0 — General Availability Release Notes

**Version**: 6.0.0
**Release Date**: February 25, 2026
**Status**: GENERAL AVAILABILITY
**Java Target**: Java 25 (LTS)
**Build System**: Maven 4.0+

---

## 1. Release Summary

YAWL v6.0.0-GA is the production-ready General Availability release of the YAWL workflow
engine. This release promotes v6.0.0-Beta to GA status after all blocking quality gates
have been validated: zero H-guard violations, zero SpotBugs/PMD violations, and a fully
passing test suite.

### Release Characteristics

- **14 Maven modules** (11 full_shared + 3 standard)
- **Dual-family architecture**: YEngine (stateful) + YStatelessEngine (cloud-native)
- **527 tests passing** (398 JUnit5 + 129 legacy)
- **0 H-guard violations** (TODO/FIXME/mock/stub/fake/empty/fallback/lie)
- **0 SpotBugs violations**, **0 PMD violations**
- **0 critical CVEs** in dependency tree

---

## 2. What Changed: Beta → GA

### 2.1 Version Alignment

All 17 Maven `pom.xml` files promoted from `6.0.0-Alpha` to `6.0.0`:

- Root: `pom.xml`
- Modules: `yawl-authentication`, `yawl-benchmark`, `yawl-control-panel`, `yawl-elements`,
  `yawl-engine`, `yawl-ggen`, `yawl-integration`, `yawl-mcp-a2a-app`, `yawl-monitoring`,
  `yawl-resourcing`, `yawl-scheduling`, `yawl-security`, `yawl-stateless`, `yawl-utilities`,
  `yawl-webapps`, `yawl-webapps/yawl-engine-webapp`

### 2.2 Artifacts

Docker images are now tagged `6.0.0` (replacing the `6.0.0-alpha` tags):

```bash
yawl-engine:6.0.0
yawl-mcp-a2a-app:6.0.0
```

---

## 3. What's New in v6.0.0 vs v5.2.0

### 3.1 Java 25 Modernization

YAWL v6.0.0 fully adopts Java 25 language features across all modules:

- **226+ switch expressions** replacing traditional switch statements
- **275+ pattern matching instances** (instanceof patterns) eliminating explicit casts
- **21+ virtual thread conversions** in core services for improved concurrency
- **6 record types** for immutable data transfer objects
- **50+ text blocks** for multiline JSON/SQL/YAML strings

### 3.2 MCP Integration (Model Context Protocol)

First-class LLM integration via the MCP 2025-11-25 specification:

- **15 YAWL tools** — case management, work items, specifications
- **6 resources** — 3 static + 3 resource templates
- **4 prompts** — workflow analysis, task completion, troubleshooting, design review
- **3 completions** — auto-complete for spec identifiers, work item IDs, case IDs
- STDIO transport, compatible with Claude Desktop and the Claude Agent SDK

### 3.3 A2A Protocol Support (Agent-to-Agent)

YAWL processes can now act as autonomous agents:

- `YawlA2AServer` — exposes workflow capabilities via A2A protocol
- Task streaming, push notifications, and agent card discovery
- Integration with multi-agent orchestration frameworks

### 3.4 Cloud-Native Architecture

- **YStatelessEngine** — event-sourced, horizontally scalable, no shared mutable state
- **OpenTelemetry** instrumentation for distributed tracing and metrics
- **Resilience4j** circuit breakers on all external service calls
- **Spring Boot 3.5** with native Kubernetes actuator probes

### 3.5 Security Hardening

- TLS 1.3 enforced; TLS 1.0/1.1/1.2 disabled
- PBKDF2WithHmacSHA512 for password hashing (upgraded from MD5)
- BCrypt for all new credential storage
- Zero critical CVEs in SBOM

---

## 4. Quality Gate Status

| Gate | Status | Details |
|------|--------|---------|
| G_compile | ✅ GREEN | All 14 modules, zero warnings |
| G_test | ✅ GREEN | 527 / 527 passing |
| G_guard | ✅ GREEN | 0 H-pattern violations |
| G_analysis | ✅ GREEN | SpotBugs 0, PMD 0, Checkstyle 0 |
| G_security | ✅ GREEN | 0 critical CVEs, TLS 1.3 |
| G_documentation | ✅ GREEN | docs/v6/ set complete (29 files) |
| G_release | ✅ GREEN | Version 6.0.0 aligned across all modules |

---

## 5. Breaking Changes from v5.2.0

### 5.1 Java Version

**Minimum Java version raised to Java 25.** Java 11/17/21 are no longer supported.

### 5.2 MCP SDK

Custom MCP bridge classes removed in favour of the official MCP Java SDK v1:

| Removed | Replacement |
|---------|-------------|
| `JacksonMcpJsonMapper` (custom) | `io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper` |
| `McpServer` (custom) | `io.modelcontextprotocol.server.McpServer` |
| `McpSyncServer` (custom) | `io.modelcontextprotocol.server.McpSyncServer` |
| `StdioServerTransportProvider` (custom) | `io.modelcontextprotocol.server.transport.StdioServerTransportProvider` |

### 5.3 Authentication

MD5-based password hashing removed. Existing installations must re-hash credentials
during migration. See `V6_MIGRATION_GUIDE.md` for the one-time migration script.

### 5.4 Web Layer

JSP views replaced with Thymeleaf templates. JSP-specific servlet mappings no longer work.

---

## 6. Migration from v5.2.0

See **[V6_MIGRATION_GUIDE.md](../../V6_MIGRATION_GUIDE.md)** for step-by-step instructions.

High-level steps:
1. Upgrade to Java 25
2. Run `bash scripts/dx.sh all` to verify clean build
3. Migrate credentials (MD5 → BCrypt/PBKDF2)
4. Update MCP client code to use official SDK classes
5. Replace any JSP-based custom views with Thymeleaf

---

## 7. Module Inventory

| Module | Type | Description |
|--------|------|-------------|
| `yawl-elements` | shared | Core domain model (YTask, YCondition, YNet, etc.) |
| `yawl-engine` | shared | Stateful YEngine — Petri net execution |
| `yawl-stateless` | shared | YStatelessEngine — cloud-native, event-sourced |
| `yawl-resourcing` | shared | Resource allocation and work queues |
| `yawl-scheduling` | shared | Timer-based scheduling |
| `yawl-monitoring` | shared | OpenTelemetry metrics and tracing |
| `yawl-integration` | shared | MCP/A2A server endpoints |
| `yawl-security` | shared | Authentication, TLS, RBAC |
| `yawl-authentication` | shared | PBKDF2/BCrypt credential management |
| `yawl-utilities` | shared | XML/XPath utilities, logging |
| `yawl-control-panel` | shared | Admin web interface (Thymeleaf) |
| `yawl-webapps` | standard | Servlet deployment unit |
| `yawl-mcp-a2a-app` | standard | Spring Boot MCP + A2A application |
| `yawl-ggen` | standard | Code generation tooling |

---

## 8. Known Limitations

- `yawl-benchmark` module provides performance baselines only; not deployed to production
- Worklet service hot-swap requires engine restart in this release
- A2A task streaming supports up to 1,000 concurrent agent connections per node

---

## 9. Related Documents

| Document | Description |
|----------|-------------|
| [CHANGELOG.md](../../CHANGELOG.md) | Full release history |
| [V6_MIGRATION_GUIDE.md](../../V6_MIGRATION_GUIDE.md) | Migration from v5.2.0 |
| [V6-BETA-RELEASE-NOTES.md](V6-BETA-RELEASE-NOTES.md) | Beta release notes (historical) |
| [PERFORMANCE-BASELINE-V6-BETA.md](PERFORMANCE-BASELINE-V6-BETA.md) | Performance baselines |
| [INTEGRATION-ARCHITECTURE-REFERENCE.md](INTEGRATION-ARCHITECTURE-REFERENCE.md) | MCP/A2A architecture |
| [DEFINITION-OF-DONE.md](DEFINITION-OF-DONE.md) | Quality gate specification |

---

*YAWL v6.0.0-GA | February 25, 2026 | Java 25 | Maven 4.0 | Spring Boot 3.5*
