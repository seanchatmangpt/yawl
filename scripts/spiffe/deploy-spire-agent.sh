#!/bin/bash
# Deploy SPIRE Agent for YAWL Workload Identity
#
# This script installs and configures SPIRE Agent to provide SPIFFE
# workload identity to YAWL services. The agent runs as a systemd service
# and provides SVIDs via Unix domain socket.
#
# Prerequisites:
#   - Linux system with systemd
#   - Root or sudo access
#   - Network access to SPIRE Server
#
# Usage:
#   sudo ./deploy-spire-agent.sh <spire-server-address> <join-token>
#
# Example:
#   sudo ./deploy-spire-agent.sh spire-server.yawl.cloud a1b2c3d4-e5f6-7890-abcd-ef1234567890

set -euo pipefail

SPIRE_VERSION="1.9.0"
SPIRE_SERVER_ADDRESS="${1:-spire-server.yawl.cloud}"
JOIN_TOKEN="${2:-}"

if [[ $EUID -ne 0 ]]; then
   echo "This script must be run as root"
   exit 1
fi

echo "=== SPIRE Agent Deployment for YAWL ==="
echo "SPIRE Version: $SPIRE_VERSION"
echo "Server Address: $SPIRE_SERVER_ADDRESS"
echo ""

# Create directories
echo "[1/8] Creating directories..."
mkdir -p /opt/spire/bin
mkdir -p /etc/spire
mkdir -p /var/lib/spire/agent
mkdir -p /var/log/spire
mkdir -p /run/spire/sockets

# Download SPIRE
echo "[2/8] Downloading SPIRE $SPIRE_VERSION..."
cd /tmp
wget -q "https://github.com/spiffe/spire/releases/download/v${SPIRE_VERSION}/spire-${SPIRE_VERSION}-linux-amd64-musl.tar.gz"
tar xzf "spire-${SPIRE_VERSION}-linux-amd64-musl.tar.gz"
cp "spire-${SPIRE_VERSION}/bin/spire-agent" /opt/spire/bin/
chmod +x /opt/spire/bin/spire-agent

# Create configuration
echo "[3/8] Creating agent configuration..."
cat > /etc/spire/agent.conf <<EOF
agent {
    trust_domain = "yawl.cloud"
    data_dir = "/var/lib/spire/agent"
    log_file = "/var/log/spire/agent.log"
    log_level = "INFO"
    server_address = "$SPIRE_SERVER_ADDRESS"
    server_port = 8081
    socket_path = "/run/spire/sockets/agent.sock"
}

plugins {
    NodeAttestor "join_token" {
        plugin_data {}
    }

    WorkloadAttestor "unix" {
        plugin_data {}
    }

    WorkloadAttestor "docker" {
        plugin_data {
            docker_socket_path = "/var/run/docker.sock"
        }
    }

    KeyManager "disk" {
        plugin_data {
            directory = "/var/lib/spire/agent/keys"
        }
    }
}

health_checks {
    listener_enabled = true
    bind_address = "127.0.0.1"
    bind_port = 8080
    live_path = "/live"
    ready_path = "/ready"
}
EOF

# Create systemd service
echo "[4/8] Creating systemd service..."
cat > /etc/systemd/system/spire-agent.service <<EOF
[Unit]
Description=SPIRE Agent
After=network.target
Wants=network.target

[Service]
Type=simple
User=root
Group=root
ExecStart=/opt/spire/bin/spire-agent run -config /etc/spire/agent.conf
Restart=on-failure
RestartSec=5s
LimitNOFILE=65536

[Install]
WantedBy=multi-user.target
EOF

# Set permissions
echo "[5/8] Setting permissions..."
chmod 755 /run/spire/sockets
chmod 644 /etc/spire/agent.conf

# Enable and start service
echo "[6/8] Enabling and starting SPIRE Agent..."
systemctl daemon-reload
systemctl enable spire-agent
systemctl start spire-agent

# Wait for socket
echo "[7/8] Waiting for Workload API socket..."
for i in {1..30}; do
    if [[ -S /run/spire/sockets/agent.sock ]]; then
        echo "Socket ready!"
        break
    fi
    echo "Waiting... ($i/30)"
    sleep 1
done

if [[ ! -S /run/spire/sockets/agent.sock ]]; then
    echo "ERROR: Socket not created after 30 seconds"
    echo "Check logs: journalctl -u spire-agent -n 50"
    exit 1
fi

# Join with token if provided
if [[ -n "$JOIN_TOKEN" ]]; then
    echo "[8/8] Joining SPIRE Server with token..."
    /opt/spire/bin/spire-agent api join -socketPath /run/spire/sockets/agent.sock -token "$JOIN_TOKEN" || {
        echo "WARNING: Join failed. Agent may need manual registration."
    }
else
    echo "[8/8] Skipping join - no token provided"
    echo "To join manually, run:"
    echo "  spire-agent api join -socketPath /run/spire/sockets/agent.sock -token <token>"
fi

echo ""
echo "=== SPIRE Agent Deployment Complete ==="
echo "Socket: /run/spire/sockets/agent.sock"
echo "Status: systemctl status spire-agent"
echo "Logs: journalctl -u spire-agent -f"
echo ""
echo "Set YAWL environment variable:"
echo "  export SPIFFE_ENDPOINT_SOCKET=unix:///run/spire/sockets/agent.sock"
echo ""
echo "Test with YAWL:"
echo "  java -cp yawl.jar org.yawlfoundation.yawl.integration.spiffe.SpiffeWorkloadApiClient"
