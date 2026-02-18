# ==========================================================================
# Makefile — YAWL V6 Code Analysis Observatory
#
# Build automation for observatory analysis scripts.
# Provides targets for generating facts, diagrams, YAWL XML, and validation.
#
# Usage:
#   make              # Run full observatory analysis (default: all)
#   make facts        # Generate facts only
#   make diagrams     # Generate diagrams only
#   make yawl-xml     # Generate YAWL XML only
#   make clean        # Clean generated files
#   make validate     # Validate outputs
#   make help         # Show available targets
#
# Output: docs/v6/latest/{INDEX.md,receipts/,facts/,diagrams/}
# ==========================================================================

SHELL := /bin/bash
SCRIPT_DIR := $(dir $(realpath $(lastword $(MAKEFILE_LIST))))
OBSERVATORY := $(SCRIPT_DIR)observatory.sh
OUTPUT_DIR := $(SCRIPT_DIR)../../docs/v6/latest

# Colors for output
CYAN := \033[36m
GREEN := \033[32m
YELLOW := \033[33m
RESET := \033[0m

.PHONY: all facts diagrams yawl-xml clean validate help integration static-analysis

# Default target
all: ## Run full observatory analysis (facts, diagrams, YAWL XML, integration, static analysis, receipt)
	@echo "$(CYAN)Running full observatory analysis...$(RESET)"
	@$(OBSERVATORY)

facts: ## Generate fact JSON files only
	@echo "$(CYAN)Generating facts...$(RESET)"
	@$(OBSERVATORY) --facts

diagrams: ## Generate Mermaid diagrams only
	@echo "$(CYAN)Generating diagrams...$(RESET)"
	@$(OBSERVATORY) --diagrams

yawl-xml: ## Generate YAWL XML specifications only
	@echo "$(CYAN)Generating YAWL XML...$(RESET)"
	@$(OBSERVATORY) --yawl

integration: ## Generate MCP/A2A integration diagrams only
	@echo "$(CYAN)Generating integration diagrams...$(RESET)"
	@$(OBSERVATORY) --integration

static-analysis: ## Generate static analysis facts and diagrams only
	@echo "$(CYAN)Generating static analysis...$(RESET)"
	@$(OBSERVATORY) --static-analysis

clean: ## Clean all generated files in docs/v6/latest/
	@echo "$(YELLOW)Cleaning generated files...$(RESET)"
	@rm -rf $(OUTPUT_DIR)/facts
	@rm -rf $(OUTPUT_DIR)/diagrams
	@rm -rf $(OUTPUT_DIR)/yawl
	@rm -rf $(OUTPUT_DIR)/receipts
	@rm -rf $(OUTPUT_DIR)/performance
	@rm -f $(OUTPUT_DIR)/INDEX.md
	@echo "$(GREEN)Clean complete.$(RESET)"

validate: ## Validate generated outputs exist and are well-formed
	@echo "$(CYAN)Validating outputs...$(RESET)"
	@echo "Checking facts directory..."
	@test -d $(OUTPUT_DIR)/facts || { echo "ERROR: facts directory missing"; exit 1; }
	@echo "Checking diagrams directory..."
	@test -d $(OUTPUT_DIR)/diagrams || { echo "ERROR: diagrams directory missing"; exit 1; }
	@echo "Checking yawl directory..."
	@test -d $(OUTPUT_DIR)/yawl || { echo "ERROR: yawl directory missing"; exit 1; }
	@echo "Checking receipts directory..."
	@test -d $(OUTPUT_DIR)/receipts || { echo "ERROR: receipts directory missing"; exit 1; }
	@echo "Checking INDEX.md..."
	@test -f $(OUTPUT_DIR)/INDEX.md || { echo "ERROR: INDEX.md missing"; exit 1; }
	@echo "Validating JSON files..."
	@for f in $(OUTPUT_DIR)/facts/*.json; do \
		test -f "$$f" && python3 -m json.tool "$$f" > /dev/null 2>&1 || echo "WARNING: Invalid JSON: $$f"; \
	done
	@echo "Validating XML files..."
	@for f in $(OUTPUT_DIR)/yawl/*.xml; do \
		test -f "$$f" && xmllint --noout "$$f" 2>/dev/null || echo "WARNING: Invalid XML: $$f"; \
	done
	@echo "$(GREEN)Validation complete.$(RESET)"

help: ## Show this help message
	@echo ""
	@echo "YAWL V6 Code Analysis Observatory"
	@echo "=================================="
	@echo ""
	@echo "Usage: make [target]"
	@echo ""
	@echo "Targets:"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  $(CYAN)%-18s$(RESET) %s\n", $$1, $$2}'
	@echo ""
	@echo "Examples:"
	@echo "  make              # Run full analysis"
	@echo "  make facts        # Generate facts only"
	@echo "  make validate     # Validate outputs"
	@echo "  make clean all    # Clean and regenerate"
	@echo ""
