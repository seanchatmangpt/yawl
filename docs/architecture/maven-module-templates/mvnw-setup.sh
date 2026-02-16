#!/bin/bash
# Maven Wrapper Setup Script for YAWL
# Usage: ./mvnw-setup.sh

set -e

MAVEN_VERSION="3.9.6"

echo "╔═══════════════════════════════════════════════════════════════╗"
echo "║         YAWL Maven Wrapper Installation                      ║"
echo "║         Maven Version: ${MAVEN_VERSION}                               ║"
echo "╚═══════════════════════════════════════════════════════════════╝"
echo ""

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo "ERROR: Maven not found. Please install Maven first."
    echo "  brew install maven  # macOS"
    echo "  apt install maven   # Ubuntu/Debian"
    echo "  dnf install maven   # Fedora/RHEL"
    exit 1
fi

echo "1. Installing Maven Wrapper..."
mvn wrapper:wrapper -Dmaven=${MAVEN_VERSION}

echo ""
echo "2. Making wrapper scripts executable..."
chmod +x mvnw
chmod +x mvnw.cmd

echo ""
echo "3. Verifying wrapper installation..."
./mvnw --version

echo ""
echo "╔═══════════════════════════════════════════════════════════════╗"
echo "║         Maven Wrapper Installed Successfully!                ║"
echo "╚═══════════════════════════════════════════════════════════════╝"
echo ""
echo "Usage:"
echo "  ./mvnw clean install       # Build all modules"
echo "  ./mvnw test                # Run tests"
echo "  ./mvnw jib:dockerBuild     # Build Docker images"
echo ""
echo "The wrapper will automatically download Maven ${MAVEN_VERSION} on first run."
echo "No local Maven installation needed for other developers!"
echo ""

# Git add wrapper files
if [ -d .git ]; then
    echo "4. Adding wrapper files to Git..."
    git add mvnw mvnw.cmd .mvn/
    echo ""
    echo "Next steps:"
    echo "  git commit -m 'Add Maven wrapper for consistent builds'"
    echo "  git push"
fi
