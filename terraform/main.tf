terraform {
  required_version = ">= 1.3.0"
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 5.0"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.20"
    }
  }

  backend "gcs" {
    bucket = "yawl-terraform-state"
    prefix = "prod"
  }
}

provider "google" {
  project = var.gcp_project_id
  region  = var.gcp_region
}

# Get GKE cluster credentials
data "google_client_config" "default" {}

provider "kubernetes" {
  host                   = "https://${google_container_cluster.yawl.endpoint}"
  token                  = data.google_client_config.default.access_token
  cluster_ca_certificate = base64decode(google_container_cluster.yawl.master_auth[0].cluster_ca_certificate)
}

# GKE Cluster
resource "google_container_cluster" "yawl" {
  name     = "yawl-cluster"
  location = var.gcp_region

  remove_default_node_pool = true
  initial_node_count       = 1

  workload_identity_config {
    workload_pool = "${var.gcp_project_id}.svc.id.goog"
  }

  addons_config {
    http_load_balancing {
      disabled = false
    }
    horizontal_pod_autoscaling {
      disabled = false
    }
    network_policy_config {
      disabled = false
    }
  }

  network_policy {
    enabled  = true
    provider = "PROVIDER_UNSPECIFIED"
  }

  network    = google_compute_network.yawl.name
  subnetwork = google_compute_subnetwork.yawl.name
}

# GKE Node Pool
resource "google_container_node_pool" "yawl_nodes" {
  name           = "yawl-node-pool"
  cluster        = google_container_cluster.yawl.name
  location       = var.gcp_region
  node_count     = var.gke_node_count
  initial_node_count = var.gke_node_count

  autoscaling {
    min_node_count = var.gke_min_nodes
    max_node_count = var.gke_max_nodes
  }

  node_config {
    preemptible  = false
    machine_type = var.gke_machine_type

    oauth_scopes = [
      "https://www.googleapis.com/auth/cloud-platform",
    ]

    workload_metadata_config {
      mode = "GKE_METADATA"
    }

    tags = ["yawl", "gke-node"]

    shielded_instance_config {
      enable_secure_boot          = true
      enable_integrity_monitoring = true
    }
  }
}

# VPC Network
resource "google_compute_network" "yawl" {
  name                    = "yawl-network"
  auto_create_subnetworks = false
}

# Subnet
resource "google_compute_subnetwork" "yawl" {
  name          = "yawl-subnet"
  ip_cidr_range = "10.0.0.0/20"
  region        = var.gcp_region
  network       = google_compute_network.yawl.id

  private_ip_google_access = true

  log_config {
    aggregation_interval = "INTERVAL_5_SEC"
    flow_logs_enabled    = true
    metadata             = "INCLUDE_ALL_METADATA"
  }
}

# Cloud SQL Instance
resource "google_sql_database_instance" "yawl" {
  name                = "yawl-postgres-14"
  database_version    = "POSTGRES_14"
  region              = var.gcp_region
  deletion_protection = true

  settings {
    tier              = var.cloudsql_tier
    availability_type = "REGIONAL"
    disk_size         = var.cloudsql_disk_size
    disk_type         = "PD_SSD"
    disk_autoresize   = true

    backup_configuration {
      enabled                        = true
      start_time                     = "03:00"
      point_in_time_recovery_enabled = true
      transaction_log_retention_days = 7
      backup_retention_settings {
        retained_backups = 30
        retention_unit   = "COUNT"
      }
    }

    ip_configuration {
      require_ssl             = true
      private_network         = google_compute_network.yawl.id
      enable_private_path_import = false
      authorized_networks {
        value = "0.0.0.0/0"
        name  = "all"
      }
    }

    database_flags {
      name  = "max_connections"
      value = "200"
    }
    database_flags {
      name  = "shared_buffers"
      value = "262144"
    }
    database_flags {
      name  = "work_mem"
      value = "4096"
    }
    database_flags {
      name  = "effective_cache_size"
      value = "1048576"
    }

    user_labels = {
      app     = "yawl"
      env     = "production"
      managed = "terraform"
    }
  }
}

# Cloud SQL Database
resource "google_sql_database" "yawl" {
  name     = "yawl"
  instance = google_sql_database_instance.yawl.name
}

# Cloud SQL User
resource "random_password" "db_password" {
  length  = 32
  special = true
}

resource "google_sql_user" "yawl_user" {
  name     = "yawl"
  instance = google_sql_database_instance.yawl.name
  password = random_password.db_password.result
}

# Cloud Artifact Registry
resource "google_artifact_registry_repository" "yawl" {
  repository_id = "yawl"
  location      = var.gcp_region
  format        = "DOCKER"
  description   = "YAWL Workflow Engine Docker images"

  docker_config {
    immutable_tags = false
  }
}

# Cloud Storage for backups
resource "google_storage_bucket" "yawl_backups" {
  name          = "${var.gcp_project_id}-yawl-backups"
  location      = var.gcp_region
  force_destroy = false

  uniform_bucket_level_access = true

  versioning {
    enabled = true
  }

  lifecycle_rule {
    condition {
      num_newer_versions = 10
      age_days           = 90
    }
    action {
      action          = "Delete"
    }
  }

  lifecycle_rule {
    condition {
      age_days = 30
    }
    action {
      type = "SetStorageClass"
      storage_class = "NEARLINE"
    }
  }
}

# Static IP for Load Balancer
resource "google_compute_address" "yawl" {
  name   = "yawl-ip"
  region = var.gcp_region
}

# Cloud Memorystore (Redis) for caching
resource "google_redis_instance" "yawl" {
  name           = "yawl-cache"
  memory_size_gb = var.redis_memory_gb
  region         = var.gcp_region
  tier           = "STANDARD_HA"
  redis_version  = "7.0"

  location_id             = "${var.gcp_region}-a"
  alternative_location_id = "${var.gcp_region}-b"

  authorized_network = google_compute_network.yawl.id

  transit_encryption_mode = "SERVER_AUTHENTICATION"
}

# Monitoring Alert Policy
resource "google_monitoring_alert_policy" "yawl_cpu" {
  display_name = "YAWL GKE CPU High"
  combiner     = "OR"
  enabled      = true

  conditions {
    display_name = "GKE Container CPU > 80%"

    condition_threshold {
      filter          = "metric.type=\"kubernetes.io/container/cpu/core_usage_time\" AND resource.labels.cluster_name=\"yawl-cluster\""
      duration        = "300s"
      comparison      = "COMPARISON_GT"
      threshold_value = 0.8
    }
  }

  notification_channels = [google_monitoring_notification_channel.yawl_email.id]

  alert_strategy {
    auto_close = "1800s"
  }
}

# Notification Channel
resource "google_monitoring_notification_channel" "yawl_email" {
  display_name = "YAWL Alerts Email"
  type         = "email"

  labels = {
    email_address = var.alert_email
  }
}

# Outputs
output "cluster_endpoint" {
  value       = google_container_cluster.yawl.endpoint
  description = "GKE Cluster endpoint"
}

output "database_instance" {
  value       = google_sql_database_instance.yawl.connection_name
  description = "Cloud SQL connection name"
}

output "artifact_registry_url" {
  value       = "${var.gcp_region}-docker.pkg.dev/${var.gcp_project_id}/${google_artifact_registry_repository.yawl.repository_id}"
  description = "Artifact Registry repository URL"
}

output "static_ip" {
  value       = google_compute_address.yawl.address
  description = "Static IP for load balancer"
}

output "db_password" {
  value       = random_password.db_password.result
  sensitive   = true
  description = "Database password (store securely)"
}

output "redis_host" {
  value       = google_redis_instance.yawl.host
  description = "Redis instance host"
}

output "redis_port" {
  value       = google_redis_instance.yawl.port
  description = "Redis instance port"
}
