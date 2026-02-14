#------------------------------------------------------------------------------
# YAWL Azure Marketplace Module
# Version: 1.0.0
#
# This module handles Azure Marketplace deployment for YAWL
#------------------------------------------------------------------------------

locals {
  name_prefix = "${var.project_name}-${var.environment}"
}

#------------------------------------------------------------------------------
# Azure Marketplace Managed Application
#------------------------------------------------------------------------------

resource "azurerm_managed_application" "yawl_marketplace" {
  name                        = "${local.name_prefix}-marketplace-app"
  location                    = var.location
  resource_group_name         = var.resource_group_name
  kind                        = "ServiceCatalog"
  managed_resource_group_name = "${local.name_prefix}-managed-rg"

  application_definition_id = var.application_definition_id != "" ? var.application_definition_id : null

  parameter_values = jsonencode({
    clusterName = {
      value = "${local.name_prefix}-cluster"
    }
    clusterEndpoint = {
      value = var.cluster_endpoint
    }
    environment = {
      value = var.environment
    }
    planId = {
      value = var.plan_id
    }
    offerId = {
      value = var.offer_id
    }
    publisher = {
      value = var.publisher
    }
  })
}

#------------------------------------------------------------------------------
# Azure Marketplace Plan
#------------------------------------------------------------------------------

data "azurerm_marketplace_agreement" "yawl" {
  publisher = var.publisher
  offer     = var.offer_id
  plan      = var.plan_id
}

resource "azurerm_marketplace_agreement" "yawl" {
  publisher = var.publisher
  offer     = var.offer_id
  plan      = var.plan_id

  # Only accept if not already accepted
  count = data.azurerm_marketplace_agreement.yawl.accepted ? 0 : 1
}

#------------------------------------------------------------------------------
# Key Vault for Marketplace Secrets
#------------------------------------------------------------------------------

resource "azurerm_key_vault" "marketplace" {
  name                        = "${replace(local.name_prefix, "-", "")}mpvault"
  location                    = var.location
  resource_group_name         = var.resource_group_name
  enabled_for_disk_encryption = true
  tenant_id                   = var.tenant_id
  soft_delete_retention_days  = 7
  purge_protection_enabled    = false

  sku_name = "standard"

  access_policy {
    tenant_id = var.tenant_id
    object_id = var.object_id

    secret_permissions = [
      "Get", "List", "Set", "Delete"
    ]
  }

  tags = var.common_tags
}

resource "azurerm_key_vault_secret" "marketplace_config" {
  name         = "${local.name_prefix}-marketplace-config"
  value        = jsonencode({
    cluster_endpoint = var.cluster_endpoint
    plan_id          = var.plan_id
    offer_id         = var.offer_id
    publisher        = var.publisher
    deployment_time  = timestamp()
  })
  key_vault_id = azurerm_key_vault.marketplace.id
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

variable "location" {
  type = string
}

variable "resource_group_name" {
  type = string
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

variable "application_definition_id" {
  type    = string
  default = ""
}

variable "tenant_id" {
  type = string
}

variable "object_id" {
  type = string
}

variable "common_tags" {
  type    = map(string)
  default = {}
}

#------------------------------------------------------------------------------
# Outputs
#------------------------------------------------------------------------------

output "application_name" {
  value = azurerm_managed_application.yawl_marketplace.name
}

output "application_id" {
  value = azurerm_managed_application.yawl_marketplace.id
}

output "managed_resource_group" {
  value = azurerm_managed_application.yawl_marketplace.managed_resource_group_name
}

output "key_vault_id" {
  value = azurerm_key_vault.marketplace.id
}
