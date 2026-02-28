#!/bin/bash

# Generate diagrams for YAWL v6.0.0
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DIAGRAMS_DIR="docs/v6/latest/diagrams"
TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Ensure diagrams directory exists
mkdir -p "$DIAGRAMS_DIR"

# Generate architecture diagram
log_info "Generating architecture diagram..."
cat > "$DIAGRAMS_DIR/architecture.md" << EOF
# YAWL v6.0.0 Architecture Diagram

*Generated at $TIMESTAMP*

## System Overview

\`\`\`mermaid
graph TB
    subgraph "YAWL v6.0.0"
        Y[YEngine] -->|creates| WI[YWorkItem]
        WI -->|processed by| NE[YNetRunner]
        NE -->|triggers| WE[WorkletExecution]
        WE -->|emits| E[YEvents]
        E -->|consumed by| M[YawlMcpServer]
        M -->|integrates with| A2A[YawlA2AServer]
    end
    
    subgraph "External Systems"
        A2A -->|API calls| EX[External Agents]
        M -->|notifications| NT[Notification System]
    end
    
    subgraph "Storage"
        WI -->|stored in| DB[(Database)]
        E -->|logged to| LG[(Logger)]
    end
\`\`\`

## Data Flow

\`\`\`mermaid
flowchart LR
    Client -->|HTTP/JSON| YawlMcpServer
    YawlMcpServer -->|process| YEngine
    YEngine -->|create| YWorkItem
    YWorkItem -->|dispatch| YNetRunner
    YNetRunner -->|execute| WorkletExecution
    WorkletExecution -->|event| YEvents
    YEvents -->|notify| Client
\`\`\`
