#!/usr/bin/env bash
# Run PM4Py A2A agent (HTTP transport).
# Agent card: http://localhost:9092/.well-known/agent-card.json
set -euo pipefail
cd "$(dirname "$0")"
exec uv run a2a_agent.py
