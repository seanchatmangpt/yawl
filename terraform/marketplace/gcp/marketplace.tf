#------------------------------------------------------------------------------
# YAWL GCP Marketplace Module
# Version: 1.0.0
#
# This module handles GCP Marketplace deployment for YAWL
#------------------------------------------------------------------------------

locals {
  name_prefix = "${var.project_name}-${var.environment}"
}

#------------------------------------------------------------------------------
# GCP Marketplace Solution
#------------------------------------------------------------------------------

resource "google_deployment_manager_deployment" "yawl_marketplace" {
  name        = "${local.name_prefix}-marketplace"
  project     = var.gcp_project_id
  description = "YAWL Workflow Engine deployment from GCP Marketplace"

  labels = {
    environment = var.environment
    managed-by  = "marketplace"
  }

  target {
    config {
      content = templatefile("${path.module}/templates/marketplace_config.yaml.tpl", {
        project_name = var.project_name
        environment  = var.environment
        region       = var.region
        zone         = var.zone
        plan_id      = var.plan_id
        offer_id     = var.offer_id
        publisher    = var.publisher
      })
    }
  }
}

#------------------------------------------------------------------------------
# Service Account for Marketplace
#------------------------------------------------------------------------------

resource "google_service_account" "marketplace" {
  account_id   = "${local.name_prefix}-marketplace"
  display_name = "YAWL Marketplace Service Account"
  project      = var.gcp_project_id
}

resource "google_project_iam_member" "marketplace_roles" {
  for_each = toset([
    "roles/container.admin",
    "roles/cloudsql.admin",
    "roles/storage.admin",
    "roles/iam.serviceAccountUser",
    "roles/compute.networkAdmin"
  ])

  project = var.gcp_project_id
  role    = each.value
  member  = "serviceAccount:${google_service_account.marketplace.email}"
}

#------------------------------------------------------------------------------
# Marketplace Configuration Secret
#------------------------------------------------------------------------------

resource "google_secret_manager_secret" "marketplace_config" {
  secret_id = "${local.name_prefix}-marketplace-config"
  project   = var.gcp_project_id

  replication {
    auto {}
  }
}

resource "google_secret_manager_secret_version" "marketplace_config" {
  secret      = google_secret_manager_secret.marketplace_config.id
  secret_data = jsonencode({
    cluster_endpoint = var.cluster_endpoint
    plan_id          = var.plan_id
    offer_id         = var.offer_id
    publisher        = var.publisher
    deployment_time  = timestamp()
  })
}

#------------------------------------------------------------------------------
# Variables
#------------------------------------------------------------------------------

variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "gcp_project_id" {
  type = string
}

variable "region" {
  type    = string
  default = "us-central1"
}

variable "zone" {
  type    = string
  default = "us-central1-a"
}

variable "cluster_endpoint" {
  type = string
}

variable "plan_id" {
  type    = string
  default = "standard"
}

variable "offer_id" {
  type    = string
  default = "yawl-workflow-engine"
}

variable "publisher" {
  type    = string
  default = "yawlfoundation"
}

#------------------------------------------------------------------------------
# Outputs
#------------------------------------------------------------------------------

output "deployment_name" {
  value = google_deployment_manager_deployment.yawl_marketplace.name
}

output "deployment_id" {
  value = google_deployment_manager_deployment.yawl_marketplace.id
}

output "service_account_email" {
  value = google_service_account.marketplace.email
}

output "marketplace_config_secret" {
  value = google_secret_manager_secret.marketplace_config.secret_id
}
