terraform {
  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 3.0"
    }
  }
}

resource "azurerm_virtual_network" "main" {
  name                = var.vnet_name
  address_space       = [var.vnet_cidr]
  location            = var.location
  resource_group_name = var.resource_group_name

  dynamic "ddos_protection_plan" {
    for_each = var.enable_ddos_protection ? [1] : []
    content {
      enabled = true
      id      = azurerm_ddos_protection_plan.main[0].id
    }
  }

  tags = merge(
    var.tags,
    {
      Name        = var.vnet_name
      Environment = var.environment
      Project     = var.project_name
    }
  )
}

resource "azurerm_ddos_protection_plan" "main" {
  count               = var.enable_ddos_protection ? 1 : 0
  name                = "${var.project_name}-ddos-plan"
  location            = var.location
  resource_group_name = var.resource_group_name

  tags = merge(
    var.tags,
    {
      Name        = "${var.project_name}-ddos-plan"
      Environment = var.environment
      Project     = var.project_name
    }
  )
}

resource "azurerm_subnet" "main" {
  for_each = var.subnet_configs

  name                 = "${var.project_name}-subnet-${each.key}"
  resource_group_name  = var.resource_group_name
  virtual_network_name = azurerm_virtual_network.main.name
  address_prefixes     = [each.value.cidr]

  enforce_private_link_endpoint_network_policies = each.value.enforce_private_link_endpoint
  enforce_private_link_service_network_policies  = each.value.enforce_private_link_service

  service_endpoints = [
    "Microsoft.Sql",
    "Microsoft.Storage"
  ]
}

resource "azurerm_network_security_group" "main" {
  name                = "${var.project_name}-nsg"
  location            = var.location
  resource_group_name = var.resource_group_name

  tags = merge(
    var.tags,
    {
      Name        = "${var.project_name}-nsg"
      Environment = var.environment
      Project     = var.project_name
    }
  )
}

# Allow HTTPS inbound
resource "azurerm_network_security_rule" "allow_https" {
  name                        = "AllowHTTPS"
  priority                    = 100
  direction                   = "Inbound"
  access                      = "Allow"
  protocol                    = "Tcp"
  source_port_range           = "*"
  destination_port_range      = "443"
  source_address_prefix       = "*"
  destination_address_prefix  = "*"
  resource_group_name         = var.resource_group_name
  network_security_group_name = azurerm_network_security_group.main.name
}

# Allow HTTP inbound
resource "azurerm_network_security_rule" "allow_http" {
  name                        = "AllowHTTP"
  priority                    = 101
  direction                   = "Inbound"
  access                      = "Allow"
  protocol                    = "Tcp"
  source_port_range           = "*"
  destination_port_range      = "80"
  source_address_prefix       = "*"
  destination_address_prefix  = "*"
  resource_group_name         = var.resource_group_name
  network_security_group_name = azurerm_network_security_group.main.name
}

# Allow inbound from subnet
resource "azurerm_network_security_rule" "allow_internal" {
  name                        = "AllowInternal"
  priority                    = 102
  direction                   = "Inbound"
  access                      = "Allow"
  protocol                    = "*"
  source_port_range           = "*"
  destination_port_range      = "*"
  source_address_prefix       = var.vnet_cidr
  destination_address_prefix  = "*"
  resource_group_name         = var.resource_group_name
  network_security_group_name = azurerm_network_security_group.main.name
}

# Associate NSG with subnets
resource "azurerm_subnet_network_security_group_association" "main" {
  for_each = azurerm_subnet.main

  subnet_id                 = each.value.id
  network_security_group_id = azurerm_network_security_group.main.id
}
