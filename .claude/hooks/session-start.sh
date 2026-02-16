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

echo "✨ YAWL environment ready for Claude Code Web"
echo ""
echo "📋 Environment Summary:"
echo "   • Java Version: Java $JAVA_VERSION (required)"
echo "   • Build System: Maven 3.x"
echo "   • Maven Cache: ${M2_CACHE_DIR}"
echo "   • Database: H2 (in-memory)"
echo "   • Test Command: mvn clean test"
echo "   • Environment: Remote/Ephemeral"
echo ""
