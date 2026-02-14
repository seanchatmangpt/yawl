#------------------------------------------------------------------------------
# YAWL Shared Security Module
# Multi-cloud security groups, firewalls, and access controls
# Version: 1.0.0
#------------------------------------------------------------------------------

locals {
  name_prefix = "${var.project_name}-${var.environment}"
}

#------------------------------------------------------------------------------
# AWS Security Groups
#------------------------------------------------------------------------------

resource "aws_security_group" "yawl_default" {
  count       = var.cloud_provider == "aws" ? 1 : 0
  name        = "${local.name_prefix}-default-sg"
  description = "Default security group for YAWL"
  vpc_id      = var.vpc_id

  ingress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    self        = true
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-default-sg"
  })
}

resource "aws_security_group" "yawl_web" {
  count       = var.cloud_provider == "aws" ? 1 : 0
  name        = "${local.name_prefix}-web-sg"
  description = "Web tier security group for YAWL"
  vpc_id      = var.vpc_id

  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = var.allowed_cidr_blocks
  }

  ingress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = var.allowed_cidr_blocks
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-web-sg"
  })
}

resource "aws_security_group" "yawl_api" {
  count       = var.cloud_provider == "aws" ? 1 : 0
  name        = "${local.name_prefix}-api-sg"
  description = "API tier security group for YAWL"
  vpc_id      = var.vpc_id

  ingress {
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.yawl_web[0].id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-api-sg"
  })
}

resource "aws_security_group" "yawl_database" {
  count       = var.cloud_provider == "aws" ? 1 : 0
  name        = "${local.name_prefix}-db-sg"
  description = "Database tier security group for YAWL"
  vpc_id      = var.vpc_id

  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.yawl_api[0].id, aws_security_group.yawl_default[0].id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-db-sg"
  })
}

#------------------------------------------------------------------------------
# GCP Firewall Rules
#------------------------------------------------------------------------------

resource "google_compute_firewall" "allow_http_https" {
  count   = var.cloud_provider == "gcp" ? 1 : 0
  name    = "${local.name_prefix}-allow-http-https"
  project = var.gcp_project_id
  network = var.vpc_id

  allow {
    protocol = "tcp"
    ports    = ["80", "443"]
  }

  source_ranges = length(var.allowed_cidr_blocks) > 0 ? var.allowed_cidr_blocks : ["0.0.0.0/0"]
  target_tags   = ["web"]
}

resource "google_compute_firewall" "allow_api" {
  count   = var.cloud_provider == "gcp" ? 1 : 0
  name    = "${local.name_prefix}-allow-api"
  project = var.gcp_project_id
  network = var.vpc_id

  allow {
    protocol = "tcp"
    ports    = ["8080"]
  }

  source_tags = ["web"]
  target_tags = ["api"]
}

resource "google_compute_firewall" "allow_database" {
  count   = var.cloud_provider == "gcp" ? 1 : 0
  name    = "${local.name_prefix}-allow-database"
  project = var.gcp_project_id
  network = var.vpc_id

  allow {
    protocol = "tcp"
    ports    = ["5432"]
  }

  source_tags = ["api", "worker"]
  target_tags = ["database"]
}

resource "google_compute_firewall" "allow_ssh" {
  count   = var.cloud_provider == "gcp" ? 1 : 0
  name    = "${local.name_prefix}-allow-ssh"
  project = var.gcp_project_id
  network = var.vpc_id

  allow {
    protocol = "tcp"
    ports    = ["22"]
  }

  source_ranges = length(var.allowed_cidr_blocks) > 0 ? var.allowed_cidr_blocks : ["0.0.0.0/0"]
}

resource "google_compute_firewall" "allow_health_checks" {
  count   = var.cloud_provider == "gcp" ? 1 : 0
  name    = "${local.name_prefix}-allow-health-checks"
  project = var.gcp_project_id
  network = var.vpc_id

  allow {
    protocol = "tcp"
    ports    = ["80", "443", "8080"]
  }

  source_ranges = ["35.191.0.0/16", "130.211.0.0/22"]
}

#------------------------------------------------------------------------------
# Azure Network Security Groups
#------------------------------------------------------------------------------

resource "azurerm_network_security_group" "yawl" {
  count               = var.cloud_provider == "azure" ? 1 : 0
  name                = "${local.name_prefix}-nsg"
  location            = var.azure_location
  resource_group_name = var.azure_resource_group_name

  tags = var.common_tags
}

resource "azurerm_network_security_rule" "allow_http" {
  count                       = var.cloud_provider == "azure" ? 1 : 0
  name                        = "AllowHTTP"
  priority                    = 100
  direction                   = "Inbound"
  access                      = "Allow"
  protocol                    = "Tcp"
  source_port_range           = "*"
  destination_port_range      = "80"
  source_address_prefixes     = length(var.allowed_cidr_blocks) > 0 ? var.allowed_cidr_blocks : ["*"]
  destination_address_prefix  = "*"
  network_security_group_name = azurerm_network_security_group.yawl[0].name
  resource_group_name         = var.azure_resource_group_name
}

resource "azurerm_network_security_rule" "allow_https" {
  count                       = var.cloud_provider == "azure" ? 1 : 0
  name                        = "AllowHTTPS"
  priority                    = 101
  direction                   = "Inbound"
  access                      = "Allow"
  protocol                    = "Tcp"
  source_port_range           = "*"
  destination_port_range      = "443"
  source_address_prefixes     = length(var.allowed_cidr_blocks) > 0 ? var.allowed_cidr_blocks : ["*"]
  destination_address_prefix  = "*"
  network_security_group_name = azurerm_network_security_group.yawl[0].name
  resource_group_name         = var.azure_resource_group_name
}

resource "azurerm_network_security_rule" "allow_api" {
  count                       = var.cloud_provider == "azure" ? 1 : 0
  name                        = "AllowAPI"
  priority                    = 110
  direction                   = "Inbound"
  access                      = "Allow"
  protocol                    = "Tcp"
  source_port_range           = "*"
  destination_port_range      = "8080"
  source_address_prefix       = "VirtualNetwork"
  destination_address_prefix  = "*"
  network_security_group_name = azurerm_network_security_group.yawl[0].name
  resource_group_name         = var.azure_resource_group_name
}

resource "azurerm_network_security_rule" "allow_database" {
  count                       = var.cloud_provider == "azure" ? 1 : 0
  name                        = "AllowDatabase"
  priority                    = 120
  direction                   = "Inbound"
  access                      = "Allow"
  protocol                    = "Tcp"
  source_port_range           = "*"
  destination_port_range      = "5432"
  source_address_prefix       = "VirtualNetwork"
  destination_address_prefix  = "*"
  network_security_group_name = azurerm_network_security_group.yawl[0].name
  resource_group_name         = var.azure_resource_group_name
}

resource "azurerm_network_security_rule" "deny_all_inbound" {
  count                       = var.cloud_provider == "azure" ? 1 : 0
  name                        = "DenyAllInbound"
  priority                    = 4096
  direction                   = "Inbound"
  access                      = "Deny"
  protocol                    = "*"
  source_port_range           = "*"
  destination_port_range      = "*"
  source_address_prefix       = "*"
  destination_address_prefix  = "*"
  network_security_group_name = azurerm_network_security_group.yawl[0].name
  resource_group_name         = var.azure_resource_group_name
}

#------------------------------------------------------------------------------
# Oracle Cloud Security Lists
#------------------------------------------------------------------------------

resource "oci_core_security_list" "yawl_private" {
  count          = var.cloud_provider == "oracle" ? 1 : 0
  compartment_id = var.oci_compartment_id
  vcn_id         = var.vpc_id
  display_name   = "${local.name_prefix}-private-sl"

  egress_security_rules {
    protocol    = "all"
    destination = "0.0.0.0/0"
  }

  ingress_security_rules {
    protocol = "6"
    source   = var.vpc_cidr
    tcp_options {
      min = 5432
      max = 5432
    }
  }

  ingress_security_rules {
    protocol = "6"
    source   = var.vpc_cidr
    tcp_options {
      min = 8080
      max = 8080
    }
  }

  freeform_tags = var.common_tags
}

resource "oci_core_security_list" "yawl_public" {
  count          = var.cloud_provider == "oracle" ? 1 : 0
  compartment_id = var.oci_compartment_id
  vcn_id         = var.vpc_id
  display_name   = "${local.name_prefix}-public-sl"

  egress_security_rules {
    protocol    = "all"
    destination = "0.0.0.0/0"
  }

  ingress_security_rules {
    protocol = "6"
    source   = length(var.allowed_cidr_blocks) > 0 ? var.allowed_cidr_blocks[0] : "0.0.0.0/0"
    tcp_options {
      min = 80
      max = 80
    }
  }

  ingress_security_rules {
    protocol = "6"
    source   = length(var.allowed_cidr_blocks) > 0 ? var.allowed_cidr_blocks[0] : "0.0.0.0/0"
    tcp_options {
      min = 443
      max = 443
    }
  }

  freeform_tags = var.common_tags
}

#------------------------------------------------------------------------------
# IBM Cloud Security Groups
#------------------------------------------------------------------------------

resource "ibm_is_security_group" "yawl" {
  count          = var.cloud_provider == "ibm" ? 1 : 0
  name           = "${local.name_prefix}-sg"
  vpc            = var.vpc_id
  resource_group = var.ibm_resource_group != "" ? data.ibm_resource_group.yawl[0].id : null

  tags = var.common_tags
}

data "ibm_resource_group" "yawl" {
  count = var.cloud_provider == "ibm" ? 1 : 0
  name  = var.ibm_resource_group
}

resource "ibm_is_security_group_rule" "allow_http" {
  count     = var.cloud_provider == "ibm" ? 1 : 0
  group     = ibm_is_security_group.yawl[0].id
  direction = "inbound"
  remote    = "0.0.0.0/0"

  tcp {
    port_min = 80
    port_max = 80
  }
}

resource "ibm_is_security_group_rule" "allow_https" {
  count     = var.cloud_provider == "ibm" ? 1 : 0
  group     = ibm_is_security_group.yawl[0].id
  direction = "inbound"
  remote    = "0.0.0.0/0"

  tcp {
    port_min = 443
    port_max = 443
  }
}

resource "ibm_is_security_group_rule" "allow_tcp_internal" {
  count     = var.cloud_provider == "ibm" ? 1 : 0
  group     = ibm_is_security_group.yawl[0].id
  direction = "inbound"
  remote    = var.vpc_cidr

  tcp {
    port_min = 1
    port_max = 65535
  }
}

resource "ibm_is_security_group_rule" "allow_outbound" {
  count     = var.cloud_provider == "ibm" ? 1 : 0
  group     = ibm_is_security_group.yawl[0].id
  direction = "outbound"
  remote    = "0.0.0.0/0"

  tcp {
    port_min = 1
    port_max = 65535
  }
}

#------------------------------------------------------------------------------
# Outputs
#------------------------------------------------------------------------------

output "security_group_ids" {
  description = "Security group IDs"
  value = var.cloud_provider == "aws" ? {
    default = aws_security_group.yawl_default[0].id
    web     = aws_security_group.yawl_web[0].id
    api     = aws_security_group.yawl_api[0].id
    db      = aws_security_group.yawl_database[0].id
  } : var.cloud_provider == "gcp" ? {
    firewall = google_compute_firewall.allow_http_https[0].id
  } : var.cloud_provider == "azure" ? {
    nsg = azurerm_network_security_group.yawl[0].id
  } : var.cloud_provider == "oracle" ? {
    private = oci_core_security_list.yawl_private[0].id
    public  = oci_core_security_list.yawl_public[0].id
  } : var.cloud_provider == "ibm" ? {
    sg = ibm_is_security_group.yawl[0].id
  } : {}
}
