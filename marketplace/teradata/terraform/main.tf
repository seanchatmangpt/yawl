# Terraform Main Configuration
# Teradata Vantage Multi-Cloud Deployment for YAWL

terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.0"
    }
    time = {
      source  = "hashicorp/time"
      version = "~> 0.9"
    }
  }

  # Backend configuration - uncomment and configure for production
  # backend "s3" {
  #   bucket         = "yawl-terraform-state"
  #   key            = "teradata/terraform.tfstate"
  #   region         = "us-east-1"
  #   encrypt        = true
  #   dynamodb_table = "terraform-locks"
  # }
}

# =============================================================================
# Provider Configuration
# =============================================================================

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = merge(local.common_tags, {
      Project     = "YAWL-Teradata"
      ManagedBy   = "Terraform"
      Environment = var.environment
    })
  }
}

# =============================================================================
# Local Variables
# =============================================================================

locals {
  common_tags = {
    Owner      = var.owner
    CostCenter = var.cost_center
    CreatedAt  = timestamp()
  }

  name_prefix = "${var.project_name}-${var.environment}"
}

# =============================================================================
# Random Suffix for Globally Unique Names
# =============================================================================

resource "random_id" "suffix" {
  byte_length = 4
}

# =============================================================================
# Data Sources
# =============================================================================

data "aws_availability_zones" "available" {
  state = "available"
}

data "aws_caller_identity" "current" {}
