#!/usr/bin/env bash
# =============================================================================
# Order Fulfillment Simulation - Launch case and validate 100% automation
# =============================================================================
# Prerequisites:
#   - YAWL engine running (production profile or external)
#   - Party agents running (docker-compose simulation or separate processes)
#   - ZAI_API_KEY set for agents (not needed for launcher)
#
# Usage:
#   ./scripts/run-orderfulfillment-simulation.sh
#
# With process mining after success (PM4Py A2A agent must be running):
#   RUN_PROCESS_MINING=true ./scripts/run-orderfulfillment-simulation.sh
#
# With engine on host:
#   YAWL_ENGINE_URL=http://localhost:8888/yawl ./scripts/run-orderfulfillment-simulation.sh
#
# With Docker full stack (engine + agents):
#   docker compose -f docker-compose.yml -f docker-compose.simulation.yml \
#     --profile production --profile simulation up -d
#   # Wait for engine healthy, then:
#   YAWL_ENGINE_URL=http://localhost:8888/yawl ./scripts/run-orderfulfillment-simulation.sh
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_DIR"

export YAWL_ENGINE_URL="${YAWL_ENGINE_URL:-http://localhost:8888/yawl}"
export YAWL_USERNAME="${YAWL_USERNAME:-admin}"
export YAWL_PASSWORD="${YAWL_PASSWORD:-YAWL}"
export UPLOAD_SPEC="${UPLOAD_SPEC:-true}"
export TIMEOUT_SEC="${TIMEOUT_SEC:-600}"

# Resolve engine host for connectivity check (strip path)
ENGINE_HOST="${YAWL_ENGINE_URL%%/*}"
ENGINE_HOST="${ENGINE_HOST#*://}"
ENGINE_HOST="${ENGINE_HOST%%:*}"
ENGINE_PORT="${YAWL_ENGINE_URL#*://}"
ENGINE_PORT="${ENGINE_PORT#*:}"
ENGINE_PORT="${ENGINE_PORT%%/*}"
ENGINE_PORT="${ENGINE_PORT:-8888}"

echo "Order Fulfillment Simulation"
echo "  Engine: $YAWL_ENGINE_URL"
echo "  Upload spec: $UPLOAD_SPEC"
echo "  Timeout: ${TIMEOUT_SEC}s"
echo ""

# Optional: check engine reachability (best-effort)
if command -v curl &>/dev/null; then
    if curl -sf --connect-timeout 5 "${YAWL_ENGINE_URL%%/yawl*}/" &>/dev/null; then
        echo "Engine reachable."
    else
        echo "Warning: Engine may not be reachable at $YAWL_ENGINE_URL"
        echo "  Ensure engine is running and agents are started."
    fi
fi

if [ ! -d "classes" ]; then
    echo "Building..."
    ant -f build/build.xml compile -q
fi

echo "Launching case and waiting for completion..."
if ant -f build/build.xml run-orderfulfillment-launcher; then
    echo ""
    echo "SUCCESS: Order fulfillment case completed (100% agent automation validated)."

    # Optional: run process mining (export XES, call PM4Py A2A agent)
    if [ "${RUN_PROCESS_MINING:-false}" = "true" ]; then
        echo ""
        echo "Process mining (PM4Py)..."
        XES_PATH="${PROJECT_DIR}/orderfulfillment.xes"
        export OUTPUT_PATH="$XES_PATH"
        if ant -f build/build.xml run-export-xes -q 2>/dev/null; then
            PM4PY_URL="${PM4PY_AGENT_URL:-http://localhost:9092}"
            XES_INPUT="$XES_PATH"
            [ -d /workspace ] 2>/dev/null && XES_INPUT="/workspace/orderfulfillment.xes"
            if command -v curl &>/dev/null && command -v python3 &>/dev/null; then
                BODY=$(XES_INPUT="$XES_INPUT" python3 -c '
import json, os
payload = {"skill":"performance","xes_input":os.environ.get("XES_INPUT","")}
msg = {"jsonrpc":"2.0","id":1,"method":"message/send","params":{"message":{"role":"user","parts":[{"kind":"text","text":json.dumps(payload)}]}}}
print(json.dumps(msg))
' 2>/dev/null)
                if [ -n "$BODY" ]; then
                    RESP=$(curl -sf -X POST "$PM4PY_URL/" -H "Content-Type: application/json" -d "$BODY" 2>/dev/null) || true
                    [ -n "$RESP" ] && echo "  PM4Py: $(echo "$RESP" | head -c 400)..."
                fi
            fi
        else
            echo "  (XES export skipped - log gateway may need completed cases)"
        fi
    fi

    exit 0
else
    echo ""
    echo "FAILURE: Case did not complete within timeout or launcher error."
    exit 1
fi
