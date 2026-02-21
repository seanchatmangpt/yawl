# MCP (Model Context Protocol) for YAWL — Documentation Index

**Complete design research for enabling LLM tool use via YAWL workflows**

---

## Documents (Start Here)

### 1. **MCP_RESEARCH_SUMMARY.md** (7 pages) — START HERE
**Overview and executive summary of the complete design.**

- What was delivered (2 design docs + 1 summary)
- Key concepts (What is MCP, tool example, LLM orchestration)
- Architectural highlights (production-ready server, security, observability)
- Use cases enabled (purchase approval, procurement, compliance)
- Implementation path (phases, timeline)
- Metrics & SLAs
- Design rationale (Why MCP, Why Workflows-as-Tools)
- Next steps and conclusion

**Read this first to understand scope and approach.**

---

### 2. **MCP_LLM_TOOL_DESIGN.md** (45 KB, ~15 pages) — MAIN DESIGN DOCUMENT
**Comprehensive architectural design covering all aspects.**

#### Part 1: MCP Server Architecture
- What is MCP (protocol overview)
- Tool registration & schema definition (JSON Schema examples)
- Tool invocation flow (7-step sequence diagram)
- Detailed tool example with full Java code

#### Part 2: Workflow-as-Tools Pattern
- Pattern overview (workflow = tool)
- Template for creating workflow tools
- Three real-world examples:
  - Vendor Selection (RFQ workflow)
  - Compliance Check (sanctions/thresholds)
  - Purchase Approval (delegation limits)

#### Part 3: LLM Interaction Patterns
- Sequential tool calls (multi-step business processes)
- Tool result streaming (long-running workflows)
- Error recovery patterns (timeout, rate limit, validation)
- Execution traces showing Claude orchestrating workflows

#### Part 4: Security & Observability
- Input validation (type checking, XSS/SQL prevention)
- Rate limiting (per-client, per-tool)
- Output filtering (redact SSN, credit cards, API keys)
- Logging & tracing (JSON structured logs, OpenTelemetry, metrics)

#### Part 5: Prompt Engineering for LLM Tool Use
- System prompt template
- Few-shot examples
- Error handling guidance
- Response format for multi-step flows

#### Part 6: Proof of Concept
- PoC scenario (supply chain automation)
- Setup instructions (5 steps)
- Workflow definitions (XML)

#### Part 7: Implementation Reference
- Links to existing YAWL MCP server
- Adding custom workflow tools
- Production deployment options

**Read this for complete architecture details.**

---

### 3. **MCP_WORKFLOW_TOOL_EXAMPLES.md** (34 KB, ~12 pages) — CODE REFERENCE
**Copy-paste implementation templates and working code examples.**

#### Section 1: Tool Implementation Template
- Basic template (reusable boilerplate)
- Validation helpers (`validateStringArg()`, `validateNumberArg()`, etc.)
- Utility functions (XML escaping, case polling, logging)

#### Section 2: Real-World Workflow Tools (Complete Code)
- **Purchase Approval Tool** (~150 lines)
  - Auto-approve under delegation limit
  - Delegation limit lookup from HR system
  - XML result parsing

- **Vendor Selection Tool** (~120 lines)
  - RFQ workflow orchestration
  - Budget/timeline constraints
  - Best-price selection

- **Compliance Check Tool** (~130 lines)
  - OFAC/sanctions checking
  - Amount thresholds
  - Country restrictions

#### Section 3: Integration into YawlMcpServer
- How to add custom tools
- Main entry point configuration

#### Section 4: Testing Your Tools
- Unit test template
- Integration test with real YAWL engine

#### Section 5: Claude System Prompt for Tools
- Ready-to-use system prompt
- Few-shot examples
- Error handling guidance

**Read this to implement custom tools. Copy code directly.**

---

## Quick Links

### For Architects
1. Read MCP_RESEARCH_SUMMARY.md (7 pages, 15 min)
2. Skim MCP_LLM_TOOL_DESIGN.md Part 1-2 (architecture + patterns)
3. Review metrics & SLAs in summary

### For Engineers
1. Read MCP_RESEARCH_SUMMARY.md (overview)
2. Read MCP_LLM_TOOL_DESIGN.md Part 7 (implementation reference)
3. Use MCP_WORKFLOW_TOOL_EXAMPLES.md as code template
4. Copy template from Section 1 and customize

### For LLM Platform Teams
1. Read MCP_LLM_TOOL_DESIGN.md Part 3 (interaction patterns)
2. Read MCP_LLM_TOOL_DESIGN.md Part 5 (prompt engineering)
3. Copy system prompt from MCP_WORKFLOW_TOOL_EXAMPLES.md Section 5
4. Deploy MCP server using existing YawlMcpServer.java

### For DevOps/SRE
1. Read MCP_RESEARCH_SUMMARY.md (implementation path)
2. Read MCP_LLM_TOOL_DESIGN.md Part 7 (deployment)
3. Use Docker/Kubernetes examples from deployment section
4. Set up monitoring per Section 4 (observability)

---

## Key Files in YAWL Codebase

| File | Purpose | Status |
|------|---------|--------|
| `src/org/yawlfoundation/yawl/integration/mcp/YawlMcpServer.java` | Main MCP server (270 lines) | Production-ready |
| `src/org/yawlfoundation/yawl/integration/mcp/spec/YawlToolSpecifications.java` | 15 core tool definitions (920 lines) | Production-ready |
| `src/org/yawlfoundation/yawl/integration/mcp/resource/YawlResourceProvider.java` | Resources (specs, cases, work items) | Production-ready |
| `.claude/MCP-QUICKSTART.md` | Quick setup guide | Reference |
| `.claude/AGENT-INTEGRATION.md` | A2A integration patterns | Reference |

---

## Implementation Checklist

### Phase 1: Design (Complete ✓)
- [x] Architecture document (MCP_LLM_TOOL_DESIGN.md)
- [x] Code examples (MCP_WORKFLOW_TOOL_EXAMPLES.md)
- [x] PoC scenario (supply chain automation)

### Phase 2: Implementation (Ready)
- [ ] Identify 3-5 priority workflows
- [ ] Implement tool handlers using template
- [ ] Add to YawlToolSpecifications.createAll()
- [ ] Write unit tests
- [ ] Deploy locally and test with Claude Desktop

### Phase 3: Production
- [ ] Containerize MCP server (Docker)
- [ ] Deploy to Kubernetes
- [ ] Set up monitoring (Prometheus + Grafana)
- [ ] Configure rate limiting
- [ ] Document custom tools
- [ ] Train LLM team on system prompts

---

## Frequently Asked Questions

### Q: Do I need to implement the MCP server?
**A**: No! The YAWL MCP server is already production-ready. You just need to add custom workflow tools using the template in MCP_WORKFLOW_TOOL_EXAMPLES.md.

### Q: Can I use this with ChatGPT, Gemini, or other LLMs?
**A**: Yes! MCP is a standardized protocol. Works with Claude, ChatGPT, Gemini, Llama, and any LLM with MCP client support.

### Q: What about security?
**A**: Complete security design in MCP_LLM_TOOL_DESIGN.md Part 4:
- Input validation (type checking, XSS/SQL prevention)
- Rate limiting (per-client, per-tool)
- Output filtering (redact sensitive data)
- Audit logging (structured JSON logs)

### Q: How do I test this?
**A**: Use MCP_WORKFLOW_TOOL_EXAMPLES.md Section 4:
- Unit tests for validation
- Integration tests with real YAWL engine
- Manual testing with Claude Desktop

### Q: What's the performance impact?
**A**: Negligible. MCP adds ~5-10ms overhead. Tool execution time is dominated by YAWL workflow (typically 10-60 seconds).

### Q: Can I add more tools later?
**A**: Yes! Just add new tool definitions to YawlToolSpecifications.createAll(). No LLM prompt changes needed (much).

### Q: What's the error handling strategy?
**A**: See MCP_LLM_TOOL_DESIGN.md Part 3 (error recovery patterns). Tools return structured errors with specific error codes. LLMs retry intelligently based on error type.

### Q: Do I need to modify YAWL engine?
**A**: No! MCP server uses existing InterfaceB and InterfaceA APIs. No engine changes needed.

---

## Architectural Overview

```
┌─────────────────────────────────┐
│ LLM (Claude, ChatGPT, Gemini)   │
└────────────────┬────────────────┘
                 │ MCP Protocol (JSON over STDIO/HTTP)
                 │ "invoke tool: approve_purchase"
                 ▼
┌─────────────────────────────────────────────────────┐
│ YAWL MCP Server                                     │
│ ┌──────────────────────────────────────────────┐   │
│ │ Core Tools (15):                             │   │
│ │  - launch_case, cancel_case, etc.            │   │
│ │ Custom Tools (Your workflows):                │   │
│ │  - approve_purchase                          │   │
│ │  - select_vendor                             │   │
│ │  - compliance_check                          │   │
│ └──────────────────────────────────────────────┘   │
│                                                     │
│ ┌──────────────────────────────────────────────┐   │
│ │ Security & Observability:                    │   │
│ │  - Input validation (XSS/SQL prevention)     │   │
│ │  - Rate limiting (per-client, per-tool)      │   │
│ │  - Output filtering (redact sensitive data)  │   │
│ │  - Structured logging (JSON for ELK)         │   │
│ └──────────────────────────────────────────────┘   │
└────────────────┬─────────────────────────────────┘
                 │ REST/XML
                 ▼
┌─────────────────────────────────┐
│ YAWL Engine                     │
│ ┌─────────────────────────────┐ │
│ │ InterfaceB (Runtime):       │ │
│ │  - launchCase()             │ │
│ │  - getCaseState()           │ │
│ │  - getCaseData()            │ │
│ │  - completeWorkItem()       │ │
│ └─────────────────────────────┘ │
│ ┌─────────────────────────────┐ │
│ │ InterfaceA (Design-time):   │ │
│ │  - uploadSpecification()    │ │
│ │  - getSpecification()       │ │
│ └─────────────────────────────┘ │
└─────────────────────────────────┘
```

---

## Timeline & Effort Estimate

| Phase | Task | Effort | Timeline |
|-------|------|--------|----------|
| 1 | Identify 3-5 workflows | 1 day | Week 1 |
| 2 | Implement tool handlers | 3-5 days | Week 1-2 |
| 3 | Write unit tests | 2-3 days | Week 2 |
| 4 | Deploy to staging | 1 day | Week 2 |
| 5 | Integration test with Claude | 1 day | Week 3 |
| 6 | Production deployment | 2-3 days | Week 3-4 |
| 7 | Monitoring & alerting | 2 days | Week 4 |
| 8 | LLM team training | 1 day | Week 4 |

**Total**: ~3-4 weeks from design to production.

---

## Support & References

### For Architecture Questions
- **MCP_RESEARCH_SUMMARY.md** — Design rationale section
- **MCP_LLM_TOOL_DESIGN.md** — Complete architecture

### For Implementation Questions
- **MCP_WORKFLOW_TOOL_EXAMPLES.md** — Code templates
- `src/org/yawlfoundation/yawl/integration/mcp/YawlMcpServer.java` — Reference implementation

### For Deployment Questions
- **MCP_LLM_TOOL_DESIGN.md** Part 7 — Deployment options
- `docs/architecture/decisions/ADR-023-mcp-a2a-cicd-deployment.md` — CI/CD patterns

### For LLM Integration Questions
- **MCP_LLM_TOOL_DESIGN.md** Part 5 — Prompt engineering
- **MCP_WORKFLOW_TOOL_EXAMPLES.md** Section 5 — System prompts

### Official Specification
- https://spec.modelcontextprotocol.io/ — MCP 2025-11-25 spec

---

## Conclusion

This documentation provides everything needed to extend YAWL with LLM-callable workflow tools via MCP:

1. **Architecture** (MCP_LLM_TOOL_DESIGN.md) — Complete design
2. **Code** (MCP_WORKFLOW_TOOL_EXAMPLES.md) — Reusable templates
3. **Overview** (MCP_RESEARCH_SUMMARY.md) — Executive summary
4. **Index** (this file) — Navigation

**Status**: Ready for implementation. No blockers.

**Next**: Choose your first 3 workflows and start implementing!

---

**Questions?**
- Architecture: See MCP_LLM_TOOL_DESIGN.md
- Code: See MCP_WORKFLOW_TOOL_EXAMPLES.md
- Overview: See MCP_RESEARCH_SUMMARY.md

**Last Updated**: 2026-02-21
**Version**: 1.0
**YAWL Version**: 6.0.0
**MCP Spec**: 2025-11-25
**Java SDK**: v1.0.0-RC1
