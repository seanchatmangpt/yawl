# YAWL Helm Chart Makefile
# Multi-cloud packaging and deployment

SHELL := /bin/bash
HELM := helm
KUBECTL := kubectl
CHART_DIR := .
CHART_NAME := yawl
CHART_VERSION := 5.2.0
NAMESPACE := yawl
RELEASE_NAME := yawl

# Cloud provider values files
VALUES_FILES := values.yaml values-gcp.yaml values-aws.yaml values-azure.yaml values-oracle.yaml values-ibm.yaml

# Repository settings
REGISTRY ?= docker.io
REPOSITORY ?= yawl
HELM_REPO ?= https://yawlfoundation.github.io/charts

# Output directory
DIST_DIR := dist

.PHONY: all help clean lint template package package-all publish install uninstall upgrade test

all: lint template package

## ============================================================================
## Development
## ============================================================================

help: ## Show this help message
	@echo 'YAWL Helm Chart Makefile'
	@echo ''
	@echo 'Usage: make [target]'
	@echo ''
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'

clean: ## Clean build artifacts
	@echo "Cleaning build artifacts..."
	rm -rf $(DIST_DIR)
	rm -f *.tgz

lint: ## Lint the Helm chart
	@echo "Linting Helm chart..."
	$(HELM) lint $(CHART_DIR)
	$(HELM) lint $(CHART_DIR)/charts/postgresql
	$(HELM) lint $(CHART_DIR)/charts/redis

template: ## Render templates without installing
	@echo "Rendering templates..."
	$(HELM) template $(RELEASE_NAME) $(CHART_DIR) --namespace $(NAMESPACE) --debug > $(DIST_DIR)/rendered.yaml

template-gcp: ## Render GCP-specific templates
	@echo "Rendering GCP templates..."
	mkdir -p $(DIST_DIR)
	$(HELM) template $(RELEASE_NAME) $(CHART_DIR) \
		--namespace $(NAMESPACE) \
		--values $(CHART_DIR)/values-gcp.yaml \
		--debug > $(DIST_DIR)/rendered-gcp.yaml

template-aws: ## Render AWS-specific templates
	@echo "Rendering AWS templates..."
	mkdir -p $(DIST_DIR)
	$(HELM) template $(RELEASE_NAME) $(CHART_DIR) \
		--namespace $(NAMESPACE) \
		--values $(CHART_DIR)/values-aws.yaml \
		--debug > $(DIST_DIR)/rendered-aws.yaml

template-azure: ## Render Azure-specific templates
	@echo "Rendering Azure templates..."
	mkdir -p $(DIST_DIR)
	$(HELM) template $(RELEASE_NAME) $(CHART_DIR) \
		--namespace $(NAMESPACE) \
		--values $(CHART_DIR)/values-azure.yaml \
		--debug > $(DIST_DIR)/rendered-azure.yaml

template-oracle: ## Render Oracle Cloud templates
	@echo "Rendering Oracle Cloud templates..."
	mkdir -p $(DIST_DIR)
	$(HELM) template $(RELEASE_NAME) $(CHART_DIR) \
		--namespace $(NAMESPACE) \
		--values $(CHART_DIR)/values-oracle.yaml \
		--debug > $(DIST_DIR)/rendered-oracle.yaml

template-ibm: ## Render IBM Cloud templates
	@echo "Rendering IBM Cloud templates..."
	mkdir -p $(DIST_DIR)
	$(HELM) template $(RELEASE_NAME) $(CHART_DIR) \
		--namespace $(NAMESPACE) \
		--values $(CHART_DIR)/values-ibm.yaml \
		--debug > $(DIST_DIR)/rendered-ibm.yaml

## ============================================================================
## Packaging
## ============================================================================

package: ## Package the Helm chart
	@echo "Packaging Helm chart..."
	mkdir -p $(DIST_DIR)
	$(HELM) package $(CHART_DIR) \
		--version $(CHART_VERSION) \
		--destination $(DIST_DIR)

package-all: package package-gcp package-aws package-azure package-oracle package-ibm ## Package for all cloud providers

package-gcp: ## Package chart with GCP values
	@echo "Packaging GCP-specific chart..."
	mkdir -p $(DIST_DIR)/gcp
	$(HELM) template $(RELEASE_NAME) $(CHART_DIR) \
		--namespace $(NAMESPACE) \
		--values $(CHART_DIR)/values-gcp.yaml | \
		$(KUBECTL) apply --dry-run=client -f - > /dev/null
	cp $(CHART_DIR)/values-gcp.yaml $(DIST_DIR)/gcp/

package-aws: ## Package chart with AWS values
	@echo "Packaging AWS-specific chart..."
	mkdir -p $(DIST_DIR)/aws
	$(HELM) template $(RELEASE_NAME) $(CHART_DIR) \
		--namespace $(NAMESPACE) \
		--values $(CHART_DIR)/values-aws.yaml | \
		$(KUBECTL) apply --dry-run=client -f - > /dev/null
	cp $(CHART_DIR)/values-aws.yaml $(DIST_DIR)/aws/

package-azure: ## Package chart with Azure values
	@echo "Packaging Azure-specific chart..."
	mkdir -p $(DIST_DIR)/azure
	$(HELM) template $(RELEASE_NAME) $(CHART_DIR) \
		--namespace $(NAMESPACE) \
		--values $(CHART_DIR)/values-azure.yaml | \
		$(KUBECTL) apply --dry-run=client -f - > /dev/null
	cp $(CHART_DIR)/values-azure.yaml $(DIST_DIR)/azure/

package-oracle: ## Package chart with Oracle Cloud values
	@echo "Packaging Oracle Cloud-specific chart..."
	mkdir -p $(DIST_DIR)/oracle
	$(HELM) template $(RELEASE_NAME) $(CHART_DIR) \
		--namespace $(NAMESPACE) \
		--values $(CHART_DIR)/values-oracle.yaml | \
		$(KUBECTL) apply --dry-run=client -f - > /dev/null
	cp $(CHART_DIR)/values-oracle.yaml $(DIST_DIR)/oracle/

package-ibm: ## Package chart with IBM Cloud values
	@echo "Packaging IBM Cloud-specific chart..."
	mkdir -p $(DIST_DIR)/ibm
	$(HELM) template $(RELEASE_NAME) $(CHART_DIR) \
		--namespace $(NAMESPACE) \
		--values $(CHART_DIR)/values-ibm.yaml | \
		$(KUBECTL) apply --dry-run=client -f - > /dev/null
	cp $(CHART_DIR)/values-ibm.yaml $(DIST_DIR)/ibm/

## ============================================================================
## Publishing
## ============================================================================

publish: package ## Publish chart to repository
	@echo "Publishing Helm chart..."
	$(HELM) push $(DIST_DIR)/$(CHART_NAME)-$(CHART_VERSION).tgz oci://$(REGISTRY)/$(REPOSITORY)

publish-index: ## Generate and update index
	@echo "Generating Helm repository index..."
	$(HELM) repo index $(DIST_DIR) --url $(HELM_REPO)

## ============================================================================
## Deployment
## ============================================================================

install: ## Install the Helm chart
	@echo "Installing YAWL Helm chart..."
	$(KUBECTL) create namespace $(NAMESPACE) --dry-run=client -o yaml | $(KUBECTL) apply -f -
	$(HELM) install $(RELEASE_NAME) $(CHART_DIR) \
		--namespace $(NAMESPACE) \
		--timeout 10m \
		--wait

install-gcp: ## Install on GCP GKE
	@echo "Installing YAWL on GCP GKE..."
	$(KUBECTL) create namespace $(NAMESPACE) --dry-run=client -o yaml | $(KUBECTL) apply -f -
	$(HELM) install $(RELEASE_NAME) $(CHART_DIR) \
		--namespace $(NAMESPACE) \
		--values $(CHART_DIR)/values-gcp.yaml \
		--timeout 15m \
		--wait

install-aws: ## Install on AWS EKS
	@echo "Installing YAWL on AWS EKS..."
	$(KUBECTL) create namespace $(NAMESPACE) --dry-run=client -o yaml | $(KUBECTL) apply -f -
	$(HELM) install $(RELEASE_NAME) $(CHART_DIR) \
		--namespace $(NAMESPACE) \
		--values $(CHART_DIR)/values-aws.yaml \
		--timeout 15m \
		--wait

install-azure: ## Install on Azure AKS
	@echo "Installing YAWL on Azure AKS..."
	$(KUBECTL) create namespace $(NAMESPACE) --dry-run=client -o yaml | $(KUBECTL) apply -f -
	$(HELM) install $(RELEASE_NAME) $(CHART_DIR) \
		--namespace $(NAMESPACE) \
		--values $(CHART_DIR)/values-azure.yaml \
		--timeout 15m \
		--wait

install-oracle: ## Install on Oracle Cloud OKE
	@echo "Installing YAWL on Oracle Cloud OKE..."
	$(KUBECTL) create namespace $(NAMESPACE) --dry-run=client -o yaml | $(KUBECTL) apply -f -
	$(HELM) install $(RELEASE_NAME) $(CHART_DIR) \
		--namespace $(NAMESPACE) \
		--values $(CHART_DIR)/values-oracle.yaml \
		--timeout 15m \
		--wait

install-ibm: ## Install on IBM Cloud IKS/ROKS
	@echo "Installing YAWL on IBM Cloud..."
	$(KUBECTL) create namespace $(NAMESPACE) --dry-run=client -o yaml | $(KUBECTL) apply -f -
	$(HELM) install $(RELEASE_NAME) $(CHART_DIR) \
		--namespace $(NAMESPACE) \
		--values $(CHART_DIR)/values-ibm.yaml \
		--timeout 15m \
		--wait

uninstall: ## Uninstall the Helm chart
	@echo "Uninstalling YAWL Helm chart..."
	$(HELM) uninstall $(RELEASE_NAME) --namespace $(NAMESPACE) || true

upgrade: ## Upgrade the Helm chart
	@echo "Upgrading YAWL Helm chart..."
	$(HELM) upgrade $(RELEASE_NAME) $(CHART_DIR) \
		--namespace $(NAMESPACE) \
		--timeout 10m \
		--wait

upgrade-gcp: ## Upgrade on GCP
	$(HELM) upgrade $(RELEASE_NAME) $(CHART_DIR) \
		--namespace $(NAMESPACE) \
		--values $(CHART_DIR)/values-gcp.yaml \
		--timeout 15m \
		--wait

upgrade-aws: ## Upgrade on AWS
	$(HELM) upgrade $(RELEASE_NAME) $(CHART_DIR) \
		--namespace $(NAMESPACE) \
		--values $(CHART_DIR)/values-aws.yaml \
		--timeout 15m \
		--wait

## ============================================================================
## Testing
## ============================================================================

test: ## Run Helm chart tests
	@echo "Running Helm tests..."
	$(HELM) test $(RELEASE_NAME) --namespace $(NAMESPACE)

test-unit: ## Run unit tests with helm-unittest plugin
	@echo "Running unit tests..."
	$(HELM) unittest $(CHART_DIR) -f tests/*.yaml

validate: lint ## Validate chart syntax and templates
	@echo "Validating chart..."
	$(HELM) template $(RELEASE_NAME) $(CHART_DIR) \
		--namespace $(NAMESPACE) | \
		$(KUBECTL) apply --dry-run=client -f -

validate-all: validate validate-gcp validate-aws validate-azure validate-oracle validate-ibm ## Validate all cloud provider templates

validate-gcp:
	@echo "Validating GCP templates..."
	$(HELM) template $(RELEASE_NAME) $(CHART_DIR) \
		--namespace $(NAMESPACE) \
		--values $(CHART_DIR)/values-gcp.yaml | \
		$(KUBECTL) apply --dry-run=client -f -

validate-aws:
	@echo "Validating AWS templates..."
	$(HELM) template $(RELEASE_NAME) $(CHART_DIR) \
		--namespace $(NAMESPACE) \
		--values $(CHART_DIR)/values-aws.yaml | \
		$(KUBECTL) apply --dry-run=client -f -

validate-azure:
	@echo "Validating Azure templates..."
	$(HELM) template $(RELEASE_NAME) $(CHART_DIR) \
		--namespace $(NAMESPACE) \
		--values $(CHART_DIR)/values-azure.yaml | \
		$(KUBECTL) apply --dry-run=client -f -

validate-oracle:
	@echo "Validating Oracle Cloud templates..."
	$(HELM) template $(RELEASE_NAME) $(CHART_DIR) \
		--namespace $(NAMESPACE) \
		--values $(CHART_DIR)/values-oracle.yaml | \
		$(KUBECTL) apply --dry-run=client -f -

validate-ibm:
	@echo "Validating IBM Cloud templates..."
	$(HELM) template $(RELEASE_NAME) $(CHART_DIR) \
		--namespace $(NAMESPACE) \
		--values $(CHART_DIR)/values-ibm.yaml | \
		$(KUBECTL) apply --dry-run=client -f -

## ============================================================================
## Utility
## ============================================================================

status: ## Show release status
	$(HELM) status $(RELEASE_NAME) --namespace $(NAMESPACE)

history: ## Show release history
	$(HELM) history $(RELEASE_NAME) --namespace $(NAMESPACE)

rollback: ## Rollback to previous revision
	$(HELM) rollback $(RELEASE_NAME) --namespace $(NAMESPACE)

get-values: ## Get current release values
	$(HELM) get values $(RELEASE_NAME) --namespace $(NAMESPACE) --all

get-manifest: ## Get current release manifest
	$(HELM) get manifest $(RELEASE_NAME) --namespace $(NAMESPACE)

diff: ## Show diff between current and proposed (requires helm-diff plugin)
	$(HELM) diff upgrade $(RELEASE_NAME) $(CHART_DIR) --namespace $(NAMESPACE)

dependency-update: ## Update chart dependencies
	$(HELM) dependency update $(CHART_DIR)

dependency-build: ## Build chart dependencies
	$(HELM) dependency build $(CHART_DIR)

## ============================================================================
## CI/CD
## ============================================================================

ci: clean lint validate-all package ## Run CI pipeline
	@echo "CI pipeline completed successfully"

ci-test: validate-all test-unit ## Run CI test pipeline
	@echo "CI test pipeline completed successfully"

release: clean package publish ## Create a release
	@echo "Release $(CHART_VERSION) completed successfully"
