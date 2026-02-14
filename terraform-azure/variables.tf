variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "Environment must be dev, staging, or prod."
  }
}

variable "project_name" {
  description = "Project name"
  type        = string
  default     = "yawl"
}

variable "location" {
  description = "Azure region for resources"
  type        = string
  default     = "East US"
  validation {
    condition     = contains(["East US", "West US", "Central US", "North Europe", "West Europe"], var.location)
    error_message = "Location must be a valid Azure region."
  }
}

# Resource Group Variables
variable "resource_group_name" {
  description = "Name of the resource group"
  type        = string
}

# Virtual Network Variables
variable "vnet_name" {
  description = "Name of the virtual network"
  type        = string
}

variable "vnet_cidr" {
  description = "CIDR block for the virtual network"
  type        = string
  default     = "10.0.0.0/16"
}

variable "subnet_configs" {
  description = "Configuration for subnets"
  type = map(object({
    cidr                          = string
    enforce_private_link_endpoint = optional(bool, false)
    enforce_private_link_service  = optional(bool, false)
  }))
  default = {
    "app-service" = {
      cidr = "10.0.1.0/24"
    }
    "database" = {
      cidr                          = "10.0.2.0/24"
      enforce_private_link_endpoint = true
    }
    "monitoring" = {
      cidr = "10.0.3.0/24"
    }
  }
}

variable "enable_ddos_protection" {
  description = "Enable DDoS protection for the virtual network"
  type        = bool
  default     = false
}

# App Service Variables
variable "app_service_plan_name" {
  description = "Name of the App Service Plan"
  type        = string
}

variable "app_service_name" {
  description = "Name of the App Service"
  type        = string
}

variable "app_service_plan_sku" {
  description = "SKU for the App Service Plan (e.g., B1, B2, S1, S2, P1V2)"
  type        = string
  default     = "S1"
  validation {
    condition     = can(regex("^[BS]\\d|P\\dV\\d$", var.app_service_plan_sku))
    error_message = "SKU must be a valid App Service Plan SKU."
  }
}

variable "app_runtime_stack" {
  description = "Runtime stack for the App Service (e.g., DOTNETCORE|6.0, NODE|18-lts, PYTHON|3.10, JAVA|11)"
  type        = string
  default     = "DOTNETCORE|6.0"
}

# Database Variables
variable "db_server_name" {
  description = "Name of the database server"
  type        = string
}

variable "db_database_name" {
  description = "Name of the database"
  type        = string
}

variable "db_admin_username" {
  description = "Administrator username for the database"
  type        = string
  sensitive   = true
}

variable "db_admin_password" {
  description = "Administrator password for the database"
  type        = string
  sensitive   = true
}

variable "db_sku_name" {
  description = "SKU name for the database (e.g., B_Gen5_1, GP_Gen5_2)"
  type        = string
  default     = "B_Gen5_1"
}

variable "db_storage_mb" {
  description = "Maximum storage in MB for the database"
  type        = number
  default     = 51200
  validation {
    condition     = var.db_storage_mb >= 5120 && var.db_storage_mb <= 1048576
    error_message = "Storage must be between 5120 MB and 1048576 MB."
  }
}

variable "db_version" {
  description = "MySQL database version"
  type        = string
  default     = "8.0"
  validation {
    condition     = contains(["5.7", "8.0"], var.db_version)
    error_message = "Version must be 5.7 or 8.0."
  }
}

# Monitoring Variables
variable "log_analytics_workspace_name" {
  description = "Name of the Log Analytics Workspace"
  type        = string
}

variable "log_analytics_sku" {
  description = "SKU for Log Analytics Workspace (PerGB2018 or Free)"
  type        = string
  default     = "PerGB2018"
  validation {
    condition     = contains(["Free", "PerGB2018", "CapacityReservation"], var.log_analytics_sku)
    error_message = "SKU must be Free, PerGB2018, or CapacityReservation."
  }
}

variable "app_insights_name" {
  description = "Name of the Application Insights instance"
  type        = string
}

variable "log_retention_days" {
  description = "Retention period in days for logs"
  type        = number
  default     = 30
  validation {
    condition     = var.log_retention_days > 0 && var.log_retention_days <= 730
    error_message = "Retention days must be between 1 and 730."
  }
}

# Optional Features
variable "enable_backup" {
  description = "Enable backup for resources"
  type        = bool
  default     = true
}

variable "enable_monitoring" {
  description = "Enable monitoring and logging"
  type        = bool
  default     = true
}

variable "enable_private_endpoint" {
  description = "Enable private endpoints for resources"
  type        = bool
  default     = true
}

variable "tags" {
  description = "Common tags for all resources"
  type        = map(string)
  default     = {}
}
