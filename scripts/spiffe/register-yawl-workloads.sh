#!/usr/bin/env bash
# Register YAWL Workloads with SPIRE Server
#
# This script creates SPIRE registration entries for all YAWL components,
# allowing them to receive SPIFFE identities.
#
# Prerequisites:
#   - SPIRE Server running and accessible
#   - Node already registered with SPIRE Server
#   - spire-server binary in PATH
#
# Usage:
#   ./register-yawl-workloads.sh <node-spiffe-id> <deployment-type>
#
# Deployment types:
#   - bare-metal: Unix UID/GID selectors
#   - docker: Docker label selectors
#   - kubernetes: K8s namespace/service account selectors
#
# Example:
#   ./register-yawl-workloads.sh spiffe://yawl.cloud/node/prod-1 docker

set -euo pipefail

NODE_SPIFFE_ID="${1:-spiffe://yawl.cloud/node/default}"
DEPLOYMENT_TYPE="${2:-bare-metal}"
TRUST_DOMAIN="yawl.cloud"
SVID_TTL="3600"

echo "=== YAWL Workload Registration ==="
echo "Trust Domain: $TRUST_DOMAIN"
echo "Node SPIFFE ID: $NODE_SPIFFE_ID"
echo "Deployment Type: $DEPLOYMENT_TYPE"
echo ""

# Helper function to create entry
create_entry() {
    local workload_name=$1
    local spiffe_path=$2
    shift 2
    local selectors=("$@")

    echo "Registering: spiffe://$TRUST_DOMAIN/$spiffe_path"

    selector_args=""
    for selector in "${selectors[@]}"; do
        selector_args="$selector_args -selector $selector"
    done

    spire-server entry create \
        -parentID "$NODE_SPIFFE_ID" \
        -spiffeID "spiffe://$TRUST_DOMAIN/$spiffe_path" \
        -ttl "$SVID_TTL" \
        $selector_args || {
            echo "WARNING: Failed to register $workload_name"
            return 1
        }

    echo "  ✓ Registered $workload_name"
    echo ""
}

# Registration based on deployment type
case "$DEPLOYMENT_TYPE" in
    bare-metal)
        echo "Registering for bare-metal deployment (Unix selectors)..."
        echo ""

        # YAWL Engine
        create_entry "YAWL Engine" "yawl-engine" \
            "unix:uid:1000" \
            "unix:gid:1000"

        # YAWL MCP Server
        create_entry "YAWL MCP Server" "mcp-server" \
            "unix:uid:1001" \
            "unix:gid:1001"

        # YAWL A2A Server
        create_entry "YAWL A2A Server" "a2a-server" \
            "unix:uid:1002" \
            "unix:gid:1002"

        # Generic Agent (can be instantiated multiple times)
        create_entry "YAWL Generic Agent" "agent/generic" \
            "unix:uid:1003" \
            "unix:gid:1003"

        # Order Processor Agent
        create_entry "Order Processor Agent" "agent/order-processor" \
            "unix:uid:1004" \
            "unix:gid:1004"

        # Inventory Agent
        create_entry "Inventory Agent" "agent/inventory" \
            "unix:uid:1005" \
            "unix:gid:1005"

        # Credit Checker Agent
        create_entry "Credit Checker Agent" "agent/credit-checker" \
            "unix:uid:1006" \
            "unix:gid:1006"
        ;;

    docker)
        echo "Registering for Docker deployment (container labels)..."
        echo ""

        # YAWL Engine
        create_entry "YAWL Engine" "yawl-engine" \
            "docker:label:app:yawl-engine" \
            "docker:label:component:engine"

        # YAWL MCP Server
        create_entry "YAWL MCP Server" "mcp-server" \
            "docker:label:app:yawl-mcp" \
            "docker:label:component:mcp-server"

        # YAWL A2A Server
        create_entry "YAWL A2A Server" "a2a-server" \
            "docker:label:app:yawl-a2a" \
            "docker:label:component:a2a-server"

        # Generic Agent
        create_entry "YAWL Generic Agent" "agent/generic" \
            "docker:label:app:yawl-agent" \
            "docker:label:capability:generic"

        # Order Processor Agent
        create_entry "Order Processor Agent" "agent/order-processor" \
            "docker:label:app:yawl-agent" \
            "docker:label:capability:order-processing"

        # Inventory Agent
        create_entry "Inventory Agent" "agent/inventory" \
            "docker:label:app:yawl-agent" \
            "docker:label:capability:inventory-check"

        # Credit Checker Agent
        create_entry "Credit Checker Agent" "agent/credit-checker" \
            "docker:label:app:yawl-agent" \
            "docker:label:capability:credit-check"
        ;;

    kubernetes)
        echo "Registering for Kubernetes deployment (namespace/service account)..."
        echo ""

        # YAWL Engine
        create_entry "YAWL Engine" "yawl-engine" \
            "k8s:ns:yawl" \
            "k8s:sa:yawl-engine" \
            "k8s:pod-label:app:yawl-engine"

        # YAWL MCP Server
        create_entry "YAWL MCP Server" "mcp-server" \
            "k8s:ns:yawl" \
            "k8s:sa:yawl-mcp-server" \
            "k8s:pod-label:app:yawl-mcp"

        # YAWL A2A Server
        create_entry "YAWL A2A Server" "a2a-server" \
            "k8s:ns:yawl" \
            "k8s:sa:yawl-a2a-server" \
            "k8s:pod-label:app:yawl-a2a"

        # Generic Agent
        create_entry "YAWL Generic Agent" "agent/generic" \
            "k8s:ns:yawl" \
            "k8s:sa:yawl-agent" \
            "k8s:pod-label:capability:generic"

        # Order Processor Agent
        create_entry "Order Processor Agent" "agent/order-processor" \
            "k8s:ns:yawl" \
            "k8s:sa:yawl-agent" \
            "k8s:pod-label:capability:order-processing"

        # Inventory Agent
        create_entry "Inventory Agent" "agent/inventory" \
            "k8s:ns:yawl" \
            "k8s:sa:yawl-agent" \
            "k8s:pod-label:capability:inventory-check"

        # Credit Checker Agent
        create_entry "Credit Checker Agent" "agent/credit-checker" \
            "k8s:ns:yawl" \
            "k8s:sa:yawl-agent" \
            "k8s:pod-label:capability:credit-check"
        ;;

    *)
        echo "ERROR: Unknown deployment type: $DEPLOYMENT_TYPE"
        echo "Valid types: bare-metal, docker, kubernetes"
        exit 1
        ;;
esac

echo "=== Registration Complete ==="
echo ""
echo "Verify entries:"
echo "  spire-server entry show"
echo ""
echo "Test workload identity:"
echo "  export SPIFFE_ENDPOINT_SOCKET=unix:///run/spire/sockets/agent.sock"
echo "  java -cp yawl.jar org.yawlfoundation.yawl.integration.spiffe.SpiffeWorkloadApiClient"
