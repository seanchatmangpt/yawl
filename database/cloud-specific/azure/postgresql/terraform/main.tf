# Azure PostgreSQL Terraform Configuration
# Production-ready Azure Database for PostgreSQL Flexible Server for YAWL

terraform {
  required_version = ">= 1.0.0"

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 3.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.0"
    }
  }

  backend "azurerm" {
    resource_group_name  = "yawl-terraform-rg"
    storage_account_name = "yawltfstate"
    container_name       = "tfstate"
    key                  = "postgresql/terraform.tfstate"
  }
}

# Provider configuration
provider "azurerm" {
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

# Variables
variable "location" {
  description = "Azure region"
  type        = string
  default     = "eastus"
}

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
  default     = "prod"
}

variable "resource_group_name" {
  description = "Resource group name"
  type        = string
  default     = "yawl-rg"
}

variable "server_name" {
  description = "PostgreSQL server name"
  type        = string
  default     = "yawl-db"
}

variable "database_name" {
  description = "Database name"
  type        = string
  default     = "yawl"
}

variable "administrator_login" {
  description = "Administrator username"
  type        = string
  default     = "yawldbadmin"
}

variable "sku_name" {
  description = "SKU name for the server"
  type        = string
  default     = "GP_Standard_D2s_v3"
}

variable "postgres_version" {
  description = "PostgreSQL version"
  type        = string
  default     = "14"
}

variable "storage_mb" {
  description = "Storage size in MB"
  type        = number
  default     = 131072  # 128 GB
}

variable "backup_retention_days" {
  description = "Backup retention in days"
  type        = number
  default     = 30
}

variable "geo_redundant_backup_enabled" {
  description = "Enable geo-redundant backup"
  type        = bool
  default     = true
}

variable "vnet_address_space" {
  description = "VNet address space"
  type        = list(string)
  default     = ["10.0.0.0/16"]
}

variable "subnet_address_prefixes" {
  description = "Subnet address prefixes"
  type        = list(string)
  default     = ["10.0.1.0/24"]
}

variable "tags" {
  description = "Tags to apply to resources"
  type        = map(string)
  default = {
    Project     = "YAWL"
    Environment = "Production"
    ManagedBy   = "Terraform"
  }
}

# Random password
resource "random_password" "db_password" {
  length           = 32
  special          = true
  override_special = "!#$%&*()-_=+[]{}<>:?"
}

# Resource Group
resource "azurerm_resource_group" "yawl" {
  name     = var.resource_group_name
  location = var.location
  tags     = var.tags
}

# Virtual Network
resource "azurerm_virtual_network" "yawl" {
  name                = "yawl-vnet-${var.environment}"
  location            = azurerm_resource_group.yawl.location
  resource_group_name = azurerm_resource_group.yawl.name
  address_space       = var.vnet_address_space
  tags                = var.tags
}

# Subnet for PostgreSQL
resource "azurerm_subnet" "postgresql" {
  name                 = "yawl-postgresql-subnet-${var.environment}"
  resource_group_name  = azurerm_resource_group.yawl.name
  virtual_network_name = azurerm_virtual_network.yawl.name
  address_prefixes     = var.subnet_address_prefixes

  delegation {
    name = "postgresql-delegation"
    service_delegation {
      name    = "Microsoft.DBforPostgreSQL/flexibleServers"
      actions = ["Microsoft.Network/virtualNetworks/subnets/join/action"]
    }
  }
}

# Private DNS Zone
resource "azurerm_private_dns_zone" "postgresql" {
  name                = "privatelink.postgres.database.azure.com"
  resource_group_name = azurerm_resource_group.yawl.name
  tags                = var.tags
}

resource "azurerm_private_dns_zone_virtual_network_link" "postgresql" {
  name                  = "yawl-postgresql-dns-link-${var.environment}"
  resource_group_name   = azurerm_resource_group.yawl.name
  private_dns_zone_name = azurerm_private_dns_zone.postgresql.name
  virtual_network_id    = azurerm_virtual_network.yawl.id
  registration_enabled  = false
  tags                  = var.tags
}

# PostgreSQL Flexible Server
resource "azurerm_postgresql_flexible_server" "yawl" {
  name                          = "${var.server_name}-${var.environment}"
  resource_group_name           = azurerm_resource_group.yawl.name
  location                      = azurerm_resource_group.yawl.location
  version                       = var.postgres_version
  delegated_subnet_id           = azurerm_subnet.postgresql.id
  private_dns_zone_id           = azurerm_private_dns_zone.postgresql.id
  administrator_login           = var.administrator_login
  administrator_password        = random_password.db_password.result
  sku_name                      = var.sku_name
  storage_mb                    = var.storage_mb
  backup_retention_days         = var.backup_retention_days
  geo_redundant_backup_enabled  = var.geo_redundant_backup_enabled
  public_network_access_enabled = false
  tags                          = var.tags

  high_availability {
    mode = "ZoneRedundant"
  }

  maintenance_window {
    day_of_week  = 0  # Sunday
    start_hour   = 3
    start_minute = 0
  }

  storage {
    auto_grow_enabled = true
    tier              = "GeneralPurpose"
    type              = "Premium_LRS"
  }

  lifecycle {
    prevent_destroy = false
    ignore_changes  = [storage_mb]
  }
}

# Database
resource "azurerm_postgresql_flexible_server_database" "yawl" {
  name      = var.database_name
  server_id = azurerm_postgresql_flexible_server.yawl.id
  charset   = "UTF8"
  collation = "en_US.UTF8"
}

# Server Configuration
resource "azurerm_postgresql_flexible_server_configuration" "max_connections" {
  name      = "max_connections"
  server_id = azurerm_postgresql_flexible_server.yawl.id
  value     = "200"
}

resource "azurerm_postgresql_flexible_server_configuration" "work_mem" {
  name      = "work_mem"
  server_id = azurerm_postgresql_flexible_server.yawl.id
  value     = "16777216"  # 16MB
}

resource "azurerm_postgresql_flexible_server_configuration" "log_min_duration_statement" {
  name      = "log_min_duration_statement"
  server_id = azurerm_postgresql_flexible_server.yawl.id
  value     = "1000"
}

resource "azurerm_postgresql_flexible_server_configuration" "log_connections" {
  name      = "log_connections"
  server_id = azurerm_postgresql_flexible_server.yawl.id
  value     = "on"
}

resource "azurerm_postgresql_flexible_server_configuration" "log_disconnections" {
  name      = "log_disconnections"
  server_id = azurerm_postgresql_flexible_server.yawl.id
  value     = "on"
}

# Key Vault
resource "azurerm_key_vault" "yawl" {
  name                          = "yawl-kv-${var.environment}"
  location                      = azurerm_resource_group.yawl.location
  resource_group_name           = azurerm_resource_group.yawl.name
  tenant_id                     = data.azurerm_client_config.current.tenant_id
  sku_name                      = "premium"
  soft_delete_retention_days    = 90
  purge_protection_enabled      = true
  enable_rbac_authorization     = true
  public_network_access_enabled = false
  tags                          = var.tags

  network_acls {
    bypass                     = "AzureServices"
    default_action             = "Deny"
    ip_rules                   = []
    virtual_network_subnet_ids = [azurerm_subnet.postgresql.id]
  }
}

# Store database password in Key Vault
resource "azurerm_key_vault_secret" "db_password" {
  name         = "yawl-db-password"
  value        = random_password.db_password.result
  key_vault_id = azurerm_key_vault.yawl.id

  depends_on = [
    azurerm_role_assignment.current_user
  ]
}

resource "azurerm_key_vault_secret" "connection_string" {
  name         = "yawl-db-connection-string"
  value        = "jdbc:postgresql://${azurerm_postgresql_flexible_server.yawl.fqdn}:5432/${var.database_name}?sslmode=require"
  key_vault_id = azurerm_key_vault.yawl.id

  depends_on = [
    azurerm_role_assignment.current_user
  ]
}

# RBAC for current user
data "azurerm_client_config" "current" {}

resource "azurerm_role_assignment" "current_user" {
  scope                = azurerm_key_vault.yawl.id
  role_definition_name = "Key Vault Secrets Officer"
  principal_id         = data.azurerm_client_config.current.object_id
}

# Monitoring
resource "azurerm_monitor_diagnostic_setting" "postgresql" {
  name                       = "yawl-postgresql-diagnostic-${var.environment}"
  target_resource_id         = azurerm_postgresql_flexible_server.yawl.id
  log_analytics_workspace_id = azurerm_log_analytics_workspace.yawl.id

  enabled_log {
    category = "PostgreSQLLogs"
  }

  enabled_log {
    category = "PostgreSQLFlexSessions"
  }

  enabled_log {
    category = "PostgreSQLFlexQueryStoreRuntime"
  }

  enabled_log {
    category = "PostgreSQLFlexQueryStoreWaitStatistics"
  }

  metric {
    category = "AllMetrics"
    enabled  = true
  }
}

resource "azurerm_log_analytics_workspace" "yawl" {
  name                = "yawl-law-${var.environment}"
  location            = azurerm_resource_group.yawl.location
  resource_group_name = azurerm_resource_group.yawl.name
  sku                 = "PerGB2018"
  retention_in_days   = 90
  tags                = var.tags
}

# Outputs
output "server_fqdn" {
  value       = azurerm_postgresql_flexible_server.yawl.fqdn
  description = "PostgreSQL server FQDN"
}

output "server_name" {
  value       = azurerm_postgresql_flexible_server.yawl.name
  description = "PostgreSQL server name"
}

output "database_name" {
  value       = azurerm_postgresql_flexible_server_database.yawl.name
  description = "Database name"
}

output "administrator_login" {
  value       = var.administrator_login
  description = "Administrator username"
}

output "administrator_password" {
  value     = random_password.db_password.result
  sensitive = true
}

output "jdbc_url" {
  value       = "jdbc:postgresql://${azurerm_postgresql_flexible_server.yawl.fqdn}:5432/${var.database_name}?sslmode=require"
  description = "JDBC connection URL"
}

output "connection_string" {
  value       = "Server=${azurerm_postgresql_flexible_server.yawl.fqdn};Database=${var.database_name};Port=5432;User Id=${var.administrator_login};SslMode=Require;"
  description = "ADO.NET connection string"
}

output "key_vault_id" {
  value       = azurerm_key_vault.yawl.id
  description = "Key Vault ID"
}

output "key_vault_uri" {
  value       = azurerm_key_vault.yawl.vault_uri
  description = "Key Vault URI"
}

output "vnet_id" {
  value       = azurerm_virtual_network.yawl.id
  description = "Virtual Network ID"
}

output "subnet_id" {
  value       = azurerm_subnet.postgresql.id
  description = "Subnet ID"
}

output "log_analytics_workspace_id" {
  value       = azurerm_log_analytics_workspace.yawl.id
  description = "Log Analytics Workspace ID"
}
