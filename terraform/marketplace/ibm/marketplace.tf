#------------------------------------------------------------------------------
# YAWL IBM Cloud Marketplace Module
# Version: 1.0.0
#
# This module handles IBM Cloud Marketplace deployment for YAWL
#------------------------------------------------------------------------------

locals {
  name_prefix = "${var.project_name}-${var.environment}"
}

#------------------------------------------------------------------------------
# IBM Cloud Schematics Workspace (Terraform)
#------------------------------------------------------------------------------

resource "ibm_schematics_workspace" "yawl_marketplace" {
  name                  = "${local.name_prefix}-marketplace-ws"
  description           = "YAWL Workflow Engine deployment from IBM Cloud Marketplace"
  resource_group        = var.resource_group
  location              = "us-south"

  template_type         = "terraform"
  template_git_url      = var.template_git_url
  template_git_branch   = var.template_git_branch
  template_git_folder   = "terraform/"

  variable {
    name  = "project_name"
    value = var.project_name
  }

  variable {
    name  = "environment"
    value = var.environment
  }

  variable {
    name  = "region"
    value = var.region
  }

  variable {
    name  = "cluster_id"
    value = var.cluster_id
  }

  variable {
    name  = "plan_id"
    value = var.plan_id
  }

  variable {
    name  = "offer_id"
    value = var.offer_id
  }

  variable {
    name  = "publisher"
    value = var.publisher
  }

  tags = ["marketplace", "yawl", var.environment, var.plan_id]
}

#------------------------------------------------------------------------------
# IBM Cloud Secrets Manager for Marketplace
#------------------------------------------------------------------------------

data "ibm_resource_group" "yawl" {
  name = var.resource_group
}

resource "ibm_resource_instance" "secrets_manager" {
  name              = "${local.name_prefix}-mp-secrets"
  service           = "secrets-manager"
  plan              = "standard"
  location          = var.region
  resource_group_id = data.ibm_resource_group.yawl.id

  tags = ["marketplace", "yawl"]
}

resource "ibm_sm_secret_group" "marketplace" {
  instance_id  = ibm_resource_instance.secrets_manager.guid
  region       = var.region
  name         = "${local.name_prefix}-marketplace"
  description  = "Secrets for YAWL Marketplace deployment"
}

resource "ibm_sm_arbitrary_secret" "marketplace_config" {
  instance_id     = ibm_resource_instance.secrets_manager.guid
  region          = var.region
  name            = "${local.name_prefix}-config"
  description     = "Marketplace configuration for ${local.name_prefix}"
  secret_group_id = ibm_sm_secret_group.marketplace.secret_group_id
  payload         = jsonencode({
    cluster_id     = var.cluster_id
    plan_id        = var.plan_id
    offer_id       = var.offer_id
    publisher      = var.publisher
    deployment_time = timestamp()
  })
}

#------------------------------------------------------------------------------
# Schematics Action for Post-Deployment
#------------------------------------------------------------------------------

resource "ibm_schematics_action" "post_deploy" {
  name            = "${local.name_prefix}-post-deploy"
  description     = "Post-deployment actions for YAWL Marketplace"
  resource_group  = var.resource_group

  source {
    source_type   = "git"
    git {
      git_url     = var.action_git_url
      git_branch  = var.action_git_branch
    }
  }

  command_parameter = "deploy"

  user_state {
    state  = "draft"
    set_by = "user"
  }

  tags = ["marketplace", "yawl", "post-deploy"]
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

variable "region" {
  type = string
}

variable "resource_group" {
  type    = string
  default = "default"
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

variable "template_git_url" {
  type    = string
  default = "https://github.com/yawlfoundation/yawl-terraform"
}

variable "template_git_branch" {
  type    = string
  default = "main"
}

variable "action_git_url" {
  type    = string
  default = "https://github.com/yawlfoundation/yawl-ansible"
}

variable "action_git_branch" {
  type    = string
  default = "main"
}

#------------------------------------------------------------------------------
# Outputs
#------------------------------------------------------------------------------

output "workspace_id" {
  value = ibm_schematics_workspace.yawl_marketplace.id
}

output "workspace_name" {
  value = ibm_schematics_workspace.yawl_marketplace.name
}

output "action_id" {
  value = ibm_schematics_action.post_deploy.id
}

output "secrets_manager_id" {
  value = ibm_resource_instance.secrets_manager.guid
}

output "secret_group_id" {
  value = ibm_sm_secret_group.marketplace.secret_group_id
}
