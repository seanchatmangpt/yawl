#------------------------------------------------------------------------------
# YAWL Production Environment - Terraform Configuration
# Version: 1.0.0
#
# CRITICAL: Production environment with maximum availability and security
# Usage: terraform init && terraform apply -var-file=terraform.tfvars
#
# Requirements:
#   - Terraform >= 1.6.0
#   - Backend configured with state locking
#   - All secrets in Vault or cloud-native secret manager
#------------------------------------------------------------------------------

terraform {
  required_version = ">= 1.6.0"

  # Production uses GCS backend with versioning and locking
  backend "gcs" {
    bucket = "yawl-terraform-state-production"
    prefix = "terraform/state/production"
  }
}

#------------------------------------------------------------------------------
# Provider Configuration with Production Settings
#------------------------------------------------------------------------------

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Environment = "production"
      ManagedBy   = "terraform"
      Project     = var.project_name
    }
  }
}

provider "google" {
  project = var.gcp_project_id
  region  = var.gcp_region

  # Production requires explicit user project
  user_project_override = true
}

provider "azurerm" {
  features {
    key_vault {
      purge_soft_delete_on_protected_days    = 14
      recover_soft_deleted_key_vaults        = true
    }
    resource_group {
      prevent_deletion_if_contains_resources = true
    }
  }

  skip_provider_registration = false
}

provider "oci" {
  region = var.oci_region
}

provider "ibm" {
  region           = var.ibm_region
  ibmcloud_api_key = var.ibm_api_key
}

#------------------------------------------------------------------------------
# Local Variables
#------------------------------------------------------------------------------

locals {
  name_prefix = "${var.project_name}-${var.environment}"

  common_tags = {
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "terraform"
    CreatedAt   = timestamp()
    Compliance  = "production"
  }
}

#------------------------------------------------------------------------------
# Module: Networking (High Availability)
#------------------------------------------------------------------------------

module "networking" {
  source = "../../shared/networking"

  cloud_provider  = var.cloud_provider
  project_name    = var.project_name
  environment     = var.environment
  region          = var.cloud_provider == "aws" ? var.aws_region : var.cloud_provider == "gcp" ? var.gcp_region : var.azure_location
  vpc_cidr        = var.vpc_cidr
  subnet_cidrs    = var.subnet_cidrs
  enable_nat      = true  # Always enabled for production
  enable_vpn      = var.enable_vpn_gateway
  common_tags     = local.common_tags

  # Cloud-specific
  gcp_project_id  = var.gcp_project_id
  gcp_region      = var.gcp_region
  gcp_zone        = var.gcp_zone
  aws_region      = var.aws_region
  aws_availability_zones = var.aws_availability_zones
  azure_location  = var.azure_location
  azure_resource_group_name = var.azure_resource_group_name
  oci_compartment_id = var.oci_compartment_id
  oci_region      = var.oci_region
  ibm_region      = var.ibm_region
  ibm_resource_group = var.ibm_resource_group
}

#------------------------------------------------------------------------------
# Module: Security (Strict)
#------------------------------------------------------------------------------

module "security" {
  source = "../../shared/security"

  cloud_provider     = var.cloud_provider
  project_name       = var.project_name
  environment        = var.environment
  vpc_id             = module.networking.vpc_id
  vpc_cidr           = var.vpc_cidr
  allowed_cidr_blocks = var.allowed_cidr_blocks
  common_tags        = local.common_tags

  # Cloud-specific
  gcp_project_id     = var.gcp_project_id
  aws_region         = var.aws_region
  azure_location     = var.azure_location
  azure_resource_group_name = var.azure_resource_group_name
  oci_compartment_id = var.oci_compartment_id
  ibm_region         = var.ibm_region

  depends_on = [module.networking]
}

#------------------------------------------------------------------------------
# Module: Monitoring (Full Stack)
#------------------------------------------------------------------------------

module "monitoring" {
  source = "../../shared/monitoring"

  count = var.enable_monitoring ? 1 : 0

  cloud_provider     = var.cloud_provider
  project_name       = var.project_name
  environment        = var.environment
  enable_logging     = true   # Always enabled for production
  enable_alerting    = true   # Always enabled for production
  log_retention_days = 90     # Extended retention for production
  metric_retention_days = 90
  alert_email_recipients = var.alert_email_recipients
  alert_slack_webhook = var.alert_slack_webhook
  common_tags        = local.common_tags

  # Cloud-specific
  gcp_project_id     = var.gcp_project_id
  gcp_region         = var.gcp_region
  aws_region         = var.aws_region
  azure_location     = var.azure_location
  azure_resource_group_name = var.azure_resource_group_name
  oci_compartment_id = var.oci_compartment_id
  ibm_region         = var.ibm_region

  depends_on = [module.networking]
}

#------------------------------------------------------------------------------
# GCP Module (High Availability GKE, Cloud SQL HA, GCS)
#------------------------------------------------------------------------------

module "gcp" {
  source = "../../modules/gcp"
  count  = var.cloud_provider == "gcp" ? 1 : 0

  project_name           = var.project_name
  environment            = var.environment
  gcp_project_id         = var.gcp_project_id
  region                 = var.gcp_region
  zone                   = var.gcp_zone

  vpc_id                 = module.networking.vpc_id
  private_subnet_ids     = module.networking.private_subnet_ids
  public_subnet_ids      = module.networking.public_subnet_ids

  # GKE Configuration (High Availability)
  kubernetes_version     = var.kubernetes_version
  node_machine_type      = var.gke_machine_type
  node_count_min         = var.node_count_min
  node_count_max         = var.node_count_max
  node_disk_size_gb      = var.node_disk_size_gb
  enable_autoscaling     = true
  preemptible_nodes      = false   # No preemptible in production
  node_pool_labels       = var.node_pool_labels
  node_pool_taints       = var.node_pool_taints

  # Cloud SQL Configuration (High Availability)
  database_version       = var.database_version
  database_tier          = var.database_instance_class
  database_storage_gb    = var.database_storage_gb
  database_multi_az      = true    # Always HA in production
  database_backup_days   = 30      # Extended retention
  database_deletion_protection = true   # Prevent accidental deletion
  database_encryption    = true    # Always encrypted
  database_username      = var.database_username

  # GCS Configuration
  storage_bucket_names   = var.storage_bucket_names
  storage_versioning     = true    # Always versioned in production
  storage_lifecycle_days = var.storage_lifecycle_days
  storage_archive_days   = var.storage_archive_days

  # Security
  kms_key_id             = var.kms_key_id
  enable_private_endpoint = true   # Private endpoint in production

  common_tags            = local.common_tags

  depends_on = [module.networking, module.security]
}

#------------------------------------------------------------------------------
# AWS Module (High Availability EKS, RDS Multi-AZ, S3)
#------------------------------------------------------------------------------

module "aws" {
  source = "../../modules/aws"
  count  = var.cloud_provider == "aws" ? 1 : 0

  project_name           = var.project_name
  environment            = var.environment
  aws_region             = var.aws_region
  availability_zones     = var.aws_availability_zones

  vpc_id                 = module.networking.vpc_id
  private_subnet_ids     = module.networking.private_subnet_ids
  public_subnet_ids      = module.networking.public_subnet_ids

  # EKS Configuration (High Availability)
  kubernetes_version     = var.kubernetes_version
  node_instance_type     = var.eks_instance_type
  node_ami_type          = var.eks_ami_type
  node_count_min         = var.node_count_min
  node_count_max         = var.node_count_max
  node_disk_size_gb      = var.node_disk_size_gb
  enable_autoscaling     = true
  node_pool_labels       = var.node_pool_labels
  node_pool_taints       = var.node_pool_taints

  # RDS Configuration (Multi-AZ)
  database_engine        = "postgresql"
  database_version       = var.database_version
  database_instance_class = var.database_instance_class
  database_storage_gb    = var.database_storage_gb
  database_multi_az      = true    # Always Multi-AZ in production
  database_backup_days   = 30
  database_deletion_protection = true
  database_encryption    = true
  database_username      = var.database_username

  # S3 Configuration
  storage_bucket_names   = var.storage_bucket_names
  storage_versioning     = true
  storage_lifecycle_days = var.storage_lifecycle_days
  storage_archive_days   = var.storage_archive_days

  # Security
  kms_key_id             = var.kms_key_id
  ssl_certificate_arn    = var.ssl_certificate_arn
  enable_private_endpoint = true

  common_tags            = local.common_tags

  depends_on = [module.networking, module.security]
}

#------------------------------------------------------------------------------
# Azure Module (AKS, PostgreSQL HA, Blob)
#------------------------------------------------------------------------------

module "azure" {
  source = "../../modules/azure"
  count  = var.cloud_provider == "azure" ? 1 : 0

  project_name           = var.project_name
  environment            = var.environment
  location               = var.azure_location
  resource_group_name    = var.azure_resource_group_name

  vnet_id                = module.networking.vpc_id
  private_subnet_ids     = module.networking.private_subnet_ids
  public_subnet_ids      = module.networking.public_subnet_ids

  # AKS Configuration
  kubernetes_version     = var.kubernetes_version
  node_vm_size           = var.aks_vm_size
  node_count_min         = var.node_count_min
  node_count_max         = var.node_count_max
  node_disk_size_gb      = var.node_disk_size_gb
  enable_autoscaling     = true
  sku_tier               = "Premium"  # Premium SLA for production
  node_pool_labels       = var.node_pool_labels
  node_pool_taints       = var.node_pool_taints

  # PostgreSQL Configuration
  database_version       = var.database_version
  database_sku           = var.database_instance_class
  database_storage_gb    = var.database_storage_gb
  database_geo_redundant = true       # Geo-redundant for production
  database_backup_days   = 30
  database_username      = var.database_username

  # Blob Storage Configuration
  storage_container_names = var.storage_bucket_names
  storage_account_tier   = "Premium"
  storage_replication    = "GRS"       # Geo-redundant for production
  storage_versioning     = true
  storage_lifecycle_days = var.storage_lifecycle_days
  storage_archive_days   = var.storage_archive_days

  enable_private_endpoint = true

  common_tags            = local.common_tags

  depends_on = [module.networking, module.security]
}

#------------------------------------------------------------------------------
# Production Outputs (Sensitive values redacted)
#------------------------------------------------------------------------------

output "cluster_endpoint" {
  description = "Kubernetes cluster endpoint"
  value       = var.cloud_provider == "gcp" ? try(module.gcp[0].cluster_endpoint, "") :
                var.cloud_provider == "aws" ? try(module.aws[0].cluster_endpoint, "") :
                var.cloud_provider == "azure" ? try(module.azure[0].cluster_fqdn, "") : ""
  sensitive   = true
}

output "database_endpoint" {
  description = "Database endpoint (use with caution)"
  value       = var.cloud_provider == "gcp" ? try(module.gcp[0].database_connection_name, "") :
                var.cloud_provider == "aws" ? try(module.aws[0].database_endpoint, "") :
                var.cloud_provider == "azure" ? try(module.azure[0].database_fqdn, "") : ""
  sensitive   = true
}

output "monitoring_dashboard_url" {
  description = "Monitoring dashboard URL"
  value       = try(module.monitoring[0].dashboard_url, "")
}

output "log_endpoint" {
  description = "Log aggregation endpoint"
  value       = try(module.monitoring[0].log_endpoint, "")
}

output "alert_topic_id" {
  description = "Alert notification topic ID"
  value       = try(module.monitoring[0].alert_topic_id, "")
}
