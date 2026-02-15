#------------------------------------------------------------------------------
# YAWL Shared Networking Module
# Multi-cloud VPC/VNet configuration
# Version: 1.0.0
#------------------------------------------------------------------------------

locals {
  name_prefix = "${var.project_name}-${var.environment}"
}

#------------------------------------------------------------------------------
# AWS VPC Configuration
#------------------------------------------------------------------------------

resource "aws_vpc" "yawl" {
  count                = var.cloud_provider == "aws" ? 1 : 0
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-vpc"
  })
}

resource "aws_internet_gateway" "yawl" {
  count  = var.cloud_provider == "aws" && var.enable_nat ? 1 : 0
  vpc_id = aws_vpc.yawl[0].id

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-igw"
  })
}

resource "aws_eip" "nat" {
  count  = var.cloud_provider == "aws" && var.enable_nat ? length(var.aws_availability_zones) : 0
  domain = "vpc"

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-nat-eip-${count.index + 1}"
  })

  depends_on = [aws_internet_gateway.yawl]
}

resource "aws_nat_gateway" "yawl" {
  count         = var.cloud_provider == "aws" && var.enable_nat ? length(var.aws_availability_zones) : 0
  allocation_id = aws_eip.nat[count.index].id
  subnet_id     = aws_subnet.public[count.index].id

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-nat-${count.index + 1}"
  })

  depends_on = [aws_internet_gateway.yawl]
}

resource "aws_subnet" "private" {
  count             = var.cloud_provider == "aws" ? length(var.subnet_cidrs.private) : 0
  vpc_id            = aws_vpc.yawl[0].id
  cidr_block        = var.subnet_cidrs.private[count.index]
  availability_zone = var.aws_availability_zones[count.index % length(var.aws_availability_zones)]

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-private-${count.index + 1}"
    Type = "private"
    "kubernetes.io/role/internal-elb" = "1"
    "kubernetes.io/cluster/${local.name_prefix}" = "shared"
  })
}

resource "aws_subnet" "public" {
  count                   = var.cloud_provider == "aws" ? length(var.subnet_cidrs.public) : 0
  vpc_id                  = aws_vpc.yawl[0].id
  cidr_block              = var.subnet_cidrs.public[count.index]
  availability_zone       = var.aws_availability_zones[count.index % length(var.aws_availability_zones)]
  map_public_ip_on_launch = true

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-public-${count.index + 1}"
    Type = "public"
    "kubernetes.io/role/elb" = "1"
    "kubernetes.io/cluster/${local.name_prefix}" = "shared"
  })
}

resource "aws_subnet" "data" {
  count             = var.cloud_provider == "aws" ? length(var.subnet_cidrs.data) : 0
  vpc_id            = aws_vpc.yawl[0].id
  cidr_block        = var.subnet_cidrs.data[count.index]
  availability_zone = var.aws_availability_zones[count.index % length(var.aws_availability_zones)]

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-data-${count.index + 1}"
    Type = "data"
  })
}

resource "aws_route_table" "private" {
  count  = var.cloud_provider == "aws" ? length(var.subnet_cidrs.private) : 0
  vpc_id = aws_vpc.yawl[0].id

  dynamic "route" {
    for_each = var.enable_nat ? [1] : []
    content {
      cidr_block     = "0.0.0.0/0"
      nat_gateway_id = aws_nat_gateway.yawl[count.index % length(aws_nat_gateway.yawl)].id
    }
  }

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-private-rt-${count.index + 1}"
  })
}

resource "aws_route_table" "public" {
  count  = var.cloud_provider == "aws" ? 1 : 0
  vpc_id = aws_vpc.yawl[0].id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.yawl[0].id
  }

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-public-rt"
  })
}

resource "aws_route_table_association" "private" {
  count          = var.cloud_provider == "aws" ? length(aws_subnet.private) : 0
  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private[count.index].id
}

resource "aws_route_table_association" "public" {
  count          = var.cloud_provider == "aws" ? length(aws_subnet.public) : 0
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public[0].id
}

resource "aws_route_table_association" "data" {
  count          = var.cloud_provider == "aws" ? length(aws_subnet.data) : 0
  subnet_id      = aws_subnet.data[count.index].id
  route_table_id = aws_route_table.private[count.index % length(aws_route_table.private)].id
}

#------------------------------------------------------------------------------
# GCP VPC Configuration
#------------------------------------------------------------------------------

resource "google_compute_network" "yawl" {
  count                   = var.cloud_provider == "gcp" ? 1 : 0
  name                    = "${local.name_prefix}-vpc"
  project                 = var.gcp_project_id
  auto_create_subnetworks = false
  routing_mode            = "REGIONAL"

  lifecycle {
    create_before_destroy = true
  }
}

resource "google_compute_subnetwork" "private" {
  count         = var.cloud_provider == "gcp" ? length(var.subnet_cidrs.private) : 0
  name          = "${local.name_prefix}-private-${count.index + 1}"
  project       = var.gcp_project_id
  region        = var.gcp_region
  network       = google_compute_network.yawl[0].id
  ip_cidr_range = var.subnet_cidrs.private[count.index]

  secondary_ip_range {
    range_name    = "pods"
    ip_cidr_range = "10.${count.index + 100}.0.0/16"
  }

  secondary_ip_range {
    range_name    = "services"
    ip_cidr_range = "10.${count.index + 200}.0.0/20"
  }

  private_ip_google_access = true

  lifecycle {
    create_before_destroy = true
  }
}

resource "google_compute_subnetwork" "public" {
  count         = var.cloud_provider == "gcp" ? length(var.subnet_cidrs.public) : 0
  name          = "${local.name_prefix}-public-${count.index + 1}"
  project       = var.gcp_project_id
  region        = var.gcp_region
  network       = google_compute_network.yawl[0].id
  ip_cidr_range = var.subnet_cidrs.public[count.index]

  lifecycle {
    create_before_destroy = true
  }
}

resource "google_compute_subnetwork" "data" {
  count         = var.cloud_provider == "gcp" ? length(var.subnet_cidrs.data) : 0
  name          = "${local.name_prefix}-data-${count.index + 1}"
  project       = var.gcp_project_id
  region        = var.gcp_region
  network       = google_compute_network.yawl[0].id
  ip_cidr_range = var.subnet_cidrs.data[count.index]

  private_ip_google_access = true

  lifecycle {
    create_before_destroy = true
  }
}

resource "google_compute_router" "yawl" {
  count   = var.cloud_provider == "gcp" && var.enable_nat ? 1 : 0
  name    = "${local.name_prefix}-router"
  project = var.gcp_project_id
  region  = var.gcp_region
  network = google_compute_network.yawl[0].id
}

resource "google_compute_router_nat" "yawl" {
  count                              = var.cloud_provider == "gcp" && var.enable_nat ? 1 : 0
  name                               = "${local.name_prefix}-nat"
  project                            = var.gcp_project_id
  region                             = var.gcp_region
  router                             = google_compute_router.yawl[0].name
  nat_ip_allocate_option             = "AUTO_ONLY"
  source_subnetwork_ip_ranges_to_nat = "ALL_SUBNETWORKS_ALL_IP_RANGES"
}

resource "google_compute_firewall" "allow_internal" {
  count   = var.cloud_provider == "gcp" ? 1 : 0
  name    = "${local.name_prefix}-allow-internal"
  project = var.gcp_project_id
  network = google_compute_network.yawl[0].name

  allow {
    protocol = "tcp"
    ports    = ["0-65535"]
  }

  allow {
    protocol = "udp"
    ports    = ["0-65535"]
  }

  allow {
    protocol = "icmp"
  }

  source_ranges = [var.vpc_cidr]
}

#------------------------------------------------------------------------------
# Azure VNet Configuration
#------------------------------------------------------------------------------

resource "azurerm_virtual_network" "yawl" {
  count               = var.cloud_provider == "azure" ? 1 : 0
  name                = "${local.name_prefix}-vnet"
  location            = var.azure_location
  resource_group_name = coalesce(var.azure_resource_group_name, azurerm_resource_group.yawl[0].name)
  address_space       = [var.vpc_cidr]

  tags = var.common_tags
}

resource "azurerm_resource_group" "yawl" {
  count    = var.cloud_provider == "azure" && var.azure_resource_group_name == "" ? 1 : 0
  name     = "${local.name_prefix}-rg"
  location = var.azure_location

  tags = var.common_tags
}

resource "azurerm_subnet" "private" {
  count                = var.cloud_provider == "azure" ? length(var.subnet_cidrs.private) : 0
  name                 = "private-${count.index + 1}"
  resource_group_name  = coalesce(var.azure_resource_group_name, azurerm_resource_group.yawl[0].name)
  virtual_network_name = azurerm_virtual_network.yawl[0].name
  address_prefixes     = [var.subnet_cidrs.private[count.index]]

  private_endpoint_network_policies_enabled = true
}

resource "azurerm_subnet" "public" {
  count                = var.cloud_provider == "azure" ? length(var.subnet_cidrs.public) : 0
  name                 = "public-${count.index + 1}"
  resource_group_name  = coalesce(var.azure_resource_group_name, azurerm_resource_group.yawl[0].name)
  virtual_network_name = azurerm_virtual_network.yawl[0].name
  address_prefixes     = [var.subnet_cidrs.public[count.index]]
}

resource "azurerm_subnet" "data" {
  count                = var.cloud_provider == "azure" ? length(var.subnet_cidrs.data) : 0
  name                 = "data-${count.index + 1}"
  resource_group_name  = coalesce(var.azure_resource_group_name, azurerm_resource_group.yawl[0].name)
  virtual_network_name = azurerm_virtual_network.yawl[0].name
  address_prefixes     = [var.subnet_cidrs.data[count.index]]

  private_endpoint_network_policies_enabled = true
}

resource "azurerm_subnet_nat_gateway" "yawl" {
  count       = var.cloud_provider == "azure" && var.enable_nat ? 1 : 0
  name        = "${local.name_prefix}-nat-gw"
  location    = var.azure_location
  resource_group_name = coalesce(var.azure_resource_group_name, azurerm_resource_group.yawl[0].name)
  subnet_id   = azurerm_subnet.private[0].id
  nat_gateway_id = azurerm_nat_gateway.yawl[0].id
}

resource "azurerm_nat_gateway" "yawl" {
  count               = var.cloud_provider == "azure" && var.enable_nat ? 1 : 0
  name                = "${local.name_prefix}-nat-gw"
  location            = var.azure_location
  resource_group_name = coalesce(var.azure_resource_group_name, azurerm_resource_group.yawl[0].name)
  sku_name            = "Standard"

  tags = var.common_tags
}

resource "azurerm_public_ip" "nat" {
  count               = var.cloud_provider == "azure" && var.enable_nat ? 1 : 0
  name                = "${local.name_prefix}-nat-ip"
  location            = var.azure_location
  resource_group_name = coalesce(var.azure_resource_group_name, azurerm_resource_group.yawl[0].name)
  allocation_method   = "Static"
  sku                 = "Standard"

  tags = var.common_tags
}

resource "azurerm_nat_gateway_public_ip_association" "yawl" {
  count               = var.cloud_provider == "azure" && var.enable_nat ? 1 : 0
  nat_gateway_id      = azurerm_nat_gateway.yawl[0].id
  public_ip_address_id = azurerm_public_ip.nat[0].id
}

#------------------------------------------------------------------------------
# Oracle Cloud VCN Configuration
#------------------------------------------------------------------------------

resource "oci_core_vcn" "yawl" {
  count          = var.cloud_provider == "oracle" ? 1 : 0
  compartment_id = var.oci_compartment_id
  cidr_blocks    = [var.vpc_cidr]
  display_name   = "${local.name_prefix}-vcn"

  freeform_tags = var.common_tags
}

resource "oci_core_subnet" "private" {
  count               = var.cloud_provider == "oracle" ? length(var.subnet_cidrs.private) : 0
  compartment_id      = var.oci_compartment_id
  vcn_id              = oci_core_vcn.yawl[0].id
  cidr_block          = var.subnet_cidrs.private[count.index]
  display_name        = "${local.name_prefix}-private-${count.index + 1}"
  prohibit_public_ip_on_vnic = true

  freeform_tags = var.common_tags
}

resource "oci_core_subnet" "public" {
  count          = var.cloud_provider == "oracle" ? length(var.subnet_cidrs.public) : 0
  compartment_id = var.oci_compartment_id
  vcn_id         = oci_core_vcn.yawl[0].id
  cidr_block     = var.subnet_cidrs.public[count.index]
  display_name   = "${local.name_prefix}-public-${count.index + 1}"

  freeform_tags = var.common_tags
}

resource "oci_core_subnet" "data" {
  count               = var.cloud_provider == "oracle" ? length(var.subnet_cidrs.data) : 0
  compartment_id      = var.oci_compartment_id
  vcn_id              = oci_core_vcn.yawl[0].id
  cidr_block          = var.subnet_cidrs.data[count.index]
  display_name        = "${local.name_prefix}-data-${count.index + 1}"
  prohibit_public_ip_on_vnic = true

  freeform_tags = var.common_tags
}

resource "oci_core_nat_gateway" "yawl" {
  count          = var.cloud_provider == "oracle" && var.enable_nat ? 1 : 0
  compartment_id = var.oci_compartment_id
  vcn_id         = oci_core_vcn.yawl[0].id
  display_name   = "${local.name_prefix}-nat-gw"

  freeform_tags = var.common_tags
}

resource "oci_core_route_table" "private" {
  count          = var.cloud_provider == "oracle" ? 1 : 0
  compartment_id = var.oci_compartment_id
  vcn_id         = oci_core_vcn.yawl[0].id
  display_name   = "${local.name_prefix}-private-rt"

  dynamic "route_rules" {
    for_each = var.enable_nat ? [1] : []
    content {
      destination       = "0.0.0.0/0"
      network_entity_id = oci_core_nat_gateway.yawl[0].id
    }
  }

  freeform_tags = var.common_tags
}

resource "oci_core_internet_gateway" "yawl" {
  count          = var.cloud_provider == "oracle" ? 1 : 0
  compartment_id = var.oci_compartment_id
  vcn_id         = oci_core_vcn.yawl[0].id
  display_name   = "${local.name_prefix}-igw"

  freeform_tags = var.common_tags
}

resource "oci_core_route_table" "public" {
  count          = var.cloud_provider == "oracle" ? 1 : 0
  compartment_id = var.oci_compartment_id
  vcn_id         = oci_core_vcn.yawl[0].id
  display_name   = "${local.name_prefix}-public-rt"

  route_rules {
    destination       = "0.0.0.0/0"
    network_entity_id = oci_core_internet_gateway.yawl[0].id
  }

  freeform_tags = var.common_tags
}

#------------------------------------------------------------------------------
# IBM Cloud VPC Configuration
#------------------------------------------------------------------------------

resource "ibm_is_vpc" "yawl" {
  count         = var.cloud_provider == "ibm" && var.vpc_id == "" ? 1 : 0
  name          = "${local.name_prefix}-vpc"
  resource_group = data.ibm_resource_group.yawl[0].id

  tags = var.common_tags
}

data "ibm_resource_group" "yawl" {
  count = var.cloud_provider == "ibm" ? 1 : 0
  name  = var.ibm_resource_group
}

resource "ibm_is_subnet" "private" {
  count          = var.cloud_provider == "ibm" ? length(var.subnet_cidrs.private) : 0
  name           = "${local.name_prefix}-private-${count.index + 1}"
  vpc            = var.vpc_id != "" ? var.vpc_id : ibm_is_vpc.yawl[0].id
  zone           = "${var.ibm_region}-${count.index + 1}"
  ipv4_cidr_block = var.subnet_cidrs.private[count.index]
  resource_group = data.ibm_resource_group.yawl[0].id

  tags = var.common_tags
}

resource "ibm_is_subnet" "public" {
  count          = var.cloud_provider == "ibm" ? length(var.subnet_cidrs.public) : 0
  name           = "${local.name_prefix}-public-${count.index + 1}"
  vpc            = var.vpc_id != "" ? var.vpc_id : ibm_is_vpc.yawl[0].id
  zone           = "${var.ibm_region}-${count.index + 1}"
  ipv4_cidr_block = var.subnet_cidrs.public[count.index]
  resource_group = data.ibm_resource_group.yawl[0].id

  tags = var.common_tags
}

resource "ibm_is_public_gateway" "yawl" {
  count          = var.cloud_provider == "ibm" && var.enable_nat ? length(var.subnet_cidrs.private) : 0
  name           = "${local.name_prefix}-pgw-${count.index + 1}"
  vpc            = var.vpc_id != "" ? var.vpc_id : ibm_is_vpc.yawl[0].id
  zone           = "${var.ibm_region}-${count.index + 1}"
  resource_group = data.ibm_resource_group.yawl[0].id

  tags = var.common_tags
}

#------------------------------------------------------------------------------
# Outputs (conditional based on cloud provider)
#------------------------------------------------------------------------------

output "vpc_id" {
  description = "VPC/VNet ID"
  value = var.cloud_provider == "aws" ? try(aws_vpc.yawl[0].id, null) : var.cloud_provider == "gcp" ? try(google_compute_network.yawl[0].id, null) : var.cloud_provider == "azure" ? try(azurerm_virtual_network.yawl[0].id, null) : var.cloud_provider == "oracle" ? try(oci_core_vcn.yawl[0].id, null) : var.cloud_provider == "ibm" ? try(ibm_is_vpc.yawl[0].id, null) : null
}

output "vpc_cidr" {
  description = "VPC/VNet CIDR block"
  value       = var.vpc_cidr
}

output "private_subnet_ids" {
  description = "Private subnet IDs"
  value = flatten([
    var.cloud_provider == "aws" ? aws_subnet.private[*].id : [],
    var.cloud_provider == "gcp" ? google_compute_subnetwork.private[*].id : [],
    var.cloud_provider == "azure" ? azurerm_subnet.private[*].id : [],
    var.cloud_provider == "oracle" ? oci_core_subnet.private[*].id : [],
    var.cloud_provider == "ibm" ? ibm_is_subnet.private[*].id : []
  ])
}

output "public_subnet_ids" {
  description = "Public subnet IDs"
  value = flatten([
    var.cloud_provider == "aws" ? aws_subnet.public[*].id : [],
    var.cloud_provider == "gcp" ? google_compute_subnetwork.public[*].id : [],
    var.cloud_provider == "azure" ? azurerm_subnet.public[*].id : [],
    var.cloud_provider == "oracle" ? oci_core_subnet.public[*].id : [],
    var.cloud_provider == "ibm" ? ibm_is_subnet.public[*].id : []
  ])
}

output "data_subnet_ids" {
  description = "Data subnet IDs"
  value = flatten([
    var.cloud_provider == "aws" ? aws_subnet.data[*].id : [],
    var.cloud_provider == "gcp" ? google_compute_subnetwork.data[*].id : [],
    var.cloud_provider == "azure" ? azurerm_subnet.data[*].id : [],
    var.cloud_provider == "oracle" ? oci_core_subnet.data[*].id : [],
    var.cloud_provider == "ibm" ? ibm_is_subnet.data[*].id : []
  ])
}
