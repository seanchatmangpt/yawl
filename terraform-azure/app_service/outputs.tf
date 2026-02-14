output "app_service_id" {
  description = "ID of the App Service"
  value       = azurerm_linux_web_app.main.id
}

output "app_service_name" {
  description = "Name of the App Service"
  value       = azurerm_linux_web_app.main.name
}

output "app_service_default_hostname" {
  description = "Default hostname of the App Service"
  value       = azurerm_linux_web_app.main.default_hostname
}

output "app_service_possible_outbound_ip_addresses" {
  description = "Possible outbound IP addresses for the App Service"
  value       = azurerm_linux_web_app.main.possible_outbound_ip_addresses
}

output "app_service_plan_id" {
  description = "ID of the App Service Plan"
  value       = azurerm_service_plan.main.id
}

output "staging_slot_id" {
  description = "ID of the staging slot"
  value       = azurerm_app_service_slot.staging.id
}

output "staging_slot_default_hostname" {
  description = "Default hostname of the staging slot"
  value       = azurerm_app_service_slot.staging.default_site_hostname
}
