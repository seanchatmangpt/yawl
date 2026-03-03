#!/bin/bash
set -euo pipefail

# SessionStart hook for YAWL in Claude Code Web
# This hook:
# 1. Verifies Maven is available (for build system)
# 2. Ensures Java 25 is installed (auto-installs via Adoptium if missing)
# 3. Configures apt proxy for package downloads
# 4. Starts local Maven proxy bridge (maven-proxy-v2.py)
# 5. Configures Maven settings.xml to use local proxy
# 6. Configures H2 database for ephemeral testing
# 7. Only runs in remote Claude Code Web environment

TEMURIN_25_HOME="/usr/lib/jvm/temurin-25-jdk-amd64"

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
# MAVEN PROXY SETUP — must start BEFORE apt/Java install so downloads work
# ============================================================================

echo "🌐 Checking for egress proxy requirements..."

if [ -n "${https_proxy:-}" ] || [ -n "${HTTPS_PROXY:-}" ]; then
    echo "   📡 Egress proxy detected"

    # Resolve project root (hook may run from any cwd)
    HOOK_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    REPO_ROOT="$(cd "${HOOK_DIR}/../.." && pwd)"

    # Check if proxy is already listening on port 3128 (TCP connectivity test)
    proxy_listening() {
        bash -c '< /dev/tcp/127.0.0.1/3128' 2>/dev/null
    }

    if proxy_listening; then
        echo "   ✅ Local Maven proxy already running"
    else
        echo "   🔧 Starting local Maven proxy bridge..."
        PROXY_SCRIPT=""
        [ -f "${REPO_ROOT}/maven-proxy-v2.py" ] && PROXY_SCRIPT="${REPO_ROOT}/maven-proxy-v2.py"
        [ -z "${PROXY_SCRIPT}" ] && [ -f "${REPO_ROOT}/maven-proxy.py" ] && PROXY_SCRIPT="${REPO_ROOT}/maven-proxy.py"

        if [ -n "${PROXY_SCRIPT}" ]; then
            nohup python3 "${PROXY_SCRIPT}" > /tmp/maven-proxy.log 2>&1 &
            # Wait up to 10s for port 3128 to be bound instead of a fixed sleep
            PROXY_READY=false
            for i in $(seq 1 20); do
                sleep 0.5
                if proxy_listening; then
                    PROXY_READY=true
                    break
                fi
            done
            if [ "${PROXY_READY}" = "true" ]; then
                echo "   ✅ Local Maven proxy started (127.0.0.1:3128)"
            else
                echo "   ⚠️  Proxy start failed — check /tmp/maven-proxy.log"
                cat /tmp/maven-proxy.log >&2 2>/dev/null || true
            fi
        fi
    fi
    export MAVEN_PROXY_ENABLED=true
    export MAVEN_PROXY_PORT=3128

    # Sweep any Maven .lastUpdated poison markers left by prior session.
    # These appear when a JAR download was truncated mid-stream (the egress
    # gateway has a per-CONNECT-tunnel data cap of ~1.6 MB). Re-download via
    # curl which opens a fresh connection per file and reliably gets large JARs.
    M2_CACHE="${HOME}/.m2/repository"
    MAVEN_CENTRAL="https://repo.maven.apache.org/maven2"
    UPSTREAM_PROXY="${https_proxy:-${HTTPS_PROXY:-}}"
    SWEPT=0
    FAILED=0
    if [ -n "${UPSTREAM_PROXY}" ] && [ -d "${M2_CACHE}" ]; then
        while IFS= read -r -d '' marker; do
            jarfile="${marker%.lastUpdated}"
            relpath="${jarfile#${M2_CACHE}/}"
            # Skip internal YAWL artifacts — built from source, not on Central
            if echo "${relpath}" | grep -q "yawlfoundation"; then
                rm -f "${marker}"
                continue
            fi
            jar_url="${MAVEN_CENTRAL}/${relpath}"
            sha_url="${jar_url%.jar}.jar.sha1"
            sha_file="${jarfile%.jar}.jar.sha1"
            if curl --silent --show-error --proxy "${UPSTREAM_PROXY}" \
                    --retry 3 --retry-delay 2 \
                    -o "${jarfile}" "${jar_url}" 2>/dev/null; then
                curl --silent --proxy "${UPSTREAM_PROXY}" -o "${sha_file}" "${sha_url}" 2>/dev/null
                remote_sha=$(cat "${sha_file}" 2>/dev/null | tr -d '[:space:]' | head -c 40)
                local_sha=$(sha1sum "${jarfile}" 2>/dev/null | cut -d' ' -f1)
                if [ "${remote_sha}" = "${local_sha}" ] && [ -n "${local_sha}" ]; then
                    rm -f "${marker}"
                    SWEPT=$((SWEPT + 1))
                else
                    rm -f "${jarfile}"
                    FAILED=$((FAILED + 1))
                fi
            else
                FAILED=$((FAILED + 1))
            fi
        done < <(find "${M2_CACHE}" -name "*.jar.lastUpdated" -print0 2>/dev/null)
        if [ "${SWEPT}" -gt 0 ] || [ "${FAILED}" -gt 0 ]; then
            echo "   🔄 Maven cache repair: ${SWEPT} JARs recovered, ${FAILED} failed"
        fi
    fi

    # Configure apt to use the upstream proxy for package downloads
    if [ -n "${http_proxy:-}" ]; then
        mkdir -p /etc/apt/apt.conf.d
        cat > /etc/apt/apt.conf.d/99claude-proxy << APTEOF
Acquire::http::Proxy "${http_proxy}";
Acquire::https::Proxy "${https_proxy:-${http_proxy}}";
APTEOF
        echo "   ✅ apt proxy configured"
    fi
else
    echo "   ✅ No egress proxy detected - using direct repository access"
fi

# ============================================================================
# ERLANG/OTP 28 — install from Hex.pm pre-built binary if not present
# ============================================================================

echo "🔴 Checking Erlang/OTP 28 requirement..."

ERLMCP_DIR="/home/user/yawl/.erlmcp"
OTP_VERSION="28.3.1"
OTP_DIR="${ERLMCP_DIR}/otp-${OTP_VERSION}"
OTP_BIN="${OTP_DIR}/bin/erl"
REBAR3_BIN="${ERLMCP_DIR}/rebar3"

if [ -f "${OTP_BIN}" ]; then
    echo "   ✅ OTP ${OTP_VERSION} already installed at ${OTP_DIR}"
else
    echo "   📥 OTP ${OTP_VERSION} not found — downloading from Hex.pm..."
    mkdir -p "${ERLMCP_DIR}"
    OTP_URL="https://builds.hex.pm/builds/otp/amd64/ubuntu-22.04/OTP-${OTP_VERSION}.tar.gz"
    OTP_DOWNLOAD_OK=false
    for attempt in 1 2 3; do
        if curl -fsSL --max-time 300 "${OTP_URL}" | tar -xz -C "${ERLMCP_DIR}"; then
            OTP_DOWNLOAD_OK=true
            break
        fi
        echo "   ⚠️  Attempt ${attempt} failed, retrying in 5s..."
        sleep 5
    done
    if [ "${OTP_DOWNLOAD_OK}" != "true" ]; then
        echo "   ❌ OTP download failed after 3 attempts — Erlang features will be unavailable"
    else
        # Rename extracted directory
        if [ -d "${ERLMCP_DIR}/OTP-${OTP_VERSION}" ] && [ ! -d "${OTP_DIR}" ]; then
            mv "${ERLMCP_DIR}/OTP-${OTP_VERSION}" "${OTP_DIR}"
        fi
        # Run Install
        cd "${OTP_DIR}" && ./Install -minimal "$(pwd)"
        cd /home/user/yawl
        echo "   ✅ OTP ${OTP_VERSION} installed at ${OTP_DIR}"
    fi
fi

# Install rebar3 if not present
if [ -x "${REBAR3_BIN}" ]; then
    echo "   ✅ rebar3 already installed"
else
    echo "   📥 Downloading rebar3..."
    mkdir -p "${ERLMCP_DIR}"
    if curl -fsSL --max-time 120 \
        "https://github.com/erlang/rebar3/releases/download/3.23.0/rebar3" \
        -o "${REBAR3_BIN}"; then
        chmod +x "${REBAR3_BIN}"
        echo "   ✅ rebar3 installed at ${REBAR3_BIN}"
    else
        echo "   ⚠️  rebar3 download failed — Erlang compilation will be skipped"
    fi
fi

# Export OTP to PATH for this session
if [ -f "${OTP_BIN}" ]; then
    export PATH="${OTP_DIR}/bin:${PATH}"
    export YAWL_OTP_HOME="${OTP_DIR}"
    if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
        echo "export PATH=${OTP_DIR}/bin:\$PATH" >> "${CLAUDE_ENV_FILE}"
        echo "export YAWL_OTP_HOME=${OTP_DIR}" >> "${CLAUDE_ENV_FILE}"
    fi
    # Compile .erl → .beam if needed
    EBIN_DIR="${ERLMCP_DIR}/../yawl-erlang/src/main/resources/org/yawlfoundation/yawl/erlang/ebin"
    if [ ! -d "${EBIN_DIR}" ] || [ -z "$(ls -A "${EBIN_DIR}" 2>/dev/null)" ]; then
        echo "   🔨 Compiling Erlang sources..."
        bash /home/user/yawl/scripts/build-erlang-beams.sh 2>&1 || echo "   ⚠️  Beam compilation failed"
    fi
    OTP_RELEASE=$("${OTP_BIN}" -eval 'io:format("~s",[erlang:system_info(otp_release)])' -s init stop -noshell 2>/dev/null)
    echo "   ✅ Erlang/OTP ${OTP_RELEASE} active ($(which erl 2>/dev/null || echo "${OTP_BIN}"))"
fi

# ============================================================================
# JAVA 25 — install via Adoptium if not present
# ============================================================================

echo "☕ Checking Java 25 requirement..."

JAVA_VERSION=$(java -version 2>&1 | grep 'version "' | head -n1 | cut -d'"' -f2 | cut -d'.' -f1)

if [ "$JAVA_VERSION" = "25" ]; then
    echo "   ✅ Java 25 already active"
else
    echo "   ⚠️  Java $JAVA_VERSION detected — installing Java 25 (Eclipse Temurin)..."

    # Add Adoptium GPG key + repo
    if [ ! -f /etc/apt/trusted.gpg.d/adoptium.gpg ]; then
        wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public \
            | gpg --dearmor -o /etc/apt/trusted.gpg.d/adoptium.gpg
        . /etc/os-release
        echo "deb https://packages.adoptium.net/artifactory/deb ${VERSION_CODENAME} main" \
            > /etc/apt/sources.list.d/adoptium.list
        apt-get update -qq
    fi

    DEBIAN_FRONTEND=noninteractive apt-get install -y temurin-25-jdk

    # Register with update-alternatives
    update-alternatives --install /usr/bin/java  java  "${TEMURIN_25_HOME}/bin/java"  100
    update-alternatives --install /usr/bin/javac javac "${TEMURIN_25_HOME}/bin/javac" 100
    update-alternatives --set java  "${TEMURIN_25_HOME}/bin/java"
    update-alternatives --set javac "${TEMURIN_25_HOME}/bin/javac"

    JAVA_VERSION=$(java -version 2>&1 | grep 'version "' | head -n1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [ "$JAVA_VERSION" != "25" ]; then
        echo "❌ Java 25 installation failed. Cannot continue."
        exit 1
    fi
    echo "   ✅ Java 25 installed successfully"
fi

# Always export JAVA_HOME pointing at Temurin 25
if [ -d "${TEMURIN_25_HOME}" ]; then
    export JAVA_HOME="${TEMURIN_25_HOME}"
    export PATH="${JAVA_HOME}/bin:${PATH}"
fi

echo "   ✅ JAVA_HOME=${JAVA_HOME:-$(dirname $(dirname $(readlink -f $(which java))))}"

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

# Disable Maven's HTTP connection pool so each artifact download gets a fresh
# CONNECT tunnel through the egress proxy. Without this, Maven reuses tunnels
# across downloads; the egress gateway drops connections after ~1.6 MB of data
# through a single tunnel, causing "Premature end of Content-Length" errors for
# large JARs (bouncycastle 8.4 MB, byte-buddy 9 MB, etc.).
export MAVEN_OPTS="${MAVEN_OPTS} -Dmaven.wagon.http.pool=false -Dmaven.wagon.httpconnectionManager.ttlSeconds=5"

echo "✅ H2 database configured (in-memory, ephemeral)"

# Export environment variables for runtime detection and correct Java toolchain.
# Variables written here are sourced into Claude Code's process environment so
# that every subsequent Bash tool call inherits JAVA_HOME and PATH correctly.
if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  echo 'export YAWL_REMOTE_ENVIRONMENT=true' >> "$CLAUDE_ENV_FILE"
  echo 'export YAWL_DATABASE_TYPE=h2' >> "$CLAUDE_ENV_FILE"
  # Persist Java 25 JAVA_HOME so Maven uses the right javac in every tool call.
  # Without this, JAVA_HOME stays at the system default (Java 21) even though
  # the hook exports it inside its own subprocess.
  echo "export JAVA_HOME=${TEMURIN_25_HOME}" >> "$CLAUDE_ENV_FILE"
  echo "export PATH=${TEMURIN_25_HOME}/bin:\$PATH" >> "$CLAUDE_ENV_FILE"
  # H2 database properties + egress-proxy connection pool disable.
  # The egress gateway drops CONNECT tunnels after ~1.6 MB; disabling Maven's
  # connection pool ensures each artifact download gets a fresh tunnel.
  echo "export MAVEN_OPTS=\"-Dspring.datasource.url=jdbc:h2:mem:yawl;DB_CLOSE_DELAY=-1 -Dspring.datasource.username=sa -Dspring.datasource.password= -Dhibernate.dialect=org.hibernate.dialect.H2Dialect -Dmaven.wagon.http.pool=false -Dmaven.wagon.httpconnectionManager.ttlSeconds=5\"" >> "$CLAUDE_ENV_FILE"
fi

# ============================================================================
# Observatory Codebase Analysis
# ============================================================================

echo "🔍 Running Observatory to generate codebase facts..."

# Run Observatory in facts-only mode for fast startup (~13s)
if bash scripts/observatory/observatory.sh --facts; then
    echo "✅ Observatory facts generated successfully"

    # Write pom-hash sidecar so dx.sh all skips observatory on first run.
    # observatory.sh --facts does not write the full receipt, so dx.sh
    # uses this sidecar as the primary freshness signal.
    mkdir -p .yawl/.dx-state
    sha256sum pom.xml 2>/dev/null | awk '{print $1}' \
        > .yawl/.dx-state/observatory-pom-hash.txt || true

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

# ============================================================================
# COMPILE + TEST BASELINE — correct by construction
# Verify the guard validation module compiles and critical tests pass.
# This establishes a green baseline before any code changes.
# ============================================================================

echo "🧪 Verifying guard validation baseline (correct by construction)..."

# Remove stale .lastUpdated poison markers for local YAWL modules.
# These are created by Maven when a local artifact has never been installed
# to .m2. They prevent re-installation even after source changes.
find /root/.m2/repository/org/yawlfoundation -name "*.lastUpdated" -delete 2>/dev/null || true

# Pre-flight: ensure zstd-jni is in the local cache before Maven needs it.
# maven-jar-plugin:3.4.1 (used by mvn install) depends on zstd-jni:1.5.5-11 (6.7 MB).
# The egress proxy caps per-CONNECT-tunnel at ~1.6 MB, so Maven's built-in
# transport always truncates this download and leaves a .lastUpdated marker.
# curl opens a fresh connection per retry and reliably fetches the full file.
_ZSTD_JAR="${HOME}/.m2/repository/com/github/luben/zstd-jni/1.5.5-11/zstd-jni-1.5.5-11.jar"
if [ ! -f "${_ZSTD_JAR}" ] && [ -n "${https_proxy:-${HTTPS_PROXY:-}}" ]; then
    _UPSTREAM="${https_proxy:-${HTTPS_PROXY:-}}"
    rm -f "${_ZSTD_JAR}.lastUpdated"
    mkdir -p "$(dirname "${_ZSTD_JAR}")"
    curl --silent --proxy "${_UPSTREAM}" --retry 3 --retry-delay 2 \
         -o "${_ZSTD_JAR}" \
         "https://repo.maven.apache.org/maven2/com/github/luben/zstd-jni/1.5.5-11/zstd-jni-1.5.5-11.jar" \
         2>/dev/null || rm -f "${_ZSTD_JAR}"
fi

GGEN_TEST_LOG="/tmp/yawl-ggen-baseline-test.log"
GGEN_TEST_EXIT=0

# Step 1: Install yawl-parent POM into .m2 (non-recursive: just the root POM).
# yawl-graalpy's installed POM references this parent; Maven must be able to
# resolve it from .m2 when yawl-ggen reads graalpy's transitive dependency chain.
mvn install -N \
    -Dmaven.test.skip=true \
    --no-transfer-progress \
    --batch-mode \
    -q >> "${GGEN_TEST_LOG}" 2>&1 || GGEN_TEST_EXIT=$?

# Step 2: Install yawl-graalpy (Layer 0) into .m2, skipping its own test
# compilation. Its integration tests have cross-module dependencies on
# yawl-engine that are not yet built at this point in the session, so
# -Dmaven.test.skip=true avoids cascading testCompile failures.
if [ "${GGEN_TEST_EXIT}" -eq 0 ]; then
    mvn install \
        -pl yawl-graalpy \
        -Dmaven.test.skip=true \
        --no-transfer-progress \
        --batch-mode \
        -q >> "${GGEN_TEST_LOG}" 2>&1 || GGEN_TEST_EXIT=$?
fi

# Step 3: Test yawl-ggen against the now-installed yawl-graalpy JAR.
if [ "${GGEN_TEST_EXIT}" -eq 0 ]; then
    mvn test \
        -pl yawl-ggen \
        -Dtest="HyperStandardsValidatorTest" \
        -Dmaven.test.skip=false \
        -Dsurefire.failIfNoSpecifiedTests=false \
        --no-transfer-progress \
        --batch-mode \
        -q >> "${GGEN_TEST_LOG}" 2>&1 || GGEN_TEST_EXIT=$?
fi

if [ "${GGEN_TEST_EXIT}" -eq 0 ]; then
    echo "✅ Guard validation tests: GREEN (HyperStandardsValidatorTest 20/20)"
else
    echo "⚠️  Guard validation tests FAILED (exit ${GGEN_TEST_EXIT})"
    tail -10 "${GGEN_TEST_LOG}" || true
    echo "   Run: mvn install -pl yawl-graalpy -Dmaven.test.skip=true && mvn test -pl yawl-ggen -Dtest=HyperStandardsValidatorTest -Dmaven.test.skip=false"
    echo "   Fix failures before making changes to the guard validation package"
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

# State summary (observatory + H/Q receipts) is emitted by yawl-state.sh,
# which runs as a separate SessionStart hook and works on all platforms.
