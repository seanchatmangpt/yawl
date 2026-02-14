variable "resource_group_name" {
  description = "Name of the resource group"
  type        = string
}

variable "location" {
  description = "Azure region for resources"
  type        = string
}

variable "workspace_name" {
  description = "Name of the Log Analytics Workspace"
  type        = string
}

variable "workspace_sku" {
  description = "SKU for Log Analytics Workspace"
  type        = string
  default     = "PerGB2018"
  validation {
    condition     = contains(["Free", "PerGB2018", "CapacityReservation"], var.workspace_sku)
    error_message = "SKU must be Free, PerGB2018, or CapacityReservation."
  }
}

variable "app_insights_name" {
  description = "Name of the Application Insights instance"
  type        = string
}

variable "retention_days" {
  description = "Retention period in days for logs"
  type        = number
  default     = 30
  validation {
    condition     = var.retention_days > 0 && var.retention_days <= 730
    error_message = "Retention days must be between 1 and 730."
  }
}

variable "sampling_percentage" {
  description = "Sampling percentage for Application Insights"
  type        = number
  default     = 100
  validation {
    condition     = var.sampling_percentage > 0 && var.sampling_percentage <= 100
    error_message = "Sampling percentage must be between 1 and 100."
  }
}

variable "app_service_id" {
  description = "ID of the App Service to monitor"
  type        = string
}

variable "enable_availability_tests" {
  description = "Enable availability tests for the application"
  type        = bool
  default     = false
}

variable "test_url" {
  description = "URL to test for availability"
  type        = string
  default     = ""
}

variable "geo_test_locations" {
  description = "Geographic locations for availability tests"
  type        = list(string)
  default     = ["us-va-ash-azr", "emea-nl-ams-azr"]
}

variable "enable_smart_detection" {
  description = "Enable smart detection rules"
  type        = bool
  default     = true
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
