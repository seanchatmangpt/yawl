#!/bin/bash
set -euo pipefail

# SessionStart hook for YAWL in Claude Code Web
# This hook:
# 1. Verifies Maven is available (for build system)
# 2. Configures H2 database for ephemeral testing
# 3. Only runs in remote Claude Code Web environment

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

# Configure H2 database for ephemeral testing via environment variables
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
echo "   • Build System: Maven 3.x"
echo "   • Database: H2 (in-memory)"
echo "   • Test Command: mvn clean test"
echo "   • Environment: Remote/Ephemeral"
echo ""
