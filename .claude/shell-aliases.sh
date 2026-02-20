#!/bin/bash
# YAWL Developer Shell Aliases - Source this in ~/.bashrc or ~/.zshrc
# Add this line to your shell config:
#   source /path/to/yawl/.claude/shell-aliases.sh

# Fast build shortcuts
alias yf='make fast'           # Compile (30s)
alias yb='make build'          # Build all (2m)
alias yt='make test'           # Run tests
alias yv='make verify'         # Verify blockers
alias yc='make clean'          # Clean

# Git shortcuts
alias ys='git s'               # Status
alias yl='git l'               # Log
alias yp='git psh'             # Push

# YAWL-specific commands
alias ystart='cat .claude/DX-QUICK-START.md | head -50'    # Show quick start
alias yref='cat .claude/QUICK-REFERENCE.txt'               # Show reference
alias yhelp='make help'                                    # Show help
alias ycheck='python3 scripts/verify-marketplace.py'       # Verify

# Function: Quick commit + push
ycp() {
    if [ -z "$1" ]; then
        echo "Usage: ycp 'commit message'"
        return 1
    fi
    git add -A
    git commit -m "$1"
    git push -u origin $(git rev-parse --abbrev-ref HEAD)
}

# Function: View YAWL architecture
yarch() {
    echo "YAWL v6.0.0 - Key Components:"
    echo "  TenantContext        - Multi-tenancy isolation"
    echo "  QuotaEnforcer        - Resource quota enforcement"
    echo "  Cloud SQL (CMEK)     - Encrypted database"
    echo "  Cloud Storage (CMEK) - Encrypted backups"
    echo ""
    echo "Documentation:"
    echo "  PRIVACY.md           - GDPR compliance"
    echo "  SLA.md               - Service guarantees"
    echo "  DPA.md               - Data processing"
    echo "  SUPPORT-POLICY.md    - Support terms"
}

# Function: Show tenant integration example
ytenancy() {
    cat << 'TENANT_EXAMPLE'
// Quick Tenant Integration Pattern:

TenantContext ctx = new TenantContext(customerId);
YEngine.setTenantContext(ctx);
try {
    ctx.registerCase(caseID);           // Register case
    String data = engine.getCaseData(caseID);  // Validates tenant
    // ... process data
} finally {
    YEngine.clearTenantContext();        // Clean up
}

// See TenantContext.java for full documentation
TENANT_EXAMPLE
}

# Function: Show quota integration example
yquota() {
    cat << 'QUOTA_EXAMPLE'
// Quick Quota Integration Pattern:

UsageMeter meter = new UsageMeter(credentialsPath, participantId);
try {
    // Throws IllegalStateException if quota exceeded
    meter.recordWorkflowUsage(
        customerId,
        entitlementId,
        workflowName,
        executionTimeMs,
        computeUnits
    );
} catch (IllegalStateException e) {
    // Quota exceeded - return 429
    return Response.status(429).entity("Quota exceeded").build();
}

// Limits: 8.3 hours/month, 10K compute units/month
// See UsageMeter.java for details
QUOTA_EXAMPLE
}

# Function: Show agent integration example
yagent() {
    cat << 'AGENT_EXAMPLE'
// Quick Agent Integration Pattern (A2A Protocol):

A2AClient agent = new A2AClient("approval-agent");
WorkflowResponse res = agent.invoke(
    new WorkflowRequest()
        .put("amount", 5000)
        .put("requestor", "alice@company.com")
);

if (res.getBoolean("approved")) {
    engine.completeWorkItem(workItem.getID(), res.getData());
} else {
    escalateToManual();
}

// See .claude/AGENT-INTEGRATION.md for full guide
AGENT_EXAMPLE
}

# Function: Show MCP quick setup
yamcp() {
    cat << 'MCP_EXAMPLE'
// Quick MCP Server Setup:

YawlMcpServer mcp = new YawlMcpServer(9000);

mcp.registerTool("list_cases", (args) -> {
    return engine.getCaseList();
});

mcp.registerTool("complete_task", (args) -> {
    return engine.completeWorkItem(
        args.getString("task_id"),
        args.getMap("data")
    );
});

mcp.start();  // Server running on :9000

// Now Claude can call YAWL via MCP protocol
// See .claude/AUTONOMICS-PATTERNS.md for patterns
MCP_EXAMPLE
}

# Function: Show A2A quick setup
yaa2a() {
    cat << 'A2A_EXAMPLE'
// Quick A2A Server Setup:

YawlA2AServer server = new YawlA2AServer(9001);

server.registerAgent("approval-agent", (req) -> {
    double amount = req.getDouble("amount");

    if (amount < 10000) {
        return WorkflowResponse.success()
            .put("approved", true)
            .put("confidence", 0.99);
    } else {
        return WorkflowResponse.success()
            .put("approved", false)
            .put("confidence", 0.95);
    }
});

server.start();  // Server running on :9001

// See .claude/AGENT-INTEGRATION.md for full guide
A2A_EXAMPLE
}

# Function: Show autonomics architecture
yauto() {
    echo "YAWL v6.0.0 - Autonomics Architecture:"
    echo ""
    echo "Components:"
    echo "  YawlMcpServer        - Model Context Protocol integration"
    echo "  YawlA2AServer        - Agent-to-Agent communication"
    echo "  A2AClient            - Client for invoking agents"
    echo "  AgentMetrics         - Track agent performance"
    echo "  AgentHealthCheck     - Monitor agent degradation"
    echo ""
    echo "Patterns:"
    echo "  Agent-Driven Approval - Auto-approve with escalation"
    echo "  Multi-Agent          - Parallel agent orchestration"
    echo "  Agent Chain          - Sequential agent composition"
    echo "  Feedback Loop        - Agent learning from feedback"
    echo ""
    echo "Documentation:"
    echo "  .claude/AUTONOMICS-PATTERNS.md   - 7 production patterns"
    echo "  .claude/AGENT-INTEGRATION.md     - 5-minute setup guide"
}

# Echo setup confirmation
echo "✅ YAWL shell aliases loaded!"
echo "   Run: yhelp     - Show Make targets"
echo "   Run: yref      - Show quick reference"
echo "   Run: ystart    - Show quick start"
echo "   Run: ytenancy  - Show tenant example"
echo "   Run: yquota    - Show quota example"
echo "   Run: yagent    - Show agent integration"
echo "   Run: yamcp     - Show MCP setup"
echo "   Run: yaa2a     - Show A2A setup"
echo "   Run: yauto     - Show autonomics architecture"
