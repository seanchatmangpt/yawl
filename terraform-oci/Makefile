.PHONY: help init plan apply destroy fmt validate clean output cost-estimate refresh state-show state-list

# Variables
TF_DIR := .
TF_VARS := terraform.tfvars
TF_PLAN := tfplan.json
TF_LOCK := .terraform.lock.hcl

# Colors for output
BLUE := \033[0;34m
GREEN := \033[0;32m
RED := \033[0;31m
YELLOW := \033[0;33m
NC := \033[0m # No Color

help: ## Show this help message
	@echo "$(BLUE)YAWL Terraform OCI Module - Available Commands$(NC)"
	@echo ""
	@echo "$(GREEN)Initialization:$(NC)"
	@echo "  make init              Initialize Terraform working directory"
	@echo ""
	@echo "$(GREEN)Planning & Validation:$(NC)"
	@echo "  make plan              Plan infrastructure changes"
	@echo "  make validate          Validate Terraform configuration"
	@echo "  make fmt               Format Terraform files"
	@echo "  make fmt-check         Check if Terraform files are properly formatted"
	@echo ""
	@echo "$(GREEN)Deployment:$(NC)"
	@echo "  make apply             Apply infrastructure changes"
	@echo "  make apply-auto        Apply without prompting for confirmation"
	@echo ""
	@echo "$(GREEN)Monitoring:$(NC)"
	@echo "  make output            Show all outputs"
	@echo "  make refresh           Refresh Terraform state"
	@echo "  make state-show        Show complete Terraform state"
	@echo "  make state-list        List resources in state"
	@echo ""
	@echo "$(GREEN)Cleanup:$(NC)"
	@echo "  make destroy           Destroy all infrastructure"
	@echo "  make destroy-auto      Destroy without prompting"
	@echo "  make clean             Clean Terraform temporary files"
	@echo ""
	@echo "$(GREEN)Utilities:$(NC)"
	@echo "  make cost-estimate     Estimate infrastructure costs"
	@echo "  make backend-setup     Configure remote state backend"
	@echo "  make docs              Generate module documentation"

init: ## Initialize Terraform
	@echo "$(BLUE)Initializing Terraform...$(NC)"
	terraform init
	@echo "$(GREEN)✓ Terraform initialized$(NC)"

validate: ## Validate Terraform configuration
	@echo "$(BLUE)Validating Terraform configuration...$(NC)"
	terraform validate
	@echo "$(GREEN)✓ Configuration is valid$(NC)"

fmt: ## Format Terraform files
	@echo "$(BLUE)Formatting Terraform files...$(NC)"
	terraform fmt -recursive .
	@echo "$(GREEN)✓ Files formatted$(NC)"

fmt-check: ## Check if Terraform files are properly formatted
	@echo "$(BLUE)Checking Terraform file formatting...$(NC)"
	terraform fmt -recursive -check .
	@echo "$(GREEN)✓ All files properly formatted$(NC)"

plan: ## Plan infrastructure changes
	@echo "$(BLUE)Planning infrastructure changes...$(NC)"
	@if [ ! -f "$(TF_VARS)" ]; then \
		echo "$(RED)Error: $(TF_VARS) not found$(NC)"; \
		echo "$(YELLOW)Create it from terraform.tfvars.example:$(NC)"; \
		echo "  cp terraform.tfvars.example terraform.tfvars"; \
		exit 1; \
	fi
	terraform plan -var-file="$(TF_VARS)" -out="$(TF_PLAN)"
	@echo "$(GREEN)✓ Plan saved to $(TF_PLAN)$(NC)"

apply: ## Apply infrastructure changes (interactive)
	@echo "$(BLUE)Applying infrastructure changes...$(NC)"
	@if [ ! -f "$(TF_PLAN)" ]; then \
		echo "$(YELLOW)Plan file not found, running terraform plan first...$(NC)"; \
		make plan; \
	fi
	terraform apply "$(TF_PLAN)"
	@echo "$(GREEN)✓ Infrastructure applied successfully$(NC)"

apply-auto: ## Apply infrastructure changes (non-interactive)
	@echo "$(BLUE)Applying infrastructure changes (auto-approval)...$(NC)"
	terraform apply -auto-approve -var-file="$(TF_VARS)"
	@echo "$(GREEN)✓ Infrastructure applied successfully$(NC)"

destroy: ## Destroy infrastructure (interactive)
	@echo "$(RED)WARNING: This will destroy all infrastructure!$(NC)"
	@echo "$(YELLOW)Press Ctrl+C to cancel, or Enter to continue...$(NC)"
	@read
	terraform destroy -var-file="$(TF_VARS)"
	@echo "$(GREEN)✓ Infrastructure destroyed$(NC)"

destroy-auto: ## Destroy infrastructure (non-interactive)
	@echo "$(RED)WARNING: This will destroy all infrastructure automatically!$(NC)"
	terraform destroy -auto-approve -var-file="$(TF_VARS)"
	@echo "$(GREEN)✓ Infrastructure destroyed$(NC)"

output: ## Show all Terraform outputs
	@echo "$(BLUE)Terraform Outputs:$(NC)"
	terraform output
	@echo ""
	@echo "$(GREEN)✓ Outputs displayed$(NC)"

refresh: ## Refresh Terraform state
	@echo "$(BLUE)Refreshing Terraform state...$(NC)"
	terraform refresh -var-file="$(TF_VARS)"
	@echo "$(GREEN)✓ State refreshed$(NC)"

state-show: ## Show complete Terraform state
	@echo "$(BLUE)Terraform State:$(NC)"
	terraform show

state-list: ## List all resources in state
	@echo "$(BLUE)Resources in Terraform State:$(NC)"
	terraform state list

state-pull: ## Pull remote state to local
	@echo "$(BLUE)Pulling remote state...$(NC)"
	terraform state pull > terraform.tfstate.backup
	@echo "$(GREEN)✓ State saved to terraform.tfstate.backup$(NC)"

clean: ## Clean Terraform temporary files
	@echo "$(BLUE)Cleaning temporary files...$(NC)"
	rm -rf .terraform
	rm -f "$(TF_PLAN)"
	rm -f ".terraform.lock.hcl"
	rm -f "*.tfstate*"
	rm -f "crash.log"
	rm -f ".terraform.tfstate*"
	@echo "$(GREEN)✓ Cleaned temporary files$(NC)"

cost-estimate: ## Estimate infrastructure costs
	@echo "$(BLUE)Infrastructure Cost Estimation:$(NC)"
	@echo ""
	@echo "$(YELLOW)Compute Instances (2x VM.Standard.E2.1.Micro):$(NC)"
	@echo "  - Free tier (always): $0/month (750 hours/month for 2 instances)"
	@echo "  - After free tier: ~$2-5/month"
	@echo ""
	@echo "$(YELLOW)Block Storage (100GB per instance):$(NC)"
	@echo "  - Free tier (always): $0/month (200GB available)"
	@echo "  - Beyond free tier: ~$10/month per 100GB"
	@echo ""
	@echo "$(YELLOW)MySQL Database (MySQL.VM.Standard.E3.1.8GB):$(NC)"
	@echo "  - Monthly: ~$50-80 (varies by region)"
	@echo ""
	@echo "$(YELLOW)Load Balancer (Flexible shape):$(NC)"
	@echo "  - Base: $0 (free tier eligible)"
	@echo "  - Per processed GB: ~$0.005"
	@echo ""
	@echo "$(YELLOW)Network (Data transfer):$(NC)"
	@echo "  - Ingress: Always free"
	@echo "  - Egress: First 10GB free, then ~$0.0085/GB"
	@echo ""
	@echo "$(GREEN)Estimated Monthly Total (Production):$(NC)"
	@echo "  - Minimum: $60-80/month"
	@echo "  - With moderate usage: $100-150/month"

backend-setup: ## Setup remote state backend in OCI Object Storage
	@echo "$(BLUE)Setting up remote state backend...$(NC)"
	@echo "$(YELLOW)To enable remote state, uncomment the backend block in provider.tf$(NC)"
	@echo ""
	@echo "$(YELLOW)Steps:$(NC)"
	@echo "1. Create OCI Object Storage bucket: yawl-terraform-state"
	@echo "2. Uncomment backend 's3' block in provider.tf"
	@echo "3. Update bucket name and region"
	@echo "4. Run: terraform init -reconfigure"
	@echo "5. Choose 'yes' to migrate state to backend"

docs: ## Generate module documentation
	@echo "$(BLUE)Module Documentation:$(NC)"
	@echo ""
	@echo "$(GREEN)Module Structure:$(NC)"
	@echo "  provider.tf        - Provider and Terraform configuration"
	@echo "  variables.tf       - Input variables with descriptions"
	@echo "  locals.tf          - Local computed values"
	@echo "  network.tf         - VCN, subnets, security groups"
	@echo "  compute.tf         - Compute instances and storage"
	@echo "  database.tf        - MySQL database and users"
	@echo "  load_balancer.tf   - Load balancer and listeners"
	@echo "  outputs.tf         - Output values"
	@echo ""
	@echo "$(GREEN)For detailed documentation:$(NC)"
	@echo "  See README.md in the terraform-oci directory"

test: ## Run Terraform tests
	@echo "$(BLUE)Running Terraform tests...$(NC)"
	terraform validate
	terraform fmt -recursive -check .
	@echo "$(GREEN)✓ All tests passed$(NC)"

show-config: ## Show current configuration
	@echo "$(BLUE)Current Configuration:$(NC)"
	@if [ -f "$(TF_VARS)" ]; then \
		echo "$(GREEN)Variables from $(TF_VARS):$(NC)"; \
		grep -v '^#' "$(TF_VARS)" | grep -v '^$$'; \
	else \
		echo "$(YELLOW)$(TF_VARS) not found$(NC)"; \
	fi

lock-info: ## Show lock information
	@echo "$(BLUE)Lock Information:$(NC)"
	@if [ -f "$(TF_LOCK)" ]; then \
		cat "$(TF_LOCK)"; \
	else \
		echo "$(YELLOW)No lock file found$(NC)"; \
	fi

version: ## Show Terraform and provider versions
	@echo "$(BLUE)Version Information:$(NC)"
	terraform version
	@echo ""
	@echo "$(BLUE)Required providers:$(NC)"
	@grep "source\|version" provider.tf | grep -v "^#"

setup: init validate fmt ## Complete setup (init, validate, format)
	@echo "$(GREEN)✓ Setup complete$(NC)"

.DEFAULT_GOAL := help
