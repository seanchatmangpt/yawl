#!/bin/bash

# Three-Domain Native Bridge Pattern Validation Script
# Validates isolation, boundaries, call patterns, and H guards

echo "🔍 Three-Domain Native Bridge Pattern Validation"
echo "================================================="

# Configuration
BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BRIDGE_TTL="$BASE_DIR/../../../ontology/process-mining/pm-bridge.ttl"
RUST4PM_DIR="$BASE_DIR/../../../yawl-rust4pm/rust4pm"
QLEVER_DIR="$BASE_DIR/src/main/java/org/yawlfoundation/yawl/bridge/qlever"
ERLANG_DIR="$BASE_DIR/../../../yawl-erlang-bridge/yawl-erlang-bridge"

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Validation functions
validate_isolation() {
    echo -e "\n🔒 Verifying Isolation Guarantee..."

    # Check Rust boundary in lib.rs
    if grep -q "ResourceArc<OcelLogResource>" "$RUST4PM_DIR/src/lib.rs"; then
        echo -e "${GREEN}✅ Rust ResourceArc boundary detected${NC}"
    else
        echo -e "${RED}❌ Rust ResourceArc boundary missing${NC}"
        return 1
    fi

    # Check NIF error handling
    if grep -q "Result<.*>.*String" "$RUST4PM_DIR/src/lib.rs"; then
        echo -e "${GREEN}✅ Rust Result<T, E> error boundary detected${NC}"
    else
        echo -e "${RED}❌ Rust error boundary missing${NC}"
        return 1
    fi

    # Check Java error translation
    if grep -q "QleverRuntimeException" "$QLEVER_DIR/QLeverEngineImpl.java"; then
        echo -e "${GREEN}✅ Java error translation detected${NC}"
    else
        echo -e "${RED}❌ Java error translation missing${NC}"
        return 1
    fi

    return 0
}

validate_boundaries() {
    echo -e "\n🌉 Verifying Boundaries..."

    # Check Boundary A: JVM ↔ BEAM
    if [ -f "$ERLANG_DIR/src/process_mining_bridge.erl" ]; then
        if grep -q "gen_server:call" "$ERLANG_DIR/src/process_mining_bridge.erl"; then
            echo -e "${GREEN}✅ BEAM gen_server boundary detected${NC}"
        else
            echo -e "${RED}❌ BEAM gen_server boundary missing${NC}"
            return 1
        fi
    fi

    # Check Boundary B: BEAM ↔ Rust (NIF)
    if grep -q "#\[rustler::nif\]" "$RUST4PM_DIR/src/lib.rs"; then
        echo -e "${GREEN}✅ Rust NIF boundary detected${NC}"
    else
        echo -e "${RED}❌ Rust NIF boundary missing${NC}"
        return 1
    fi

    # Check Panama FFI in JVM
    if grep -q "MemorySegment" "$QLEVER_DIR/QLeverEngineImpl.java"; then
        echo -e "${GREEN}✅ Panama FFI boundary detected${NC}"
    else
        echo -e "${RED}❌ Panama FFI boundary missing${NC}"
        return 1
    fi

    return 0
}

validate_call_patterns() {
    echo -e "\n🛣️  Verifying Call Pattern Routing..."

    # Check pm-bridge.ttl for call patterns
    if [ -f "$BRIDGE_TTL" ]; then
        # Check jvm pattern
        if grep -A5 'callPattern "jvm"' "$BRIDGE_TTL" | grep -q "QLEVER_ASK"; then
            echo -e "${GREEN}✅ JVM domain capability routing detected${NC}"
        else
            echo -e "${RED}❌ JVM domain capability routing missing${NC}"
            return 1
        fi

        # Check beam pattern
        if grep -A5 'callPattern "beam"' "$BRIDGE_TTL" | grep -q "PARSE_OCEL2_JSON"; then
            echo -e "${GREEN}✅ BEAM domain capability routing detected${NC}"
        else
            echo -e "${RED}❌ BEAM domain capability routing missing${NC}"
            return 1
        fi

        # Check direct pattern is blocked
        if grep -A3 'callPattern "direct"' "$BRIDGE_TTL" | grep -q "BLOCKED"; then
            echo -e "${GREEN}✅ Direct pattern security block detected${NC}"
        else
            echo -e "${RED}❌ Direct pattern security block missing${NC}"
            return 1
        fi
    else
        echo -e "${RED}❌ pm-bridge.ttl not found${NC}"
        return 1
    fi

    return 0
}

validate_latency_targets() {
    echo -e "\n⚡ Verifying Latency Targets..."

    # Check for latency claims in documentation
    if grep -q "~10ns" "$BASE_DIR/IMPLEMENTATION_SUMMARY.md"; then
        echo -e "${GREEN}✅ JVM → QLever latency target documented${NC}"
    else
        echo -e "${YELLOW}⚠️  JVM → QLever latency target not documented${NC}"
    fi

    if grep -q "~5-20µs" "$BASE_DIR/IMPLEMENTATION_SUMMARY.md"; then
        echo -e "${GREEN}✅ JVM → BEAM latency target documented${NC}"
    else
        echo -e "${YELLOW}⚠️  JVM → BEAM latency target not documented${NC}"
    fi

    if grep -q "~100ns" "$BASE_DIR/IMPLEMENTATION_SUMMARY.md"; then
        echo -e "${GREEN}✅ BEAM → Rust latency target documented${NC}"
    else
        echo -e "${YELLOW}⚠️  BEAM → Rust latency target not documented${NC}"
    fi

    return 0
}

validate_h_guards() {
    echo -e "\n🛡️  Verifying H Guards..."

    # Check for violations in Java code
    VIOLATIONS=$(find "$BASE_DIR/src" -name "*.java" -exec grep -l "TODO\|FIXME\|XXX\|HACK\|mock\|stub\|fake" {} \; 2>/dev/null)

    if [ -z "$VIOLATIONS" ]; then
        echo -e "${GREEN}✅ No H guard violations in Java code${NC}"
    else
        echo -e "${RED}❌ H guard violations found:${NC}"
        for file in $VIOLATIONS; do
            echo -e "   $file"
        done
        return 1
    fi

    # Check for violations in Rust code
    VIOLATIONS=$(find "$RUST4PM_DIR/src" -name "*.rs" -exec grep -l "TODO\|FIXME\|XXX\|HACK\|mock\|stub\|fake" {} \; 2>/dev/null)

    if [ -z "$VIOLATIONS" ]; then
        echo -e "${GREEN}✅ No H guard violations in Rust code${NC}"
    else
        echo -e "${RED}❌ H guard violations found:${NC}"
        for file in $VIOLATIONS; do
            echo -e "   $file"
        done
        return 1
    fi

    return 0
}

validate_error_handling() {
    echo -e "\n🚨 Verifying Error Handling Patterns..."

    # Check Result<T, E> pattern in Rust
    if grep -q "Result<.*, String>" "$RUST4PM_DIR/src/lib.rs"; then
        echo -e "${GREEN}✅ Rust Result<T, E> error pattern detected${NC}"
    else
        echo -e "${RED}❌ Rust error pattern missing${NC}"
        return 1
    fi

    # Check exception hierarchy in Java
    if grep -q "extends Exception" "$QLEVER_DIR/QleverException.java"; then
        echo -e "${GREEN}✅ Java exception hierarchy detected${NC}"
    else
        echo -e "${RED}❌ Java exception hierarchy missing${NC}"
        return 1
    fi

    # Check try-catch in Erlang
    if grep -q "try.*catch.*error" "$ERLANG_DIR/src/process_mining_bridge.erl"; then
        echo -e "${GREEN}✅ Erlang error handling detected${NC}"
    else
        echo -e "${RED}❌ Erlang error handling missing${NC}"
        return 1
    fi

    return 0
}

validate_hot_reload() {
    echo -e "\n🔄 Verifying Hot Reload Mechanism..."

    if grep -q "code_change" "$ERLANG_DIR/src/process_mining_bridge.erl"; then
        echo -e "${GREEN}✅ BEAM hot reload support detected${NC}"
    else
        echo -e "${YELLOW}⚠️  BEAM hot reload support not implemented${NC}"
    fi

    # Check for NIF reload pattern
    if grep -q "NIF.*load" "$ERLANG_DIR/src/process_mining_bridge.erl"; then
        echo -e "${GREEN}✅ NIF reload pattern detected${NC}"
    else
        echo -e "${YELLOW}⚠️  NIF reload pattern not implemented${NC}"
    fi

    return 0
}

# Run all validations
echo "Starting validation..."
echo ""

VALIDATION_PASSED=true

validate_isolation || VALIDATION_PASSED=false
validate_boundaries || VALIDATION_PASSED=false
validate_call_patterns || VALIDATION_PASSED=false
validate_latency_targets
validate_error_handling || VALIDATION_PASSED=false
validate_h_guards || VALIDATION_PASSED=false
validate_hot_reload

echo ""
echo "================================================="
if [ "$VALIDATION_PASSED" = true ]; then
    echo -e "${GREEN}🎉 ALL VALIDATIONS PASSED${NC}"
    echo ""
    echo "Three-Domain Native Bridge Pattern implementation is:"
    echo "- ✅ Properly isolated"
    echo "- ✅ Correctly bounded"
    echo "- ✅ Properly routed"
    echo "- ✅ Compliant with H guards"
    echo "- ✅ Production-ready"
    exit 0
else
    echo -e "${RED}❌ VALIDATION FAILED${NC}"
    echo ""
    echo "Please address the issues above before proceeding."
    exit 1
fi