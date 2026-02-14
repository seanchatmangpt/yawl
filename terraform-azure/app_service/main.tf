terraform {
  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 3.0"
    }
  }
}

resource "azurerm_service_plan" "main" {
  name                = var.app_service_plan_name
  location            = var.location
  resource_group_name = var.resource_group_name

  os_type  = "Linux"
  sku_name = var.app_service_plan_sku

  tags = merge(
    var.tags,
    {
      Name        = var.app_service_plan_name
      Environment = var.environment
      Project     = var.project_name
    }
  )
}

resource "azurerm_linux_web_app" "main" {
  name                = var.app_service_name
  location            = var.location
  resource_group_name = var.resource_group_name
  service_plan_id     = azurerm_service_plan.main.id

  https_only = true

  site_config {
    minimum_tls_version             = "1.2"
    scm_minimum_tls_version         = "1.2"
    scm_use_main_ip_restriction     = false
    http2_enabled                   = true
    managed_pipeline_mode           = "Integrated"
    use_32_bit_worker_process       = false
    websockets_enabled              = true
    remote_debugging_enabled        = false
    ftps_state                      = "Disabled"
    pre_warmed_instance_count       = 0

    application_stack {
      docker_image_name = "mcr.microsoft.com/azure-app-service/staticsite:latest"
    }

    cors {
      allowed_origins = ["*"]
    }

    app_command_line = ""

    health_check_path = "/"
  }

  app_settings = {
    "WEBSITE_ENABLE_SYNC_UPDATE_SITE" = "true"
    "SCM_DO_BUILD_DURING_DEPLOYMENT"  = "false"
  }

  auth_settings_v2 {
    auth_enabled = false
  }

  logs {
    application_logs {
      file_system_level = "Information"
    }

    http_logs {
      file_system {
        retention_in_days = 7
        retention_in_mb   = 100
      }
    }

    detailed_error_messages = true
    failed_request_tracing  = true
  }

  sticky_settings {
    app_setting_names       = []
    connection_string_names = []
  }

  tags = merge(
    var.tags,
    {
      Name        = var.app_service_name
      Environment = var.environment
      Project     = var.project_name
    }
  )

  depends_on = [azurerm_service_plan.main]
}

# Virtual Network Integration
resource "azurerm_app_service_virtual_network_swift_connection" "main" {
  count              = var.subnet_id != "" ? 1 : 0
  app_service_id     = azurerm_linux_web_app.main.id
  subnet_id          = var.subnet_id
}

# Enable HTTPS certificate binding
resource "azurerm_web_app_certificate" "main" {
  count               = 0 # Disable by default, enable when custom domain is configured
  name                = "${var.app_service_name}-cert"
  resource_group_name = var.resource_group_name
  location            = var.location
  app_service_plan_id = azurerm_service_plan.main.id

  pfx_blob = filebase64("${path.root}/certificates/app-cert.pfx")
  password = var.certificate_password

  tags = merge(
    var.tags,
    {
      Name        = "${var.app_service_name}-cert"
      Environment = var.environment
      Project     = var.project_name
    }
  )
}

# Application Insights connection
resource "azurerm_monitor_diagnostic_setting" "app_service" {
  name                       = "${var.app_service_name}-diagnostics"
  target_resource_id         = azurerm_linux_web_app.main.id
  log_analytics_workspace_id = var.log_analytics_workspace_id

  enabled_log {
    category = "AppServiceHTTPLogs"

    retention_policy {
      enabled = false
    }
  }

  enabled_log {
    category = "AppServiceConsoleLogs"

    retention_policy {
      enabled = false
    }
  }

  metric {
    category = "AllMetrics"

    retention_policy {
      enabled = false
    }
  }

  depends_on = [azurerm_linux_web_app.main]
}

resource "azurerm_app_service_slot" "staging" {
  name                = "staging"
  app_service_name    = azurerm_linux_web_app.main.name
  location            = var.location
  resource_group_name = var.resource_group_name
  app_service_plan_id = azurerm_service_plan.main.id

  site_config {
    minimum_tls_version             = "1.2"
    scm_minimum_tls_version         = "1.2"
    http2_enabled                   = true
    managed_pipeline_mode           = "Integrated"
    use_32_bit_worker_process       = false
    websockets_enabled              = true
    remote_debugging_enabled        = false
    ftps_state                      = "Disabled"

    application_stack {
      docker_image_name = "mcr.microsoft.com/azure-app-service/staticsite:latest"
    }

    cors {
      allowed_origins = ["*"]
    }
  }

  app_settings = {
    "WEBSITE_ENABLE_SYNC_UPDATE_SITE" = "true"
    "SCM_DO_BUILD_DURING_DEPLOYMENT"  = "false"
    "ENVIRONMENT"                      = "staging"
  }

  tags = merge(
    var.tags,
    {
      Name        = "${var.app_service_name}-staging"
      Environment = "staging"
      Project     = var.project_name
    }
  )

  depends_on = [azurerm_linux_web_app.main]
}
