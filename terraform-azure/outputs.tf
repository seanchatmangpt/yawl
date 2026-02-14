output "resource_group_id" {
  description = "ID of the resource group"
  value       = module.resource_group.resource_group_id
}

output "resource_group_name" {
  description = "Name of the resource group"
  value       = module.resource_group.resource_group_name
}

output "vnet_id" {
  description = "ID of the virtual network"
  value       = module.vnet.vnet_id
}

output "vnet_name" {
  description = "Name of the virtual network"
  value       = module.vnet.vnet_name
}

output "app_service_subnet_id" {
  description = "ID of the app service subnet"
  value       = module.vnet.app_service_subnet_id
}

output "database_subnet_id" {
  description = "ID of the database subnet"
  value       = module.vnet.database_subnet_id
}

output "app_service_id" {
  description = "ID of the App Service"
  value       = module.app_service.app_service_id
}

output "app_service_name" {
  description = "Name of the App Service"
  value       = module.app_service.app_service_name
}

output "app_service_default_hostname" {
  description = "Default hostname of the App Service"
  value       = module.app_service.app_service_default_hostname
}

output "database_server_fqdn" {
  description = "FQDN of the database server"
  value       = module.database.server_fqdn
}

output "database_name" {
  description = "Name of the database"
  value       = module.database.database_name
}

output "log_analytics_workspace_id" {
  description = "ID of the Log Analytics Workspace"
  value       = module.monitoring.workspace_id
}

output "app_insights_instrumentation_key" {
  description = "Instrumentation key for Application Insights"
  value       = module.monitoring.instrumentation_key
  sensitive   = true
}

output "app_insights_connection_string" {
  description = "Connection string for Application Insights"
  value       = module.monitoring.connection_string
  sensitive   = true
}
