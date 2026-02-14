terraform {
  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 3.0"
    }
  }
}

resource "azurerm_log_analytics_workspace" "main" {
  name                = var.workspace_name
  location            = var.location
  resource_group_name = var.resource_group_name
  sku                 = var.workspace_sku
  retention_in_days   = var.retention_days

  internet_ingestion_enabled = true
  internet_query_enabled     = true

  tags = merge(
    var.tags,
    {
      Name        = var.workspace_name
      Environment = var.environment
      Project     = var.project_name
    }
  )
}

resource "azurerm_application_insights" "main" {
  name                = var.app_insights_name
  location            = var.location
  resource_group_name = var.resource_group_name
  application_type    = "web"
  workspace_id        = azurerm_log_analytics_workspace.main.id

  retention_in_days = var.retention_days
  sampling_percentage = var.sampling_percentage

  tags = merge(
    var.tags,
    {
      Name        = var.app_insights_name
      Environment = var.environment
      Project     = var.project_name
    }
  )
}

# Alert Rules
resource "azurerm_monitor_metric_alert" "app_service_cpu" {
  name                = "${var.app_insights_name}-high-cpu"
  resource_group_name = var.resource_group_name
  scopes              = [var.app_service_id]
  description         = "Alert when CPU usage is high"
  severity            = 2
  enabled             = true
  frequency           = "PT1M"
  window_size         = "PT5M"

  criteria {
    metric_name      = "CpuPercentage"
    metric_namespace = "Microsoft.Web/serverfarms"
    aggregation      = "Average"
    operator         = "GreaterThan"
    threshold        = 80
  }

  action {
    action_group_id = azurerm_monitor_action_group.main.id
  }
}

resource "azurerm_monitor_metric_alert" "app_service_memory" {
  name                = "${var.app_insights_name}-high-memory"
  resource_group_name = var.resource_group_name
  scopes              = [var.app_service_id]
  description         = "Alert when memory usage is high"
  severity            = 2
  enabled             = true
  frequency           = "PT1M"
  window_size         = "PT5M"

  criteria {
    metric_name      = "MemoryPercentage"
    metric_namespace = "Microsoft.Web/serverfarms"
    aggregation      = "Average"
    operator         = "GreaterThan"
    threshold        = 85
  }

  action {
    action_group_id = azurerm_monitor_action_group.main.id
  }
}

resource "azurerm_monitor_metric_alert" "app_service_response_time" {
  name                = "${var.app_insights_name}-slow-response"
  resource_group_name = var.resource_group_name
  scopes              = [var.app_service_id]
  description         = "Alert when response time is slow"
  severity            = 3
  enabled             = true
  frequency           = "PT1M"
  window_size         = "PT5M"

  criteria {
    metric_name      = "ResponseTime"
    metric_namespace = "Microsoft.Web/sites"
    aggregation      = "Average"
    operator         = "GreaterThan"
    threshold        = 2000
  }

  action {
    action_group_id = azurerm_monitor_action_group.main.id
  }
}

resource "azurerm_monitor_metric_alert" "app_service_http_errors" {
  name                = "${var.app_insights_name}-http-errors"
  resource_group_name = var.resource_group_name
  scopes              = [var.app_service_id]
  description         = "Alert on HTTP 5xx errors"
  severity            = 2
  enabled             = true
  frequency           = "PT1M"
  window_size         = "PT5M"

  criteria {
    metric_name      = "Http5xx"
    metric_namespace = "Microsoft.Web/sites"
    aggregation      = "Total"
    operator         = "GreaterThan"
    threshold        = 10
  }

  action {
    action_group_id = azurerm_monitor_action_group.main.id
  }
}

# Action Group for alerts
resource "azurerm_monitor_action_group" "main" {
  name                = "${var.project_name}-action-group"
  resource_group_name = var.resource_group_name
  short_name          = substr("${var.project_name}-alerts", 0, 12)

  enabled = true

  tags = merge(
    var.tags,
    {
      Name        = "${var.project_name}-action-group"
      Environment = var.environment
      Project     = var.project_name
    }
  )
}

# Azure Activity Log Alert
resource "azurerm_monitor_activity_log_alert" "resource_health" {
  name                = "${var.project_name}-resource-health"
  resource_group_name = var.resource_group_name
  scopes              = ["/subscriptions/${data.azurerm_client_config.current.subscription_id}"]
  description         = "Alert on resource health issues"
  enabled             = true

  criteria {
    category = "ServiceHealth"
    status   = "Active"
  }

  action {
    action_group_id = azurerm_monitor_action_group.main.id
  }

  depends_on = [azurerm_monitor_action_group.main]
}

# Log Analytics Query Packs
resource "azurerm_log_analytics_query_pack" "main" {
  name                = "${var.project_name}-query-pack"
  resource_group_name = var.resource_group_name
  location            = var.location

  tags = merge(
    var.tags,
    {
      Name        = "${var.project_name}-query-pack"
      Environment = var.environment
      Project     = var.project_name
    }
  )
}

# Saved queries for common analysis
resource "azurerm_log_analytics_query_pack_query" "failed_requests" {
  query_pack_id = azurerm_log_analytics_query_pack.main.id
  body          = "requests | where success == false | summarize count() by name"
  display_name  = "Failed Requests"
  category      = "Application"
}

resource "azurerm_log_analytics_query_pack_query" "slow_queries" {
  query_pack_id = azurerm_log_analytics_query_pack.main.id
  body          = "requests | where duration > 3000 | summarize count() by name, duration"
  display_name  = "Slow Requests"
  category      = "Application"
}

resource "azurerm_log_analytics_query_pack_query" "database_connections" {
  query_pack_id = azurerm_log_analytics_query_pack.main.id
  body          = "AzureDiagnostics | where ResourceType == 'SERVERS' | summarize count() by bin(TimeGenerated, 1m)"
  display_name  = "Database Connections"
  category      = "Database"
}

# Application Insights Availability Tests
resource "azurerm_application_insights_web_test" "main" {
  count               = var.enable_availability_tests ? 1 : 0
  name                = "${var.app_insights_name}-availability-test"
  location            = var.location
  resource_group_name = var.resource_group_name
  application_insights_id = azurerm_application_insights.main.id
  kind                = "ping"
  frequency           = 300
  timeout             = 60
  enabled             = true

  geo_locations = var.geo_test_locations

  request_url = "https://${var.test_url}"
  description = "Availability test for YAWL application"

  depends_on = [azurerm_application_insights.main]
}

# Application Insights Smart Detection Rule
resource "azurerm_application_insights_smart_detection_rule" "main" {
  count                       = var.enable_smart_detection ? 1 : 0
  name                        = "Potential memory leak detected"
  application_insights_id     = azurerm_application_insights.main.id
  enabled                     = true
  send_notifications_to_mail_administrators = true
}

# Data source for current client config
data "azurerm_client_config" "current" {}
