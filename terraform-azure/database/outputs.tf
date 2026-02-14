output "server_id" {
  description = "ID of the database server"
  value       = azurerm_mysql_flexible_server.main.id
}

output "server_fqdn" {
  description = "FQDN of the database server"
  value       = azurerm_mysql_flexible_server.main.fqdn
}

output "database_name" {
  description = "Name of the database"
  value       = azurerm_mysql_flexible_server_database.main.name
}

output "administrator_login" {
  description = "Administrator login"
  value       = azurerm_mysql_flexible_server.main.administrator_login
  sensitive   = true
}

output "private_endpoint_id" {
  description = "ID of the private endpoint"
  value       = try(azurerm_private_endpoint.database[0].id, null)
}

output "private_endpoint_ip_address" {
  description = "Private IP address of the endpoint"
  value       = try(azurerm_private_endpoint.database[0].private_service_connection[0].private_ip_address, null)
}

output "connection_string_admin" {
  description = "Connection string for administrator"
  value       = "Server=${azurerm_mysql_flexible_server.main.fqdn};User Id=${azurerm_mysql_flexible_server.main.administrator_login};Database=${azurerm_mysql_flexible_server_database.main.name};"
  sensitive   = true
}

output "backup_vault_id" {
  description = "ID of the backup vault"
  value       = try(azurerm_backup_vault.main[0].id, null)
}
