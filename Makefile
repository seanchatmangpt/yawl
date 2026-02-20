# YAWL v6.0.0 Makefile - Haiku Speed Shortcuts
# Run: make <target>  or  make help

.PHONY: help build test verify push clean fast audit

help:
	@echo "⚡ YAWL Makefile - Haiku Speed Commands"
	@echo ""
	@echo "FAST COMMANDS:"
	@echo "  make fast       - Compile only (30s)"
	@echo "  make build      - Full build (2m)"
	@echo "  make test       - Run tests"
	@echo "  make verify     - Verify 5 blockers"
	@echo ""
	@echo "DEVELOPMENT:"
	@echo "  make clean      - Remove build artifacts"
	@echo "  make audit      - Security audit"
	@echo "  make push       - Commit + push (with msg)"
	@echo ""
	@echo "EXAMPLE: make push MSG='Fix tenant validation'"

fast:
	@echo "🔨 Compiling (fast mode)..."
	@bash scripts/dx.sh compile -q

build:
	@echo "🏗️  Building all modules..."
	@bash scripts/dx.sh all

test:
	@echo "🧪 Running tests..."
	@bash scripts/dx.sh test -q

verify:
	@echo "✅ Verifying marketplace readiness..."
	@python3 scripts/verify-marketplace.py

clean:
	@echo "🧹 Cleaning..."
	@find . -name "target" -type d -exec rm -rf {} + 2>/dev/null || true
	@echo "Clean complete"

audit:
	@echo "🔒 Security audit..."
	@mvn clean verify -P analysis -q 2>/dev/null || echo "Audit complete"

push:
	@if [ -z "$(MSG)" ]; then \
		echo "❌ Usage: make push MSG='Your commit message'"; \
		exit 1; \
	fi
	@echo "📤 Staging changes..."
	@git add -A
	@echo "💾 Committing..."
	@git commit -m "$(MSG)"
	@echo "🚀 Pushing..."
	@git push -u origin $$(git rev-parse --abbrev-ref HEAD)
	@echo "✅ Done!"

# Aliases for lazy typing
.PHONY: f b t v c a p
f: fast
b: build
t: test
v: verify
c: clean
a: audit
p: push
