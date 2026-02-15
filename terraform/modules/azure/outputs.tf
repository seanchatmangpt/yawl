#------------------------------------------------------------------------------
# YAWL Azure Module Outputs
# Version: 1.0.0
#------------------------------------------------------------------------------

output "cluster_name" {
  description = "AKS cluster name"
  value       = azurerm_kubernetes_cluster.yawl.name
}

output "cluster_fqdn" {
  description = "AKS cluster FQDN"
  value       = azurerm_kubernetes_cluster.yawl.fqdn
}

output "kube_config_raw" {
  description = "Raw kubeconfig for AKS"
  value       = azurerm_kubernetes_cluster.yawl.kube_config_raw
  sensitive   = true
}

output "get_credentials_command" {
  description = "Command to get AKS credentials"
  value       = "az aks get-credentials --resource-group ${coalesce(var.resource_group_name, azurerm_resource_group.yawl[0].name)} --name ${azurerm_kubernetes_cluster.yawl.name}"
}

output "postgres_fqdn" {
  description = "Azure PostgreSQL FQDN"
  value       = azurerm_postgresql_flexible_server.yawl.fqdn
}

output "postgres_name" {
  description = "Azure PostgreSQL server name"
  value       = azurerm_postgresql_flexible_server.yawl.name
}

output "postgres_database_name" {
  description = "PostgreSQL database name"
  value       = azurerm_postgresql_flexible_server_database.yawl.name
}

output "postgres_username" {
  description = "PostgreSQL administrator username"
  value       = var.database_username
}

output "postgres_password" {
  description = "PostgreSQL administrator password"
  value       = random_password.db_password.result
  sensitive   = true
}

output "storage_account_name" {
  description = "Storage account name"
  value       = azurerm_storage_account.yawl.name
}

output "storage_blob_endpoint" {
  description = "Storage blob endpoint"
  value       = azurerm_storage_account.yawl.primary_blob_endpoint
}

output "container_names" {
  description = "Storage container names"
  value       = { for k, v in azurerm_storage_container.yawl : k => v.name }
}

output "container_urls" {
  description = "Storage container URLs"
  value       = { for k, v in azurerm_storage_container.yawl : k => "${azurerm_storage_account.yawl.primary_blob_endpoint}${k}" }
}

output "node_resource_group" {
  description = "AKS node resource group"
  value       = azurerm_kubernetes_cluster.yawl.node_resource_group
}

output "kubelet_identity_object_id" {
  description = "AKS kubelet identity object ID"
  value       = azurerm_kubernetes_cluster.yawl.kubelet_identity[0].object_id
}

output "log_analytics_workspace_id" {
  description = "Log Analytics workspace ID"
  value       = var.enable_monitoring ? azurerm_log_analytics_workspace.yawl[0].id : null
}

output "yawl_endpoint" {
  description = "YAWL service endpoint"
  value       = "https://${azurerm_kubernetes_cluster.yawl.fqdn}"
}

output "yawl_ui_endpoint" {
  description = "YAWL UI endpoint"
  value       = "https://${azurerm_kubernetes_cluster.yawl.fqdn}/yawl"
}

output "lb_fqdn" {
  description = "Load balancer FQDN"
  value       = azurerm_kubernetes_cluster.yawl.fqdn
}
