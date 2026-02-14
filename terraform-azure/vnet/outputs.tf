output "vnet_id" {
  description = "ID of the virtual network"
  value       = azurerm_virtual_network.main.id
}

output "vnet_name" {
  description = "Name of the virtual network"
  value       = azurerm_virtual_network.main.name
}

output "vnet_address_space" {
  description = "Address space of the virtual network"
  value       = azurerm_virtual_network.main.address_space
}

output "app_service_subnet_id" {
  description = "ID of the app service subnet"
  value       = azurerm_subnet.main["app-service"].id
}

output "database_subnet_id" {
  description = "ID of the database subnet"
  value       = azurerm_subnet.main["database"].id
}

output "monitoring_subnet_id" {
  description = "ID of the monitoring subnet"
  value       = azurerm_subnet.main["monitoring"].id
}

output "nsg_id" {
  description = "ID of the network security group"
  value       = azurerm_network_security_group.main.id
}
