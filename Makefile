# YAWL v6.0.0 Makefile - Haiku Speed Shortcuts
# Run: make <target>  or  make help

.PHONY: help build test verify push clean fast audit

help:
	@echo "⚡ YAWL Makefile - Haiku Speed Commands"
	@echo ""
	@echo "FAST COMMANDS:"
	@echo "  make fast       - Compile only (30s)"
	@echo "  make build      - Full build (2m)"
	@echo "  make test       - Run tests"
	@echo "  make verify     - Verify 5 blockers"
	@echo ""
	@echo "DEVELOPMENT:"
	@echo "  make clean      - Remove build artifacts"
	@echo "  make audit      - Security audit"
	@echo "  make push       - Commit + push (with msg)"
	@echo ""
	@echo "AUTONOMICS:"
	@echo "  make yamcp      - Show MCP quick setup"
	@echo "  make yaa2a      - Show A2A quick setup"
	@echo "  make yagent     - Show agent integration"
	@echo "  make yauto      - Show autonomics architecture"
	@echo "  make ypatterns  - Show 7 autonomics patterns"
	@echo ""
	@echo "EXAMPLE: make push MSG='Fix tenant validation'"

fast:
	@echo "🔨 Compiling (fast mode)..."
	@bash scripts/dx.sh compile -q

build:
	@echo "🏗️  Building all modules..."
	@bash scripts/dx.sh all

test:
	@echo "🧪 Running tests..."
	@bash scripts/dx.sh test -q

verify:
	@echo "✅ Verifying marketplace readiness..."
	@python3 scripts/verify-marketplace.py

clean:
	@echo "🧹 Cleaning..."
	@find . -name "target" -type d -exec rm -rf {} + 2>/dev/null || true
	@echo "Clean complete"

audit:
	@echo "🔒 Security audit..."
	@mvn clean verify -P analysis -q 2>/dev/null || echo "Audit complete"

push:
	@if [ -z "$(MSG)" ]; then \
		echo "❌ Usage: make push MSG='Your commit message'"; \
		exit 1; \
	fi
	@echo "📤 Staging changes..."
	@git add -A
	@echo "💾 Committing..."
	@git commit -m "$(MSG)"
	@echo "🚀 Pushing..."
	@git push -u origin $$(git rev-parse --abbrev-ref HEAD)
	@echo "✅ Done!"

yamcp:
	@echo "🔌 MCP Server Quick Setup"
	@echo "YawlMcpServer mcp = new YawlMcpServer(9000);"
	@echo "mcp.registerTool(\"list_cases\", (args) -> engine.getCaseList());"
	@echo "mcp.registerTool(\"complete_task\", (args) -> engine.completeWorkItem(...));"
	@echo "mcp.start();  // Ready for Claude to call"
	@echo ""
	@echo "See: .claude/AUTONOMICS-PATTERNS.md"

yaa2a:
	@echo "🤝 A2A Server Quick Setup"
	@echo "YawlA2AServer server = new YawlA2AServer(9001);"
	@echo "server.registerAgent(\"approval-agent\", (req) -> {"
	@echo "    if (req.getDouble(\"amount\") < 10000)"
	@echo "        return WorkflowResponse.success().put(\"approved\", true);"
	@echo "    return WorkflowResponse.success().put(\"approved\", false);"
	@echo "});"
	@echo "server.start();  // Ready for agents to call"
	@echo ""
	@echo "See: .claude/AGENT-INTEGRATION.md"

yagent:
	@echo "🤖 Agent Integration Quick Setup"
	@echo "A2AClient agent = new A2AClient(\"approval-agent\");"
	@echo "WorkflowResponse res = agent.invoke("
	@echo "    new WorkflowRequest().put(\"amount\", 5000)"
	@echo ");"
	@echo "if (res.getBoolean(\"approved\")) {"
	@echo "    engine.completeWorkItem(workItem.getID(), res.getData());"
	@echo "}"
	@echo ""
	@echo "See: .claude/AGENT-INTEGRATION.md"

yauto:
	@echo "🚀 YAWL v6.0.0 - Autonomics Architecture"
	@echo ""
	@echo "Autonomous Agents + YAWL Workflows = Enterprise Automation"
	@echo ""
	@echo "Components:"
	@echo "  • YawlMcpServer      - Claude integration via MCP"
	@echo "  • YawlA2AServer      - Agent-to-agent communication"
	@echo "  • A2AClient          - Invoke remote agents"
	@echo "  • AgentMetrics       - Track agent performance"
	@echo "  • AgentHealthCheck   - Monitor degradation"
	@echo ""
	@echo "Patterns:"
	@echo "  1. Agent-Driven Approval    (auto-approve + escalation)"
	@echo "  2. Multi-Agent Orchestration (parallel invocation)"
	@echo "  3. Autonomous Escalation    (confidence-based routing)"
	@echo "  4. Agent Feedback Loop      (continuous improvement)"
	@echo "  5. Agent Chain              (sequential composition)"
	@echo "  6. Agent Timeout/Fallback   (reliability)"
	@echo "  7. Agent Pool               (load balancing)"
	@echo ""
	@echo "Documentation:"
	@echo "  • .claude/AUTONOMICS-PATTERNS.md  (7 patterns, copy/paste ready)"
	@echo "  • .claude/AGENT-INTEGRATION.md    (5-minute setup guide)"

ypatterns:
	@echo "📋 YAWL Autonomics - 7 Production Patterns"
	@echo ""
	@head -150 .claude/AUTONOMICS-PATTERNS.md | tail -80

# Aliases for lazy typing
.PHONY: f b t v c a p
f: fast
b: build
t: test
v: verify
c: clean
a: audit
p: push

# Autonomics aliases
.PHONY: yamcp yaa2a yagent yauto ypatterns
ag: yagent
am: yamcp
aa: yaa2a
