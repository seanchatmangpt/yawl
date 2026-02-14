terraform {
  required_version = ">= 1.3.0"
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 5.0"
    }
    google-beta = {
      source  = "hashicorp/google-beta"
      version = "~> 5.0"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.20"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.1"
    }
  }

  backend "gcs" {
    bucket = "yawl-terraform-state"
    prefix = "teradata"
  }
}

provider "google" {
  project = var.gcp_project_id
  region  = var.gcp_region
}

provider "google-beta" {
  project = var.gcp_project_id
  region  = var.gcp_region
}

# Get GKE cluster credentials
data "google_client_config" "default" {}

provider "kubernetes" {
  host                   = "https://${google_container_cluster.yawl_teradata.endpoint}"
  token                  = data.google_client_config.default.access_token
  cluster_ca_certificate = base64decode(google_container_cluster.yawl_teradata.master_auth[0].cluster_ca_certificate)
}

# ============================================================================
# VPC Network and Networking
# ============================================================================

resource "google_compute_network" "yawl_teradata" {
  name                    = var.network_name
  auto_create_subnetworks = false
  routing_mode            = "REGIONAL"
}

resource "google_compute_subnetwork" "yawl_teradata" {
  name          = "${var.network_name}-subnet"
  ip_cidr_range = var.subnet_cidr
  region        = var.gcp_region
  network       = google_compute_network.yawl_teradata.id

  private_ip_google_access = true

  log_config {
    aggregation_interval = "INTERVAL_5_SEC"
    flow_logs_enabled    = true
    metadata             = "INCLUDE_ALL_METADATA"
  }
}

# ============================================================================
# Compute Instance for Teradata
# ============================================================================

resource "google_compute_address" "teradata" {
  name   = "${var.teradata_instance_name}-ip"
  region = var.gcp_region
}

resource "google_compute_instance" "teradata" {
  name         = var.teradata_instance_name
  machine_type = var.teradata_instance_type
  zone         = "${var.gcp_region}-a"

  boot_disk {
    initialize_params {
      image = "teradata-database/teradata-express-edition"
      size  = var.teradata_disk_size
      type  = "zones/${var.gcp_region}-a/${var.teradata_disk_type}"
    }

    auto_delete = false
  }

  network_interface {
    network    = google_compute_network.yawl_teradata.name
    subnetwork = google_compute_subnetwork.yawl_teradata.name

    access_config {
      nat_ip = google_compute_address.teradata.address
    }
  }

  metadata = {
    enable-oslogin = "true"
  }

  service_account {
    scopes = ["cloud-platform"]
  }

  labels = var.labels

  tags = var.tags

  lifecycle {
    create_before_destroy = true
  }

  depends_on = [google_compute_network.yawl_teradata]
}

# ============================================================================
# Firewall Rules
# ============================================================================

resource "google_compute_firewall" "teradata_ingress" {
  name    = "${var.teradata_instance_name}-ingress"
  network = google_compute_network.yawl_teradata.name

  allow {
    protocol = "tcp"
    ports    = [var.teradata_port, "1025", "8443"]
  }

  source_ranges = var.allowed_cidr_blocks
  target_tags   = var.tags
}

resource "google_compute_firewall" "teradata_egress" {
  name    = "${var.teradata_instance_name}-egress"
  network = google_compute_network.yawl_teradata.name

  allow {
    protocol = "tcp"
    ports    = ["443", "80"]
  }

  allow {
    protocol = "udp"
    ports    = ["53"]
  }

  direction       = "EGRESS"
  destination_ranges = ["0.0.0.0/0"]
  target_tags     = var.tags
}

# ============================================================================
# GKE Cluster for YAWL Engine
# ============================================================================

resource "google_container_cluster" "yawl_teradata" {
  name     = "yawl-teradata-cluster"
  location = var.gcp_region

  remove_default_node_pool = true
  initial_node_count       = 1

  network    = google_compute_network.yawl_teradata.name
  subnetwork = google_compute_subnetwork.yawl_teradata.name

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

  resource_labels = var.labels
}

resource "google_container_node_pool" "yawl_teradata_nodes" {
  name           = "yawl-teradata-node-pool"
  cluster        = google_container_cluster.yawl_teradata.name
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

    tags = var.tags

    shielded_instance_config {
      enable_secure_boot          = true
      enable_integrity_monitoring = true
    }

    labels = var.labels
  }

  management {
    auto_repair  = true
    auto_upgrade = true
  }
}

# ============================================================================
# Artifact Registry for Container Images
# ============================================================================

resource "google_artifact_registry_repository" "yawl_teradata" {
  repository_id = "yawl-teradata"
  location      = var.gcp_region
  format        = "DOCKER"
  description   = "YAWL Workflow Engine with Teradata integration"

  docker_config {
    immutable_tags = false
  }
}

# ============================================================================
# Cloud Storage for Backups
# ============================================================================

resource "google_storage_bucket" "yawl_teradata_backups" {
  name          = "${var.gcp_project_id}-yawl-teradata-backups"
  location      = var.gcp_region
  force_destroy = false

  uniform_bucket_level_access = true

  versioning {
    enabled = true
  }

  encryption {
    default_kms_key_name = var.enable_encryption_at_rest ? google_kms_crypto_key.backup.id : null
  }

  lifecycle_rule {
    condition {
      num_newer_versions = 10
    }
    action {
      action = "Delete"
    }
  }

  lifecycle_rule {
    condition {
      age_days = var.backup_retention_days
    }
    action {
      type = "Delete"
    }
  }

  lifecycle_rule {
    condition {
      age_days = 30
    }
    action {
      type          = "SetStorageClass"
      storage_class = "NEARLINE"
    }
  }

  labels = var.labels
}

# ============================================================================
# KMS for Encryption
# ============================================================================

resource "google_kms_key_ring" "yawl_teradata" {
  name     = "yawl-teradata-keyring"
  location = var.gcp_region
}

resource "google_kms_crypto_key" "backup" {
  name            = "yawl-teradata-backup-key"
  key_ring        = google_kms_key_ring.yawl_teradata.id
  rotation_period = "7776000s"
}

resource "google_kms_crypto_key" "database" {
  name            = "yawl-teradata-db-key"
  key_ring        = google_kms_key_ring.yawl_teradata.id
  rotation_period = "7776000s"
}

# ============================================================================
# Cloud SQL for YAWL Metadata (Postgres)
# ============================================================================

resource "random_password" "yawl_db_password" {
  length  = 32
  special = true
}

resource "google_sql_database_instance" "yawl_metadata" {
  name                = "yawl-metadata-postgres"
  database_version    = "POSTGRES_15"
  region              = var.gcp_region
  deletion_protection = true

  settings {
    tier              = "db-custom-2-7680"
    availability_type = "REGIONAL"
    disk_size         = 100
    disk_type         = "PD_SSD"
    disk_autoresize   = true

    backup_configuration {
      enabled                        = var.enable_automatic_backups
      start_time                     = "${padded_zero(var.backup_window_start_hour)}:00"
      point_in_time_recovery_enabled = true
      transaction_log_retention_days = 7
      backup_retention_settings {
        retained_backups = 30
        retention_unit   = "COUNT"
      }
    }

    ip_configuration {
      require_ssl             = true
      private_network         = google_compute_network.yawl_teradata.id
      enable_private_path_import = false
      authorized_networks {
        value = "0.0.0.0/0"
        name  = "all"
      }
    }

    database_flags {
      name  = "max_connections"
      value = "500"
    }

    user_labels = var.labels
  }
}

resource "google_sql_database" "yawl_metadata" {
  name     = "yawl_metadata"
  instance = google_sql_database_instance.yawl_metadata.name
}

resource "google_sql_user" "yawl_metadata_user" {
  name     = var.yawl_db_user
  instance = google_sql_database_instance.yawl_metadata.name
  password = random_password.yawl_db_password.result
}

# ============================================================================
# Cloud Redis for Caching
# ============================================================================

resource "google_redis_instance" "yawl_cache" {
  name           = "yawl-teradata-cache"
  memory_size_gb = 5
  region         = var.gcp_region
  tier           = "STANDARD_HA"
  redis_version  = "7.0"

  location_id             = "${var.gcp_region}-a"
  alternative_location_id = "${var.gcp_region}-b"

  authorized_network = google_compute_network.yawl_teradata.id

  transit_encryption_mode = "SERVER_AUTHENTICATION"

  labels = var.labels
}

# ============================================================================
# Cloud Logging and Monitoring
# ============================================================================

resource "google_logging_project_sink" "yawl_teradata" {
  count           = var.enable_logging ? 1 : 0
  name            = "yawl-teradata-log-sink"
  destination     = "storage.googleapis.com/${google_storage_bucket.yawl_teradata_backups.name}"
  filter          = "resource.type=gce_instance OR resource.type=k8s_cluster"
  unique_writer_identity = true
}

resource "google_storage_bucket_iam_member" "yawl_teradata_logs" {
  count      = var.enable_logging ? 1 : 0
  bucket     = google_storage_bucket.yawl_teradata_backups.name
  role       = "roles/storage.objectCreator"
  member     = google_logging_project_sink.yawl_teradata[0].writer_identity
}

# ============================================================================
# Monitoring Alerts
# ============================================================================

resource "google_monitoring_alert_policy" "teradata_cpu" {
  count            = var.enable_monitoring ? 1 : 0
  display_name     = "YAWL Teradata CPU High"
  combiner         = "OR"
  enabled          = true
  notification_channels = [google_monitoring_notification_channel.yawl_email[0].id]

  conditions {
    display_name = "Teradata CPU > ${var.cpu_threshold_percent}%"

    condition_threshold {
      filter          = "metric.type=\"compute.googleapis.com/instance/cpu/utilization\" AND resource.labels.instance_id=\"${google_compute_instance.teradata.instance_id}\""
      duration        = "300s"
      comparison      = "COMPARISON_GT"
      threshold_value = var.cpu_threshold_percent / 100
    }
  }

  alert_strategy {
    auto_close = "1800s"
  }
}

resource "google_monitoring_alert_policy" "teradata_disk" {
  count            = var.enable_monitoring ? 1 : 0
  display_name     = "YAWL Teradata Disk High"
  combiner         = "OR"
  enabled          = true
  notification_channels = [google_monitoring_notification_channel.yawl_email[0].id]

  conditions {
    display_name = "Teradata Disk > ${var.disk_threshold_percent}%"

    condition_threshold {
      filter          = "metric.type=\"compute.googleapis.com/instance/disk/write_bytes_count\" AND resource.labels.instance_id=\"${google_compute_instance.teradata.instance_id}\""
      duration        = "600s"
      comparison      = "COMPARISON_GT"
      threshold_value = var.disk_threshold_percent / 100 * 1000000000
    }
  }

  alert_strategy {
    auto_close = "1800s"
  }
}

resource "google_monitoring_notification_channel" "yawl_email" {
  count           = var.enable_monitoring ? 1 : 0
  display_name    = "YAWL Teradata Alerts Email"
  type            = "email"
  enabled         = true

  labels = {
    email_address = var.alert_email
  }
}

# ============================================================================
# Helper Functions
# ============================================================================

locals {
  backup_start_time = format("%02d:00", var.backup_window_start_hour)
}

# ============================================================================
# Outputs
# ============================================================================

output "teradata_instance_ip" {
  value       = google_compute_address.teradata.address
  description = "Teradata instance external IP address"
}

output "teradata_instance_internal_ip" {
  value       = google_compute_instance.teradata.network_interface[0].network_ip
  description = "Teradata instance internal IP address"
}

output "teradata_port" {
  value       = var.teradata_port
  description = "Teradata listening port"
}

output "gke_cluster_endpoint" {
  value       = google_container_cluster.yawl_teradata.endpoint
  description = "GKE Cluster endpoint"
  sensitive   = true
}

output "gke_cluster_name" {
  value       = google_container_cluster.yawl_teradata.name
  description = "GKE Cluster name"
}

output "yawl_metadata_connection_name" {
  value       = google_sql_database_instance.yawl_metadata.connection_name
  description = "Cloud SQL connection name for YAWL metadata"
}

output "yawl_metadata_database" {
  value       = google_sql_database.yawl_metadata.name
  description = "YAWL metadata database name"
}

output "yawl_db_password" {
  value       = random_password.yawl_db_password.result
  sensitive   = true
  description = "YAWL database password (store securely in Secret Manager)"
}

output "redis_host" {
  value       = google_redis_instance.yawl_cache.host
  description = "Redis instance host"
}

output "redis_port" {
  value       = google_redis_instance.yawl_cache.port
  description = "Redis instance port"
}

output "artifact_registry_url" {
  value       = "${var.gcp_region}-docker.pkg.dev/${var.gcp_project_id}/${google_artifact_registry_repository.yawl_teradata.repository_id}"
  description = "Artifact Registry repository URL for Docker images"
}

output "backup_bucket_name" {
  value       = google_storage_bucket.yawl_teradata_backups.name
  description = "Cloud Storage bucket for backups"
}

output "network_id" {
  value       = google_compute_network.yawl_teradata.id
  description = "VPC Network ID"
}

output "subnet_id" {
  value       = google_compute_subnetwork.yawl_teradata.id
  description = "Subnet ID"
}
