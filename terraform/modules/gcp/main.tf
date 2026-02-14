#------------------------------------------------------------------------------
# YAWL GCP Module - GKE, Cloud SQL, GCS
# Version: 1.0.0
#------------------------------------------------------------------------------

locals {
  name_prefix = "${var.project_name}-${var.environment}"
}

#------------------------------------------------------------------------------
# Cloud SQL (PostgreSQL)
#------------------------------------------------------------------------------

resource "google_compute_global_address" "private_ip_range" {
  name          = "${local.name_prefix}-db-private-ip"
  purpose       = "VPC_PEERING"
  address_type  = "PRIVATE"
  prefix_length = 16
  network       = var.vpc_id
  project       = var.gcp_project_id
}

resource "google_service_networking_connection" "private_vpc_connection" {
  network                 = var.vpc_id
  service                 = "servicenetworking.googleapis.com"
  reserved_peering_ranges = [google_compute_global_address.private_ip_range.name]
}

resource "random_password" "db_password" {
  length  = 32
  special = false
}

resource "google_sql_database_instance" "yawl_db" {
  name             = "${local.name_prefix}-db"
  project          = var.gcp_project_id
  region           = var.region
  database_version = "POSTGRES_${var.database_version}"

  settings {
    tier = var.database_tier
    disk_size = var.database_storage_gb
    disk_type = "PD_SSD"

    backup_configuration {
      enabled                        = true
      start_time                     = "02:00"
      location                       = var.region
      point_in_time_recovery_enabled = true
      retained_backups               = var.database_backup_days
      retention_unit                 = "COUNT"
    }

    ip_configuration {
      ipv4_enabled    = false
      private_network = var.vpc_id
      require_ssl     = true
    }

    location_preference {
      zone = var.zone
    }

    maintenance_window {
      day          = 7
      hour         = 3
      update_track = "stable"
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

    dynamic "insights_config" {
      for_each = var.enable_query_insights ? [1] : []
      content {
        query_insights_enabled  = true
        query_string_length     = 1024
        record_application_tags = true
        record_client_address   = true
      }
    }
  }

  deletion_protection = var.database_deletion_protection

  depends_on = [google_service_networking_connection.private_vpc_connection]

  lifecycle {
    ignore_changes = [settings[0].disk_size]
  }
}

resource "google_sql_database" "yawl" {
  name     = var.project_name
  project  = var.gcp_project_id
  instance = google_sql_database_instance.yawl_db.name
}

resource "google_sql_user" "yawl" {
  name     = var.database_username
  project  = var.gcp_project_id
  instance = google_sql_database_instance.yawl_db.name
  password = random_password.db_password.result
}

#------------------------------------------------------------------------------
# GKE Cluster
#------------------------------------------------------------------------------

resource "google_container_cluster" "yawl" {
  name     = "${local.name_prefix}-cluster"
  project  = var.gcp_project_id
  location = var.region

  # We will use a separately managed node pool
  remove_default_node_pool = true
  initial_node_count       = 1

  network    = var.vpc_id
  subnetwork = var.private_subnet_ids[0]

  networking_mode = "VPC_NATIVE"

  ip_allocation_policy {
    cluster_secondary_range_name  = "pods"
    services_secondary_range_name = "services"
  }

  private_cluster_config {
    enable_private_nodes    = true
    enable_private_endpoint = var.enable_private_endpoint
    master_ipv4_cidr_block  = "172.16.0.0/28"
  }

  master_auth {
    client_certificate_config {
      issue_client_certificate = false
    }
  }

  master_authorized_networks_config {
    dynamic "cidr_blocks" {
      for_each = var.authorized_networks
      content {
        cidr_block   = cidr_blocks.value
        display_name = "authorized-network-${cidr_blocks.key}"
      }
    }
  }

  vertical_pod_autoscaling {
    enabled = true
  }

  workload_identity_config {
    workload_pool = "${var.gcp_project_id}.svc.id.goog"
  }

  release_channel {
    channel = "REGULAR"
  }

  maintenance_policy {
    recurring_window {
      start_time = "2024-01-01T09:00:00Z"
      end_time   = "2024-01-01T17:00:00Z"
      recurrence = "FREQ=WEEKLY;BYDAY=SA,SU"
    }
  }

  monitoring_config {
    enable_components = ["SYSTEM_COMPONENTS", "WORKLOADS"]
    managed_prometheus {
      enabled = true
    }
  }

  logging_config {
    enable_components = ["SYSTEM_COMPONENTS", "WORKLOADS"]
  }

  addons_config {
    gce_persistent_disk_csi_driver_config {
      enabled = true
    }
    horizontal_pod_autoscaling {
      disabled = false
    }
    http_load_balancing {
      disabled = false
    }
    network_policy_config {
      disabled = false
    }
    dns_cache_config {
      enabled = true
    }
    gcp_filestore_csi_driver_config {
      enabled = true
    }
  }

  network_policy {
    enabled  = true
    provider = "CALICO"
  }

  resource_labels = var.common_tags
}

resource "google_container_node_pool" "yawl" {
  name       = "${local.name_prefix}-node-pool"
  project    = var.gcp_project_id
  location   = var.region
  cluster    = google_container_cluster.yawl.name
  node_count = var.node_count_min

  autoscaling {
    min_node_count = var.enable_autoscaling ? var.node_count_min : null
    max_node_count = var.enable_autoscaling ? var.node_count_max : null
  }

  management {
    auto_repair  = true
    auto_upgrade = true
  }

  node_config {
    machine_type = var.node_machine_type
    disk_size_gb = var.node_disk_size_gb
    disk_type    = "pd-balanced"

    preemptible = var.preemptible_nodes

    service_account = google_service_account.gke_nodes.email
    oauth_scopes = [
      "https://www.googleapis.com/auth/cloud-platform"
    ]

    labels = merge({
      environment = var.environment
      managed-by  = "terraform"
    }, var.node_pool_labels)

    dynamic "taint" {
      for_each = var.node_pool_taints
      content {
        key    = taint.value.key
        value  = taint.value.value
        effect = taint.value.effect
      }
    }

    shielded_instance_config {
      enable_secure_boot          = true
      enable_integrity_monitoring = true
    }

    workload_metadata_config {
      mode = "GKE_METADATA"
    }
  }

  lifecycle {
    ignore_changes = [node_count]
  }
}

resource "google_service_account" "gke_nodes" {
  account_id   = "${local.name_prefix}-gke-nodes"
  display_name = "GKE Node Service Account for ${local.name_prefix}"
  project      = var.gcp_project_id
}

resource "google_project_iam_member" "gke_node_roles" {
  for_each = toset([
    "roles/logging.logWriter",
    "roles/monitoring.metricWriter",
    "roles/monitoring.viewer",
    "roles/stackdriver.resourceMetadata.writer",
    "roles/artifactregistry.reader",
    "roles/storage.objectViewer"
  ])

  project = var.gcp_project_id
  role    = each.value
  member  = "serviceAccount:${google_service_account.gke_nodes.email}"
}

#------------------------------------------------------------------------------
# GCS Buckets
#------------------------------------------------------------------------------

resource "google_storage_bucket" "yawl" {
  for_each = toset(var.storage_bucket_names)

  name          = "${local.name_prefix}-${each.value}"
  project       = var.gcp_project_id
  location      = var.region
  force_destroy = var.environment == "dev" ? true : false

  uniform_bucket_level_access = true

  versioning {
    enabled = var.storage_versioning
  }

  lifecycle_rule {
    condition {
      age = var.storage_lifecycle_days
    }
    action {
      type = "SetStorageClass"
      storage_class = "NEARLINE"
    }
  }

  lifecycle_rule {
    condition {
      age = var.storage_archive_days
    }
    action {
      type = "SetStorageClass"
      storage_class = "COLDLINE"
    }
  }

  dynamic "encryption" {
    for_each = var.kms_key_id != "" ? [1] : []
    content {
      default_kms_key_name = var.kms_key_id
    }
  }

  cors {
    origin          = ["*"]
    method          = ["GET", "HEAD", "PUT", "POST", "DELETE"]
    response_header = ["*"]
    max_age_seconds = 3600
  }

  logging {
    log_bucket = "${local.name_prefix}-logs"
  }

  labels = var.common_tags
}

#------------------------------------------------------------------------------
# Outputs
#------------------------------------------------------------------------------

output "cluster_name" {
  value = google_container_cluster.yawl.name
}

output "cluster_endpoint" {
  value     = google_container_cluster.yawl.endpoint
  sensitive = true
}

output "cluster_ca_certificate" {
  value     = google_container_cluster.yawl.master_auth[0].cluster_ca_certificate
  sensitive = true
}

output "gcloud_command" {
  value = "gcloud container clusters get-credentials ${google_container_cluster.yawl.name} --region ${var.region} --project ${var.gcp_project_id}"
}

output "cloudsql_connection_name" {
  value = google_sql_database_instance.yawl_db.connection_name
}

output "db_host" {
  value = google_sql_database_instance.yawl_db.private_ip_address
}

output "db_password" {
  value     = random_password.db_password.result
  sensitive = true
}

output "bucket_urls" {
  value = { for k, v in google_storage_bucket.yawl : k => v.url }
}

output "service_account_email" {
  value = google_service_account.gke_nodes.email
}
