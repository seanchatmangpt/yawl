# Makefile
#
# YAWL V6 Build Control Plane + Shell-Based Testing
#
# Control plane targets (observatory):
#   make verify        # Run Maven verify gates; emit receipt
#   make observatory   # Generate observatory facts/diagrams/receipt
#   make closure       # verify + observatory (final status line)
#
# Black-box testing with zero tolerance for mocks/stubs.
# All tests verify real system behavior through observable interfaces.
#
# Usage:
#   make test          # Run all test phases
#   make test-quick    # Run quick tests only (phases 1-3)
#   make test-phase PHASE=04  # Run specific phase
#   make test-schema   # Run schema validation
#   make test-stub     # Run stub detection
#   make test-build    # Run build verification
#   make test-engine   # Run engine lifecycle tests
#   make test-a2a      # Run A2A protocol tests
#   make test-mcp      # Run MCP protocol tests
#   make test-patterns # Run workflow pattern tests
#   make test-report   # Generate integration report
#   make clean         # Clean test artifacts

SHELL := /bin/bash
.SHELLFLAGS := -euo pipefail -c

# Configuration
PROJECT_DIR := $(PWD)
TEST_DIR := $(PROJECT_DIR)/test/shell
SCRIPTS_DIR := $(PROJECT_DIR)/scripts/shell-test
REPORT_DIR := $(PROJECT_DIR)/reports

# Tools
ANT ?= ant
JAVA ?= java
CURL ?= curl
XMLLINT ?= xmllint
JQ ?= jq

# Colors
RED := \033[0;31m
GREEN := \033[0;32m
YELLOW := \033[1;33m
BLUE := \033[0;34m
CYAN := \033[0;36m
BOLD := \033[1m
NC := \033[0m

# Ports (can be overridden)
ENGINE_PORT ?= 8080
A2A_PORT ?= 8082
MCP_PORT ?= 3000

# Export for test scripts
export PROJECT_DIR
export TEST_DIR
export REPORT_DIR
export ENGINE_PORT
export A2A_PORT
export MCP_PORT

.PHONY: help test test-quick test-phase clean
.PHONY: test-schema test-stub test-build test-engine test-a2a test-mcp test-patterns test-report
.PHONY: list-phases check-deps

# Default target
help:
	@echo ""
	@echo "$(CYAN)YAWL Shell-Based Testing$(NC)"
	@echo ""
	@echo "Usage: make [target]"
	@echo ""
	@echo "Targets:"
	@echo "  test          Run all test phases"
	@echo "  test-quick    Run quick tests only (phases 1-3, no server)"
	@echo "  test-phase    Run specific phase (use PHASE=01-08)"
	@echo ""
	@echo "Individual Phases:"
	@echo "  test-schema   Phase 01: Schema validation"
	@echo "  test-stub     Phase 02: Stub detection"
	@echo "  test-build    Phase 03: Build verification"
	@echo "  test-engine   Phase 04: Engine lifecycle"
	@echo "  test-a2a      Phase 05: A2A protocol"
	@echo "  test-mcp      Phase 06: MCP protocol"
	@echo "  test-patterns Phase 07: Workflow patterns"
	@echo "  test-report   Phase 08: Integration report"
	@echo ""
	@echo "Utilities:"
	@echo "  list-phases   List all test phases"
	@echo "  check-deps    Check required dependencies"
	@echo "  clean         Clean test artifacts"
	@echo ""
	@echo "Environment Variables:"
	@echo "  ENGINE_PORT   YAWL engine port (default: 8080)"
	@echo "  A2A_PORT      A2A server port (default: 8082)"
	@echo "  MCP_PORT      MCP server port (default: 3000)"
	@echo "  REPORT_DIR    Report output directory (default: reports/)"
	@echo ""

# Check dependencies
check-deps:
	@echo "Checking dependencies..."
	@echo ""
	@command -v bash >/dev/null 2>&1 && echo "  $(GREEN)✓$(NC) bash" || echo "  $(RED)✗$(NC) bash (required)"
	@command -v curl >/dev/null 2>&1 && echo "  $(GREEN)✓$(NC) curl" || echo "  $(YELLOW)?$(NC) curl (required for HTTP tests)"
	@command -v xmllint >/dev/null 2>&1 && echo "  $(GREEN)✓$(NC) xmllint" || echo "  $(YELLOW)?$(NC) xmllint (required for schema tests)"
	@command -v jq >/dev/null 2>&1 && echo "  $(GREEN)✓$(NC) jq" || echo "  $(YELLOW)?$(NC) jq (required for JSON tests)"
	@command -v nc >/dev/null 2>&1 && echo "  $(GREEN)✓$(NC) nc (netcat)" || echo "  $(YELLOW)?$(NC) nc (required for port tests)"
	@command -v ant >/dev/null 2>&1 && echo "  $(GREEN)✓$(NC) ant" || echo "  $(YELLOW)?$(NC) ant (optional, for build tests)"
	@command -v java >/dev/null 2>&1 && echo "  $(GREEN)✓$(NC) java" || echo "  $(YELLOW)?$(NC) java (optional, for engine tests)"
	@echo ""

# List all phases
list-phases:
	@echo ""
	@echo "$(CYAN)Test Phases$(NC)"
	@echo ""
	@echo "  01 - Schema Validation    : Validates XML specifications against XSD"
	@echo "  02 - Stub Detection       : Scans for forbidden patterns"
	@echo "  03 - Build Verification   : Verifies compilation succeeds"
	@echo "  04 - Engine Lifecycle     : Tests start/stop/health"
	@echo "  05 - A2A Protocol         : Tests Agent-to-Agent JSON-RPC"
	@echo "  06 - MCP Protocol         : Tests Model Context Protocol"
	@echo "  07 - Workflow Patterns    : Tests YAWL pattern execution"
	@echo "  08 - Integration Report   : Generates test reports"
	@echo ""

# Run all tests
test: test-schema test-stub test-build test-engine test-a2a test-mcp test-patterns test-report
	@echo ""
	@echo "$(GREEN)╔══════════════════════════════════════════════════════════════╗$(NC)"
	@echo "$(GREEN)║              ALL TESTS COMPLETED                             ║$(NC)"
	@echo "$(GREEN)╚══════════════════════════════════════════════════════════════╝$(NC)"
	@echo ""

# Quick tests (no server startup required)
test-quick: test-schema test-stub test-build
	@echo ""
	@echo "$(GREEN)Quick tests completed$(NC)"
	@echo ""

# Run specific phase
test-phase:
ifndef PHASE
	@echo "$(RED)Error: PHASE not specified$(NC)"
	@echo "Usage: make test-phase PHASE=01"
	@exit 1
endif
	@$(MAKE) test-$(shell echo $(PHASE) | sed 's/^0*//')

# Phase 01: Schema Validation
test-schema:
	@echo ""
	@echo "$(BOLD)═══════════════════════════════════════════════════════════════$(NC)"
	@echo "$(BOLD)Phase 01: Schema Validation$(NC)"
	@echo "$(BOLD)═══════════════════════════════════════════════════════════════$(NC)"
	@chmod +x $(TEST_DIR)/01-schema-validation/run.sh
	@$(TEST_DIR)/01-schema-validation/run.sh

# Phase 02: Stub Detection
test-stub:
	@echo ""
	@echo "$(BOLD)═══════════════════════════════════════════════════════════════$(NC)"
	@echo "$(BOLD)Phase 02: Stub Detection$(NC)"
	@echo "$(BOLD)═══════════════════════════════════════════════════════════════$(NC)"
	@chmod +x $(TEST_DIR)/02-stub-detection/run.sh
	@$(TEST_DIR)/02-stub-detection/run.sh

# Phase 03: Build Verification
test-build:
	@echo ""
	@echo "$(BOLD)═══════════════════════════════════════════════════════════════$(NC)"
	@echo "$(BOLD)Phase 03: Build Verification$(NC)"
	@echo "$(BOLD)═══════════════════════════════════════════════════════════════$(NC)"
	@chmod +x $(TEST_DIR)/03-build-verification/run.sh
	@$(TEST_DIR)/03-build-verification/run.sh

# Phase 04: Engine Lifecycle
test-engine:
	@echo ""
	@echo "$(BOLD)═══════════════════════════════════════════════════════════════$(NC)"
	@echo "$(BOLD)Phase 04: Engine Lifecycle$(NC)"
	@echo "$(BOLD)═══════════════════════════════════════════════════════════════$(NC)"
	@chmod +x $(TEST_DIR)/04-engine-lifecycle/run.sh
	@$(TEST_DIR)/04-engine-lifecycle/run.sh

# Phase 05: A2A Protocol
test-a2a:
	@echo ""
	@echo "$(BOLD)═══════════════════════════════════════════════════════════════$(NC)"
	@echo "$(BOLD)Phase 05: A2A Protocol$(NC)"
	@echo "$(BOLD)═══════════════════════════════════════════════════════════════$(NC)"
	@chmod +x $(TEST_DIR)/05-a2a-protocol/run.sh
	@$(TEST_DIR)/05-a2a-protocol/run.sh

# Phase 06: MCP Protocol
test-mcp:
	@echo ""
	@echo "$(BOLD)═══════════════════════════════════════════════════════════════$(NC)"
	@echo "$(BOLD)Phase 06: MCP Protocol$(NC)"
	@echo "$(BOLD)═══════════════════════════════════════════════════════════════$(NC)"
	@chmod +x $(TEST_DIR)/06-mcp-protocol/run.sh
	@$(TEST_DIR)/06-mcp-protocol/run.sh

# Phase 07: Workflow Patterns
test-patterns:
	@echo ""
	@echo "$(BOLD)═══════════════════════════════════════════════════════════════$(NC)"
	@echo "$(BOLD)Phase 07: Workflow Patterns$(NC)"
	@echo "$(BOLD)═══════════════════════════════════════════════════════════════$(NC)"
	@chmod +x $(TEST_DIR)/07-workflow-patterns/run.sh
	@$(TEST_DIR)/07-workflow-patterns/run.sh

# Phase 08: Integration Report
test-report:
	@echo ""
	@echo "$(BOLD)═══════════════════════════════════════════════════════════════$(NC)"
	@echo "$(BOLD)Phase 08: Integration Report$(NC)"
	@echo "$(BOLD)═══════════════════════════════════════════════════════════════$(NC)"
	@chmod +x $(TEST_DIR)/08-integration/run.sh
	@$(TEST_DIR)/08-integration/run.sh

# Clean test artifacts
clean:
	@echo "Cleaning test artifacts..."
	@rm -rf $(REPORT_DIR)
	@rm -f $(TEST_DIR)/*/*.log
	@rm -f /tmp/yawl-*.log
	@rm -f /tmp/a2a-*.log
	@rm -f /tmp/mcp-*.log
	@echo "$(GREEN)Clean complete$(NC)"

# Run the full test runner script
run:
	@chmod +x $(SCRIPTS_DIR)/runner.sh
	@$(SCRIPTS_DIR)/runner.sh $(ARGS)

# ========================================
# Resilience4j Operations
# ========================================

.PHONY: resilience-metrics resilience-health resilience-events resilience-test

# View resilience metrics
resilience-metrics:
	@echo "$(CYAN)$(BOLD)Fetching Resilience4j Metrics...$(NC)"
	@curl -s http://localhost:$(ENGINE_PORT)/actuator/metrics | jq '.names[] | select(. | contains("resilience4j"))'
	@echo ""
	@echo "$(CYAN)Circuit Breaker States:$(NC)"
	@curl -s http://localhost:$(ENGINE_PORT)/actuator/metrics/resilience4j.circuitbreaker.state | jq .
	@echo ""
	@echo "$(CYAN)Failure Rates:$(NC)"
	@curl -s http://localhost:$(ENGINE_PORT)/actuator/metrics/resilience4j.circuitbreaker.failure.rate | jq .
	@echo ""
	@echo "$(CYAN)Retry Metrics:$(NC)"
	@curl -s http://localhost:$(ENGINE_PORT)/actuator/metrics/resilience4j.retry.calls | jq .

# View health status
resilience-health:
	@echo "$(CYAN)$(BOLD)Fetching Health Status...$(NC)"
	@curl -s http://localhost:$(ENGINE_PORT)/actuator/health | jq .

# View circuit breaker events
resilience-events:
	@echo "$(CYAN)$(BOLD)Fetching Circuit Breaker Events...$(NC)"
	@curl -s http://localhost:$(ENGINE_PORT)/actuator/circuitbreakerevents | jq .

# Run resilience tests
resilience-test:
	@echo "$(CYAN)$(BOLD)Running Resilience4j Tests...$(NC)"
	@mvn test -Dtest=ResilienceProviderTest

# Export Prometheus metrics
resilience-prometheus:
	@echo "$(CYAN)$(BOLD)Fetching Prometheus Metrics...$(NC)"
	@curl -s http://localhost:$(ENGINE_PORT)/actuator/prometheus | grep resilience4j

# View resilience configuration
resilience-config:
	@echo "$(CYAN)$(BOLD)Current Resilience Configuration:$(NC)"
	@cat config/resilience/resilience4j.yml

# Resilience dashboard (requires jq)
resilience-dashboard:
	@echo "$(CYAN)$(BOLD)YAWL Resilience Dashboard$(NC)"
	@echo "$(YELLOW)=====================================$(NC)"
	@echo ""
	@echo "$(BOLD)Circuit Breakers:$(NC)"
	@curl -s http://localhost:$(ENGINE_PORT)/actuator/metrics/resilience4j.circuitbreaker.state | \
		jq -r '.measurements[] | "  State: \(.value) (0=CLOSED, 1=OPEN, 2=HALF_OPEN)"'
	@echo ""
	@echo "$(BOLD)Retry Statistics:$(NC)"
	@curl -s http://localhost:$(ENGINE_PORT)/actuator/metrics/resilience4j.retry.calls | \
		jq -r '.measurements[] | "  Total Calls: \(.value)"'
	@echo ""
	@echo "$(BOLD)Rate Limiters:$(NC)"
	@curl -s http://localhost:$(ENGINE_PORT)/actuator/metrics/resilience4j.ratelimiter.available.permissions | \
		jq -r '.measurements[] | "  Available Permissions: \(.value)"'
	@echo ""
	@echo "$(BOLD)Bulkheads:$(NC)"
	@curl -s http://localhost:$(ENGINE_PORT)/actuator/metrics/resilience4j.bulkhead.available.concurrent.calls | \
		jq -r '.measurements[] | "  Available Concurrent Calls: \(.value)"'
	@echo "$(YELLOW)=====================================$(NC)"

# Help for resilience targets
resilience-help:
	@echo "$(CYAN)$(BOLD)YAWL Resilience4j Makefile Targets$(NC)"
	@echo ""
	@echo "$(BOLD)Monitoring:$(NC)"
	@echo "  resilience-metrics      View all resilience metrics"
	@echo "  resilience-health       View health status"
	@echo "  resilience-events       View circuit breaker events"
	@echo "  resilience-prometheus   Export Prometheus metrics"
	@echo "  resilience-dashboard    Interactive dashboard"
	@echo ""
	@echo "$(BOLD)Testing:$(NC)"
	@echo "  resilience-test         Run resilience test suite"
	@echo ""
	@echo "$(BOLD)Configuration:$(NC)"
	@echo "  resilience-config       View current configuration"
	@echo ""
	@echo "$(BOLD)Documentation:$(NC)"
	@echo "  docs/resilience/README.md                      Platform overview"
	@echo "  docs/resilience/RESILIENCE_OPERATIONS_GUIDE.md Tuning guide"
	@echo "  docs/resilience/QUICK_START.md                 5-minute setup"
	@echo ""

# ========================================
# V6 Observatory Control Plane
# ========================================

.PHONY: verify observatory closure observatory-help

# Observatory output directories
V6_LATEST := $(PROJECT_DIR)/docs/v6/latest
V6_RECEIPTS := $(V6_LATEST)/receipts
OBSERVATORY_SCRIPT := $(PROJECT_DIR)/scripts/observatory/observatory.sh

# Run Maven verify gates and emit a receipt
verify:
	@echo ""
	@echo "$(CYAN)$(BOLD)═══════════════════════════════════════════════════════════$(NC)"
	@echo "$(CYAN)$(BOLD) V6 Control Plane: verify$(NC)"
	@echo "$(CYAN)$(BOLD)═══════════════════════════════════════════════════════════$(NC)"
	@echo ""
	@mkdir -p $(V6_RECEIPTS)
	@RUN_ID=$$(date -u +%Y%m%dT%H%M%SZ); \
	VERIFY_START=$$(date +%s); \
	MVN_CMD="mvn -T 1.5C clean verify -DskipITs=true"; \
	echo "[verify] Running: $$MVN_CMD"; \
	if $$MVN_CMD > $(V6_RECEIPTS)/verify.log 2>&1; then \
		VERIFY_EXIT=0; \
		VERIFY_STATUS="GREEN"; \
	else \
		VERIFY_EXIT=$$?; \
		VERIFY_STATUS="RED"; \
	fi; \
	VERIFY_END=$$(date +%s); \
	VERIFY_DURATION=$$(( (VERIFY_END - VERIFY_START) * 1000 )); \
	LOG_SHA=$$(sha256sum $(V6_RECEIPTS)/verify.log 2>/dev/null | awk '{print "sha256:" $$1}' || echo "sha256:unavailable"); \
	printf '{\n  "run_id": "%s",\n  "status": "%s",\n  "target": "verify",\n  "command": "%s",\n  "exit_code": %d,\n  "duration_ms": %d,\n  "refusals": [],\n  "outputs": {\n    "log_ref": "docs/v6/latest/receipts/verify.log",\n    "sha256": "%s"\n  }\n}\n' \
		"$$RUN_ID" "$$VERIFY_STATUS" "$$MVN_CMD" "$$VERIFY_EXIT" "$$VERIFY_DURATION" "$$LOG_SHA" \
		> $(V6_RECEIPTS)/verify.json; \
	echo ""; \
	echo "STATUS=$$VERIFY_STATUS RUN_ID=$$RUN_ID RECEIPT=docs/v6/latest/receipts/verify.json"

# Generate observatory (facts + diagrams + YAWL XML + receipt)
observatory:
	@echo ""
	@echo "$(CYAN)$(BOLD)═══════════════════════════════════════════════════════════$(NC)"
	@echo "$(CYAN)$(BOLD) V6 Control Plane: observatory$(NC)"
	@echo "$(CYAN)$(BOLD)═══════════════════════════════════════════════════════════$(NC)"
	@echo ""
	@chmod +x $(OBSERVATORY_SCRIPT)
	@bash $(OBSERVATORY_SCRIPT)

# Full closure: verify + observatory
closure: verify observatory
	@echo ""
	@echo "$(GREEN)$(BOLD)═══════════════════════════════════════════════════════════$(NC)"
	@echo "$(GREEN)$(BOLD) V6 Control Plane: closure COMPLETE$(NC)"
	@echo "$(GREEN)$(BOLD)═══════════════════════════════════════════════════════════$(NC)"
	@echo ""
	@echo "Receipts:"
	@echo "  - docs/v6/latest/receipts/verify.json"
	@echo "  - docs/v6/latest/receipts/observatory.json"
	@echo ""
	@echo "Index:  docs/v6/latest/INDEX.md"
	@echo ""
	@if [ -f $(V6_RECEIPTS)/observatory.json ]; then \
		OBS_STATUS=$$(grep -oP '(?<="status": ")[^"]+' $(V6_RECEIPTS)/observatory.json | head -1); \
		VER_STATUS=$$(grep -oP '(?<="status": ")[^"]+' $(V6_RECEIPTS)/verify.json | head -1); \
		if [ "$$OBS_STATUS" = "GREEN" ] && [ "$$VER_STATUS" = "GREEN" ]; then \
			FINAL="GREEN"; \
		elif [ "$$OBS_STATUS" = "RED" ] || [ "$$VER_STATUS" = "RED" ]; then \
			FINAL="RED"; \
		else \
			FINAL="YELLOW"; \
		fi; \
		echo "STATUS=$$FINAL CLOSURE=COMPLETE"; \
	fi

# Observatory help
observatory-help:
	@echo ""
	@echo "$(CYAN)$(BOLD)V6 Observatory Control Plane$(NC)"
	@echo ""
	@echo "$(BOLD)Targets:$(NC)"
	@echo "  verify         Run Maven verify gates; emit docs/v6/latest/receipts/verify.json"
	@echo "  observatory    Generate facts + diagrams + YAWL XML + receipt"
	@echo "  closure        verify then observatory (full pipeline)"
	@echo ""
	@echo "$(BOLD)Output:$(NC)"
	@echo "  docs/v6/latest/INDEX.md                Entry point"
	@echo "  docs/v6/latest/receipts/*.json         Machine-readable receipts"
	@echo "  docs/v6/latest/facts/*.json            9 fact files"
	@echo "  docs/v6/latest/diagrams/*.mmd          7 Mermaid diagrams"
	@echo "  docs/v6/latest/diagrams/yawl/*.xml     YAWL build workflow"
	@echo ""
