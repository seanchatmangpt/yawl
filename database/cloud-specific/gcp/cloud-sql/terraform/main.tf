# GCP Cloud SQL Terraform Configuration
# Production-ready PostgreSQL deployment for YAWL

terraform {
  required_version = ">= 1.0.0"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.0"
    }
  }

  backend "gcs" {
    bucket = "yawl-terraform-state"
    prefix = "cloud-sql"
  }
}

# Variables
variable "project_id" {
  description = "GCP Project ID"
  type        = string
}

variable "region" {
  description = "GCP Region"
  type        = string
  default     = "us-central1"
}

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
  default     = "prod"
}

variable "database_name" {
  description = "Database name"
  type        = string
  default     = "yawl"
}

variable "database_version" {
  description = "PostgreSQL version"
  type        = string
  default     = "POSTGRES_14"
}

variable "tier" {
  description = "Instance tier"
  type        = string
  default     = "db-custom-2-8192"
}

variable "disk_size_gb" {
  description = "Disk size in GB"
  type        = number
  default     = 100
}

variable "disk_type" {
  description = "Disk type (PD_SSD or PD_HDD)"
  type        = string
  default     = "PD_SSD"
}

variable "availability_type" {
  description = "Availability type (REGIONAL or ZONAL)"
  type        = string
  default     = "REGIONAL"
}

variable "deletion_protection" {
  description = "Enable deletion protection"
  type        = bool
  default     = true
}

variable "enable_point_in_time_recovery" {
  description = "Enable point-in-time recovery"
  type        = bool
  default     = true
}

variable "backup_retention_days" {
  description = "Backup retention days"
  type        = number
  default     = 30
}

variable "max_connections" {
  description = "Maximum connections"
  type        = number
  default     = 200
}

variable "network_name" {
  description = "VPC network name"
  type        = string
  default     = "yawl-vpc"
}

variable "enable_iam_auth" {
  description = "Enable IAM database authentication"
  type        = bool
  default     = true
}

variable "ssl_mode" {
  description = "SSL mode (ENCRYPTED_ONLY, ALLOW_UNENCRYPTED_AND_ENCRYPTED)"
  type        = string
  default     = "ENCRYPTED_ONLY"
}

# Provider configuration
provider "google" {
  project = var.project_id
  region  = var.region
}

# Random password for database user
resource "random_password" "db_password" {
  length           = 32
  special          = true
  override_special = "!#$%&*()-_=+[]{}<>:?"
}

# VPC Network
resource "google_compute_network" "yawl_vpc" {
  name                    = var.network_name
  project                 = var.project_id
  auto_create_subnetworks = false
  routing_mode            = "REGIONAL"
}

# Private IP allocation
resource "google_compute_global_address" "private_ip_range" {
  name          = "google-managed-services-${var.environment}"
  project       = var.project_id
  purpose       = "VPC_PEERING"
  address_type  = "INTERNAL"
  prefix_length = 16
  network       = google_compute_network.yawl_vpc.id
}

# Private connection
resource "google_service_networking_connection" "private_vpc_connection" {
  network                 = google_compute_network.yawl_vpc.id
  service                 = "servicenetworking.googleapis.com"
  reserved_peering_ranges = [google_compute_global_address.private_ip_range.name]
}

# Cloud SQL instance
resource "google_sql_database_instance" "yawl_db" {
  name             = "yawl-db-${var.environment}"
  project          = var.project_id
  region           = var.region
  database_version = var.database_version

  settings {
    tier              = var.tier
    disk_size         = var.disk_size_gb
    disk_type         = var.disk_type
    availability_type = var.availability_type

    # Enable auto storage increase
    disk_autoresize       = true
    disk_autoresize_limit = 500

    # Backup configuration
    backup_configuration {
      enabled                        = true
      start_time                     = "02:00"
      location                       = var.region
      point_in_time_recovery_enabled = var.enable_point_in_time_recovery
      transaction_log_retention_days = var.backup_retention_days
      retained_backups               = var.backup_retention_days
    }

    # Maintenance window
    maintenance_window {
      day          = 7  # Sunday
      hour         = 3
      update_track = "stable"
    }

    # IP configuration
    ip_configuration {
      ipv4_enabled       = false
      private_network    = google_compute_network.yawl_vpc.id
      ssl_mode           = var.ssl_mode
      enable_iam_authn   = var.enable_iam_auth
      allocated_ip_range = google_compute_global_address.private_ip_range.name
    }

    # Database flags
    database_flags {
      name  = "max_connections"
      value = tostring(var.max_connections)
    }

    database_flags {
      name  = "shared_buffers"
      value = "256MB"
    }

    database_flags {
      name  = "work_mem"
      value = "16MB"
    }

    database_flags {
      name  = "maintenance_work_mem"
      value = "128MB"
    }

    database_flags {
      name  = "log_min_duration_statement"
      value = "1000"  # Log queries > 1 second
    }

    database_flags {
      name  = "log_checkpoints"
      value = "on"
    }

    database_flags {
      name  = "log_connections"
      value = "on"
    }

    database_flags {
      name  = "log_disconnections"
      value = "on"
    }

    database_flags {
      name  = "log_lock_waits"
      value = "on"
    }

    # Insights configuration
    insights_config {
      query_insights_enabled  = true
      query_string_length     = 1024
      record_application_tags = true
      record_client_address   = true
    }
  }

  deletion_protection = var.deletion_protection

  depends_on = [google_service_networking_connection.private_vpc_connection]

  lifecycle {
    prevent_destroy = true
  }

  timeouts {
    create = "30m"
    update = "30m"
    delete = "30m"
  }
}

# Database
resource "google_sql_database" "yawl" {
  name       = var.database_name
  project    = var.project_id
  instance   = google_sql_database_instance.yawl_db.name
  charset    = "UTF8"
  collation  = "en_US.UTF8"
  depends_on = [google_sql_database_instance.yawl_db]
}

# Database user
resource "google_sql_user" "yawl" {
  name       = "yawl"
  project    = var.project_id
  instance   = google_sql_database_instance.yawl_db.name
  password   = random_password.db_password.result
  depends_on = [google_sql_database_instance.yawl_db]
}

# IAM service account for Cloud SQL Proxy
resource "google_service_account" "cloudsql_proxy" {
  account_id   = "yawl-cloudsql-proxy"
  project      = var.project_id
  display_name = "YAWL Cloud SQL Proxy Service Account"
  description  = "Service account for Cloud SQL Proxy authentication"
}

# IAM bindings for Cloud SQL Proxy
resource "google_project_iam_member" "cloudsql_client" {
  project = var.project_id
  role    = "roles/cloudsql.client"
  member  = "serviceAccount:${google_service_account.cloudsql_proxy.email}"
}

resource "google_project_iam_member" "cloudsql_instanceUser" {
  project = var.project_id
  role    = "roles/cloudsql.instanceUser"
  member  = "serviceAccount:${google_service_account.cloudsql_proxy.email}"
}

# SSL certificate
resource "google_sql_ssl_cert" "yawl_client" {
  common_name = "yawl-client-${var.environment}"
  project     = var.project_id
  instance    = google_sql_database_instance.yawl_db.name
}

# Outputs
output "instance_name" {
  value       = google_sql_database_instance.yawl_db.name
  description = "Cloud SQL instance name"
}

output "instance_connection_name" {
  value       = google_sql_database_instance.yawl_db.connection_name
  description = "Cloud SQL connection name"
}

output "private_ip_address" {
  value       = google_sql_database_instance.yawl_db.private_ip_address
  description = "Private IP address"
}

output "database_name" {
  value       = google_sql_database.yawl.name
  description = "Database name"
}

output "database_user" {
  value       = google_sql_user.yawl.name
  description = "Database username"
}

output "database_password" {
  value     = random_password.db_password.result
  sensitive = true
}

output "proxy_service_account_email" {
  value       = google_service_account.cloudsql_proxy.email
  description = "Service account for Cloud SQL Proxy"
}

output "jdbc_url" {
  value       = "jdbc:postgresql:///${var.database_name}?cloudSqlInstance=${google_sql_database_instance.yawl_db.connection_name}&socketFactory=com.google.cloud.sql.postgres.SocketFactory"
  description = "JDBC URL for Cloud SQL"
}

output "ssl_cert" {
  value     = google_sql_ssl_cert.yawl_client.cert
  sensitive = true
}

output "ssl_key" {
  value     = google_sql_ssl_cert.yawl_client.private_key
  sensitive = true
}

output "ssl_ca" {
  value     = google_sql_ssl_cert.yawl_client.server_ca_cert
  sensitive = true
}
