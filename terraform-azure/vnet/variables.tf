variable "resource_group_name" {
  description = "Name of the resource group"
  type        = string
}

variable "location" {
  description = "Azure region for resources"
  type        = string
}

variable "vnet_name" {
  description = "Name of the virtual network"
  type        = string
}

variable "vnet_cidr" {
  description = "CIDR block for the virtual network"
  type        = string
}

variable "subnet_configs" {
  description = "Configuration for subnets"
  type = map(object({
    cidr                          = string
    enforce_private_link_endpoint = optional(bool, false)
    enforce_private_link_service  = optional(bool, false)
  }))
}

variable "enable_ddos_protection" {
  description = "Enable DDoS protection for the virtual network"
  type        = bool
  default     = false
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
