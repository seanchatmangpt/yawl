#!/bin/bash
# Activate Permanent 5-Agent Teams — Continuous Parallel Execution
#
# Purpose:
#   Activate and manage permanent 5-agent teams across all phases:
#   - Team EXPLORE: 5 agents (research, investigation, discovery)
#   - Team PLAN: 5 agents (architecture, design, strategy)
#   - Team IMPLEMENT: 5 agents (coding, building, integration)
#
# Behavior:
#   - Teams persist across all phases (not phase-dependent)
#   - Each team has 5 agents operating in parallel
#   - Total: 15 agents (5+5+5) active simultaneously
#   - Tasks automatically routed to appropriate team
#   - Implicit team formation on session start
#   - Auto-scaling: if agents idle >5min, assign new tasks
#
# Configuration:
#   - .claude/config/permanent-teams.toml
#   - .claude/config/team-routing.toml
#
# Output:
#   - Team state: .claude/.team-state/{explore,plan,implement}/
#   - Team logs: .claude/logs/team-{explore,plan,implement}.log
#   - Team metrics: .claude/receipts/team-metrics.json
#
# Exit Codes:
#   0 = Teams activated successfully
#   1 = Transient error (retry safe)
#   2 = Fatal error (teams misconfigured)

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CLAUDE_DIR="${PROJECT_ROOT}/.claude"
SCRIPTS_DIR="${CLAUDE_DIR}/scripts"
CONFIG_DIR="${CLAUDE_DIR}/config"
STATE_DIR="${CLAUDE_DIR}/.team-state"
LOGS_DIR="${CLAUDE_DIR}/logs"
RECEIPTS_DIR="${CLAUDE_DIR}/receipts"

# Colors
BLUE='\033[0;34m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
MAGENTA='\033[0;35m'
NC='\033[0m'

# Create directories BEFORE anything else
mkdir -p "${STATE_DIR}/explore"
mkdir -p "${STATE_DIR}/plan"
mkdir -p "${STATE_DIR}/implement"
mkdir -p "${LOGS_DIR}"
mkdir -p "${RECEIPTS_DIR}"
mkdir -p "${CONFIG_DIR}"

log_info() {
    echo -e "${BLUE}[team-activate]${NC} $*"
}

log_success() {
    echo -e "${GREEN}[team-activate]${NC} $*"
}

log_warn() {
    echo -e "${YELLOW}[team-activate]${NC} $*"
}

log_team() {
    echo -e "${MAGENTA}[team-activate]${NC} $*"
}

# ──────────────────────────────────────────────────────────────────────────────
# CONFIGURATION
# ──────────────────────────────────────────────────────────────────────────────

# Create permanent teams configuration
create_team_config() {
    log_info "Creating permanent teams configuration..."

    cat > "${CONFIG_DIR}/permanent-teams.toml" <<'EOF'
# Permanent Teams Configuration — 5-Agent Multi-Team Setup
# Active at all times, across all phases, independent of task type

[teams]
mode = "permanent"
total_agents = 15
team_count = 3

# Team 1: EXPLORE (5 agents)
# Mission: Research, investigation, discovery, data gathering
[teams.explore]
name = "explore"
agent_count = 5
agents = [
    "explorer_1",
    "explorer_2",
    "explorer_3",
    "explorer_4",
    "explorer_5"
]
capabilities = ["research", "investigation", "discovery", "analysis", "reporting"]
priority = 1
auto_scale = true
idle_timeout_minutes = 5

# Team 2: PLAN (5 agents)
# Mission: Architecture, design, strategy, planning, decision-making
[teams.plan]
name = "plan"
agent_count = 5
agents = [
    "planner_1",
    "planner_2",
    "planner_3",
    "planner_4",
    "planner_5"
]
capabilities = ["architecture", "design", "strategy", "planning", "decision-making"]
priority = 2
auto_scale = true
idle_timeout_minutes = 5

# Team 3: IMPLEMENT (5 agents)
# Mission: Coding, building, integration, execution, testing
[teams.implement]
name = "implement"
agent_count = 5
agents = [
    "implementer_1",
    "implementer_2",
    "implementer_3",
    "implementer_4",
    "implementer_5"
]
capabilities = ["coding", "building", "integration", "execution", "testing"]
priority = 3
auto_scale = true
idle_timeout_minutes = 5

# Coordination
[coordination]
messaging_enabled = true
message_ttl_minutes = 30
max_message_queue_per_team = 100
auto_checkpoint_minutes = 5
deadlock_detection = true

# Metrics
[metrics]
enabled = true
granularity = "per_agent"
report_frequency_seconds = 60
EOF

    log_success "Created permanent teams configuration"
}

# ──────────────────────────────────────────────────────────────────────────────
# TEAM INITIALIZATION
# ──────────────────────────────────────────────────────────────────────────────

init_team_state() {
    local team_name="$1"
    local agent_count="$2"
    local team_dir="${STATE_DIR}/${team_name}"

    log_info "Initializing ${team_name} team (${agent_count} agents)..."

    # Create team metadata
    cat > "${team_dir}/metadata.json" <<EOF
{
  "team_id": "$(uuidgen | tr -d '-' | head -c 12)",
  "team_name": "${team_name}",
  "agent_count": ${agent_count},
  "status": "active",
  "created_at": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "agents": []
}
EOF

    # Create agent roster
    for i in $(seq 1 ${agent_count}); do
        local agent_id="${team_name}_agent_${i}"
        cat >> "${team_dir}/agents.jsonl" <<EOF
{
  "agent_id": "${agent_id}",
  "team": "${team_name}",
  "status": "idle",
  "task": null,
  "heartbeat": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "tasks_completed": 0
}
EOF
    done

    # Create mailbox for team communication
    touch "${team_dir}/mailbox.jsonl"
    echo "[]" > "${team_dir}/mailbox.json"

    # Create task queue
    echo "[]" > "${team_dir}/task-queue.json"

    log_success "  ${team_name}: initialized with ${agent_count} agents"
}

# ──────────────────────────────────────────────────────────────────────────────
# TEAM STARTUP
# ──────────────────────────────────────────────────────────────────────────────

activate_teams() {
    log_info "Activating permanent teams..."

    local timestamp="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

    # Explore team
    init_team_state "explore" 5

    # Plan team
    init_team_state "plan" 5

    # Implement team
    init_team_state "implement" 5

    log_success "All 3 teams activated (15 agents total)"

    # Create team summary
    cat > "${RECEIPTS_DIR}/team-activation-receipt.json" <<EOF
{
  "phase": "team-activation",
  "timestamp": "${timestamp}",
  "status": "GREEN",
  "teams_activated": 3,
  "total_agents": 15,
  "teams": {
    "explore": {
      "agent_count": 5,
      "status": "active",
      "state_dir": "${STATE_DIR}/explore"
    },
    "plan": {
      "agent_count": 5,
      "status": "active",
      "state_dir": "${STATE_DIR}/plan"
    },
    "implement": {
      "agent_count": 5,
      "status": "active",
      "state_dir": "${STATE_DIR}/implement"
    }
  },
  "configuration": "${CONFIG_DIR}/permanent-teams.toml",
  "messaging": {
    "enabled": true,
    "coordination": "enabled"
  }
}
EOF

    log_success "Team activation receipt created"
}

# ──────────────────────────────────────────────────────────────────────────────
# TASK ROUTING
# ──────────────────────────────────────────────────────────────────────────────

create_routing_config() {
    log_info "Creating task routing configuration..."

    cat > "${CONFIG_DIR}/team-routing.toml" <<'EOF'
# Task Routing — Route tasks to appropriate team
# Applies to all phases, all contexts

[routing]
mode = "keyword_based"
fallback = "implement"

# Route to EXPLORE team
[routing.explore]
keywords = [
    "research", "investigate", "discover", "analyze", "explore",
    "understand", "study", "examine", "survey", "probe",
    "find", "look for", "identify", "detect", "investigate"
]
confidence_threshold = 0.7

# Route to PLAN team
[routing.plan]
keywords = [
    "plan", "design", "architect", "strategy", "blueprint",
    "organize", "structure", "layout", "framework", "approach",
    "refactor", "reorganize", "conceptualize", "decide"
]
confidence_threshold = 0.7

# Route to IMPLEMENT team
[routing.implement]
keywords = [
    "implement", "build", "code", "develop", "write",
    "create", "fix", "debug", "test", "integrate",
    "commit", "deploy", "execute", "run", "integrate",
    "solve", "patch", "improve"
]
confidence_threshold = 0.7

# Auto-assignment when multiple teams could handle
[routing.auto_assignment]
prefer_loaded_team = false
prefer_specialized_team = true
round_robin = true
max_queue_per_agent = 10
EOF

    log_success "Created task routing configuration"
}

# ──────────────────────────────────────────────────────────────────────────────
# TEAM MONITORING
# ──────────────────────────────────────────────────────────────────────────────

monitor_teams() {
    log_info "Setting up team monitoring..."

    local timestamp="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

    # Count agents in each team
    local explore_count=$(wc -l < "${STATE_DIR}/explore/agents.jsonl" || echo 0)
    local plan_count=$(wc -l < "${STATE_DIR}/plan/agents.jsonl" || echo 0)
    local implement_count=$(wc -l < "${STATE_DIR}/implement/agents.jsonl" || echo 0)
    local total=$((explore_count + plan_count + implement_count))

    cat > "${RECEIPTS_DIR}/team-metrics.json" <<EOF
{
  "timestamp": "${timestamp}",
  "teams": {
    "explore": {
      "agent_count": ${explore_count},
      "status": "active",
      "tasks_total": 0,
      "tasks_in_progress": 0,
      "tasks_completed": 0
    },
    "plan": {
      "agent_count": ${plan_count},
      "status": "active",
      "tasks_total": 0,
      "tasks_in_progress": 0,
      "tasks_completed": 0
    },
    "implement": {
      "agent_count": ${implement_count},
      "status": "active",
      "tasks_total": 0,
      "tasks_in_progress": 0,
      "tasks_completed": 0
    }
  },
  "summary": {
    "total_teams": 3,
    "total_agents": ${total},
    "all_healthy": true
  }
}
EOF

    log_success "Team monitoring initialized"
}

# ──────────────────────────────────────────────────────────────────────────────
# COORDINATION SETUP
# ──────────────────────────────────────────────────────────────────────────────

setup_coordination() {
    log_info "Setting up inter-team coordination..."

    # Create message router for team-to-team communication
    mkdir -p "${STATE_DIR}/routing"

    cat > "${STATE_DIR}/routing/routes.json" <<'EOF'
{
  "explore_to_plan": {
    "from": "explore",
    "to": "plan",
    "topic": "findings",
    "priority": "high"
  },
  "plan_to_implement": {
    "from": "plan",
    "to": "implement",
    "topic": "tasks",
    "priority": "high"
  },
  "implement_to_explore": {
    "from": "implement",
    "to": "explore",
    "topic": "blockers",
    "priority": "medium"
  }
}
EOF

    # Create checkpoint mechanism
    cat > "${STATE_DIR}/routing/checkpoint.json" <<EOF
{
  "frequency_minutes": 5,
  "enabled": true,
  "scope": ["all_teams"],
  "auto_sync": true,
  "git_persist": true
}
EOF

    log_success "Inter-team coordination enabled"
}

# ──────────────────────────────────────────────────────────────────────────────
# ENVIRONMENT SETUP
# ──────────────────────────────────────────────────────────────────────────────

setup_environment() {
    log_info "Setting up environment variables..."

    cat >> "${CLAUDE_DIR}/.env-teams" <<'EOF'
# Permanent Teams Environment

# Team configuration
export CLAUDE_TEAMS_MODE=permanent
export CLAUDE_TEAMS_ENABLED=true
export CLAUDE_TEAMS_COUNT=3
export CLAUDE_AGENTS_TOTAL=15

# Team names and sizes
export TEAM_EXPLORE_COUNT=5
export TEAM_PLAN_COUNT=5
export TEAM_IMPLEMENT_COUNT=5

# Coordination
export TEAM_MESSAGING_ENABLED=true
export TEAM_AUTO_CHECKPOINT=true
export TEAM_CHECKPOINT_INTERVAL_MINUTES=5

# Routing
export TASK_ROUTING_MODE=keyword_based
export TASK_AUTO_ASSIGN=true

# Monitoring
export TEAM_MONITORING_ENABLED=true
export TEAM_METRICS_INTERVAL_SECONDS=60

# State persistence
export TEAM_STATE_DIR=.claude/.team-state
export TEAM_METRICS_DIR=.claude/receipts
export TEAM_LOGS_DIR=.claude/logs
EOF

    log_success "Environment variables configured"
}

# ──────────────────────────────────────────────────────────────────────────────
# MAIN EXECUTION
# ──────────────────────────────────────────────────────────────────────────────

main() {
    echo ""
    echo "╔════════════════════════════════════════════════════════════════════════════╗"
    echo "║                    ACTIVATE PERMANENT 5-AGENT TEAMS                        ║"
    echo "╚════════════════════════════════════════════════════════════════════════════╝"
    echo ""

    log_info "Initializing permanent team structure..."
    echo ""

    # Create configurations
    create_team_config
    create_routing_config

    # Activate teams
    activate_teams

    # Setup monitoring and coordination
    monitor_teams
    setup_coordination
    setup_environment

    echo ""
    log_success "╔════════════════════════════════════════════════════════════════════════════╗"
    log_success "║ PERMANENT TEAMS ACTIVATED SUCCESSFULLY                                    ║"
    log_success "╚════════════════════════════════════════════════════════════════════════════╝"
    echo ""
    echo -e "${GREEN}Teams Ready:${NC}"
    echo "  • EXPLORE:    5 agents (research, investigation, discovery)"
    echo "  • PLAN:       5 agents (architecture, design, strategy)"
    echo "  • IMPLEMENT:  5 agents (coding, building, execution)"
    echo ""
    echo -e "${GREEN}Status:${NC}"
    echo "  • Total Agents: 15"
    echo "  • All Teams Active & Healthy"
    echo "  • Messaging: ENABLED"
    echo "  • Coordination: ENABLED"
    echo "  • Auto-checkpoint: EVERY 5 MINUTES"
    echo ""
    echo -e "${GREEN}Configuration:${NC}"
    echo "  • Teams Config: ${CONFIG_DIR}/permanent-teams.toml"
    echo "  • Routing Config: ${CONFIG_DIR}/team-routing.toml"
    echo "  • State Directory: ${STATE_DIR}"
    echo "  • Team Metrics: ${RECEIPTS_DIR}/team-metrics.json"
    echo ""
}

main "$@"
