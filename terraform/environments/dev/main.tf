#------------------------------------------------------------------------------
# YAWL Development Environment
# Version: 1.0.0
#
# This is the development environment configuration
# Usage: terraform init && terraform apply -var-file=terraform.tfvars
#------------------------------------------------------------------------------

terraform {
  required_version = ">= 1.6.0"

  backend "local" {
    path = "terraform.tfstate"
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
  region          = var.cloud_provider == "aws" ? var.aws_region : var.cloud_provider == "gcp" ? var.gcp_region : var.cloud_provider == "azure" ? var.azure_location : var.cloud_provider == "oracle" ? var.oci_region : var.ibm_region
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
# Cloud-Specific Modules
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
  preemptible_nodes      = var.gke_preemptible
  node_pool_labels       = var.node_pool_labels
  node_pool_taints       = var.node_pool_taints

  database_version       = var.database_version
  database_tier          = var.database_instance_class
  database_storage_gb    = var.database_storage_gb
  database_multi_az      = false  # Dev environment - single AZ
  database_backup_days   = 7      # Reduced for dev
  database_deletion_protection = false
  database_encryption    = var.database_encryption
  database_username      = var.database_username

  storage_bucket_names   = var.storage_bucket_names
  storage_versioning     = var.storage_versioning
  storage_lifecycle_days = var.storage_lifecycle_days
  storage_archive_days   = var.storage_archive_days

  kms_key_id             = var.kms_key_id
  enable_private_endpoint = false  # Dev environment - public endpoint

  common_tags            = local.common_tags

  depends_on = [module.networking, module.security]
}

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

  database_engine        = "postgresql"  # Dev - use single instance
  database_version       = var.database_version
  database_instance_class = var.database_instance_class
  database_storage_gb    = var.database_storage_gb
  database_multi_az      = false         # Dev environment - single AZ
  database_backup_days   = 7             # Reduced for dev
  database_deletion_protection = false
  database_encryption    = var.database_encryption
  database_username      = var.database_username

  storage_bucket_names   = var.storage_bucket_names
  storage_versioning     = var.storage_versioning
  storage_lifecycle_days = var.storage_lifecycle_days
  storage_archive_days   = var.storage_archive_days

  kms_key_id             = var.kms_key_id
  ssl_certificate_arn    = var.ssl_certificate_arn
  enable_private_endpoint = false        # Dev environment - public endpoint

  common_tags            = local.common_tags

  depends_on = [module.networking, module.security]
}

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
  sku_tier               = "Free"        # Dev environment
  node_pool_labels       = var.node_pool_labels
  node_pool_taints       = var.node_pool_taints

  database_version       = var.database_version
  database_sku           = "B_Standard_B2s"  # Dev - smaller SKU
  database_storage_gb    = var.database_storage_gb
  database_geo_redundant = false        # Dev environment
  database_backup_days   = 7            # Reduced for dev
  database_username      = var.database_username

  storage_container_names = var.storage_bucket_names
  storage_account_tier   = "Standard"
  storage_replication    = "LRS"        # Dev environment
  storage_versioning     = var.storage_versioning
  storage_lifecycle_days = var.storage_lifecycle_days
  storage_archive_days   = var.storage_archive_days

  enable_private_endpoint = false       # Dev environment

  common_tags            = local.common_tags

  depends_on = [module.networking, module.security]
}

module "oracle" {
  source = "../../modules/oracle"
  count  = var.cloud_provider == "oracle" ? 1 : 0

  project_name           = var.project_name
  environment            = var.environment
  compartment_id         = var.oci_compartment_id
  region                 = var.oci_region

  vcn_id                 = module.networking.vpc_id
  private_subnet_ids     = module.networking.private_subnet_ids
  public_subnet_ids      = module.networking.public_subnet_ids

  kubernetes_version     = var.kubernetes_version
  node_shape             = var.oke_node_shape
  node_ocpus             = var.oke_node_ocpus
  node_memory_gb         = var.oke_node_memory_gb
  node_count_min         = var.node_count_min
  node_count_max         = var.node_count_max
  node_disk_size_gb      = var.node_disk_size_gb
  enable_autoscaling     = var.enable_node_auto_scaling
  node_pool_labels       = var.node_pool_labels

  database_cpu_core_count = 1            # Dev - minimal
  database_storage_tbs   = 1
  database_auto_scaling  = true
  database_backup_days   = 7            # Reduced for dev
  database_username      = var.database_username

  storage_bucket_names   = var.storage_bucket_names
  storage_versioning     = var.storage_versioning

  enable_private_endpoint = false       # Dev environment

  common_tags            = local.common_tags

  depends_on = [module.networking, module.security]
}

module "ibm" {
  source = "../../modules/ibm"
  count  = var.cloud_provider == "ibm" ? 1 : 0

  project_name           = var.project_name
  environment            = var.environment
  region                 = var.ibm_region
  resource_group         = var.ibm_resource_group

  vpc_name               = "${local.name_prefix}-vpc"

  kubernetes_version     = var.kubernetes_version
  node_flavor            = var.iks_flavor
  node_hardware          = var.iks_hardware
  node_count_min         = var.node_count_min
  node_count_max         = var.node_count_max
  enable_autoscaling     = var.enable_node_auto_scaling
  node_pool_labels       = var.node_pool_labels

  database_name          = "${local.name_prefix}-db"
  database_plan          = "standard"
  database_version       = var.database_version
  database_members       = 1            # Dev - single member
  database_memory_gb     = 2            # Dev - minimal
  database_disk_gb       = var.database_storage_gb

  cos_bucket_names       = var.storage_bucket_names
  storage_class          = "standard"
  storage_versioning     = var.storage_versioning

  enable_private_endpoint = false       # Dev environment

  common_tags            = local.common_tags

  depends_on = [module.networking, module.security]
}
