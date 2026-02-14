variable "resource_group_name" {
  description = "Name of the resource group"
  type        = string
}

variable "location" {
  description = "Azure region for resources"
  type        = string
}

variable "app_service_plan_name" {
  description = "Name of the App Service Plan"
  type        = string
}

variable "app_service_name" {
  description = "Name of the App Service"
  type        = string
}

variable "app_service_plan_sku" {
  description = "SKU for the App Service Plan"
  type        = string
}

variable "app_runtime_stack" {
  description = "Runtime stack for the App Service"
  type        = string
  default     = "DOTNETCORE|6.0"
}

variable "subnet_id" {
  description = "ID of the subnet for VNet integration"
  type        = string
  default     = ""
}

variable "environment" {
  description = "Environment name"
  type        = string
}

variable "project_name" {
  description = "Project name"
  type        = string
}

variable "tags" {
  description = "Tags to apply to resources"
  type        = map(string)
  default     = {}
}

variable "log_analytics_workspace_id" {
  description = "ID of the Log Analytics Workspace for diagnostics"
  type        = string
  default     = ""
}

variable "certificate_password" {
  description = "Password for the SSL certificate"
  type        = string
  sensitive   = true
  default     = ""
}
