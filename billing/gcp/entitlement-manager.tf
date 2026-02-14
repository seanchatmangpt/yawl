# GCP Marketplace Entitlement Manager
# Terraform configuration for managing entitlements and billing infrastructure

terraform {
  required_version = ">= 1.5.0"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 5.0"
    }
    google-beta = {
      source  = "hashicorp/google-beta"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.5"
    }
    time = {
      source  = "hashicorp/time"
      version = "~> 0.9"
    }
  }

  backend "gcs" {
    bucket = "yawl-terraform-state"
    prefix = "billing/gcp"
  }
}

# Provider configuration
provider "google" {
  project = var.project_id
  region  = var.region
}

provider "google-beta" {
  project = var.project_id
  region  = var.region
}

# Variables
variable "project_id" {
  description = "GCP project ID"
  type        = string
}

variable "region" {
  description = "GCP region for resources"
  type        = string
  default     = "us-central1"
}

variable "marketplace_participant_id" {
  description = "GCP Marketplace participant ID"
  type        = string
}

variable "service_account_key" {
  description = "Base64 encoded service account key for metering"
  type        = string
  sensitive   = true
}

# Service account for metering
resource "google_service_account" "metering_service" {
  account_id   = "yawl-metering"
  display_name = "YAWL Marketplace Metering Service"
  description  = "Service account for GCP Marketplace usage metering"
  project      = var.project_id
}

# IAM roles for metering service account
resource "google_project_iam_member" "metering_roles" {
  for_each = toset([
    "roles/servicecontrol.serviceController",
    "roles/cloudcommerceprocurement.admin",
    "roles/monitoring.metricWriter",
    "roles/logging.logWriter"
  ])

  project = var.project_id
  role    = each.value
  member  = "serviceAccount:${google_service_account.metering_service.email}"
}

# Service account key
resource "google_service_account_key" "metering_key" {
  service_account_id = google_service_account.metering_service.name
  key_algorithm      = "KEY_ALG_RSA_2048"
  private_key_type   = "TYPE_GOOGLE_CREDENTIALS_FILE"
}

# Secret for storing credentials
resource "google_secret_manager_secret" "marketplace_credentials" {
  secret_id = "gcp-marketplace-credentials"
  project   = var.project_id

  replication {
    auto {}
  }
}

resource "google_secret_manager_secret_version" "marketplace_credentials_version" {
  secret      = google_secret_manager_secret.marketplace_credentials.id
  secret_data = base64decode(google_service_account_key.metering_key.private_key)
}

# Pub/Sub topic for entitlement events
resource "google_pubsub_topic" "entitlement_events" {
  name    = "yawl-entitlement-events"
  project = var.project_id

  message_retention_duration = "86400s"
}

# Pub/Sub subscription for entitlement processing
resource "google_pubsub_subscription" "entitlement_processor" {
  name    = "yawl-entitlement-processor"
  topic   = google_pubsub_topic.entitlement_events.name
  project = var.project_id

  ack_deadline_seconds = 60

  retry_policy {
    minimum_backoff = "10s"
    maximum_backoff = "600s"
  }

  dead_letter_policy {
    dead_letter_topic     = google_pubsub_topic.entitlement_dlq.id
    max_delivery_attempts = 5
  }
}

# Dead letter queue for failed entitlement processing
resource "google_pubsub_topic" "entitlement_dlq" {
  name    = "yawl-entitlement-dlq"
  project = var.project_id
}

resource "google_pubsub_subscription" "entitlement_dlq_subscription" {
  name    = "yawl-entitlement-dlq-sub"
  topic   = google_pubsub_topic.entitlement_dlq.name
  project = var.project_id
}

# Cloud Storage bucket for usage reports
resource "google_storage_bucket" "usage_reports" {
  name          = "${var.project_id}-yawl-usage-reports"
  location      = var.region
  project       = var.project_id
  force_destroy = false

  uniform_bucket_level_access = true

  versioning {
    enabled = true
  }

  lifecycle_rule {
    condition {
      age = 365
    }
    action {
      type = "Delete"
    }
  }

  lifecycle_rule {
    condition {
      age = 30
    }
    action {
      type = "SetStorageClass"
      storage_class = "NEARLINE"
    }
  }
}

# BigQuery dataset for usage analytics
resource "google_bigquery_dataset" "usage_analytics" {
  dataset_id  = "yawl_usage_analytics"
  project     = var.project_id
  location    = var.region
  description = "YAWL usage analytics and billing data"

  default_table_expiration_ms = null

  labels = {
    environment = "production"
    component   = "billing"
  }
}

# Usage records table
resource "google_bigquery_table" "usage_records" {
  dataset_id = google_bigquery_dataset.usage_analytics.dataset_id
  table_id   = "usage_records"
  project    = var.project_id

  time_partitioning {
    type  = "DAY"
    field = "timestamp"
  }

  clustering = ["customer_id", "entitlement_id"]

  schema = <<EOF
[
  {"name": "timestamp", "type": "TIMESTAMP", "mode": "REQUIRED"},
  {"name": "customer_id", "type": "STRING", "mode": "REQUIRED"},
  {"name": "entitlement_id", "type": "STRING", "mode": "REQUIRED"},
  {"name": "plan_id", "type": "STRING", "mode": "REQUIRED"},
  {"name": "workflow_name", "type": "STRING", "mode": "NULLABLE"},
  {"name": "execution_count", "type": "INT64", "mode": "REQUIRED"},
  {"name": "compute_units", "type": "FLOAT64", "mode": "REQUIRED"},
  {"name": "execution_time_ms", "type": "INT64", "mode": "REQUIRED"},
  {"name": "region", "type": "STRING", "mode": "NULLABLE"},
  {"name": "status", "type": "STRING", "mode": "NULLABLE"},
  {"name": "reported_to_gcp", "type": "BOOLEAN", "mode": "REQUIRED"},
  {"name": "report_timestamp", "type": "TIMESTAMP", "mode": "NULLABLE"}
]
EOF
}

# Entitlements table
resource "google_bigquery_table" "entitlements" {
  dataset_id = google_bigquery_dataset.usage_analytics.dataset_id
  table_id   = "entitlements"
  project    = var.project_id

  time_partitioning {
    type  = "DAY"
    field = "created_at"
  }

  clustering = ["customer_id"]

  schema = <<EOF
[
  {"name": "entitlement_id", "type": "STRING", "mode": "REQUIRED"},
  {"name": "customer_id", "type": "STRING", "mode": "REQUIRED"},
  {"name": "account_id", "type": "STRING", "mode": "REQUIRED"},
  {"name": "plan_id", "type": "STRING", "mode": "REQUIRED"},
  {"name": "state", "type": "STRING", "mode": "REQUIRED"},
  {"name": "created_at", "type": "TIMESTAMP", "mode": "REQUIRED"},
  {"name": "updated_at", "type": "TIMESTAMP", "mode": "REQUIRED"},
  {"name": "activated_at", "type": "TIMESTAMP", "mode": "NULLABLE"},
  {"name": "cancelled_at", "type": "TIMESTAMP", "mode": "NULLABLE"},
  {"name": "trial_end_at", "type": "TIMESTAMP", "mode": "NULLABLE"},
  {"name": "current_period_start", "type": "TIMESTAMP", "mode": "NULLABLE"},
  {"name": "current_period_end", "type": "TIMESTAMP", "mode": "NULLABLE"},
  {"name": "billing_period", "type": "STRING", "mode": "NULLABLE"},
  {"name": "custom_pricing", "type": "BOOLEAN", "mode": "REQUIRED"}
]
EOF
}

# Billing events table
resource "google_bigquery_table" "billing_events" {
  dataset_id = google_bigquery_dataset.usage_analytics.dataset_id
  table_id   = "billing_events"
  project    = var.project_id

  time_partitioning {
    type  = "DAY"
    field = "event_timestamp"
  }

  clustering = ["customer_id", "event_type"]

  schema = <<EOF
[
  {"name": "event_id", "type": "STRING", "mode": "REQUIRED"},
  {"name": "event_timestamp", "type": "TIMESTAMP", "mode": "REQUIRED"},
  {"name": "customer_id", "type": "STRING", "mode": "REQUIRED"},
  {"name": "entitlement_id", "type": "STRING", "mode": "REQUIRED"},
  {"name": "event_type", "type": "STRING", "mode": "REQUIRED"},
  {"name": "amount", "type": "FLOAT64", "mode": "NULLABLE"},
  {"name": "currency", "type": "STRING", "mode": "NULLABLE"},
  {"name": "usage_details", "type": "JSON", "mode": "NULLABLE"},
  {"name": "metadata", "type": "JSON", "mode": "NULLABLE"}
]
EOF
}

# Cloud Monitoring dashboard
resource "google_monitoring_dashboard" "billing_dashboard" {
  dashboard_json = jsonencode({
    displayName = "YAWL Billing Dashboard"
    gridLayout = {
      widgets = [
        {
          title = "Daily Active Subscriptions"
          xyChart = {
            dataSets = [{
              timeSeriesQuery = {
                timeSeriesFilter = {
                  filter = "metric.type=\"custom.googleapis.com/yawl/active_subscriptions\""
                  aggregation = {
                    alignmentPeriod = "86400s"
                    perSeriesAligner = "ALIGN_MEAN"
                  }
                }
              }
            }]
          }
        },
        {
          title = "Usage Revenue"
          xyChart = {
            dataSets = [{
              timeSeriesQuery = {
                timeSeriesFilter = {
                  filter = "metric.type=\"custom.googleapis.com/yawl/usage_revenue\""
                  aggregation = {
                    alignmentPeriod = "3600s"
                    perSeriesAligner = "ALIGN_SUM"
                  }
                }
              }
            }]
          }
        },
        {
          title = "Metering Success Rate"
          xyChart = {
            dataSets = [{
              timeSeriesQuery = {
                timeSeriesFilter = {
                  filter = "metric.type=\"custom.googleapis.com/yawl/metering_success_rate\""
                  aggregation = {
                    alignmentPeriod = "300s"
                    perSeriesAligner = "ALIGN_MEAN"
                  }
                }
              }
            }]
          }
        }
      ]
    }
  })
}

# Alert policy for metering failures
resource "google_monitoring_alert_policy" "metering_failures" {
  display_name = "YAWL Metering Failure Alert"
  project      = var.project_id
  combiner     = "OR"

  conditions {
    display_name = "High metering failure rate"

    condition_threshold {
      filter          = "resource.type=\"k8s_container\" AND metric.type=\"custom.googleapis.com/yawl/metering_errors\""
      duration        = "300s"
      comparison      = "COMPARISON_GT"
      threshold_value = 10

      aggregations {
        alignment_period   = "60s"
        per_series_aligner = "ALIGN_RATE"
      }
    }
  }

  notification_channels = [google_monitoring_notification_channel.email.id]

  alert_strategy {
    auto_close = "604800s"
  }
}

# Notification channel for alerts
resource "google_monitoring_notification_channel" "email" {
  display_name = "Billing Team Email"
  type         = "email"
  project      = var.project_id

  labels = {
    email_address = "billing@yawlfoundation.org"
  }
}

# Cloud Run service for metering API
resource "google_cloud_run_service" "metering_api" {
  name     = "yawl-metering-api"
  location = var.region
  project  = var.project_id

  template {
    spec {
      service_account_name = google_service_account.metering_service.email
      containers {
        image = "gcr.io/${var.project_id}/yawl-metering:latest"
        ports {
          container_port = 8080
        }
        env {
          name  = "PARTICIPANT_ID"
          value = var.marketplace_participant_id
        }
        env {
          name  = "GOOGLE_CLOUD_PROJECT"
          value = var.project_id
        }
        resources {
          limits = {
            cpu    = "1000m"
            memory = "512Mi"
          }
          requests = {
            cpu    = "250m"
            memory = "256Mi"
          }
        }
      }
    }
  }

  traffic {
    percent         = 100
    latest_revision = true
  }

  autogenerate_revision_name = true
}

# Cloud Run IAM policy for public access
resource "google_cloud_run_service_iam_member" "public_access" {
  location = google_cloud_run_service.metering_api.location
  project  = google_cloud_run_service.metering_api.project
  service  = google_cloud_run_service.metering_api.name
  role     = "roles/run.invoker"
  member   = "allUsers"
}

# Outputs
output "metering_api_url" {
  value = google_cloud_run_service.metering_api.status[0].url
}

output "usage_reports_bucket" {
  value = google_storage_bucket.usage_reports.url
}

output "entitlement_events_topic" {
  value = google_pubsub_topic.entitlement_events.id
}

output "billing_dashboard_url" {
  value = "https://console.cloud.google.com/monitoring/dashboards?project=${var.project_id}"
}
