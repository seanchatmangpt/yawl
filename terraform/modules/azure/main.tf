#------------------------------------------------------------------------------
# YAWL Azure Module - AKS, PostgreSQL, Blob Storage
# Version: 1.0.0
#------------------------------------------------------------------------------

locals {
  name_prefix = "${var.project_name}-${var.environment}"
}

#------------------------------------------------------------------------------
# Resource Group
#------------------------------------------------------------------------------

resource "azurerm_resource_group" "yawl" {
  count     = var.resource_group_name == "" ? 1 : 0
  name     = "${local.name_prefix}-rg"
  location = var.location

  tags = var.common_tags
}

#------------------------------------------------------------------------------
# Azure PostgreSQL Flexible Server
#------------------------------------------------------------------------------

resource "random_password" "db_password" {
  length  = 32
  special = false
}

resource "azurerm_postgresql_flexible_server" "yawl" {
  name                   = "${local.name_prefix}-psql"
  resource_group_name    = coalesce(var.resource_group_name, azurerm_resource_group.yawl[0].name)
  location               = var.location
  version                = var.database_version
  administrator_login    = var.database_username
  administrator_password = random_password.db_password.result
  sku_name               = var.database_sku
  storage_mb             = var.database_storage_gb * 1024
  backup_retention_days  = var.database_backup_days
  geo_redundant_backup_enabled = var.database_geo_redundant

  high_availability {
    mode                      = var.database_geo_redundant ? "ZoneRedundant" : "Disabled"
    standby_availability_zone = var.database_geo_redundant ? "2" : null
  }

  maintenance_window {
    day_of_week  = 0
    start_hour   = 3
    start_minute = 0
  }

  dynamic "identity" {
    for_each = var.enable_managed_identity ? [1] : []
    content {
      type = "SystemAssigned"
    }
  }

  tags = var.common_tags
}

resource "azurerm_postgresql_flexible_server_database" "yawl" {
  name      = var.project_name
  server_id = azurerm_postgresql_flexible_server.yawl.id

  lifecycle {
    prevent_destroy = false
  }
}

resource "azurerm_postgresql_flexible_server_configuration" "yawl" {
  name      = "log_checkpoints"
  server_id = azurerm_postgresql_flexible_server.yawl.id
  value     = "on"
}

resource "azurerm_postgresql_flexible_server_configuration" "log_connections" {
  name      = "log_connections"
  server_id = azurerm_postgresql_flexible_server.yawl.id
  value     = "on"
}

resource "azurerm_postgresql_flexible_server_configuration" "log_disconnections" {
  name      = "log_disconnections"
  server_id = azurerm_postgresql_flexible_server.yawl.id
  value     = "on"
}

resource "azurerm_postgresql_flexible_server_active_directory_administrator" "yawl" {
  count = var.enable_aad_admin ? 1 : 0

  server_name         = azurerm_postgresql_flexible_server.yawl.name
  resource_group_name = coalesce(var.resource_group_name, azurerm_resource_group.yawl[0].name)
  login               = var.aad_admin_login
  object_id           = var.aad_admin_object_id
  tenant_id           = var.aad_tenant_id
}

#------------------------------------------------------------------------------
# Azure Kubernetes Service (AKS)
#------------------------------------------------------------------------------

resource "azurerm_kubernetes_cluster" "yawl" {
  name                = "${local.name_prefix}-aks"
  location            = var.location
  resource_group_name = coalesce(var.resource_group_name, azurerm_resource_group.yawl[0].name)
  dns_prefix          = "${local.name_prefix}-aks"
  kubernetes_version  = var.kubernetes_version
  sku_tier            = var.sku_tier

  default_node_pool {
    name                = "default"
    node_count          = var.node_count_min
    vm_size             = var.node_vm_size
    os_disk_size_gb     = var.node_disk_size_gb
    os_disk_type        = "Managed"
    vnet_subnet_id      = var.private_subnet_ids[0]
    type                = "VirtualMachineScaleSets"
    enable_auto_scaling = var.enable_autoscaling
    min_count           = var.enable_autoscaling ? var.node_count_min : null
    max_count           = var.enable_autoscaling ? var.node_count_max : null

    node_labels = merge({
      environment = var.environment
    }, var.node_pool_labels)

    dynamic "node_taints" {
      for_each = var.node_pool_taints
      content {
        value = "${node_taints.value.key}=${node_taints.value.value}:${node_taints.value.effect}"
      }
    }

    tags = var.common_tags
  }

  identity {
    type = "SystemAssigned"
  }

  network_profile {
    network_plugin     = "azure"
    network_policy     = "calico"
    load_balancer_sku  = "standard"
    outbound_type      = "loadBalancer"
    service_cidr       = "10.100.0.0/16"
    dns_service_ip     = "10.100.0.10"
  }

  oms_agent {
    log_analytics_workspace_id = var.enable_monitoring ? azurerm_log_analytics_workspace.yawl[0].id : null
  }

  azure_active_directory_role_based_access_control {
    managed            = true
    azure_rbac_enabled = true
  }

  key_vault_secrets_provider {
    secret_rotation_enabled  = true
    secret_rotation_interval = "2m"
  }

  dynamic "http_proxy_config" {
    for_each = var.http_proxy_config != null ? [1] : []
    content {
      http_proxy  = var.http_proxy_config.http_proxy
      https_proxy = var.http_proxy_config.https_proxy
      no_proxy    = var.http_proxy_config.no_proxy
    }
  }

  maintenance_window {
    allowed {
      day   = "Sunday"
      hours = [3, 4, 5]
    }
  }

  auto_scaler_profile {
    balance_similar_node_groups      = true
    expander                         = "random"
    max_graceful_termination_sec     = 600
    max_node_provisioning_time       = "15m"
    max_unready_nodes                = 2
    new_pod_scale_up_delay           = "10s"
    scale_down_delay_after_add       = "10m"
    scale_down_delay_after_delete    = "10s"
    scale_down_delay_after_failure   = "3m"
    scan_interval                    = "10s"
    scale_down_unneeded              = "10m"
    scale_down_unready               = "20m"
    scale_down_utilization_threshold = "0.5"
  }

  tags = var.common_tags

  lifecycle {
    ignore_changes = [
      default_node_pool[0].node_count
    ]
  }
}

resource "azurerm_kubernetes_cluster_node_pool" "workload" {
  count = var.enable_workload_node_pool ? 1 : 0

  name                  = "workload"
  kubernetes_cluster_id = azurerm_kubernetes_cluster.yawl.id
  vm_size               = var.workload_node_vm_size
  vnet_subnet_id        = var.private_subnet_ids[0]
  os_disk_size_gb       = var.node_disk_size_gb
  os_disk_type          = "Managed"
  enable_auto_scaling   = true
  min_count             = var.workload_node_count_min
  max_count             = var.workload_node_count_max
  mode                  = "User"

  node_labels = {
    workload = "true"
  }

  tags = var.common_tags
}

#------------------------------------------------------------------------------
# Azure Storage Account and Blob Containers
#------------------------------------------------------------------------------

resource "azurerm_storage_account" "yawl" {
  name                     = replace("${local.name_prefix}storage", "-", "")
  resource_group_name      = coalesce(var.resource_group_name, azurerm_resource_group.yawl[0].name)
  location                 = var.location
  account_tier             = var.storage_account_tier
  account_replication_type = var.storage_replication

  blob_properties {
    versioning_enabled = var.storage_versioning

    delete_retention_policy {
      days = 30
    }

    container_delete_retention_policy {
      days = 30
    }

    cors_rule {
      allowed_headers    = ["*"]
      allowed_methods    = ["GET", "HEAD", "PUT", "POST", "DELETE"]
      allowed_origins    = ["*"]
      exposed_headers    = ["*"]
      max_age_in_seconds = 3600
    }
  }

  network_rules {
    default_action             = "Deny"
    ip_rules                   = var.storage_allowed_ips
    virtual_network_subnet_ids = var.private_subnet_ids
    bypass                     = ["AzureServices"]
  }

  dynamic "identity" {
    for_each = var.enable_managed_identity ? [1] : []
    content {
      type = "SystemAssigned"
    }
  }

  tags = var.common_tags
}

resource "azurerm_storage_container" "yawl" {
  for_each = toset(var.storage_container_names)

  name                  = each.value
  storage_account_name  = azurerm_storage_account.yawl.name
  container_access_type = "private"
}

resource "azurerm_storage_management_policy" "lifecycle" {
  storage_account_id = azurerm_storage_account.yawl.id

  rule {
    name    = "lifecycle-rule"
    enabled = true

    filters {
      prefix_match = var.storage_container_names
      blob_types   = ["blockBlob"]
    }

    actions {
      base_blob {
        tier_to_cool_after_days_since_modification_greater_than    = var.storage_lifecycle_days
        tier_to_archive_after_days_since_modification_greater_than = var.storage_archive_days
        delete_after_days_since_modification_greater_than          = var.storage_archive_days + 365
      }

      snapshot {
        delete_after_days_since_creation_greater_than = 90
      }

      version {
        delete_after_days_since_creation = 90
      }
    }
  }
}

#------------------------------------------------------------------------------
# Log Analytics Workspace (for monitoring)
#------------------------------------------------------------------------------

resource "azurerm_log_analytics_workspace" "yawl" {
  count = var.enable_monitoring ? 1 : 0

  name                = "${local.name_prefix}-logs"
  location            = var.location
  resource_group_name = coalesce(var.resource_group_name, azurerm_resource_group.yawl[0].name)
  sku                 = "PerGB2018"
  retention_in_days   = var.log_retention_days

  tags = var.common_tags
}

#------------------------------------------------------------------------------
# Outputs
#------------------------------------------------------------------------------

output "cluster_name" {
  value = azurerm_kubernetes_cluster.yawl.name
}

output "cluster_fqdn" {
  value = azurerm_kubernetes_cluster.yawl.fqdn
}

output "kube_config_raw" {
  value     = azurerm_kubernetes_cluster.yawl.kube_config_raw
  sensitive = true
}

output "postgres_fqdn" {
  value = azurerm_postgresql_flexible_server.yawl.fqdn
}

output "storage_blob_endpoint" {
  value = azurerm_storage_account.yawl.primary_blob_endpoint
}

output "container_urls" {
  value = { for k, v in azurerm_storage_container.yawl : k => "${azurerm_storage_account.yawl.primary_blob_endpoint}${k}" }
}
