# IBM Cloud Databases Terraform Configuration
# Production-ready PostgreSQL deployment for YAWL

terraform {
  required_version = ">= 1.0.0"

  required_providers {
    ibm = {
      source  = "IBM-Cloud/ibm"
      version = "~> 1.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.0"
    }
  }
}

# Provider configuration
provider "ibm" {
  region           = var.region
  ibmcloud_api_key = var.ibmcloud_api_key
}

# Variables
variable "ibmcloud_api_key" {
  description = "IBM Cloud API key"
  type        = string
  sensitive   = true
}

variable "region" {
  description = "IBM Cloud region"
  type        = string
  default     = "us-south"
}

variable "resource_group" {
  description = "Resource group name"
  type        = string
  default     = "default"
}

variable "environment" {
  description = "Environment name"
  type        = string
  default     = "prod"
}

variable "service_name" {
  description = "Database service name"
  type        = string
  default     = "yawl-db"
}

variable "plan" {
  description = "Service plan (standard, enterprise)"
  type        = string
  default     = "standard"
}

variable "database_name" {
  description = "Database name"
  type        = string
  default     = "yawl"
}

variable "admin_username" {
  description = "Admin username"
  type        = string
  default     = "yawl"
}

variable "memory_allocation_mb" {
  description = "Memory allocation in MB"
  type        = number
  default     = 4096
}

variable "disk_allocation_mb" {
  description = "Disk allocation in MB"
  type        = number
  default     = 10240
}

variable "cpu_allocation_count" {
  description = "CPU allocation count"
  type        = number
  default     = 2
}

variable "auto_scaling" {
  description = "Auto-scaling configuration"
  type = object({
    memory = object({
      enabled           = bool
      free_space_percent = number
      io_percent        = number
    })
    disk = object({
      enabled           = bool
      free_space_percent = number
    })
    cpu = object({
      enabled    = bool
      io_percent = number
    })
  })
  default = {
    memory = {
      enabled            = true
      free_space_percent = 15
      io_percent         = 90
    }
    disk = {
      enabled            = true
      free_space_percent = 15
    }
    cpu = {
      enabled    = true
      io_percent = 90
    }
  }
}

variable "members" {
  description = "Number of database members"
  type        = number
  default     = 3
}

variable "tags" {
  description = "Tags for resources"
  type        = list(string)
  default     = ["yawl", "production"]
}

# Data sources
data "ibm_resource_group" "yawl" {
  name = var.resource_group
}

# Random password
resource "random_password" "db_password" {
  length           = 32
  special          = true
  override_special = "!#$%&*()-_=+[]{}<>:?"
}

# IBM Cloud Databases for PostgreSQL
resource "ibm_database" "yawl" {
  name              = "${var.service_name}-${var.environment}"
  service           = "databases-for-postgresql"
  plan              = var.plan
  location          = var.region
  resource_group_id = data.ibm_resource_group.yawl.id
  tags              = var.tags

  # Hardware allocation
  adminpassword                = random_password.db_password.result
  members_memory_allocation_mb = var.memory_allocation_mb
  members_disk_allocation_mb   = var.disk_allocation_mb
  members_cpu_allocation_count = var.cpu_allocation_count
  members                      = var.members

  # Service endpoints (private only for production)
  service_endpoints = "private"

  # User configuration
  users {
    name     = var.admin_username
    password = random_password.db_password.result
  }

  # Whitelist (IP access control)
  whitelist {
    address     = "10.0.0.0/8"
    description = "Internal VPC access"
  }

  # Auto-scaling configuration
  autoscaling {
    memory {
      capacity {
        enabled             = var.auto_scaling.memory.enabled
        free_space_remaining_percent = var.auto_scaling.memory.free_space_percent
      }
      io_utilization {
        enabled       = var.auto_scaling.memory.enabled
        over_period   = "30m"
        above_percent = var.auto_scaling.memory.io_percent
      }
    }
    disk {
      capacity {
        enabled             = var.auto_scaling.disk.enabled
        free_space_remaining_percent = var.auto_scaling.disk.free_space_percent
      }
    }
  }

  # Timeouts
  timeouts {
    create = "120m"
    update = "120m"
    delete = "30m"
  }
}

# Database within the instance
resource "ibm_database_connection" "yawl" {
  resource_group_id = data.ibm_resource_group.yawl.id
  deployment_id     = ibm_database.yawl.id
  user_type         = "database"
  user_id           = var.admin_username
  password          = random_password.db_password.result
  database          = var.database_name
}

# Service credentials
resource "ibm_resource_key" "yawl_db" {
  name                 = "${var.service_name}-credentials-${var.environment}"
  resource_instance_id = ibm_database.yawl.id
  role                 = "Writer"

  parameters = {
    database = var.database_name
    user_id  = var.admin_username
  }
}

# Log Analysis (for logging)
resource "ibm_resource_instance" "logdna" {
  count             = var.environment == "prod" ? 1 : 0
  name              = "yawl-logging-${var.environment}"
  service           = "logdna"
  plan              = "7-day"
  location          = var.region
  resource_group_id = data.ibm_resource_group.yawl.id
  tags              = var.tags
}

# Sysdig Monitoring
resource "ibm_resource_instance" "sysdig" {
  count             = var.environment == "prod" ? 1 : 0
  name              = "yawl-monitoring-${var.environment}"
  service           = "sysdig-monitor"
  plan              = "graduated-tier"
  location          = var.region
  resource_group_id = data.ibm_resource_group.yawl.id
  tags              = var.tags
}

# Outputs
output "database_id" {
  value       = ibm_database.yawl.id
  description = "Database instance ID"
}

output "database_name" {
  value       = ibm_database.yawl.name
  description = "Database instance name"
}

output "database_status" {
  value       = ibm_database.yawl.status
  description = "Database status"
}

output "admin_username" {
  value       = var.admin_username
  description = "Admin username"
}

output "admin_password" {
  value     = random_password.db_password.result
  sensitive = true
}

output "connection_strings" {
  value       = ibm_database.yawl.connectionstrings
  description = "Connection strings"
}

output "postgres_uri" {
  value       = try(ibm_database.yawl.connectionstrings[0].composed[0], null)
  description = "PostgreSQL connection URI"
  sensitive   = true
}

output "postgres_host" {
  value       = try(ibm_database.yawl.connectionstrings[0].hosts[0].hostname, null)
  description = "PostgreSQL host"
}

output "postgres_port" {
  value       = try(ibm_database.yawl.connectionstrings[0].hosts[0].port, null)
  description = "PostgreSQL port"
}

output "certificate" {
  value       = ibm_database.yawl.certificate
  description = "SSL certificate"
  sensitive   = true
}

output "jdbc_url" {
  value       = "jdbc:postgresql://${try(ibm_database.yawl.connectionstrings[0].hosts[0].hostname, "host")}:${try(ibm_database.yawl.connectionstrings[0].hosts[0].port, 5432)}/${var.database_name}?sslmode=verify-full"
  description = "JDBC URL"
}

output "service_credentials_json" {
  value       = ibm_resource_key.yawl_db.credentials
  description = "Service credentials JSON"
  sensitive   = true
}

output "resource_group_id" {
  value       = data.ibm_resource_group.yawl.id
  description = "Resource group ID"
}
