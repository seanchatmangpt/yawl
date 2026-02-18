#!/usr/bin/env bash
# ==========================================================================
# install-mvnd.sh — Install Maven Daemon (mvnd) for faster builds
#
# Downloads and configures mvnd for significantly faster Maven builds.
#
# Usage:
#   bash scripts/install-mvnd.sh              # Install mvnd
#   bash scripts/install-mvnd.sh --check      # Check if already installed
#   bash scripts/install-mvnd.sh --remove     # Remove mvnd
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

MVND_VERSION="0.9.1"
MVND_URL="https://repo1.maven.org/maven2/org/apache/maven/mvnd/${MVND_VERSION}/mvnd-${MVND_VERSION}-macosx-amd64.tar.gz"
MVND_DIR="$HOME/.mvnd/mvnd-${MVND_VERSION}"
MVND_BIN="$MVND_DIR/bin/mvnd"

# Parse arguments
CHECK_ONLY=0
REMOVE=0

while [[ $# -gt 0 ]]; do
    case "$1" in
        --check) CHECK_ONLY=1; shift ;;
        --remove) REMOVE=1; shift ;;
        -h|--help)
            echo "Usage: $0 [--check] [--remove] [-h]"
            echo "  --check    : Check if mvnd is installed"
            echo "  --remove   : Remove mvnd installation"
            echo "  -h         : Show this help"
            exit 0
            ;;
        *) echo "Unknown arg: $1"; exit 1 ;;
    esac
done

# Check if mvnd is already installed
check_mvnd() {
    if [[ -f "$MVND_BIN" ]]; then
        echo "✓ mvnd $MVND_VERSION is already installed at: $MVND_BIN"
        echo "✓ Version: $(mvnd --version)"
        return 0
    else
        echo "✗ mvnd is not installed"
        return 1
    fi
}

# Install mvnd
install_mvnd() {
    echo "Installing mvnd $MVND_VERSION..."

    # Remove existing installation if any
    if [[ -d "$MVND_DIR" ]]; then
        echo "Removing existing installation..."
        rm -rf "$MVND_DIR"
    fi

    # Download and extract
    echo "Downloading mvnd..."
    curl -L "$MVND_URL" | tar -xzf - -C "$HOME/.mvnd"

    # Make it executable
    chmod +x "$MVND_BIN"

    # Create symlink in PATH
    if [[ ! -L /usr/local/bin/mvnd ]]; then
        sudo ln -sf "$MVND_BIN" /usr/local/bin/mvnd
    fi

    echo "✓ mvnd $MVND_VERSION installed successfully!"
    echo "✓ Version: $(mvnd --version)"

    # Update dx.sh to prefer mvnd
    echo ""
    echo "To use mvnd with dx.sh, run:"
    echo "export MVND_HOME=\"$HOME/.mvnd/mvnd-${MVND_VERSION}\""
    echo "export PATH=\"\$MVND_HOME/bin:\$PATH\""
}

# Remove mvnd
remove_mvnd() {
    echo "Removing mvnd..."

    # Remove installation
    rm -rf "$MVND_DIR"

    # Remove symlink
    if [[ -L /usr/local/bin/mvnd ]]; then
        sudo rm /usr/local/bin/mvnd
    fi

    echo "✓ mvnd removed successfully"
}

# Main logic
if [[ $CHECK_ONLY -eq 1 ]]; then
    check_mvnd
elif [[ $REMOVE -eq 1 ]]; then
    check_mvnd && remove_mvnd
else
    if check_mvnd; then
        echo ""
        read -p "mvnd is already installed. Reinstall? (y/N) " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            remove_mvnd
            install_mvnd
        fi
    else
        install_mvnd
    fi
fi