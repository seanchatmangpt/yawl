#------------------------------------------------------------------------------
# YAWL Oracle Cloud Marketplace Module
# Version: 1.0.0
#
# This module handles Oracle Cloud Marketplace deployment for YAWL
#------------------------------------------------------------------------------

locals {
  name_prefix = "${var.project_name}-${var.environment}"
}

#------------------------------------------------------------------------------
# Oracle Cloud Marketplace Stack
#------------------------------------------------------------------------------

resource "oci_resourcemanager_stack" "yawl_marketplace" {
  compartment_id = var.compartment_id
  display_name   = "${local.name_prefix}-marketplace-stack"
  description    = "YAWL Workflow Engine deployment from Oracle Cloud Marketplace"

  config_source {
    config_source_type = "OBJECT_STORAGE_TF_CONFIG_SOURCE"
    bucket_name       = var.config_bucket_name
    namespace         = var.config_bucket_namespace
    region            = var.region
    working_directory = ""
  }

  variables = {
    compartment_id    = var.compartment_id
    cluster_name      = "${local.name_prefix}-cluster"
    cluster_id        = var.cluster_id
    environment       = var.environment
    plan_id           = var.plan_id
    offer_id          = var.offer_id
    publisher         = var.publisher
  }

  terraform_version = "1.6.0"

  freeform_tags = merge(var.common_tags, {
    Marketplace = "true"
    PlanId      = var.plan_id
    OfferId     = var.offer_id
    Publisher   = var.publisher
  })
}

resource "oci_resourcemanager_job" "apply" {
  stack_id   = oci_resourcemanager_stack.yawl_marketplace.id
  operation  = "APPLY"

  apply_job_plan_resolution {
    is_auto_approved = true
  }

  freeform_tags = var.common_tags
}

#------------------------------------------------------------------------------
# Dynamic Group for Marketplace
#------------------------------------------------------------------------------

resource "oci_identity_dynamic_group" "marketplace" {
  compartment_id = var.tenancy_id
  description    = "Dynamic group for YAWL Marketplace"
  display_name   = "${local.name_prefix}-marketplace-dg"
  matching_rule  = "ALL {resource.type = 'resource-manager-job', resource.compartment.id = '${var.compartment_id}'}"

  freeform_tags = var.common_tags
}

#------------------------------------------------------------------------------
# Policy for Marketplace
#------------------------------------------------------------------------------

resource "oci_identity_policy" "marketplace" {
  compartment_id = var.compartment_id
  description    = "Policy for YAWL Marketplace deployment"
  display_name   = "${local.name_prefix}-marketplace-policy"
  statements     = [
    "Allow dynamic-group ${oci_identity_dynamic_group.marketplace.name} to manage all-resources in compartment id ${var.compartment_id}",
    "Allow dynamic-group ${oci_identity_dynamic_group.marketplace.name} to use tag-namespaces in tenancy"
  ]

  freeform_tags = var.common_tags
}

#------------------------------------------------------------------------------
# Object Storage for Marketplace Configuration
#------------------------------------------------------------------------------

resource "oci_objectstorage_bucket" "marketplace_config" {
  compartment_id = var.compartment_id
  name           = "${local.name_prefix}-marketplace-config"
  namespace      = var.config_bucket_namespace

  access_type  = "NoPublicAccess"
  storage_tier = "Standard"

  freeform_tags = var.common_tags
}

resource "oci_objectstorage_object" "marketplace_config" {
  bucket    = oci_objectstorage_bucket.marketplace_config.name
  namespace = var.config_bucket_namespace
  object    = "marketplace-config.json"
  content   = jsonencode({
    cluster_id     = var.cluster_id
    plan_id        = var.plan_id
    offer_id       = var.offer_id
    publisher      = var.publisher
    deployment_time = timestamp()
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

variable "compartment_id" {
  type = string
}

variable "tenancy_id" {
  type = string
}

variable "region" {
  type = string
}

variable "cluster_id" {
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

variable "config_bucket_name" {
  type    = string
  default = "yawl-marketplace-config"
}

variable "config_bucket_namespace" {
  type = string
}

variable "common_tags" {
  type    = map(string)
  default = {}
}

#------------------------------------------------------------------------------
# Outputs
#------------------------------------------------------------------------------

output "stack_id" {
  value = oci_resourcemanager_stack.yawl_marketplace.id
}

output "stack_name" {
  value = oci_resourcemanager_stack.yawl_marketplace.display_name
}

output "job_id" {
  value = oci_resourcemanager_job.apply.id
}

output "dynamic_group_name" {
  value = oci_identity_dynamic_group.marketplace.name
}

output "policy_name" {
  value = oci_identity_policy.marketplace.display_name
}
