#!/bin/bash
# 80/20 Innovation #5: Instant Status Dashboard
# Shows everything important at a glance

set -euo pipefail

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🎯 YAWL Status Dashboard"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Environment
echo "📍 ENVIRONMENT"
if [ "${CLAUDE_CODE_REMOTE:-}" = "true" ]; then
    echo "  Mode: ☁️  Claude Code Web (Remote)"
    echo "  Session: ${CLAUDE_CODE_REMOTE_SESSION_ID:-unknown}"
else
    echo "  Mode: 🏠 Local Development"
fi
echo ""

# Database
echo "💾 DATABASE"
if [ -f "build/build.properties" ]; then
    DB_TYPE=$(grep "^database.type=" build/build.properties 2>/dev/null | cut -d= -f2 || echo "unknown")
    DB_PATH=$(grep "^database.path=" build/build.properties 2>/dev/null | cut -d= -f2 || echo "unknown")
    echo "  Type: $DB_TYPE"
    echo "  Path: $DB_PATH"
else
    echo "  ⚠️  No build.properties found"
fi
echo ""

# Build Tools
echo "🔨 BUILD TOOLS"
if command -v ant &> /dev/null; then
    ANT_VER=$(ant -version 2>&1 | head -n1 | cut -d' ' -f4)
    echo "  Ant: ✅ $ANT_VER"
else
    echo "  Ant: ❌ Not installed"
fi

if command -v java &> /dev/null; then
    JAVA_VER=$(java -version 2>&1 | head -n1 | cut -d'"' -f2)
    echo "  Java: ✅ $JAVA_VER"
else
    echo "  Java: ❌ Not installed"
fi
echo ""

# Compiled Classes
echo "📦 COMPILED CODE"
if [ -d "classes" ] && [ -n "$(ls -A classes 2>/dev/null)" ]; then
    CLASS_COUNT=$(find classes -name "*.class" 2>/dev/null | wc -l)
    echo "  Classes: ✅ $CLASS_COUNT files"
else
    echo "  Classes: ⚠️  Not compiled (run: ant compile)"
fi
echo ""

# Git Status
echo "📝 GIT STATUS"
if git rev-parse --git-dir > /dev/null 2>&1; then
    BRANCH=$(git branch --show-current 2>/dev/null || echo "unknown")
    UNCOMMITTED=$(git status --short 2>/dev/null | wc -l)
    echo "  Branch: $BRANCH"
    if [ "$UNCOMMITTED" -eq 0 ]; then
        echo "  Changes: ✅ Clean working tree"
    else
        echo "  Changes: ⚠️  $UNCOMMITTED uncommitted"
    fi
else
    echo "  ⚠️  Not a git repository"
fi
echo ""

# Quick Actions
echo "⚡ QUICK ACTIONS"
echo "  ./.claude/quick-start.sh test   → Run tests"
echo "  ./.claude/quick-start.sh build  → Compile code"
echo "  ./.claude/smart-build.sh        → Smart build + test"
echo "  java -cp classes org...QuickTest → Verify environment"
echo ""

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ Status check complete"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
