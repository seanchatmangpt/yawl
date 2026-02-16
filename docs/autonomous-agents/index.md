# YAWL Autonomous Agents - Documentation Index

## Quick Links

- **Migration Guide:** [migration-guide.md](migration-guide.md) - Step-by-step guide for migrating from legacy to generic framework
- **Architecture Overview:** [README.md](README.md) - System architecture and design
- **Code Review:** [../code-review-generic-framework.md](../code-review-generic-framework.md) - Comprehensive code quality assessment
- **Phase 7 Summary:** [../PHASE_7_COMPLETION_SUMMARY.md](../PHASE_7_COMPLETION_SUMMARY.md) - Completion status and metrics

## Documentation Structure

```
docs/
├── autonomous-agents/
│   ├── index.md                    # This file - Documentation index
│   ├── README.md                   # Architecture overview
│   ├── migration-guide.md          # Migration from legacy to generic
│   ├── configuration-guide.md      # YAML configuration reference
│   └── api-documentation.md        # API specifications
├── code-review-generic-framework.md # Code quality assessment (Phase 7)
└── PHASE_7_COMPLETION_SUMMARY.md   # Phase 7 deliverables and metrics
```

## For New Users

Start here:
1. **[Architecture Overview](README.md)** - Understand the system design
2. **[Configuration Guide](configuration-guide.md)** - Learn how to configure agents
3. **[API Documentation](api-documentation.md)** - Reference for integrations

## For Existing Orderfulfillment Users

Migration path:
1. **[Migration Guide](migration-guide.md)** - Complete migration instructions
2. **[Code Review](../code-review-generic-framework.md)** - Quality assurance for new framework
3. **[Phase 7 Summary](../PHASE_7_COMPLETION_SUMMARY.md)** - Production readiness status

## Key Components

### Generic Framework (`/src/org/yawlfoundation/yawl/integration/autonomous/`)

| Component | Description | Status |
|-----------|-------------|--------|
| **GenericPartyAgent** | Main autonomous agent implementation | ✅ Production |
| **AgentFactory** | Factory for creating agents | ✅ Production |
| **AgentConfiguration** | Builder-pattern configuration | ✅ Production |
| **ZaiEligibilityReasoner** | AI-based eligibility reasoning | ✅ Production |
| **ZaiDecisionReasoner** | AI-based decision reasoning | ✅ Production |
| **AgentRegistry** | Service registry with REST API | ✅ Production |
| **CircuitBreaker** | Fault tolerance component | ✅ Production |
| **RetryPolicy** | Retry with exponential backoff | ✅ Production |
| **StructuredLogger** | Enhanced logging with context | ✅ Production |

### Legacy Framework (`/src/org/yawlfoundation/yawl/integration/orderfulfillment/`)

| Component | Status | Replacement |
|-----------|--------|-------------|
| **OrderfulfillmentLauncher** | ⚠️ Deprecated | GenericWorkflowLauncher |
| **PartyAgent** | ⚠️ Deprecated | GenericPartyAgent |
| **EligibilityWorkflow** | ⚠️ Deprecated | ZaiEligibilityReasoner |
| **DecisionWorkflow** | ⚠️ Deprecated | ZaiDecisionReasoner |

**Deprecation Timeline:**
- **v5.2 (Current):** Marked @Deprecated with migration guidance
- **v5.3 (Q2 2026):** Runtime warnings when using deprecated classes
- **v6.0 (Q4 2026):** Legacy classes removed (breaking change)

## Production Readiness

**Overall Score:** 9.0/10 (Excellent - Production Ready)

**Quality Metrics:**
- HYPER_STANDARDS Compliance: 100% (5/5 checks passed)
- Architecture (SOLID): 9/10
- Security: 8.5/10
- Code Quality: 9.5/10
- Thread Safety: 10/10
- Documentation: 10/10

**See:** [Code Review](../code-review-generic-framework.md) for detailed analysis.

## Getting Started

### 1. Environment Setup

```bash
# Set required environment variables
export YAWL_ENGINE_URL="http://localhost:8080/yawl"
export YAWL_USERNAME="admin"
export YAWL_PASSWORD="YAWL"
export ZAI_API_KEY="your-zai-api-key"
export AGENT_CAPABILITY="Ordering: procurement, purchase orders, approvals"
```

### 2. Compile YAWL

```bash
cd /home/user/yawl
ant compile
```

### 3. Run Generic Agent

```bash
java -cp build/classes:lib/* \
  org.yawlfoundation.yawl.integration.autonomous.GenericPartyAgent
```

### 4. Launch Workflow

```bash
java -cp build/classes:lib/* \
  org.yawlfoundation.yawl.integration.autonomous.launcher.GenericWorkflowLauncher \
  --spec-id UID_ae0b797c-2ac8-4d5e-9421-ece89d8043d0 \
  --spec-path exampleSpecs/orderfulfillment.yawl
```

## Support

- **Documentation Issues:** Check [migration-guide.md](migration-guide.md) FAQ section
- **Code Quality Questions:** See [code-review-generic-framework.md](../code-review-generic-framework.md)
- **Architecture Questions:** See [README.md](README.md)
- **Migration Help:** Follow step-by-step guide in [migration-guide.md](migration-guide.md)

## Contributing

When contributing to the autonomous agents framework:

1. **Follow HYPER_STANDARDS:**
   - No TODO/FIXME markers
   - No mocks/stubs in production code
   - All exceptions properly propagated
   - Documentation must match implementation

2. **Code Quality:**
   - Adhere to SOLID principles
   - Use dependency injection
   - Ensure thread safety
   - Comprehensive error handling

3. **Documentation:**
   - JavaDoc for all public APIs
   - Usage examples in JavaDoc
   - Update migration guide if APIs change

4. **Testing:**
   - Unit tests for all strategies
   - Integration tests for workflows
   - Performance tests for high-throughput scenarios

## Version History

- **v5.2 (2026-02-16):** Generic framework introduced, legacy framework deprecated
- **v5.3 (Q2 2026):** Runtime deprecation warnings, enhanced tooling
- **v6.0 (Q4 2026):** Legacy framework removed (breaking change)

## License

YAWL is licensed under the GNU Lesser General Public License (LGPL).

See: [https://www.yawlfoundation.org/](https://www.yawlfoundation.org/)
