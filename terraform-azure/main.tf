terraform {
  required_version = ">= 1.0"

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 3.0"
    }
  }

  # Uncomment to use remote state
  # backend "azurerm" {
  #   resource_group_name  = "rg-terraform-state"
  #   storage_account_name = "tfstate"
  #   container_name       = "state"
  #   key                  = "yawl.tfstate"
  # }
}

provider "azurerm" {
  features {
    virtual_machine {
      delete_os_disk_on_deletion            = true
      graceful_shutdown                     = false
      skip_shutdown_and_force_delete        = false
    }
    key_vault {
      purge_soft_delete_on_destroy = false
    }
  }

  skip_provider_registration = false
}

# Resource Group Module
module "resource_group" {
  source = "./resource_group"

  resource_group_name = var.resource_group_name
  location            = var.location
  environment         = var.environment
  project_name        = var.project_name

  tags = local.common_tags
}

# Virtual Network Module
module "vnet" {
  source = "./vnet"

  resource_group_name = module.resource_group.resource_group_name
  location            = module.resource_group.location

  vnet_name           = var.vnet_name
  vnet_cidr           = var.vnet_cidr
  subnet_configs      = var.subnet_configs

  enable_ddos_protection = var.enable_ddos_protection

  environment = var.environment
  project_name = var.project_name

  tags = local.common_tags

  depends_on = [module.resource_group]
}

# App Service Module
module "app_service" {
  source = "./app_service"

  resource_group_name = module.resource_group.resource_group_name
  location            = module.resource_group.location

  app_service_plan_name = var.app_service_plan_name
  app_service_name      = var.app_service_name

  app_service_plan_sku = var.app_service_plan_sku
  app_runtime_stack    = var.app_runtime_stack

  subnet_id            = module.vnet.app_service_subnet_id

  environment = var.environment
  project_name = var.project_name

  tags = local.common_tags

  depends_on = [module.resource_group, module.vnet]
}

# Database Module
module "database" {
  source = "./database"

  resource_group_name = module.resource_group.resource_group_name
  location            = module.resource_group.location

  server_name          = var.db_server_name
  database_name        = var.db_database_name
  administrator_login  = var.db_admin_username

  subnet_id            = module.vnet.database_subnet_id

  db_sku_name          = var.db_sku_name
  storage_mb           = var.db_storage_mb

  environment = var.environment
  project_name = var.project_name

  tags = local.common_tags

  depends_on = [module.resource_group, module.vnet]
}

# Monitoring Module
module "monitoring" {
  source = "./monitoring"

  resource_group_name = module.resource_group.resource_group_name
  location            = module.resource_group.location

  workspace_name       = var.log_analytics_workspace_name
  workspace_sku        = var.log_analytics_sku

  app_insights_name    = var.app_insights_name
  app_service_id       = module.app_service.app_service_id

  retention_days       = var.log_retention_days

  environment = var.environment
  project_name = var.project_name

  tags = local.common_tags

  depends_on = [module.resource_group, module.app_service]
}

locals {
  common_tags = {
    Environment = var.environment
    Project     = var.project_name
    ManagedBy   = "Terraform"
    CreatedDate = formatdate("YYYY-MM-DD", timestamp())
  }
}
