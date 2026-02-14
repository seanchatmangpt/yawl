#------------------------------------------------------------------------------
# YAWL Multi-Cloud Terraform Main Configuration
# Version: 1.0.0
#
# This is the root module that orchestrates multi-cloud deployments
# for the YAWL workflow engine.
#------------------------------------------------------------------------------

locals {
  # Common resource naming convention
  name_prefix = "${var.project_name}-${var.environment}"

  # Common tags applied to all resources
  common_tags = merge(var.common_tags, {
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "terraform"
    CreatedAt   = timestamp()
  })

  # Region selection based on cloud provider
  region = var.region != "" ? var.region : (
    var.cloud_provider == "gcp" ? var.gcp_region :
    var.cloud_provider == "aws" ? var.aws_region :
    var.cloud_provider == "azure" ? var.azure_location :
    var.cloud_provider == "oracle" ? var.oci_region :
    var.ibm_region
  )
}

#------------------------------------------------------------------------------
# Provider Configurations
#------------------------------------------------------------------------------

# GCP Provider
provider "google" {
  count    = var.cloud_provider == "gcp" ? 1 : 0
  project  = var.gcp_project_id
  region   = var.gcp_region
  zone     = var.gcp_zone
}

provider "google-beta" {
  count    = var.cloud_provider == "gcp" ? 1 : 0
  project  = var.gcp_project_id
  region   = var.gcp_region
  zone     = var.gcp_zone
}

# AWS Provider
provider "aws" {
  count              = var.cloud_provider == "aws" ? 1 : 0
  region             = var.aws_region
  allowed_account_ids = var.aws_account_id != "" ? [var.aws_account_id] : null
}

# Azure Provider
provider "azurerm" {
  count               = var.cloud_provider == "azure" ? 1 : 0
  subscription_id     = var.azure_subscription_id
  features {
    key_vault {
      purge_soft_delete_on_destroy    = false
      recover_soft_deleted_key_vaults = true
    }
    resource_group {
      prevent_deletion_if_contains_resources = true
    }
  }
}

# Oracle Cloud Provider
provider "oci" {
  count         = var.cloud_provider == "oracle" ? 1 : 0
  tenancy_ocid  = var.oci_tenancy_id
  region        = var.oci_region
}

# IBM Cloud Provider
provider "ibm" {
  count          = var.cloud_provider == "ibm" ? 1 : 0
  region         = var.ibm_region
  ibmcloud_api_key = var.ibm_api_key
}

#------------------------------------------------------------------------------
# Shared Networking Module
#------------------------------------------------------------------------------

module "networking" {
  source   = "./shared/networking"
  count    = 1

  cloud_provider  = var.cloud_provider
  project_name    = var.project_name
  environment     = var.environment
  region          = local.region
  vpc_cidr        = var.vpc_cidr
  subnet_cidrs    = var.subnet_cidrs
  enable_nat      = var.enable_nat_gateway
  enable_vpn      = var.enable_vpn_gateway
  common_tags     = local.common_tags

  # Cloud-specific configuration
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
# Shared Security Module
#------------------------------------------------------------------------------

module "security" {
  source   = "./shared/security"
  count    = 1

  cloud_provider     = var.cloud_provider
  project_name       = var.project_name
  environment        = var.environment
  vpc_id             = module.networking[0].vpc_id
  vpc_cidr           = var.vpc_cidr
  allowed_cidr_blocks = var.allowed_cidr_blocks
  common_tags        = local.common_tags

  # Cloud-specific configuration
  gcp_project_id     = var.gcp_project_id
  aws_region         = var.aws_region
  azure_location     = var.azure_location
  azure_resource_group_name = var.azure_resource_group_name
  oci_compartment_id = var.oci_compartment_id
  ibm_region         = var.ibm_region
}

#------------------------------------------------------------------------------
# Shared Monitoring Module
#------------------------------------------------------------------------------

module "monitoring" {
  source   = "./shared/monitoring"
  count    = var.enable_monitoring ? 1 : 0

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

  # Cloud-specific configuration
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
  source   = "./modules/gcp"
  count    = var.cloud_provider == "gcp" ? 1 : 0

  project_name           = var.project_name
  environment            = var.environment
  gcp_project_id         = var.gcp_project_id
  region                 = var.gcp_region
  zone                   = var.gcp_zone

  # Networking
  vpc_id                 = module.networking[0].vpc_id
  private_subnet_ids     = module.networking[0].private_subnet_ids
  public_subnet_ids      = module.networking[0].public_subnet_ids

  # GKE Configuration
  kubernetes_version     = var.kubernetes_version
  node_machine_type      = var.gke_machine_type
  node_count_min         = var.node_count_min
  node_count_max         = var.node_count_max
  node_disk_size_gb      = var.node_disk_size_gb
  enable_autoscaling     = var.enable_node_auto_scaling
  preemptible_nodes      = var.gke_preemptible
  node_pool_labels       = var.node_pool_labels
  node_pool_taints       = var.node_pool_taints

  # Cloud SQL Configuration
  database_version       = var.database_version
  database_tier          = var.database_instance_class != "" ? var.database_instance_class : "db-custom-4-16384"
  database_storage_gb    = var.database_storage_gb
  database_multi_az      = var.database_multi_az
  database_backup_days   = var.database_backup_retention_days
  database_deletion_protection = var.database_deletion_protection
  database_encryption    = var.database_encryption
  database_username      = var.database_username

  # GCS Configuration
  storage_bucket_names   = var.storage_bucket_names
  storage_versioning     = var.storage_versioning
  storage_lifecycle_days = var.storage_lifecycle_days
  storage_archive_days   = var.storage_archive_days

  # Security
  kms_key_id             = var.kms_key_id
  enable_private_endpoint = var.enable_private_endpoint

  common_tags            = local.common_tags

  depends_on = [module.networking, module.security]
}

#------------------------------------------------------------------------------
# AWS Module (EKS, RDS, S3)
#------------------------------------------------------------------------------

module "aws" {
  source   = "./modules/aws"
  count    = var.cloud_provider == "aws" ? 1 : 0

  project_name           = var.project_name
  environment            = var.environment
  aws_region             = var.aws_region
  availability_zones     = var.aws_availability_zones

  # Networking
  vpc_id                 = module.networking[0].vpc_id
  private_subnet_ids     = module.networking[0].private_subnet_ids
  public_subnet_ids      = module.networking[0].public_subnet_ids

  # EKS Configuration
  kubernetes_version     = var.kubernetes_version
  node_instance_type     = var.eks_instance_type
  node_ami_type          = var.eks_ami_type
  node_count_min         = var.node_count_min
  node_count_max         = var.node_count_max
  node_disk_size_gb      = var.node_disk_size_gb
  enable_autoscaling     = var.enable_node_auto_scaling
  node_pool_labels       = var.node_pool_labels
  node_pool_taints       = var.node_pool_taints

  # RDS Configuration
  database_engine        = var.database_engine
  database_version       = var.database_version
  database_instance_class = var.database_instance_class != "" ? var.database_instance_class : "db.r6g.xlarge"
  database_storage_gb    = var.database_storage_gb
  database_multi_az      = var.database_multi_az
  database_backup_days   = var.database_backup_retention_days
  database_deletion_protection = var.database_deletion_protection
  database_encryption    = var.database_encryption
  database_username      = var.database_username

  # S3 Configuration
  storage_bucket_names   = var.storage_bucket_names
  storage_versioning     = var.storage_versioning
  storage_lifecycle_days = var.storage_lifecycle_days
  storage_archive_days   = var.storage_archive_days

  # Security
  kms_key_id             = var.kms_key_id
  ssl_certificate_arn    = var.ssl_certificate_arn
  enable_private_endpoint = var.enable_private_endpoint

  common_tags            = local.common_tags

  depends_on = [module.networking, module.security]
}

#------------------------------------------------------------------------------
# Azure Module (AKS, PostgreSQL, Blob)
#------------------------------------------------------------------------------

module "azure" {
  source   = "./modules/azure"
  count    = var.cloud_provider == "azure" ? 1 : 0

  project_name           = var.project_name
  environment            = var.environment
  location               = var.azure_location
  resource_group_name    = var.azure_resource_group_name != "" ? var.azure_resource_group_name : "${local.name_prefix}-rg"

  # Networking
  vnet_id                = module.networking[0].vpc_id
  private_subnet_ids     = module.networking[0].private_subnet_ids
  public_subnet_ids      = module.networking[0].public_subnet_ids

  # AKS Configuration
  kubernetes_version     = var.kubernetes_version
  node_vm_size           = var.aks_vm_size
  node_count_min         = var.node_count_min
  node_count_max         = var.node_count_max
  node_disk_size_gb      = var.node_disk_size_gb
  enable_autoscaling     = var.enable_node_auto_scaling
  sku_tier               = var.aks_sku_tier
  node_pool_labels       = var.node_pool_labels
  node_pool_taints       = var.node_pool_taints

  # PostgreSQL Configuration
  database_version       = var.database_version
  database_sku           = var.database_instance_class != "" ? var.database_instance_class : "GP_Standard_D4s_v3"
  database_storage_gb    = var.database_storage_gb
  database_geo_redundant = var.database_multi_az
  database_backup_days   = var.database_backup_retention_days
  database_username      = var.database_username

  # Blob Storage Configuration
  storage_container_names = var.storage_bucket_names
  storage_account_tier   = "Standard"
  storage_replication    = var.database_multi_az ? "GRS" : "LRS"
  storage_versioning     = var.storage_versioning
  storage_lifecycle_days = var.storage_lifecycle_days
  storage_archive_days   = var.storage_archive_days

  # Security
  enable_private_endpoint = var.enable_private_endpoint

  common_tags            = local.common_tags

  depends_on = [module.networking, module.security]
}

#------------------------------------------------------------------------------
# Oracle Cloud Module (OKE, Autonomous DB, Object Storage)
#------------------------------------------------------------------------------

module "oracle" {
  source   = "./modules/oracle"
  count    = var.cloud_provider == "oracle" ? 1 : 0

  project_name           = var.project_name
  environment            = var.environment
  compartment_id         = var.oci_compartment_id
  region                 = var.oci_region

  # Networking
  vcn_id                 = module.networking[0].vpc_id
  private_subnet_ids     = module.networking[0].private_subnet_ids
  public_subnet_ids      = module.networking[0].public_subnet_ids

  # OKE Configuration
  kubernetes_version     = var.kubernetes_version
  node_shape             = var.oke_node_shape
  node_ocpus             = var.oke_node_ocpus
  node_memory_gb         = var.oke_node_memory_gb
  node_count_min         = var.node_count_min
  node_count_max         = var.node_count_max
  node_disk_size_gb      = var.node_disk_size_gb
  enable_autoscaling     = var.enable_node_auto_scaling
  node_pool_labels       = var.node_pool_labels

  # Autonomous Database Configuration
  database_cpu_core_count = var.oke_node_ocpus
  database_storage_tbs   = ceil(var.database_storage_gb / 1024)
  database_auto_scaling  = true
  database_backup_days   = var.database_backup_retention_days
  database_username      = var.database_username

  # Object Storage Configuration
  storage_bucket_names   = var.storage_bucket_names
  storage_versioning     = var.storage_versioning

  common_tags            = local.common_tags

  depends_on = [module.networking, module.security]
}

#------------------------------------------------------------------------------
# IBM Cloud Module (IKS, Databases, COS)
#------------------------------------------------------------------------------

module "ibm" {
  source   = "./modules/ibm"
  count    = var.cloud_provider == "ibm" ? 1 : 0

  project_name           = var.project_name
  environment            = var.environment
  region                 = var.ibm_region
  resource_group         = var.ibm_resource_group

  # Networking
  vpc_id                 = module.networking[0].vpc_id
  private_subnet_ids     = module.networking[0].private_subnet_ids
  public_subnet_ids      = module.networking[0].public_subnet_ids

  # IKS Configuration
  kubernetes_version     = var.kubernetes_version
  node_flavor            = var.iks_flavor
  node_hardware          = var.iks_hardware
  node_count_min         = var.node_count_min
  node_count_max         = var.node_count_max
  enable_autoscaling     = var.enable_node_auto_scaling
  node_pool_labels       = var.node_pool_labels

  # Database Configuration
  database_name          = "${local.name_prefix}-db"
  database_plan          = "standard"
  database_version       = var.database_version
  database_members       = var.database_multi_az ? 3 : 1
  database_memory_gb     = 4
  database_disk_gb       = var.database_storage_gb

  # COS Configuration
  cos_bucket_names       = var.storage_bucket_names
  storage_class          = "standard"
  storage_versioning     = var.storage_versioning

  common_tags            = local.common_tags

  depends_on = [module.networking, module.security]
}

#------------------------------------------------------------------------------
# Marketplace Module (when enabled)
#------------------------------------------------------------------------------

module "marketplace_gcp" {
  source   = "./marketplace/gcp"
  count    = var.marketplace_enabled && var.cloud_provider == "gcp" ? 1 : 0

  project_name      = var.project_name
  environment       = var.environment
  gcp_project_id    = var.gcp_project_id
  region            = var.gcp_region
  cluster_endpoint  = module.gcp[0].cluster_endpoint
  plan_id           = var.marketplace_plan_id
  offer_id          = var.marketplace_offer_id
  publisher         = var.marketplace_publisher
}

module "marketplace_aws" {
  source   = "./marketplace/aws"
  count    = var.marketplace_enabled && var.cloud_provider == "aws" ? 1 : 0

  project_name      = var.project_name
  environment       = var.environment
  aws_region        = var.aws_region
  cluster_endpoint  = module.aws[0].cluster_endpoint
  plan_id           = var.marketplace_plan_id
  offer_id          = var.marketplace_offer_id
  publisher         = var.marketplace_publisher
}

module "marketplace_azure" {
  source   = "./marketplace/azure"
  count    = var.marketplace_enabled && var.cloud_provider == "azure" ? 1 : 0

  project_name           = var.project_name
  environment            = var.environment
  location               = var.azure_location
  resource_group_name    = var.azure_resource_group_name != "" ? var.azure_resource_group_name : "${local.name_prefix}-rg"
  cluster_endpoint       = module.azure[0].cluster_fqdn
  plan_id                = var.marketplace_plan_id
  offer_id               = var.marketplace_offer_id
  publisher              = var.marketplace_publisher
}

module "marketplace_oracle" {
  source   = "./marketplace/oracle"
  count    = var.marketplace_enabled && var.cloud_provider == "oracle" ? 1 : 0

  project_name      = var.project_name
  environment       = var.environment
  compartment_id    = var.oci_compartment_id
  region            = var.oci_region
  cluster_id        = module.oracle[0].cluster_id
  plan_id           = var.marketplace_plan_id
  offer_id          = var.marketplace_offer_id
  publisher         = var.marketplace_publisher
}

module "marketplace_ibm" {
  source   = "./marketplace/ibm"
  count    = var.marketplace_enabled && var.cloud_provider == "ibm" ? 1 : 0

  project_name      = var.project_name
  environment       = var.environment
  region            = var.ibm_region
  resource_group    = var.ibm_resource_group
  cluster_id        = module.ibm[0].cluster_id
  plan_id           = var.marketplace_plan_id
  offer_id          = var.marketplace_offer_id
  publisher         = var.marketplace_publisher
}
