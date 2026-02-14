terraform {
  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 3.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.0"
    }
  }
}

# Generate secure password if not provided
resource "random_password" "database" {
  length  = 32
  special = true
}

resource "azurerm_mysql_flexible_server" "main" {
  name                   = var.server_name
  location               = var.location
  resource_group_name    = var.resource_group_name
  administrator_login    = var.administrator_login
  administrator_password = var.administrator_password != "" ? var.administrator_password : random_password.database.result

  sku_name   = var.db_sku_name
  version    = var.db_version

  backup_retention_days             = var.backup_retention_days
  geo_redundant_backup_enabled      = var.geo_redundant_backup_enabled
  high_availability {
    mode = var.enable_ha ? "ZoneRedundant" : "Disabled"
  }

  delegated_subnet_id = var.subnet_id != "" ? var.subnet_id : null

  ssl_enforce_enabled = true

  tags = merge(
    var.tags,
    {
      Name        = var.server_name
      Environment = var.environment
      Project     = var.project_name
    }
  )

  depends_on = []
}

resource "azurerm_mysql_flexible_server_database" "main" {
  name                = var.database_name
  resource_group_name = var.resource_group_name
  server_name         = azurerm_mysql_flexible_server.main.name
  charset             = "utf8mb4"
  collation           = "utf8mb4_unicode_ci"
}

# Firewall rule for Azure services
resource "azurerm_mysql_flexible_server_firewall_rule" "azure_services" {
  name                = "AllowAzureServices"
  resource_group_name = var.resource_group_name
  server_name         = azurerm_mysql_flexible_server.main.name
  start_ip_address    = "0.0.0.0"
  end_ip_address      = "0.0.0.0"
}

# Private Endpoint for database
resource "azurerm_private_endpoint" "database" {
  count               = var.enable_private_endpoint ? 1 : 0
  name                = "${var.server_name}-private-endpoint"
  location            = var.location
  resource_group_name = var.resource_group_name
  subnet_id           = var.subnet_id

  private_service_connection {
    name                           = "${var.server_name}-connection"
    is_manual_connection           = false
    private_connection_resource_id = azurerm_mysql_flexible_server.main.id
    subresource_names              = ["mysqlServer"]
  }

  tags = merge(
    var.tags,
    {
      Name        = "${var.server_name}-private-endpoint"
      Environment = var.environment
      Project     = var.project_name
    }
  )
}

# Database configuration parameters
resource "azurerm_mysql_flexible_server_configuration" "max_connections" {
  name                = "max_connections"
  resource_group_name = var.resource_group_name
  server_name         = azurerm_mysql_flexible_server.main.name
  value               = "500"
}

resource "azurerm_mysql_flexible_server_configuration" "slow_query_log" {
  name                = "slow_query_log"
  resource_group_name = var.resource_group_name
  server_name         = azurerm_mysql_flexible_server.main.name
  value               = "ON"
}

resource "azurerm_mysql_flexible_server_configuration" "long_query_time" {
  name                = "long_query_time"
  resource_group_name = var.resource_group_name
  server_name         = azurerm_mysql_flexible_server.main.name
  value               = "2"
}

resource "azurerm_mysql_flexible_server_configuration" "character_set_server" {
  name                = "character_set_server"
  resource_group_name = var.resource_group_name
  server_name         = azurerm_mysql_flexible_server.main.name
  value               = "utf8mb4"
}

resource "azurerm_mysql_flexible_server_configuration" "collation_server" {
  name                = "collation_server"
  resource_group_name = var.resource_group_name
  server_name         = azurerm_mysql_flexible_server.main.name
  value               = "utf8mb4_unicode_ci"
}

# Database user for application
resource "azurerm_mysql_flexible_server_database_user" "app" {
  name                = var.app_username
  resource_group_name = var.resource_group_name
  server_name         = azurerm_mysql_flexible_server.main.name
  password            = var.app_password != "" ? var.app_password : random_password.database.result
}

# Diagnostic settings
resource "azurerm_monitor_diagnostic_setting" "database" {
  name                       = "${var.server_name}-diagnostics"
  target_resource_id         = azurerm_mysql_flexible_server.main.id
  log_analytics_workspace_id = var.log_analytics_workspace_id

  enabled_log {
    category = "MySqlSlowLogs"
  }

  enabled_log {
    category = "MySqlAuditLogs"
  }

  metric {
    category = "AllMetrics"
  }

  depends_on = [azurerm_mysql_flexible_server.main]
}

# Backup
resource "azurerm_backup_protection_mysql_flexible_server" "main" {
  count                     = var.enable_backup ? 1 : 0
  resource_group_name       = var.resource_group_name
  backup_vault_name         = azurerm_backup_vault.main[0].name
  mysql_flexible_server_id  = azurerm_mysql_flexible_server.main.id
  backup_policy_name        = azurerm_data_protection_backup_policy_mysql_flexible_server.main[0].name
}

resource "azurerm_backup_vault" "main" {
  count               = var.enable_backup ? 1 : 0
  name                = "${var.project_name}-backup-vault"
  resource_group_name = var.resource_group_name
  location            = var.location
  datastore_type      = "VaultStore"
  redundancy          = var.backup_redundancy

  tags = merge(
    var.tags,
    {
      Name        = "${var.project_name}-backup-vault"
      Environment = var.environment
      Project     = var.project_name
    }
  )
}

resource "azurerm_data_protection_backup_policy_mysql_flexible_server" "main" {
  count           = var.enable_backup ? 1 : 0
  name            = "${var.project_name}-backup-policy"
  vault_id        = azurerm_backup_vault.main[0].id
  backup_repeating_time_intervals = ["R/2024-01-01T01:00:00+00:00/P1D"]
  default_retention_rule {
    lifetime = "P30D"
  }
}
