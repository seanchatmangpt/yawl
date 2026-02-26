# Tutorials Index

Learning-oriented guides that take you through a concrete journey from start to accomplishment. Designed for newcomers or anyone learning an unfamiliar part of YAWL.

---

## Learning Path (Sequential)

Work through these in order for the best introduction to YAWL:

| # | Tutorial | What you accomplish |
|---|----------|---------------------|
| [01](01-build-yawl.md) | Build YAWL | Clone the repo, compile all 16 modules, verify the build is green |
| [02](02-understand-the-build.md) | Understand the Build | Navigate the Maven multi-module structure and shared-src strategy |
| [03](03-run-your-first-workflow.md) | Run Your First Workflow | Launch the engine, deploy a specification, execute and monitor a case |
| [04](04-write-a-yawl-specification.md) | Write a YAWL Specification | Design a multi-task workflow with conditions, flows, and splits |
| [05](05-call-yawl-rest-api.md) | Call the REST API | Drive a workflow case via HTTP/JSON with Interface B |
| [06](06-write-a-custom-work-item-handler.md) | Custom Work Item Handler | Extend the engine with domain-specific task execution logic |
| [07](07-docker-dev-environment.md) | Docker Dev Environment | Set up a fully containerized local development stack |
| [08](08-mcp-agent-integration.md) | MCP Agent Integration | Connect an LLM-powered AI agent to YAWL via the MCP server |
| [09](09-marketplace-quick-start.md) | Marketplace Quick Start | Deploy YAWL to a cloud marketplace (GCP/AWS/Azure) |
| [10](10-getting-started.md) | Getting Started (User Guide) | End-to-end user perspective: navigate the control panel and run cases |
| [11](11-grpo-workflow-generation.md) | GRPO Workflow Generation | Generate optimal workflows using Reinforcement Learning |
| [12](12-opensage-memory.md) | OpenSage Memory System | Configure and operate the persistent learning memory store |
| [13](13-virtual-threads-performance.md) | Virtual Threads Performance | Boost throughput with Java 25+ virtual thread pools |

## v6.0.0-GA Features

YAWL v6.0.0-GA introduces several major enhancements:
- **GRPO/RL**: Generate, Run, Optimize workflow patterns with Reinforcement Learning (`yawl-ggen` module)
- **MCP/A2A**: Enhanced autonomous agent integration via the Model Context Protocol
- **OpenSage**: Persistent learning system for workflow optimization
- **Java 25**: Full virtual thread support for improved concurrency
- **Performance**: 43+ workflow patterns with optimized execution

## Topic-Specific Entry Points

| Tutorial | When to use it |
|----------|----------------|
| [quick-start-users.md](quick-start-users.md) | 5-minute "does it work?" check for new installations |

---

**Version**: v6.0.0-GA (2026-02-26)

After completing the tutorials, continue to the **[How-To Guides](../how-to/index.md)** for task-specific instructions.
