#------------------------------------------------------------------------------
# YAWL Staging Environment - Terraform Configuration
# Version: 1.0.0
#
# This is the staging environment configuration
# Usage: terraform init && terraform apply -var-file=terraform.tfvars
#------------------------------------------------------------------------------

terraform {
  required_version = ">= 1.6.0"

  backend "gcs" {
    bucket = "yawl-terraform-state-staging"
    prefix = "terraform/state/staging"
  }
}

#------------------------------------------------------------------------------
# Provider Configuration
#------------------------------------------------------------------------------

provider "aws" {
  region = var.aws_region
}

provider "google" {
  project = var.gcp_project_id
  region  = var.gcp_region
}

provider "azurerm" {
  features {}
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
  }
}

#------------------------------------------------------------------------------
# Module: Networking
#------------------------------------------------------------------------------

module "networking" {
  source = "../../shared/networking"

  cloud_provider  = var.cloud_provider
  project_name    = var.project_name
  environment     = var.environment
  region          = var.cloud_provider == "aws" ? var.aws_region : var.cloud_provider == "gcp" ? var.gcp_region : var.azure_location
  vpc_cidr        = var.vpc_cidr
  subnet_cidrs    = var.subnet_cidrs
  enable_nat      = var.enable_nat_gateway
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
# Module: Security
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
# Module: Monitoring
#------------------------------------------------------------------------------

module "monitoring" {
  source = "../../shared/monitoring"

  count = var.enable_monitoring ? 1 : 0

  cloud_provider     = var.cloud_provider
  project_name       = var.project_name
  environment        = var.environment
  enable_logging     = var.enable_logging
  enable_alerting    = var.enable_alerting
  log_retention_days = var.log_retention_days
  metric_retention_days = var.metric_retention_days
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
# GCP Module (GKE, Cloud SQL, GCS)
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

  kubernetes_version     = var.kubernetes_version
  node_machine_type      = var.gke_machine_type
  node_count_min         = var.node_count_min
  node_count_max         = var.node_count_max
  node_disk_size_gb      = var.node_disk_size_gb
  enable_autoscaling     = var.enable_node_auto_scaling
  preemptible_nodes      = false  # No preemptible in staging
  node_pool_labels       = var.node_pool_labels
  node_pool_taints       = var.node_pool_taints

  database_version       = var.database_version
  database_tier          = var.database_instance_class
  database_storage_gb    = var.database_storage_gb
  database_multi_az      = true   # Multi-AZ for staging
  database_backup_days   = 14     # Longer retention for staging
  database_deletion_protection = false
  database_encryption    = var.database_encryption
  database_username      = var.database_username

  storage_bucket_names   = var.storage_bucket_names
  storage_versioning     = var.storage_versioning
  storage_lifecycle_days = var.storage_lifecycle_days
  storage_archive_days   = var.storage_archive_days

  kms_key_id             = var.kms_key_id
  enable_private_endpoint = false

  common_tags            = local.common_tags

  depends_on = [module.networking, module.security]
}

#------------------------------------------------------------------------------
# AWS Module (EKS, RDS, S3)
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

  kubernetes_version     = var.kubernetes_version
  node_instance_type     = var.eks_instance_type
  node_ami_type          = var.eks_ami_type
  node_count_min         = var.node_count_min
  node_count_max         = var.node_count_max
  node_disk_size_gb      = var.node_disk_size_gb
  enable_autoscaling     = var.enable_node_auto_scaling
  node_pool_labels       = var.node_pool_labels
  node_pool_taints       = var.node_pool_taints

  database_engine        = "postgresql"
  database_version       = var.database_version
  database_instance_class = var.database_instance_class
  database_storage_gb    = var.database_storage_gb
  database_multi_az      = true   # Multi-AZ for staging
  database_backup_days   = 14
  database_deletion_protection = false
  database_encryption    = var.database_encryption
  database_username      = var.database_username

  storage_bucket_names   = var.storage_bucket_names
  storage_versioning     = var.storage_versioning
  storage_lifecycle_days = var.storage_lifecycle_days
  storage_archive_days   = var.storage_archive_days

  kms_key_id             = var.kms_key_id
  ssl_certificate_arn    = var.ssl_certificate_arn
  enable_private_endpoint = false

  common_tags            = local.common_tags

  depends_on = [module.networking, module.security]
}

#------------------------------------------------------------------------------
# Azure Module (AKS, PostgreSQL, Blob)
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

  kubernetes_version     = var.kubernetes_version
  node_vm_size           = var.aks_vm_size
  node_count_min         = var.node_count_min
  node_count_max         = var.node_count_max
  node_disk_size_gb      = var.node_disk_size_gb
  enable_autoscaling     = var.enable_node_auto_scaling
  sku_tier               = "Standard"  # Standard for staging
  node_pool_labels       = var.node_pool_labels
  node_pool_taints       = var.node_pool_taints

  database_version       = var.database_version
  database_sku           = var.database_instance_class
  database_storage_gb    = var.database_storage_gb
  database_geo_redundant = true        # Geo-redundant for staging
  database_backup_days   = 14
  database_username      = var.database_username

  storage_container_names = var.storage_bucket_names
  storage_account_tier   = "Standard"
  storage_replication    = "GRS"       # Geo-redundant for staging
  storage_versioning     = var.storage_versioning
  storage_lifecycle_days = var.storage_lifecycle_days
  storage_archive_days   = var.storage_archive_days

  enable_private_endpoint = false

  common_tags            = local.common_tags

  depends_on = [module.networking, module.security]
}

#------------------------------------------------------------------------------
# Outputs
#------------------------------------------------------------------------------

output "cluster_endpoint" {
  description = "Kubernetes cluster endpoint"
  value = var.cloud_provider == "gcp" ? try(module.gcp[0].cluster_endpoint, "") :
          var.cloud_provider == "aws" ? try(module.aws[0].cluster_endpoint, "") :
          var.cloud_provider == "azure" ? try(module.azure[0].cluster_fqdn, "") : ""
}

output "database_endpoint" {
  description = "Database endpoint"
  value = var.cloud_provider == "gcp" ? try(module.gcp[0].database_connection_name, "") :
          var.cloud_provider == "aws" ? try(module.aws[0].database_endpoint, "") :
          var.cloud_provider == "azure" ? try(module.azure[0].database_fqdn, "") : ""
}

output "monitoring_dashboard_url" {
  description = "Monitoring dashboard URL"
  value       = try(module.monitoring[0].dashboard_url, "")
}
