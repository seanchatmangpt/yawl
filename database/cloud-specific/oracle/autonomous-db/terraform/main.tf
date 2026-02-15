# Oracle Cloud Infrastructure Terraform Configuration
# Production-ready Autonomous Database deployment for YAWL

terraform {
  required_version = ">= 1.0.0"

  required_providers {
    oci = {
      source  = "oracle/oci"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.0"
    }
  }
}

# Provider configuration
provider "oci" {
  tenancy_ocid     = var.tenancy_ocid
  user_ocid        = var.user_ocid
  fingerprint      = var.fingerprint
  private_key_path = var.private_key_path
  region           = var.region
}

# Variables
variable "tenancy_ocid" {
  description = "Tenancy OCID"
  type        = string
}

variable "user_ocid" {
  description = "User OCID"
  type        = string
}

variable "fingerprint" {
  description = "API key fingerprint"
  type        = string
}

variable "private_key_path" {
  description = "Path to API private key"
  type        = string
}

variable "region" {
  description = "OCI region"
  type        = string
  default     = "us-phoenix-1"
}

variable "compartment_id" {
  description = "Compartment OCID"
  type        = string
}

variable "environment" {
  description = "Environment name"
  type        = string
  default     = "prod"
}

variable "display_name" {
  description = "Display name for the database"
  type        = string
  default     = "yawl-db"
}

variable "db_name" {
  description = "Database name (must be unique, max 14 chars)"
  type        = string
  default     = "YAWLDB"
}

variable "db_workload" {
  description = "Database workload type (OLTP, DW, AJD)"
  type        = string
  default     = "OLTP"
}

variable "cpu_core_count" {
  description = "CPU core count"
  type        = number
  default     = 2
}

variable "data_storage_size_in_tbs" {
  description = "Data storage size in TBs"
  type        = number
  default     = 1
}

variable "db_version" {
  description = "Database version"
  type        = string
  default     = "19c"
}

variable "license_model" {
  description = "License model (LICENSE_INCLUDED, BRING_YOUR_OWN_LICENSE)"
  type        = string
  default     = "LICENSE_INCLUDED"
}

variable "is_auto_scaling_enabled" {
  description = "Enable auto scaling"
  type        = bool
  default     = true
}

variable "backup_retention_period_in_days" {
  description = "Backup retention period in days"
  type        = number
  default     = 30
}

variable "vcn_cidr" {
  description = "VCN CIDR block"
  type        = string
  default     = "10.0.0.0/16"
}

variable "subnet_cidr" {
  description = "Subnet CIDR block"
  type        = string
  default     = "10.0.1.0/24"
}

variable "private_endpoint_label" {
  description = "Private endpoint label"
  type        = string
  default     = "yawldb"
}

variable "tags" {
  description = "Tags for resources"
  type        = map(string)
  default = {
    Project     = "YAWL"
    Environment = "Production"
    ManagedBy   = "Terraform"
  }
}

# Random password for admin
resource "random_password" "admin_password" {
  length           = 16
  special          = true
  override_special = "!#$%&*()-_=+[]{}<>:?"
  min_lower        = 1
  min_upper        = 1
  min_numeric      = 1
}

# VCN
resource "oci_core_vcn" "yawl" {
  compartment_id = var.compartment_id
  display_name   = "yawl-vcn-${var.environment}"
  cidr_block     = var.vcn_cidr
  dns_label      = "yawlvcn"

  freeform_tags = var.tags
}

# Subnet
resource "oci_core_subnet" "yawl" {
  compartment_id    = var.compartment_id
  vcn_id            = oci_core_vcn.yawl.id
  display_name      = "yawl-db-subnet-${var.environment}"
  cidr_block        = var.subnet_cidr
  dns_label         = "yawldbsubnet"
  prohibit_public_ip_on_vnic = true

  freeform_tags = var.tags
}

# Security List
resource "oci_core_security_list" "yawl_db" {
  compartment_id = var.compartment_id
  vcn_id         = oci_core_vcn.yawl.id
  display_name   = "yawl-db-security-list-${var.environment}"

  egress_security_rules {
    destination = "0.0.0.0/0"
    protocol    = "all"
  }

  ingress_security_rules {
    source   = var.vcn_cidr
    protocol = "6"

    tcp_options {
      min = 1521
      max = 1522
    }
  }

  freeform_tags = var.tags
}

# Route Table
resource "oci_core_route_table" "yawl" {
  compartment_id = var.compartment_id
  vcn_id         = oci_core_vcn.yawl.id
  display_name   = "yawl-route-table-${var.environment}"

  # No internet gateway for private subnet
  freeform_tags = var.tags
}

# Service Gateway
resource "oci_core_service_gateway" "yawl" {
  compartment_id = var.compartment_id
  vcn_id         = oci_core_vcn.yawl.id
  display_name   = "yawl-service-gateway-${var.environment}"

  services {
    service_id = data.oci_core_services.all_oci_services.services[0].id
  }

  freeform_tags = var.tags
}

data "oci_core_services" "all_oci_services" {
  filter {
    name   = "name"
    values = ["All .* Services In .*"]
    regex  = true
  }
}

# NAT Gateway (for private subnet to access internet)
resource "oci_core_nat_gateway" "yawl" {
  compartment_id = var.compartment_id
  vcn_id         = oci_core_vcn.yawl.id
  display_name   = "yawl-nat-gateway-${var.environment}"

  freeform_tags = var.tags
}

# Autonomous Database
resource "oci_database_autonomous_database" "yawl" {
  compartment_id                       = var.compartment_id
  display_name                         = "${var.display_name}-${var.environment}"
  db_name                              = "${var.db_name}${var.environment == "prod" ? "" : replace(var.environment, "-", "")}"
  db_workload                          = var.db_workload
  cpu_core_count                       = var.cpu_core_count
  data_storage_size_in_tbs             = var.data_storage_size_in_tbs
  admin_password                       = random_password.admin_password.result
  db_version                           = var.db_version
  license_model                        = var.license_model
  is_auto_scaling_enabled              = var.is_auto_scaling_enabled
  backup_retention_period_in_days      = var.backup_retention_period_in_days
  subnet_id                            = oci_core_subnet.yawl.id
  private_endpoint_label               = "${var.private_endpoint_label}${var.environment == "prod" ? "" : replace(var.environment, "-", "")}"
  is_mtls_connection_required          = false

  # Long-term backup
  long_term_backup_schedule {
    schedule_type   = "WEEKLY"
    day_of_week     = "SUNDAY"
    retention_days  = 90
    time_of_hour    = 3
  }

  freeform_tags = var.tags

  lifecycle {
    prevent_destroy = false
    ignore_changes  = [admin_password]
  }
}

# Autonomous Database Wallet
resource "oci_database_autonomous_database_wallet" "yawl" {
  autonomous_database_id = oci_database_autonomous_database.yawl.id
  password               = random_password.wallet_password.result
  generate_type          = "SINGLE"
}

resource "random_password" "wallet_password" {
  length  = 16
  special = false
}

# Outputs
output "autonomous_database_id" {
  value       = oci_database_autonomous_database.yawl.id
  description = "Autonomous Database OCID"
}

output "autonomous_database_name" {
  value       = oci_database_autonomous_database.yawl.db_name
  description = "Database name"
}

output "autonomous_database_display_name" {
  value       = oci_database_autonomous_database.yawl.display_name
  description = "Display name"
}

output "private_endpoint" {
  value       = oci_database_autonomous_database.yawl.private_endpoint
  description = "Private endpoint FQDN"
}

output "connection_strings" {
  value       = oci_database_autonomous_database.yawl.connection_strings
  description = "Connection strings"
}

output "high_connection_string" {
  value       = try(oci_database_autonomous_database.yawl.connection_strings[0].profiles[0].value, null)
  description = "High priority connection string"
}

output "admin_password" {
  value     = random_password.admin_password.result
  sensitive = true
}

output "wallet_password" {
  value     = random_password.wallet_password.result
  sensitive = true
}

output "jdbc_url_high" {
  value       = "jdbc:oracle:thin:@${oci_database_autonomous_database.yawl.db_name}_high?TNS_ADMIN=/path/to/wallet"
  description = "JDBC URL for high priority connections"
}

output "jdbc_url_medium" {
  value       = "jdbc:oracle:thin:@${oci_database_autonomous_database.yawl.db_name}_medium?TNS_ADMIN=/path/to/wallet"
  description = "JDBC URL for medium priority connections"
}

output "vcn_id" {
  value       = oci_core_vcn.yawl.id
  description = "VCN ID"
}

output "subnet_id" {
  value       = oci_core_subnet.yawl.id
  description = "Subnet ID"
}
