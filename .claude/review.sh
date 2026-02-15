#!/bin/bash
# YAWL Review Skill Wrapper
# Usage: /yawl-review [path] [--hyper-standards]

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "${SCRIPT_DIR}/skills/yawl-review.sh" "$@"
