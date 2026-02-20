#!/bin/bash
set -euo pipefail

# SessionStart hook for YAWL in Claude Code Web
# This hook:
# 1. Verifies Maven is available (for build system)
# 2. Validates Java 25 requirement (YAWL v5.2)
# 3. Configures H2 database for ephemeral testing
# 4. Configures Maven dependency caching
# 5. Only runs in remote Claude Code Web environment

# Exit early if not in Claude Code Web
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  echo "Local environment detected - skipping remote setup"
  exit 0
fi

echo "🔧 Setting up YAWL for Claude Code Web..."

# Verify Maven is available (should be pre-installed in environment)
if ! command -v mvn &> /dev/null; then
  echo "⚠️  Maven not found in PATH"
  echo "   Please ensure Maven 3.8.1+ is installed"
  exit 1
else
  echo "✅ Maven available: $(mvn --version 2>/dev/null | head -n1)"
fi

# ============================================================================
# JAVA 25 VALIDATION (Required for YAWL v5.2)
# ============================================================================

echo "☕ Validating Java 25 requirement..."

JAVA_VERSION=$(java -version 2>&1 | head -n1 | cut -d'"' -f2 | cut -d'.' -f1)

if [ "$JAVA_VERSION" != "25" ]; then
    echo "❌ ERROR: Java 25 required, found Java $JAVA_VERSION"
    echo "   YAWL v5.2 requires Java 25 specifically"
    echo "   Install: https://jdk.java.net/25/"
    echo ""
    echo "   Current: Java $JAVA_VERSION"
    echo "   Required: Java 25"
    exit 1
fi

echo "   ✅ Java 25 detected"

# Enable Java 25 preview features
export MAVEN_OPTS="--enable-preview --add-modules jdk.incubator.concurrent -Xmx2g"
echo "   ✅ Maven configured for Java 25 preview features"

# ============================================================================
# MAVEN PROXY SETUP (Web Environment Only)
# ============================================================================

echo "🌐 Checking for egress proxy requirements..."

# Detect if we need to use Maven proxy workaround
if [ -n "${https_proxy:-}" ] || [ -n "${HTTPS_PROXY:-}" ]; then
    PROXY_URL="${https_proxy:-${HTTPS_PROXY:-}}"
    echo "   📡 Egress proxy detected: ${PROXY_URL%:*}***" # Hide password

    # Start local Maven proxy in background if not already running
    if ! pgrep -f "maven-proxy.*python" > /dev/null 2>&1; then
        echo "   🔧 Starting local Maven proxy..."

        # Use maven-proxy-v2.py if available, fallback to maven-proxy.py
        PROXY_SCRIPT=""
        if [ -f "maven-proxy-v2.py" ]; then
            PROXY_SCRIPT="maven-proxy-v2.py"
        elif [ -f "maven-proxy.py" ]; then
            PROXY_SCRIPT="maven-proxy.py"
        fi

        if [ -n "${PROXY_SCRIPT}" ]; then
            # Start proxy on port 3128 (non-privileged)
            python3 "${PROXY_SCRIPT}" > /tmp/maven-proxy.log 2>&1 &
            PROXY_PID=$!

            # Wait for proxy to start
            sleep 2

            # Verify proxy is running
            if pgrep -p "${PROXY_PID}" > /dev/null 2>&1; then
                echo "   ✅ Local Maven proxy started (PID: ${PROXY_PID})"
                export MAVEN_PROXY_ENABLED=true
                export MAVEN_PROXY_PORT=3128
            else
                echo "   ⚠️  Failed to start local Maven proxy"
            fi
        else
            echo "   ⚠️  Maven proxy scripts not found in repository"
        fi
    else
        echo "   ✅ Local Maven proxy already running"
        export MAVEN_PROXY_ENABLED=true
        export MAVEN_PROXY_PORT=3128
    fi
else
    echo "   ✅ No egress proxy detected - using direct repository access"
fi

# Setup Maven cache directory
echo "📦 Configuring Maven dependency cache..."
M2_CACHE_DIR="${HOME}/.m2/repository"

if [ ! -d "${HOME}/.m2" ]; then
  mkdir -p "${HOME}/.m2"
  echo "✅ Created Maven directory: ${HOME}/.m2"
fi

if [ ! -d "${M2_CACHE_DIR}" ]; then
  mkdir -p "${M2_CACHE_DIR}"
  echo "✅ Created Maven cache directory: ${M2_CACHE_DIR}"
else
  CACHE_SIZE=$(du -sh "${M2_CACHE_DIR}" 2>/dev/null | cut -f1 || echo "0B")
  echo "✅ Maven cache directory exists: ${M2_CACHE_DIR} (${CACHE_SIZE})"
fi

# Configure Maven settings with proxy (if needed)
if [ "${MAVEN_PROXY_ENABLED:-false}" = "true" ]; then
    echo "⚙️  Configuring Maven to use local proxy..."

    # Create Maven settings.xml with local proxy config
    mkdir -p "${HOME}/.m2"
    cat > "${HOME}/.m2/settings.xml" << 'MAVEN_SETTINGS'
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
          http://maven.apache.org/xsd/settings-1.0.0.xsd">
  <proxies>
    <proxy>
      <id>local-proxy</id>
      <active>true</active>
      <protocol>https</protocol>
      <host>127.0.0.1</host>
      <port>3128</port>
    </proxy>
    <proxy>
      <id>local-proxy-http</id>
      <active>true</active>
      <protocol>http</protocol>
      <host>127.0.0.1</host>
      <port>3128</port>
    </proxy>
  </proxies>
</settings>
MAVEN_SETTINGS

    echo "   ✅ Maven configured to use local proxy (127.0.0.1:3128)"
fi

# Verify cache is writable
if [ -w "${M2_CACHE_DIR}" ]; then
  echo "✅ Maven cache is writable"
else
  echo "⚠️  Maven cache directory is not writable"
  echo "   Dependencies will be re-downloaded on each build"
fi

# Configure H2 database for ephemeral testing
echo "🗄️  Configuring H2 database for remote environment..."

# Export Maven properties for H2 in-memory database configuration
export MAVEN_OPTS="${MAVEN_OPTS:-} -Dspring.datasource.url=jdbc:h2:mem:yawl;DB_CLOSE_DELAY=-1"
export MAVEN_OPTS="${MAVEN_OPTS} -Dspring.datasource.username=sa"
export MAVEN_OPTS="${MAVEN_OPTS} -Dspring.datasource.password="

# Set Hibernate dialect for H2
export MAVEN_OPTS="${MAVEN_OPTS} -Dhibernate.dialect=org.hibernate.dialect.H2Dialect"

echo "✅ H2 database configured (in-memory, ephemeral)"

# Export environment variable for runtime detection
if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  echo 'export YAWL_REMOTE_ENVIRONMENT=true' >> "$CLAUDE_ENV_FILE"
  echo 'export YAWL_DATABASE_TYPE=h2' >> "$CLAUDE_ENV_FILE"
fi

# ============================================================================
# Observatory Codebase Analysis
# ============================================================================

echo "🔍 Running Observatory to generate codebase facts..."

# Run Observatory in facts-only mode for fast startup (~13s)
if bash scripts/observatory/observatory.sh --facts; then
    echo "✅ Observatory facts generated successfully"

    # Display summary from INDEX.md
    if [ -f "docs/v6/latest/INDEX.md" ]; then
        FACT_COUNT=$(grep -c "^- " docs/v6/latest/INDEX.md || echo "0")
        echo "   📊 Available facts: ${FACT_COUNT} files"

        # Show recent commit info
        if [ -f "docs/v6/latest/receipts/observatory.json" ]; then
            COMMIT_HASH=$(jq -r '.repo.git.commit // "N/A"' docs/v6/latest/receipts/observatory.json)
            echo "   🔍 Commit hash: ${COMMIT_HASH}"
        fi
    fi
else
    echo "⚠️  Observatory generation failed - continuing without facts"
    echo "   You can manually run: bash scripts/observatory/observatory.sh"
fi

echo ""
echo "✨ YAWL environment ready for Claude Code Web"
echo ""
echo "📋 Environment Summary:"
echo "   • Java Version: Java $JAVA_VERSION (required)"
echo "   • Build System: Maven 3.x"
echo "   • Maven Cache: ${M2_CACHE_DIR}"
if [ "${MAVEN_PROXY_ENABLED:-false}" = "true" ]; then
    echo "   • Network: Egress proxy (local proxy bridge: 127.0.0.1:3128)"
else
    echo "   • Network: Direct repository access"
fi
echo "   • Database: H2 (in-memory)"
echo "   • Observatory: Facts auto-generated on startup"
echo "   • Test Command: bash scripts/dx.sh (fast) or mvn clean test (full)"
echo "   • Environment: Remote/Ephemeral"
echo ""

# Cleanup note for user
if [ "${MAVEN_PROXY_ENABLED:-false}" = "true" ]; then
    echo "💡 TIP: To stop the Maven proxy, run: pkill -f maven-proxy"
    echo ""
fi
